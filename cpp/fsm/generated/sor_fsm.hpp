// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sorfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
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
