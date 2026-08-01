//! The slice, as far as it has been built.

use std::collections::{BTreeMap, BTreeSet};

use ems_aaa::{AaaService, Identity};
use ems_core::{DeterministicIds, JournalEvent};
use ems_fsm::generated::order_fsm::{FullFillPayload, PartialFillPayload};
use ems_fsm::generated::route_fsm::{
    RouteCancelRejectedPayload, RouteFilledPayload, RoutePartiallyFilledPayload,
    RouteReplaceRejectedPayload, RouteReplaceRequestedPayload, RouteReplacedPayload,
};
use ems_fsm::{
    OrderFsmContext, OrderFsmEvent, OrderFsmPayload, OrderFsmState, RouteFsmContext, RouteFsmEvent,
    RouteFsmPayload, RouteFsmState, VenueSessionFsmEvent, VenueSessionFsmState,
};
use ems_validator::{
    validate, InstrumentStatus, SecurityMaster, ValidationRequest, ValidationResult,
};

use crate::routes::RouteBook;
use crate::venues::VenueSessions;

/// Input event adding an instrument to the security master.
const TYPE_INSTRUMENT_CREATED: &str = "InstrumentCreated";
/// Output event acknowledging one.
const TYPE_INSTRUMENT_ACCEPTED: &str = "InstrumentAccepted";
/// Input event registering a session and its entitlements.
const TYPE_SESSION_LOGON: &str = "SessionLogon";
/// Output event acknowledging a logon.
const TYPE_SESSION_ACCEPTED: &str = "SessionAccepted";
/// Input event that opens an order.
const TYPE_ORDER_NEW: &str = "OrderNew";
/// Output event acknowledging one.
const TYPE_ORDER_ACCEPTED: &str = "OrderAccepted";
/// Output event refusing one.
const TYPE_ORDER_REJECTED: &str = "OrderRejected";
/// Input event carrying a venue or client action against a live order.
///
/// One input type rather than one per FSM event: the journal names the FSM
/// event in a field, so adding a transition to the schema needs no new event
/// type here. An unrecognised name is ignored rather than fatal.
const TYPE_ORDER_EVENT: &str = "OrderEvent";
/// Output event recording every FSM transition taken.
///
/// What `conformance/harness/fsm_coverage.py` reads. Coverage is measured from
/// what the corpus actually exercised, not inferred from the input.
const TYPE_FSM_TRANSITION: &str = "FsmTransition";
/// Output event for an order action that changed nothing.
const TYPE_ORDER_EVENT_IGNORED: &str = "OrderEventIgnored";
/// Input event projecting an accepted order onto one venue.
const TYPE_ROUTE_NEW: &str = "RouteNew";
/// Output event acknowledging a route.
const TYPE_ROUTE_ACCEPTED: &str = "RouteAccepted";
/// Output event refusing one.
const TYPE_ROUTE_REJECTED: &str = "RouteRejected";
/// Input event carrying a venue action against a live route.
///
/// The route counterpart of `OrderEvent`, named the same way for the same
/// reason: the journal carries the FSM event name in a field, so a transition
/// added to the schema needs no new event type here.
const TYPE_ROUTE_EVENT: &str = "RouteEvent";
/// Output event for a route action that reached no route.
const TYPE_ROUTE_EVENT_IGNORED: &str = "RouteEventIgnored";
/// Input event driving one venue's FIX session.
const TYPE_VENUE_SESSION: &str = "VenueSession";
/// Output event for a session action the schema does not define.
const TYPE_VENUE_SESSION_IGNORED: &str = "VenueSessionEventIgnored";
/// Output event: the outbound FIX message a dispatched route produces.
///
/// The journal records that a message was produced and its identifying tags, not
/// a wire-format string. A byte-exact FIX encoder is a component of its own;
/// recording the intent keeps the conformance gate meaningful without three
/// languages having to agree on tag ordering and checksums as well.
const TYPE_FIX_OUT: &str = "FixOut";
/// Input event: an inbound FIX `ExecutionReport` from a venue.
const TYPE_EXECUTION_REPORT: &str = "ExecutionReport";
/// Output event for an `ExecutionReport` that reached no route.
const TYPE_EXECUTION_REPORT_IGNORED: &str = "ExecutionReportIgnored";
/// Input event distributing an order's filled quantity across accounts.
const TYPE_ALLOCATE: &str = "Allocate";
/// Output event: one account's share of a fill.
const TYPE_ALLOCATION_RECORD: &str = "AllocationRecord";
/// Output event refusing an allocation.
const TYPE_ALLOCATION_REJECTED: &str = "AllocationRejected";
/// Catalog code: no such order to route.
const CODE_ROUTE_UNKNOWN_ORDER: &str = "EMS-RTE-4001";
/// Catalog code: the order is not in a state that can be routed.
const CODE_ROUTE_ORDER_NOT_ROUTABLE: &str = "EMS-RTE-4002";
/// Catalog code: the requested quantity is not routable against what is left.
const CODE_ROUTE_QTY_INVALID: &str = "EMS-RTE-4003";
/// Catalog code: the route's `ClOrdID` is already in use.
const CODE_ROUTE_CLORDID_COLLISION: &str = "EMS-RTE-2005";
/// Catalog code: the venue's FIX session cannot currently take an order.
const CODE_VENUE_SESSION_NOT_ACTIVE: &str = "EMS-VEN-5001";
/// Catalog code: no such order to allocate.
const CODE_ALLOC_UNKNOWN_ORDER: &str = "EMS-ALC-6001";
/// Catalog code: the order has no filled quantity to allocate.
const CODE_ALLOC_NOTHING_FILLED: &str = "EMS-ALC-6002";
/// Catalog code: the share list is empty, malformed, or sums to nothing.
const CODE_ALLOC_BAD_SHARES: &str = "EMS-ALC-6003";
/// Final output event: makes the seed and the input size visible in the journal.
const TYPE_RUN_SUMMARY: &str = "RunSummary";

/// Fields copied from `OrderNew` onto `OrderAccepted`.
///
/// An explicit list rather than "copy everything": an unknown field silently
/// reaching the output would be a divergence that only shows up once some other
/// language's map happens to order it differently.
const ECHOED_FIELDS: [&str; 5] = ["account", "figi", "price", "qty", "side"];

/// Runs the slice over `input`, returning the output journal.
///
/// **Today this covers components 1–7**: the journal codec, deterministic
/// identifiers, the transport seam, the AAA entitlement decision, the layered
/// validation pipeline, the order FSM, routing, and the venue edge. A
/// `SessionLogon` registers a session; an `OrderNew` becomes an `OrderAccepted`
/// or an `OrderRejected`; an `OrderEvent` drives the order FSM; a `RouteNew`
/// projects an accepted order onto a venue — refused with `EMS-VEN-5001` unless
/// that venue's FIX session is `ACTIVE`, and emitting a `FixOut` when accepted;
/// a `VenueSession` drives the session FSM; an inbound `ExecutionReport` is
/// translated by `ExecType` into a route event and **cascades to the order
/// FSM** via the schema's `emit_event` effects.
///
/// An `Allocate` distributes an order's filled quantity across accounts by the
/// largest-remainder method, reading `cum_qty` from the order FSM's own
/// context. **With that, the slice is complete**: every component of the
/// cash-equity order path in ADR 0002's scope is implemented and
/// conformance-checked in all three languages. `FixOut` records intent and
/// identifying tags, not a wire-format FIX string — a byte-exact encoder stays
/// out of scope by decision, not omission.
///
/// Kept in lockstep with `java/ems-it/.../SliceRunner.java` and
/// `cpp/ems-it/src/slice_runner.cpp`.
#[must_use]
pub fn run(input: &[JournalEvent], ids: &mut DeterministicIds) -> Vec<JournalEvent> {
    let mut output = Vec::with_capacity(input.len() + 1);
    let mut aaa = AaaService::new();
    let mut securities = SecurityMaster::new();
    // Keyed on the CLIENT's identifier, not ours. ClOrdID exists before the
    // order reaches us; OrderID is ours and is assigned on acceptance. That is
    // the FIX convention, and it is what lets a rejected order take the real
    // PENDING_NEW -> REJECTED transition without consuming an order id.
    let mut orders: BTreeMap<String, (OrderFsmState, OrderFsmContext)> = BTreeMap::new();
    let mut routes = RouteBook::new();
    let mut venues = VenueSessions::new();
    // The order id we assigned to each accepted order, by the client's ClOrdID.
    // A runner-level index rather than a field on the order book: the FSM context
    // is the schema's shape and this is ours. A rejected order has no entry, so a
    // route can never be hung off an order id that was never issued.
    let mut order_ids: BTreeMap<String, String> = BTreeMap::new();
    let mut seq: u64 = 0;

    for event in input {
        match event.event_type.as_str() {
            TYPE_INSTRUMENT_CREATED => {
                seq += 1;
                output.push(on_instrument_created(event, seq, &mut securities));
            }
            TYPE_SESSION_LOGON => {
                seq += 1;
                output.push(on_session_logon(event, seq, &mut aaa));
            }
            TYPE_ORDER_NEW => {
                seq = on_order_new(
                    event,
                    seq,
                    &aaa,
                    &securities,
                    ids,
                    &mut orders,
                    &mut order_ids,
                    &mut output,
                );
            }
            TYPE_ORDER_EVENT => {
                seq = on_order_event(event, seq, &mut orders, &mut output);
            }
            TYPE_ROUTE_NEW => {
                seq = on_route_new(
                    event,
                    seq,
                    ids,
                    &orders,
                    &order_ids,
                    &mut routes,
                    &venues,
                    &mut output,
                );
            }
            TYPE_ROUTE_EVENT => {
                seq = on_route_event(
                    event,
                    seq,
                    &mut orders,
                    &order_ids,
                    &mut routes,
                    &mut output,
                );
            }
            TYPE_VENUE_SESSION => {
                seq = on_venue_session(event, seq, &mut venues, &mut output);
            }
            TYPE_EXECUTION_REPORT => {
                seq = on_execution_report(
                    event,
                    seq,
                    &mut orders,
                    &order_ids,
                    &mut routes,
                    &mut output,
                );
            }
            TYPE_ALLOCATE => {
                seq = on_allocate(event, seq, &orders, &order_ids, &mut output);
            }
            _ => {
                seq += 1;
                output.push(event.with_seq(seq));
            }
        }
    }

    let mut summary = BTreeMap::new();
    summary.insert("events".to_owned(), input.len().to_string());
    summary.insert("seed".to_owned(), ids.seed().to_string());
    seq += 1;
    output.push(JournalEvent {
        seq,
        event_type: TYPE_RUN_SUMMARY.to_owned(),
        fields: summary,
    });

    output
}

