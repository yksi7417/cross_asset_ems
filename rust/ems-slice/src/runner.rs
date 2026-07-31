//! The slice, as far as it has been built.

use std::collections::{BTreeMap, BTreeSet};

use ems_aaa::{AaaService, AuthorizationResult, Identity, CODE_SESSION_NOT_FOUND};
use ems_core::{DeterministicIds, JournalEvent};

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
    let mut seq: u64 = 0;

    for event in input {
        seq += 1;
        let emitted = match event.event_type.as_str() {
            TYPE_SESSION_LOGON => on_session_logon(event, seq, &mut aaa),
            TYPE_ORDER_NEW => on_order_new(event, seq, &aaa, ids),
            _ => event.with_seq(seq),
        };
        output.push(emitted);
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

fn on_order_new(
    event: &JournalEvent,
    seq: u64,
    aaa: &AaaService,
    ids: &mut DeterministicIds,
) -> JournalEvent {
    let session_id = parse_session_id(event);
    let Some(session) = session_id.and_then(|id| aaa.session(id)) else {
        return reject(
            seq,
            event,
            CODE_SESSION_NOT_FOUND,
            "SES",
            &format!(
                "Session {} not found or has expired.",
                session_id_display(session_id)
            ),
        );
    };

    let tag = field(event, "tag");
    // The three-layer AND-gate decides this, and its reject code names the
    // outermost missing layer: firm (1003), desk (1002) or user (1001).
    if let AuthorizationResult::Deny { code, message, .. } = aaa.authorize(session, &tag) {
        return reject(seq, event, &code, "PRM", &message);
    }

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
    JournalEvent {
        seq,
        event_type: TYPE_ORDER_ACCEPTED.to_owned(),
        fields,
    }
}

fn reject(
    seq: u64,
    event: &JournalEvent,
    code: &str,
    category: &str,
    reason: &str,
) -> JournalEvent {
    let mut fields = BTreeMap::new();
    fields.insert("category".to_owned(), category.to_owned());
    fields.insert("code".to_owned(), code.to_owned());
    fields.insert("reason".to_owned(), reason.to_owned());
    fields.insert("sessionId".to_owned(), field(event, "sessionId"));
    JournalEvent {
        seq,
        event_type: TYPE_ORDER_REJECTED.to_owned(),
        fields,
    }
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

/// The id as it appears in a rejection reason.
fn session_id_display(session_id: Option<u64>) -> String {
    session_id_text(session_id)
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

        assert_eq!(
            encode(&out[1]),
            "{\"fields\":{\"account\":\"ACC1\",\"orderId\":\"ORD-0000000001\"},\
             \"seq\":2,\"type\":\"OrderAccepted\"}"
        );
    }

    #[test]
    fn order_without_a_known_session_is_rejected() {
        let mut ids = DeterministicIds::new(0);
        let out = run(&[event(1, "OrderNew", &[("sessionId", "99")])], &mut ids);

        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"category\":\"SES\",\"code\":\"EMS-SES-1002\",\
             \"reason\":\"Session 99 not found or has expired.\",\"sessionId\":\"99\"},\
             \"seq\":1,\"type\":\"OrderRejected\"}"
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
        let encoded = encode(&out[1]);
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
        assert_eq!(out[2].fields["orderId"], "ORD-0000000001");
    }

    #[test]
    fn a_non_numeric_session_id_is_a_rejection_not_a_panic() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[event(1, "OrderNew", &[("sessionId", "not-a-number")])],
            &mut ids,
        );

        assert_eq!(out[0].fields["code"], "EMS-SES-1002");
        assert_eq!(
            out[0].fields["reason"],
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

        assert_eq!(out[1].event_type, "OrderRejected");
        assert_eq!(out[3].event_type, "OrderAccepted");
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

        assert_eq!(out.len(), 3);
        assert_eq!(out[0].seq, 1);
        assert_eq!(out[1].seq, 2);
        assert_eq!(out[2].seq, 3);
    }
}
