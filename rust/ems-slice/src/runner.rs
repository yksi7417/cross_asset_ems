//! The slice, as far as it has been built.

use std::collections::BTreeMap;

use ems_core::{DeterministicIds, JournalEvent};

/// Input event that opens an order.
const TYPE_ORDER_NEW: &str = "OrderNew";
/// Output event acknowledging one.
const TYPE_ORDER_ACCEPTED: &str = "OrderAccepted";
/// Final output event: makes the seed and the input size visible in the journal itself.
const TYPE_RUN_SUMMARY: &str = "RunSummary";

/// Fields copied from `OrderNew` onto `OrderAccepted`.
///
/// An explicit list rather than "copy everything": an unknown field silently
/// reaching the output would be a divergence that only shows up once some other
/// language's map happens to order it differently.
const ECHOED_FIELDS: [&str; 5] = ["account", "figi", "price", "qty", "side"];

/// Runs the slice over `input`, returning the output journal.
///
/// **Today this covers component 1 only**: the journal codec and deterministic
/// identifiers. An `OrderNew` becomes an `OrderAccepted` carrying a generated
/// order id; everything else passes through with its sequence renumbered; a
/// `RunSummary` closes the journal. There is no validation, no FSM, no routing
/// and no venue — those are later components, and pretending otherwise in the
/// output would make the conformance corpus lie about what is implemented.
///
/// Kept in lockstep with `java/ems-it/.../SliceRunner.java` and
/// `cpp/ems-it/src/slice_runner.cpp`.
#[must_use]
pub fn run(input: &[JournalEvent], ids: &mut DeterministicIds) -> Vec<JournalEvent> {
    let mut output = Vec::with_capacity(input.len() + 1);
    let mut seq: u64 = 0;

    for event in input {
        if event.event_type == TYPE_ORDER_NEW {
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
        } else {
            seq += 1;
            output.push(event.with_seq(seq));
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
    fn order_new_becomes_order_accepted_with_a_generated_id() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[event(1, "OrderNew", &[("account", "ACC1"), ("qty", "100")])],
            &mut ids,
        );

        assert_eq!(out.len(), 2);
        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"account\":\"ACC1\",\"orderId\":\"ORD-0000000001\",\"qty\":\"100\"},\
             \"seq\":1,\"type\":\"OrderAccepted\"}"
        );
    }

    #[test]
    fn unrecognised_fields_are_not_echoed() {
        // Only the agreed field list crosses to the output. A stray field
        // reaching the journal would diverge the moment another language
        // ordered it differently.
        let mut ids = DeterministicIds::new(0);
        let out = run(&[event(1, "OrderNew", &[("surprise", "x")])], &mut ids);

        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"orderId\":\"ORD-0000000001\"},\"seq\":1,\"type\":\"OrderAccepted\"}"
        );
    }

    #[test]
    fn other_event_types_pass_through_with_sequence_renumbered() {
        let mut ids = DeterministicIds::new(0);
        let out = run(&[event(41, "Heartbeat", &[("note", "hi")])], &mut ids);

        assert_eq!(out.len(), 2);
        assert_eq!(
            encode(&out[0]),
            "{\"fields\":{\"note\":\"hi\"},\"seq\":1,\"type\":\"Heartbeat\"}"
        );
    }

    #[test]
    fn seed_shifts_generated_identifiers() {
        let mut ids = DeterministicIds::new(41);
        let out = run(&[event(1, "OrderNew", &[])], &mut ids);

        assert_eq!(out[0].fields["orderId"], "ORD-0000000042");
        assert_eq!(out[1].fields["seed"], "41");
    }

    #[test]
    fn output_sequence_is_contiguous_from_one() {
        let mut ids = DeterministicIds::new(0);
        let out = run(
            &[event(7, "OrderNew", &[]), event(9, "Heartbeat", &[])],
            &mut ids,
        );

        assert_eq!(out.len(), 3);
        assert_eq!(out[0].seq, 1);
        assert_eq!(out[1].seq, 2);
        assert_eq!(out[2].seq, 3);
    }
}
