package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.SorFsmState.*;
import static io.crossasset.ems.fsm.generated.SorFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class SorFsmGeneratedTest {

  private static SorFsmContext minimalCtx() {
    return new SorFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, 0L, "default", "default", "default");
  }

  private static Object createRouteReplaceRequestedPayload() {
    return new SorFsmPayloads.RouteReplaceRequestedPayload("default", 0L, 0L);
  }

  private static Object createRouteReplacedPayload() {
    return new SorFsmPayloads.RouteReplacedPayload("default");
  }

  private static Object createRouteReplaceRejectedPayload() {
    return new SorFsmPayloads.RouteReplaceRejectedPayload(0);
  }

  private static Object createRouteCancelRejectedPayload() {
    return new SorFsmPayloads.RouteCancelRejectedPayload(0);
  }

  private static Object createRoutePartiallyFilledPayload() {
    return new SorFsmPayloads.RoutePartiallyFilledPayload(0L, 0L, "default");
  }

  private static Object createRouteFilledPayload() {
    return new SorFsmPayloads.RouteFilledPayload(0L, 0L, "default");
  }

  @Test
  void test_trans_0_PENDING_RouteSent_to_SENT() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteSent, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING on RouteSent");
    assertEquals(SENT, result.newState());
  }

  @Test
  void test_trans_1_SENT_SorStrategyDecided_to_SENT() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, SorStrategyDecided, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on SorStrategyDecided");
    assertEquals(SENT, result.newState());
  }

  @Test
  void test_trans_2_SENT_RoutePendingNewAtVenue_to_PENDING_NEW_AT_VENUE() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RoutePendingNewAtVenue, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RoutePendingNewAtVenue");
    assertEquals(PENDING_NEW_AT_VENUE, result.newState());
  }

  @Test
  void test_trans_3_PENDING_NEW_AT_VENUE_RouteAcknowledged_to_WORKING() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteAcknowledged, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW_AT_VENUE on RouteAcknowledged");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_4_SENT_RouteAcknowledged_to_WORKING() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteAcknowledged, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RouteAcknowledged");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_5_SENT_RouteRejected_to_REJECTED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteRejected, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from SENT on RouteRejected");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_trans_6_WORKING_SorPlanAdjusted_to_WORKING() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, SorPlanAdjusted, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on SorPlanAdjusted");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_7_WORKING_RouteReplaceRequested_to_PENDING_REPLACE_AT_VENUE() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteReplaceRequested");
    assertEquals(PENDING_REPLACE_AT_VENUE, result.newState());
  }

  @Test
  void test_trans_8_PENDING_REPLACE_AT_VENUE_RouteReplacePendingAtVenue_to_PENDING_REPLACE_AT_VENUE() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplacePendingAtVenue");
    assertEquals(PENDING_REPLACE_AT_VENUE, result.newState());
  }

  @Test
  void test_trans_9_PENDING_REPLACE_AT_VENUE_RouteReplaced_to_WORKING() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplaced");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_10_PENDING_REPLACE_AT_VENUE_RouteReplaceRejected_to_WORKING() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteReplaceRejected");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_11_WORKING_RouteCancelRequested_to_PENDING_CANCEL_AT_VENUE() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteCancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteCancelRequested");
    assertEquals(PENDING_CANCEL_AT_VENUE, result.newState());
  }

  @Test
  void test_trans_12_PARTIALLY_FILLED_RouteCancelRequested_to_PENDING_CANCEL_AT_VENUE() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteCancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteCancelRequested");
    assertEquals(PENDING_CANCEL_AT_VENUE, result.newState());
  }

  @Test
  void test_trans_13_PENDING_CANCEL_AT_VENUE_RouteCanceled_to_CANCELED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCanceled, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCanceled");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_trans_14_PENDING_CANCEL_AT_VENUE_RouteCancelRejected_to_WORKING() {
    var ctx = new SorFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, 0L, "default", "0", "default");
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCancelRejected");
    assertEquals(WORKING, result.newState());
  }

  @Test
  void test_trans_15_PENDING_CANCEL_AT_VENUE_RouteCancelRejected_to_PARTIALLY_FILLED() {
    var ctx = new SorFsmContext("default", "default", "default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, 0L, "default", "1", "default");
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteCancelRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_16_WORKING_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_17_PARTIALLY_FILLED_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_18_PENDING_REPLACE_AT_VENUE_RoutePartiallyFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RoutePartiallyFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_19_WORKING_RouteFilled_to_FILLED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteFilled, ctx, createRouteFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_20_PARTIALLY_FILLED_RouteFilled_to_FILLED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteFilled, ctx, createRouteFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_21_WORKING_RouteExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_trans_22_PARTIALLY_FILLED_RouteExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on RouteExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_trans_23_WORKING_RouteSuperseded_to_SUPERSEDED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteSuperseded, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteSuperseded");
    assertEquals(SUPERSEDED, result.newState());
  }

  @Test
  void test_trans_24_PENDING_REPLACE_AT_VENUE_RouteSuperseded_to_SUPERSEDED() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteSuperseded, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteSuperseded");
    assertEquals(SUPERSEDED, result.newState());
  }

  @Test
  void test_trans_25_WORKING_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from WORKING on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_trans_26_PENDING_REPLACE_AT_VENUE_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE_AT_VENUE on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_trans_27_PENDING_CANCEL_AT_VENUE_RouteAnomaly_to_ANOMALY() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteAnomaly, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL_AT_VENUE on RouteAnomaly");
    assertEquals(ANOMALY, result.newState());
  }

  @Test
  void test_no_trans_0_0_PENDING_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with SorStrategyDecided");
  }

  @Test
  void test_no_trans_0_1_PENDING_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_0_3_PENDING_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_0_4_PENDING_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteAcknowledged");
  }

  @Test
  void test_no_trans_0_5_PENDING_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteRejected");
  }

  @Test
  void test_no_trans_0_6_PENDING_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_0_7_PENDING_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_0_8_PENDING_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaced");
  }

  @Test
  void test_no_trans_0_9_PENDING_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_0_10_PENDING_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCancelRequested");
  }

  @Test
  void test_no_trans_0_11_PENDING_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCanceled");
  }

  @Test
  void test_no_trans_0_12_PENDING_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteCancelRejected");
  }

  @Test
  void test_no_trans_0_13_PENDING_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_0_14_PENDING_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteFilled");
  }

  @Test
  void test_no_trans_0_15_PENDING_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteExpired");
  }

  @Test
  void test_no_trans_0_16_PENDING_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteSuperseded");
  }

  @Test
  void test_no_trans_0_17_PENDING_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING with RouteAnomaly");
  }

  @Test
  void test_no_trans_1_1_SENT_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_1_2_SENT_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteSent");
  }

  @Test
  void test_no_trans_1_6_SENT_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_1_7_SENT_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_1_8_SENT_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaced");
  }

  @Test
  void test_no_trans_1_9_SENT_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_1_10_SENT_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCancelRequested");
  }

  @Test
  void test_no_trans_1_11_SENT_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCanceled");
  }

  @Test
  void test_no_trans_1_12_SENT_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteCancelRejected");
  }

  @Test
  void test_no_trans_1_13_SENT_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_1_14_SENT_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteFilled");
  }

  @Test
  void test_no_trans_1_15_SENT_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteExpired");
  }

  @Test
  void test_no_trans_1_16_SENT_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteSuperseded");
  }

  @Test
  void test_no_trans_1_17_SENT_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SENT, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SENT with RouteAnomaly");
  }

  @Test
  void test_no_trans_2_0_PENDING_NEW_AT_VENUE_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with SorStrategyDecided");
  }

  @Test
  void test_no_trans_2_1_PENDING_NEW_AT_VENUE_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_2_2_PENDING_NEW_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_2_3_PENDING_NEW_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_2_5_PENDING_NEW_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_2_6_PENDING_NEW_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_2_7_PENDING_NEW_AT_VENUE_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_2_8_PENDING_NEW_AT_VENUE_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaced");
  }

  @Test
  void test_no_trans_2_9_PENDING_NEW_AT_VENUE_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_2_10_PENDING_NEW_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_2_11_PENDING_NEW_AT_VENUE_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCanceled");
  }

  @Test
  void test_no_trans_2_12_PENDING_NEW_AT_VENUE_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteCancelRejected");
  }

  @Test
  void test_no_trans_2_13_PENDING_NEW_AT_VENUE_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_2_14_PENDING_NEW_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_2_15_PENDING_NEW_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_2_16_PENDING_NEW_AT_VENUE_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteSuperseded");
  }

  @Test
  void test_no_trans_2_17_PENDING_NEW_AT_VENUE_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_NEW_AT_VENUE, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW_AT_VENUE with RouteAnomaly");
  }

  @Test
  void test_no_trans_3_0_WORKING_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with SorStrategyDecided");
  }

  @Test
  void test_no_trans_3_2_WORKING_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteSent");
  }

  @Test
  void test_no_trans_3_3_WORKING_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_3_4_WORKING_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteAcknowledged");
  }

  @Test
  void test_no_trans_3_5_WORKING_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteRejected");
  }

  @Test
  void test_no_trans_3_7_WORKING_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_3_8_WORKING_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplaced");
  }

  @Test
  void test_no_trans_3_9_WORKING_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_3_11_WORKING_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteCanceled");
  }

  @Test
  void test_no_trans_3_12_WORKING_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(WORKING, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for WORKING with RouteCancelRejected");
  }

  @Test
  void test_no_trans_4_0_PENDING_REPLACE_AT_VENUE_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with SorStrategyDecided");
  }

  @Test
  void test_no_trans_4_1_PENDING_REPLACE_AT_VENUE_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_4_2_PENDING_REPLACE_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_4_3_PENDING_REPLACE_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_4_4_PENDING_REPLACE_AT_VENUE_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteAcknowledged");
  }

  @Test
  void test_no_trans_4_5_PENDING_REPLACE_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_4_6_PENDING_REPLACE_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_4_10_PENDING_REPLACE_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_4_11_PENDING_REPLACE_AT_VENUE_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCanceled");
  }

  @Test
  void test_no_trans_4_12_PENDING_REPLACE_AT_VENUE_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteCancelRejected");
  }

  @Test
  void test_no_trans_4_14_PENDING_REPLACE_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_4_15_PENDING_REPLACE_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_REPLACE_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_5_0_PENDING_CANCEL_AT_VENUE_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with SorStrategyDecided");
  }

  @Test
  void test_no_trans_5_1_PENDING_CANCEL_AT_VENUE_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_5_2_PENDING_CANCEL_AT_VENUE_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteSent");
  }

  @Test
  void test_no_trans_5_3_PENDING_CANCEL_AT_VENUE_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_5_4_PENDING_CANCEL_AT_VENUE_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteAcknowledged");
  }

  @Test
  void test_no_trans_5_5_PENDING_CANCEL_AT_VENUE_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteRejected");
  }

  @Test
  void test_no_trans_5_6_PENDING_CANCEL_AT_VENUE_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_5_7_PENDING_CANCEL_AT_VENUE_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_5_8_PENDING_CANCEL_AT_VENUE_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaced");
  }

  @Test
  void test_no_trans_5_9_PENDING_CANCEL_AT_VENUE_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_5_10_PENDING_CANCEL_AT_VENUE_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteCancelRequested");
  }

  @Test
  void test_no_trans_5_13_PENDING_CANCEL_AT_VENUE_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_5_14_PENDING_CANCEL_AT_VENUE_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteFilled");
  }

  @Test
  void test_no_trans_5_15_PENDING_CANCEL_AT_VENUE_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteExpired");
  }

  @Test
  void test_no_trans_5_16_PENDING_CANCEL_AT_VENUE_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PENDING_CANCEL_AT_VENUE, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL_AT_VENUE with RouteSuperseded");
  }

  @Test
  void test_no_trans_6_0_PARTIALLY_FILLED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_6_1_PARTIALLY_FILLED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_6_2_PARTIALLY_FILLED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteSent");
  }

  @Test
  void test_no_trans_6_3_PARTIALLY_FILLED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_6_4_PARTIALLY_FILLED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_6_5_PARTIALLY_FILLED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteRejected");
  }

  @Test
  void test_no_trans_6_6_PARTIALLY_FILLED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_6_7_PARTIALLY_FILLED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_6_8_PARTIALLY_FILLED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaced");
  }

  @Test
  void test_no_trans_6_9_PARTIALLY_FILLED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_6_11_PARTIALLY_FILLED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteCanceled");
  }

  @Test
  void test_no_trans_6_12_PARTIALLY_FILLED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_6_16_PARTIALLY_FILLED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteSuperseded");
  }

  @Test
  void test_no_trans_6_17_PARTIALLY_FILLED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(PARTIALLY_FILLED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with RouteAnomaly");
  }

  @Test
  void test_no_trans_7_0_FILLED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_7_1_FILLED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_7_2_FILLED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteSent");
  }

  @Test
  void test_no_trans_7_3_FILLED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_7_4_FILLED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_7_5_FILLED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteRejected");
  }

  @Test
  void test_no_trans_7_6_FILLED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_7_7_FILLED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_7_8_FILLED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaced");
  }

  @Test
  void test_no_trans_7_9_FILLED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_7_10_FILLED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_7_11_FILLED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCanceled");
  }

  @Test
  void test_no_trans_7_12_FILLED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_7_13_FILLED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_7_14_FILLED_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteFilled");
  }

  @Test
  void test_no_trans_7_15_FILLED_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteExpired");
  }

  @Test
  void test_no_trans_7_16_FILLED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteSuperseded");
  }

  @Test
  void test_no_trans_7_17_FILLED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(FILLED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with RouteAnomaly");
  }

  @Test
  void test_no_trans_8_0_CANCELED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_8_1_CANCELED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_8_2_CANCELED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteSent");
  }

  @Test
  void test_no_trans_8_3_CANCELED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_8_4_CANCELED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_8_5_CANCELED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteRejected");
  }

  @Test
  void test_no_trans_8_6_CANCELED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_8_7_CANCELED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_8_8_CANCELED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaced");
  }

  @Test
  void test_no_trans_8_9_CANCELED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_8_10_CANCELED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_8_11_CANCELED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCanceled");
  }

  @Test
  void test_no_trans_8_12_CANCELED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_8_13_CANCELED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_8_14_CANCELED_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteFilled");
  }

  @Test
  void test_no_trans_8_15_CANCELED_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteExpired");
  }

  @Test
  void test_no_trans_8_16_CANCELED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteSuperseded");
  }

  @Test
  void test_no_trans_8_17_CANCELED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(CANCELED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with RouteAnomaly");
  }

  @Test
  void test_no_trans_9_0_REJECTED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_9_1_REJECTED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_9_2_REJECTED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteSent");
  }

  @Test
  void test_no_trans_9_3_REJECTED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_9_4_REJECTED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_9_5_REJECTED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteRejected");
  }

  @Test
  void test_no_trans_9_6_REJECTED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_9_7_REJECTED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_9_8_REJECTED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaced");
  }

  @Test
  void test_no_trans_9_9_REJECTED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_9_10_REJECTED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_9_11_REJECTED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCanceled");
  }

  @Test
  void test_no_trans_9_12_REJECTED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_9_13_REJECTED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_9_14_REJECTED_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteFilled");
  }

  @Test
  void test_no_trans_9_15_REJECTED_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteExpired");
  }

  @Test
  void test_no_trans_9_16_REJECTED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteSuperseded");
  }

  @Test
  void test_no_trans_9_17_REJECTED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(REJECTED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with RouteAnomaly");
  }

  @Test
  void test_no_trans_10_0_EXPIRED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_10_1_EXPIRED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_10_2_EXPIRED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteSent");
  }

  @Test
  void test_no_trans_10_3_EXPIRED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_10_4_EXPIRED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_10_5_EXPIRED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteRejected");
  }

  @Test
  void test_no_trans_10_6_EXPIRED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_10_7_EXPIRED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_10_8_EXPIRED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaced");
  }

  @Test
  void test_no_trans_10_9_EXPIRED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_10_10_EXPIRED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_10_11_EXPIRED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCanceled");
  }

  @Test
  void test_no_trans_10_12_EXPIRED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_10_13_EXPIRED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_10_14_EXPIRED_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteFilled");
  }

  @Test
  void test_no_trans_10_15_EXPIRED_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteExpired");
  }

  @Test
  void test_no_trans_10_16_EXPIRED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteSuperseded");
  }

  @Test
  void test_no_trans_10_17_EXPIRED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(EXPIRED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with RouteAnomaly");
  }

  @Test
  void test_no_trans_11_0_SUPERSEDED_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with SorStrategyDecided");
  }

  @Test
  void test_no_trans_11_1_SUPERSEDED_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_11_2_SUPERSEDED_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteSent");
  }

  @Test
  void test_no_trans_11_3_SUPERSEDED_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_11_4_SUPERSEDED_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteAcknowledged");
  }

  @Test
  void test_no_trans_11_5_SUPERSEDED_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteRejected");
  }

  @Test
  void test_no_trans_11_6_SUPERSEDED_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_11_7_SUPERSEDED_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_11_8_SUPERSEDED_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaced");
  }

  @Test
  void test_no_trans_11_9_SUPERSEDED_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_11_10_SUPERSEDED_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCancelRequested");
  }

  @Test
  void test_no_trans_11_11_SUPERSEDED_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCanceled");
  }

  @Test
  void test_no_trans_11_12_SUPERSEDED_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteCancelRejected");
  }

  @Test
  void test_no_trans_11_13_SUPERSEDED_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_11_14_SUPERSEDED_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteFilled");
  }

  @Test
  void test_no_trans_11_15_SUPERSEDED_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteExpired");
  }

  @Test
  void test_no_trans_11_16_SUPERSEDED_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteSuperseded");
  }

  @Test
  void test_no_trans_11_17_SUPERSEDED_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(SUPERSEDED, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for SUPERSEDED with RouteAnomaly");
  }

  @Test
  void test_no_trans_12_0_ANOMALY_SorStrategyDecided() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, SorStrategyDecided, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with SorStrategyDecided");
  }

  @Test
  void test_no_trans_12_1_ANOMALY_SorPlanAdjusted() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, SorPlanAdjusted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with SorPlanAdjusted");
  }

  @Test
  void test_no_trans_12_2_ANOMALY_RouteSent() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteSent, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteSent");
  }

  @Test
  void test_no_trans_12_3_ANOMALY_RoutePendingNewAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RoutePendingNewAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RoutePendingNewAtVenue");
  }

  @Test
  void test_no_trans_12_4_ANOMALY_RouteAcknowledged() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteAcknowledged, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteAcknowledged");
  }

  @Test
  void test_no_trans_12_5_ANOMALY_RouteRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteRejected, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteRejected");
  }

  @Test
  void test_no_trans_12_6_ANOMALY_RouteReplaceRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteReplaceRequested, ctx, createRouteReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaceRequested");
  }

  @Test
  void test_no_trans_12_7_ANOMALY_RouteReplacePendingAtVenue() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteReplacePendingAtVenue, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplacePendingAtVenue");
  }

  @Test
  void test_no_trans_12_8_ANOMALY_RouteReplaced() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteReplaced, ctx, createRouteReplacedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaced");
  }

  @Test
  void test_no_trans_12_9_ANOMALY_RouteReplaceRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteReplaceRejected, ctx, createRouteReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteReplaceRejected");
  }

  @Test
  void test_no_trans_12_10_ANOMALY_RouteCancelRequested() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteCancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCancelRequested");
  }

  @Test
  void test_no_trans_12_11_ANOMALY_RouteCanceled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteCanceled, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCanceled");
  }

  @Test
  void test_no_trans_12_12_ANOMALY_RouteCancelRejected() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteCancelRejected, ctx, createRouteCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteCancelRejected");
  }

  @Test
  void test_no_trans_12_13_ANOMALY_RoutePartiallyFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RoutePartiallyFilled, ctx, createRoutePartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RoutePartiallyFilled");
  }

  @Test
  void test_no_trans_12_14_ANOMALY_RouteFilled() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteFilled, ctx, createRouteFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteFilled");
  }

  @Test
  void test_no_trans_12_15_ANOMALY_RouteExpired() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteExpired");
  }

  @Test
  void test_no_trans_12_16_ANOMALY_RouteSuperseded() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteSuperseded, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteSuperseded");
  }

  @Test
  void test_no_trans_12_17_ANOMALY_RouteAnomaly() {
    var ctx = minimalCtx();
    var result = SorFsmRunner.transition(ANOMALY, RouteAnomaly, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for ANOMALY with RouteAnomaly");
  }

}