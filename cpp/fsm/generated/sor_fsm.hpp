// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sorfsm.fsm.yaml
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
enum class SorFsmState : uint8_t {
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
enum class SorFsmEvent : uint16_t {
  SorStrategyDecided,
  SorPlanAdjusted,
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

inline const char* name(SorFsmState state) noexcept {
  switch (state) {
    case SorFsmState::PENDING: return "PENDING";
    case SorFsmState::SENT: return "SENT";
    case SorFsmState::PENDING_NEW_AT_VENUE: return "PENDING_NEW_AT_VENUE";
    case SorFsmState::WORKING: return "WORKING";
    case SorFsmState::PENDING_REPLACE_AT_VENUE: return "PENDING_REPLACE_AT_VENUE";
    case SorFsmState::PENDING_CANCEL_AT_VENUE: return "PENDING_CANCEL_AT_VENUE";
    case SorFsmState::PARTIALLY_FILLED: return "PARTIALLY_FILLED";
    case SorFsmState::FILLED: return "FILLED";
    case SorFsmState::CANCELED: return "CANCELED";
    case SorFsmState::REJECTED: return "REJECTED";
    case SorFsmState::EXPIRED: return "EXPIRED";
    case SorFsmState::SUPERSEDED: return "SUPERSEDED";
    case SorFsmState::ANOMALY: return "ANOMALY";
  }
  return "UNKNOWN";
}

inline const char* name(SorFsmEvent event) noexcept {
  switch (event) {
    case SorFsmEvent::SorStrategyDecided: return "SorStrategyDecided";
    case SorFsmEvent::SorPlanAdjusted: return "SorPlanAdjusted";
    case SorFsmEvent::RouteSent: return "RouteSent";
    case SorFsmEvent::RoutePendingNewAtVenue: return "RoutePendingNewAtVenue";
    case SorFsmEvent::RouteAcknowledged: return "RouteAcknowledged";
    case SorFsmEvent::RouteRejected: return "RouteRejected";
    case SorFsmEvent::RouteReplaceRequested: return "RouteReplaceRequested";
    case SorFsmEvent::RouteReplacePendingAtVenue: return "RouteReplacePendingAtVenue";
    case SorFsmEvent::RouteReplaced: return "RouteReplaced";
    case SorFsmEvent::RouteReplaceRejected: return "RouteReplaceRejected";
    case SorFsmEvent::RouteCancelRequested: return "RouteCancelRequested";
    case SorFsmEvent::RouteCanceled: return "RouteCanceled";
    case SorFsmEvent::RouteCancelRejected: return "RouteCancelRejected";
    case SorFsmEvent::RoutePartiallyFilled: return "RoutePartiallyFilled";
    case SorFsmEvent::RouteFilled: return "RouteFilled";
    case SorFsmEvent::RouteExpired: return "RouteExpired";
    case SorFsmEvent::RouteSuperseded: return "RouteSuperseded";
    case SorFsmEvent::RouteAnomaly: return "RouteAnomaly";
  }
  return "UNKNOWN";
}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<SorFsmEvent> SorFsmEventFromName(std::string_view name) {
  if (name == "SorStrategyDecided") return SorFsmEvent::SorStrategyDecided;
  if (name == "SorPlanAdjusted") return SorFsmEvent::SorPlanAdjusted;
  if (name == "RouteSent") return SorFsmEvent::RouteSent;
  if (name == "RoutePendingNewAtVenue") return SorFsmEvent::RoutePendingNewAtVenue;
  if (name == "RouteAcknowledged") return SorFsmEvent::RouteAcknowledged;
  if (name == "RouteRejected") return SorFsmEvent::RouteRejected;
  if (name == "RouteReplaceRequested") return SorFsmEvent::RouteReplaceRequested;
  if (name == "RouteReplacePendingAtVenue") return SorFsmEvent::RouteReplacePendingAtVenue;
  if (name == "RouteReplaced") return SorFsmEvent::RouteReplaced;
  if (name == "RouteReplaceRejected") return SorFsmEvent::RouteReplaceRejected;
  if (name == "RouteCancelRequested") return SorFsmEvent::RouteCancelRequested;
  if (name == "RouteCanceled") return SorFsmEvent::RouteCanceled;
  if (name == "RouteCancelRejected") return SorFsmEvent::RouteCancelRejected;
  if (name == "RoutePartiallyFilled") return SorFsmEvent::RoutePartiallyFilled;
  if (name == "RouteFilled") return SorFsmEvent::RouteFilled;
  if (name == "RouteExpired") return SorFsmEvent::RouteExpired;
  if (name == "RouteSuperseded") return SorFsmEvent::RouteSuperseded;
  if (name == "RouteAnomaly") return SorFsmEvent::RouteAnomaly;
  return std::nullopt;
}

// ── Context ───────────────────────────────────────────────────────────────────
struct SorFsmContext {
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
  std::string sorStrategyId{};
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
enum class SorFsmEffectKind : uint8_t {
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
};

/// One `key: value` pair from an effect's `args` map in the schema.
struct SorFsmEffectArg {
  std::string_view key{};
  std::string_view value{};
};

struct SorFsmEffect {
  SorFsmEffectKind kind{};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const SorFsmEffectArg> args{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct SorFsmTransitionResult {
  SorFsmState newState;
  SorFsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const SorFsmEffect> effects;
  bool isNoTransition;
};

// ── Payload structs ──────────────────────────────────────────────────────────
struct SorFsmPayloads {
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
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects0 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteSent", {}}}};
inline constexpr std::array<SorFsmEffectArg, 2> kSorFsmEffectArgs1_2 = {{SorFsmEffectArg{"signal", "dispatch_sor_children"}, SorFsmEffectArg{"route_id", "{{ context.route_id }}"}}};
inline constexpr std::array<SorFsmEffect, 3> kSorFsmEffects1 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorStrategySelected", {}}, {SorFsmEffectKind::PublishEventLog, {}, "SorWheelSelectionLogged", {}}, {SorFsmEffectKind::Notify, {}, {}, kSorFsmEffectArgs1_2}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects2 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRoutePendingNewAtVenue", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects3 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteWorking", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "ValidationPassed", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects4 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteWorking", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "ValidationPassed", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects5 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteRejected", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "ValidationFailed", {}}}};
inline constexpr std::array<SorFsmEffectArg, 2> kSorFsmEffectArgs6_1 = {{SorFsmEffectArg{"signal", "dispatch_sor_children"}, SorFsmEffectArg{"route_id", "{{ context.route_id }}"}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects6 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorPlanAdjusted", {}}, {SorFsmEffectKind::Notify, {}, {}, kSorFsmEffectArgs6_1}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects7 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorReplaceRequested", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects8 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorReplacePendingAtVenue", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects9 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteReplaced", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "ReplaceAccepted", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects10 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorReplaceRejected", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "ReplaceRejected", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects11 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorCancelRequested", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects12 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorCancelRequested", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects13 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteCanceled", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects14 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorCancelRejected", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "CancelRejected", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects15 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorCancelRejected", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "CancelRejected", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects16 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRoutePartiallyFilled", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects17 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRoutePartiallyFilled", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects18 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRoutePartiallyFilled", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "PartialFill", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects19 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteFilled", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects20 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteFilled", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "FullFill", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects21 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteExpired", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "OrderExpired", {}}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects22 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteExpired", {}}, {SorFsmEffectKind::EmitEvent, "OrderFsm", "OrderExpired", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects23 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteSuperseded", {}}}};
inline constexpr std::array<SorFsmEffect, 1> kSorFsmEffects24 = {{{SorFsmEffectKind::PublishEventLog, {}, "SorRouteSuperseded", {}}}};
inline constexpr std::array<SorFsmEffectArg, 2> kSorFsmEffectArgs25_0 = {{SorFsmEffectArg{"channel", "ops-alerts"}, SorFsmEffectArg{"message", "SOR route anomaly — manual triage required"}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects25 = {{{SorFsmEffectKind::Notify, {}, {}, kSorFsmEffectArgs25_0}, {SorFsmEffectKind::PublishEventLog, {}, "SorRouteAnomaly", {}}}};
inline constexpr std::array<SorFsmEffectArg, 2> kSorFsmEffectArgs26_0 = {{SorFsmEffectArg{"channel", "ops-alerts"}, SorFsmEffectArg{"message", "SOR route anomaly in PENDING_REPLACE — manual triage required"}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects26 = {{{SorFsmEffectKind::Notify, {}, {}, kSorFsmEffectArgs26_0}, {SorFsmEffectKind::PublishEventLog, {}, "SorRouteAnomaly", {}}}};
inline constexpr std::array<SorFsmEffectArg, 2> kSorFsmEffectArgs27_0 = {{SorFsmEffectArg{"channel", "ops-alerts"}, SorFsmEffectArg{"message", "SOR route anomaly in PENDING_CANCEL — manual triage required"}}};
inline constexpr std::array<SorFsmEffect, 2> kSorFsmEffects27 = {{{SorFsmEffectKind::Notify, {}, {}, kSorFsmEffectArgs27_0}, {SorFsmEffectKind::PublishEventLog, {}, "SorRouteAnomaly", {}}}};

// ── Transition implementation (inline) ──────────────────────────────────────
inline SorFsmTransitionResult transition(
    SorFsmState state,
    SorFsmEvent event,
    const SorFsmContext& ctx,
    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {
  switch (state) {
  case SorFsmState::PENDING:
    switch (event) {
    case SorFsmEvent::RouteSent:
      return {SorFsmState::SENT, ctx, kSorFsmEffects0, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::SENT:
    switch (event) {
    case SorFsmEvent::SorStrategyDecided:
      return {SorFsmState::SENT, ctx, kSorFsmEffects1, false};
    case SorFsmEvent::RoutePendingNewAtVenue:
      return {SorFsmState::PENDING_NEW_AT_VENUE, ctx, kSorFsmEffects2, false};
    case SorFsmEvent::RouteAcknowledged:
      return {SorFsmState::WORKING, ctx, kSorFsmEffects4, false};
    case SorFsmEvent::RouteRejected:
      return {SorFsmState::REJECTED, ctx, kSorFsmEffects5, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::PENDING_NEW_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteAcknowledged:
      return {SorFsmState::WORKING, ctx, kSorFsmEffects3, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::WORKING:
    switch (event) {
    case SorFsmEvent::SorPlanAdjusted:
      return {SorFsmState::WORKING, ctx, kSorFsmEffects6, false};
    case SorFsmEvent::RouteReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplaceRequestedPayload*>(rawPayload);
      return {SorFsmState::PENDING_REPLACE_AT_VENUE, ctx, kSorFsmEffects7, false};
    }
    case SorFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {SorFsmState::PENDING_CANCEL_AT_VENUE, newCtx, kSorFsmEffects11, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, kSorFsmEffects16, false};
    }
    case SorFsmEvent::RouteFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {SorFsmState::FILLED, newCtx, kSorFsmEffects19, false};
    }
    case SorFsmEvent::RouteExpired:
      return {SorFsmState::EXPIRED, ctx, kSorFsmEffects21, false};
    case SorFsmEvent::RouteSuperseded:
      return {SorFsmState::SUPERSEDED, ctx, kSorFsmEffects23, false};
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, kSorFsmEffects25, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::PENDING_REPLACE_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteReplacePendingAtVenue:
      return {SorFsmState::PENDING_REPLACE_AT_VENUE, ctx, kSorFsmEffects8, false};
    case SorFsmEvent::RouteReplaced: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplacedPayload*>(rawPayload);
      return {SorFsmState::WORKING, ctx, kSorFsmEffects9, false};
    }
    case SorFsmEvent::RouteReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplaceRejectedPayload*>(rawPayload);
      return {SorFsmState::WORKING, ctx, kSorFsmEffects10, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, kSorFsmEffects18, false};
    }
    case SorFsmEvent::RouteSuperseded:
      return {SorFsmState::SUPERSEDED, ctx, kSorFsmEffects24, false};
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, kSorFsmEffects26, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::PENDING_CANCEL_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteCanceled:
      return {SorFsmState::CANCELED, ctx, kSorFsmEffects13, false};
    case SorFsmEvent::RouteCancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteCancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {SorFsmState::WORKING, ctx, kSorFsmEffects14, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {SorFsmState::PARTIALLY_FILLED, ctx, kSorFsmEffects15, false};
      }
      return {state, ctx, {}, true};
    }
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, kSorFsmEffects27, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::PARTIALLY_FILLED:
    switch (event) {
    case SorFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {SorFsmState::PENDING_CANCEL_AT_VENUE, newCtx, kSorFsmEffects12, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, kSorFsmEffects17, false};
    }
    case SorFsmEvent::RouteFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {SorFsmState::FILLED, newCtx, kSorFsmEffects20, false};
    }
    case SorFsmEvent::RouteExpired:
      return {SorFsmState::EXPIRED, ctx, kSorFsmEffects22, false};
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::SUPERSEDED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case SorFsmState::ANOMALY:
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
