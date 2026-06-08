package io.crossasset.ems.transport.session;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

    /**
     * SequenceRecoveryService provides a skeleton for tracking session sequence numbers,
     * detecting gaps, and managing the session lifecycle per arch-sequence-recovery.md.
     *
     * This is a skeleton implementation focused on the core state tracking logic.
     */
public class SequenceRecoveryService {
  private static final Logger logger = Logger.getLogger(SequenceRecoveryService.class.getName());

  public record SessionState(
      long sessionId, AtomicLong nextExpectedSeq, long lastHeartbeat, SessionStatus status) {}

  public enum SessionStatus {
    ACTIVE,
    GAP_DETECTED,
    RECOVERY,
    STALE,
    LOGOUT
  }

  private final ConcurrentHashMap<Long, SessionState> sessions = new ConcurrentHashMap<>();

  /**
   * Establish or restore a session.
   *
   * @param sessionId The edge-assigned session ID.
   * @param declaredSeq The sequence number the client claims to be at.
   * @return RecoveryResult indicating if the session is active or requires resend/reset.
   */
  public RecoveryResult logon(long sessionId, long declaredSeq) {
    SessionState state = sessions.get(sessionId);

    if (state == null) {
      // New session: accept declared sequence
      sessions.put(
          sessionId,
          new SessionState(
              sessionId,
              new AtomicLong(declaredSeq),
              System.currentTimeMillis(),
              SessionStatus.ACTIVE));
      return new RecoveryResult(true, declaredSeq, "Session established");
    }

    long expected = state.nextExpectedSeq().get();
    if (declaredSeq < expected) {
      return new RecoveryResult(false, expected, "Sequence too low (EMS-SES-1002)");
    } else if (declaredSeq > expected) {
      return new RecoveryResult(false, expected, "Gap detected: resend required (EMS-SES-2001)");
    }

    return new RecoveryResult(true, expected, "Session resumed");
  }

  /**
   * Process an incoming message sequence number.
   *
   * @return Optional.of("RESEND") if a gap is detected, empty if OK.
   */
  public Optional<String> checkSequence(long sessionId, long incomingSeq) {
    SessionState state = sessions.get(sessionId);
    if (state == null) return Optional.of("SESSION_NOT_FOUND");

    long expected = state.nextExpectedSeq().getAndIncrement();

    if (incomingSeq < expected) {
      // Possible duplicate - handled by request_id at application layer
      return Optional.empty();
    } else if (incomingSeq > expected) {
      // Gap detected
      updateStatus(sessionId, SessionStatus.GAP_DETECTED);
      return Optional.of("RESEND");
    }

    return Optional.empty();
  }

  public void updateStatus(long sessionId, SessionStatus status) {
    sessions.computeIfPresent(
        sessionId,
        (id, state) ->
            new SessionState(
                state.sessionId(), state.nextExpectedSeq(), state.lastHeartbeat(), status));
  }

  public record RecoveryResult(boolean success, long expectedSeq, String message) {}

  public Optional<SessionState> getSession(long sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }
}
