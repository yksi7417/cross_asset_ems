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
import io.crossasset.ems.fsm.generated.RouteFsmContext;
import io.crossasset.ems.fsm.generated.RouteFsmEffect;
import io.crossasset.ems.fsm.generated.RouteFsmEvent;
import io.crossasset.ems.fsm.generated.RouteFsmPayloads;
import io.crossasset.ems.fsm.generated.RouteFsmState;
import io.crossasset.ems.fsm.generated.VenueSessionFsmEvent;
import io.crossasset.ems.fsm.generated.VenueSessionFsmState;
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
 * <p><strong>Today it covers components 1–7</strong>: the journal codec, deterministic identifiers,
 * the transport seam, the AAA entitlement decision, the layered validation pipeline, the order FSM,
 * routing, and the venue edge. A {@code SessionLogon} registers a session; an {@code OrderNew}
 * becomes an {@code OrderAccepted} or an {@code OrderRejected}; an {@code OrderEvent} drives the
 * order FSM; a {@code RouteNew} projects an accepted order onto a venue — refused with {@code
 * EMS-VEN-5001} unless that venue's FIX session is {@code ACTIVE}, and emitting a {@code FixOut}
 * when accepted; a {@code VenueSession} drives the session FSM; an inbound {@code ExecutionReport}
 * is translated by {@code ExecType} into a route event and <strong>cascades to the order
 * FSM</strong> via the schema's {@code emit_event} effects.
 *
 * <p>An {@code Allocate} distributes an order's filled quantity across accounts by the
 * largest-remainder method, reading {@code cumQty} from the order FSM's own context. <strong>With
 * that, the slice is complete</strong>: every component of the cash-equity order path in ADR 0002's
 * scope is implemented and conformance-checked in all three languages. {@code FixOut} records
 * intent and identifying tags, not a wire-format FIX string — a byte-exact encoder stays out of
 * scope by decision, not omission.
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

  /** Input event projecting an accepted order onto one venue. */
  private static final String TYPE_ROUTE_NEW = "RouteNew";

  /** Output event acknowledging a route. */
  private static final String TYPE_ROUTE_ACCEPTED = "RouteAccepted";

  /** Output event refusing one. */
  private static final String TYPE_ROUTE_REJECTED = "RouteRejected";

  /**
   * Input event carrying a venue action against a live route.
   *
   * <p>The route counterpart of {@code OrderEvent}, and named the same way for the same reason: the
   * journal carries the FSM event name in a field, so a transition added to the schema needs no new
   * event type here.
   */
  private static final String TYPE_ROUTE_EVENT = "RouteEvent";

  /** Output event for a route action that reached no route. */
  private static final String TYPE_ROUTE_EVENT_IGNORED = "RouteEventIgnored";

  /** Input event driving one venue's FIX session. */
  private static final String TYPE_VENUE_SESSION = "VenueSession";

  /**
   * Output event: the outbound FIX message a dispatched route produces.
   *
   * <p>The journal records that a message was produced and its identifying tags, not a wire-format
   * string. A byte-exact FIX encoder is a component of its own; recording the intent here keeps the
   * conformance gate meaningful without three languages having to agree on tag ordering and
   * checksums as well.
   */
  private static final String TYPE_FIX_OUT = "FixOut";

  /** Input event: an inbound FIX ExecutionReport from a venue. */
  private static final String TYPE_EXECUTION_REPORT = "ExecutionReport";

  /** Output event for an ExecutionReport that reached no route. */
  private static final String TYPE_EXECUTION_REPORT_IGNORED = "ExecutionReportIgnored";

  /** Input event distributing an order's filled quantity across accounts. */
  private static final String TYPE_ALLOCATE = "Allocate";

  /** Output event: one account's share of a fill. */
  private static final String TYPE_ALLOCATION_RECORD = "AllocationRecord";

  /** Output event refusing an allocation. */
  private static final String TYPE_ALLOCATION_REJECTED = "AllocationRejected";

  /** Final output event: makes the seed and the input size visible in the journal itself. */
  private static final String TYPE_RUN_SUMMARY = "RunSummary";

  /** Catalog code: session ID unknown or already logged out. */
  private static final String CODE_SESSION_NOT_FOUND = "EMS-SES-1002";

  /** Catalog code: no such order to route. */
  private static final String CODE_ROUTE_UNKNOWN_ORDER = "EMS-RTE-4001";

  /** Catalog code: the order is not in a state that can be routed. */
  private static final String CODE_ROUTE_ORDER_NOT_ROUTABLE = "EMS-RTE-4002";

  /** Catalog code: the requested quantity is not routable against what is left. */
  private static final String CODE_ROUTE_QTY_INVALID = "EMS-RTE-4003";

  /** Catalog code: the route's ClOrdID is already in use. */
  private static final String CODE_ROUTE_CLORDID_COLLISION = "EMS-RTE-2005";

  /** Catalog code: the venue's FIX session cannot currently take an order. */
  private static final String CODE_VENUE_SESSION_NOT_ACTIVE = "EMS-VEN-5001";

  /** Catalog code: no such order to allocate. */
  private static final String CODE_ALLOC_UNKNOWN_ORDER = "EMS-ALC-6001";

  /** Catalog code: the order has no filled quantity to allocate. */
  private static final String CODE_ALLOC_NOTHING_FILLED = "EMS-ALC-6002";

  /** Catalog code: the share list is empty, malformed, or sums to nothing. */
  private static final String CODE_ALLOC_BAD_SHARES = "EMS-ALC-6003";

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
  private final SliceRouteBook routes = new SliceRouteBook();
  private final SliceVenueSessions venues = new SliceVenueSessions();

  /**
   * The order id we assigned to each accepted order, by the client's ClOrdID.
   *
   * <p>A runner-level index rather than a field on the order book: the FSM context is the schema's
   * shape and this is ours. A rejected order has no entry here — the same property {@code
   * aRejectedOrderDoesNotConsumeAnIdentifier} pins — so a route can never be hung off an order id
   * that was never issued.
   */
  private final TreeMap<String, String> orderIds = new TreeMap<>();

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
        case TYPE_ROUTE_NEW -> seq = onRouteNew(event, seq, output);
        case TYPE_ROUTE_EVENT -> seq = onRouteEvent(event, seq, output);
        case TYPE_VENUE_SESSION -> seq = onVenueSession(event, seq, output);
        case TYPE_EXECUTION_REPORT -> seq = onExecutionReport(event, seq, output);
        case TYPE_ALLOCATE -> seq = onAllocate(event, seq, output);
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
    orderIds.put(clOrdId, orderId);
    return seq;
  }

  /**
   * Projects an accepted order onto one venue.
   *
   * <p>Four refusals, checked in a fixed order with the first one winning, exactly as the
   * validation pipeline short-circuits: unknown order, order not routable, quantity not routable,
   * ClOrdID already taken. Fixed order matters because a request can fail two of them at once and
   * the journal must say the same thing in all three languages.
   *
   * <p><strong>A refused route creates nothing.</strong> There is no {@code FsmTransition} on this
   * path, and that asymmetry with {@code OrderNew} is the schema's, not a shortcut: {@code
   * order.fsm.yaml} models validation failure as a real {@code PENDING_NEW -> REJECTED} transition,
   * while {@code route.fsm.yaml} has no transition out of {@code PENDING} except {@code RouteSent}.
   * Emitting one would mean inventing a transition the schema does not define — and {@code
   * fsm-coverage} would then count a transition that does not exist.
   */
  private long onRouteNew(JournalEvent event, long seq, List<JournalEvent> output) {
    String clOrdId = field(event, "clOrdId");
    String venueMic = field(event, "venueMic");
    long qty = parseLong(field(event, "qty"));

    // The venue gate runs first. A route to a venue that cannot take it is refused
    // before any of the order-side checks, because the answer does not depend on
    // them — and telling a trader "your order is fine, the venue is down" is more
    // useful than a quantity complaint.
    if (!venues.isActive(venueMic)) {
      return rejectRoute(
          event,
          seq,
          output,
          CODE_VENUE_SESSION_NOT_ACTIVE,
          "venue session is "
              + venues.stateOf(venueMic).map(VenueSessionFsmState::name).orElse("never connected"));
    }

    Optional<SliceOrderBook.Entry> parent = orders.get(clOrdId);
    if (parent.isEmpty()) {
      return rejectRoute(event, seq, output, CODE_ROUTE_UNKNOWN_ORDER, "no such order");
    }
    // A REJECTED order is in the book and lands here, not on 4001 — it exists,
    // it just cannot take quantity. "your order was rejected" is a more useful
    // thing to tell a trader than "we have never heard of it".
    if (!isRoutable(parent.get().state())) {
      return rejectRoute(
          event,
          seq,
          output,
          CODE_ROUTE_ORDER_NOT_ROUTABLE,
          "order is " + parent.get().state().name());
    }
    // Unreachable: a routable state is only reached after acceptance, and
    // acceptance is what fills orderIds. Handled rather than asserted, because
    // this runs against a journal a venue wrote.
    String orderId = orderIds.get(clOrdId);
    if (orderId == null) {
      return rejectRoute(
          event, seq, output, CODE_ROUTE_ORDER_NOT_ROUTABLE, "order has no identifier");
    }

    long orderQty = parent.get().context().orderQty();
    long alreadyRouted = routes.routedQty(orderId);
    if (qty == 0 || alreadyRouted + qty > orderQty) {
      return rejectRoute(
          event,
          seq,
          output,
          CODE_ROUTE_QTY_INVALID,
          "qty " + qty + " not routable against " + (orderQty - alreadyRouted) + " remaining");
    }

    // Absent, the route's ClOrdID is derived from the parent's and the count of
    // routes already hung off it: C-A-1, C-A-2. Derived rather than taken from
    // the route id so that the check below can run BEFORE an id is consumed —
    // the same "a refusal costs nothing" property the order path has.
    String routeClOrdId = field(event, "routeClOrdId");
    if (routeClOrdId.isEmpty()) {
      routeClOrdId = clOrdId + "-" + (routes.countForOrder(orderId) + 1);
    }
    if (routes.hasClOrdId(routeClOrdId)) {
      return rejectRoute(
          event, seq, output, CODE_ROUTE_CLORDID_COLLISION, "ClOrdID " + routeClOrdId + " in use");
    }

    String routeId = ids.nextRouteId();
    Long price =
        emptyToNull(field(event, "price")) == null ? null : parseLong(field(event, "price"));
    RouteFsmContext context =
        new RouteFsmContext(
            routeId,
            orderId,
            routeClOrdId,
            null,
            venueMic,
            parent.get().context().instrumentId(),
            parent.get().context().side(),
            qty,
            price,
            0L,
            qty,
            1L,
            orderId,
            null);

    var result = routes.open(routeId, context);

    TreeMap<String, String> transition = new TreeMap<>();
    transition.put("applied", Boolean.toString(!result.get().isNoTransition()));
    transition.put("event", RouteFsmEvent.RouteSent.name());
    transition.put("from", RouteFsmState.PENDING.name());
    transition.put("fsm", "route");
    transition.put("routeId", routeId);
    // Read back from the book rather than taken from the transition result: the
    // journal should report the state the slice is actually holding.
    transition.put("to", routes.stateOf(routeId).orElse(RouteFsmState.PENDING).name());
    output.add(new JournalEvent(++seq, TYPE_FSM_TRANSITION, transition));

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("clOrdId", clOrdId);
    fields.put("orderId", orderId);
    fields.put("qty", Long.toString(qty));
    fields.put("routeClOrdId", routeClOrdId);
    fields.put("routeId", routeId);
    fields.put("venueMic", venueMic);
    if (price != null) {
      fields.put("price", Long.toString(price));
    }
    output.add(new JournalEvent(++seq, TYPE_ROUTE_ACCEPTED, fields));

    // 35=D goes out last: the journal reads in the order things happened, and the
    // message is a consequence of the route existing rather than the cause of it.
    TreeMap<String, String> fix = new TreeMap<>();
    fix.put("clOrdId", routeClOrdId);
    fix.put("msgType", "D");
    fix.put("orderQty", Long.toString(qty));
    fix.put("side", Integer.toString(parent.get().context().side()));
    fix.put("symbol", parent.get().context().instrumentId());
    fix.put("venueMic", venueMic);
    if (price != null) {
      fix.put("price", Long.toString(price));
    }
    output.add(new JournalEvent(++seq, TYPE_FIX_OUT, fix));
    return seq;
  }

  /** One account's claim on a fill: {@code weightBps} out of the total weight. */
  private record Share(String account, long weightBps) {}

  /**
   * Distributes an order's filled quantity across accounts.
   *
   * <p><strong>Largest-remainder method.</strong> Each account gets the floor of its proportional
   * share; the lots lost to flooring go one each to the accounts with the largest division
   * remainders, ties broken by larger weight then instruction order. Floors alone under-allocate,
   * and naive rounding can over-allocate — this is the standard way to make the parts sum exactly
   * to the whole, and the tie-break rules are what make three languages produce identical bytes.
   *
   * <p>Shares arrive inline as {@code "ACC1:5000,ACC2:3000"} — account and weight in basis points.
   * The weights need not sum to 10000: they are relative, and requiring a fixed total would just
   * push the arithmetic onto whoever writes the instruction.
   */
  private long onAllocate(JournalEvent event, long seq, List<JournalEvent> output) {
    String clOrdId = field(event, "clOrdId");

    Optional<SliceOrderBook.Entry> parent = orders.get(clOrdId);
    String orderId = orderIds.get(clOrdId);
    if (parent.isEmpty() || orderId == null) {
      return rejectAllocation(event, seq, output, CODE_ALLOC_UNKNOWN_ORDER, "no such order");
    }

    // cumQty is maintained by the order FSM's own update_context effects on
    // fills, so what is allocated here is what the machine says was executed —
    // not a number re-derived from the fill events by a second code path.
    long filled = parent.get().context().cumQty();
    if (filled <= 0) {
      return rejectAllocation(
          event, seq, output, CODE_ALLOC_NOTHING_FILLED, "order has no filled quantity");
    }

    List<Share> shares = parseShares(field(event, "shares"));
    long totalWeight = shares.stream().mapToLong(Share::weightBps).sum();
    if (shares.isEmpty() || totalWeight <= 0) {
      return rejectAllocation(
          event, seq, output, CODE_ALLOC_BAD_SHARES, "shares are empty or sum to nothing");
    }

    // Floor pass: every account gets its proportional floor, and the remainder
    // of each division is kept to decide who absorbs the lots flooring lost.
    int n = shares.size();
    long[] qty = new long[n];
    long[] remainder = new long[n];
    long allocated = 0;
    for (int i = 0; i < n; i++) {
      long numerator = filled * shares.get(i).weightBps();
      qty[i] = numerator / totalWeight;
      remainder[i] = numerator % totalWeight;
      allocated += qty[i];
    }

    // Residual pass: largest remainder first, ties by larger weight then by
    // instruction order. Every rule here is load-bearing — an unstated tie-break
    // is a divergence waiting for the first corpus case that hits it.
    long residual = filled - allocated;
    Integer[] order = new Integer[n];
    for (int i = 0; i < n; i++) {
      order[i] = i;
    }
    java.util.Arrays.sort(
        order,
        java.util.Comparator.<Integer>comparingLong(i -> remainder[i])
            .reversed()
            .thenComparing(
                java.util.Comparator.<Integer>comparingLong(i -> shares.get(i).weightBps())
                    .reversed())
            .thenComparingInt(i -> i));
    for (int k = 0; k < residual; k++) {
      qty[order[(int) (k % (long) n)]]++;
    }

    // One record per account, in instruction order. The venue's fill arrived as
    // one quantity; this is the slice's answer to whose it is.
    for (int i = 0; i < n; i++) {
      TreeMap<String, String> fields = new TreeMap<>();
      fields.put("account", shares.get(i).account());
      fields.put("clOrdId", clOrdId);
      fields.put("orderId", orderId);
      fields.put("qty", Long.toString(qty[i]));
      fields.put("weightBps", Long.toString(shares.get(i).weightBps()));
      output.add(new JournalEvent(++seq, TYPE_ALLOCATION_RECORD, fields));
    }
    return seq;
  }

  /** Parses {@code "ACC1:5000,ACC2:3000"}. Malformed entries are dropped, not fatal. */
  private static List<Share> parseShares(String raw) {
    List<Share> shares = new ArrayList<>();
    if (raw.isEmpty()) {
      return shares;
    }
    for (String part : raw.split(",", -1)) {
      int colon = part.indexOf(':');
      if (colon <= 0) {
        continue;
      }
      String account = part.substring(0, colon).trim();
      long weight;
      try {
        weight = Long.parseLong(part.substring(colon + 1).trim());
      } catch (NumberFormatException e) {
        continue;
      }
      if (!account.isEmpty() && weight > 0) {
        shares.add(new Share(account, weight));
      }
    }
    return shares;
  }

  /** Refuses an allocation. Nothing is recorded against any account. */
  private long rejectAllocation(
      JournalEvent event, long seq, List<JournalEvent> output, String code, String reason) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("clOrdId", field(event, "clOrdId"));
    fields.put("code", code);
    fields.put("reason", reason);
    output.add(new JournalEvent(++seq, TYPE_ALLOCATION_REJECTED, fields));
    return seq;
  }

  /**
   * Drives one venue's FIX session.
   *
   * <p>A venue we have never heard of starts in the schema's initial state rather than being
   * refused, so {@code ConnectRequested} is reachable by a corpus case. Unknown event names are
   * recorded as no-transitions rather than being dropped: a session that ignored something is a
   * fact an operator needs.
   */
  private long onVenueSession(JournalEvent event, long seq, List<JournalEvent> output) {
    String venueMic = field(event, "venueMic");
    String raw = field(event, "event");

    VenueSessionFsmEvent fsmEvent = toVenueSessionEvent(raw);
    if (fsmEvent == null) {
      TreeMap<String, String> fields = new TreeMap<>();
      fields.put("event", raw);
      fields.put("reason", "unknown FSM event");
      fields.put("venueMic", venueMic);
      output.add(new JournalEvent(++seq, "VenueSessionEventIgnored", fields));
      return seq;
    }

    VenueSessionFsmState before =
        venues.stateOf(venueMic).orElse(VenueSessionFsmState.DISCONNECTED);
    var result = venues.apply(venueMic, fsmEvent);

    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("applied", Boolean.toString(!result.isNoTransition()));
    fields.put("event", fsmEvent.name());
    fields.put("from", before.name());
    fields.put("fsm", "venue_session");
    fields.put("to", venues.stateOf(venueMic).orElse(before).name());
    fields.put("venueMic", venueMic);
    output.add(new JournalEvent(++seq, TYPE_FSM_TRANSITION, fields));
    return seq;
  }

  /** Maps a journal event name to a venue-session FSM event. Null for anything the schema omits. */
  private static @Nullable VenueSessionFsmEvent toVenueSessionEvent(String name) {
    for (VenueSessionFsmEvent candidate : VenueSessionFsmEvent.values()) {
      if (candidate.name().equals(name)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Translates an inbound FIX ExecutionReport into a route event and applies it.
   *
   * <p>This is the whole venue edge in one method: FIX vocabulary on the way in, the slice's own
   * vocabulary on the way out. {@code ExecType} is the discriminator, and {@code OrdStatus}
   * separates the two cases that share one — a trade is a partial fill or the final one depending
   * on whether anything is left.
   *
   * <p>The report names a route by <strong>ClOrdID</strong>, because that is what a venue knows. It
   * has never seen our route id.
   */
  private long onExecutionReport(JournalEvent event, long seq, List<JournalEvent> output) {
    String clOrdId = field(event, "clOrdId");
    String execType = field(event, "execType");

    RouteFsmEvent fsmEvent = fromExecType(execType, field(event, "ordStatus"));
    if (fsmEvent == null) {
      return ignoreReport(seq, clOrdId, execType, "unmapped ExecType", output);
    }

    String routeId = routes.routeIdForClOrdId(clOrdId).orElse("");
    if (routeId.isEmpty()) {
      return ignoreReport(seq, clOrdId, execType, "unknown route ClOrdID", output);
    }

    // Re-uses the route-event path, so an ExecutionReport and a hand-written
    // RouteEvent cannot drift apart — including the cascade to the parent order.
    TreeMap<String, String> translated = new TreeMap<>(event.fields());
    translated.put("event", fsmEvent.name());
    translated.put("routeId", routeId);
    return onRouteEvent(new JournalEvent(event.seq(), TYPE_ROUTE_EVENT, translated), seq, output);
  }

  /**
   * FIX {@code ExecType} (tag 150) to a route FSM event.
   *
   * <p>An explicit table, not a name-matching convention. The FIX values are one character and the
   * schema's event names are not, so there is no derivation to be had — and a wrong guess here is a
   * venue message silently applied to the wrong transition.
   */
  private static @Nullable RouteFsmEvent fromExecType(String execType, String ordStatus) {
    return switch (execType) {
      case "0" -> RouteFsmEvent.RouteAcknowledged;
      case "4" -> RouteFsmEvent.RouteCanceled;
      case "5" -> RouteFsmEvent.RouteReplaced;
      case "8" -> RouteFsmEvent.RouteRejected;
      case "A" -> RouteFsmEvent.RoutePendingNewAtVenue;
      case "C" -> RouteFsmEvent.RouteExpired;
      case "E" -> RouteFsmEvent.RouteReplacePendingAtVenue;
      // ExecType=F is a trade. OrdStatus=2 means nothing is left, so it is the
      // final fill; anything else leaves the route open.
      case "F" ->
          "2".equals(ordStatus) ? RouteFsmEvent.RouteFilled : RouteFsmEvent.RoutePartiallyFilled;
      default -> null;
    };
  }

  private static long ignoreReport(
      long seq, String clOrdId, String execType, String reason, List<JournalEvent> output) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("clOrdId", clOrdId);
    fields.put("execType", execType);
    fields.put("reason", reason);
    output.add(new JournalEvent(++seq, TYPE_EXECUTION_REPORT_IGNORED, fields));
    return seq;
  }

  /**
   * Applies a venue action to a live route, and cascades whatever the schema says it cascades.
   *
   * <p><strong>The cascade is not written here.</strong> {@code route.fsm.yaml} declares {@code
   * emit_event} effects — a route reaching {@code WORKING} emits {@code ValidationPassed} to the
   * order machine, a fill emits {@code PartialFill}, and so on — and this method reads them off the
   * transition result. Hand-writing that mapping would mean three languages each holding an opinion
   * about what the YAML says, which is the exact failure the generator exists to prevent.
   *
   * <p>Ordering is fixed and journalled: the route's own transition first, then each cascaded order
   * transition in the order the schema declares the effects. Two languages emitting the same events
   * in a different order would fail the conformance gate, which is how we know they agree.
   */
  private long onRouteEvent(JournalEvent event, long seq, List<JournalEvent> output) {
    String routeId = field(event, "routeId");
    String raw = field(event, "event");

    RouteFsmEvent fsmEvent = toRouteFsmEvent(raw);
    if (fsmEvent == null) {
      return ignoreRouteEvent(seq, routeId, raw, "unknown FSM event", output);
    }
    Optional<SliceRouteBook.Entry> entry = routes.get(routeId);
    if (entry.isEmpty()) {
      return ignoreRouteEvent(seq, routeId, raw, "unknown route", output);
    }

    RouteFsmState before = entry.get().state();
    var result = routes.apply(routeId, fsmEvent, routePayloadFor(fsmEvent, event));

    TreeMap<String, String> fields = new TreeMap<>();
    boolean applied = result.isPresent() && !result.get().isNoTransition();
    fields.put("applied", Boolean.toString(applied));
    fields.put("event", fsmEvent.name());
    fields.put("from", before.name());
    fields.put("fsm", "route");
    fields.put("routeId", routeId);
    fields.put("to", routes.stateOf(routeId).orElse(before).name());
    output.add(new JournalEvent(++seq, TYPE_FSM_TRANSITION, fields));

    if (!applied) {
      // A declined event cascades nothing. The generated effects are empty on a
      // no-transition precisely so this cannot be got wrong by forgetting to check.
      return seq;
    }

    String clOrdId = clOrdIdFor(entry.get().context().orderId());
    for (RouteFsmEffect effect : result.get().effects()) {
      if (!(effect instanceof RouteFsmEffect.EmitEvent emit)) {
        continue;
      }
      if (clOrdId == null) {
        continue;
      }
      OrderFsmEvent orderEvent = toFsmEvent(emit.event());
      Optional<SliceOrderBook.Entry> parent = orders.get(clOrdId);
      if (orderEvent == null || parent.isEmpty()) {
        continue;
      }
      seq =
          emitTransition(
              clOrdId,
              orderEvent,
              cascadePayload(orderEvent, event),
              parent.get().context(),
              seq,
              output);
    }
    return seq;
  }

  /**
   * The client identifier for one of our order ids.
   *
   * <p>A scan rather than a second map. The order book is keyed on ClOrdID and routes carry
   * OrderID, so something has to bridge them; a reverse index would be a second structure to keep
   * in step, and this book holds tens of orders. The {@link TreeMap} makes the scan order
   * deterministic.
   */
  private @Nullable String clOrdIdFor(String orderId) {
    for (var candidate : orderIds.entrySet()) {
      if (candidate.getValue().equals(orderId)) {
        return candidate.getKey();
      }
    }
    return null;
  }

  /** Records a route action that reached no route. Never fatal — the venue sends what it sends. */
  private static long ignoreRouteEvent(
      long seq, String routeId, String raw, String reason, List<JournalEvent> output) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("event", raw);
    fields.put("reason", reason);
    fields.put("routeId", routeId);
    output.add(new JournalEvent(++seq, TYPE_ROUTE_EVENT_IGNORED, fields));
    return seq;
  }

  /** Maps a journal event name to a route FSM event. Null for anything the schema omits. */
  private static @Nullable RouteFsmEvent toRouteFsmEvent(String name) {
    for (RouteFsmEvent candidate : RouteFsmEvent.values()) {
      if (candidate.name().equals(name)) {
        return candidate;
      }
    }
    return null;
  }

  /** Builds the payload a route FSM event needs, or null when it takes none. */
  private static @Nullable Object routePayloadFor(RouteFsmEvent fsmEvent, JournalEvent event) {
    long lastQty = parseLong(field(event, "lastQty"));
    long lastPx = parseLong(field(event, "lastPx"));
    String execId = field(event, "execId");
    int reason = (int) parseLong(field(event, "cxlRejReason"));
    return switch (fsmEvent) {
      case RoutePartiallyFilled ->
          new RouteFsmPayloads.RoutePartiallyFilledPayload(lastQty, lastPx, execId);
      case RouteFilled -> new RouteFsmPayloads.RouteFilledPayload(lastQty, lastPx, execId);
      case RouteCancelRejected -> new RouteFsmPayloads.RouteCancelRejectedPayload(reason);
      case RouteReplaceRejected -> new RouteFsmPayloads.RouteReplaceRejectedPayload(reason);
      case RouteReplaced -> new RouteFsmPayloads.RouteReplacedPayload(field(event, "newClOrdId"));
      case RouteReplaceRequested ->
          new RouteFsmPayloads.RouteReplaceRequestedPayload(
              field(event, "newClOrdId"), parseLong(field(event, "newRouteQty")), null);
      default -> null;
    };
  }

  /**
   * The payload the cascaded order event needs, built from the same journal fields.
   *
   * <p>A route fill and the order fill it cascades describe the same execution, so they read the
   * same {@code lastQty} / {@code lastPx} / {@code execId} off the input event. Deriving the order
   * payload from the route's would be indirection with no extra truth in it.
   */
  private static @Nullable Object cascadePayload(OrderFsmEvent fsmEvent, JournalEvent event) {
    return payloadFor(fsmEvent, event);
  }

  /**
   * States an order can be routed from.
   *
   * <p>An allowlist, not a denylist of terminal states. A state added to the schema is then
   * un-routable until someone decides it should be — which is the safe direction for a list that
   * decides whether quantity goes to a venue.
   */
  private static boolean isRoutable(OrderFsmState state) {
    return state == OrderFsmState.NEW
        || state == OrderFsmState.REPLACED
        || state == OrderFsmState.PARTIALLY_FILLED;
  }

  /** Refuses a route. No route is created, and no identifier is consumed. */
  private long rejectRoute(
      JournalEvent event, long seq, List<JournalEvent> output, String code, String reason) {
    TreeMap<String, String> fields = new TreeMap<>();
    fields.put("clOrdId", field(event, "clOrdId"));
    fields.put("code", code);
    fields.put("qty", field(event, "qty"));
    fields.put("reason", reason);
    fields.put("venueMic", field(event, "venueMic"));
    output.add(new JournalEvent(++seq, TYPE_ROUTE_REJECTED, fields));
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
