/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.aaa.permission.AuthorizationResult;
import io.crossasset.ems.core.journal.DeterministicIds;
import io.crossasset.ems.core.journal.JournalEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The slice, as far as it has been built.
 *
 * <p><strong>Today it covers components 1–3</strong>: the journal codec, deterministic identifiers,
 * the transport seam and the AAA entitlement decision. A {@code SessionLogon} registers a session;
 * an {@code OrderNew} is checked against it and becomes either an {@code OrderAccepted} carrying a
 * generated order id or an {@code OrderRejected} carrying a catalog reject code. Everything else
 * passes through with its sequence renumbered, and a {@code RunSummary} closes the journal.
 *
 * <p>There is still no validation pipeline, no FSM, no routing and no venue — those are later
 * components, and pretending otherwise in the output would make the conformance corpus lie about
 * what is implemented.
 *
 * <p>Kept in lockstep with {@code rust/ems-slice/src/runner.rs} and {@code
 * cpp/ems-it/src/slice_runner.cpp}.
 */
public final class SliceRunner {

  /** Input event registering a session and its entitlements. */
  private static final String TYPE_SESSION_LOGON = "SessionLogon";

  /** Output event acknowledging a logon. */
  private static final String TYPE_SESSION_ACCEPTED = "SessionAccepted";

  /** Input event that opens an order. */
  private static final String TYPE_ORDER_NEW = "OrderNew";

  /** Output event acknowledging one. */
  private static final String TYPE_ORDER_ACCEPTED = "OrderAccepted";

  /** Output event refusing one. */
  private static final String TYPE_ORDER_REJECTED = "OrderRejected";

  /** Final output event: makes the seed and the input size visible in the journal itself. */
  private static final String TYPE_RUN_SUMMARY = "RunSummary";

  /** Catalog code: session ID unknown or already logged out. */
  private static final String CODE_SESSION_NOT_FOUND = "EMS-SES-1002";

  /**
   * Fields copied from {@code OrderNew} onto {@code OrderAccepted}, in this order.
   *
   * <p>An explicit list rather than "copy everything": an unknown field silently reaching the
   * output would be a divergence that only shows up once some other language's map happens to order
   * it differently.
   */
  private static final List<String> ECHOED_FIELDS =
      List.of("account", "figi", "price", "qty", "side");

  private final DeterministicIds ids;
  private final SliceAaa aaa = new SliceAaa();

  public SliceRunner(DeterministicIds ids) {
    this.ids = ids;
  }

  /** Runs the slice over {@code input}, returning the output journal. */
  public List<JournalEvent> run(List<JournalEvent> input) {
    List<JournalEvent> output = new ArrayList<>(input.size() + 1);
    long seq = 0;

    for (JournalEvent event : input) {
      switch (event.type()) {
        case TYPE_SESSION_LOGON -> output.add(onSessionLogon(event, ++seq));
        case TYPE_ORDER_NEW -> output.add(onOrderNew(event, ++seq));
        default -> output.add(event.withSeq(++seq));
      }
    }

    TreeMap<String, String> summary = new TreeMap<>();
    summary.put("events", Integer.toString(input.size()));
    summary.put("seed", Long.toString(ids.seed()));
    output.add(new JournalEvent(++seq, TYPE_RUN_SUMMARY, summary));

    return output;
  }

  private JournalEvent onSessionLogon(JournalEvent event, long seq) {
    long sessionId = parseSessionId(event);
    String firm = field(event, "firm");
    String desk = field(event, "desk");
    String user = field(event, "user");
    TreeSet<String> userTags = splitTags(field(event, "tags"));

    // firmTags / deskTags default to the user's tags, so the common case reads
    // as "this user may do these things" and the AND-gate passes. A case that
    // wants to exercise a firm- or desk-level denial states them explicitly.
    TreeSet<String> firmTags = tagsOrDefault(event, "firmTags", userTags);
    TreeSet<String> deskTags = tagsOrDefault(event, "deskTags", userTags);

    aaa.register(sessionId, firm, desk, user, userTags, firmTags, deskTags);

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("sessionId", Long.toString(sessionId));
    fields.put("user", user);
    // The granted tags are echoed so a corpus case can show *why* a later
    // rejection happened without the reader having to re-read the input.
    fields.put("tags", String.join(",", userTags));
    fields.put("firmTags", String.join(",", firmTags));
    fields.put("deskTags", String.join(",", deskTags));
    return new JournalEvent(seq, TYPE_SESSION_ACCEPTED, fields);
  }

  /** Tags from {@code key}, or {@code fallback} when the field is absent. */
  private static TreeSet<String> tagsOrDefault(
      JournalEvent event, String key, TreeSet<String> fallback) {
    String raw = event.fields().get(key);
    return raw == null ? fallback : splitTags(raw);
  }

  private JournalEvent onOrderNew(JournalEvent event, long seq) {
    long sessionId = parseSessionId(event);
    Optional<Session> session = aaa.session(sessionId);

    if (session.isEmpty()) {
      return reject(
          seq,
          event,
          CODE_SESSION_NOT_FOUND,
          "SES",
          "Session " + sessionId + " not found or has expired.");
    }

    String tag = field(event, "tag");
    // The production three-layer AND-gate decides this, and its reject code
    // names the outermost missing layer: firm (1003), desk (1002) or user (1001).
    if (aaa.authorize(session.get(), tag) instanceof AuthorizationResult.Deny deny) {
      return reject(seq, event, deny.rejectCode(), "PRM", deny.message());
    }

    // Only an accepted order consumes an identifier. If a rejected one did, the
    // ids in a journal would depend on how many orders failed — and every corpus
    // case downstream of a rejection would shift.
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("orderId", ids.nextOrderId());
    for (String key : ECHOED_FIELDS) {
      String value = event.fields().get(key);
      if (value != null) {
        fields.put(key, value);
      }
    }
    return new JournalEvent(seq, TYPE_ORDER_ACCEPTED, fields);
  }

  private static JournalEvent reject(
      long seq, JournalEvent event, String code, String category, String reason) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("category", category);
    fields.put("code", code);
    fields.put("reason", reason);
    fields.put("sessionId", field(event, "sessionId"));
    return new JournalEvent(seq, TYPE_ORDER_REJECTED, fields);
  }

  /**
   * A missing or non-numeric session id becomes {@code -1}, which no session can hold, so the order
   * is rejected as "session not found" rather than crashing the run. Malformed data on the wire is
   * a rejection, not a defect.
   */
  private static long parseSessionId(JournalEvent event) {
    try {
      return Long.parseLong(field(event, "sessionId"));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String field(JournalEvent event, String key) {
    String value = event.fields().get(key);
    return value == null ? "" : value;
  }

  /** Splits a comma-separated tag list. Empty entries are dropped; order comes from the set. */
  private static TreeSet<String> splitTags(String raw) {
    TreeSet<String> tags = new TreeSet<>();
    if (raw.isEmpty()) {
      return tags;
    }
    Arrays.stream(raw.split(",", -1))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(tags::add);
    return tags;
  }
}
