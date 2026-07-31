// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sorfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
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

// ── TransitionResult ──────────────────────────────────────────────────────────
struct SorFsmTransitionResult {
  SorFsmState newState;
  SorFsmContext newContext;
  // effects: deferred — C++ effect dispatch not yet generated (Java has full effects)
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
      return {SorFsmState::SENT, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::SENT:
    switch (event) {
    case SorFsmEvent::SorStrategyDecided:
      return {SorFsmState::SENT, ctx, false};
    case SorFsmEvent::RoutePendingNewAtVenue:
      return {SorFsmState::PENDING_NEW_AT_VENUE, ctx, false};
    case SorFsmEvent::RouteAcknowledged:
      return {SorFsmState::WORKING, ctx, false};
    case SorFsmEvent::RouteRejected:
      return {SorFsmState::REJECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::PENDING_NEW_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteAcknowledged:
      return {SorFsmState::WORKING, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::WORKING:
    switch (event) {
    case SorFsmEvent::SorPlanAdjusted:
      return {SorFsmState::WORKING, ctx, false};
    case SorFsmEvent::RouteReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplaceRequestedPayload*>(rawPayload);
      return {SorFsmState::PENDING_REPLACE_AT_VENUE, ctx, false};
    }
    case SorFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {SorFsmState::PENDING_CANCEL_AT_VENUE, newCtx, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case SorFsmEvent::RouteFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {SorFsmState::FILLED, newCtx, false};
    }
    case SorFsmEvent::RouteExpired:
      return {SorFsmState::EXPIRED, ctx, false};
    case SorFsmEvent::RouteSuperseded:
      return {SorFsmState::SUPERSEDED, ctx, false};
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::PENDING_REPLACE_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteReplacePendingAtVenue:
      return {SorFsmState::PENDING_REPLACE_AT_VENUE, ctx, false};
    case SorFsmEvent::RouteReplaced: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplacedPayload*>(rawPayload);
      return {SorFsmState::WORKING, ctx, false};
    }
    case SorFsmEvent::RouteReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteReplaceRejectedPayload*>(rawPayload);
      return {SorFsmState::WORKING, ctx, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case SorFsmEvent::RouteSuperseded:
      return {SorFsmState::SUPERSEDED, ctx, false};
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::PENDING_CANCEL_AT_VENUE:
    switch (event) {
    case SorFsmEvent::RouteCanceled:
      return {SorFsmState::CANCELED, ctx, false};
    case SorFsmEvent::RouteCancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const SorFsmPayloads::RouteCancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {SorFsmState::WORKING, ctx, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {SorFsmState::PARTIALLY_FILLED, ctx, false};
      }
      return {state, ctx, true};
    }
    case SorFsmEvent::RouteAnomaly:
      return {SorFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::PARTIALLY_FILLED:
    switch (event) {
    case SorFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {SorFsmState::PENDING_CANCEL_AT_VENUE, newCtx, false};
    }
    case SorFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {SorFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case SorFsmEvent::RouteFilled: {
      const auto* p = static_cast<const SorFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {SorFsmState::FILLED, newCtx, false};
    }
    case SorFsmEvent::RouteExpired:
      return {SorFsmState::EXPIRED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case SorFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case SorFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case SorFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case SorFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case SorFsmState::SUPERSEDED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case SorFsmState::ANOMALY:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  default:
    return {state, ctx, true};
  }
  return {state, ctx, true};
}

} // namespace crossasset::ems::fsm