fn on_session_logon(event: &JournalEvent, seq: u64, aaa: &mut AaaService) -> JournalEvent {
    let session_id = parse_session_id(event);
    let user_tags = split_tags(&field(event, "tags"));

    // firmTags / deskTags default to the user's tags, so the common case reads
    // as "this user may do these things" and the AND-gate passes. A case that
    // wants a firm- or desk-level denial states them explicitly.
    let firm_tags = tags_or_default(event, "firmTags", &user_tags);
    let desk_tags = tags_or_default(event, "deskTags", &user_tags);

    let identity = Identity {
        firm: field(event, "firm"),
        desk: field(event, "desk"),
        user: field(event, "user"),
        tags: user_tags.clone(),
    };

    let mut fields = BTreeMap::new();
    fields.insert("deskTags".to_owned(), join_tags(&desk_tags));
    fields.insert("firmTags".to_owned(), join_tags(&firm_tags));
    fields.insert("sessionId".to_owned(), session_id_text(session_id));
    // The granted tags are echoed so a corpus case can show *why* a later
    // rejection happened without the reader having to re-read the input.
    fields.insert("tags".to_owned(), join_tags(&user_tags));
    fields.insert("user".to_owned(), identity.user.clone());

    aaa.register_session(
        session_id.unwrap_or(u64::MAX),
        identity,
        &firm_tags,
        &desk_tags,
    );

    JournalEvent {
        seq,
        event_type: TYPE_SESSION_ACCEPTED.to_owned(),
        fields,
    }
}

/// Tags from `key`, or `fallback` when the field is absent.
fn tags_or_default(
    event: &JournalEvent,
    key: &str,
    fallback: &BTreeSet<String>,
) -> BTreeSet<String> {
    event
        .fields
        .get(key)
        .map_or_else(|| fallback.clone(), |raw| split_tags(raw))
}

fn join_tags(tags: &BTreeSet<String>) -> String {
    tags.iter().cloned().collect::<Vec<_>>().join(",")
}

/// Adds an instrument to the security master.
///
/// An unrecognised status is treated as inactive, so a malformed instrument
/// makes orders on it fail the REFERENCE layer rather than failing the run.
fn on_instrument_created(
    event: &JournalEvent,
    seq: u64,
    securities: &mut SecurityMaster,
) -> JournalEvent {
    let figi = field(event, "figi");
    let raw = field(event, "status");
    let status = match raw.as_str() {
        "ACTIVE" => InstrumentStatus::Active,
        "SUSPENDED" => InstrumentStatus::Inactive("SUSPENDED"),
        "EXPIRED" => InstrumentStatus::Inactive("EXPIRED"),
        "MATURED" => InstrumentStatus::Inactive("MATURED"),
        "DEFAULTED" => InstrumentStatus::Inactive("DEFAULTED"),
        _ => InstrumentStatus::Inactive("UNKNOWN"),
    };
    securities.add(&figi, status);

    let mut fields = BTreeMap::new();
    fields.insert("figi".to_owned(), figi);
    fields.insert(
        "status".to_owned(),
        match status {
            InstrumentStatus::Active => "ACTIVE".to_owned(),
            InstrumentStatus::Inactive(name) => name.to_owned(),
        },
    );
    JournalEvent {
        seq,
        event_type: TYPE_INSTRUMENT_ACCEPTED.to_owned(),
        fields,
    }
}

#[allow(clippy::too_many_arguments)]
fn on_order_new(
    event: &JournalEvent,
    seq: u64,
    aaa: &AaaService,
    securities: &SecurityMaster,
    ids: &mut DeterministicIds,
    orders: &mut BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    order_ids: &mut BTreeMap<String, String>,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    // The whole decision is the pipeline's. SESSION, IDENTITY, REFERENCE and
    // PERMISSION run in that fixed order and the first failure short-circuits,
    // so the reject the journal carries names the outermost thing that was
    // wrong — which is the one worth telling a trader about.
    let request = ValidationRequest {
        request_id: field(event, "clOrdId"),
        session_id: parse_session_id(event),
        tag: non_empty(event, "tag"),
        figi: non_empty(event, "figi"),
    };

    // Keyed on the CLIENT's identifier: ClOrdID exists before the order reaches
    // us, OrderID is ours and is assigned on acceptance. That is what lets a
    // rejected order take the real PENDING_NEW -> REJECTED transition without
    // consuming an order id.
    let cl_ord_id = field(event, "clOrdId");
    let context = context_for(&cl_ord_id, event);

    if let ValidationResult::Reject {
        code,
        category,
        layer,
        message,
        admin_hint,
        ..
    } = validate(&request, aaa, securities)
    {
        let mut seq = emit_transition(
            &cl_ord_id,
            OrderFsmEvent::ValidationFailed,
            None,
            &context,
            seq,
            orders,
            output,
        );
        seq += 1;
        output.push(reject_from(
            seq,
            event,
            &code,
            &category,
            layer.name(),
            &message,
            admin_hint.as_deref(),
        ));
        return seq;
    }

    let mut seq = emit_transition(
        &cl_ord_id,
        OrderFsmEvent::ValidationPassed,
        None,
        &context,
        seq,
        orders,
        output,
    );

    // Only an accepted order consumes an identifier. If a rejected one did, the
    // ids in a journal would depend on how many orders failed — and every corpus
    // case downstream of a rejection would shift.
    let order_id = ids.next_order_id();
    order_ids.insert(cl_ord_id.clone(), order_id.clone());
    let mut fields = BTreeMap::new();
    fields.insert("orderId".to_owned(), order_id);
    for key in ECHOED_FIELDS {
        if let Some(value) = event.fields.get(key) {
            fields.insert(key.to_owned(), value.clone());
        }
    }
    seq += 1;
    output.push(JournalEvent {
        seq,
        event_type: TYPE_ORDER_ACCEPTED.to_owned(),
        fields,
    });
    seq
}

