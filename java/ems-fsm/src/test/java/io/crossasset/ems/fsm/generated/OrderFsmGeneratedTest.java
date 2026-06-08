package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.OrderFsmState.*;
import static io.crossasset.ems.fsm.generated.OrderFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class OrderFsmGeneratedTest {

  private static OrderFsmContext minimalCtx() {
    return new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "default", "default");
  }

  private static Object createReplaceRequestedPayload() {
    return new OrderFsmPayloads.ReplaceRequestedPayload("default", 0L, 0L);
  }

  private static Object createReplaceAcceptedPayload() {
    return new OrderFsmPayloads.ReplaceAcceptedPayload("default");
  }

  private static Object createReplaceRejectedPayload() {
    return new OrderFsmPayloads.ReplaceRejectedPayload(0);
  }

  private static Object createCancelRejectedPayload() {
    return new OrderFsmPayloads.CancelRejectedPayload(0);
  }

  private static Object createPartialFillPayload() {
    return new OrderFsmPayloads.PartialFillPayload(0L, 0L, "default");
  }

  private static Object createFullFillPayload() {
    return new OrderFsmPayloads.FullFillPayload(0L, 0L, "default");
  }

  private static Object createTradeCorrectPayload() {
    return new OrderFsmPayloads.TradeCorrectPayload(0L, 0L, "default");
  }

  private static Object createTradeCancelBustPayload() {
    return new OrderFsmPayloads.TradeCancelBustPayload("default");
  }

  @Test
  void test_trans_0_PENDING_NEW_ValidationPassed_to_NEW() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ValidationPassed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW on ValidationPassed");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_trans_1_PENDING_NEW_ValidationFailed_to_REJECTED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ValidationFailed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW on ValidationFailed");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_trans_2_NEW_ReplaceRequested_to_PENDING_REPLACE() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on ReplaceRequested");
    assertEquals(PENDING_REPLACE, result.newState());
  }

  @Test
  void test_trans_3_REPLACED_ReplaceRequested_to_PENDING_REPLACE() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on ReplaceRequested");
    assertEquals(PENDING_REPLACE, result.newState());
  }

  @Test
  void test_trans_4_PENDING_REPLACE_ReplaceAccepted_to_REPLACED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceAccepted");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_trans_5_PENDING_REPLACE_ReplaceRejected_to_NEW() {
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "default", "0");
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceRejected");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_trans_6_PENDING_REPLACE_ReplaceRejected_to_REPLACED() {
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "default", "5");
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceRejected");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_trans_7_NEW_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_trans_8_REPLACED_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_trans_9_PARTIALLY_FILLED_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_trans_10_PENDING_CANCEL_CancelAccepted_to_CANCELED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelAccepted, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelAccepted");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_trans_11_PENDING_CANCEL_CancelRejected_to_NEW() {
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "0", "default");
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_trans_12_PENDING_CANCEL_CancelRejected_to_REPLACED() {
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "5", "default");
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_trans_13_PENDING_CANCEL_CancelRejected_to_PARTIALLY_FILLED() {
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, "default", "default", 0L, "1", "default");
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_14_NEW_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_15_REPLACED_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_16_PENDING_REPLACE_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_17_PARTIALLY_FILLED_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_trans_18_NEW_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_19_REPLACED_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_20_PARTIALLY_FILLED_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_trans_21_FILLED_TradeCorrect_to_TRADE_CORRECTED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertFalse(result.isNoTransition(), "Expected transition from FILLED on TradeCorrect");
    assertEquals(TRADE_CORRECTED, result.newState());
  }

  @Test
  void test_trans_22_FILLED_TradeCancelBust_to_TRADE_CANCELED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertFalse(result.isNoTransition(), "Expected transition from FILLED on TradeCancelBust");
    assertEquals(TRADE_CANCELED, result.newState());
  }

  @Test
  void test_trans_23_NEW_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_trans_24_REPLACED_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_trans_25_PARTIALLY_FILLED_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_trans_26_NEW_DoneForDay_to_DONE_FOR_DAY() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, DoneForDay, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on DoneForDay");
    assertEquals(DONE_FOR_DAY, result.newState());
  }

  @Test
  void test_trans_27_PARTIALLY_FILLED_DoneForDay_to_DONE_FOR_DAY() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, DoneForDay, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on DoneForDay");
    assertEquals(DONE_FOR_DAY, result.newState());
  }

  @Test
  void test_no_trans_0_2_PENDING_NEW_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceRequested");
  }

  @Test
  void test_no_trans_0_3_PENDING_NEW_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceAccepted");
  }

  @Test
  void test_no_trans_0_4_PENDING_NEW_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceRejected");
  }

  @Test
  void test_no_trans_0_5_PENDING_NEW_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelRequested");
  }

  @Test
  void test_no_trans_0_6_PENDING_NEW_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelAccepted");
  }

  @Test
  void test_no_trans_0_7_PENDING_NEW_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelRejected");
  }

  @Test
  void test_no_trans_0_8_PENDING_NEW_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with PartialFill");
  }

  @Test
  void test_no_trans_0_9_PENDING_NEW_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with FullFill");
  }

  @Test
  void test_no_trans_0_10_PENDING_NEW_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with TradeCorrect");
  }

  @Test
  void test_no_trans_0_11_PENDING_NEW_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with TradeCancelBust");
  }

  @Test
  void test_no_trans_0_12_PENDING_NEW_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with OrderExpired");
  }

  @Test
  void test_no_trans_0_13_PENDING_NEW_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with DoneForDay");
  }

  @Test
  void test_no_trans_1_0_NEW_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ValidationPassed");
  }

  @Test
  void test_no_trans_1_1_NEW_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ValidationFailed");
  }

  @Test
  void test_no_trans_1_3_NEW_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ReplaceAccepted");
  }

  @Test
  void test_no_trans_1_4_NEW_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ReplaceRejected");
  }

  @Test
  void test_no_trans_1_6_NEW_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with CancelAccepted");
  }

  @Test
  void test_no_trans_1_7_NEW_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with CancelRejected");
  }

  @Test
  void test_no_trans_1_10_NEW_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with TradeCorrect");
  }

  @Test
  void test_no_trans_1_11_NEW_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with TradeCancelBust");
  }

  @Test
  void test_no_trans_2_0_PENDING_REPLACE_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ValidationPassed");
  }

  @Test
  void test_no_trans_2_1_PENDING_REPLACE_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ValidationFailed");
  }

  @Test
  void test_no_trans_2_2_PENDING_REPLACE_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ReplaceRequested");
  }

  @Test
  void test_no_trans_2_5_PENDING_REPLACE_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelRequested");
  }

  @Test
  void test_no_trans_2_6_PENDING_REPLACE_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelAccepted");
  }

  @Test
  void test_no_trans_2_7_PENDING_REPLACE_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelRejected");
  }

  @Test
  void test_no_trans_2_9_PENDING_REPLACE_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with FullFill");
  }

  @Test
  void test_no_trans_2_10_PENDING_REPLACE_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with TradeCorrect");
  }

  @Test
  void test_no_trans_2_11_PENDING_REPLACE_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with TradeCancelBust");
  }

  @Test
  void test_no_trans_2_12_PENDING_REPLACE_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with OrderExpired");
  }

  @Test
  void test_no_trans_2_13_PENDING_REPLACE_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with DoneForDay");
  }

  @Test
  void test_no_trans_3_0_REPLACED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ValidationPassed");
  }

  @Test
  void test_no_trans_3_1_REPLACED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ValidationFailed");
  }

  @Test
  void test_no_trans_3_3_REPLACED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_3_4_REPLACED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ReplaceRejected");
  }

  @Test
  void test_no_trans_3_6_REPLACED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with CancelAccepted");
  }

  @Test
  void test_no_trans_3_7_REPLACED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with CancelRejected");
  }

  @Test
  void test_no_trans_3_10_REPLACED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with TradeCorrect");
  }

  @Test
  void test_no_trans_3_11_REPLACED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with TradeCancelBust");
  }

  @Test
  void test_no_trans_3_13_REPLACED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with DoneForDay");
  }

  @Test
  void test_no_trans_4_0_PENDING_CANCEL_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ValidationPassed");
  }

  @Test
  void test_no_trans_4_1_PENDING_CANCEL_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ValidationFailed");
  }

  @Test
  void test_no_trans_4_2_PENDING_CANCEL_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceRequested");
  }

  @Test
  void test_no_trans_4_3_PENDING_CANCEL_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceAccepted");
  }

  @Test
  void test_no_trans_4_4_PENDING_CANCEL_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceRejected");
  }

  @Test
  void test_no_trans_4_5_PENDING_CANCEL_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with CancelRequested");
  }

  @Test
  void test_no_trans_4_8_PENDING_CANCEL_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with PartialFill");
  }

  @Test
  void test_no_trans_4_9_PENDING_CANCEL_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with FullFill");
  }

  @Test
  void test_no_trans_4_10_PENDING_CANCEL_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with TradeCorrect");
  }

  @Test
  void test_no_trans_4_11_PENDING_CANCEL_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with TradeCancelBust");
  }

  @Test
  void test_no_trans_4_12_PENDING_CANCEL_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with OrderExpired");
  }

  @Test
  void test_no_trans_4_13_PENDING_CANCEL_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with DoneForDay");
  }

  @Test
  void test_no_trans_5_0_PARTIALLY_FILLED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ValidationPassed");
  }

  @Test
  void test_no_trans_5_1_PARTIALLY_FILLED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ValidationFailed");
  }

  @Test
  void test_no_trans_5_2_PARTIALLY_FILLED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceRequested");
  }

  @Test
  void test_no_trans_5_3_PARTIALLY_FILLED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_5_4_PARTIALLY_FILLED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceRejected");
  }

  @Test
  void test_no_trans_5_6_PARTIALLY_FILLED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelAccepted");
  }

  @Test
  void test_no_trans_5_7_PARTIALLY_FILLED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelRejected");
  }

  @Test
  void test_no_trans_5_10_PARTIALLY_FILLED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with TradeCorrect");
  }

  @Test
  void test_no_trans_5_11_PARTIALLY_FILLED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with TradeCancelBust");
  }

  @Test
  void test_no_trans_6_0_FILLED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ValidationPassed");
  }

  @Test
  void test_no_trans_6_1_FILLED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ValidationFailed");
  }

  @Test
  void test_no_trans_6_2_FILLED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceRequested");
  }

  @Test
  void test_no_trans_6_3_FILLED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_6_4_FILLED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceRejected");
  }

  @Test
  void test_no_trans_6_5_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_6_6_FILLED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelAccepted");
  }

  @Test
  void test_no_trans_6_7_FILLED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRejected");
  }

  @Test
  void test_no_trans_6_8_FILLED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with PartialFill");
  }

  @Test
  void test_no_trans_6_9_FILLED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with FullFill");
  }

  @Test
  void test_no_trans_6_12_FILLED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with OrderExpired");
  }

  @Test
  void test_no_trans_6_13_FILLED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with DoneForDay");
  }

  @Test
  void test_no_trans_7_0_CANCELED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ValidationPassed");
  }

  @Test
  void test_no_trans_7_1_CANCELED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ValidationFailed");
  }

  @Test
  void test_no_trans_7_2_CANCELED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceRequested");
  }

  @Test
  void test_no_trans_7_3_CANCELED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_7_4_CANCELED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceRejected");
  }

  @Test
  void test_no_trans_7_5_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_7_6_CANCELED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelAccepted");
  }

  @Test
  void test_no_trans_7_7_CANCELED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRejected");
  }

  @Test
  void test_no_trans_7_8_CANCELED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with PartialFill");
  }

  @Test
  void test_no_trans_7_9_CANCELED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with FullFill");
  }

  @Test
  void test_no_trans_7_10_CANCELED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with TradeCorrect");
  }

  @Test
  void test_no_trans_7_11_CANCELED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with TradeCancelBust");
  }

  @Test
  void test_no_trans_7_12_CANCELED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with OrderExpired");
  }

  @Test
  void test_no_trans_7_13_CANCELED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with DoneForDay");
  }

  @Test
  void test_no_trans_8_0_REJECTED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ValidationPassed");
  }

  @Test
  void test_no_trans_8_1_REJECTED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ValidationFailed");
  }

  @Test
  void test_no_trans_8_2_REJECTED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceRequested");
  }

  @Test
  void test_no_trans_8_3_REJECTED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_8_4_REJECTED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceRejected");
  }

  @Test
  void test_no_trans_8_5_REJECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRequested");
  }

  @Test
  void test_no_trans_8_6_REJECTED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelAccepted");
  }

  @Test
  void test_no_trans_8_7_REJECTED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRejected");
  }

  @Test
  void test_no_trans_8_8_REJECTED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with PartialFill");
  }

  @Test
  void test_no_trans_8_9_REJECTED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with FullFill");
  }

  @Test
  void test_no_trans_8_10_REJECTED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with TradeCorrect");
  }

  @Test
  void test_no_trans_8_11_REJECTED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with TradeCancelBust");
  }

  @Test
  void test_no_trans_8_12_REJECTED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with OrderExpired");
  }

  @Test
  void test_no_trans_8_13_REJECTED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with DoneForDay");
  }

  @Test
  void test_no_trans_9_0_EXPIRED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ValidationPassed");
  }

  @Test
  void test_no_trans_9_1_EXPIRED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ValidationFailed");
  }

  @Test
  void test_no_trans_9_2_EXPIRED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceRequested");
  }

  @Test
  void test_no_trans_9_3_EXPIRED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_9_4_EXPIRED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceRejected");
  }

  @Test
  void test_no_trans_9_5_EXPIRED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelRequested");
  }

  @Test
  void test_no_trans_9_6_EXPIRED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelAccepted");
  }

  @Test
  void test_no_trans_9_7_EXPIRED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelRejected");
  }

  @Test
  void test_no_trans_9_8_EXPIRED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with PartialFill");
  }

  @Test
  void test_no_trans_9_9_EXPIRED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with FullFill");
  }

  @Test
  void test_no_trans_9_10_EXPIRED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with TradeCorrect");
  }

  @Test
  void test_no_trans_9_11_EXPIRED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with TradeCancelBust");
  }

  @Test
  void test_no_trans_9_12_EXPIRED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with OrderExpired");
  }

  @Test
  void test_no_trans_9_13_EXPIRED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with DoneForDay");
  }

  @Test
  void test_no_trans_10_0_DONE_FOR_DAY_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ValidationPassed");
  }

  @Test
  void test_no_trans_10_1_DONE_FOR_DAY_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ValidationFailed");
  }

  @Test
  void test_no_trans_10_2_DONE_FOR_DAY_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceRequested");
  }

  @Test
  void test_no_trans_10_3_DONE_FOR_DAY_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceAccepted");
  }

  @Test
  void test_no_trans_10_4_DONE_FOR_DAY_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceRejected");
  }

  @Test
  void test_no_trans_10_5_DONE_FOR_DAY_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelRequested");
  }

  @Test
  void test_no_trans_10_6_DONE_FOR_DAY_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelAccepted");
  }

  @Test
  void test_no_trans_10_7_DONE_FOR_DAY_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelRejected");
  }

  @Test
  void test_no_trans_10_8_DONE_FOR_DAY_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with PartialFill");
  }

  @Test
  void test_no_trans_10_9_DONE_FOR_DAY_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with FullFill");
  }

  @Test
  void test_no_trans_10_10_DONE_FOR_DAY_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with TradeCorrect");
  }

  @Test
  void test_no_trans_10_11_DONE_FOR_DAY_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with TradeCancelBust");
  }

  @Test
  void test_no_trans_10_12_DONE_FOR_DAY_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with OrderExpired");
  }

  @Test
  void test_no_trans_10_13_DONE_FOR_DAY_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with DoneForDay");
  }

  @Test
  void test_no_trans_11_0_TRADE_CORRECTED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ValidationPassed");
  }

  @Test
  void test_no_trans_11_1_TRADE_CORRECTED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ValidationFailed");
  }

  @Test
  void test_no_trans_11_2_TRADE_CORRECTED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceRequested");
  }

  @Test
  void test_no_trans_11_3_TRADE_CORRECTED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_11_4_TRADE_CORRECTED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceRejected");
  }

  @Test
  void test_no_trans_11_5_TRADE_CORRECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelRequested");
  }

  @Test
  void test_no_trans_11_6_TRADE_CORRECTED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelAccepted");
  }

  @Test
  void test_no_trans_11_7_TRADE_CORRECTED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelRejected");
  }

  @Test
  void test_no_trans_11_8_TRADE_CORRECTED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with PartialFill");
  }

  @Test
  void test_no_trans_11_9_TRADE_CORRECTED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with FullFill");
  }

  @Test
  void test_no_trans_11_10_TRADE_CORRECTED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with TradeCorrect");
  }

  @Test
  void test_no_trans_11_11_TRADE_CORRECTED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with TradeCancelBust");
  }

  @Test
  void test_no_trans_11_12_TRADE_CORRECTED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with OrderExpired");
  }

  @Test
  void test_no_trans_11_13_TRADE_CORRECTED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with DoneForDay");
  }

  @Test
  void test_no_trans_12_0_TRADE_CANCELED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ValidationPassed");
  }

  @Test
  void test_no_trans_12_1_TRADE_CANCELED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ValidationFailed");
  }

  @Test
  void test_no_trans_12_2_TRADE_CANCELED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceRequested");
  }

  @Test
  void test_no_trans_12_3_TRADE_CANCELED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_12_4_TRADE_CANCELED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceRejected");
  }

  @Test
  void test_no_trans_12_5_TRADE_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_12_6_TRADE_CANCELED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelAccepted");
  }

  @Test
  void test_no_trans_12_7_TRADE_CANCELED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelRejected");
  }

  @Test
  void test_no_trans_12_8_TRADE_CANCELED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with PartialFill");
  }

  @Test
  void test_no_trans_12_9_TRADE_CANCELED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with FullFill");
  }

  @Test
  void test_no_trans_12_10_TRADE_CANCELED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with TradeCorrect");
  }

  @Test
  void test_no_trans_12_11_TRADE_CANCELED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with TradeCancelBust");
  }

  @Test
  void test_no_trans_12_12_TRADE_CANCELED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with OrderExpired");
  }

  @Test
  void test_no_trans_12_13_TRADE_CANCELED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with DoneForDay");
  }

}