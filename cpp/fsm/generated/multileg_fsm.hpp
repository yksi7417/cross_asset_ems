// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multilegfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <array>
#include <cstdint>
#include <string>
#include <optional>
#include <span>
#include <string_view>
#include <variant>
#include <vector>

namespace crossasset::ems::fsm {

// ── States ──────────────────────────────────────────────────────────────────
enum class MultiLegFsmState : uint8_t {
  STAGED,
  READY,
  LEGS_WORKING,
  FILLED,
  PARTIALLY_FILLED,
  CANCELED,
  REJECTED,
};

// ── Events ───────────────────────────────────────────────────────────────────
enum class MultiLegFsmEvent : uint16_t {
  LegsValidated,
  LegsValidationFailed,
  FirstLegDispatched,
  LegFilled,
  LegPartiallyFilled,
  LegRejected,
  LegCanceled,
  CancelRequested,
};

// ── Names ────────────────────────────────────────────────────────────────────
//
// State and event names reach the output journal, which the conformance gate
// compares byte-for-byte across three languages — so these must match the Java
// enum constants character for character.

inline const char* name(MultiLegFsmState state) noexcept {
  switch (state) {
    case MultiLegFsmState::STAGED: return "STAGED";
    case MultiLegFsmState::READY: return "READY";
    case MultiLegFsmState::LEGS_WORKING: return "LEGS_WORKING";
    case MultiLegFsmState::FILLED: return "FILLED";
    case MultiLegFsmState::PARTIALLY_FILLED: return "PARTIALLY_FILLED";
    case MultiLegFsmState::CANCELED: return "CANCELED";
    case MultiLegFsmState::REJECTED: return "REJECTED";
  }
  return "UNKNOWN";
}

inline const char* name(MultiLegFsmEvent event) noexcept {
  switch (event) {
    case MultiLegFsmEvent::LegsValidated: return "LegsValidated";
    case MultiLegFsmEvent::LegsValidationFailed: return "LegsValidationFailed";
    case MultiLegFsmEvent::FirstLegDispatched: return "FirstLegDispatched";
    case MultiLegFsmEvent::LegFilled: return "LegFilled";
    case MultiLegFsmEvent::LegPartiallyFilled: return "LegPartiallyFilled";
    case MultiLegFsmEvent::LegRejected: return "LegRejected";
    case MultiLegFsmEvent::LegCanceled: return "LegCanceled";
    case MultiLegFsmEvent::CancelRequested: return "CancelRequested";
  }
  return "UNKNOWN";
}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<MultiLegFsmEvent> MultiLegFsmEventFromName(std::string_view name) {
  if (name == "LegsValidated") return MultiLegFsmEvent::LegsValidated;
  if (name == "LegsValidationFailed") return MultiLegFsmEvent::LegsValidationFailed;
  if (name == "FirstLegDispatched") return MultiLegFsmEvent::FirstLegDispatched;
  if (name == "LegFilled") return MultiLegFsmEvent::LegFilled;
  if (name == "LegPartiallyFilled") return MultiLegFsmEvent::LegPartiallyFilled;
  if (name == "LegRejected") return MultiLegFsmEvent::LegRejected;
  if (name == "LegCanceled") return MultiLegFsmEvent::LegCanceled;
  if (name == "CancelRequested") return MultiLegFsmEvent::CancelRequested;
  return std::nullopt;
}

// ── Context ───────────────────────────────────────────────────────────────────
struct MultiLegFsmContext {
  std::string orderId{};
  std::string multilegKind{};
  std::string executionMode{};
  uint32_t totalLegs{};
  uint32_t legsFilled{};
  uint32_t legsRejected{};
  uint32_t legsCanceled{};
  std::optional<std::string> packageId{};
};

// ── Effects ──────────────────────────────────────────────────────────────────
//
// A side effect a transition asks for, as the schema declares it.
//
// STUDY: effects-as-static-data
//
// Every value comes from the YAML, so a transition's effects are compile-time
// data: the generator emits `static constexpr` arrays and `transition` returns a
// span over one of them. Nothing is allocated and nothing has to be kept alive.
//
// The honest caveat, which Rust does not have: this is a struct with a `kind`
// tag, not a sum type. An `EmitEvent` still *has* an `args` field and a
// `PublishFixMessage` still has a `targetFsm` — both empty, and nothing stops a
// caller reading them. Java models this as a sealed interface of records and
// Rust as an enum, where both are unrepresentable. `std::variant` could express
// it, at the cost of every read becoming a visit; the tag was chosen because the
// only consumer switches on the kind anyway.
//
// The kind and arg types are prefixed like everything else here for a concrete
// reason: a translation unit that drives two machines includes two of these
// headers, and an unprefixed `FsmEffectKind` in the shared namespace would be a
// redefinition. Rust has no such problem — each machine is its own module.
enum class MultiLegFsmEffectKind : uint8_t {
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
};

/// One `key: value` pair from an effect's `args` map in the schema.
struct MultiLegFsmEffectArg {
  std::string_view key{};
  std::string_view value{};
};

struct MultiLegFsmEffect {
  MultiLegFsmEffectKind kind{};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const MultiLegFsmEffectArg> args{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct MultiLegFsmTransitionResult {
  MultiLegFsmState newState;
  MultiLegFsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const MultiLegFsmEffect> effects;
  bool isNoTransition;
};

// ── Payload structs ──────────────────────────────────────────────────────────
struct MultiLegFsmPayloads {
  struct LegFilledPayload {
    std::string legId{};
    uint64_t lastQty{};
    int64_t lastPx{};
  };
  struct LegPartiallyFilledPayload {
    std::string legId{};
    uint64_t lastQty{};
    int64_t lastPx{};
  };
  struct LegRejectedPayload {
    std::string legId{};
  };
  struct LegCanceledPayload {
    std::string legId{};
  };
};

// ── Effect tables ────────────────────────────────────────────────────────────
//
// One table per transition that asks for something. `transition` returns a span
// over the matching table, so the effects cost nothing to return and outlive any
// caller.
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects0 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegValidated", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 1> kMultiLegFsmEffectArgs1_0 = {{MultiLegFsmEffectArg{"msg_type", "j"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects1 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs1_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegRejected", {}}}};
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects2 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegExecutionStarted", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs3_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "4"}, MultiLegFsmEffectArg{"ord_status", "4"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects3 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs3_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegCanceled", {}}}};
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects4 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "LegPartiallyFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs5_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "F"}, MultiLegFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects5 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs5_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs6_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "F"}, MultiLegFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects6 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs6_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegPartiallyFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects7 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "LegFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs8_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "8"}, MultiLegFsmEffectArg{"ord_status", "8"}}};
inline constexpr std::array<MultiLegFsmEffect, 3> kMultiLegFsmEffects8 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs8_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegRejected", {}}, {MultiLegFsmEffectKind::EmitEvent, "RouteFsm", "RouteCancelRequested", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs9_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "8"}, MultiLegFsmEffectArg{"ord_status", "8"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects9 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs9_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegRejected", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs10_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "F"}, MultiLegFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects10 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs10_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegPartiallyFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs11_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "4"}, MultiLegFsmEffectArg{"ord_status", "4"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects11 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs11_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegCanceled", {}}}};
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects12 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "LegRejected", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs13_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "F"}, MultiLegFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects13 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs13_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegPartiallyFilled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs14_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "4"}, MultiLegFsmEffectArg{"ord_status", "4"}}};
inline constexpr std::array<MultiLegFsmEffect, 2> kMultiLegFsmEffects14 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs14_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegCanceled", {}}}};
inline constexpr std::array<MultiLegFsmEffect, 1> kMultiLegFsmEffects15 = {{{MultiLegFsmEffectKind::PublishEventLog, {}, "LegCanceled", {}}}};
inline constexpr std::array<MultiLegFsmEffectArg, 3> kMultiLegFsmEffectArgs16_0 = {{MultiLegFsmEffectArg{"msg_type", "8"}, MultiLegFsmEffectArg{"exec_type", "4"}, MultiLegFsmEffectArg{"ord_status", "4"}}};
inline constexpr std::array<MultiLegFsmEffect, 3> kMultiLegFsmEffects16 = {{{MultiLegFsmEffectKind::PublishFixMessage, {}, {}, kMultiLegFsmEffectArgs16_0}, {MultiLegFsmEffectKind::PublishEventLog, {}, "MultiLegCanceled", {}}, {MultiLegFsmEffectKind::EmitEvent, "RouteFsm", "RouteCancelRequested", {}}}};

