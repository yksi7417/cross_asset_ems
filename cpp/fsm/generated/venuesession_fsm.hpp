// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesessionfsm.fsm.yaml
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

// ── Names ────────────────────────────────────────────────────────────────────
//
// State and event names reach the output journal, which the conformance gate
// compares byte-for-byte across three languages — so these must match the Java
// enum constants character for character.

inline const char* name(VenueSessionFsmState state) noexcept {
  switch (state) {
    case VenueSessionFsmState::DISCONNECTED: return "DISCONNECTED";
    case VenueSessionFsmState::CONNECTING: return "CONNECTING";
    case VenueSessionFsmState::LOGON_SENT: return "LOGON_SENT";
    case VenueSessionFsmState::ACTIVE: return "ACTIVE";
    case VenueSessionFsmState::TEST_REQUEST_SENT: return "TEST_REQUEST_SENT";
    case VenueSessionFsmState::RESEND_IN_PROGRESS: return "RESEND_IN_PROGRESS";
    case VenueSessionFsmState::SEQUENCE_RESETTING: return "SEQUENCE_RESETTING";
    case VenueSessionFsmState::LOGOUT_IN_PROGRESS: return "LOGOUT_IN_PROGRESS";
  }
  return "UNKNOWN";
}

inline const char* name(VenueSessionFsmEvent event) noexcept {
  switch (event) {
    case VenueSessionFsmEvent::ConnectRequested: return "ConnectRequested";
    case VenueSessionFsmEvent::TcpConnected: return "TcpConnected";
    case VenueSessionFsmEvent::TcpFailed: return "TcpFailed";
    case VenueSessionFsmEvent::LogonAcknowledged: return "LogonAcknowledged";
    case VenueSessionFsmEvent::LogonRejected: return "LogonRejected";
    case VenueSessionFsmEvent::HeartbeatReceived: return "HeartbeatReceived";
    case VenueSessionFsmEvent::HeartbeatOverdue: return "HeartbeatOverdue";
    case VenueSessionFsmEvent::TestRequestResponse: return "TestRequestResponse";
    case VenueSessionFsmEvent::TestRequestTimeout: return "TestRequestTimeout";
    case VenueSessionFsmEvent::GapDetected: return "GapDetected";
    case VenueSessionFsmEvent::ResendComplete: return "ResendComplete";
    case VenueSessionFsmEvent::InboundResendRequest: return "InboundResendRequest";
    case VenueSessionFsmEvent::SequenceResetReceived: return "SequenceResetReceived";
    case VenueSessionFsmEvent::LogoutRequested: return "LogoutRequested";
    case VenueSessionFsmEvent::LogoutReceived: return "LogoutReceived";
    case VenueSessionFsmEvent::LogoutEchoed: return "LogoutEchoed";
    case VenueSessionFsmEvent::UnexpectedDisconnect: return "UnexpectedDisconnect";
  }
  return "UNKNOWN";
}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<VenueSessionFsmEvent> VenueSessionFsmEventFromName(std::string_view name) {
  if (name == "ConnectRequested") return VenueSessionFsmEvent::ConnectRequested;
  if (name == "TcpConnected") return VenueSessionFsmEvent::TcpConnected;
  if (name == "TcpFailed") return VenueSessionFsmEvent::TcpFailed;
  if (name == "LogonAcknowledged") return VenueSessionFsmEvent::LogonAcknowledged;
  if (name == "LogonRejected") return VenueSessionFsmEvent::LogonRejected;
  if (name == "HeartbeatReceived") return VenueSessionFsmEvent::HeartbeatReceived;
  if (name == "HeartbeatOverdue") return VenueSessionFsmEvent::HeartbeatOverdue;
  if (name == "TestRequestResponse") return VenueSessionFsmEvent::TestRequestResponse;
  if (name == "TestRequestTimeout") return VenueSessionFsmEvent::TestRequestTimeout;
  if (name == "GapDetected") return VenueSessionFsmEvent::GapDetected;
  if (name == "ResendComplete") return VenueSessionFsmEvent::ResendComplete;
  if (name == "InboundResendRequest") return VenueSessionFsmEvent::InboundResendRequest;
  if (name == "SequenceResetReceived") return VenueSessionFsmEvent::SequenceResetReceived;
  if (name == "LogoutRequested") return VenueSessionFsmEvent::LogoutRequested;
  if (name == "LogoutReceived") return VenueSessionFsmEvent::LogoutReceived;
  if (name == "LogoutEchoed") return VenueSessionFsmEvent::LogoutEchoed;
  if (name == "UnexpectedDisconnect") return VenueSessionFsmEvent::UnexpectedDisconnect;
  return std::nullopt;
}

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
enum class VenueSessionFsmEffectKind : uint8_t {
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
};

/// One `key: value` pair from an effect's `args` map in the schema.
struct VenueSessionFsmEffectArg {
  std::string_view key{};
  std::string_view value{};
};

struct VenueSessionFsmEffect {
  VenueSessionFsmEffectKind kind{};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const VenueSessionFsmEffectArg> args{};
};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct VenueSessionFsmTransitionResult {
  VenueSessionFsmState newState;
  VenueSessionFsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const VenueSessionFsmEffect> effects;
  bool isNoTransition;
};


