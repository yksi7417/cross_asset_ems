//! Behavioural tests over the generated order FSM.
//!
//! These test the *generator's contract*, which is why they live here rather
//! than in the Python suite: they fail if a future emitter change breaks the
//! shape the rest of the port depends on.

#![allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]

use ems_fsm::{OrderFsmContext, OrderFsmEvent, OrderFsmPayload, OrderFsmState};

fn ctx() -> OrderFsmContext {
    OrderFsmContext::default()
}

#[test]
fn initial_state_is_pending_new() {
    assert_eq!(OrderFsmState::initial(), OrderFsmState::PendingNew);
}

#[test]
fn validation_passed_moves_pending_new_to_new() {
    let result = OrderFsmState::PendingNew.apply(OrderFsmEvent::ValidationPassed, &ctx(), None);

    assert_eq!(result.new_state, OrderFsmState::New);
    assert!(!result.is_no_transition);
}

#[test]
fn validation_failed_moves_pending_new_to_rejected() {
    let result = OrderFsmState::PendingNew.apply(OrderFsmEvent::ValidationFailed, &ctx(), None);

    assert_eq!(result.new_state, OrderFsmState::Rejected);
}

#[test]
fn an_event_with_no_matching_transition_is_ignored_not_an_error() {
    // A venue can send anything. An unexpected event leaves the machine where
    // it was — it is not a panic and not a rejection.
    let result = OrderFsmState::PendingNew.apply(OrderFsmEvent::CancelAccepted, &ctx(), None);

    assert_eq!(result.new_state, OrderFsmState::PendingNew);
    assert!(result.is_no_transition);
}

#[test]
fn state_names_match_the_schema_spelling() {
    // These strings reach the output journal, so they must match Java's enum
    // constants character for character.
    assert_eq!(OrderFsmState::PendingNew.name(), "PENDING_NEW");
    assert_eq!(OrderFsmState::New.name(), "NEW");
    assert_eq!(OrderFsmState::PartiallyFilled.name(), "PARTIALLY_FILLED");
    assert_eq!(OrderFsmState::DoneForDay.name(), "DONE_FOR_DAY");
}

#[test]
fn terminal_states_are_marked_terminal() {
    assert!(OrderFsmState::Canceled.is_terminal());
    assert!(OrderFsmState::Rejected.is_terminal());
    assert!(OrderFsmState::Expired.is_terminal());
    assert!(!OrderFsmState::New.is_terminal());
    // FILLED is deliberately NOT terminal in the schema: a fill can still be
    // busted or corrected afterwards.
    assert!(!OrderFsmState::Filled.is_terminal());
}

#[test]
fn a_terminal_state_accepts_nothing() {
    for event in [
        OrderFsmEvent::ValidationPassed,
        OrderFsmEvent::CancelRequested,
        OrderFsmEvent::CancelAccepted,
    ] {
        let result = OrderFsmState::Canceled.apply(event, &ctx(), None);
        assert!(result.is_no_transition, "{event:?} moved a terminal state");
    }
}

#[test]
fn a_payload_carrying_transition_updates_the_context() {
    let mut before = ctx();
    before.order_qty = 1000;
    before.leaves_qty = 1000;
    before.cum_qty = 0;

    let result = OrderFsmState::New.apply(
        OrderFsmEvent::PartialFill,
        &before,
        Some(&OrderFsmPayload::PartialFill(
            ems_fsm::generated::order_fsm::PartialFillPayload {
                last_qty: 400,
                last_px: 1_250_000,
                exec_id: "EXE-0000000001".to_owned(),
            },
        )),
    );

    assert_eq!(result.new_state, OrderFsmState::PartiallyFilled);
    assert_eq!(result.new_context.cum_qty, 400);
    assert_eq!(result.new_context.leaves_qty, 600);
}

#[test]
fn a_payload_carrying_transition_without_its_payload_is_a_no_transition() {
    // Malformed input is data, not a defect: the transition cannot be computed,
    // so nothing happens. A panic here would be a denial-of-service on a venue
    // sending a truncated message.
    let result = OrderFsmState::New.apply(OrderFsmEvent::PartialFill, &ctx(), None);

    assert!(result.is_no_transition);
    assert_eq!(result.new_state, OrderFsmState::New);
}

#[test]
fn a_guarded_transition_picks_the_arm_whose_guard_holds() {
    // A rejected replace returns the order to wherever it was before the
    // replace was requested, which pre_replace_status records: '0' → NEW,
    // '5' → REPLACED. Two arms, one event, distinguished only by the guard.
    let mut from_new = ctx();
    from_new.pre_replace_status = Some("0".to_owned());
    assert_eq!(
        OrderFsmState::PendingReplace
            .apply(OrderFsmEvent::ReplaceRejected, &from_new, None)
            .new_state,
        OrderFsmState::New
    );

    let mut from_replaced = ctx();
    from_replaced.pre_replace_status = Some("5".to_owned());
    assert_eq!(
        OrderFsmState::PendingReplace
            .apply(OrderFsmEvent::ReplaceRejected, &from_replaced, None)
            .new_state,
        OrderFsmState::Replaced
    );
}

#[test]
fn a_guard_that_holds_for_no_arm_is_a_no_transition() {
    // Both ReplaceRejected arms are guarded, so an unrecognised
    // pre_replace_status matches neither and the machine stays put rather than
    // falling through to an arbitrary arm.
    let mut unknown = ctx();
    unknown.pre_replace_status = Some("ZZ".to_owned());

    let result =
        OrderFsmState::PendingReplace.apply(OrderFsmEvent::ReplaceRejected, &unknown, None);

    assert!(result.is_no_transition);
    assert_eq!(result.new_state, OrderFsmState::PendingReplace);
}

#[test]
fn an_unguarded_transition_fires_regardless_of_context() {
    // ReplaceAccepted out of PENDING_REPLACE has no guard: it always goes to
    // REPLACED, whatever pre_replace_status happens to hold.
    let mut any = ctx();
    any.pre_replace_status = Some("ZZ".to_owned());

    assert_eq!(
        OrderFsmState::PendingReplace
            .apply(OrderFsmEvent::ReplaceAccepted, &any, None)
            .new_state,
        OrderFsmState::Replaced
    );
}

#[test]
fn the_context_is_not_mutated_in_place() {
    let before = ctx();
    let result = OrderFsmState::PendingNew.apply(OrderFsmEvent::ValidationPassed, &before, None);

    // `apply` takes &ctx and returns a new one. Replay depends on the input
    // journal producing the same output every time, which an in-place mutation
    // would quietly break.
    assert_eq!(before, ctx());
    let _ = result;
}
