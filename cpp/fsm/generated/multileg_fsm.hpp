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
  // effects: TODO — will be replaced by generated effect variant
  bool isNoTransition;
};

// ── Transition function stub ───────────────────────────────────────────────────
// TODO: implement from YAML via codegen in a follow-up commit.
MultiLegFsmTransitionResult transition(
    MultiLegFsmState state,
    MultiLegFsmEvent event,
    const MultiLegFsmContext& ctx) noexcept;

} // namespace crossasset::ems::fsm