// ── Effect tables ────────────────────────────────────────────────────────────
//
// One table per transition that asks for something. `transition` returns a span
// over the matching table, so the effects cost nothing to return and outlive any
// caller.
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs0_0 = {{VenueSessionFsmEffectArg{"signal", "initiate_tcp"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects0 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs0_0}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs1_0 = {{VenueSessionFsmEffectArg{"signal", "send_logon"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs1_1 = {{VenueSessionFsmEffectArg{"name", "logon_timer"}, VenueSessionFsmEffectArg{"duration_secs", "30"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects1 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs1_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs1_1}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs2_1 = {{VenueSessionFsmEffectArg{"name", "reconnect_backoff"}, VenueSessionFsmEffectArg{"duration_secs", "5"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects2 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "TcpConnectionFailed", {}}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs2_1}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 1> kVenueSessionFsmEffectArgs3_0 = {{VenueSessionFsmEffectArg{"name", "logon_timer"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs3_1 = {{VenueSessionFsmEffectArg{"name", "heartbeat_rx_timer"}, VenueSessionFsmEffectArg{"duration_secs", "{{ context.heartbeat_interval_secs }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 3> kVenueSessionFsmEffects3 = {{{VenueSessionFsmEffectKind::CancelTimer, {}, {}, kVenueSessionFsmEffectArgs3_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs3_1}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "SessionActive", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 1> kVenueSessionFsmEffectArgs4_0 = {{VenueSessionFsmEffectArg{"name", "logon_timer"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs4_1 = {{VenueSessionFsmEffectArg{"signal", "close_socket"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 3> kVenueSessionFsmEffects4 = {{{VenueSessionFsmEffectKind::CancelTimer, {}, {}, kVenueSessionFsmEffectArgs4_0}, {VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs4_1}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "LogonRejected", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 1> kVenueSessionFsmEffectArgs5_0 = {{VenueSessionFsmEffectArg{"name", "heartbeat_rx_timer"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs5_1 = {{VenueSessionFsmEffectArg{"name", "heartbeat_rx_timer"}, VenueSessionFsmEffectArg{"duration_secs", "{{ context.heartbeat_interval_secs }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects5 = {{{VenueSessionFsmEffectKind::CancelTimer, {}, {}, kVenueSessionFsmEffectArgs5_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs5_1}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs6_0 = {{VenueSessionFsmEffectArg{"signal", "send_test_request"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs6_1 = {{VenueSessionFsmEffectArg{"name", "test_request_timer"}, VenueSessionFsmEffectArg{"duration_secs", "{{ context.heartbeat_interval_secs }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects6 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs6_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs6_1}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 1> kVenueSessionFsmEffectArgs7_0 = {{VenueSessionFsmEffectArg{"name", "test_request_timer"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs7_1 = {{VenueSessionFsmEffectArg{"name", "heartbeat_rx_timer"}, VenueSessionFsmEffectArg{"duration_secs", "{{ context.heartbeat_interval_secs }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects7 = {{{VenueSessionFsmEffectKind::CancelTimer, {}, {}, kVenueSessionFsmEffectArgs7_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs7_1}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs8_0 = {{VenueSessionFsmEffectArg{"signal", "close_socket"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects8 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs8_0}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "SessionStale", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs9_0 = {{VenueSessionFsmEffectArg{"signal", "send_resend_request"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 2> kVenueSessionFsmEffects9 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs9_0}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "GapDetected", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects10 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "ResendComplete", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs11_0 = {{VenueSessionFsmEffectArg{"signal", "send_resend_messages"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects11 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs11_0}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects12 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "SequenceResetReceived", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects13 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "SequenceResetReceived", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects14 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "SequenceResetApplied", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs15_0 = {{VenueSessionFsmEffectArg{"signal", "send_logout"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs15_1 = {{VenueSessionFsmEffectArg{"name", "logout_timer"}, VenueSessionFsmEffectArg{"duration_secs", "10"}}};
inline constexpr std::array<VenueSessionFsmEffect, 3> kVenueSessionFsmEffects15 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs15_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs15_1}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "LogoutInitiated", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs16_0 = {{VenueSessionFsmEffectArg{"signal", "send_logout"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs16_1 = {{VenueSessionFsmEffectArg{"name", "logout_timer"}, VenueSessionFsmEffectArg{"duration_secs", "5"}}};
inline constexpr std::array<VenueSessionFsmEffect, 3> kVenueSessionFsmEffects16 = {{{VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs16_0}, {VenueSessionFsmEffectKind::ScheduleTimer, {}, {}, kVenueSessionFsmEffectArgs16_1}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "LogoutEchoSent", {}}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 1> kVenueSessionFsmEffectArgs17_0 = {{VenueSessionFsmEffectArg{"name", "logout_timer"}}};
inline constexpr std::array<VenueSessionFsmEffectArg, 2> kVenueSessionFsmEffectArgs17_1 = {{VenueSessionFsmEffectArg{"signal", "close_socket"}, VenueSessionFsmEffectArg{"session_id", "{{ context.session_id }}"}}};
inline constexpr std::array<VenueSessionFsmEffect, 3> kVenueSessionFsmEffects17 = {{{VenueSessionFsmEffectKind::CancelTimer, {}, {}, kVenueSessionFsmEffectArgs17_0}, {VenueSessionFsmEffectKind::Notify, {}, {}, kVenueSessionFsmEffectArgs17_1}, {VenueSessionFsmEffectKind::PublishEventLog, {}, "SessionClosed", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects18 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects19 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects20 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects21 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects22 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};
inline constexpr std::array<VenueSessionFsmEffect, 1> kVenueSessionFsmEffects23 = {{{VenueSessionFsmEffectKind::PublishEventLog, {}, "UnexpectedDisconnect", {}}}};

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
      return {VenueSessionFsmState::CONNECTING, ctx, kVenueSessionFsmEffects0, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::CONNECTING:
    switch (event) {
    case VenueSessionFsmEvent::TcpConnected:
      return {VenueSessionFsmState::LOGON_SENT, ctx, kVenueSessionFsmEffects1, false};
    case VenueSessionFsmEvent::TcpFailed:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects2, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::LOGON_SENT:
    switch (event) {
    case VenueSessionFsmEvent::LogonAcknowledged:
      return {VenueSessionFsmState::ACTIVE, ctx, kVenueSessionFsmEffects3, false};
    case VenueSessionFsmEvent::LogonRejected:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects4, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects18, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::ACTIVE:
    switch (event) {
    case VenueSessionFsmEvent::HeartbeatReceived:
      return {VenueSessionFsmState::ACTIVE, ctx, kVenueSessionFsmEffects5, false};
    case VenueSessionFsmEvent::HeartbeatOverdue: {
      auto newCtx = ctx;
      newCtx.testRequestOutstanding = true;
      return {VenueSessionFsmState::TEST_REQUEST_SENT, newCtx, kVenueSessionFsmEffects6, false};
    }
    case VenueSessionFsmEvent::GapDetected:
      return {VenueSessionFsmState::RESEND_IN_PROGRESS, ctx, kVenueSessionFsmEffects9, false};
    case VenueSessionFsmEvent::InboundResendRequest:
      return {VenueSessionFsmState::ACTIVE, ctx, kVenueSessionFsmEffects11, false};
    case VenueSessionFsmEvent::SequenceResetReceived:
      return {VenueSessionFsmState::SEQUENCE_RESETTING, ctx, kVenueSessionFsmEffects12, false};
    case VenueSessionFsmEvent::LogoutRequested:
      return {VenueSessionFsmState::LOGOUT_IN_PROGRESS, ctx, kVenueSessionFsmEffects15, false};
    case VenueSessionFsmEvent::LogoutReceived:
      return {VenueSessionFsmState::LOGOUT_IN_PROGRESS, ctx, kVenueSessionFsmEffects16, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects19, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::TEST_REQUEST_SENT:
    switch (event) {
    case VenueSessionFsmEvent::TestRequestResponse: {
      auto newCtx = ctx;
      newCtx.testRequestOutstanding = false;
      return {VenueSessionFsmState::ACTIVE, newCtx, kVenueSessionFsmEffects7, false};
    }
    case VenueSessionFsmEvent::TestRequestTimeout:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects8, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects20, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::RESEND_IN_PROGRESS:
    switch (event) {
    case VenueSessionFsmEvent::ResendComplete: {
      auto newCtx = ctx;
      newCtx.resendWindowLow = static_cast<uint64_t>(0);
      newCtx.resendWindowHigh = static_cast<uint64_t>(0);
      return {VenueSessionFsmState::ACTIVE, newCtx, kVenueSessionFsmEffects10, false};
    }
    case VenueSessionFsmEvent::SequenceResetReceived:
      return {VenueSessionFsmState::SEQUENCE_RESETTING, ctx, kVenueSessionFsmEffects13, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects21, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::SEQUENCE_RESETTING:
    switch (event) {
    case VenueSessionFsmEvent::ResendComplete: {
      auto newCtx = ctx;
      newCtx.resendWindowLow = static_cast<uint64_t>(0);
      newCtx.resendWindowHigh = static_cast<uint64_t>(0);
      return {VenueSessionFsmState::ACTIVE, newCtx, kVenueSessionFsmEffects14, false};
    }
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects22, false};
    default:
      return {state, ctx, {}, true};
    }
  case VenueSessionFsmState::LOGOUT_IN_PROGRESS:
    switch (event) {
    case VenueSessionFsmEvent::LogoutEchoed:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects17, false};
    case VenueSessionFsmEvent::UnexpectedDisconnect:
      return {VenueSessionFsmState::DISCONNECTED, ctx, kVenueSessionFsmEffects23, false};
    default:
      return {state, ctx, {}, true};
    }
  default:
    return {state, ctx, {}, true};
  }
  return {state, ctx, {}, true};
}

} // namespace crossasset::ems::fsm
