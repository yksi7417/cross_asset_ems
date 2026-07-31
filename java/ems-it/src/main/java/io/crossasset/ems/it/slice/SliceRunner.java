/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.core.journal.DeterministicIds;
import io.crossasset.ems.core.journal.JournalEvent;
import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidationRequest;
import io.crossasset.ems.validator.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

  /**
   * Input event carrying a venue or client action against a live order.
   *
   * <p>One input type rather than one per FSM event: the journal names the FSM event in a field, so
   * adding a transition to the schema does not require a new event type here. The mapping is {@link
   * #toFsmEvent}, and an unrecognised name is a no-transition rather than a crash.
   */
  private static final String TYPE_ORDER_EVENT = "OrderEvent";

  /**
   * Output event recording every FSM transition taken.
   *
   * <p>This is what {@code conformance/harness/fsm_coverage.py} reads. Coverage is measured from
   * what the corpus actually exercised rather than inferred from the input, so a case that claims
   * to reach a transition and does not is caught.
   */
  private static final String TYPE_FSM_TRANSITION = "FsmTransition";

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
  private final SliceOrderBook orders = new SliceOrderBook();

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
        case TYPE_ORDER_NEW -> seq = onOrderNew(event, seq, output);
        case TYPE_ORDER_EVENT -> seq = onOrderEvent(event, seq, output);
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

  private long onOrderNew(JournalEvent event, long seq, List<JournalEvent> output) {
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

    // The FSM tracks the order under the CLIENT's identifier, not ours.
    //
    // ClOrdID exists before the order reaches us; OrderID is ours and is assigned
    // on acceptance. Keying the book on ClOrdID is both the FIX convention and
    // what lets a rejected order take the real PENDING_NEW -> REJECTED transition
    // without consuming an order id — the property component 3 established, and
    // which SliceMainTest.aRejectedOrderDoesNotConsumeAnIdentifier still guards.
    String clOrdId = field(event, "clOrdId");
    OrderFsmContext context = contextFor(clOrdId, event);

    if (validator.validate(request) instanceof ValidationResult.Reject reject) {
      seq = emitTransition(clOrdId, OrderFsmEvent.ValidationFailed, null, context, seq, output);
      output.add(rejectFrom(++seq, event, reject));
      return seq;
    }

    seq = emitTransition(clOrdId, OrderFsmEvent.ValidationPassed, null, context, seq, output);
    String orderId = ids.nextOrderId();

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("orderId", orderId);
    for (String key : ECHOED_FIELDS) {
      String value = event.fields().get(key);
      if (value != null) {
        fields.put(key, value);
      }
    }
    output.add(new JournalEvent(++seq, TYPE_ORDER_ACCEPTED, fields));
    return seq;
  }

  /**
   * Applies a venue or client action to a live order.
   *
   * <p>Every outcome is a journal event: a transition, a no-transition, or an unknown order. None
   * of the three is an exception — an order book fed by a venue must survive anything the venue
   * sends.
   */
  private long onOrderEvent(JournalEvent event, long seq, List<JournalEvent> output) {
    String clOrdId = field(event, "clOrdId");
    OrderFsmEvent fsmEvent = toFsmEvent(field(event, "event"));

    if (fsmEvent == null) {
      TreeMap<String, String> fields = new TreeMap<>();
      fields.put("clOrdId", clOrdId);
      fields.put("event", field(event, "event"));
      fields.put("reason", "unknown FSM event");
      output.add(new JournalEvent(++seq, "OrderEventIgnored", fields));
      return seq;
    }

    Optional<SliceOrderBook.Entry> entry = orders.get(clOrdId);
    if (entry.isEmpty()) {
      TreeMap<String, String> fields = new TreeMap<>();
      fields.put("clOrdId", clOrdId);
      fields.put("event", fsmEvent.name());
      fields.put("reason", "unknown order");
      output.add(new JournalEvent(++seq, "OrderEventIgnored", fields));
      return seq;
    }

    return emitTransition(
        clOrdId, fsmEvent, payloadFor(fsmEvent, event), entry.get().context(), seq, output);
  }

  /**
   * Applies a transition and records it.
   *
   * <p>A no-transition is recorded too, with {@code applied=false}. Dropping it would make a corpus
   * case that fed the FSM an event it ignored look identical to one that never sent the event — and
   * the difference is exactly what a reviewer needs to see.
   */
  private long emitTransition(
      String clOrdId,
      OrderFsmEvent fsmEvent,
      @Nullable Object payload,
      OrderFsmContext context,
      long seq,
      List<JournalEvent> output) {

    OrderFsmState before =
        orders.get(clOrdId).map(SliceOrderBook.Entry::state).orElse(OrderFsmState.PENDING_NEW);

    var result =
        orders.get(clOrdId).isEmpty()
            ? orders.open(clOrdId, context, fsmEvent)
            : orders.apply(clOrdId, fsmEvent, payload);

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("clOrdId", clOrdId);
    fields.put("event", fsmEvent.name());
    fields.put("fsm", "order");
    if (result.isEmpty() || result.get().isNoTransition()) {
      fields.put("applied", "false");
      fields.put("from", before.name());
      fields.put("to", before.name());
    } else {
      fields.put("applied", "true");
      fields.put("from", before.name());
      fields.put("to", result.get().newState().name());
    }
    output.add(new JournalEvent(++seq, TYPE_FSM_TRANSITION, fields));
    return seq;
  }

  /** Maps a journal event name to an FSM event. Null for anything the schema does not define. */
  private static @Nullable OrderFsmEvent toFsmEvent(String name) {
    for (OrderFsmEvent candidate : OrderFsmEvent.values()) {
      if (candidate.name().equals(name)) {
        return candidate;
      }
    }
    return null;
  }

  /** Builds the payload an FSM event needs, or null when it takes none. */
  private static @Nullable Object payloadFor(OrderFsmEvent fsmEvent, JournalEvent event) {
    long lastQty = parseLong(field(event, "lastQty"));
    long lastPx = parseLong(field(event, "lastPx"));
    String execId = field(event, "execId");
    return switch (fsmEvent) {
      case PartialFill -> SliceOrderBook.partialFill(lastQty, lastPx, execId);
      case FullFill -> SliceOrderBook.fullFill(lastQty, lastPx, execId);
      default -> null;
    };
  }

  private static long parseLong(String raw) {
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  /** The FSM context an order starts with, from the fields the journal carries. */
  private static OrderFsmContext contextFor(String clOrdId, JournalEvent event) {
    long qty = parseLong(field(event, "qty"));
    return new OrderFsmContext(
        clOrdId,
        clOrdId,
        null,
        field(event, "figi"),
        "SELL".equals(field(event, "side")) ? 2 : 1,
        qty,
        null,
        0L,
        qty,
        field(event, "account"),
        0,
        clOrdId,
        clOrdId,
        1L,
        null,
        null);
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
