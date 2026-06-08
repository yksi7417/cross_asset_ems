package io.crossasset.ems.fsm.generated;

import static io.crossasset.ems.fsm.generated.OrderFsmState.*;
import static io.crossasset.ems.fsm.generated.OrderFsmEvent.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class OrderFsmGeneratedTest {

  private static OrderFsmContext minimalCtx() {
    return new OrderFsmContext("default", "default", "default", "default", 0, 0L, 0L, 0L, 0L, "default", 0, 0L, "default", "default", "default");
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
  void test_PENDING_NEW_ValidationPassed_to_NEW() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ValidationPassed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW on ValidationPassed");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_PENDING_NEW_ValidationFailed_to_REJECTED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ValidationFailed, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_NEW on ValidationFailed");
    assertEquals(REJECTED, result.newState());
  }

  @Test
  void test_NEW_ReplaceRequested_to_PENDING_REPLACE() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on ReplaceRequested");
    assertEquals(PENDING_REPLACE, result.newState());
  }

  @Test
  void test_REPLACED_ReplaceRequested_to_PENDING_REPLACE() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on ReplaceRequested");
    assertEquals(PENDING_REPLACE, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_ReplaceAccepted_to_REPLACED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceAccepted");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_ReplaceRejected_to_NEW() {
    // guard: pre_replace_status == '0'
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, null, 0L, 0L, "default", 0, 0L, "default", null, "0");
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceRejected");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_ReplaceRejected_to_REPLACED() {
    // guard: pre_replace_status == '5'
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, null, 0L, 0L, "default", 0, 0L, "default", null, "5");
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on ReplaceRejected");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_NEW_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_REPLACED_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_CancelRequested_to_PENDING_CANCEL() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelRequested, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on CancelRequested");
    assertEquals(PENDING_CANCEL, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_CancelAccepted_to_CANCELED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelAccepted, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelAccepted");
    assertEquals(CANCELED, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_CancelRejected_to_NEW() {
    // guard: pre_cancel_status == '0'
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, null, 0L, 0L, "default", 0, 0L, "default", "0", null);
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(NEW, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_CancelRejected_to_REPLACED() {
    // guard: pre_cancel_status == '5'
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, null, 0L, 0L, "default", 0, 0L, "default", "5", null);
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void test_PENDING_CANCEL_CancelRejected_to_PARTIALLY_FILLED() {
    // guard: pre_cancel_status == '1'
    var ctx = new OrderFsmContext("default", "default", "default", "default", 0, 0L, null, 0L, 0L, "default", 0, 0L, "default", "1", null);
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRejected, ctx, createCancelRejectedPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_CANCEL on CancelRejected");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_NEW_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_REPLACED_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_PENDING_REPLACE_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PENDING_REPLACE on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_PartialFill_to_PARTIALLY_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, PartialFill, ctx, createPartialFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on PartialFill");
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  @Test
  void test_NEW_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from NEW on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_REPLACED_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_FullFill_to_FILLED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, FullFill, ctx, createFullFillPayload());
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on FullFill");
    assertEquals(FILLED, result.newState());
  }

  @Test
  void test_FILLED_TradeCorrect_to_TRADE_CORRECTED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertFalse(result.isNoTransition(), "Expected transition from FILLED on TradeCorrect");
    assertEquals(TRADE_CORRECTED, result.newState());
  }

  @Test
  void test_FILLED_TradeCancelBust_to_TRADE_CANCELED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertFalse(result.isNoTransition(), "Expected transition from FILLED on TradeCancelBust");
    assertEquals(TRADE_CANCELED, result.newState());
  }

  @Test
  void test_NEW_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_REPLACED_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from REPLACED on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_OrderExpired_to_EXPIRED() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, OrderExpired, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on OrderExpired");
    assertEquals(EXPIRED, result.newState());
  }

  @Test
  void test_NEW_DoneForDay_to_DONE_FOR_DAY() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, DoneForDay, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from NEW on DoneForDay");
    assertEquals(DONE_FOR_DAY, result.newState());
  }

  @Test
  void test_PARTIALLY_FILLED_DoneForDay_to_DONE_FOR_DAY() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, DoneForDay, ctx, null);
    assertFalse(result.isNoTransition(), "Expected transition from PARTIALLY_FILLED on DoneForDay");
    assertEquals(DONE_FOR_DAY, result.newState());
  }

  @Test
  void test_no_trans_PENDING_NEW_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_NEW_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceAccepted");
  }

  @Test
  void test_no_trans_PENDING_NEW_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with ReplaceRejected");
  }

  @Test
  void test_no_trans_PENDING_NEW_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelRequested");
  }

  @Test
  void test_no_trans_PENDING_NEW_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelAccepted");
  }

  @Test
  void test_no_trans_PENDING_NEW_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with CancelRejected");
  }

  @Test
  void test_no_trans_PENDING_NEW_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with PartialFill");
  }

  @Test
  void test_no_trans_PENDING_NEW_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with FullFill");
  }

  @Test
  void test_no_trans_PENDING_NEW_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with TradeCorrect");
  }

  @Test
  void test_no_trans_PENDING_NEW_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with TradeCancelBust");
  }

  @Test
  void test_no_trans_PENDING_NEW_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with OrderExpired");
  }

  @Test
  void test_no_trans_PENDING_NEW_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_NEW, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_NEW with DoneForDay");
  }

  @Test
  void test_no_trans_NEW_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ValidationPassed");
  }

  @Test
  void test_no_trans_NEW_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ValidationFailed");
  }

  @Test
  void test_no_trans_NEW_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ReplaceAccepted");
  }

  @Test
  void test_no_trans_NEW_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with ReplaceRejected");
  }

  @Test
  void test_no_trans_NEW_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with CancelAccepted");
  }

  @Test
  void test_no_trans_NEW_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with CancelRejected");
  }

  @Test
  void test_no_trans_NEW_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with TradeCorrect");
  }

  @Test
  void test_no_trans_NEW_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(NEW, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for NEW with TradeCancelBust");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ValidationPassed");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ValidationFailed");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with ReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelRequested");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelAccepted");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with CancelRejected");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with FullFill");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with TradeCorrect");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with TradeCancelBust");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with OrderExpired");
  }

  @Test
  void test_no_trans_PENDING_REPLACE_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_REPLACE, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_REPLACE with DoneForDay");
  }

  @Test
  void test_no_trans_REPLACED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ValidationPassed");
  }

  @Test
  void test_no_trans_REPLACED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ValidationFailed");
  }

  @Test
  void test_no_trans_REPLACED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_REPLACED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with ReplaceRejected");
  }

  @Test
  void test_no_trans_REPLACED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with CancelAccepted");
  }

  @Test
  void test_no_trans_REPLACED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with CancelRejected");
  }

  @Test
  void test_no_trans_REPLACED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with TradeCorrect");
  }

  @Test
  void test_no_trans_REPLACED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with TradeCancelBust");
  }

  @Test
  void test_no_trans_REPLACED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REPLACED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REPLACED with DoneForDay");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ValidationPassed");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ValidationFailed");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceRequested");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceAccepted");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with ReplaceRejected");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with CancelRequested");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with PartialFill");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with FullFill");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with TradeCorrect");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with TradeCancelBust");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with OrderExpired");
  }

  @Test
  void test_no_trans_PENDING_CANCEL_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PENDING_CANCEL, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PENDING_CANCEL with DoneForDay");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ValidationPassed");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ValidationFailed");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceRequested");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with ReplaceRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelAccepted");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with CancelRejected");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with TradeCorrect");
  }

  @Test
  void test_no_trans_PARTIALLY_FILLED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(PARTIALLY_FILLED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for PARTIALLY_FILLED with TradeCancelBust");
  }

  @Test
  void test_no_trans_FILLED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ValidationPassed");
  }

  @Test
  void test_no_trans_FILLED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ValidationFailed");
  }

  @Test
  void test_no_trans_FILLED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceRequested");
  }

  @Test
  void test_no_trans_FILLED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_FILLED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with ReplaceRejected");
  }

  @Test
  void test_no_trans_FILLED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRequested");
  }

  @Test
  void test_no_trans_FILLED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelAccepted");
  }

  @Test
  void test_no_trans_FILLED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with CancelRejected");
  }

  @Test
  void test_no_trans_FILLED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with PartialFill");
  }

  @Test
  void test_no_trans_FILLED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with FullFill");
  }

  @Test
  void test_no_trans_FILLED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with OrderExpired");
  }

  @Test
  void test_no_trans_FILLED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(FILLED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for FILLED with DoneForDay");
  }

  @Test
  void test_no_trans_CANCELED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ValidationPassed");
  }

  @Test
  void test_no_trans_CANCELED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ValidationFailed");
  }

  @Test
  void test_no_trans_CANCELED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceRequested");
  }

  @Test
  void test_no_trans_CANCELED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_CANCELED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with ReplaceRejected");
  }

  @Test
  void test_no_trans_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_CANCELED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelAccepted");
  }

  @Test
  void test_no_trans_CANCELED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with CancelRejected");
  }

  @Test
  void test_no_trans_CANCELED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with PartialFill");
  }

  @Test
  void test_no_trans_CANCELED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with FullFill");
  }

  @Test
  void test_no_trans_CANCELED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with TradeCorrect");
  }

  @Test
  void test_no_trans_CANCELED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with TradeCancelBust");
  }

  @Test
  void test_no_trans_CANCELED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with OrderExpired");
  }

  @Test
  void test_no_trans_CANCELED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(CANCELED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for CANCELED with DoneForDay");
  }

  @Test
  void test_no_trans_REJECTED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ValidationPassed");
  }

  @Test
  void test_no_trans_REJECTED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ValidationFailed");
  }

  @Test
  void test_no_trans_REJECTED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceRequested");
  }

  @Test
  void test_no_trans_REJECTED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_REJECTED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with ReplaceRejected");
  }

  @Test
  void test_no_trans_REJECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRequested");
  }

  @Test
  void test_no_trans_REJECTED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelAccepted");
  }

  @Test
  void test_no_trans_REJECTED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with CancelRejected");
  }

  @Test
  void test_no_trans_REJECTED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with PartialFill");
  }

  @Test
  void test_no_trans_REJECTED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with FullFill");
  }

  @Test
  void test_no_trans_REJECTED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with TradeCorrect");
  }

  @Test
  void test_no_trans_REJECTED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with TradeCancelBust");
  }

  @Test
  void test_no_trans_REJECTED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with OrderExpired");
  }

  @Test
  void test_no_trans_REJECTED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(REJECTED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for REJECTED with DoneForDay");
  }

  @Test
  void test_no_trans_EXPIRED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ValidationPassed");
  }

  @Test
  void test_no_trans_EXPIRED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ValidationFailed");
  }

  @Test
  void test_no_trans_EXPIRED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceRequested");
  }

  @Test
  void test_no_trans_EXPIRED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_EXPIRED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with ReplaceRejected");
  }

  @Test
  void test_no_trans_EXPIRED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelRequested");
  }

  @Test
  void test_no_trans_EXPIRED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelAccepted");
  }

  @Test
  void test_no_trans_EXPIRED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with CancelRejected");
  }

  @Test
  void test_no_trans_EXPIRED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with PartialFill");
  }

  @Test
  void test_no_trans_EXPIRED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with FullFill");
  }

  @Test
  void test_no_trans_EXPIRED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with TradeCorrect");
  }

  @Test
  void test_no_trans_EXPIRED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with TradeCancelBust");
  }

  @Test
  void test_no_trans_EXPIRED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with OrderExpired");
  }

  @Test
  void test_no_trans_EXPIRED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(EXPIRED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for EXPIRED with DoneForDay");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ValidationPassed");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ValidationFailed");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceRequested");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceAccepted");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with ReplaceRejected");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelRequested");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelAccepted");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with CancelRejected");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with PartialFill");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with FullFill");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with TradeCorrect");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with TradeCancelBust");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with OrderExpired");
  }

  @Test
  void test_no_trans_DONE_FOR_DAY_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(DONE_FOR_DAY, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for DONE_FOR_DAY with DoneForDay");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ValidationPassed");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ValidationFailed");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceRequested");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with ReplaceRejected");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelRequested");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelAccepted");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with CancelRejected");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with PartialFill");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with FullFill");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with TradeCorrect");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with TradeCancelBust");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with OrderExpired");
  }

  @Test
  void test_no_trans_TRADE_CORRECTED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CORRECTED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CORRECTED with DoneForDay");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_ValidationPassed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ValidationPassed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ValidationPassed");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_ValidationFailed() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ValidationFailed, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ValidationFailed");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_ReplaceRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceRequested, ctx, createReplaceRequestedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceRequested");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_ReplaceAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceAccepted, ctx, createReplaceAcceptedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceAccepted");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_ReplaceRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, ReplaceRejected, ctx, createReplaceRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with ReplaceRejected");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_CancelRequested() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelRequested, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelRequested");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_CancelAccepted() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelAccepted, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelAccepted");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_CancelRejected() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, CancelRejected, ctx, createCancelRejectedPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with CancelRejected");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_PartialFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, PartialFill, ctx, createPartialFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with PartialFill");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_FullFill() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, FullFill, ctx, createFullFillPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with FullFill");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_TradeCorrect() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, TradeCorrect, ctx, createTradeCorrectPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with TradeCorrect");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_TradeCancelBust() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, TradeCancelBust, ctx, createTradeCancelBustPayload());
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with TradeCancelBust");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_OrderExpired() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, OrderExpired, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with OrderExpired");
  }

  @Test
  void test_no_trans_TRADE_CANCELED_DoneForDay() {
    var ctx = minimalCtx();
    var result = OrderFsmRunner.transition(TRADE_CANCELED, DoneForDay, ctx, null);
    assertTrue(result.isNoTransition(), "Expected no transition for TRADE_CANCELED with DoneForDay");
  }

}