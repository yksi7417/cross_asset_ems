// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/orderfsm.fsm.yaml
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

// ── Names ────────────────────────────────────────────────────────────────────
//
// State and event names reach the output journal, which the conformance gate
// compares byte-for-byte across three languages — so these must match the Java
// enum constants character for character.

inline const char* name(OrderFsmState state) noexcept {
  switch (state) {
    case OrderFsmState::PENDING_NEW: return "PENDING_NEW";
    case OrderFsmState::NEW: return "NEW";
    case OrderFsmState::PENDING_REPLACE: return "PENDING_REPLACE";
    case OrderFsmState::REPLACED: return "REPLACED";
    case OrderFsmState::PENDING_CANCEL: return "PENDING_CANCEL";
    case OrderFsmState::PARTIALLY_FILLED: return "PARTIALLY_FILLED";
    case OrderFsmState::FILLED: return "FILLED";
    case OrderFsmState::CANCELED: return "CANCELED";
    case OrderFsmState::REJECTED: return "REJECTED";
    case OrderFsmState::EXPIRED: return "EXPIRED";
    case OrderFsmState::DONE_FOR_DAY: return "DONE_FOR_DAY";
    case OrderFsmState::TRADE_CORRECTED: return "TRADE_CORRECTED";
    case OrderFsmState::TRADE_CANCELED: return "TRADE_CANCELED";
  }
  return "UNKNOWN";
}

inline const char* name(OrderFsmEvent event) noexcept {
  switch (event) {
    case OrderFsmEvent::ValidationPassed: return "ValidationPassed";
    case OrderFsmEvent::ValidationFailed: return "ValidationFailed";
    case OrderFsmEvent::ReplaceRequested: return "ReplaceRequested";
    case OrderFsmEvent::ReplaceAccepted: return "ReplaceAccepted";
    case OrderFsmEvent::ReplaceRejected: return "ReplaceRejected";
    case OrderFsmEvent::CancelRequested: return "CancelRequested";
    case OrderFsmEvent::CancelAccepted: return "CancelAccepted";
    case OrderFsmEvent::CancelRejected: return "CancelRejected";
    case OrderFsmEvent::PartialFill: return "PartialFill";
    case OrderFsmEvent::FullFill: return "FullFill";
    case OrderFsmEvent::TradeCorrect: return "TradeCorrect";
    case OrderFsmEvent::TradeCancelBust: return "TradeCancelBust";
    case OrderFsmEvent::OrderExpired: return "OrderExpired";
    case OrderFsmEvent::DoneForDay: return "DoneForDay";
  }
  return "UNKNOWN";
}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<OrderFsmEvent> OrderFsmEventFromName(std::string_view name) {
  if (name == "ValidationPassed") return OrderFsmEvent::ValidationPassed;
  if (name == "ValidationFailed") return OrderFsmEvent::ValidationFailed;
  if (name == "ReplaceRequested") return OrderFsmEvent::ReplaceRequested;
  if (name == "ReplaceAccepted") return OrderFsmEvent::ReplaceAccepted;
  if (name == "ReplaceRejected") return OrderFsmEvent::ReplaceRejected;
  if (name == "CancelRequested") return OrderFsmEvent::CancelRequested;
  if (name == "CancelAccepted") return OrderFsmEvent::CancelAccepted;
  if (name == "CancelRejected") return OrderFsmEvent::CancelRejected;
  if (name == "PartialFill") return OrderFsmEvent::PartialFill;
  if (name == "FullFill") return OrderFsmEvent::FullFill;
  if (name == "TradeCorrect") return OrderFsmEvent::TradeCorrect;
  if (name == "TradeCancelBust") return OrderFsmEvent::TradeCancelBust;
  if (name == "OrderExpired") return OrderFsmEvent::OrderExpired;
  if (name == "DoneForDay") return OrderFsmEvent::DoneForDay;
  return std::nullopt;
}

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
  std::string initialClOrdId{};
  std::string chainId{};
  uint32_t orderVersion{};
  std::optional<std::string> preCancelStatus{};
  std::optional<std::string> preReplaceStatus{};
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
enum class OrderFsmEffectKind : uint8_t {
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
};

/// One `key: value` pair from an effect's `args` map in the schema.
struct OrderFsmEffectArg {
  std::string_view key{};
  std::string_view value{};
};

