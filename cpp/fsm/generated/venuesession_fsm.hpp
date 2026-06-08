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
  // effects: deferred — C++ effect dispatch not yet generated (Java has full effects)
  bool isNoTransition;
};


// ── Transition implementation (inline) ──────────────────────────────────────
inline VenueSessionFsmTransitionResult transition(
    VenueSessionFsmState state,
    VenueSessionFsmEvent event,
    const VenueSessionFsmContext& ctx,
    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {
  switch (state) {
  case VenueSessionFsmState::DISCONNECTED:
    switch (event) {
    case VenueSessionFsmEvent::ConnectRequested:
      return {VenueSessionFsmState::CONNECTING, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::CONNECTING:
    switch (event) {
    case VenueSessionFsmEvent::TcpConnected:
      return {VenueSessionFsmState::LOGON_SENT, ctx, false};
    case VenueSessionFsmEvent::TcpFailed:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::LOGON_SENT:
    switch (event) {
    case VenueSessionFsmEvent::LogonAcknowledged:
      return {VenueSessionFsmState::ACTIVE, ctx, false};
    case VenueSessionFsmEvent::LogonRejected:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::ACTIVE:
    switch (event) {
    case VenueSessionFsmEvent::HeartbeatReceived:
      return {VenueSessionFsmState::ACTIVE, ctx, false};
    case VenueSessionFsmEvent::HeartbeatOverdue: {
      auto newCtx = ctx;
      newCtx.testRequestOutstanding = true;
      return {VenueSessionFsmState::TEST_REQUEST_SENT, newCtx, false};
    }
    case VenueSessionFsmEvent::GapDetected:
      return {VenueSessionFsmState::RESEND_IN_PROGRESS, ctx, false};
    case VenueSessionFsmEvent::InboundResendRequest:
      return {VenueSessionFsmState::ACTIVE, ctx, false};
    case VenueSessionFsmEvent::SequenceResetReceived:
      return {VenueSessionFsmState::SEQUENCE_RESETTING, ctx, false};
    case VenueSessionFsmEvent::LogoutRequested:
      return {VenueSessionFsmState::LOGOUT_IN_PROGRESS, ctx, false};
    case VenueSessionFsmEvent::LogoutReceived:
      return {VenueSessionFsmState::LOGOUT_IN_PROGRESS, ctx, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::TEST_REQUEST_SENT:
    switch (event) {
    case VenueSessionFsmEvent::TestRequestResponse: {
      auto newCtx = ctx;
      newCtx.testRequestOutstanding = false;
      return {VenueSessionFsmState::ACTIVE, newCtx, false};
    }
    case VenueSessionFsmEvent::TestRequestTimeout:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::RESEND_IN_PROGRESS:
    switch (event) {
    case VenueSessionFsmEvent::ResendComplete: {
      auto newCtx = ctx;
      newCtx.resendWindowLow = static_cast<uint64_t>(0);
      newCtx.resendWindowHigh = static_cast<uint64_t>(0);
      return {VenueSessionFsmState::ACTIVE, newCtx, false};
    }
    case VenueSessionFsmEvent::SequenceResetReceived:
      return {VenueSessionFsmState::SEQUENCE_RESETTING, ctx, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::SEQUENCE_RESETTING:
    switch (event) {
    case VenueSessionFsmEvent::ResendComplete: {
      auto newCtx = ctx;
      newCtx.resendWindowLow = static_cast<uint64_t>(0);
      newCtx.resendWindowHigh = static_cast<uint64_t>(0);
      return {VenueSessionFsmState::ACTIVE, newCtx, false};
    }
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  case VenueSessionFsmState::LOGOUT_IN_PROGRESS:
    switch (event) {
    case VenueSessionFsmEvent::LogoutEchoed:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, false};
    default:
      return {state, ctx, true};
    }
  default:
    return {state, ctx, true};
  }
  return {state, ctx, true};
}

} // namespace crossasset::ems::fsm
