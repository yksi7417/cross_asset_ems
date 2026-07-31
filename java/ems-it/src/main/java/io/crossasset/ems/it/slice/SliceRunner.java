/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.core.journal.DeterministicIds;
import io.crossasset.ems.core.journal.JournalEvent;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidationRequest;
import io.crossasset.ems.validator.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

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

  /** Input event adding an instrument to the security master. */
  private static final String TYPE_INSTRUMENT_CREATED = "InstrumentCreated";

  /** Output event acknowledging one. */
  private static final String TYPE_INSTRUMENT_ACCEPTED = "InstrumentAccepted";

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
  private final SliceSecurityMaster securityMaster = new SliceSecurityMaster();

  /**
   * The production pipeline, wired with the production collaborators.
   *
   * <p>SESSION, IDENTITY, REFERENCE and PERMISSION all live here now. The runner used to check the
   * session and the entitlement itself; that was the validator's job all along, and doing it here
   * meant the ports were mirroring the runner rather than the pipeline.
   */
  private final LayeredValidatorPipeline validator =
      new LayeredValidatorPipeline(aaa.aaaService(), securityMaster.service(), aaa.evaluator());

  public SliceRunner(DeterministicIds ids) {
    this.ids = ids;
  }

  /** Runs the slice over {@code input}, returning the output journal. */
  public List<JournalEvent> run(List<JournalEvent> input) {
    List<JournalEvent> output = new ArrayList<>(input.size() + 1);
    long seq = 0;

    for (JournalEvent event : input) {
      switch (event.type()) {
        case TYPE_INSTRUMENT_CREATED -> output.add(onInstrumentCreated(event, ++seq));
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

  /**
   * Adds an instrument to the security master.
   *
   * <p>An unparseable status is {@code UNKNOWN}, which is not active — so a malformed instrument
   * makes orders on it fail the REFERENCE layer rather than making the run fail.
   */
  private JournalEvent onInstrumentCreated(JournalEvent event, long seq) {
    String figi = field(event, "figi");
    LifecycleStatus status = parseStatus(field(event, "status"));
    securityMaster.add(figi, status);

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("figi", figi);
    fields.put("status", status.name());
    return new JournalEvent(seq, TYPE_INSTRUMENT_ACCEPTED, fields);
  }

  private static LifecycleStatus parseStatus(String raw) {
    for (LifecycleStatus candidate : LifecycleStatus.values()) {
      if (candidate.name().equals(raw)) {
        return candidate;
      }
    }
    return LifecycleStatus.UNKNOWN;
  }

  private JournalEvent onOrderNew(JournalEvent event, long seq) {
    // The whole decision is the pipeline's. SESSION, IDENTITY, REFERENCE and
    // PERMISSION run in that fixed order and the first failure short-circuits,
    // so the reject the journal carries names the outermost thing that was
    // wrong — which is the one worth telling a trader about.
    String figi = field(event, "figi");
    ValidationRequest request =
        new ValidationRequest(
            field(event, "clOrdId"),
            parseSessionId(event),
            emptyToNull(field(event, "tag")),
            emptyToNull(figi));

    if (validator.validate(request) instanceof ValidationResult.Reject reject) {
      return rejectFrom(seq, event, reject);
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

  /**
   * Turns a pipeline rejection into a journal event.
   *
   * <p>The layer is carried explicitly. Two rejections can share a code and differ in which layer
   * produced them once later layers land, and a journal that omits it would make those two
   * indistinguishable on replay.
   */
  private static JournalEvent rejectFrom(
      long seq, JournalEvent event, ValidationResult.Reject reject) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("category", reject.category());
    fields.put("code", reject.code());
    fields.put("layer", reject.layer().name());
    fields.put("reason", reject.message());
    fields.put("sessionId", field(event, "sessionId"));
    String adminHint = reject.adminHint();
    if (adminHint != null) {
      fields.put("adminHint", adminHint);
    }
    return new JournalEvent(seq, TYPE_ORDER_REJECTED, fields);
  }

  /** The pipeline treats null as "not supplied"; the journal has no null, only absent. */
  private static @Nullable String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
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
