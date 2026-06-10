package io.crossasset.ems.transport.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Resumable, sequence-numbered session channel per arch-sequence-recovery.md.
 *
 * <p>Tracks per-session, per-direction sequence numbers and provides the FIX-equivalent recovery
 * primitives the EMS uses on every hop (client → aaa → order → router): inbound gap/duplicate
 * detection, an outbound resend buffer for replaying missed messages, resume-from-seq on reconnect,
 * and heartbeat liveness with {@code TEST_REQUEST} / {@code STALE} escalation.
 *
 * <p><b>Inbound</b> ({@link #checkSequence}) dedups and gap-detects messages arriving from the
 * peer. <b>Outbound</b> ({@link #recordOutbound}, {@link #resend}, {@link #resumeOutbound}) buffers
 * messages we send so a reconnecting peer can recover anything it missed. The two directions carry
 * independent monotonic sequence numbers, exactly as a FIX session does.
 *
 * <p>The clock is injectable so heartbeat behaviour is deterministic under the simulated clock used
 * by replay (see arch-time-replay-server.md); the no-arg constructor uses the wall clock.
 *
 * <p>All per-session state mutations synchronize on the session's channel, so the service is safe
 * under concurrent access from multiple transport threads.
 */
public class SequenceRecoveryService {
  private static final Logger logger = Logger.getLogger(SequenceRecoveryService.class.getName());

  /** Default heartbeat interval (FIX-typical 30s). */
  public static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 30_000L;

  /** Default outbound resend-buffer depth (messages retained per session for replay). */
  public static final int DEFAULT_MAX_RESEND_WINDOW = 10_000;

  public enum SessionStatus {
    ACTIVE,
    GAP_DETECTED,
    RECOVERY,
    STALE,
    LOGOUT
  }

  /** Action the caller should take after a liveness check. */
  public enum HeartbeatAction {
    /** Peer is live within the heartbeat interval. */
    OK,
    /** One interval elapsed with no traffic — issue a {@code TEST_REQUEST}. */
    SEND_TEST_REQUEST,
    /** Two intervals elapsed — session is stale and must recover on reconnect. */
    STALE
  }

  /** Read-only snapshot of a session's state. */
  public record SessionState(
      long sessionId,
      long nextExpectedSeq,
      long outboundSeq,
      long lastActivityMillis,
      int missedBeats,
      SessionStatus status) {}

  /** Outcome of a logon / resume attempt. */
  public record RecoveryResult(boolean success, long expectedSeq, String message) {}

  /** A buffered outbound message, returned by resend/resume. */
  public record BufferedMessage(long seq, byte[] payload) {}

  private final ConcurrentHashMap<Long, SessionChannel> channels = new ConcurrentHashMap<>();
  private final LongSupplier clockMillis;
  private final long heartbeatIntervalMillis;
  private final int maxResendWindow;

  public SequenceRecoveryService() {
    this(System::currentTimeMillis, DEFAULT_HEARTBEAT_INTERVAL_MILLIS, DEFAULT_MAX_RESEND_WINDOW);
  }

  public SequenceRecoveryService(LongSupplier clockMillis) {
    this(clockMillis, DEFAULT_HEARTBEAT_INTERVAL_MILLIS, DEFAULT_MAX_RESEND_WINDOW);
  }

  public SequenceRecoveryService(
      LongSupplier clockMillis, long heartbeatIntervalMillis, int maxResendWindow) {
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    if (heartbeatIntervalMillis <= 0) {
      throw new IllegalArgumentException("heartbeatIntervalMillis must be > 0");
    }
    if (maxResendWindow <= 0) {
      throw new IllegalArgumentException("maxResendWindow must be > 0");
    }
    this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    this.maxResendWindow = maxResendWindow;
  }

  // ── Logon / resume ─────────────────────────────────────────────────────────

  /**
   * Establish or restore a session.
   *
   * @param sessionId the edge-assigned session ID.
   * @param declaredSeq the next inbound sequence number the client claims to be at.
   * @return RecoveryResult indicating if the session is active or requires resend/reset.
   */
  public RecoveryResult logon(long sessionId, long declaredSeq) {
    long now = clockMillis.getAsLong();
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      SessionChannel created = new SessionChannel(sessionId, declaredSeq, now);
      SessionChannel prev = channels.putIfAbsent(sessionId, created);
      if (prev == null) {
        return new RecoveryResult(true, declaredSeq, "Session established");
      }
      ch = prev; // lost the race; fall through to the resume path
    }

    synchronized (ch) {
      ch.lastActivityMillis = now;
      long expected = ch.inboundExpectedSeq;
      if (declaredSeq < expected) {
        return new RecoveryResult(false, expected, "Sequence too low (EMS-SES-1002)");
      }
      if (declaredSeq > expected) {
        ch.status = SessionStatus.GAP_DETECTED;
        ch.gapHighWater = Math.max(ch.gapHighWater, declaredSeq - 1);
        return new RecoveryResult(false, expected, "Gap detected: resend required (EMS-SES-2001)");
      }
      ch.status = SessionStatus.ACTIVE;
      ch.missedBeats = 0;
      return new RecoveryResult(true, expected, "Session resumed");
    }
  }

  // ── Inbound: gap / duplicate detection ─────────────────────────────────────

  /**
   * Process an incoming message sequence number.
   *
   * <ul>
   *   <li>{@code seq == expected} → in-order; advance the expected counter, return empty.
   *   <li>{@code seq < expected} → duplicate; drop (idempotency confirmed by {@code request_id} at
   *       the application layer); the expected counter is <b>not</b> advanced.
   *   <li>{@code seq > expected} → gap; mark {@code GAP_DETECTED} and return {@code "RESEND"}; the
   *       expected counter is <b>not</b> advanced so the resent messages are accepted in order.
   * </ul>
   *
   * @return {@code Optional.of("RESEND")} on a gap, {@code Optional.of("SESSION_NOT_FOUND")} for an
   *     unknown session, empty otherwise.
   */
  public Optional<String> checkSequence(long sessionId, long incomingSeq) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      return Optional.of("SESSION_NOT_FOUND");
    }
    synchronized (ch) {
      ch.lastActivityMillis = clockMillis.getAsLong();
      ch.missedBeats = 0;
      long expected = ch.inboundExpectedSeq;
      if (incomingSeq < expected) {
        // Duplicate — drop without advancing. Do NOT increment, or the next in-order
        // message would be mistaken for a duplicate (the original skeleton's bug).
        return Optional.empty();
      }
      if (incomingSeq > expected) {
        ch.status = SessionStatus.GAP_DETECTED;
        ch.gapHighWater = Math.max(ch.gapHighWater, incomingSeq);
        return Optional.of("RESEND");
      }
      ch.inboundExpectedSeq = expected + 1;
      // Gap closes only once the expected counter has caught up past the high-water mark.
      if (ch.status == SessionStatus.GAP_DETECTED && ch.inboundExpectedSeq > ch.gapHighWater) {
        ch.status = SessionStatus.ACTIVE;
      }
      return Optional.empty();
    }
  }

  // ── Outbound: resend buffer + resume-from-seq ──────────────────────────────

  /**
   * Record an outbound message and assign it the next outbound sequence number. The payload is
   * retained in the per-session resend buffer (bounded by {@code maxResendWindow}; the oldest is
   * evicted past that depth) so a reconnecting peer can recover it.
   *
   * @return the assigned outbound sequence number.
   * @throws IllegalStateException if the session is unknown.
   */
  public long recordOutbound(long sessionId, byte[] payload) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      throw new IllegalStateException("Unknown session " + sessionId);
    }
    synchronized (ch) {
      long seq = ch.outboundSeq++;
      ch.outboundBuffer.put(seq, payload == null ? new byte[0] : payload.clone());
      while (ch.outboundBuffer.size() > maxResendWindow) {
        ch.outboundBuffer.pollFirstEntry();
      }
      return seq;
    }
  }

  /**
   * Replay buffered outbound messages in the inclusive range {@code [fromSeq, toSeq]}. Messages
   * that have already been evicted from the resend buffer are silently absent from the result (the
   * gap is unrecoverable and warrants a {@code RESET} at the caller).
   */
  public List<BufferedMessage> resend(long sessionId, long fromSeq, long toSeq) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null || fromSeq > toSeq) {
      return List.of();
    }
    synchronized (ch) {
      List<BufferedMessage> out = new ArrayList<>();
      for (var e : ch.outboundBuffer.subMap(fromSeq, true, toSeq, true).entrySet()) {
        out.add(new BufferedMessage(e.getKey(), e.getValue().clone()));
      }
      return out;
    }
  }

  /**
   * Resume-from-seq: replay everything we sent at or after {@code clientNextExpectedSeq}, used when
   * a peer reconnects declaring the next outbound seq it expects from us.
   */
  public List<BufferedMessage> resumeOutbound(long sessionId, long clientNextExpectedSeq) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      return List.of();
    }
    synchronized (ch) {
      return resend(sessionId, clientNextExpectedSeq, ch.outboundSeq - 1);
    }
  }

  // ── Heartbeats ─────────────────────────────────────────────────────────────

  /** Record inbound activity (any message or heartbeat), resetting the liveness timer. */
  public void recordActivity(long sessionId) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      return;
    }
    synchronized (ch) {
      ch.lastActivityMillis = clockMillis.getAsLong();
      ch.missedBeats = 0;
      if (ch.status == SessionStatus.STALE) {
        ch.status = SessionStatus.ACTIVE;
      }
    }
  }

  /**
   * Evaluate liveness against the heartbeat interval: one interval of silence → {@code
   * SEND_TEST_REQUEST}; two intervals → {@code STALE} (status updated accordingly). Pure function
   * of the injected clock and last-activity time, so it is deterministic under replay.
   */
  public HeartbeatAction checkLiveness(long sessionId) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      return HeartbeatAction.STALE;
    }
    synchronized (ch) {
      long elapsed = clockMillis.getAsLong() - ch.lastActivityMillis;
      if (elapsed >= 2 * heartbeatIntervalMillis) {
        ch.status = SessionStatus.STALE;
        ch.missedBeats = 2;
        return HeartbeatAction.STALE;
      }
      if (elapsed >= heartbeatIntervalMillis) {
        ch.missedBeats = 1;
        return HeartbeatAction.SEND_TEST_REQUEST;
      }
      return HeartbeatAction.OK;
    }
  }

  // ── Status / inspection ────────────────────────────────────────────────────

  public void updateStatus(long sessionId, SessionStatus status) {
    SessionChannel ch = channels.get(sessionId);
    if (ch != null) {
      synchronized (ch) {
        ch.status = status;
      }
    }
  }

  public Optional<SessionState> getSession(long sessionId) {
    SessionChannel ch = channels.get(sessionId);
    if (ch == null) {
      return Optional.empty();
    }
    synchronized (ch) {
      return Optional.of(
          new SessionState(
              ch.sessionId,
              ch.inboundExpectedSeq,
              ch.outboundSeq,
              ch.lastActivityMillis,
              ch.missedBeats,
              ch.status));
    }
  }

  /** Per-session mutable channel state; all access synchronizes on the instance. */
  private static final class SessionChannel {
    final long sessionId;
    long inboundExpectedSeq;
    long gapHighWater;
    long outboundSeq = 1;
    long lastActivityMillis;
    int missedBeats;
    SessionStatus status = SessionStatus.ACTIVE;
    final TreeMap<Long, byte[]> outboundBuffer = new TreeMap<>();

    SessionChannel(long sessionId, long inboundExpectedSeq, long nowMillis) {
      this.sessionId = sessionId;
      this.inboundExpectedSeq = inboundExpectedSeq;
      this.gapHighWater = inboundExpectedSeq - 1;
      this.lastActivityMillis = nowMillis;
    }
  }
}
