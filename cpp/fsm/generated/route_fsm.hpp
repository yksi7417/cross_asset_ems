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
  // effects: TODO — will be replaced by generated effect variant
  bool isNoTransition;
};

// ── Transition function stub ───────────────────────────────────────────────────
// TODO: implement from YAML via codegen in a follow-up commit.
RouteFsmTransitionResult transition(
    RouteFsmState state,
    RouteFsmEvent event,
    const RouteFsmContext& ctx) noexcept;

} // namespace crossasset::ems::fsm
