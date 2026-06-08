// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/orderfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
#include <variant>
#include <vector>

namespace crossasset::ems::fsm {

// ── States ──────────────────────────────────────────────────────────────────
enum class OrderFsmState : uint8_t {
  PENDING_NEW,
  NEW,
  PENDING_REPLACE,
  REPLACED,
  PENDING_CANCEL,
  PARTIALLY_FILLED,
  FILLED,
  CANCELED,
  REJECTED,
  EXPIRED,
  DONE_FOR_DAY,
  TRADE_CORRECTED,
  TRADE_CANCELED,
};

// ── Events ───────────────────────────────────────────────────────────────────
enum class OrderFsmEvent : uint16_t {
  ValidationPassed,
  ValidationFailed,
  ReplaceRequested,
  ReplaceAccepted,
  ReplaceRejected,
  CancelRequested,
  CancelAccepted,
  CancelRejected,
  PartialFill,
  FullFill,
  TradeCorrect,
  TradeCancelBust,
  OrderExpired,
  DoneForDay,
};

// ── Context ───────────────────────────────────────────────────────────────────
struct OrderFsmContext {
  std::string orderId{};
  std::string clOrdId{};
  std::optional<std::string> origClOrdId{};
  std::string instrumentId{};
  uint8_t side{};
  uint64_t orderQty{};
  std::optional<int64_t> price{};
  uint64_t cumQty{};
  uint64_t leavesQty{};
  std::string account{};
  uint8_t tif{};
  uint64_t traceId{};
  std::string initialOrderId{};
  std::optional<std::string> preCancelStatus{};
  std::optional<std::string> preReplaceStatus{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct OrderFsmTransitionResult {
  OrderFsmState newState;
  OrderFsmContext newContext;
  // effects: deferred — C++ effect dispatch not yet generated (Java has full effects)
  bool isNoTransition;
};

// ── Payload structs ──────────────────────────────────────────────────────────
struct OrderFsmPayloads {
  struct ReplaceRequestedPayload {
    std::string newClOrdId{};
    uint64_t newOrderQty{};
    std::optional<int64_t> newPrice{};
  };
  struct ReplaceAcceptedPayload {
    std::string newClOrdId{};
  };
  struct ReplaceRejectedPayload {
    uint8_t cxlRejReason{};
  };
  struct CancelRejectedPayload {
    uint8_t cxlRejReason{};
  };
  struct PartialFillPayload {
    uint64_t lastQty{};
    int64_t lastPx{};
    std::string execId{};
  };
  struct FullFillPayload {
    uint64_t lastQty{};
    int64_t lastPx{};
    std::string execId{};
  };
  struct TradeCorrectPayload {
    uint64_t correctedQty{};
    int64_t correctedPx{};
    std::string execId{};
  };
  struct TradeCancelBustPayload {
    std::string bustedExecId{};
  };
};

// ── Transition implementation (inline) ──────────────────────────────────────
inline OrderFsmTransitionResult transition(
    OrderFsmState state,
    OrderFsmEvent event,
    const OrderFsmContext& ctx,
    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {
  switch (state) {
  case OrderFsmState::PENDING_NEW:
    switch (event) {
    case OrderFsmEvent::ValidationPassed:
      return {OrderFsmState::NEW, ctx, false};
    case OrderFsmEvent::ValidationFailed:
      return {OrderFsmState::REJECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::NEW:
    switch (event) {
    case OrderFsmEvent::ReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRequestedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = "0";
      return {OrderFsmState::PENDING_REPLACE, newCtx, false};
    }
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {OrderFsmState::PENDING_CANCEL, newCtx, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, false};
    case OrderFsmEvent::DoneForDay:
      return {OrderFsmState::DONE_FOR_DAY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::PENDING_REPLACE:
    switch (event) {
    case OrderFsmEvent::ReplaceAccepted: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceAcceptedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = std::nullopt;
      return {OrderFsmState::REPLACED, newCtx, false};
    }
    case OrderFsmEvent::ReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRejectedPayload*>(rawPayload);
      if ((ctx.preReplaceStatus.has_value() && *ctx.preReplaceStatus == "0")) {
        return {OrderFsmState::NEW, ctx, false};
      }
      if ((ctx.preReplaceStatus.has_value() && *ctx.preReplaceStatus == "5")) {
        return {OrderFsmState::REPLACED, ctx, false};
      }
      return {state, ctx, true};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::REPLACED:
    switch (event) {
    case OrderFsmEvent::ReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRequestedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = "5";
      return {OrderFsmState::PENDING_REPLACE, newCtx, false};
    }
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "5";
      return {OrderFsmState::PENDING_CANCEL, newCtx, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::PENDING_CANCEL:
    switch (event) {
    case OrderFsmEvent::CancelAccepted:
      return {OrderFsmState::CANCELED, ctx, false};
    case OrderFsmEvent::CancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::CancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {OrderFsmState::NEW, ctx, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "5")) {
        return {OrderFsmState::REPLACED, ctx, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {OrderFsmState::PARTIALLY_FILLED, ctx, false};
      }
      return {state, ctx, true};
    }
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::PARTIALLY_FILLED:
    switch (event) {
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {OrderFsmState::PENDING_CANCEL, newCtx, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, false};
    case OrderFsmEvent::DoneForDay:
      return {OrderFsmState::DONE_FOR_DAY, ctx, false};
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::FILLED:
    switch (event) {
    case OrderFsmEvent::TradeCorrect: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::TradeCorrectPayload*>(rawPayload);
      return {OrderFsmState::TRADE_CORRECTED, ctx, false};
    }
    case OrderFsmEvent::TradeCancelBust: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::TradeCancelBustPayload*>(rawPayload);
      return {OrderFsmState::TRADE_CANCELED, ctx, false};
    }
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::DONE_FOR_DAY:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::TRADE_CORRECTED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case OrderFsmState::TRADE_CANCELED:
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