/// Projects an accepted order onto one venue.
///
/// Four refusals, checked in a fixed order with the first one winning, exactly
/// as the validation pipeline short-circuits: unknown order, order not routable,
/// quantity not routable, `ClOrdID` already taken. Fixed order matters because a
/// request can fail two of them at once and the journal must say the same thing
/// in all three languages.
///
/// **A refused route creates nothing.** There is no `FsmTransition` on this path,
/// and that asymmetry with `OrderNew` is the schema's, not a shortcut:
/// `order.fsm.yaml` models validation failure as a real `PENDING_NEW ->
/// REJECTED` transition, while `route.fsm.yaml` has no transition out of
/// `PENDING` except `RouteSent`. Emitting one would mean inventing a transition
/// the schema does not define — and `fsm-coverage` would then count a transition
/// that does not exist.
#[allow(clippy::too_many_arguments)]
fn on_route_new(
    event: &JournalEvent,
    seq: u64,
    ids: &mut DeterministicIds,
    orders: &BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    order_ids: &BTreeMap<String, String>,
    routes: &mut RouteBook,
    venues: &VenueSessions,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let cl_ord_id = field(event, "clOrdId");
    let venue_mic = field(event, "venueMic");
    let qty = field(event, "qty").parse::<u64>().unwrap_or(0);

    // The venue gate runs first. A route to a venue that cannot take it is
    // refused before any of the order-side checks, because the answer does not
    // depend on them — and telling a trader "your order is fine, the venue is
    // down" is more useful than a quantity complaint.
    if let Some(reason) = venue_gate_refusal(venues, &venue_mic) {
        return reject_route(event, seq, CODE_VENUE_SESSION_NOT_ACTIVE, &reason, output);
    }

    let Some((state, context)) = orders.get(&cl_ord_id) else {
        return reject_route(
            event,
            seq,
            CODE_ROUTE_UNKNOWN_ORDER,
            "no such order",
            output,
        );
    };
    // A Rejected order is in the book and lands here, not on 4001 — it exists, it
    // just cannot take quantity. "your order was rejected" is a more useful thing
    // to tell a trader than "we have never heard of it".
    if !is_routable(*state) {
        return reject_route(
            event,
            seq,
            CODE_ROUTE_ORDER_NOT_ROUTABLE,
            &format!("order is {}", state.name()),
            output,
        );
    }
    // Unreachable: a routable state is only reached after acceptance, and
    // acceptance is what fills order_ids. Handled rather than asserted, because
    // this runs against a journal a venue wrote.
    let Some(order_id) = order_ids.get(&cl_ord_id) else {
        return reject_route(
            event,
            seq,
            CODE_ROUTE_ORDER_NOT_ROUTABLE,
            "order has no identifier",
            output,
        );
    };

    let already_routed = routes.routed_qty(order_id);
    if qty == 0 || already_routed + qty > context.order_qty {
        return reject_route(
            event,
            seq,
            CODE_ROUTE_QTY_INVALID,
            &format!(
                "qty {qty} not routable against {} remaining",
                context.order_qty - already_routed
            ),
            output,
        );
    }

    // Absent, the route's ClOrdID is derived from the parent's and the count of
    // routes already hung off it: C-A-1, C-A-2. Derived rather than taken from
    // the route id so that the check below can run BEFORE an id is consumed —
    // the same "a refusal costs nothing" property the order path has.
    let mut route_cl_ord_id = field(event, "routeClOrdId");
    if route_cl_ord_id.is_empty() {
        route_cl_ord_id = format!("{cl_ord_id}-{}", routes.count_for_order(order_id) + 1);
    }
    if routes.has_cl_ord_id(&route_cl_ord_id) {
        return reject_route(
            event,
            seq,
            CODE_ROUTE_CLORDID_COLLISION,
            &format!("ClOrdID {route_cl_ord_id} in use"),
            output,
        );
    }

    let route_id = ids.next_route_id();
    let price = non_empty(event, "price").and_then(|raw| raw.parse::<i64>().ok());
    let result = routes.open(
        &route_id,
        RouteFsmContext {
            route_id: route_id.clone(),
            order_id: order_id.clone(),
            cl_ord_id: route_cl_ord_id.clone(),
            orig_cl_ord_id: None,
            venue_mic: venue_mic.clone(),
            instrument_id: context.instrument_id.clone(),
            side: context.side,
            route_qty: qty,
            price,
            cum_qty: 0,
            leaves_qty: qty,
            trace_id: 1,
            initial_order_id: order_id.clone(),
            pre_cancel_status: None,
        },
    );

    let instrument_id = context.instrument_id.clone();
    let side = context.side;
    push_route_accepted(
        RouteJournal {
            seq,
            applied: !result.is_no_transition,
            stored: routes.state_of(&route_id).unwrap_or(RouteFsmState::Pending),
            route_id: &route_id,
            order_id,
            cl_ord_id: &cl_ord_id,
            route_cl_ord_id: &route_cl_ord_id,
            venue_mic: &venue_mic,
            qty,
            price,
            instrument_id: &instrument_id,
            side,
        },
        output,
    )
}

/// Everything the two output events for an accepted route need.
///
/// A struct rather than ten positional arguments: `push_route_accepted` writes
/// six string fields whose values are all `&str`, and getting two of them the
/// wrong way round would produce a journal that still parses.
#[derive(Clone, Copy)]
struct RouteJournal<'a> {
    seq: u64,
    applied: bool,
    stored: RouteFsmState,
    route_id: &'a str,
    order_id: &'a str,
    cl_ord_id: &'a str,
    route_cl_ord_id: &'a str,
    venue_mic: &'a str,
    qty: u64,
    price: Option<i64>,
    instrument_id: &'a str,
    side: u8,
}

/// Records the dispatch transition and the acknowledgement, in that order.
fn push_route_accepted(journal: RouteJournal<'_>, output: &mut Vec<JournalEvent>) -> u64 {
    let mut transition = BTreeMap::new();
    transition.insert("applied".to_owned(), journal.applied.to_string());
    transition.insert(
        "event".to_owned(),
        RouteFsmEvent::RouteSent.name().to_owned(),
    );
    transition.insert("from".to_owned(), RouteFsmState::Pending.name().to_owned());
    transition.insert("fsm".to_owned(), "route".to_owned());
    transition.insert("routeId".to_owned(), journal.route_id.to_owned());
    // Read back from the book rather than taken from the transition result: the
    // journal should report the state the slice is actually holding.
    transition.insert("to".to_owned(), journal.stored.name().to_owned());
    output.push(JournalEvent {
        seq: journal.seq + 1,
        event_type: TYPE_FSM_TRANSITION.to_owned(),
        fields: transition,
    });

    let mut fields = BTreeMap::new();
    fields.insert("clOrdId".to_owned(), journal.cl_ord_id.to_owned());
    fields.insert("orderId".to_owned(), journal.order_id.to_owned());
    fields.insert("qty".to_owned(), journal.qty.to_string());
    fields.insert(
        "routeClOrdId".to_owned(),
        journal.route_cl_ord_id.to_owned(),
    );
    fields.insert("routeId".to_owned(), journal.route_id.to_owned());
    fields.insert("venueMic".to_owned(), journal.venue_mic.to_owned());
    if let Some(value) = journal.price {
        fields.insert("price".to_owned(), value.to_string());
    }
    output.push(JournalEvent {
        seq: journal.seq + 2,
        event_type: TYPE_ROUTE_ACCEPTED.to_owned(),
        fields,
    });

    // 35=D goes out last: the journal reads in the order things happened, and the
    // message is a consequence of the route existing rather than the cause of it.
    let mut fix = BTreeMap::new();
    fix.insert("clOrdId".to_owned(), journal.route_cl_ord_id.to_owned());
    fix.insert("msgType".to_owned(), "D".to_owned());
    fix.insert("orderQty".to_owned(), journal.qty.to_string());
    fix.insert("side".to_owned(), journal.side.to_string());
    fix.insert("symbol".to_owned(), journal.instrument_id.to_owned());
    fix.insert("venueMic".to_owned(), journal.venue_mic.to_owned());
    if let Some(value) = journal.price {
        fix.insert("price".to_owned(), value.to_string());
    }
    output.push(JournalEvent {
        seq: journal.seq + 3,
        event_type: TYPE_FIX_OUT.to_owned(),
        fields: fix,
    });
    journal.seq + 3
}

/// One account's claim on a fill: `weight_bps` out of the total weight.
struct AllocShare {
    account: String,
    weight_bps: u64,
}

/// Parses `"ACC1:5000,ACC2:3000"`. Malformed entries are dropped, not fatal.
fn parse_shares(raw: &str) -> Vec<AllocShare> {
    raw.split(',')
        .filter_map(|part| {
            let (account, weight) = part.split_once(':')?;
            let account = account.trim();
            let weight_bps = weight.trim().parse::<u64>().ok()?;
            (!account.is_empty() && weight_bps > 0).then(|| AllocShare {
                account: account.to_owned(),
                weight_bps,
            })
        })
        .collect()
}

/// Distributes an order's filled quantity across accounts.
///
/// **Largest-remainder method.** Each account gets the floor of its
/// proportional share; the lots lost to flooring go one each to the accounts
/// with the largest division remainders, ties broken by larger weight then
/// instruction order. Floors alone under-allocate and naive rounding can
/// over-allocate — this is the standard way to make the parts sum exactly to
/// the whole, and the tie-break rules are what make three languages produce
/// identical bytes.
fn on_allocate(
    event: &JournalEvent,
    seq: u64,
    orders: &BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    order_ids: &BTreeMap<String, String>,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let cl_ord_id = field(event, "clOrdId");

    let (Some((_, context)), Some(order_id)) = (orders.get(&cl_ord_id), order_ids.get(&cl_ord_id))
    else {
        return reject_allocation(
            event,
            seq,
            CODE_ALLOC_UNKNOWN_ORDER,
            "no such order",
            output,
        );
    };

    // cum_qty is maintained by the order FSM's own update_context effects on
    // fills, so what is allocated is what the machine says was executed — not a
    // number re-derived from the fill events by a second code path.
    let filled = context.cum_qty;
    if filled == 0 {
        return reject_allocation(
            event,
            seq,
            CODE_ALLOC_NOTHING_FILLED,
            "order has no filled quantity",
            output,
        );
    }

    let shares = parse_shares(&field(event, "shares"));
    let total_weight: u64 = shares.iter().map(|s| s.weight_bps).sum();
    if shares.is_empty() || total_weight == 0 {
        return reject_allocation(
            event,
            seq,
            CODE_ALLOC_BAD_SHARES,
            "shares are empty or sum to nothing",
            output,
        );
    }

    // Floor pass: every account gets its proportional floor, and the remainder
    // of each division is kept to decide who absorbs the lots flooring lost.
    let n = shares.len();
    let mut qty = vec![0_u64; n];
    let mut remainder = vec![0_u64; n];
    let mut allocated = 0_u64;
    for (i, share) in shares.iter().enumerate() {
        let numerator = filled * share.weight_bps;
        qty[i] = numerator / total_weight;
        remainder[i] = numerator % total_weight;
        allocated += qty[i];
    }

    // Residual pass: largest remainder first, ties by larger weight then by
    // instruction order. Every rule is load-bearing — an unstated tie-break is
    // a divergence waiting for the first corpus case that hits it.
    let residual = filled - allocated;
    let mut order: Vec<usize> = (0..n).collect();
    order.sort_by(|&a, &b| {
        remainder[b]
            .cmp(&remainder[a])
            .then(shares[b].weight_bps.cmp(&shares[a].weight_bps))
            .then(a.cmp(&b))
    });
    for k in 0..residual {
        let index = usize::try_from(k).unwrap_or(usize::MAX) % n;
        qty[order[index]] += 1;
    }

    // One record per account, in instruction order. The venue's fill arrived as
    // one quantity; this is the slice's answer to whose it is.
    let mut seq = seq;
    for (i, share) in shares.iter().enumerate() {
        let mut fields = BTreeMap::new();
        fields.insert("account".to_owned(), share.account.clone());
        fields.insert("clOrdId".to_owned(), cl_ord_id.clone());
        fields.insert("orderId".to_owned(), order_id.clone());
        fields.insert("qty".to_owned(), qty[i].to_string());
        fields.insert("weightBps".to_owned(), share.weight_bps.to_string());
        seq += 1;
        output.push(JournalEvent {
            seq,
            event_type: TYPE_ALLOCATION_RECORD.to_owned(),
            fields,
        });
    }
    seq
}

