package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.RouteFsmState.*;
import static io.crossasset.ems.fsm.generated.RouteFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class RouteFsmGeneratedTest {

  private static RouteFsmContext minimalCtx() {
    return new RouteFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, 0L, "default", "default");
  }

  private static Object createRouteReplaceRequestedPayload() {
    return new RouteFsmPayloads.RouteReplaceRequestedPayload("default", 0L, 0L);
  }

  private static Object createRouteReplacedPayload() {
    return new RouteFsmPayloads.RouteReplacedPayload("default");
  }

  private static Object createRouteReplaceRejectedPayload() {
    return new RouteFsmPayloads.RouteReplaceRejectedPayload(0);
  }

  private static Object createRouteCancelRejectedPayload() {
    return new RouteFsmPayloads.RouteCancelRejectedPayload(0);
  }

  private static Object createRoutePartiallyFilledPayload() {
    return new RouteFsmPayloads.RoutePartiallyFilledPayload(0L, 0L, "default");
  }

  private static Object createRouteFilledPayload() {
    return new RouteFsmPayloads.RouteFilledPayload(0L, 0L, "default");
  }

  @Test
  void test_PENDING_RouteSent_to_SENT() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteSent, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING on RouteSent");
    assertEquals(SENT, result.newState());
  }

  @Test
  void test_SENT_RoutePendingNewAtVenue_to_PENDING_NEW_AT_VENUE() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RoutePendingNewAtVenue, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RoutePendingNewAtVenue");
    assertEquals(PENDING_NEW_AT_VENUE, result.newState());
  }

  @Test
  void test_PENDING_NEW_AT_VENUE_RouteAcknowledged_to_WORKING() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteAcknowledged, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW_AT_VENUE on RouteAcknowledged");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_SENT_RouteAcknowledged_to_WORKING() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteAcknowledged, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RouteAcknowledged");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_SENT_RouteRejected_to_REJECTED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteRejected, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RouteRejected");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_WORKING_RouteReplaceRequested_to_PENDING_REPLACE_AT_VENUE() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteReplaceRequested");
    assertEquals(PENDING_REPLACE_AT_VENUE, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RouteReplacePendingAtVenue_to_PENDING_REPLACE_AT_VENUE() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplacePendingAtVenue");
    assertEquals(PENDING_REPLACE_AT_VENUE, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RouteReplaced_to_WORKING() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplaced");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RouteReplaceRejected_to_WORKING() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplaceRejected");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_WORKING_RouteCancelRequested_to_PENDING_CANCEL_AT_VENUE() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteCancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteCancelRequested");
    assertEquals(PENDING_CANCEL_AT_VENUE, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_RouteCancelRequested_to_PENDING_CANCEL_AT_VENUE() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteCancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteCancelRequested");
    assertEquals(PENDING_CANCEL_AT_VENUE, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_AT_VENUE_RouteCanceled_to_CANCELED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCanceled, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCanceled");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_AT_VENUE_RouteCancelRejected_to_WORKING() {
    // guard: pre_cancel_status == '0'
    var ctx = new RouteFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, null, 0L, 0L, 0L, "default", "0");
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCancelRejected");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_AT_VENUE_RouteCancelRejected_to_PARTIALLY_FILLED() {
    // guard: pre_cancel_status == '1'
    var ctx = new RouteFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, null, 0L, 0L, 0L, "default", "1");
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCancelRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_WORKING_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_WORKING_RouteFilled_to_FILLED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteFilled, ctx, createRouteFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_RouteFilled_to_FILLED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteFilled, ctx, createRouteFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_WORKING_RouteExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_RouteExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_WORKING_RouteSuperseded_to_SUPERSEDED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteSuperseded, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteSuperseded");
    assertEquals(SUPERSEDED, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RouteSuperseded_to_SUPERSEDED() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteSuperseded, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteSuperseded");
    assertEquals(SUPERSEDED, result.newState());
  }

  @Test
  void test_WORKING_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_AT_VENUE_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_AT_VENUE_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_no_trans_PENDING_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_PENDING_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteAcknowledged");
  }

  @Test
  void test_no_trans_PENDING_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteRejected");
  }

  @Test
  void test_no_trans_PENDING_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_PENDING_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaced");
  }

  @Test
  void test_no_trans_PENDING_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_PENDING_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCancelRequested");
  }

  @Test
  void test_no_trans_PENDING_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCanceled");
  }

  @Test
  void test_no_trans_PENDING_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCancelRejected");
  }

  @Test
  void test_no_trans_PENDING_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_PENDING_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteFilled");
  }

  @Test
  void test_no_trans_PENDING_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteExpired");
  }

  @Test
  void test_no_trans_PENDING_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteSuperseded");
  }

  @Test
  void test_no_trans_PENDING_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteAnomaly");
  }

  @Test
  void test_no_trans_SENT_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteSent");
  }

  @Test
  void test_no_trans_SENT_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_SENT_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_SENT_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaced");
  }

  @Test
  void test_no_trans_SENT_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_SENT_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCancelRequested");
  }

  @Test
  void test_no_trans_SENT_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCanceled");
  }

  @Test
  void test_no_trans_SENT_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCancelRejected");
  }

  @Test
  void test_no_trans_SENT_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_SENT_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteFilled");
  }

  @Test
  void test_no_trans_SENT_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteExpired");
  }

  @Test
  void test_no_trans_SENT_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteSuperseded");
  }

  @Test
  void test_no_trans_SENT_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SENT, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteAnomaly");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaced");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCanceled");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCancelRejected");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteSuperseded");
  }

  @Test
  void test_no_trans_PENDING_NEW_AT_VENUE_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteAnomaly");
  }

  @Test
  void test_no_trans_WORKING_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteSent");
  }

  @Test
  void test_no_trans_WORKING_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_WORKING_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteAcknowledged");
  }

  @Test
  void test_no_trans_WORKING_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteRejected");
  }

  @Test
  void test_no_trans_WORKING_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_WORKING_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplaced");
  }

  @Test
  void test_no_trans_WORKING_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_WORKING_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteCanceled");
  }

  @Test
  void test_no_trans_WORKING_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(WORKING, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteCancelRejected");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteAcknowledged");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCanceled");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCancelRejected");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteAcknowledged");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaced");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_AT_VENUE_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteSuperseded");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteSent");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaced");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteCanceled");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteSuperseded");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(PARTIALLY_FILLED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteAnomaly");
  }

  @Test
  void test_no_trans_FILLED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteSent");
  }

  @Test
  void test_no_trans_FILLED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_FILLED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_FILLED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteRejected");
  }

  @Test
  void test_no_trans_FILLED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_FILLED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_FILLED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaced");
  }

  @Test
  void test_no_trans_FILLED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_FILLED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_FILLED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCanceled");
  }

  @Test
  void test_no_trans_FILLED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_FILLED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_FILLED_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteFilled");
  }

  @Test
  void test_no_trans_FILLED_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteExpired");
  }

  @Test
  void test_no_trans_FILLED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteSuperseded");
  }

  @Test
  void test_no_trans_FILLED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(FILLED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteAnomaly");
  }

  @Test
  void test_no_trans_CANCELED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteSent");
  }

  @Test
  void test_no_trans_CANCELED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_CANCELED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_CANCELED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteRejected");
  }

  @Test
  void test_no_trans_CANCELED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_CANCELED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_CANCELED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaced");
  }

  @Test
  void test_no_trans_CANCELED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_CANCELED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_CANCELED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCanceled");
  }

  @Test
  void test_no_trans_CANCELED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_CANCELED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_CANCELED_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteFilled");
  }

  @Test
  void test_no_trans_CANCELED_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteExpired");
  }

  @Test
  void test_no_trans_CANCELED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteSuperseded");
  }

  @Test
  void test_no_trans_CANCELED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(CANCELED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteAnomaly");
  }

  @Test
  void test_no_trans_REJECTED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteSent");
  }

  @Test
  void test_no_trans_REJECTED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_REJECTED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_REJECTED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteRejected");
  }

  @Test
  void test_no_trans_REJECTED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_REJECTED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_REJECTED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaced");
  }

  @Test
  void test_no_trans_REJECTED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_REJECTED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_REJECTED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCanceled");
  }

  @Test
  void test_no_trans_REJECTED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_REJECTED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_REJECTED_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteFilled");
  }

  @Test
  void test_no_trans_REJECTED_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteExpired");
  }

  @Test
  void test_no_trans_REJECTED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteSuperseded");
  }

  @Test
  void test_no_trans_REJECTED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(REJECTED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteAnomaly");
  }

  @Test
  void test_no_trans_EXPIRED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteSent");
  }

  @Test
  void test_no_trans_EXPIRED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_EXPIRED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_EXPIRED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteRejected");
  }

  @Test
  void test_no_trans_EXPIRED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_EXPIRED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_EXPIRED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaced");
  }

  @Test
  void test_no_trans_EXPIRED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_EXPIRED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_EXPIRED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCanceled");
  }

  @Test
  void test_no_trans_EXPIRED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_EXPIRED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_EXPIRED_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteFilled");
  }

  @Test
  void test_no_trans_EXPIRED_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteExpired");
  }

  @Test
  void test_no_trans_EXPIRED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteSuperseded");
  }

  @Test
  void test_no_trans_EXPIRED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(EXPIRED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteAnomaly");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteSent");
  }

  @Test
  void test_no_trans_SUPERSEDED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteRejected");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaced");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCanceled");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_SUPERSEDED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteFilled");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteExpired");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteSuperseded");
  }

  @Test
  void test_no_trans_SUPERSEDED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(SUPERSEDED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteAnomaly");
  }

  @Test
  void test_no_trans_ANOMALY_RouteSent() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteSent");
  }

  @Test
  void test_no_trans_ANOMALY_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_ANOMALY_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteAcknowledged");
  }

  @Test
  void test_no_trans_ANOMALY_RouteRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteRejected");
  }

  @Test
  void test_no_trans_ANOMALY_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_ANOMALY_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_ANOMALY_RouteReplaced() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaced");
  }

  @Test
  void test_no_trans_ANOMALY_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_ANOMALY_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCancelRequested");
  }

  @Test
  void test_no_trans_ANOMALY_RouteCanceled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCanceled");
  }

  @Test
  void test_no_trans_ANOMALY_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCancelRejected");
  }

  @Test
  void test_no_trans_ANOMALY_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_ANOMALY_RouteFilled() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteFilled");
  }

  @Test
  void test_no_trans_ANOMALY_RouteExpired() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteExpired");
  }

  @Test
  void test_no_trans_ANOMALY_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteSuperseded");
  }

  @Test
  void test_no_trans_ANOMALY_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = RouteFsmRunner.transition(ANOMALY, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteAnomaly");
  }

}