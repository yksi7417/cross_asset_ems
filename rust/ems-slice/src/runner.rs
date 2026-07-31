//! The slice, as far as it has been built.

use std::collections::{BTreeMap, BTreeSet};

use ems_aaa::{AaaService, Identity};
use ems_core::{DeterministicIds, JournalEvent};
use ems_fsm::generated::order_fsm::{FullFillPayload, PartialFillPayload};
use ems_fsm::{OrderFsmContext, OrderFsmEvent, OrderFsmPayload, OrderFsmState};
use ems_validator::{
    validate, InstrumentStatus, SecurityMaster, ValidationRequest, ValidationResult,
};

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
/// **Today this covers components 1–3**: the journal codec, deterministic
/// identifiers, the transport seam and the AAA entitlement decision. A
/// `SessionLogon` registers a session; an `OrderNew` is checked against it and
/// becomes either an `OrderAccepted` carrying a generated order id or an
/// `OrderRejected` carrying a catalog reject code.
///
/// There is still no validation pipeline, no FSM, no routing and no venue —
/// those are later components, and pretending otherwise in the output would
/// make the conformance corpus lie about what is implemented.
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
                seq = on_order_new(event, seq, &aaa, &securities, ids, &mut orders, &mut output);
            }
            TYPE_ORDER_EVENT => {
                seq = on_order_event(event, seq, &mut orders, &mut output);
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
    let mut fields = BTreeMap::new();
    fields.insert("orderId".to_owned(), ids.next_order_id());
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
}
