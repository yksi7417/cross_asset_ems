// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesession.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM events for {@link VenueSessionFsmRunner}. */
public enum VenueSessionFsmEvent {
  /** Operator or reconnect-scheduler requests a new connection attempt. */
  ConnectRequested,
  /** TCP handshake completed successfully. */
  TcpConnected,
  /** TCP connection attempt failed or timed out. */
  TcpFailed,
  /** Venue responded with 35=A Logon; session is now active. */
  LogonAcknowledged,
  /** Venue responded with 35=5 Logout or 35=3 Reject during logon. */
  LogonRejected,
  /** Inbound 35=0 Heartbeat or any application message — resets timer. */
  HeartbeatReceived,
  /** Heartbeat timer expired without receiving any inbound message. */
  HeartbeatOverdue,
  /** Any inbound message received after sending a 35=1 TestRequest. */
  TestRequestResponse,
  /** No response to 35=1 TestRequest within 2× HeartBtInt; session stale. */
  TestRequestTimeout,
  /** Inbound sequence gap detected; EMS must send 35=2 ResendRequest. */
  GapDetected,
  /** All requested messages received (or gap-filled via 35=4 GapFill=Y). */
  ResendComplete,
  /** Venue sent 35=2 ResendRequest; EMS must replay the requested range. */
  InboundResendRequest,
  /** Venue sent 35=4 SequenceReset (NewSeqNo in GapFill or Reset mode). */
  SequenceResetReceived,
  /** Operator or session manager initiated graceful logout. */
  LogoutRequested,
  /** Venue sent 35=5 Logout; EMS must echo back and then disconnect. */
  LogoutReceived,
  /** EMS echoed the venue-initiated 35=5; session can now close. */
  LogoutEchoed,
  /** TCP connection dropped unexpectedly from any live state. */
  UnexpectedDisconnect;
}
