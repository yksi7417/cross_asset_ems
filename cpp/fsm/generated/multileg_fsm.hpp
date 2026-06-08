// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multilegfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
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

// ── TransitionResult ──────────────────────────────────────────────────────────
struct MultiLegFsmTransitionResult {
  MultiLegFsmState newState;
  MultiLegFsmContext newContext;
  // effects: deferred — C++ effect dispatch not yet generated (Java has full effects)
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
      return {MultiLegFsmState::READY, ctx, false};
    case MultiLegFsmEvent::LegsValidationFailed:
      return {MultiLegFsmState::REJECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::READY:
    switch (event) {
    case MultiLegFsmEvent::FirstLegDispatched:
      return {MultiLegFsmState::LEGS_WORKING, ctx, false};
    case MultiLegFsmEvent::CancelRequested:
      return {MultiLegFsmState::CANCELED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::LEGS_WORKING:
    switch (event) {
    case MultiLegFsmEvent::LegPartiallyFilled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegPartiallyFilledPayload*>(rawPayload);
      return {MultiLegFsmState::LEGS_WORKING, ctx, false};
    }
    case MultiLegFsmEvent::LegFilled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegFilledPayload*>(rawPayload);
      if ((((ctx.legsFilled + 1) == ctx.totalLegs) && (ctx.legsRejected == 0) && (ctx.legsCanceled == 0))) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::FILLED, newCtx, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && ((((ctx.legsFilled + 1) + ctx.legsRejected) + ctx.legsCanceled) == ctx.totalLegs) && (((ctx.legsRejected > 0) || (ctx.legsCanceled > 0))))) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, false};
      }
      if (((((ctx.legsFilled + 1) + ctx.legsRejected) + ctx.legsCanceled) < ctx.totalLegs)) {
        auto newCtx = ctx;
        newCtx.legsFilled = (ctx.legsFilled + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, false};
      }
      return {state, ctx, true};
    }
    case MultiLegFsmEvent::LegRejected: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegRejectedPayload*>(rawPayload);
      if ((ctx.executionMode == "ALL_OR_NONE")) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::REJECTED, newCtx, false};
      }
      if ((ctx.executionMode == "SEQUENCED")) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::REJECTED, newCtx, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && (ctx.legsFilled > 0) && ((((ctx.legsFilled + ctx.legsRejected) + 1) + ctx.legsCanceled) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && (ctx.legsFilled == 0) && (((ctx.legsRejected + 1) + ctx.legsCanceled) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::CANCELED, newCtx, false};
      }
      if (((ctx.executionMode == "LEGS_INDEPENDENT") && ((((ctx.legsFilled + ctx.legsRejected) + 1) + ctx.legsCanceled) < ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsRejected = (ctx.legsRejected + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, false};
      }
      return {state, ctx, true};
    }
    case MultiLegFsmEvent::LegCanceled: {
      [[maybe_unused]] const auto* p = static_cast<const MultiLegFsmPayloads::LegCanceledPayload*>(rawPayload);
      if (((ctx.legsFilled > 0) && ((((ctx.legsFilled + ctx.legsRejected) + ctx.legsCanceled) + 1) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::PARTIALLY_FILLED, newCtx, false};
      }
      if (((ctx.legsFilled == 0) && (((ctx.legsRejected + ctx.legsCanceled) + 1) == ctx.totalLegs))) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::CANCELED, newCtx, false};
      }
      if (((((ctx.legsFilled + ctx.legsRejected) + ctx.legsCanceled) + 1) < ctx.totalLegs)) {
        auto newCtx = ctx;
        newCtx.legsCanceled = (ctx.legsCanceled + 1);
        return {MultiLegFsmState::LEGS_WORKING, newCtx, false};
      }
      return {state, ctx, true};
    }
    case MultiLegFsmEvent::CancelRequested:
      return {MultiLegFsmState::CANCELED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::FILLED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::PARTIALLY_FILLED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::CANCELED:
    switch (event) {
    default:
      return {state, ctx, true};
    }
  case MultiLegFsmState::REJECTED:
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
