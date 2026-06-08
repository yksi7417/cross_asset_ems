package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.MultiLegFsmState.*;
import static io.crossasset.ems.fsm.generated.MultiLegFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class MultiLegFsmGeneratedTest {

  private static MultiLegFsmContext minimalCtx() {
    return new MultiLegFsmContext("default", "default", "default", 0, 0, 0, 0, "default");
  }

  private static Object createLegFilledPayload() {
    return new MultiLegFsmPayloads.LegFilledPayload("default", 0L, 0L);
  }

  private static Object createLegPartiallyFilledPayload() {
    return new MultiLegFsmPayloads.LegPartiallyFilledPayload("default", 0L, 0L);
  }

  private static Object createLegRejectedPayload() {
    return new MultiLegFsmPayloads.LegRejectedPayload("default");
  }

  private static Object createLegCanceledPayload() {
    return new MultiLegFsmPayloads.LegCanceledPayload("default");
  }

  @Test
  void test_STAGED_LegsValidated_to_READY() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegsValidated, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from STAGED on LegsValidated");
    assertEquals(READY, result.newState());
  }

  @Test
  void test_STAGED_LegsValidationFailed_to_REJECTED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegsValidationFailed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from STAGED on LegsValidationFailed");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_READY_FirstLegDispatched_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, FirstLegDispatched, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from READY on FirstLegDispatched");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_READY_CancelRequested_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from READY on CancelRequested");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegPartiallyFilled_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegPartiallyFilled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegFilled_to_FILLED() {
    // guard: legs_filled + 1 == total_legs AND legs_rejected == 0 AND legs_canceled == 0
    var ctx = new MultiLegFsmContext("default", "default", "default", 1, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegFilled_to_PARTIALLY_FILLED() {
    // guard: LEGS_INDEPENDENT AND legs_filled+1+legs_rejected+legs_canceled == total_legs AND (legs_rejected>0 OR legs_canceled>0)
    var ctx = new MultiLegFsmContext("default", "default", "LEGS_INDEPENDENT", 2, 0, 1, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegFilled_to_LEGS_WORKING() {
    // guard: legs_filled + 1 + legs_rejected + legs_canceled < total_legs
    var ctx = new MultiLegFsmContext("default", "default", "default", 3, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegRejected_to_REJECTED() {
    // guard: execution_mode == 'ALL_OR_NONE'
    var ctx = new MultiLegFsmContext("default", "default", "ALL_OR_NONE", 0, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegRejected_to_PARTIALLY_FILLED() {
    // guard: LEGS_INDEPENDENT AND legs_filled > 0 AND legs_filled+legs_rejected+1+legs_canceled == total_legs
    var ctx = new MultiLegFsmContext("default", "default", "LEGS_INDEPENDENT", 2, 1, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegRejected_to_CANCELED() {
    // guard: LEGS_INDEPENDENT AND legs_filled == 0 AND legs_rejected+1+legs_canceled == total_legs
    var ctx = new MultiLegFsmContext("default", "default", "LEGS_INDEPENDENT", 1, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegRejected_to_LEGS_WORKING() {
    // guard: LEGS_INDEPENDENT AND legs_filled+legs_rejected+1+legs_canceled < total_legs
    var ctx = new MultiLegFsmContext("default", "default", "LEGS_INDEPENDENT", 3, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegCanceled_to_PARTIALLY_FILLED() {
    // guard: legs_filled > 0 AND legs_filled+legs_rejected+legs_canceled+1 == total_legs
    var ctx = new MultiLegFsmContext("default", "default", "default", 2, 1, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegCanceled_to_CANCELED() {
    // guard: legs_filled == 0 AND legs_rejected+legs_canceled+1 == total_legs
    var ctx = new MultiLegFsmContext("default", "default", "default", 1, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_LEGS_WORKING_LegCanceled_to_LEGS_WORKING() {
    // guard: legs_filled+legs_rejected+legs_canceled+1 < total_legs
    var ctx = new MultiLegFsmContext("default", "default", "default", 3, 0, 0, 0, null);
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_LEGS_WORKING_CancelRequested_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on CancelRequested");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_no_trans_STAGED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_STAGED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegFilled");
  }

  @Test
  void test_no_trans_STAGED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_STAGED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegRejected");
  }

  @Test
  void test_no_trans_STAGED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegCanceled");
  }

  @Test
  void test_no_trans_STAGED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with CancelRequested");
  }

  @Test
  void test_no_trans_READY_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegsValidated");
  }

  @Test
  void test_no_trans_READY_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegsValidationFailed");
  }

  @Test
  void test_no_trans_READY_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegFilled");
  }

  @Test
  void test_no_trans_READY_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_READY_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegRejected");
  }

  @Test
  void test_no_trans_READY_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegCanceled");
  }

  @Test
  void test_no_trans_LEGS_WORKING_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with LegsValidated");
  }

  @Test
  void test_no_trans_LEGS_WORKING_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with LegsValidationFailed");
  }

  @Test
  void test_no_trans_LEGS_WORKING_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with FirstLegDispatched");
  }

  @Test
  void test_no_trans_FILLED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegsValidated");
  }

  @Test
  void test_no_trans_FILLED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_FILLED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_FILLED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegFilled");
  }

  @Test
  void test_no_trans_FILLED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_FILLED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegRejected");
  }

  @Test
  void test_no_trans_FILLED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegCanceled");
  }

  @Test
  void test_no_trans_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegsValidated");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegFilled");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegCanceled");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_CANCELED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegsValidated");
  }

  @Test
  void test_no_trans_CANCELED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_CANCELED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_CANCELED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegFilled");
  }

  @Test
  void test_no_trans_CANCELED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_CANCELED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegRejected");
  }

  @Test
  void test_no_trans_CANCELED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegCanceled");
  }

  @Test
  void test_no_trans_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_REJECTED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegsValidated");
  }

  @Test
  void test_no_trans_REJECTED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_REJECTED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_REJECTED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegFilled");
  }

  @Test
  void test_no_trans_REJECTED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_REJECTED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegRejected");
  }

  @Test
  void test_no_trans_REJECTED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegCanceled");
  }

  @Test
  void test_no_trans_REJECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRequested");
  }

}