// ── Transition implementation (inline) ──────────────────────────────────────
inline MultiLegFsmTransitionResult transition(
    MultiLegFsmState state,
    MultiLegFsmEvent event,
    const MultiLegFsmContext& ctx,
    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {
  switch (state) {
  case MultiLegFsmState::STAGED:
    switch (event) {
    case MultiLegFsmEvent::LegsValidated:
      return {MultiLegFsmState::READY, ctx, kMultiLegFsmEffects0, false};
    case MultiLegFsmEvent::LegsValidationFailed:
      return {MultiLegFsmState::REJECTED, ctx, kMultiLegFsmEffects1, false};
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::READY:
    switch (event) {
    case MultiLegFsmEvent::FirstLegDispatched:
      return {MultiLegFsmState::LEGS_WORKING, ctx, kMultiLegFsmEffects2, false};
    case MultiLegFsmEvent::CancelRequested:
      return {MultiLegFsmState::CANCELED, ctx, kMultiLegFsmEffects3, false};
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::LEGS_WORKING:
    switch (event) {
    case MultiLegFsmEvent::LegPartiallyFilled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegPartiallyFilledPayload*>(rawPayload);
      return {MultiLegFsmState::LEGS_WORKING, ctx, kMultiLegFsmEffects4, false};
    }
    case MultiLegFsmEvent::LegFilled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegFilledPayload*>(rawPayload);
      if ((((ctx.legsFilled + 1) == ctx.totalLegs) && (ctx.legsRejected == 0) && (ctx.legsCanceled == 0))) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::FILLED, newCtx, kMultiLegFsmEffects5, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && ((((ctx.legsFilled + 1) + ctx.legsRejected) + ctx.legsCanceled) == ctx.totalLegs) && (((ctx.legsRejected > 0) || (ctx.legsCanceled > 0))))) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, kMultiLegFsmEffects6, false};
      }
      if (((((ctx.legsFilled + 1) + ctx.legsRejected) + ctx.legsCanceled) < ctx.totalLegs)) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, kMultiLegFsmEffects7, false};
      }
      return {state, ctx, {}, true};
    }
    case MultiLegFsmEvent::LegRejected: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegRejectedPayload*>(rawPayload);
      if ((ctx.executionMode == "ALL_OR_NONE")) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::REJECTED, newCtx, kMultiLegFsmEffects8, false};
      }
      if ((ctx.executionMode == "SEQUENCED")) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::REJECTED, newCtx, kMultiLegFsmEffects9, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && (ctx.legsFilled > 0) && ((((ctx.legsFilled + ctx.legsRejected) + 1) + ctx.legsCanceled) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, kMultiLegFsmEffects10, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && (ctx.legsFilled == 0) && (((ctx.legsRejected + 1) + ctx.legsCanceled) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::CANCELED, newCtx, kMultiLegFsmEffects11, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && ((((ctx.legsFilled + ctx.legsRejected) + 1) + ctx.legsCanceled) < ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, kMultiLegFsmEffects12, false};
      }
      return {state, ctx, {}, true};
    }
    case MultiLegFsmEvent::LegCanceled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegCanceledPayload*>(rawPayload);
      if (((ctx.legsFilled > 0) && ((((ctx.legsFilled + ctx.legsRejected) + ctx.legsCanceled) + 1) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, kMultiLegFsmEffects13, false};
      }
      if (((ctx.legsFilled == 0) && (((ctx.legsRejected + ctx.legsCanceled) + 1) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::CANCELED, newCtx, kMultiLegFsmEffects14, false};
      }
      if (((((ctx.legsFilled + ctx.legsRejected) + ctx.legsCanceled) + 1) < ctx.totalLegs)) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, kMultiLegFsmEffects15, false};
      }
      return {state, ctx, {}, true};
    }
    case MultiLegFsmEvent::CancelRequested:
      return {MultiLegFsmState::CANCELED, ctx, kMultiLegFsmEffects16, false};
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::PARTIALLY_FILLED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case MultiLegFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  default:
    return {state, ctx, {}, true};
  }
  return {state, ctx, {}, true};
}

} // namespace crossasset::ems::fsm
