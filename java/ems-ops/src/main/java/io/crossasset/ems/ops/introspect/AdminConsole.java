/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.introspect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The privileged write surface (task 14.2, arch-jmx-introspection § Write surface): event
 * injection, state overrides, sequence resets — explicit, typed, security-gated operations, never a
 * remote shell. Every call requires the {@code #superuser-inject} tag (checked through the
 * three-layer AND-gate seam), a written rationale, and is rate-limited per identity; every attempt
 * — granted or denied — lands on the admin journal as an {@code AdminAction} for the audit stream.
 * Four-eyes enforcement, where firm policy demands it, composes via the 10.5 override service in
 * front of this console.
 */
public final class AdminConsole {

  /** Qualification seam (5.3 AND-gate in production). */
  @FunctionalInterface
  public interface TagChecker {
    boolean hasTag(String firm, String desk, String user, String tag);
  }

  /** A component that accepts privileged commands (implemented alongside Introspectable). */
  public interface InjectionTarget {
    String componentId();

    /** Apply one admin action; returns a human-readable result for the journal. */
    String inject(String action, Map<String, String> args);
  }

  /** One privileged request. */
  public record AdminRequest(
      String firm,
      String desk,
      String user,
      String componentId,
      String action,
      Map<String, String> args,
      String rationale) {}

  /** Outcome: granted with the target's result, or denied with the reason. */
  public sealed interface AdminResult {
    record Granted(String result) implements AdminResult {}

    record Denied(String reason) implements AdminResult {}
  }

  /** The audited record of every attempt. */
  public record AdminAction(
      String user,
      String action,
      String target,
      String rationale,
      long atMillis,
      boolean granted,
      String outcome) {}

  /** Tag required for any write operation. */
  public static final String SUPERUSER_TAG = "#superuser-inject";

  private final TagChecker tags;
  private final LongSupplier clockMillis;
  private final int maxWritesPerMinute;
  private final ConcurrentHashMap<String, InjectionTarget> targets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();
  private final List<AdminAction> journal =
      java.util.Collections.synchronizedList(new ArrayList<>());

  public AdminConsole(TagChecker tags, LongSupplier clockMillis, int maxWritesPerMinute) {
    this.tags = Objects.requireNonNull(tags, "tags");
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    this.maxWritesPerMinute = maxWritesPerMinute;
  }

  /** Register a component's injection target (alongside its Introspectable registration). */
  public void register(InjectionTarget target) {
    targets.put(target.componentId(), target);
  }

  /** Execute one privileged operation. Every attempt is journaled. */
  public AdminResult execute(AdminRequest request) {
    long now = clockMillis.getAsLong();
    String denial = deny(request, now);
    if (denial != null) {
      journal.add(
          new AdminAction(
              request.user(),
              request.action(),
              request.componentId(),
              request.rationale(),
              now,
              false,
              denial));
      return new AdminResult.Denied(denial);
    }
    String result = targets.get(request.componentId()).inject(request.action(), request.args());
    journal.add(
        new AdminAction(
            request.user(),
            request.action(),
            request.componentId(),
            request.rationale(),
            now,
            true,
            result));
    return new AdminResult.Granted(result);
  }

  /** Immutable snapshot of the admin journal (the AdminAction audit stream). */
  public List<AdminAction> journal() {
    synchronized (journal) {
      return List.copyOf(journal);
    }
  }

  private @org.jspecify.annotations.Nullable String deny(AdminRequest request, long now) {
    if (!targets.containsKey(request.componentId())) {
      return "Unknown component: " + request.componentId() + ".";
    }
    if (!tags.hasTag(request.firm(), request.desk(), request.user(), SUPERUSER_TAG)) {
      return "Identity lacks " + SUPERUSER_TAG + " (EMS-PRM-3001).";
    }
    if (request.rationale() == null || request.rationale().isBlank()) {
      return "Write operations require a rationale.";
    }
    Deque<Long> window = rateWindows.computeIfAbsent(request.user(), k -> new ArrayDeque<>());
    synchronized (window) {
      while (!window.isEmpty() && now - window.peekFirst() >= 60_000L) {
        window.removeFirst();
      }
      if (window.size() + 1 > maxWritesPerMinute) {
        return "Rate limit: more than " + maxWritesPerMinute + " admin writes per minute.";
      }
      window.addLast(now);
    }
    return null;
  }
}
