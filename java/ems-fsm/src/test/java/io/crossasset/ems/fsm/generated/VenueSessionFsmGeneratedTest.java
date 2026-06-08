package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.VenueSessionFsmState.*;
import static io.crossasset.ems.fsm.generated.VenueSessionFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class VenueSessionFsmGeneratedTest {

  private static VenueSessionFsmContext minimalCtx() {
    return new VenueSessionFsmContext("default", 0L, 0L, 0, false, 0L, 0L, "default");
  }

  @Test
  void test_trans_0_DISCONNECTED_ConnectRequested_to_CONNECTING() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, ConnectRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from DISCONNECTED on ConnectRequested");
    assertEquals(CONNECTING, result.newState());
  }

  @Test
  void test_trans_1_CONNECTING_TcpConnected_to_LOGON_SENT() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, TcpConnected, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from CONNECTING on TcpConnected");
    assertEquals(LOGON_SENT, result.newState());
  }

  @Test
  void test_trans_2_CONNECTING_TcpFailed_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, TcpFailed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from CONNECTING on TcpFailed");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_3_LOGON_SENT_LogonAcknowledged_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, LogonAcknowledged, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LOGON_SENT on LogonAcknowledged");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_4_LOGON_SENT_LogonRejected_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, LogonRejected, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LOGON_SENT on LogonRejected");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_5_ACTIVE_HeartbeatReceived_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, HeartbeatReceived, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on HeartbeatReceived");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_6_ACTIVE_HeartbeatOverdue_to_TEST_REQUEST_SENT() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, HeartbeatOverdue, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on HeartbeatOverdue");
    assertEquals(TEST_REQUEST_SENT, result.newState());
  }

  @Test
  void test_trans_7_TEST_REQUEST_SENT_TestRequestResponse_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, TestRequestResponse, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from TEST_REQUEST_SENT on TestRequestResponse");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_8_TEST_REQUEST_SENT_TestRequestTimeout_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, TestRequestTimeout, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from TEST_REQUEST_SENT on TestRequestTimeout");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_9_ACTIVE_GapDetected_to_RESEND_IN_PROGRESS() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, GapDetected, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on GapDetected");
    assertEquals(RESEND_IN_PROGRESS, result.newState());
  }

  @Test
  void test_trans_10_RESEND_IN_PROGRESS_ResendComplete_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, ResendComplete, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from RESEND_IN_PROGRESS on ResendComplete");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_11_ACTIVE_InboundResendRequest_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, InboundResendRequest, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on InboundResendRequest");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_12_ACTIVE_SequenceResetReceived_to_SEQUENCE_RESETTING() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, SequenceResetReceived, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on SequenceResetReceived");
    assertEquals(SEQUENCE_RESETTING, result.newState());
  }

  @Test
  void test_trans_13_RESEND_IN_PROGRESS_SequenceResetReceived_to_SEQUENCE_RESETTING() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, SequenceResetReceived, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from RESEND_IN_PROGRESS on SequenceResetReceived");
    assertEquals(SEQUENCE_RESETTING, result.newState());
  }

  @Test
  void test_trans_14_SEQUENCE_RESETTING_ResendComplete_to_ACTIVE() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, ResendComplete, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SEQUENCE_RESETTING on ResendComplete");
    assertEquals(ACTIVE, result.newState());
  }

  @Test
  void test_trans_15_ACTIVE_LogoutRequested_to_LOGOUT_IN_PROGRESS() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, LogoutRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on LogoutRequested");
    assertEquals(LOGOUT_IN_PROGRESS, result.newState());
  }

  @Test
  void test_trans_16_ACTIVE_LogoutReceived_to_LOGOUT_IN_PROGRESS() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, LogoutReceived, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on LogoutReceived");
    assertEquals(LOGOUT_IN_PROGRESS, result.newState());
  }

  @Test
  void test_trans_17_LOGOUT_IN_PROGRESS_LogoutEchoed_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, LogoutEchoed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LOGOUT_IN_PROGRESS on LogoutEchoed");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_18_LOGON_SENT_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LOGON_SENT on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_19_ACTIVE_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from ACTIVE on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_20_TEST_REQUEST_SENT_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from TEST_REQUEST_SENT on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_21_RESEND_IN_PROGRESS_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from RESEND_IN_PROGRESS on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_22_SEQUENCE_RESETTING_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SEQUENCE_RESETTING on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_trans_23_LOGOUT_IN_PROGRESS_UnexpectedDisconnect_to_DISCONNECTED() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, UnexpectedDisconnect, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LOGOUT_IN_PROGRESS on UnexpectedDisconnect");
    assertEquals(DISCONNECTED, result.newState());
  }

  @Test
  void test_no_trans_0_1_DISCONNECTED_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with TcpConnected");
  }

  @Test
  void test_no_trans_0_2_DISCONNECTED_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with TcpFailed");
  }

  @Test
  void test_no_trans_0_3_DISCONNECTED_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with LogonAcknowledged");
  }

  @Test
  void test_no_trans_0_4_DISCONNECTED_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with LogonRejected");
  }

  @Test
  void test_no_trans_0_5_DISCONNECTED_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with HeartbeatReceived");
  }

  @Test
  void test_no_trans_0_6_DISCONNECTED_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_0_7_DISCONNECTED_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with TestRequestResponse");
  }

  @Test
  void test_no_trans_0_8_DISCONNECTED_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with TestRequestTimeout");
  }

  @Test
  void test_no_trans_0_9_DISCONNECTED_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with GapDetected");
  }

  @Test
  void test_no_trans_0_10_DISCONNECTED_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with ResendComplete");
  }

  @Test
  void test_no_trans_0_11_DISCONNECTED_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with InboundResendRequest");
  }

  @Test
  void test_no_trans_0_12_DISCONNECTED_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with SequenceResetReceived");
  }

  @Test
  void test_no_trans_0_13_DISCONNECTED_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with LogoutRequested");
  }

  @Test
  void test_no_trans_0_14_DISCONNECTED_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with LogoutReceived");
  }

  @Test
  void test_no_trans_0_15_DISCONNECTED_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with LogoutEchoed");
  }

  @Test
  void test_no_trans_0_16_DISCONNECTED_UnexpectedDisconnect() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(DISCONNECTED, UnexpectedDisconnect, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DISCONNECTED with UnexpectedDisconnect");
  }

  @Test
  void test_no_trans_1_0_CONNECTING_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with ConnectRequested");
  }

  @Test
  void test_no_trans_1_3_CONNECTING_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with LogonAcknowledged");
  }

  @Test
  void test_no_trans_1_4_CONNECTING_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with LogonRejected");
  }

  @Test
  void test_no_trans_1_5_CONNECTING_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with HeartbeatReceived");
  }

  @Test
  void test_no_trans_1_6_CONNECTING_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_1_7_CONNECTING_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with TestRequestResponse");
  }

  @Test
  void test_no_trans_1_8_CONNECTING_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with TestRequestTimeout");
  }

  @Test
  void test_no_trans_1_9_CONNECTING_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with GapDetected");
  }

  @Test
  void test_no_trans_1_10_CONNECTING_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with ResendComplete");
  }

  @Test
  void test_no_trans_1_11_CONNECTING_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with InboundResendRequest");
  }

  @Test
  void test_no_trans_1_12_CONNECTING_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with SequenceResetReceived");
  }

  @Test
  void test_no_trans_1_13_CONNECTING_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with LogoutRequested");
  }

  @Test
  void test_no_trans_1_14_CONNECTING_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with LogoutReceived");
  }

  @Test
  void test_no_trans_1_15_CONNECTING_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with LogoutEchoed");
  }

  @Test
  void test_no_trans_1_16_CONNECTING_UnexpectedDisconnect() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(CONNECTING, UnexpectedDisconnect, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CONNECTING with UnexpectedDisconnect");
  }

  @Test
  void test_no_trans_2_0_LOGON_SENT_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with ConnectRequested");
  }

  @Test
  void test_no_trans_2_1_LOGON_SENT_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with TcpConnected");
  }

  @Test
  void test_no_trans_2_2_LOGON_SENT_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with TcpFailed");
  }

  @Test
  void test_no_trans_2_5_LOGON_SENT_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with HeartbeatReceived");
  }

  @Test
  void test_no_trans_2_6_LOGON_SENT_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_2_7_LOGON_SENT_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with TestRequestResponse");
  }

  @Test
  void test_no_trans_2_8_LOGON_SENT_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with TestRequestTimeout");
  }

  @Test
  void test_no_trans_2_9_LOGON_SENT_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with GapDetected");
  }

  @Test
  void test_no_trans_2_10_LOGON_SENT_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with ResendComplete");
  }

  @Test
  void test_no_trans_2_11_LOGON_SENT_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with InboundResendRequest");
  }

  @Test
  void test_no_trans_2_12_LOGON_SENT_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with SequenceResetReceived");
  }

  @Test
  void test_no_trans_2_13_LOGON_SENT_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with LogoutRequested");
  }

  @Test
  void test_no_trans_2_14_LOGON_SENT_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with LogoutReceived");
  }

  @Test
  void test_no_trans_2_15_LOGON_SENT_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGON_SENT, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGON_SENT with LogoutEchoed");
  }

  @Test
  void test_no_trans_3_0_ACTIVE_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with ConnectRequested");
  }

  @Test
  void test_no_trans_3_1_ACTIVE_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with TcpConnected");
  }

  @Test
  void test_no_trans_3_2_ACTIVE_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with TcpFailed");
  }

  @Test
  void test_no_trans_3_3_ACTIVE_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with LogonAcknowledged");
  }

  @Test
  void test_no_trans_3_4_ACTIVE_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with LogonRejected");
  }

  @Test
  void test_no_trans_3_7_ACTIVE_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with TestRequestResponse");
  }

  @Test
  void test_no_trans_3_8_ACTIVE_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with TestRequestTimeout");
  }

  @Test
  void test_no_trans_3_10_ACTIVE_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with ResendComplete");
  }

  @Test
  void test_no_trans_3_15_ACTIVE_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(ACTIVE, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ACTIVE with LogoutEchoed");
  }

  @Test
  void test_no_trans_4_0_TEST_REQUEST_SENT_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with ConnectRequested");
  }

  @Test
  void test_no_trans_4_1_TEST_REQUEST_SENT_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with TcpConnected");
  }

  @Test
  void test_no_trans_4_2_TEST_REQUEST_SENT_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with TcpFailed");
  }

  @Test
  void test_no_trans_4_3_TEST_REQUEST_SENT_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with LogonAcknowledged");
  }

  @Test
  void test_no_trans_4_4_TEST_REQUEST_SENT_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with LogonRejected");
  }

  @Test
  void test_no_trans_4_5_TEST_REQUEST_SENT_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with HeartbeatReceived");
  }

  @Test
  void test_no_trans_4_6_TEST_REQUEST_SENT_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_4_9_TEST_REQUEST_SENT_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with GapDetected");
  }

  @Test
  void test_no_trans_4_10_TEST_REQUEST_SENT_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with ResendComplete");
  }

  @Test
  void test_no_trans_4_11_TEST_REQUEST_SENT_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with InboundResendRequest");
  }

  @Test
  void test_no_trans_4_12_TEST_REQUEST_SENT_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with SequenceResetReceived");
  }

  @Test
  void test_no_trans_4_13_TEST_REQUEST_SENT_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with LogoutRequested");
  }

  @Test
  void test_no_trans_4_14_TEST_REQUEST_SENT_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with LogoutReceived");
  }

  @Test
  void test_no_trans_4_15_TEST_REQUEST_SENT_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(TEST_REQUEST_SENT, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TEST_REQUEST_SENT with LogoutEchoed");
  }

  @Test
  void test_no_trans_5_0_RESEND_IN_PROGRESS_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with ConnectRequested");
  }

  @Test
  void test_no_trans_5_1_RESEND_IN_PROGRESS_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with TcpConnected");
  }

  @Test
  void test_no_trans_5_2_RESEND_IN_PROGRESS_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with TcpFailed");
  }

  @Test
  void test_no_trans_5_3_RESEND_IN_PROGRESS_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with LogonAcknowledged");
  }

  @Test
  void test_no_trans_5_4_RESEND_IN_PROGRESS_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with LogonRejected");
  }

  @Test
  void test_no_trans_5_5_RESEND_IN_PROGRESS_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with HeartbeatReceived");
  }

  @Test
  void test_no_trans_5_6_RESEND_IN_PROGRESS_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_5_7_RESEND_IN_PROGRESS_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with TestRequestResponse");
  }

  @Test
  void test_no_trans_5_8_RESEND_IN_PROGRESS_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with TestRequestTimeout");
  }

  @Test
  void test_no_trans_5_9_RESEND_IN_PROGRESS_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with GapDetected");
  }

  @Test
  void test_no_trans_5_11_RESEND_IN_PROGRESS_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with InboundResendRequest");
  }

  @Test
  void test_no_trans_5_13_RESEND_IN_PROGRESS_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with LogoutRequested");
  }

  @Test
  void test_no_trans_5_14_RESEND_IN_PROGRESS_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with LogoutReceived");
  }

  @Test
  void test_no_trans_5_15_RESEND_IN_PROGRESS_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(RESEND_IN_PROGRESS, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for RESEND_IN_PROGRESS with LogoutEchoed");
  }

  @Test
  void test_no_trans_6_0_SEQUENCE_RESETTING_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with ConnectRequested");
  }

  @Test
  void test_no_trans_6_1_SEQUENCE_RESETTING_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with TcpConnected");
  }

  @Test
  void test_no_trans_6_2_SEQUENCE_RESETTING_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with TcpFailed");
  }

  @Test
  void test_no_trans_6_3_SEQUENCE_RESETTING_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with LogonAcknowledged");
  }

  @Test
  void test_no_trans_6_4_SEQUENCE_RESETTING_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with LogonRejected");
  }

  @Test
  void test_no_trans_6_5_SEQUENCE_RESETTING_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with HeartbeatReceived");
  }

  @Test
  void test_no_trans_6_6_SEQUENCE_RESETTING_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_6_7_SEQUENCE_RESETTING_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with TestRequestResponse");
  }

  @Test
  void test_no_trans_6_8_SEQUENCE_RESETTING_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with TestRequestTimeout");
  }

  @Test
  void test_no_trans_6_9_SEQUENCE_RESETTING_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with GapDetected");
  }

  @Test
  void test_no_trans_6_11_SEQUENCE_RESETTING_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with InboundResendRequest");
  }

  @Test
  void test_no_trans_6_12_SEQUENCE_RESETTING_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with SequenceResetReceived");
  }

  @Test
  void test_no_trans_6_13_SEQUENCE_RESETTING_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with LogoutRequested");
  }

  @Test
  void test_no_trans_6_14_SEQUENCE_RESETTING_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with LogoutReceived");
  }

  @Test
  void test_no_trans_6_15_SEQUENCE_RESETTING_LogoutEchoed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(SEQUENCE_RESETTING, LogoutEchoed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SEQUENCE_RESETTING with LogoutEchoed");
  }

  @Test
  void test_no_trans_7_0_LOGOUT_IN_PROGRESS_ConnectRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, ConnectRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with ConnectRequested");
  }

  @Test
  void test_no_trans_7_1_LOGOUT_IN_PROGRESS_TcpConnected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, TcpConnected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with TcpConnected");
  }

  @Test
  void test_no_trans_7_2_LOGOUT_IN_PROGRESS_TcpFailed() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, TcpFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with TcpFailed");
  }

  @Test
  void test_no_trans_7_3_LOGOUT_IN_PROGRESS_LogonAcknowledged() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, LogonAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with LogonAcknowledged");
  }

  @Test
  void test_no_trans_7_4_LOGOUT_IN_PROGRESS_LogonRejected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, LogonRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with LogonRejected");
  }

  @Test
  void test_no_trans_7_5_LOGOUT_IN_PROGRESS_HeartbeatReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, HeartbeatReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with HeartbeatReceived");
  }

  @Test
  void test_no_trans_7_6_LOGOUT_IN_PROGRESS_HeartbeatOverdue() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, HeartbeatOverdue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with HeartbeatOverdue");
  }

  @Test
  void test_no_trans_7_7_LOGOUT_IN_PROGRESS_TestRequestResponse() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, TestRequestResponse, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with TestRequestResponse");
  }

  @Test
  void test_no_trans_7_8_LOGOUT_IN_PROGRESS_TestRequestTimeout() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, TestRequestTimeout, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with TestRequestTimeout");
  }

  @Test
  void test_no_trans_7_9_LOGOUT_IN_PROGRESS_GapDetected() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, GapDetected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with GapDetected");
  }

  @Test
  void test_no_trans_7_10_LOGOUT_IN_PROGRESS_ResendComplete() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, ResendComplete, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with ResendComplete");
  }

  @Test
  void test_no_trans_7_11_LOGOUT_IN_PROGRESS_InboundResendRequest() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, InboundResendRequest, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with InboundResendRequest");
  }

  @Test
  void test_no_trans_7_12_LOGOUT_IN_PROGRESS_SequenceResetReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, SequenceResetReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with SequenceResetReceived");
  }

  @Test
  void test_no_trans_7_13_LOGOUT_IN_PROGRESS_LogoutRequested() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, LogoutRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with LogoutRequested");
  }

  @Test
  void test_no_trans_7_14_LOGOUT_IN_PROGRESS_LogoutReceived() {
    var ctx = minimalCtx();
    var result = VenueSessionFsmRunner.transition(LOGOUT_IN_PROGRESS, LogoutReceived, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LOGOUT_IN_PROGRESS with LogoutReceived");
  }

}