// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesessionfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
#include <variant>
#include <vector>

namespace crossasset::ems::fsm {

// ── States ──────────────────────────────────────────────────────────────────
enum class VenueSessionFsmState : uint8_t {
  DISCONNECTED,
  CONNECTING,
  LOGON_SENT,
  ACTIVE,
  TEST_REQUEST_SENT,
  RESEND_IN_PROGRESS,
  SEQUENCE_RESETTING,
  LOGOUT_IN_PROGRESS,
};

// ── Events ───────────────────────────────────────────────────────────────────
enum class VenueSessionFsmEvent : uint16_t {
  ConnectRequested,
  TcpConnected,
  TcpFailed,
  LogonAcknowledged,
  LogonRejected,
  HeartbeatReceived,
  HeartbeatOverdue,
  TestRequestResponse,
  TestRequestTimeout,
  GapDetected,
  ResendComplete,
  InboundResendRequest,
  SequenceResetReceived,
  LogoutRequested,
  LogoutReceived,
  LogoutEchoed,
  UnexpectedDisconnect,
};

// ── Context ───────────────────────────────────────────────────────────────────
struct VenueSessionFsmContext {
  std::string sessionId{};
  uint64_t nextExpectedSeqIn{};
  uint64_t nextSendSeqOut{};
  uint32_t heartbeatIntervalSecs{};
  bool testRequestOutstanding{};
  uint64_t resendWindowLow{};
  uint64_t resendWindowHigh{};
  std::string venueMic{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct VenueSessionFsmTransitionResult {
  VenueSessionFsmState newState;
  VenueSessionFsmContext newContext;
  // effects: TODO — will be replaced by generated effect variant
  bool isNoTransition;
};

// ── Transition function stub ───────────────────────────────────────────────────
// TODO: implement from YAML via codegen in a follow-up commit.
VenueSessionFsmTransitionResult transition(
    VenueSessionFsmState state,
    VenueSessionFsmEvent event,
    const VenueSessionFsmContext& ctx) noexcept;

} // namespace crossasset::ems::fsm
