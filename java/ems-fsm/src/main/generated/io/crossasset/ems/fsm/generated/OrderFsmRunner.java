// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.List;
import java.util.Map;

/**
 * Pure transition function for OrderFsm.
 *
 * <p>Call {@link #transition} with the current state, event, context, and optional
 * payload. The method returns a {@link TransitionResult} with the new state,
 * updated context, and list of effect descriptors to dispatch.
 *
 * <p>This class is generated from schemas/fsm/orderfsm.fsm.yaml — do not hand-edit.
 */
public final class OrderFsmRunner {

  private OrderFsmRunner() {}

  /**
   * Execute one FSM transition.
   *
   * @param state   current state
   * @param event   incoming event
   * @param ctx     current context (will not be mutated; new context in result)
   * @param rawPayload event payload (may be null for zero-payload events)
   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row
   */
  public static TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect>
      transition(
          OrderFsmState state,
          OrderFsmEvent event,
          OrderFsmContext ctx,
          Object rawPayload) {

    return switch (state) {
      case PENDING_NEW -> switch (event) {
        case ValidationPassed -> {
          yield TransitionResult.of(
            OrderFsmState.NEW,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "0", "ord_status", "0")), new OrderFsmEffect.PublishEventLog("OrderAccepted")));
        }
        case ValidationFailed -> {
          yield TransitionResult.of(
            OrderFsmState.REJECTED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "j")), new OrderFsmEffect.PublishEventLog("OrderRejected")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case NEW -> switch (event) {
        case ReplaceRequested -> {
          var payload = (OrderFsmPayloads.ReplaceRequestedPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PENDING_REPLACE,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), "0"),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "E", "ord_status", "E")), new OrderFsmEffect.PublishEventLog("OrderReplaceRequested")));
        }
        case CancelRequested -> {
          yield TransitionResult.of(
            OrderFsmState.PENDING_CANCEL,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), "0", ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "6", "ord_status", "6")), new OrderFsmEffect.PublishEventLog("OrderCancelRequested")));
        }
        case PartialFill -> {
          var payload = (OrderFsmPayloads.PartialFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new OrderFsmEffect.PublishEventLog("OrderPartiallyFilled")));
        }
        case FullFill -> {
          var payload = (OrderFsmPayloads.FullFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "2")), new OrderFsmEffect.PublishEventLog("OrderFilled")));
        }
        case OrderExpired -> {
          yield TransitionResult.of(
            OrderFsmState.EXPIRED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "C", "ord_status", "C")), new OrderFsmEffect.PublishEventLog("OrderExpired")));
        }
        case DoneForDay -> {
          yield TransitionResult.of(
            OrderFsmState.DONE_FOR_DAY,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "3", "ord_status", "3")), new OrderFsmEffect.PublishEventLog("OrderDoneForDay")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_REPLACE -> switch (event) {
        case ReplaceAccepted -> {
          var payload = (OrderFsmPayloads.ReplaceAcceptedPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.REPLACED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), null),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "5", "ord_status", "0")), new OrderFsmEffect.PublishEventLog("OrderReplaced")));
        }
        case ReplaceRejected -> {
          var payload = (OrderFsmPayloads.ReplaceRejectedPayload) rawPayload;
          if ("0".equals(ctx.preReplaceStatus())) {
            yield TransitionResult.of(
              OrderFsmState.NEW,
              ctx,
              List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "9")), new OrderFsmEffect.PublishEventLog("OrderReplaceRejected")));
          }
          if ("5".equals(ctx.preReplaceStatus())) {
            yield TransitionResult.of(
              OrderFsmState.REPLACED,
              ctx,
              List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "9")), new OrderFsmEffect.PublishEventLog("OrderReplaceRejected")));
          }
          yield TransitionResult.noTransition(state);
        }
        case PartialFill -> {
          var payload = (OrderFsmPayloads.PartialFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new OrderFsmEffect.PublishEventLog("OrderPartiallyFilled")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case REPLACED -> switch (event) {
        case ReplaceRequested -> {
          var payload = (OrderFsmPayloads.ReplaceRequestedPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PENDING_REPLACE,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), "5"),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "E", "ord_status", "E")), new OrderFsmEffect.PublishEventLog("OrderReplaceRequested")));
        }
        case CancelRequested -> {
          yield TransitionResult.of(
            OrderFsmState.PENDING_CANCEL,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), "5", ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "6", "ord_status", "6")), new OrderFsmEffect.PublishEventLog("OrderCancelRequested")));
        }
        case PartialFill -> {
          var payload = (OrderFsmPayloads.PartialFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new OrderFsmEffect.PublishEventLog("OrderPartiallyFilled")));
        }
        case FullFill -> {
          var payload = (OrderFsmPayloads.FullFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "2")), new OrderFsmEffect.PublishEventLog("OrderFilled")));
        }
        case OrderExpired -> {
          yield TransitionResult.of(
            OrderFsmState.EXPIRED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "C", "ord_status", "C")), new OrderFsmEffect.PublishEventLog("OrderExpired")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_CANCEL -> switch (event) {
        case CancelAccepted -> {
          yield TransitionResult.of(
            OrderFsmState.CANCELED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "4", "ord_status", "4")), new OrderFsmEffect.PublishEventLog("OrderCanceled"), new OrderFsmEffect.EmitEvent("RouteFsm", "RouteCancelRequested")));
        }
        case CancelRejected -> {
          var payload = (OrderFsmPayloads.CancelRejectedPayload) rawPayload;
          if ("0".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              OrderFsmState.NEW,
              ctx,
              List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "9")), new OrderFsmEffect.PublishEventLog("OrderCancelRejected")));
          }
          if ("5".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              OrderFsmState.REPLACED,
              ctx,
              List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "9")), new OrderFsmEffect.PublishEventLog("OrderCancelRejected")));
          }
          if ("1".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              OrderFsmState.PARTIALLY_FILLED,
              ctx,
              List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "9")), new OrderFsmEffect.PublishEventLog("OrderCancelRejected")));
          }
          yield TransitionResult.noTransition(state);
        }
        default -> TransitionResult.noTransition(state);
      };
      case PARTIALLY_FILLED -> switch (event) {
        case CancelRequested -> {
          yield TransitionResult.of(
            OrderFsmState.PENDING_CANCEL,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), "1", ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "6", "ord_status", "6")), new OrderFsmEffect.PublishEventLog("OrderCancelRequested")));
        }
        case PartialFill -> {
          var payload = (OrderFsmPayloads.PartialFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new OrderFsmEffect.PublishEventLog("OrderPartiallyFilled")));
        }
        case FullFill -> {
          var payload = (OrderFsmPayloads.FullFillPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.FILLED,
            ctx.with(ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.instrumentId(), ctx.side(), ctx.orderQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.account(), ctx.tif(), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.preReplaceStatus()),
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "2")), new OrderFsmEffect.PublishEventLog("OrderFilled")));
        }
        case OrderExpired -> {
          yield TransitionResult.of(
            OrderFsmState.EXPIRED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "C", "ord_status", "C")), new OrderFsmEffect.PublishEventLog("OrderExpired")));
        }
        case DoneForDay -> {
          yield TransitionResult.of(
            OrderFsmState.DONE_FOR_DAY,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "3", "ord_status", "3")), new OrderFsmEffect.PublishEventLog("OrderDoneForDay")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case FILLED -> switch (event) {
        case TradeCorrect -> {
          var payload = (OrderFsmPayloads.TradeCorrectPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.TRADE_CORRECTED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "G")), new OrderFsmEffect.PublishEventLog("TradeCorrected")));
        }
        case TradeCancelBust -> {
          var payload = (OrderFsmPayloads.TradeCancelBustPayload) rawPayload;
          yield TransitionResult.of(
            OrderFsmState.TRADE_CANCELED,
            ctx,
            List.of(new OrderFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "H")), new OrderFsmEffect.PublishEventLog("TradeCanceled")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case CANCELED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case REJECTED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case EXPIRED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case DONE_FOR_DAY -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case TRADE_CORRECTED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case TRADE_CANCELED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
    };
  }
}
