// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/routefsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
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

// ── TransitionResult ──────────────────────────────────────────────────────────
struct RouteFsmTransitionResult {
  RouteFsmState newState;
  RouteFsmContext newContext;
  // effects: deferred — C++ effect dispatch not yet generated (Java has full effects)
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
      return {RouteFsmState::SENT, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::SENT:
    switch (event) {
    case RouteFsmEvent::RoutePendingNewAtVenue:
      return {RouteFsmState::PENDING_NEW_AT_VENUE, ctx, false};
    case RouteFsmEvent::RouteAcknowledged:
      return {RouteFsmState::WORKING, ctx, false};
    case RouteFsmEvent::RouteRejected:
      return {RouteFsmState::REJECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::PENDING_NEW_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteAcknowledged:
      return {RouteFsmState::WORKING, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::WORKING:
    switch (event) {
    case RouteFsmEvent::RouteReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplaceRequestedPayload*>(rawPayload);
      return {RouteFsmState::PENDING_REPLACE_AT_VENUE, ctx, false};
    }
    case RouteFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteExpired:
      return {RouteFsmState::EXPIRED, ctx, false};
    case RouteFsmEvent::RouteSuperseded:
      return {RouteFsmState::SUPERSEDED, ctx, false};
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::PENDING_REPLACE_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteReplacePendingAtVenue:
      return {RouteFsmState::PENDING_REPLACE_AT_VENUE, ctx, false};
    case RouteFsmEvent::RouteReplaced: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplacedPayload*>(rawPayload);
      return {RouteFsmState::WORKING, ctx, false};
    }
    case RouteFsmEvent::RouteReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteReplaceRejectedPayload*>(rawPayload);
      return {RouteFsmState::WORKING, ctx, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteSuperseded:
      return {RouteFsmState::SUPERSEDED, ctx, false};
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::PENDING_CANCEL_AT_VENUE:
    switch (event) {
    case RouteFsmEvent::RouteCanceled:
      return {RouteFsmState::CANCELED, ctx, false};
    case RouteFsmEvent::RouteCancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const RouteFsmPayloads::RouteCancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {RouteFsmState::WORKING, ctx, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {RouteFsmState::PARTIALLY_FILLED, ctx, false};
      }
      return {state, ctx, true};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      newCtx.preCancelStatus = "1";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteAnomaly:
      return {RouteFsmState::ANOMALY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::PARTIALLY_FILLED:
    switch (event) {
    case RouteFsmEvent::RouteCancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {RouteFsmState::PENDING_CANCEL_AT_VENUE, newCtx, false};
    }
    case RouteFsmEvent::RoutePartiallyFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RoutePartiallyFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {RouteFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteFilled: {
      const auto* p = static_cast<const RouteFsmPayloads::RouteFilledPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {RouteFsmState::FILLED, newCtx, false};
    }
    case RouteFsmEvent::RouteExpired:
      return {RouteFsmState::EXPIRED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::SUPERSEDED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case RouteFsmState::ANOMALY:
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
