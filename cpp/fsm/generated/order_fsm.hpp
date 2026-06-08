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
  // effects: TODO — will be replaced by generated effect variant
  bool isNoTransition;
};

// ── Transition function stub ───────────────────────────────────────────────────
// TODO: implement from YAML via codegen in a follow-up commit.
OrderFsmTransitionResult transition(
    OrderFsmState state,
    OrderFsmEvent event,
    const OrderFsmContext& ctx) noexcept;

} // namespace crossasset::ems::fsm
