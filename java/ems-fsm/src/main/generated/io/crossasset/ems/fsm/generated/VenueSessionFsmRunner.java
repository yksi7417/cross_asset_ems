// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesession.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.List;
import java.util.Map;

/**
 * Pure transition function for VenueSessionFsm.
 *
 * <p>Call {@link #transition} with the current state, event, context, and optional
 * payload. The method returns a {@link TransitionResult} with the new state,
 * updated context, and list of effect descriptors to dispatch.
 *
 * <p>This class is generated from schemas/fsm/venuesessionfsm.fsm.yaml — do not hand-edit.
 */
public final class VenueSessionFsmRunner {

  private VenueSessionFsmRunner() {}

  /**
   * Execute one FSM transition.
   *
   * @param state   current state
   * @param event   incoming event
   * @param ctx     current context (will not be mutated; new context in result)
   * @param rawPayload event payload (may be null for zero-payload events)
   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row
   */
  public static TransitionResult<VenueSessionFsmState, VenueSessionFsmContext, VenueSessionFsmEffect>
      transition(
          VenueSessionFsmState state,
          VenueSessionFsmEvent event,
          VenueSessionFsmContext ctx,
          Object rawPayload) {

    return switch (state) {
      case DISCONNECTED -> switch (event) {
        case ConnectRequested -> {
          yield TransitionResult.of(
            VenueSessionFsmState.CONNECTING,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "initiate_tcp", "session_id", "{{ context.session_id }}"))));
        }
        default -> TransitionResult.noTransition(state);
      };
      case CONNECTING -> switch (event) {
        case TcpConnected -> {
          yield TransitionResult.of(
            VenueSessionFsmState.LOGON_SENT,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_logon", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "logon_timer", "duration_secs", "30"))));
        }
        case TcpFailed -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("TcpConnectionFailed"), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "reconnect_backoff", "duration_secs", "5"))));
        }
        default -> TransitionResult.noTransition(state);
      };
      case LOGON_SENT -> switch (event) {
        case LogonAcknowledged -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx,
            List.of(new VenueSessionFsmEffect.CancelTimer(Map.of("name", "logon_timer")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "heartbeat_rx_timer", "duration_secs", "{{ context.heartbeat_interval_secs }}")), new VenueSessionFsmEffect.PublishEventLog("SessionActive")));
        }
        case LogonRejected -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.CancelTimer(Map.of("name", "logon_timer")), new VenueSessionFsmEffect.Notify(Map.of("signal", "close_socket", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.PublishEventLog("LogonRejected")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case ACTIVE -> switch (event) {
        case HeartbeatReceived -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx,
            List.of(new VenueSessionFsmEffect.CancelTimer(Map.of("name", "heartbeat_rx_timer")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "heartbeat_rx_timer", "duration_secs", "{{ context.heartbeat_interval_secs }}"))));
        }
        case HeartbeatOverdue -> {
          yield TransitionResult.of(
            VenueSessionFsmState.TEST_REQUEST_SENT,
            ctx.with(ctx.sessionId(), ctx.nextExpectedSeqIn(), ctx.nextSendSeqOut(), ctx.heartbeatIntervalSecs(), true, ctx.resendWindowLow(), ctx.resendWindowHigh(), ctx.venueMic()),
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_test_request", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "test_request_timer", "duration_secs", "{{ context.heartbeat_interval_secs }}"))));
        }
        case GapDetected -> {
          yield TransitionResult.of(
            VenueSessionFsmState.RESEND_IN_PROGRESS,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_resend_request", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.PublishEventLog("GapDetected")));
        }
        case InboundResendRequest -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_resend_messages", "session_id", "{{ context.session_id }}"))));
        }
        case SequenceResetReceived -> {
          yield TransitionResult.of(
            VenueSessionFsmState.SEQUENCE_RESETTING,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("SequenceResetReceived")));
        }
        case LogoutRequested -> {
          yield TransitionResult.of(
            VenueSessionFsmState.LOGOUT_IN_PROGRESS,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_logout", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "logout_timer", "duration_secs", "10")), new VenueSessionFsmEffect.PublishEventLog("LogoutInitiated")));
        }
        case LogoutReceived -> {
          yield TransitionResult.of(
            VenueSessionFsmState.LOGOUT_IN_PROGRESS,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "send_logout", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "logout_timer", "duration_secs", "5")), new VenueSessionFsmEffect.PublishEventLog("LogoutEchoSent")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case TEST_REQUEST_SENT -> switch (event) {
        case TestRequestResponse -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx.with(ctx.sessionId(), ctx.nextExpectedSeqIn(), ctx.nextSendSeqOut(), ctx.heartbeatIntervalSecs(), false, ctx.resendWindowLow(), ctx.resendWindowHigh(), ctx.venueMic()),
            List.of(new VenueSessionFsmEffect.CancelTimer(Map.of("name", "test_request_timer")), new VenueSessionFsmEffect.ScheduleTimer(Map.of("name", "heartbeat_rx_timer", "duration_secs", "{{ context.heartbeat_interval_secs }}"))));
        }
        case TestRequestTimeout -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.Notify(Map.of("signal", "close_socket", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.PublishEventLog("SessionStale")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case RESEND_IN_PROGRESS -> switch (event) {
        case ResendComplete -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx.with(ctx.sessionId(), ctx.nextExpectedSeqIn(), ctx.nextSendSeqOut(), ctx.heartbeatIntervalSecs(), ctx.testRequestOutstanding(), 0L, 0L, ctx.venueMic()),
            List.of(new VenueSessionFsmEffect.PublishEventLog("ResendComplete")));
        }
        case SequenceResetReceived -> {
          yield TransitionResult.of(
            VenueSessionFsmState.SEQUENCE_RESETTING,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("SequenceResetReceived")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case SEQUENCE_RESETTING -> switch (event) {
        case ResendComplete -> {
          yield TransitionResult.of(
            VenueSessionFsmState.ACTIVE,
            ctx.with(ctx.sessionId(), ctx.nextExpectedSeqIn(), ctx.nextSendSeqOut(), ctx.heartbeatIntervalSecs(), ctx.testRequestOutstanding(), 0L, 0L, ctx.venueMic()),
            List.of(new VenueSessionFsmEffect.PublishEventLog("SequenceResetApplied")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case LOGOUT_IN_PROGRESS -> switch (event) {
        case LogoutEchoed -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.CancelTimer(Map.of("name", "logout_timer")), new VenueSessionFsmEffect.Notify(Map.of("signal", "close_socket", "session_id", "{{ context.session_id }}")), new VenueSessionFsmEffect.PublishEventLog("SessionClosed")));
        }
        case UnexpectedDisconnect -> {
          yield TransitionResult.of(
            VenueSessionFsmState.DISCONNECTED,
            ctx,
            List.of(new VenueSessionFsmEffect.PublishEventLog("UnexpectedDisconnect")));
        }
        default -> TransitionResult.noTransition(state);
      };
    };
  }
}