/// Refuses an allocation. Nothing is recorded against any account.
fn reject_allocation(
    event: &JournalEvent,
    seq: u64,
    code: &str,
    reason: &str,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let mut fields = BTreeMap::new();
    fields.insert("clOrdId".to_owned(), field(event, "clOrdId"));
    fields.insert("code".to_owned(), code.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_ALLOCATION_REJECTED.to_owned(),
        fields,
    });
    seq + 1
}

/// Drives one venue's FIX session.
///
/// A venue we have never heard of starts in the schema's initial state rather
/// than being refused, so `ConnectRequested` is reachable by a corpus case.
fn on_venue_session(
    event: &JournalEvent,
    seq: u64,
    venues: &mut VenueSessions,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let venue_mic = field(event, "venueMic");
    let raw = field(event, "event");

    let Some(fsm_event) = VenueSessionFsmEvent::from_name(&raw) else {
        let mut fields = BTreeMap::new();
        fields.insert("event".to_owned(), raw);
        fields.insert("reason".to_owned(), "unknown FSM event".to_owned());
        fields.insert("venueMic".to_owned(), venue_mic);
        output.push(JournalEvent {
            seq: seq + 1,
            event_type: TYPE_VENUE_SESSION_IGNORED.to_owned(),
            fields,
        });
        return seq + 1;
    };

    let before = venues
        .state_of(&venue_mic)
        .unwrap_or(VenueSessionFsmState::Disconnected);
    let result = venues.apply(&venue_mic, fsm_event);

    let mut fields = BTreeMap::new();
    fields.insert("applied".to_owned(), (!result.is_no_transition).to_string());
    fields.insert("event".to_owned(), fsm_event.name().to_owned());
    fields.insert("from".to_owned(), before.name().to_owned());
    fields.insert("fsm".to_owned(), "venue_session".to_owned());
    fields.insert(
        "to".to_owned(),
        venues
            .state_of(&venue_mic)
            .unwrap_or(before)
            .name()
            .to_owned(),
    );
    fields.insert("venueMic".to_owned(), venue_mic);
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_FSM_TRANSITION.to_owned(),
        fields,
    });
    seq + 1
}

/// FIX `ExecType` (tag 150) to a route FSM event.
///
/// An explicit table, not a name-matching convention. The FIX values are one
/// character and the schema's event names are not, so there is no derivation to
/// be had — and a wrong guess here is a venue message silently applied to the
/// wrong transition.
fn from_exec_type(exec_type: &str, ord_status: &str) -> Option<RouteFsmEvent> {
    match exec_type {
        "0" => Some(RouteFsmEvent::RouteAcknowledged),
        "4" => Some(RouteFsmEvent::RouteCanceled),
        "5" => Some(RouteFsmEvent::RouteReplaced),
        "8" => Some(RouteFsmEvent::RouteRejected),
        "A" => Some(RouteFsmEvent::RoutePendingNewAtVenue),
        "C" => Some(RouteFsmEvent::RouteExpired),
        "E" => Some(RouteFsmEvent::RouteReplacePendingAtVenue),
        // ExecType=F is a trade. OrdStatus=2 means nothing is left, so it is the
        // final fill; anything else leaves the route open.
        "F" if ord_status == "2" => Some(RouteFsmEvent::RouteFilled),
        "F" => Some(RouteFsmEvent::RoutePartiallyFilled),
        _ => None,
    }
}

/// Translates an inbound FIX `ExecutionReport` into a route event and applies it.
///
/// This is the whole venue edge in one function: FIX vocabulary on the way in,
/// the slice's own vocabulary on the way out. The report names a route by
/// **`ClOrdID`**, because that is what a venue knows — it has never seen our
/// route id.
fn on_execution_report(
    event: &JournalEvent,
    seq: u64,
    orders: &mut BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    order_ids: &BTreeMap<String, String>,
    routes: &mut RouteBook,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let cl_ord_id = field(event, "clOrdId");
    let exec_type = field(event, "execType");

    let Some(fsm_event) = from_exec_type(&exec_type, &field(event, "ordStatus")) else {
        return push_report_ignored(seq, &cl_ord_id, &exec_type, "unmapped ExecType", output);
    };
    let Some(route_id) = routes.route_id_for_cl_ord_id(&cl_ord_id) else {
        return push_report_ignored(seq, &cl_ord_id, &exec_type, "unknown route ClOrdID", output);
    };

    // Re-uses the route-event path, so an ExecutionReport and a hand-written
    // RouteEvent cannot drift apart — including the cascade to the parent order.
    let mut fields = event.fields.clone();
    fields.insert("event".to_owned(), fsm_event.name().to_owned());
    fields.insert("routeId".to_owned(), route_id);
    let translated = JournalEvent {
        seq: event.seq,
        event_type: TYPE_ROUTE_EVENT.to_owned(),
        fields,
    };
    on_route_event(&translated, seq, orders, order_ids, routes, output)
}

fn push_report_ignored(
    seq: u64,
    cl_ord_id: &str,
    exec_type: &str,
    reason: &str,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let mut fields = BTreeMap::new();
    fields.insert("clOrdId".to_owned(), cl_ord_id.to_owned());
    fields.insert("execType".to_owned(), exec_type.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_EXECUTION_REPORT_IGNORED.to_owned(),
        fields,
    });
    seq + 1
}

/// Applies a venue action to a live route, and cascades what the schema says.
///
/// **The cascade is not written here.** `route.fsm.yaml` declares `emit_event`
/// effects — a route reaching `WORKING` emits `ValidationPassed` to the order
/// machine, a fill emits `PartialFill` — and this reads them off the transition
/// result. Hand-writing that mapping would mean three languages each holding an
/// opinion about what the YAML says, which is the failure the generator exists
/// to prevent.
///
/// Ordering is fixed and journalled: the route's own transition first, then each
/// cascaded order transition in the order the schema declares the effects. Two
/// languages emitting these in a different order would fail the conformance
/// gate, which is how we know they agree.
fn on_route_event(
    event: &JournalEvent,
    seq: u64,
    orders: &mut BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    order_ids: &BTreeMap<String, String>,
    routes: &mut RouteBook,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let route_id = field(event, "routeId");
    let raw = field(event, "event");

    let Some(fsm_event) = RouteFsmEvent::from_name(&raw) else {
        return push_route_ignored(seq, &route_id, &raw, "unknown FSM event", output);
    };
    let Some(before) = routes.state_of(&route_id) else {
        return push_route_ignored(seq, &route_id, &raw, "unknown route", output);
    };
    let order_id = routes
        .context_of(&route_id)
        .map(|context| context.order_id.clone())
        .unwrap_or_default();

    let payload = route_payload_for(fsm_event, event);
    let result = routes.apply(&route_id, fsm_event, payload.as_ref());
    let applied = result.as_ref().is_some_and(|r| !r.is_no_transition);

    let mut fields = BTreeMap::new();
    fields.insert("applied".to_owned(), applied.to_string());
    fields.insert("event".to_owned(), fsm_event.name().to_owned());
    fields.insert("from".to_owned(), before.name().to_owned());
    fields.insert("fsm".to_owned(), "route".to_owned());
    fields.insert("routeId".to_owned(), route_id.clone());
    fields.insert(
        "to".to_owned(),
        routes
            .state_of(&route_id)
            .unwrap_or(before)
            .name()
            .to_owned(),
    );
    let mut seq = seq + 1;
    output.push(JournalEvent {
        seq,
        event_type: TYPE_FSM_TRANSITION.to_owned(),
        fields,
    });

    // A declined event cascades nothing. The generated effects are empty on a
    // no-transition precisely so this cannot be got wrong by forgetting to check.
    let Some(result) = result.filter(|r| !r.is_no_transition) else {
        return seq;
    };

    let Some(cl_ord_id) = cl_ord_id_for(order_ids, &order_id) else {
        return seq;
    };

    for (_, emitted) in result.emitted_events() {
        let Some(order_event) = OrderFsmEvent::from_name(emitted) else {
            continue;
        };
        let Some((_, context)) = orders.get(&cl_ord_id) else {
            continue;
        };
        let context = context.clone();
        seq = emit_transition(
            &cl_ord_id,
            order_event,
            payload_for(order_event, event).as_ref(),
            &context,
            seq,
            orders,
            output,
        );
    }
    seq
}

