// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/routefsm.fsm.yaml
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
enum class RouteFsmState : uint8_t {
  PENDING,
  SENT,
  PENDING_NEW_AT_VENUE,
  WORKING,
  PENDING_REPLACE_AT_VENUE,
  PENDING_CANCEL_AT_VENUE,
  PARTIALLY_FILLED,
  FILLED,
  CANCELED,
  REJECTED,
  EXPIRED,
  SUPERSEDED,
  ANOMALY,
};

// ── Events ───────────────────────────────────────────────────────────────────
enum class RouteFsmEvent : uint16_t {
  RouteSent,
  RoutePendingNewAtVenue,
  RouteAcknowledged,
  RouteRejected,
  RouteReplaceRequested,
  RouteReplacePendingAtVenue,
  RouteReplaced,
  RouteReplaceRejected,
  RouteCancelRequested,
  RouteCanceled,
  RouteCancelRejected,
  RoutePartiallyFilled,
  RouteFilled,
  RouteExpired,
  RouteSuperseded,
  RouteAnomaly,
};

// ── Names ────────────────────────────────────────────────────────────────────
//
// State and event names reach the output journal, which the conformance gate
// compares byte-for-byte across three languages — so these must match the Java
// enum constants character for character.

inline const char* name(RouteFsmState state) noexcept {
  switch (state) {
    case RouteFsmState::PENDING: return "PENDING";
    case RouteFsmState::SENT: return "SENT";
    case RouteFsmState::PENDING_NEW_AT_VENUE: return "PENDING_NEW_AT_VENUE";
    case RouteFsmState::WORKING: return "WORKING";
    case RouteFsmState::PENDING_REPLACE_AT_VENUE: return "PENDING_REPLACE_AT_VENUE";
    case RouteFsmState::PENDING_CANCEL_AT_VENUE: return "PENDING_CANCEL_AT_VENUE";
    case RouteFsmState::PARTIALLY_FILLED: return "PARTIALLY_FILLED";
    case RouteFsmState::FILLED: return "FILLED";
    case RouteFsmState::CANCELED: return "CANCELED";
    case RouteFsmState::REJECTED: return "REJECTED";
    case RouteFsmState::EXPIRED: return "EXPIRED";
    case RouteFsmState::SUPERSEDED: return "SUPERSEDED";
    case RouteFsmState::ANOMALY: return "ANOMALY";
  }
  return "UNKNOWN";
}

inline const char* name(RouteFsmEvent event) noexcept {
  switch (event) {
    case RouteFsmEvent::RouteSent: return "RouteSent";
    case RouteFsmEvent::RoutePendingNewAtVenue: return "RoutePendingNewAtVenue";
    case RouteFsmEvent::RouteAcknowledged: return "RouteAcknowledged";
    case RouteFsmEvent::RouteRejected: return "RouteRejected";
    case RouteFsmEvent::RouteReplaceRequested: return "RouteReplaceRequested";
    case RouteFsmEvent::RouteReplacePendingAtVenue: return "RouteReplacePendingAtVenue";
    case RouteFsmEvent::RouteReplaced: return "RouteReplaced";
    case RouteFsmEvent::RouteReplaceRejected: return "RouteReplaceRejected";
    case RouteFsmEvent::RouteCancelRequested: return "RouteCancelRequested";
    case RouteFsmEvent::RouteCanceled: return "RouteCanceled";
    case RouteFsmEvent::RouteCancelRejected: return "RouteCancelRejected";
    case RouteFsmEvent::RoutePartiallyFilled: return "RoutePartiallyFilled";
    case RouteFsmEvent::RouteFilled: return "RouteFilled";
    case RouteFsmEvent::RouteExpired: return "RouteExpired";
    case RouteFsmEvent::RouteSuperseded: return "RouteSuperseded";
    case RouteFsmEvent::RouteAnomaly: return "RouteAnomaly";
  }
  return "UNKNOWN";
}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<RouteFsmEvent> RouteFsmEventFromName(std::string_view name) {
  if (name == "RouteSent") return RouteFsmEvent::RouteSent;
  if (name == "RoutePendingNewAtVenue") return RouteFsmEvent::RoutePendingNewAtVenue;
  if (name == "RouteAcknowledged") return RouteFsmEvent::RouteAcknowledged;
  if (name == "RouteRejected") return RouteFsmEvent::RouteRejected;
  if (name == "RouteReplaceRequested") return RouteFsmEvent::RouteReplaceRequested;
  if (name == "RouteReplacePendingAtVenue") return RouteFsmEvent::RouteReplacePendingAtVenue;
  if (name == "RouteReplaced") return RouteFsmEvent::RouteReplaced;
  if (name == "RouteReplaceRejected") return RouteFsmEvent::RouteReplaceRejected;
  if (name == "RouteCancelRequested") return RouteFsmEvent::RouteCancelRequested;
  if (name == "RouteCanceled") return RouteFsmEvent::RouteCanceled;
  if (name == "RouteCancelRejected") return RouteFsmEvent::RouteCancelRejected;
  if (name == "RoutePartiallyFilled") return RouteFsmEvent::RoutePartiallyFilled;
  if (name == "RouteFilled") return RouteFsmEvent::RouteFilled;
  if (name == "RouteExpired") return RouteFsmEvent::RouteExpired;
  if (name == "RouteSuperseded") return RouteFsmEvent::RouteSuperseded;
  if (name == "RouteAnomaly") return RouteFsmEvent::RouteAnomaly;
  return std::nullopt;
}

