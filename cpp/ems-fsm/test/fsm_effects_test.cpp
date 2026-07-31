// Behavioural tests over the effects the generated route FSM returns.
//
// The same assertions as rust/ems-fsm/tests/route_fsm_effects_test.rs. Effects
// are how a transition tells its caller to do something the machine cannot —
// most importantly cascade an event to another machine — and the slice reads
// them to drive the order FSM from route events.
//
// Until this file existed, ems-fsm's only C++ test was a compile-verify with no
// assertions in it. A header that compiles is not a header that behaves.

#include <gtest/gtest.h>

#include <algorithm>
#include <string_view>
#include <vector>

#include <ems_fsm/ems_fsm.hpp>

namespace {

using crossasset::ems::fsm::RouteFsmContext;
using crossasset::ems::fsm::RouteFsmEffectKind;
using crossasset::ems::fsm::RouteFsmEvent;
using crossasset::ems::fsm::RouteFsmState;
using crossasset::ems::fsm::transition;

/// The `(target, event)` pairs a result cascades, in order.
///
/// The Rust generator emits this as a method on the result. C++ gets a free
/// function in the test: adding it to the generated struct would mean the
/// header owned a `std::vector`, and the point of the span design is that the
/// header allocates nothing.
std::vector<std::pair<std::string_view, std::string_view>> emitted_events(
    const crossasset::ems::fsm::RouteFsmTransitionResult& result) {
    std::vector<std::pair<std::string_view, std::string_view>> out;
    for (const auto& effect : result.effects) {
        if (effect.kind == RouteFsmEffectKind::EmitEvent) {
            out.emplace_back(effect.targetFsm, effect.event);
        }
    }
    return out;
}

/// The schema declares two effects on SENT -RouteAcknowledged-> WORKING, in that
/// order. Order is part of the contract: a log record written after the cascade
/// reads differently from one written before it.
TEST(RouteFsmEffects, EffectsComeBackInSchemaOrder) {
    const RouteFsmContext ctx;
    const auto result = transition(RouteFsmState::SENT, RouteFsmEvent::RouteAcknowledged, ctx);

    ASSERT_EQ(result.effects.size(), 2U);
    EXPECT_EQ(result.effects[0].kind, RouteFsmEffectKind::PublishEventLog);
    EXPECT_EQ(result.effects[0].event, "RouteWorking");
    EXPECT_EQ(result.effects[1].kind, RouteFsmEffectKind::EmitEvent);
    EXPECT_EQ(result.effects[1].targetFsm, "OrderFsm");
    EXPECT_EQ(result.effects[1].event, "ValidationPassed");
}

/// The reason effects exist at all: a route reaching WORKING tells the order
/// machine so, and the runner must not have to know that mapping itself.
TEST(RouteFsmEffects, ARouteAcknowledgementCascadesToTheOrderMachine) {
    const RouteFsmContext ctx;
    const auto result = transition(RouteFsmState::SENT, RouteFsmEvent::RouteAcknowledged, ctx);

    const auto events = emitted_events(result);
    ASSERT_EQ(events.size(), 1U);
    EXPECT_EQ(events[0].first, "OrderFsm");
    EXPECT_EQ(events[0].second, "ValidationPassed");
}

TEST(RouteFsmEffects, NonCascadingEffectsAreNotEmittedEvents) {
    const RouteFsmContext ctx;
    const auto result = transition(RouteFsmState::PENDING, RouteFsmEvent::RouteSent, ctx);

    // RouteSent declares a log record and nothing else.
    EXPECT_EQ(result.effects.size(), 1U);
    EXPECT_TRUE(emitted_events(result).empty());
}

/// **A no-transition asks for nothing.** The negative case that matters: if a
/// declined event still returned effects, the slice would cascade an event the
/// machine explicitly refused, and the order machine would move on a route event
/// that never happened.
TEST(RouteFsmEffects, ANoTransitionCarriesNoEffects) {
    const RouteFsmContext ctx;
    // A route in PENDING has exactly one rule, RouteSent. Anything else is ignored.
    const auto result = transition(RouteFsmState::PENDING, RouteFsmEvent::RouteFilled, ctx);

    EXPECT_TRUE(result.isNoTransition);
    EXPECT_TRUE(result.effects.empty());
}

/// A terminal state has no rules at all — a different generator code path from
/// "no rule matched this event".
TEST(RouteFsmEffects, ATerminalStateCarriesNoEffects) {
    const RouteFsmContext ctx;
    const auto result = transition(RouteFsmState::CANCELED, RouteFsmEvent::RouteFilled, ctx);

    EXPECT_TRUE(result.isNoTransition);
    EXPECT_TRUE(result.effects.empty());
}

/// A guarded transition carries the effects of the arm that fired, not of the
/// first arm written for that event. PENDING_CANCEL_AT_VENUE has two
/// RouteCancelRejected rows distinguished only by preCancelStatus.
TEST(RouteFsmEffects, AGuardedTransitionCarriesItsOwnArmsEffects) {
    RouteFsmContext ctx;
    ctx.preCancelStatus = "1";
    const auto result =
        transition(RouteFsmState::PENDING_CANCEL_AT_VENUE, RouteFsmEvent::RouteCancelRejected, ctx);

    EXPECT_EQ(result.newState, RouteFsmState::PARTIALLY_FILLED);
    const auto events = emitted_events(result);
    ASSERT_EQ(events.size(), 1U);
    EXPECT_EQ(events[0].second, "CancelRejected");
}

/// Effects with an args map survive as pairs. RouteAnomaly notifies ops, and the
/// channel it names is the part an operator would actually need.
TEST(RouteFsmEffects, AnEffectWithArgumentsKeepsThem) {
    const RouteFsmContext ctx;
    const auto result = transition(RouteFsmState::WORKING, RouteFsmEvent::RouteAnomaly, ctx);

    const auto notify = std::find_if(result.effects.begin(), result.effects.end(),
                                     [](const auto& effect) {
                                         return effect.kind == RouteFsmEffectKind::Notify;
                                     });
    ASSERT_NE(notify, result.effects.end());
    const auto channel = std::find_if(notify->args.begin(), notify->args.end(),
                                      [](const auto& arg) { return arg.key == "channel"; });
    ASSERT_NE(channel, notify->args.end());
    EXPECT_EQ(channel->value, "ops-alerts");
}

/// The span points at static storage, so it stays valid after the result that
/// returned it is gone.
///
/// Rust checks this: its `effects` is `&'static [T]`, and a test there can bind
/// one to a `&'static` and let the compiler prove it. Here the same property is
/// only *observed* — nothing in the type system says the span does not dangle,
/// and a future emitter returning a span over a local would compile and fail at
/// run time under ASan rather than at the keyboard.
TEST(RouteFsmEffects, EffectsOutliveTheResultThatReturnedThem) {
    std::span<const crossasset::ems::fsm::RouteFsmEffect> effects;
    {
        const RouteFsmContext ctx;
        const auto result = transition(RouteFsmState::SENT, RouteFsmEvent::RouteAcknowledged, ctx);
        effects = result.effects;
    }

    ASSERT_EQ(effects.size(), 2U);
    EXPECT_EQ(effects[1].event, "ValidationPassed");
}

}  // namespace
