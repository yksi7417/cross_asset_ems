/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.quote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Quote multicast over Aeron (task 9.3, [[arch-quote-server]] § Transport +
 * [[arch-sbe-aeron-transport]]): the hot path is a MULTICAST TAIL — fire-and-forget UDP multicast,
 * kernel-level fan-out, no per-subscriber state — and the catch-up path is UNICAST REPLAY from a
 * sequence-indexed retention ring (a subscriber that gapped asks for {@code from = lastSeq + 1},
 * same cursor contract as every other stream in this system).
 *
 * <p>The {@link Tail} seam carries the bytes: {@link #aeronChannel} builds the real Aeron multicast
 * URI for the wire binding (exercised by the phase-0 Aeron smoke infrastructure); {@link
 * LoopbackTail} is the in-process binding for tests and single-box deployments. Sequence stamping,
 * retention, and gap replay live HERE so every binding shares them — replay determinism does not
 * depend on the wire.
 */
public final class QuoteMulticast {

  /** The wire seam: deliver one framed message (already sequence-stamped). */
  @FunctionalInterface
  public interface Tail {
    void send(long seq, QuoteServer.QuoteUpdate update);
  }

  /** An in-process tail: delivers synchronously to attached listeners (tests, single box). */
  public static final class LoopbackTail implements Tail {
    private final List<QuoteServer.QuoteListener> listeners = new ArrayList<>();
    private boolean dropping; // simulate multicast loss in tests

    public void attach(QuoteServer.QuoteListener listener) {
      listeners.add(listener);
    }

    public void setDropping(boolean dropping) {
      this.dropping = dropping;
    }

    @Override
    public void send(long seq, QuoteServer.QuoteUpdate update) {
      if (dropping) {
        return; // the tail is best-effort: loss is normal, replay is the remedy
      }
      for (QuoteServer.QuoteListener listener : listeners) {
        listener.onQuote(update);
      }
    }
  }

  /**
   * The real Aeron multicast channel URI for a quote stream, per the transport conventions: {@code
   * aeron:udp?endpoint={group}:{port}|interface={iface}|ttl={ttl}}. Stream id derives from the
   * topic so one media driver serves many topics.
   */
  public static String aeronChannel(
      String multicastGroup, int port, String networkInterface, int ttl) {
    return "aeron:udp?endpoint="
        + multicastGroup
        + ":"
        + port
        + "|interface="
        + networkInterface
        + "|ttl="
        + ttl;
  }

  /** Deterministic Aeron stream id for a topic (Aeron stream ids are int-typed). */
  public static int streamIdFor(String topic) {
    return Math.floorMod(topic.hashCode(), 1_000_000) + 1_000; // keep clear of reserved ids
  }

  private final Tail tail;
  private final int retention;
  private final Map<String, List<Framed>> ringByTopic = new LinkedHashMap<>();
  private final Map<String, Long> seqByTopic = new LinkedHashMap<>();

  private record Framed(long seq, QuoteServer.QuoteUpdate update) {}

  /**
   * @param retention messages retained per topic for unicast replay (the catch-up window)
   */
  public QuoteMulticast(Tail tail, int retention) {
    this.tail = Objects.requireNonNull(tail);
    this.retention = retention;
  }

  /** Publish to the tail, stamping the per-topic sequence and retaining for replay. */
  public synchronized long publish(QuoteServer.QuoteUpdate update) {
    long seq = seqByTopic.merge(update.topic(), 1L, Long::sum);
    List<Framed> ring = ringByTopic.computeIfAbsent(update.topic(), k -> new ArrayList<>());
    ring.add(new Framed(seq, update));
    if (ring.size() > retention) {
      ring.remove(0);
    }
    tail.send(seq, update);
    return seq;
  }

  /** The current sequence on a topic (subscribers detect gaps against it). */
  public synchronized long currentSeq(String topic) {
    return seqByTopic.getOrDefault(topic, 0L);
  }

  /**
   * Unicast replay: everything retained on {@code topic} from {@code fromSeq} (inclusive), in order
   * — the gap remedy. Empty when the gap is OLDER than retention: the subscriber must re-image from
   * {@link QuoteServer#snapshot} instead (and the empty result says so loudly).
   */
  public synchronized Optional<List<QuoteServer.QuoteUpdate>> replay(String topic, long fromSeq) {
    List<Framed> ring = ringByTopic.getOrDefault(topic, List.of());
    if (!ring.isEmpty() && ring.get(0).seq() > fromSeq) {
      return Optional.empty(); // fell off retention — snapshot re-image required
    }
    List<QuoteServer.QuoteUpdate> out = new ArrayList<>();
    for (Framed framed : ring) {
      if (framed.seq() >= fromSeq) {
        out.add(framed.update());
      }
    }
    return Optional.of(out);
  }
}
