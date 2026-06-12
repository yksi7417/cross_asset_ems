/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.ioi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The IOI service (task 9.4, [[arch-ioi]]): normalizes dealer Indications of Interest from any
 * network (AUTEX/BBG/Liquidnet/direct — pluggable sources) into one stream with the industry
 * qualifier semantics PRESERVED — a {@code NATURAL} (dealer owns the position) is a materially
 * stronger signal than {@code IN_TOUCH_WITH} (dealer is talking to someone), and downstream scoring
 * must see the difference. Client-segment visibility gates who sees what; the lifecycle (NEW /
 * CANCEL / REPLACE, expiry, traded-against) is clock-driven and deterministic.
 */
public final class IoiService {

  public enum Qualifier {
    NATURAL,
    SUPER_NATURAL,
    UNWOUND,
    IN_TOUCH_WITH,
    DELTA_HEDGED,
    PORTFOLIO_TRADE
  }

  public enum State {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    TRADED_AGAINST,
    REPLACED
  }

  /** One normalized IOI ([[arch-ioi]] § Data model). */
  public record Ioi(
      String ioiId,
      String sourceNetwork,
      String sourceMsgId,
      String dealer,
      String figi,
      int side,
      long qty,
      boolean qtyFirm,
      Qualifier qualifier,
      long validFromMillis,
      long validUntilMillis,
      Set<String> clientSegments,
      State state) {

    Ioi withState(State newState) {
      return new Ioi(
          ioiId,
          sourceNetwork,
          sourceMsgId,
          dealer,
          figi,
          side,
          qty,
          qtyFirm,
          qualifier,
          validFromMillis,
          validUntilMillis,
          clientSegments,
          newState);
    }
  }

  private final Map<String, Ioi> byId = new LinkedHashMap<>();
  private final Map<String, String> bySourceKey = new LinkedHashMap<>(); // network|msgId → ioiId
  private final Consumer<Ioi> events;
  private int seq = 0;

  public IoiService(Consumer<Ioi> events) {
    this.events = Objects.requireNonNull(events);
  }

  /** A network adapter delivers a NEW IOI. Returns the normalized record. */
  public synchronized Ioi onNew(
      String sourceNetwork,
      String sourceMsgId,
      String dealer,
      String figi,
      int side,
      long qty,
      boolean qtyFirm,
      Qualifier qualifier,
      long validFromMillis,
      long validUntilMillis,
      Set<String> clientSegments) {
    String ioiId = "IOI-" + ++seq;
    Ioi ioi =
        new Ioi(
            ioiId,
            sourceNetwork,
            sourceMsgId,
            dealer,
            figi,
            side,
            qty,
            qtyFirm,
            qualifier,
            validFromMillis,
            validUntilMillis,
            Set.copyOf(clientSegments),
            State.ACTIVE);
    byId.put(ioiId, ioi);
    bySourceKey.put(sourceNetwork + "|" + sourceMsgId, ioiId);
    events.accept(ioi);
    return ioi;
  }

  /** The network cancels its IOI (referenced by ITS id, not ours). */
  public synchronized Optional<Ioi> onCancel(String sourceNetwork, String sourceMsgId) {
    return transition(sourceNetwork, sourceMsgId, State.CANCELLED);
  }

  /** The network replaces an IOI: the old one is REPLACED, the new one arrives via onNew. */
  public synchronized Optional<Ioi> onReplace(String sourceNetwork, String sourceMsgId) {
    return transition(sourceNetwork, sourceMsgId, State.REPLACED);
  }

  /** An order traded against this IOI (the IOI→order linkage the TCA axe analysis wants). */
  public synchronized Optional<Ioi> tradedAgainst(String ioiId) {
    Ioi ioi = byId.get(ioiId);
    if (ioi == null || ioi.state() != State.ACTIVE) {
      return Optional.empty();
    }
    Ioi updated = ioi.withState(State.TRADED_AGAINST);
    byId.put(ioiId, updated);
    events.accept(updated);
    return Optional.of(updated);
  }

  /** Clock sweep: expire every ACTIVE IOI whose validity window has passed. */
  public synchronized void sweep(long nowMillis) {
    for (Map.Entry<String, Ioi> e : byId.entrySet()) {
      Ioi ioi = e.getValue();
      if (ioi.state() == State.ACTIVE && ioi.validUntilMillis() <= nowMillis) {
        Ioi expired = ioi.withState(State.EXPIRED);
        e.setValue(expired);
        events.accept(expired);
      }
    }
  }

  /**
   * The IOIs visible to {@code clientSegment} for {@code figi}, ACTIVE only, strongest qualifier
   * first (NATURAL beats IN_TOUCH_WITH — the enum declares strength order). Segment gating is hard:
   * an IOI scoped to a tier is INVISIBLE outside it, not greyed.
   */
  public synchronized List<Ioi> visibleTo(String clientSegment, String figi) {
    List<Ioi> out = new ArrayList<>();
    for (Ioi ioi : byId.values()) {
      if (ioi.state() == State.ACTIVE
          && ioi.figi().equals(figi)
          && (ioi.clientSegments().isEmpty() || ioi.clientSegments().contains(clientSegment))) {
        out.add(ioi);
      }
    }
    out.sort(
        java.util.Comparator.comparingInt((Ioi ioi) -> ioi.qualifier().ordinal())
            .thenComparing(Ioi::ioiId));
    return out;
  }

  public synchronized Optional<Ioi> find(String ioiId) {
    return Optional.ofNullable(byId.get(ioiId));
  }

  private Optional<Ioi> transition(String sourceNetwork, String sourceMsgId, State state) {
    String ioiId = bySourceKey.get(sourceNetwork + "|" + sourceMsgId);
    if (ioiId == null) {
      return Optional.empty();
    }
    Ioi ioi = byId.get(ioiId);
    if (ioi == null || ioi.state() != State.ACTIVE) {
      return Optional.empty();
    }
    Ioi updated = ioi.withState(state);
    byId.put(ioiId, updated);
    events.accept(updated);
    return Optional.of(updated);
  }
}