// ── Context ───────────────────────────────────────────────────────────────────
struct RouteFsmContext {
  std::string routeId{};
  std::string orderId{};
  std::string clOrdId{};
  std::optional<std::string> origClOrdId{};
  std::string venueMic{};
  std::string instrumentId{};
  uint8_t side{};
  uint64_t routeQty{};
  std::optional<int64_t> price{};
  uint64_t cumQty{};
  uint64_t leavesQty{};
  uint64_t traceId{};
  std::string initialOrderId{};
  std::optional<std::string> preCancelStatus{};
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
enum class RouteFsmEffectKind : uint8_t {
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
};

/// One `key: value` pair from an effect's `args` map in the schema.
struct RouteFsmEffectArg {
  std::string_view key{};
  std::string_view value{};
};

struct RouteFsmEffect {
  RouteFsmEffectKind kind{};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const RouteFsmEffectArg> args{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct RouteFsmTransitionResult {
  RouteFsmState newState;
  RouteFsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const RouteFsmEffect> effects;
  bool isNoTransition;
};

// ── Payload structs ──────────────────────────────────────────────────────────
struct RouteFsmPayloads {
  struct RouteReplaceRequestedPayload {
    std::string newClOrdId{};
    uint64_t newRouteQty{};
    std::optional<int64_t> newPrice{};
  };
  struct RouteReplacedPayload {
    std::string newClOrdId{};
  };
  struct RouteReplaceRejectedPayload {
    uint8_t cxlRejReason{};
  };
  struct RouteCancelRejectedPayload {
    uint8_t cxlRejReason{};
  };
  struct RoutePartiallyFilledPayload {
    uint64_t lastQty{};
    int64_t lastPx{};
    std::string execId{};
  };
  struct RouteFilledPayload {
    uint64_t lastQty{};
    int64_t lastPx{};
    std::string execId{};
  };
};

// ── Effect tables ────────────────────────────────────────────────────────────
//
// One table per transition that asks for something. `transition` returns a span
// over the matching table, so the effects cost nothing to return and outlive any
// caller.
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects0 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteSent", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects1 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RoutePendingNewAtVenue", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects2 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteWorking", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ValidationPassed", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects3 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteWorking", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ValidationPassed", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects4 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteRejected", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ValidationFailed", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects5 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteReplaceRequested", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects6 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteReplacePendingAtVenue", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects7 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteReplaced", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ReplaceAccepted", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects8 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteReplaceRejected", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ReplaceRejected", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects9 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteCancelRequested", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects10 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteCancelRequested", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects11 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteCanceled", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects12 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteCancelRejected", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "CancelRejected", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects13 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteCancelRejected", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "CancelRejected", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects14 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RoutePartiallyFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects15 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RoutePartiallyFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects16 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RoutePartiallyFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects17 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects18 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects19 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RoutePartiallyFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects20 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects21 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteFilled", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects22 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteExpired", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "OrderExpired", {}}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects23 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteExpired", {}}, {RouteFsmEffectKind::EmitEvent, "OrderFsm", "OrderExpired", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects24 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteSuperseded", {}}}};
inline constexpr std::array<RouteFsmEffect, 1> kRouteFsmEffects25 = {{{RouteFsmEffectKind::PublishEventLog, {}, "RouteSuperseded", {}}}};
inline constexpr std::array<RouteFsmEffectArg, 2> kRouteFsmEffectArgs26_0 = {{RouteFsmEffectArg{"channel", "ops-alerts"}, RouteFsmEffectArg{"message", "Route anomaly detected — manual triage required"}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects26 = {{{RouteFsmEffectKind::Notify, {}, {}, kRouteFsmEffectArgs26_0}, {RouteFsmEffectKind::PublishEventLog, {}, "RouteAnomaly", {}}}};
inline constexpr std::array<RouteFsmEffectArg, 2> kRouteFsmEffectArgs27_0 = {{RouteFsmEffectArg{"channel", "ops-alerts"}, RouteFsmEffectArg{"message", "Route anomaly in PENDING_REPLACE — manual triage required"}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects27 = {{{RouteFsmEffectKind::Notify, {}, {}, kRouteFsmEffectArgs27_0}, {RouteFsmEffectKind::PublishEventLog, {}, "RouteAnomaly", {}}}};
inline constexpr std::array<RouteFsmEffectArg, 2> kRouteFsmEffectArgs28_0 = {{RouteFsmEffectArg{"channel", "ops-alerts"}, RouteFsmEffectArg{"message", "Route anomaly in PENDING_CANCEL — manual triage required"}}};
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects28 = {{{RouteFsmEffectKind::Notify, {}, {}, kRouteFsmEffectArgs28_0}, {RouteFsmEffectKind::PublishEventLog, {}, "RouteAnomaly", {}}}};

// ── Transition implementation (inline) ──────────────────────────────────────
inline RouteFsmTransitionResult transition(
    RouteFsmState state,
    RouteFsmEvent event,
    const RouteFsmContext& ctx,
    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {
  switch (state) {
  case RouteFsmState::PENDING:
    switch (event) {
    case RouteFsmEvent::RouteSent:
      return {RouteFsmState::SENT, ctx, kRouteFsmEffects0, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::SENT:
    switch (event) {
    case RouteFsmEvent::RoutePendingNewAtVenue:
      return {RouteFsmState::PENDING_NEW_AT_VENUE, ctx, kRouteFsmEffects1, false};
    case RouteFsmEvent::RouteAcknowledged:
      return {RouteFsmState::WORKING, ctx, kRouteFsmEffects3, false};
    case RouteFsmEvent::RouteRejected:
      return {RouteFsmState::REJECTED, ctx, kRouteFsmEffects4, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::PENDING_NEW_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteAcknowledged:
      return {RouteFsmState::WORKING, ctx, kRouteFsmEffects2, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::WORKING:
    switch (event) {
    case RouteFsmEvent::RouteReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplaceRequestedPayload*>(rawPayload);
      return {RouteFsmState::PENDING_REPLACE_AT_VENUE, ctx, kRouteFsmEffects5, false};
    }
    case RouteFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, kRouteFsmEffects9, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, kRouteFsmEffects14, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, kRouteFsmEffects17, false};
    }
    case RouteFsmEvent::RouteExpired:
      return {RouteFsmState::EXPIRED, ctx, kRouteFsmEffects22, false};
    case RouteFsmEvent::RouteSuperseded:
      return {RouteFsmState::SUPERSEDED, ctx, kRouteFsmEffects24, false};
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, kRouteFsmEffects26, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::PENDING_REPLACE_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteReplacePendingAtVenue:
      return {RouteFsmState::PENDING_REPLACE_AT_VENUE, ctx, kRouteFsmEffects6, false};
    case RouteFsmEvent::RouteReplaced: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplacedPayload*>(rawPayload);
      return {RouteFsmState::WORKING, ctx, kRouteFsmEffects7, false};
    }
    case RouteFsmEvent::RouteReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplaceRejectedPayload*>(rawPayload);
      return {RouteFsmState::WORKING, ctx, kRouteFsmEffects8, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, kRouteFsmEffects16, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, kRouteFsmEffects21, false};
    }
    case RouteFsmEvent::RouteSuperseded:
      return {RouteFsmState::SUPERSEDED, ctx, kRouteFsmEffects25, false};
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, kRouteFsmEffects27, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::PENDING_CANCEL_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteCanceled:
      return {RouteFsmState::CANCELED, ctx, kRouteFsmEffects11, false};
    case RouteFsmEvent::RouteCancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteCancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {RouteFsmState::WORKING, ctx, kRouteFsmEffects12, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {RouteFsmState::PARTIALLY_FILLED, ctx, kRouteFsmEffects13, false};
      }
      return {state, ctx, {}, true};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      newCtx.preCancelStatus = "1";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, kRouteFsmEffects19, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, kRouteFsmEffects20, false};
    }
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, kRouteFsmEffects28, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::PARTIALLY_FILLED:
    switch (event) {
    case RouteFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, kRouteFsmEffects10, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, kRouteFsmEffects15, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, kRouteFsmEffects18, false};
    }
    case RouteFsmEvent::RouteExpired:
      return {RouteFsmState::EXPIRED, ctx, kRouteFsmEffects23, false};
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::SUPERSEDED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case RouteFsmState::ANOMALY:
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
