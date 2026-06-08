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
  void test_trans_0_STAGED_LegsValidated_to_READY() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegsValidated, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from STAGED on LegsValidated");
    assertEquals(READY, result.newState());
  }

  @Test
  void test_trans_1_STAGED_LegsValidationFailed_to_REJECTED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegsValidationFailed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from STAGED on LegsValidationFailed");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_trans_2_READY_FirstLegDispatched_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, FirstLegDispatched, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from READY on FirstLegDispatched");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_trans_3_READY_CancelRequested_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from READY on CancelRequested");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_trans_4_LEGS_WORKING_LegPartiallyFilled_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegPartiallyFilled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_trans_5_LEGS_WORKING_LegFilled_to_FILLED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_6_LEGS_WORKING_LegFilled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_7_LEGS_WORKING_LegFilled_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegFilled, ctx, createLegFilledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegFilled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_trans_8_LEGS_WORKING_LegRejected_to_REJECTED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_trans_9_LEGS_WORKING_LegRejected_to_REJECTED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_trans_10_LEGS_WORKING_LegRejected_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_11_LEGS_WORKING_LegRejected_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_trans_12_LEGS_WORKING_LegRejected_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegRejected, ctx, createLegRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegRejected");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_trans_13_LEGS_WORKING_LegCanceled_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_14_LEGS_WORKING_LegCanceled_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_trans_15_LEGS_WORKING_LegCanceled_to_LEGS_WORKING() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegCanceled, ctx, createLegCanceledPayload());
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on LegCanceled");
    assertEquals(LEGS_WORKING, result.newState());
  }

  @Test
  void test_trans_16_LEGS_WORKING_CancelRequested_to_CANCELED() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from LEGS_WORKING on CancelRequested");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_no_trans_0_2_STAGED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_0_3_STAGED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegFilled");
  }

  @Test
  void test_no_trans_0_4_STAGED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_0_5_STAGED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegRejected");
  }

  @Test
  void test_no_trans_0_6_STAGED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with LegCanceled");
  }

  @Test
  void test_no_trans_0_7_STAGED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(STAGED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for STAGED with CancelRequested");
  }

  @Test
  void test_no_trans_1_0_READY_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegsValidated");
  }

  @Test
  void test_no_trans_1_1_READY_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegsValidationFailed");
  }

  @Test
  void test_no_trans_1_3_READY_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegFilled");
  }

  @Test
  void test_no_trans_1_4_READY_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_1_5_READY_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegRejected");
  }

  @Test
  void test_no_trans_1_6_READY_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(READY, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for READY with LegCanceled");
  }

  @Test
  void test_no_trans_2_0_LEGS_WORKING_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with LegsValidated");
  }

  @Test
  void test_no_trans_2_1_LEGS_WORKING_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with LegsValidationFailed");
  }

  @Test
  void test_no_trans_2_2_LEGS_WORKING_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(LEGS_WORKING, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for LEGS_WORKING with FirstLegDispatched");
  }

  @Test
  void test_no_trans_3_0_FILLED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegsValidated");
  }

  @Test
  void test_no_trans_3_1_FILLED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_3_2_FILLED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_3_3_FILLED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegFilled");
  }

  @Test
  void test_no_trans_3_4_FILLED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_3_5_FILLED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegRejected");
  }

  @Test
  void test_no_trans_3_6_FILLED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with LegCanceled");
  }

  @Test
  void test_no_trans_3_7_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_4_0_PARTIALLY_FILLED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegsValidated");
  }

  @Test
  void test_no_trans_4_1_PARTIALLY_FILLED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_4_2_PARTIALLY_FILLED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_4_3_PARTIALLY_FILLED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegFilled");
  }

  @Test
  void test_no_trans_4_4_PARTIALLY_FILLED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_4_5_PARTIALLY_FILLED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegRejected");
  }

  @Test
  void test_no_trans_4_6_PARTIALLY_FILLED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with LegCanceled");
  }

  @Test
  void test_no_trans_4_7_PARTIALLY_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(PARTIALLY_FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_5_0_CANCELED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegsValidated");
  }

  @Test
  void test_no_trans_5_1_CANCELED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_5_2_CANCELED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_5_3_CANCELED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegFilled");
  }

  @Test
  void test_no_trans_5_4_CANCELED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_5_5_CANCELED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegRejected");
  }

  @Test
  void test_no_trans_5_6_CANCELED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with LegCanceled");
  }

  @Test
  void test_no_trans_5_7_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_6_0_REJECTED_LegsValidated() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegsValidated, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegsValidated");
  }

  @Test
  void test_no_trans_6_1_REJECTED_LegsValidationFailed() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegsValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegsValidationFailed");
  }

  @Test
  void test_no_trans_6_2_REJECTED_FirstLegDispatched() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, FirstLegDispatched, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with FirstLegDispatched");
  }

  @Test
  void test_no_trans_6_3_REJECTED_LegFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegFilled, ctx, createLegFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegFilled");
  }

  @Test
  void test_no_trans_6_4_REJECTED_LegPartiallyFilled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegPartiallyFilled, ctx, createLegPartiallyFilledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegPartiallyFilled");
  }

  @Test
  void test_no_trans_6_5_REJECTED_LegRejected() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegRejected, ctx, createLegRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegRejected");
  }

  @Test
  void test_no_trans_6_6_REJECTED_LegCanceled() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, LegCanceled, ctx, createLegCanceledPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with LegCanceled");
  }

  @Test
  void test_no_trans_6_7_REJECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = MultiLegFsmRunner.transition(REJECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRequested");
  }

}