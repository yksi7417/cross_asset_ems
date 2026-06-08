// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/route.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.List;
import java.util.Map;

/**
 * Pure transition function for RouteFsm.
 *
 * <p>Call {@link #transition} with the current state, event, context, and optional
 * payload. The method returns a {@link TransitionResult} with the new state,
 * updated context, and list of effect descriptors to dispatch.
 *
 * <p>This class is generated from schemas/fsm/routefsm.fsm.yaml — do not hand-edit.
 */
public final class RouteFsmRunner {

  private RouteFsmRunner() {}

  /**
   * Execute one FSM transition.
   *
   * @param state   current state
   * @param event   incoming event
   * @param ctx     current context (will not be mutated; new context in result)
   * @param rawPayload event payload (may be null for zero-payload events)
   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row
   */
  public static TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect>
      transition(
          RouteFsmState state,
          RouteFsmEvent event,
          RouteFsmContext ctx,
          Object rawPayload) {

    return switch (state) {
      case PENDING -> switch (event) {
        case RouteSent -> {
          yield TransitionResult.of(
            RouteFsmState.SENT,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteSent")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case SENT -> switch (event) {
        case RoutePendingNewAtVenue -> {
          yield TransitionResult.of(
            RouteFsmState.PENDING_NEW_AT_VENUE,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RoutePendingNewAtVenue")));
        }
        case RouteAcknowledged -> {
          yield TransitionResult.of(
            RouteFsmState.WORKING,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteWorking"), new RouteFsmEffect.EmitEvent("OrderFsm", "ValidationPassed")));
        }
        case RouteRejected -> {
          yield TransitionResult.of(
            RouteFsmState.REJECTED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteRejected"), new RouteFsmEffect.EmitEvent("OrderFsm", "ValidationFailed")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_NEW_AT_VENUE -> switch (event) {
        case RouteAcknowledged -> {
          yield TransitionResult.of(
            RouteFsmState.WORKING,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteWorking"), new RouteFsmEffect.EmitEvent("OrderFsm", "ValidationPassed")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case WORKING -> switch (event) {
        case RouteReplaceRequested -> {
          var payload = (RouteFsmPayloads.RouteReplaceRequestedPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.PENDING_REPLACE_AT_VENUE,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteReplaceRequested")));
        }
        case RouteCancelRequested -> {
          yield TransitionResult.of(
            RouteFsmState.PENDING_CANCEL_AT_VENUE,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.traceId(), ctx.initialOrderId(), "0"),
            List.of(new RouteFsmEffect.PublishEventLog("RouteCancelRequested")));
        }
        case RoutePartiallyFilled -> {
          var payload = (RouteFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus()),
            List.of(new RouteFsmEffect.PublishEventLog("RoutePartiallyFilled"), new RouteFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteFilled -> {
          var payload = (RouteFsmPayloads.RouteFilledPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus()),
            List.of(new RouteFsmEffect.PublishEventLog("RouteFilled"), new RouteFsmEffect.EmitEvent("OrderFsm", "FullFill")));
        }
        case RouteExpired -> {
          yield TransitionResult.of(
            RouteFsmState.EXPIRED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteExpired"), new RouteFsmEffect.EmitEvent("OrderFsm", "OrderExpired")));
        }
        case RouteSuperseded -> {
          yield TransitionResult.of(
            RouteFsmState.SUPERSEDED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteSuperseded")));
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            RouteFsmState.ANOMALY,
            ctx,
            List.of(new RouteFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "Route anomaly detected — manual triage required")), new RouteFsmEffect.PublishEventLog("RouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_REPLACE_AT_VENUE -> switch (event) {
        case RouteReplacePendingAtVenue -> {
          yield TransitionResult.of(
            RouteFsmState.PENDING_REPLACE_AT_VENUE,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteReplacePendingAtVenue")));
        }
        case RouteReplaced -> {
          var payload = (RouteFsmPayloads.RouteReplacedPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.WORKING,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteReplaced"), new RouteFsmEffect.EmitEvent("OrderFsm", "ReplaceAccepted")));
        }
        case RouteReplaceRejected -> {
          var payload = (RouteFsmPayloads.RouteReplaceRejectedPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.WORKING,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteReplaceRejected"), new RouteFsmEffect.EmitEvent("OrderFsm", "ReplaceRejected")));
        }
        case RoutePartiallyFilled -> {
          var payload = (RouteFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus()),
            List.of(new RouteFsmEffect.PublishEventLog("RoutePartiallyFilled"), new RouteFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteSuperseded -> {
          yield TransitionResult.of(
            RouteFsmState.SUPERSEDED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteSuperseded")));
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            RouteFsmState.ANOMALY,
            ctx,
            List.of(new RouteFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "Route anomaly in PENDING_REPLACE — manual triage required")), new RouteFsmEffect.PublishEventLog("RouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_CANCEL_AT_VENUE -> switch (event) {
        case RouteCanceled -> {
          yield TransitionResult.of(
            RouteFsmState.CANCELED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteCanceled")));
        }
        case RouteCancelRejected -> {
          var payload = (RouteFsmPayloads.RouteCancelRejectedPayload) rawPayload;
          if ("0".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              RouteFsmState.WORKING,
              ctx,
              List.of(new RouteFsmEffect.PublishEventLog("RouteCancelRejected"), new RouteFsmEffect.EmitEvent("OrderFsm", "CancelRejected")));
          }
          if ("1".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              RouteFsmState.PARTIALLY_FILLED,
              ctx,
              List.of(new RouteFsmEffect.PublishEventLog("RouteCancelRejected"), new RouteFsmEffect.EmitEvent("OrderFsm", "CancelRejected")));
          }
          yield TransitionResult.noTransition(state);
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            RouteFsmState.ANOMALY,
            ctx,
            List.of(new RouteFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "Route anomaly in PENDING_CANCEL — manual triage required")), new RouteFsmEffect.PublishEventLog("RouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PARTIALLY_FILLED -> switch (event) {
        case RouteCancelRequested -> {
          yield TransitionResult.of(
            RouteFsmState.PENDING_CANCEL_AT_VENUE,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.traceId(), ctx.initialOrderId(), "1"),
            List.of(new RouteFsmEffect.PublishEventLog("RouteCancelRequested")));
        }
        case RoutePartiallyFilled -> {
          var payload = (RouteFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus()),
            List.of(new RouteFsmEffect.PublishEventLog("RoutePartiallyFilled"), new RouteFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteFilled -> {
          var payload = (RouteFsmPayloads.RouteFilledPayload) rawPayload;
          yield TransitionResult.of(
            RouteFsmState.FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus()),
            List.of(new RouteFsmEffect.PublishEventLog("RouteFilled"), new RouteFsmEffect.EmitEvent("OrderFsm", "FullFill")));
        }
        case RouteExpired -> {
          yield TransitionResult.of(
            RouteFsmState.EXPIRED,
            ctx,
            List.of(new RouteFsmEffect.PublishEventLog("RouteExpired"), new RouteFsmEffect.EmitEvent("OrderFsm", "OrderExpired")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case FILLED -> switch (event) {
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
      case SUPERSEDED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case ANOMALY -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
    };
  }
}
