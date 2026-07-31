//! Behavioural tests over the effects the generated route FSM returns.
//!
//! Effects are how a transition tells its caller to do something the machine
//! itself cannot — most importantly cascade an event to another machine. The
//! slice reads them to drive the order FSM from route events, so the shape here
//! is a contract the runner depends on, not an implementation detail.
//!
//! These live alongside the other generator-contract tests: they fail if a
//! future emitter change breaks the shape the rest of the port relies on.

#![allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]

use ems_fsm::generated::route_fsm::RouteFsmEffect;
use ems_fsm::{RouteFsmContext, RouteFsmEvent, RouteFsmState};

fn ctx() -> RouteFsmContext {
    RouteFsmContext::default()
}

/// The schema declares two effects on `SENT -RouteAcknowledged-> WORKING`, in
/// that order. Order is part of the contract: a log record written after the
/// cascade reads differently from one written before it.
#[test]
fn effects_come_back_in_schema_order() {
    let result = RouteFsmState::Sent.apply(RouteFsmEvent::RouteAcknowledged, &ctx(), None);

    assert_eq!(
        result.effects,
        &[
            RouteFsmEffect::PublishEventLog {
                event: "RouteWorking"
            },
            RouteFsmEffect::EmitEvent {
                target_fsm: "OrderFsm",
                event: "ValidationPassed"
            },
        ]
    );
}

/// The reason effects exist at all: a route reaching `WORKING` tells the order
/// machine so, and the runner must not have to know that mapping itself.
#[test]
fn a_route_acknowledgement_cascades_to_the_order_machine() {
    let result = RouteFsmState::Sent.apply(RouteFsmEvent::RouteAcknowledged, &ctx(), None);

    assert_eq!(
        result.emitted_events().collect::<Vec<_>>(),
        vec![("OrderFsm", "ValidationPassed")]
    );
}

/// `emitted_events` skips effects that are not cascades, so a caller that only
/// handles cascades does not have to match on variants it ignores.
#[test]
fn emitted_events_skips_non_cascading_effects() {
    let result = RouteFsmState::Pending.apply(RouteFsmEvent::RouteSent, &ctx(), None);

    // RouteSent declares a log record and nothing else.
    assert_eq!(result.effects.len(), 1);
    assert_eq!(result.emitted_events().count(), 0);
}

/// **A no-transition asks for nothing.** This is the negative case that matters:
/// if a declined event still returned effects, the slice would cascade an event
/// the machine explicitly refused — and the order machine would move on a route
/// event that never happened.
#[test]
fn a_no_transition_carries_no_effects() {
    // A route in PENDING has exactly one rule, RouteSent. Anything else is
    // ignored.
    let result = RouteFsmState::Pending.apply(RouteFsmEvent::RouteFilled, &ctx(), None);

    assert!(result.is_no_transition);
    assert!(result.effects.is_empty());
    assert_eq!(result.emitted_events().count(), 0);
}

/// A terminal state has no rules at all, which is a different code path in the
/// generator from "no rule matched this event".
#[test]
fn a_terminal_state_carries_no_effects() {
    let result = RouteFsmState::Canceled.apply(RouteFsmEvent::RouteFilled, &ctx(), None);

    assert!(result.is_no_transition);
    assert!(result.effects.is_empty());
}

/// A guarded transition carries the effects of the arm that actually fired, not
/// of the first arm written for that event. `PENDING_CANCEL_AT_VENUE` has two
/// `RouteCancelRejected` rows distinguished only by `pre_cancel_status`.
#[test]
fn a_guarded_transition_carries_its_own_arms_effects() {
    let mut partially_filled = ctx();
    partially_filled.pre_cancel_status = Some("1".to_owned());

    let result = RouteFsmState::PendingCancelAtVenue.apply(
        RouteFsmEvent::RouteCancelRejected,
        &partially_filled,
        None,
    );

    assert_eq!(result.new_state, RouteFsmState::PartiallyFilled);
    assert_eq!(
        result.emitted_events().collect::<Vec<_>>(),
        vec![("OrderFsm", "CancelRejected")]
    );
}

/// Effects with an `args` map survive as pairs. `RouteAnomaly` notifies ops, and
/// the channel it names is the part an operator would actually need.
#[test]
fn an_effect_with_arguments_keeps_them() {
    let result = RouteFsmState::Working.apply(RouteFsmEvent::RouteAnomaly, &ctx(), None);

    let notify = result
        .effects
        .iter()
        .find_map(|effect| match effect {
            RouteFsmEffect::Notify(args) => Some(*args),
            _ => None,
        })
        .expect("RouteAnomaly declares a notify effect");

    assert!(notify.contains(&("channel", "ops-alerts")));
}

/// The effects slice is `&'static`, so it outlives the result that carried it.
/// That is the property that makes returning static tables safe, and it is
/// checked by the compiler here rather than promised in a comment.
#[test]
fn effects_outlive_the_result_that_returned_them() {
    let effects: &'static [RouteFsmEffect] = {
        let result = RouteFsmState::Sent.apply(RouteFsmEvent::RouteAcknowledged, &ctx(), None);
        result.effects
    };

    assert_eq!(effects.len(), 2);
}