struct OrderFsmEffect {
  OrderFsmEffectKind kind{};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const OrderFsmEffectArg> args{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct OrderFsmTransitionResult {
  OrderFsmState newState;
  OrderFsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const OrderFsmEffect> effects;
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

// ── Effect tables ────────────────────────────────────────────────────────────
//
// One table per transition that asks for something. `transition` returns a span
// over the matching table, so the effects cost nothing to return and outlive any
// caller.
inline constexpr std::array<OrderFsmEffectArg, 0> kOrderFsmEffectArgs0_0 = {{}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs0_1 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "0"}, OrderFsmEffectArg{"ord_status", "0"}}};
inline constexpr std::array<OrderFsmEffect, 3> kOrderFsmEffects0 = {{{OrderFsmEffectKind::ChainIdentityStamp, {}, {}, kOrderFsmEffectArgs0_0}, {OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs0_1}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderAccepted", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs1_0 = {{OrderFsmEffectArg{"msg_type", "j"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects1 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs1_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs2_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "E"}, OrderFsmEffectArg{"ord_status", "E"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects2 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs2_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderReplaceRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs3_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "E"}, OrderFsmEffectArg{"ord_status", "E"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects3 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs3_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderReplaceRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs4_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "5"}, OrderFsmEffectArg{"ord_status", "0"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects4 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs4_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderReplaced", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs5_0 = {{OrderFsmEffectArg{"msg_type", "9"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects5 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs5_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderReplaceRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs6_0 = {{OrderFsmEffectArg{"msg_type", "9"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects6 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs6_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderReplaceRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs7_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "6"}, OrderFsmEffectArg{"ord_status", "6"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects7 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs7_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs8_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "6"}, OrderFsmEffectArg{"ord_status", "6"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects8 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs8_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs9_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "6"}, OrderFsmEffectArg{"ord_status", "6"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects9 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs9_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs10_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "4"}, OrderFsmEffectArg{"ord_status", "4"}}};
inline constexpr std::array<OrderFsmEffect, 3> kOrderFsmEffects10 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs10_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCanceled", {}}, {OrderFsmEffectKind::EmitEvent, "RouteFsm", "RouteCancelRequested", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs11_0 = {{OrderFsmEffectArg{"msg_type", "9"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects11 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs11_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs12_0 = {{OrderFsmEffectArg{"msg_type", "9"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects12 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs12_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 1> kOrderFsmEffectArgs13_0 = {{OrderFsmEffectArg{"msg_type", "9"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects13 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs13_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderCancelRejected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs14_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects14 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs14_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderPartiallyFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs15_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects15 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs15_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderPartiallyFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs16_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects16 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs16_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderPartiallyFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs17_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects17 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs17_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs18_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects18 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs18_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderPartiallyFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs19_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects19 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs19_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs20_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects20 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs20_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs21_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects21 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs21_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs22_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "1"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects22 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs22_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderPartiallyFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs23_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "F"}, OrderFsmEffectArg{"ord_status", "2"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects23 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs23_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderFilled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 2> kOrderFsmEffectArgs24_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "G"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects24 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs24_0}, {OrderFsmEffectKind::PublishEventLog, {}, "TradeCorrected", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 2> kOrderFsmEffectArgs25_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "H"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects25 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs25_0}, {OrderFsmEffectKind::PublishEventLog, {}, "TradeCanceled", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs26_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "C"}, OrderFsmEffectArg{"ord_status", "C"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects26 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs26_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderExpired", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs27_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "C"}, OrderFsmEffectArg{"ord_status", "C"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects27 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs27_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderExpired", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs28_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "C"}, OrderFsmEffectArg{"ord_status", "C"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects28 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs28_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderExpired", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs29_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "3"}, OrderFsmEffectArg{"ord_status", "3"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects29 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs29_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderDoneForDay", {}}}};
inline constexpr std::array<OrderFsmEffectArg, 3> kOrderFsmEffectArgs30_0 = {{OrderFsmEffectArg{"msg_type", "8"}, OrderFsmEffectArg{"exec_type", "3"}, OrderFsmEffectArg{"ord_status", "3"}}};
inline constexpr std::array<OrderFsmEffect, 2> kOrderFsmEffects30 = {{{OrderFsmEffectKind::PublishFixMessage, {}, {}, kOrderFsmEffectArgs30_0}, {OrderFsmEffectKind::PublishEventLog, {}, "OrderDoneForDay", {}}}};

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
      return {OrderFsmState::NEW, ctx, kOrderFsmEffects0, false};
    case OrderFsmEvent::ValidationFailed:
      return {OrderFsmState::REJECTED, ctx, kOrderFsmEffects1, false};
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::NEW:
    switch (event) {
    case OrderFsmEvent::ReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRequestedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = "0";
      newCtx.orderVersion = (ctx.orderVersion + 1);
      return {OrderFsmState::PENDING_REPLACE, newCtx, kOrderFsmEffects2, false};
    }
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "0";
      return {OrderFsmState::PENDING_CANCEL, newCtx, kOrderFsmEffects7, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, kOrderFsmEffects14, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, kOrderFsmEffects19, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, kOrderFsmEffects26, false};
    case OrderFsmEvent::DoneForDay:
      return {OrderFsmState::DONE_FOR_DAY, ctx, kOrderFsmEffects29, false};
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::PENDING_REPLACE:
    switch (event) {
    case OrderFsmEvent::ReplaceAccepted: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceAcceptedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = std::nullopt;
      return {OrderFsmState::REPLACED, newCtx, kOrderFsmEffects4, false};
    }
    case OrderFsmEvent::ReplaceRejected: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRejectedPayload*>(rawPayload);
      if ((ctx.preReplaceStatus.has_value() && *ctx.preReplaceStatus == "0")) {
        return {OrderFsmState::NEW, ctx, kOrderFsmEffects5, false};
      }
      if ((ctx.preReplaceStatus.has_value() && *ctx.preReplaceStatus == "5")) {
        return {OrderFsmState::REPLACED, ctx, kOrderFsmEffects6, false};
      }
      return {state, ctx, {}, true};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, kOrderFsmEffects16, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, kOrderFsmEffects17, false};
    }
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::REPLACED:
    switch (event) {
    case OrderFsmEvent::ReplaceRequested: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::ReplaceRequestedPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.preReplaceStatus = "5";
      newCtx.orderVersion = (ctx.orderVersion + 1);
      return {OrderFsmState::PENDING_REPLACE, newCtx, kOrderFsmEffects3, false};
    }
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "5";
      return {OrderFsmState::PENDING_CANCEL, newCtx, kOrderFsmEffects8, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, kOrderFsmEffects15, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, kOrderFsmEffects20, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, kOrderFsmEffects27, false};
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::PENDING_CANCEL:
    switch (event) {
    case OrderFsmEvent::CancelAccepted:
      return {OrderFsmState::CANCELED, ctx, kOrderFsmEffects10, false};
    case OrderFsmEvent::CancelRejected: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::CancelRejectedPayload*>(rawPayload);
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "0")) {
        return {OrderFsmState::NEW, ctx, kOrderFsmEffects11, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "5")) {
        return {OrderFsmState::REPLACED, ctx, kOrderFsmEffects12, false};
      }
      if ((ctx.preCancelStatus.has_value() && *ctx.preCancelStatus == "1")) {
        return {OrderFsmState::PARTIALLY_FILLED, ctx, kOrderFsmEffects13, false};
      }
      return {state, ctx, {}, true};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, kOrderFsmEffects22, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, kOrderFsmEffects23, false};
    }
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::PARTIALLY_FILLED:
    switch (event) {
    case OrderFsmEvent::CancelRequested: {
      auto newCtx = ctx;
      newCtx.preCancelStatus = "1";
      return {OrderFsmState::PENDING_CANCEL, newCtx, kOrderFsmEffects9, false};
    }
    case OrderFsmEvent::PartialFill: {
      const auto* p = static_cast<const OrderFsmPayloads::PartialFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = (ctx.leavesQty - p->lastQty);
      return {OrderFsmState::PARTIALLY_FILLED, newCtx, kOrderFsmEffects18, false};
    }
    case OrderFsmEvent::FullFill: {
      const auto* p = static_cast<const OrderFsmPayloads::FullFillPayload*>(rawPayload);
      auto newCtx = ctx;
      newCtx.cumQty = (ctx.cumQty + p->lastQty);
      newCtx.leavesQty = static_cast<uint64_t>(0);
      return {OrderFsmState::FILLED, newCtx, kOrderFsmEffects21, false};
    }
    case OrderFsmEvent::OrderExpired:
      return {OrderFsmState::EXPIRED, ctx, kOrderFsmEffects28, false};
    case OrderFsmEvent::DoneForDay:
      return {OrderFsmState::DONE_FOR_DAY, ctx, kOrderFsmEffects30, false};
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::FILLED:
    switch (event) {
    case OrderFsmEvent::TradeCorrect: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::TradeCorrectPayload*>(rawPayload);
      return {OrderFsmState::TRADE_CORRECTED, ctx, kOrderFsmEffects24, false};
    }
    case OrderFsmEvent::TradeCancelBust: {
      [[maybe_unused]] const auto* p = static_cast<const OrderFsmPayloads::TradeCancelBustPayload*>(rawPayload);
      return {OrderFsmState::TRADE_CANCELED, ctx, kOrderFsmEffects25, false};
    }
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::REJECTED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::EXPIRED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::DONE_FOR_DAY:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::TRADE_CORRECTED:
    switch (event) {
    default:
      return {state, ctx, {}, true};
    }
  case OrderFsmState::TRADE_CANCELED:
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
