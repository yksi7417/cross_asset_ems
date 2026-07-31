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
 * <p><strong>Today it covers components 1–6b</strong>: the journal codec, deterministic
 * identifiers, the transport seam, the AAA entitlement decision, the layered validation pipeline,
 * the order FSM, route creation and the route venue lifecycle. A {@code SessionLogon} registers a
 * session; an {@code OrderNew} becomes an {@code OrderAccepted} carrying a generated order id or an
 * {@code OrderRejected} carrying a catalog reject code; an {@code OrderEvent} drives the order FSM;
 * a {@code RouteNew} projects an accepted order onto a venue; a {@code RouteEvent} drives the route
 * FSM and <strong>cascades to the order FSM</strong> via the schema's {@code emit_event} effects.
 * Everything else passes through with its sequence renumbered, and a {@code RunSummary} closes the
 * journal.
 *
 * <p>There is still no venue edge — nothing here speaks FIX, and {@code RouteEvent}s arrive as
 * journal entries rather than from a session — and no allocation. Those are components 7 and 8.
 * Pretending otherwise in the output would make the conformance corpus lie about what is
 * implemented.
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