/// The client identifier for one of our order ids.
///
/// A scan rather than a second map. The order book is keyed on `ClOrdID` and
/// routes carry `OrderID`, so something has to bridge them; a reverse index would
/// be a second structure to keep in step, and this book holds tens of orders.
/// The `BTreeMap` makes the scan order deterministic.
fn cl_ord_id_for(order_ids: &BTreeMap<String, String>, order_id: &str) -> Option<String> {
    order_ids
        .iter()
        .find(|(_, assigned)| assigned.as_str() == order_id)
        .map(|(cl_ord_id, _)| cl_ord_id.clone())
}

/// Records a route action that reached no route. Never fatal.
fn push_route_ignored(
    seq: u64,
    route_id: &str,
    raw_event: &str,
    reason: &str,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let mut fields = BTreeMap::new();
    fields.insert("event".to_owned(), raw_event.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    fields.insert("routeId".to_owned(), route_id.to_owned());
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_ROUTE_EVENT_IGNORED.to_owned(),
        fields,
    });
    seq + 1
}

/// Builds the payload a route FSM event needs, or `None` when it takes none.
fn route_payload_for(fsm_event: RouteFsmEvent, event: &JournalEvent) -> Option<RouteFsmPayload> {
    let last_qty = field(event, "lastQty").parse::<u64>().unwrap_or(0);
    let last_px = field(event, "lastPx").parse::<i64>().unwrap_or(0);
    let exec_id = field(event, "execId");
    let reason = field(event, "cxlRejReason").parse::<u8>().unwrap_or(0);
    match fsm_event {
        RouteFsmEvent::RoutePartiallyFilled => Some(RouteFsmPayload::RoutePartiallyFilled(
            RoutePartiallyFilledPayload {
                last_qty,
                last_px,
                exec_id,
            },
        )),
        RouteFsmEvent::RouteFilled => Some(RouteFsmPayload::RouteFilled(RouteFilledPayload {
            last_qty,
            last_px,
            exec_id,
        })),
        RouteFsmEvent::RouteCancelRejected => Some(RouteFsmPayload::RouteCancelRejected(
            RouteCancelRejectedPayload {
                cxl_rej_reason: reason,
            },
        )),
        RouteFsmEvent::RouteReplaceRejected => Some(RouteFsmPayload::RouteReplaceRejected(
            RouteReplaceRejectedPayload {
                cxl_rej_reason: reason,
            },
        )),
        RouteFsmEvent::RouteReplaced => {
            Some(RouteFsmPayload::RouteReplaced(RouteReplacedPayload {
                new_cl_ord_id: field(event, "newClOrdId"),
            }))
        }
        RouteFsmEvent::RouteReplaceRequested => Some(RouteFsmPayload::RouteReplaceRequested(
            RouteReplaceRequestedPayload {
                new_cl_ord_id: field(event, "newClOrdId"),
                new_route_qty: field(event, "newRouteQty").parse::<u64>().unwrap_or(0),
                new_price: None,
            },
        )),
        _ => None,
    }
}

/// Why the venue gate refuses `venue_mic`, or `None` when it is `ACTIVE`.
///
/// "Never connected" and a named dead state are different facts to a trader,
/// even though the gate refuses both.
fn venue_gate_refusal(venues: &VenueSessions, venue_mic: &str) -> Option<String> {
    if venues.is_active(venue_mic) {
        return None;
    }
    let state = venues
        .state_of(venue_mic)
        .map_or_else(|| "never connected".to_owned(), |s| s.name().to_owned());
    Some(format!("venue session is {state}"))
}

/// States an order can be routed from.
///
/// An allowlist, not a denylist of terminal states. A state added to the schema
/// is then un-routable until someone decides it should be — the safe direction
/// for a list that decides whether quantity goes to a venue.
const fn is_routable(state: OrderFsmState) -> bool {
    matches!(
        state,
        OrderFsmState::New | OrderFsmState::Replaced | OrderFsmState::PartiallyFilled
    )
}

/// Refuses a route. No route is created, and no identifier is consumed.
fn reject_route(
    event: &JournalEvent,
    seq: u64,
    code: &str,
    reason: &str,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let mut fields = BTreeMap::new();
    fields.insert("clOrdId".to_owned(), field(event, "clOrdId"));
    fields.insert("code".to_owned(), code.to_owned());
    fields.insert("qty".to_owned(), field(event, "qty"));
    fields.insert("reason".to_owned(), reason.to_owned());
    fields.insert("venueMic".to_owned(), field(event, "venueMic"));
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_ROUTE_REJECTED.to_owned(),
        fields,
    });
    seq + 1
}

/// Applies a venue or client action to a live order.
///
/// Every outcome is a journal event: a transition, a no-transition, or an
/// unknown order. None is fatal — an order book fed by a venue must survive
/// anything the venue sends.
fn on_order_event(
    event: &JournalEvent,
    seq: u64,
    orders: &mut BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let cl_ord_id = field(event, "clOrdId");
    let raw = field(event, "event");

    let Some(fsm_event) = OrderFsmEvent::from_name(&raw) else {
        return push_ignored(seq, &cl_ord_id, &raw, "unknown FSM event", output);
    };

    let Some((_, context)) = orders.get(&cl_ord_id) else {
        return push_ignored(seq, &cl_ord_id, &raw, "unknown order", output);
    };
    let context = context.clone();

    emit_transition(
        &cl_ord_id,
        fsm_event,
        payload_for(fsm_event, event).as_ref(),
        &context,
        seq,
        orders,
        output,
    )
}

fn push_ignored(
    seq: u64,
    cl_ord_id: &str,
    raw_event: &str,
    reason: &str,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let mut fields = BTreeMap::new();
    fields.insert("clOrdId".to_owned(), cl_ord_id.to_owned());
    fields.insert("event".to_owned(), raw_event.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_ORDER_EVENT_IGNORED.to_owned(),
        fields,
    });
    seq + 1
}

/// Applies a transition and records it.
///
/// A no-transition is recorded too, with `applied=false`. Dropping it would make
/// a case that fed the FSM an event it ignored look identical to one that never
/// sent the event — and that difference is what a reviewer needs to see.
#[allow(clippy::too_many_arguments)]
fn emit_transition(
    cl_ord_id: &str,
    fsm_event: OrderFsmEvent,
    payload: Option<&OrderFsmPayload>,
    context: &OrderFsmContext,
    seq: u64,
    orders: &mut BTreeMap<String, (OrderFsmState, OrderFsmContext)>,
    output: &mut Vec<JournalEvent>,
) -> u64 {
    let before = orders
        .get(cl_ord_id)
        .map_or(OrderFsmState::PendingNew, |(state, _)| *state);
    let ctx = orders
        .get(cl_ord_id)
        .map_or_else(|| context.clone(), |(_, c)| c.clone());

    let result = before.apply(fsm_event, &ctx, payload);
    if !result.is_no_transition {
        orders.insert(
            cl_ord_id.to_owned(),
            (result.new_state, result.new_context.clone()),
        );
    } else if !orders.contains_key(cl_ord_id) {
        orders.insert(cl_ord_id.to_owned(), (before, ctx));
    }

    let mut fields = BTreeMap::new();
    fields.insert("applied".to_owned(), (!result.is_no_transition).to_string());
    fields.insert("clOrdId".to_owned(), cl_ord_id.to_owned());
    fields.insert("event".to_owned(), fsm_event.name().to_owned());
    fields.insert("from".to_owned(), before.name().to_owned());
    fields.insert("fsm".to_owned(), "order".to_owned());
    fields.insert(
        "to".to_owned(),
        if result.is_no_transition {
            before.name().to_owned()
        } else {
            result.new_state.name().to_owned()
        },
    );
    output.push(JournalEvent {
        seq: seq + 1,
        event_type: TYPE_FSM_TRANSITION.to_owned(),
        fields,
    });
    seq + 1
}

/// The FSM context an order starts with, from the fields the journal carries.
fn context_for(cl_ord_id: &str, event: &JournalEvent) -> OrderFsmContext {
    let qty = field(event, "qty").parse::<u64>().unwrap_or(0);
    OrderFsmContext {
        order_id: cl_ord_id.to_owned(),
        cl_ord_id: cl_ord_id.to_owned(),
        orig_cl_ord_id: None,
        instrument_id: field(event, "figi"),
        side: if field(event, "side") == "SELL" { 2 } else { 1 },
        order_qty: qty,
        price: None,
        cum_qty: 0,
        leaves_qty: qty,
        account: field(event, "account"),
        tif: 0,
        initial_cl_ord_id: cl_ord_id.to_owned(),
        chain_id: cl_ord_id.to_owned(),
        order_version: 1,
        pre_cancel_status: None,
        pre_replace_status: None,
    }
}

/// Builds the payload an FSM event needs, or `None` when it takes none.
fn payload_for(fsm_event: OrderFsmEvent, event: &JournalEvent) -> Option<OrderFsmPayload> {
    let last_qty = field(event, "lastQty").parse::<u64>().unwrap_or(0);
    let last_px = field(event, "lastPx").parse::<i64>().unwrap_or(0);
    let exec_id = field(event, "execId");
    match fsm_event {
        OrderFsmEvent::PartialFill => Some(OrderFsmPayload::PartialFill(PartialFillPayload {
            last_qty,
            last_px,
            exec_id,
        })),
        OrderFsmEvent::FullFill => Some(OrderFsmPayload::FullFill(FullFillPayload {
            last_qty,
            last_px,
            exec_id,
        })),
        _ => None,
    }
}

/// Turns a pipeline rejection into a journal event.
///
/// The layer is carried explicitly: two rejections can share a code and differ
/// in which layer produced them once later layers land, and a journal that
/// omitted it would make those two indistinguishable on replay.
fn reject_from(
    seq: u64,
    event: &JournalEvent,
    code: &str,
    category: &str,
    layer: &str,
    reason: &str,
    admin_hint: Option<&str>,
) -> JournalEvent {
    let mut fields = BTreeMap::new();
    if let Some(hint) = admin_hint {
        fields.insert("adminHint".to_owned(), hint.to_owned());
    }
    fields.insert("category".to_owned(), category.to_owned());
    fields.insert("code".to_owned(), code.to_owned());
    fields.insert("layer".to_owned(), layer.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    fields.insert("sessionId".to_owned(), field(event, "sessionId"));
    JournalEvent {
        seq,
        event_type: TYPE_ORDER_REJECTED.to_owned(),
        fields,
    }
}

/// The field's value, or `None` when absent or empty — the pipeline reads that
/// as "skip the layer this feeds".
fn non_empty(event: &JournalEvent, key: &str) -> Option<String> {
    event.fields.get(key).filter(|v| !v.is_empty()).cloned()
}

/// `None` for a missing or non-numeric session id.
///
/// Malformed data on the wire is a rejection, not a defect: the order is
/// refused as "session not found" rather than crashing the run.
fn parse_session_id(event: &JournalEvent) -> Option<u64> {
    field(event, "sessionId").parse::<u64>().ok()
}

/// The id as it appears in a `SessionAccepted`. An unparseable logon id is
/// echoed as `-1`, matching what Java's `Long.parseLong` fallback produces.
fn session_id_text(session_id: Option<u64>) -> String {
    session_id.map_or_else(|| "-1".to_owned(), |id| id.to_string())
}

fn field(event: &JournalEvent, key: &str) -> String {
    event.fields.get(key).cloned().unwrap_or_default()
}

/// Splits a comma-separated tag list. Empty entries are dropped; order comes
/// from the set.
fn split_tags(raw: &str) -> BTreeSet<String> {
    raw.split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(ToOwned::to_owned)
        .collect()
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::run;
    use ems_core::{encode, DeterministicIds, JournalEvent};
    use std::collections::BTreeMap;

    fn event(seq: u64, event_type: &str, fields: &[(&str, &str)]) -> JournalEvent {
        JournalEvent {
            seq,
            event_type: event_type.to_owned(),
            fields: fields
                .iter()
                .map(|(k, v)| ((*k).to_owned(), (*v).to_owned()))
                .collect::<BTreeMap<_, _>>(),
        }
    }

    /// The nth output event of a given type.
    ///
    /// Position-based assertions broke the moment the FSM started emitting an
    /// `FsmTransition` before each outcome. Finding by type says what the test
    /// means and survives the next component doing the same.
    fn nth_of<'a>(out: &'a [JournalEvent], event_type: &str, index: usize) -> &'a JournalEvent {
        out.iter()
            .filter(|e| e.event_type == event_type)
            .nth(index)
            .unwrap_or_else(|| panic!("no {event_type} at index {index}"))
    }

    fn count_of(out: &[JournalEvent], event_type: &str) -> usize {
        out.iter().filter(|e| e.event_type == event_type).count()
    }

    fn logon(tags: &str) -> JournalEvent {
        event(
            1,
            "SessionLogon",
            &[
                ("desk", "DESK1"),
                ("firm", "FIRM1"),
                ("sessionId", "7"),
                ("tags", tags),
                ("user", "trader1"),
            ],
        )
    }

    #[test]
    fn empty_input_still_produces_a_run_summary() {
        let mut ids = DeterministicIds::new(0);
        let out = run(&[], &mut ids);

        assert_eq!(out.len(), 1);
        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"events\":\"0\",\"seed\":\"0\"},\"seq\":1,\"type\":\"RunSummary\"}"
        );
    }

    #[test]
    fn logon_then_order_is_accepted() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[
                logon("order-entry"),
                event(2, "OrderNew", &[("account", "ACC1"), ("sessionId", "7")]),
            ],
            &mut ids,
        );

        let accepted = nth_of(&out, "OrderAccepted", 0);
        assert_eq!(accepted.fields["orderId"], "ORD-0000000001");
        assert_eq!(accepted.fields["account"], "ACC1");
    }

    #[test]
    fn order_without_a_known_session_is_rejected() {
        let mut ids = DeterministicIds::new(0);
        let out = run(&[event(1, "OrderNew", &[("sessionId", "99")])], &mut ids);

        let rejected = nth_of(&out, "OrderRejected", 0);
        assert_eq!(rejected.fields["code"], "EMS-SES-1002");
        assert_eq!(rejected.fields["layer"], "SESSION");
        assert_eq!(
            rejected.fields["reason"],
            "Session 99 not found or has expired."
        );
    }

    #[test]
    fn order_missing_the_required_tag_is_rejected() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[
                logon("market-data"),
                event(2, "OrderNew", &[("sessionId", "7"), ("tag", "order-entry")]),
            ],
            &mut ids,
        );

        // Outermost-first: firmTags defaults to the user's tags, so the firm
        // grant is missing too and the gate reports firm (1003) rather than
        // user (1001). Reporting "you lack the tag" would send the user to the
        // wrong administrator.
        let encoded = encode(nth_of(&out, "OrderRejected", 0));
        assert!(encoded.contains("EMS-PRM-1003"), "{encoded}");
        assert!(
            encoded.contains("is not granted tag `#order-entry`"),
            "{encoded}"
        );
    }

    #[test]
    fn a_rejected_order_does_not_consume_an_identifier() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[
                logon("order-entry"),
                event(2, "OrderNew", &[("sessionId", "99")]),
                event(3, "OrderNew", &[("sessionId", "7")]),
            ],
            &mut ids,
        );

        // If a rejected order consumed an id this would be ORD-0000000002, and
        // every corpus case downstream of a rejection would shift.
        assert_eq!(
            nth_of(&out, "OrderAccepted", 0).fields["orderId"],
            "ORD-0000000001"
        );
    }

    #[test]
    fn a_non_numeric_session_id_is_a_rejection_not_a_panic() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[event(1, "OrderNew", &[("sessionId", "not-a-number")])],
            &mut ids,
        );

        let rejected = nth_of(&out, "OrderRejected", 0);
        assert_eq!(rejected.fields["code"], "EMS-SES-1002");
        assert_eq!(
            rejected.fields["reason"],
            "Session -1 not found or has expired."
        );
    }

    #[test]
    fn re_logon_replaces_the_identity() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[
                logon("market-data"),
                event(2, "OrderNew", &[("sessionId", "7"), ("tag", "order-entry")]),
                event(
                    3,
                    "SessionLogon",
                    &[
                        ("desk", "DESK1"),
                        ("firm", "FIRM1"),
                        ("sessionId", "7"),
                        ("tags", "market-data,order-entry"),
                        ("user", "trader2"),
                    ],
                ),
                event(4, "OrderNew", &[("sessionId", "7"), ("tag", "order-entry")]),
            ],
            &mut ids,
        );

        assert_eq!(count_of(&out, "OrderRejected"), 1);
        assert_eq!(count_of(&out, "OrderAccepted"), 1);
    }

    #[test]
    fn other_event_types_pass_through_with_sequence_renumbered() {
        let mut ids = DeterministicIds::new(0);
        let out = run(&[event(41, "Heartbeat", &[("note", "hi")])], &mut ids);

        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"note\":\"hi\"},\"seq\":1,\"type\":\"Heartbeat\"}"
        );
    }

    #[test]
    fn output_sequence_is_contiguous_from_one() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[logon("order-entry"), event(9, "Heartbeat", &[])],
            &mut ids,
        );

        // Sequence numbers are contiguous from 1 however many events a run emits.
        for (i, e) in out.iter().enumerate() {
            assert_eq!(e.seq, i as u64 + 1);
        }
    }

    // ── Routing (component 6a) ───────────────────────────────────────────────

    /// A logon, an active instrument and one accepted order of `qty`.
    ///
    /// Every routing test needs the same three events before it can say anything
    /// about a route, and spelling them out per test buries the line that differs.
    fn routable_order(cl_ord_id: &str, qty: &str) -> Vec<JournalEvent> {
        let mut input = vec![
            logon("order-entry"),
            event(
                2,
                "InstrumentCreated",
                &[("figi", "BBG1"), ("status", "ACTIVE")],
            ),
        ];
        // The venue gate (component 8) refuses a route to anything but an ACTIVE
        // session, so every routing fixture logs the venues on first.
        for venue in ["XNAS", "XNYS", "XLON"] {
            for name in ["ConnectRequested", "TcpConnected", "LogonAcknowledged"] {
                input.push(event(
                    0,
                    "VenueSession",
                    &[("event", name), ("venueMic", venue)],
                ));
            }
        }
        input.push(event(
            3,
            "OrderNew",
            &[
                ("clOrdId", cl_ord_id),
                ("figi", "BBG1"),
                ("qty", qty),
                ("sessionId", "7"),
                ("side", "BUY"),
                ("tag", "order-entry"),
            ],
        ));
        input
    }

    fn route_new(seq: u64, cl_ord_id: &str, qty: &str) -> JournalEvent {
        event(
            seq,
            "RouteNew",
            &[("clOrdId", cl_ord_id), ("qty", qty), ("venueMic", "XNAS")],
        )
    }

    #[test]
    fn routing_an_accepted_order_dispatches_it() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "400"));
        let out = run(&input, &mut ids);

        let accepted = nth_of(&out, "RouteAccepted", 0);
        assert_eq!(accepted.fields["routeId"], "RTE-0000000001");
        assert_eq!(accepted.fields["orderId"], "ORD-0000000001");
        assert_eq!(accepted.fields["routeClOrdId"], "C-A-1");
        // A market route carries no price rather than a zero one: absent and zero
        // are different orders to a venue.
        assert!(!accepted.fields.contains_key("price"));

        // The route is dispatched on creation, not merely created. Found by
        // machine, not by position — the venue-session transitions land first.
        let transition = out
            .iter()
            .find(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "route")
            .expect("a route transition");
        assert_eq!(transition.fields["from"], "PENDING");
        assert_eq!(transition.fields["to"], "SENT");
        assert_eq!(transition.fields["applied"], "true");
    }

    #[test]
    fn routing_more_than_the_order_holds_is_refused() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "600"));
        input.push(route_new(5, "C-A", "600"));
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteAccepted"), 1);
        let rejected = nth_of(&out, "RouteRejected", 0);
        assert_eq!(rejected.fields["code"], "EMS-RTE-4003");
        assert_eq!(
            rejected.fields["reason"],
            "qty 600 not routable against 400 remaining"
        );
    }

    #[test]
    fn zero_quantity_is_not_a_route() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "0"));
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteAccepted"), 0);
        assert_eq!(
            nth_of(&out, "RouteRejected", 0).fields["code"],
            "EMS-RTE-4003"
        );
    }

    #[test]
    fn routing_an_unknown_order_is_refused() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-NOPE", "100"));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "RouteRejected", 0).fields["code"],
            "EMS-RTE-4001"
        );
    }

    /// A rejected order is in the book but cannot take quantity — 4002, not 4001.
    #[test]
    fn routing_a_rejected_order_says_the_order_is_rejected() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[
                logon("order-entry"),
                event(
                    0,
                    "VenueSession",
                    &[("event", "ConnectRequested"), ("venueMic", "XNAS")],
                ),
                event(
                    0,
                    "VenueSession",
                    &[("event", "TcpConnected"), ("venueMic", "XNAS")],
                ),
                event(
                    0,
                    "VenueSession",
                    &[("event", "LogonAcknowledged"), ("venueMic", "XNAS")],
                ),
                event(
                    2,
                    "OrderNew",
                    &[
                        ("clOrdId", "C-Z"),
                        ("figi", "BBG-NOT-LISTED"),
                        ("qty", "100"),
                        ("sessionId", "7"),
                        ("tag", "order-entry"),
                    ],
                ),
                route_new(3, "C-Z", "100"),
            ],
            &mut ids,
        );

        let rejected = nth_of(&out, "RouteRejected", 0);
        assert_eq!(rejected.fields["code"], "EMS-RTE-4002");
        assert_eq!(rejected.fields["reason"], "order is REJECTED");
    }

    #[test]
    fn a_route_cl_ord_id_cannot_be_reused() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        for seq in [4, 5] {
            input.push(event(
                seq,
                "RouteNew",
                &[
                    ("clOrdId", "C-A"),
                    ("qty", "100"),
                    ("routeClOrdId", "MINE"),
                    ("venueMic", "XNAS"),
                ],
            ));
        }
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteAccepted"), 1);
        assert_eq!(
            nth_of(&out, "RouteRejected", 0).fields["code"],
            "EMS-RTE-2005"
        );
    }

    // ── Route lifecycle (component 6b) ──────────────────────────────────────

    fn route_event(seq: u64, route_id: &str, name: &str) -> JournalEvent {
        event(
            seq,
            "RouteEvent",
            &[
                ("event", name),
                ("execId", "E-1"),
                ("lastPx", "15000"),
                ("lastQty", "100"),
                ("routeId", route_id),
            ],
        )
    }

    /// A live route with its parent order, ready for venue events.
    fn working_route() -> Vec<JournalEvent> {
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "400"));
        input.push(route_event(5, "RTE-0000000001", "RouteAcknowledged"));
        input
    }

    /// The cascade: a venue fill on a route moves the parent ORDER, and the
    /// mapping comes from the schema's `emit_event` effects rather than from any
    /// table in this file.
    #[test]
    fn a_route_fill_cascades_to_the_parent_order() {
        let mut ids = DeterministicIds::new(0);
        let mut input = working_route();
        input.push(route_event(6, "RTE-0000000001", "RouteFilled"));
        let out = run(&input, &mut ids);

        let route = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "route")
            .expect("a route transition");
        assert_eq!(route.fields["to"], "FILLED");

        let order = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "order")
            .expect("an order transition");
        assert_eq!(order.fields["event"], "FullFill");
        assert_eq!(order.fields["to"], "FILLED");
        // The route moves first, then the order it cascaded to. Order is part of
        // the cross-language contract.
        assert!(route.seq < order.seq);
    }

    /// **A declined event cascades nothing.** The generated effects are empty on
    /// a no-transition, so a route that refuses an event cannot move the order.
    #[test]
    fn a_declined_route_event_cascades_nothing() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "400"));
        // A route in SENT has no rule for RouteCanceled.
        input.push(route_event(5, "RTE-0000000001", "RouteCanceled"));
        let out = run(&input, &mut ids);

        let route = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "route")
            .expect("a route transition");
        assert_eq!(route.fields["applied"], "false");
        // Exactly one order transition: the ValidationPassed from OrderNew. The
        // declined route event added none.
        assert_eq!(
            out.iter()
                .filter(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "order")
                .count(),
            1
        );
    }

    #[test]
    fn an_event_for_an_unknown_route_is_ignored() {
        let mut ids = DeterministicIds::new(0);
        let mut input = working_route();
        input.push(route_event(6, "RTE-9999999999", "RouteFilled"));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "RouteEventIgnored", 0).fields["reason"],
            "unknown route"
        );
    }

    #[test]
    fn an_unknown_route_event_name_is_ignored() {
        let mut ids = DeterministicIds::new(0);
        let mut input = working_route();
        input.push(route_event(6, "RTE-0000000001", "NotARouteEvent"));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "RouteEventIgnored", 0).fields["reason"],
            "unknown FSM event"
        );
    }

    /// T-7: a route the venue refused holds no quantity, so the order can be
    /// re-routed for the full amount. Before component 6b this was EMS-RTE-4003.
    #[test]
    fn a_rejected_route_releases_its_quantity() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "1000"));
        input.push(route_event(5, "RTE-0000000001", "RouteRejected"));
        input.push(route_new(6, "C-A", "1000"));
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteAccepted"), 2);
        assert_eq!(count_of(&out, "RouteRejected"), 0);
    }

    /// A FILLED route keeps its quantity — releasing it would let the order be
    /// over-filled. The mirror of the test above, and the reason the releasing
    /// set is an allowlist rather than "any terminal state".
    #[test]
    fn a_filled_route_does_not_release_its_quantity() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "1000"));
        input.push(route_event(5, "RTE-0000000001", "RouteAcknowledged"));
        input.push(route_event(6, "RTE-0000000001", "RouteFilled"));
        input.push(route_new(7, "C-A", "1000"));
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteAccepted"), 1);
        // The order is FILLED by the cascade, so it is refused as un-routable
        // before the quantity check is even reached.
        assert_eq!(
            nth_of(&out, "RouteRejected", 0).fields["code"],
            "EMS-RTE-4002"
        );
    }

    // ── Venue edge (component 8) ─────────────────────────────────────────────

    fn venue_session(venue: &str, name: &str) -> JournalEvent {
        event(0, "VenueSession", &[("event", name), ("venueMic", venue)])
    }

    /// The gate: a route to a venue that is not ACTIVE is refused with 5001,
    /// before any order-side check runs.
    #[test]
    fn a_route_to_an_inactive_venue_is_refused() {
        let mut ids = DeterministicIds::new(0);
        // Session reaches LOGON_SENT only: a socket exists, sequence numbers do
        // not. The dangerous half-open case, because it looks usable.
        let out = run(
            &[
                logon("order-entry"),
                event(
                    2,
                    "InstrumentCreated",
                    &[("figi", "BBG1"), ("status", "ACTIVE")],
                ),
                venue_session("XNAS", "ConnectRequested"),
                venue_session("XNAS", "TcpConnected"),
                event(
                    3,
                    "OrderNew",
                    &[
                        ("clOrdId", "C-A"),
                        ("figi", "BBG1"),
                        ("qty", "1000"),
                        ("sessionId", "7"),
                        ("side", "BUY"),
                        ("tag", "order-entry"),
                    ],
                ),
                route_new(4, "C-A", "400"),
            ],
            &mut ids,
        );

        let rejected = nth_of(&out, "RouteRejected", 0);
        assert_eq!(rejected.fields["code"], "EMS-VEN-5001");
        assert_eq!(rejected.fields["reason"], "venue session is LOGON_SENT");
        assert_eq!(count_of(&out, "RouteAccepted"), 0);
    }

    /// Never-connected and disconnected read differently in the journal, even
    /// though the gate refuses both.
    #[test]
    fn a_never_connected_venue_reads_differently_from_a_dead_one() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(event(
            0,
            "RouteNew",
            &[("clOrdId", "C-A"), ("qty", "100"), ("venueMic", "XJPX")],
        ));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "RouteRejected", 0).fields["reason"],
            "venue session is never connected"
        );
    }

    /// An accepted route emits the outbound 35=D, after the acceptance.
    #[test]
    fn an_accepted_route_emits_fix_out() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "400"));
        let out = run(&input, &mut ids);

        let fix = nth_of(&out, "FixOut", 0);
        assert_eq!(fix.fields["msgType"], "D");
        assert_eq!(fix.fields["clOrdId"], "C-A-1");
        assert_eq!(fix.fields["orderQty"], "400");
        assert_eq!(fix.fields["symbol"], "BBG1");
        // Message follows acceptance: a consequence, not a cause.
        let accepted = nth_of(&out, "RouteAccepted", 0);
        assert!(accepted.seq < fix.seq);
    }

    /// The full inbound chain: one `ExecutionReport` moves two machines, and
    /// neither mapping is written in the runner.
    #[test]
    fn an_execution_report_drives_route_and_order() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "1000"));
        input.push(event(
            5,
            "ExecutionReport",
            &[
                ("clOrdId", "C-A-1"),
                ("execType", "0"),
                ("ordStatus", "0"),
                ("venueMic", "XNAS"),
            ],
        ));
        input.push(event(
            6,
            "ExecutionReport",
            &[
                ("clOrdId", "C-A-1"),
                ("execId", "X-1"),
                ("execType", "F"),
                ("lastPx", "15000"),
                ("lastQty", "1000"),
                ("ordStatus", "2"),
                ("venueMic", "XNAS"),
            ],
        ));
        let out = run(&input, &mut ids);

        let route = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "route")
            .expect("a route transition");
        assert_eq!(route.fields["event"], "RouteFilled");
        assert_eq!(route.fields["to"], "FILLED");

        let order = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "order")
            .expect("an order transition");
        assert_eq!(order.fields["event"], "FullFill");
        assert_eq!(order.fields["to"], "FILLED");
    }

    /// `ExecType=F` needs `OrdStatus` to disambiguate: 2 is the final fill, anything
    /// else leaves the route open. Getting this wrong strands quantity forever.
    #[test]
    fn a_trade_with_leaves_is_a_partial_fill() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "1000"));
        input.push(event(
            5,
            "ExecutionReport",
            &[("clOrdId", "C-A-1"), ("execType", "0"), ("ordStatus", "0")],
        ));
        input.push(event(
            6,
            "ExecutionReport",
            &[
                ("clOrdId", "C-A-1"),
                ("execId", "X-1"),
                ("execType", "F"),
                ("lastPx", "15000"),
                ("lastQty", "100"),
                ("ordStatus", "1"),
            ],
        ));
        let out = run(&input, &mut ids);

        let route = out
            .iter()
            .rfind(|e| e.event_type == "FsmTransition" && e.fields["fsm"] == "route")
            .expect("a route transition");
        assert_eq!(route.fields["event"], "RoutePartiallyFilled");
        assert_eq!(route.fields["to"], "PARTIALLY_FILLED");
    }

    #[test]
    fn an_unmapped_exec_type_is_ignored() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "400"));
        input.push(event(
            5,
            "ExecutionReport",
            &[("clOrdId", "C-A-1"), ("execType", "Z")],
        ));
        let out = run(&input, &mut ids);

        let ignored = nth_of(&out, "ExecutionReportIgnored", 0);
        assert_eq!(ignored.fields["reason"], "unmapped ExecType");
    }

    #[test]
    fn a_report_for_an_unknown_cl_ord_id_is_ignored() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(event(
            5,
            "ExecutionReport",
            &[("clOrdId", "NOT-A-ROUTE"), ("execType", "0")],
        ));
        let out = run(&input, &mut ids);

        let ignored = nth_of(&out, "ExecutionReportIgnored", 0);
        assert_eq!(ignored.fields["reason"], "unknown route ClOrdID");
    }

    // ── Allocation (component 9) ─────────────────────────────────────────────

    /// A filled order ready to allocate.
    fn filled_order() -> Vec<JournalEvent> {
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-A", "1000"));
        input.push(event(
            5,
            "ExecutionReport",
            &[("clOrdId", "C-A-1"), ("execType", "0"), ("ordStatus", "0")],
        ));
        input.push(event(
            6,
            "ExecutionReport",
            &[
                ("clOrdId", "C-A-1"),
                ("execId", "X-1"),
                ("execType", "F"),
                ("lastPx", "15000"),
                ("lastQty", "1000"),
                ("ordStatus", "2"),
            ],
        ));
        input
    }

    fn allocations(out: &[JournalEvent]) -> Vec<(String, u64)> {
        out.iter()
            .filter(|e| e.event_type == "AllocationRecord")
            .map(|e| {
                (
                    e.fields["account"].clone(),
                    e.fields["qty"].parse::<u64>().unwrap(),
                )
            })
            .collect()
    }

    /// Conservation: the parts sum exactly to the filled quantity, whatever the
    /// weights. Floors alone under-allocate; naive rounding over-allocates.
    #[test]
    fn allocations_sum_exactly_to_the_filled_quantity() {
        let mut ids = DeterministicIds::new(0);
        let mut input = filled_order();
        input.push(event(
            7,
            "Allocate",
            &[("clOrdId", "C-A"), ("shares", "A:3333,B:3333,C:3334")],
        ));
        let out = run(&input, &mut ids);

        let allocs = allocations(&out);
        assert_eq!(allocs.iter().map(|(_, q)| q).sum::<u64>(), 1000);
    }

    /// The tie-break chain, end to end: equal remainders, equal weights —
    /// instruction order decides, and the first account gets the odd lot.
    #[test]
    fn a_full_tie_is_broken_by_instruction_order() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "5");
        input.push(route_new(4, "C-A", "5"));
        input.push(event(
            5,
            "ExecutionReport",
            &[("clOrdId", "C-A-1"), ("execType", "0"), ("ordStatus", "0")],
        ));
        input.push(event(
            6,
            "ExecutionReport",
            &[
                ("clOrdId", "C-A-1"),
                ("execId", "X-1"),
                ("execType", "F"),
                ("lastPx", "15000"),
                ("lastQty", "5"),
                ("ordStatus", "2"),
            ],
        ));
        input.push(event(
            7,
            "Allocate",
            &[("clOrdId", "C-A"), ("shares", "FIRST:5000,SECOND:5000")],
        ));
        let out = run(&input, &mut ids);

        assert_eq!(
            allocations(&out),
            vec![("FIRST".to_owned(), 3), ("SECOND".to_owned(), 2)]
        );
    }

    /// An unfilled order has nothing to allocate — 6002, not an empty success.
    #[test]
    fn an_unfilled_order_cannot_be_allocated() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(event(
            5,
            "Allocate",
            &[("clOrdId", "C-A"), ("shares", "A:10000")],
        ));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "AllocationRejected", 0).fields["code"],
            "EMS-ALC-6002"
        );
        assert_eq!(count_of(&out, "AllocationRecord"), 0);
    }

    /// Malformed share entries are dropped; a list with nothing left is 6003.
    #[test]
    fn a_share_list_with_nothing_usable_is_refused() {
        let mut ids = DeterministicIds::new(0);
        let mut input = filled_order();
        input.push(event(
            7,
            "Allocate",
            &[("clOrdId", "C-A"), ("shares", "garbage,x:notanumber,:5000")],
        ));
        let out = run(&input, &mut ids);

        assert_eq!(
            nth_of(&out, "AllocationRejected", 0).fields["code"],
            "EMS-ALC-6003"
        );
    }

    /// The routing analogue of `a_rejected_order_does_not_consume_an_identifier`:
    /// if refusals burned route ids, every identifier downstream of a refusal
    /// would shift — which is why the `ClOrdID` check runs before an id is drawn.
    #[test]
    fn a_refused_route_does_not_consume_an_identifier() {
        let mut ids = DeterministicIds::new(0);
        let mut input = routable_order("C-A", "1000");
        input.push(route_new(4, "C-NOPE", "100"));
        input.push(route_new(5, "C-A", "9999"));
        input.push(route_new(6, "C-A", "100"));
        let out = run(&input, &mut ids);

        assert_eq!(count_of(&out, "RouteRejected"), 2);
        assert_eq!(
            nth_of(&out, "RouteAccepted", 0).fields["routeId"],
            "RTE-0000000001"
        );
    }
}
