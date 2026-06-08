// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sor.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.List;
import java.util.Map;

/**
 * Pure transition function for SorFsm.
 *
 * <p>Call {@link #transition} with the current state, event, context, and optional
 * payload. The method returns a {@link TransitionResult} with the new state,
 * updated context, and list of effect descriptors to dispatch.
 *
 * <p>This class is generated from schemas/fsm/sorfsm.fsm.yaml — do not hand-edit.
 */
public final class SorFsmRunner {

  private SorFsmRunner() {}

  /**
   * Execute one FSM transition.
   *
   * @param state   current state
   * @param event   incoming event
   * @param ctx     current context (will not be mutated; new context in result)
   * @param rawPayload event payload (may be null for zero-payload events)
   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row
   */
  public static TransitionResult<SorFsmState, SorFsmContext, SorFsmEffect>
      transition(
          SorFsmState state,
          SorFsmEvent event,
          SorFsmContext ctx,
          Object rawPayload) {

    return switch (state) {
      case PENDING -> switch (event) {
        case RouteSent -> {
          yield TransitionResult.of(
            SorFsmState.SENT,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteSent")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case SENT -> switch (event) {
        case SorStrategyDecided -> {
          yield TransitionResult.of(
            SorFsmState.SENT,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorStrategySelected"), new SorFsmEffect.PublishEventLog("SorWheelSelectionLogged"), new SorFsmEffect.Notify(Map.of("signal", "dispatch_sor_children", "route_id", "{{ context.route_id }}"))));
        }
        case RoutePendingNewAtVenue -> {
          yield TransitionResult.of(
            SorFsmState.PENDING_NEW_AT_VENUE,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRoutePendingNewAtVenue")));
        }
        case RouteAcknowledged -> {
          yield TransitionResult.of(
            SorFsmState.WORKING,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteWorking"), new SorFsmEffect.EmitEvent("OrderFsm", "ValidationPassed")));
        }
        case RouteRejected -> {
          yield TransitionResult.of(
            SorFsmState.REJECTED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteRejected"), new SorFsmEffect.EmitEvent("OrderFsm", "ValidationFailed")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_NEW_AT_VENUE -> switch (event) {
        case RouteAcknowledged -> {
          yield TransitionResult.of(
            SorFsmState.WORKING,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteWorking"), new SorFsmEffect.EmitEvent("OrderFsm", "ValidationPassed")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case WORKING -> switch (event) {
        case SorPlanAdjusted -> {
          yield TransitionResult.of(
            SorFsmState.WORKING,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorPlanAdjusted"), new SorFsmEffect.Notify(Map.of("signal", "dispatch_sor_children", "route_id", "{{ context.route_id }}"))));
        }
        case RouteReplaceRequested -> {
          var payload = (SorFsmPayloads.RouteReplaceRequestedPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.PENDING_REPLACE_AT_VENUE,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorReplaceRequested")));
        }
        case RouteCancelRequested -> {
          yield TransitionResult.of(
            SorFsmState.PENDING_CANCEL_AT_VENUE,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.traceId(), ctx.initialOrderId(), "0", ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorCancelRequested")));
        }
        case RoutePartiallyFilled -> {
          var payload = (SorFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorRoutePartiallyFilled"), new SorFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteFilled -> {
          var payload = (SorFsmPayloads.RouteFilledPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorRouteFilled"), new SorFsmEffect.EmitEvent("OrderFsm", "FullFill")));
        }
        case RouteExpired -> {
          yield TransitionResult.of(
            SorFsmState.EXPIRED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteExpired"), new SorFsmEffect.EmitEvent("OrderFsm", "OrderExpired")));
        }
        case RouteSuperseded -> {
          yield TransitionResult.of(
            SorFsmState.SUPERSEDED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteSuperseded")));
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            SorFsmState.ANOMALY,
            ctx,
            List.of(new SorFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "SOR route anomaly — manual triage required")), new SorFsmEffect.PublishEventLog("SorRouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_REPLACE_AT_VENUE -> switch (event) {
        case RouteReplacePendingAtVenue -> {
          yield TransitionResult.of(
            SorFsmState.PENDING_REPLACE_AT_VENUE,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorReplacePendingAtVenue")));
        }
        case RouteReplaced -> {
          var payload = (SorFsmPayloads.RouteReplacedPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.WORKING,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteReplaced"), new SorFsmEffect.EmitEvent("OrderFsm", "ReplaceAccepted")));
        }
        case RouteReplaceRejected -> {
          var payload = (SorFsmPayloads.RouteReplaceRejectedPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.WORKING,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorReplaceRejected"), new SorFsmEffect.EmitEvent("OrderFsm", "ReplaceRejected")));
        }
        case RoutePartiallyFilled -> {
          var payload = (SorFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorRoutePartiallyFilled"), new SorFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteSuperseded -> {
          yield TransitionResult.of(
            SorFsmState.SUPERSEDED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteSuperseded")));
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            SorFsmState.ANOMALY,
            ctx,
            List.of(new SorFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "SOR route anomaly in PENDING_REPLACE — manual triage required")), new SorFsmEffect.PublishEventLog("SorRouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PENDING_CANCEL_AT_VENUE -> switch (event) {
        case RouteCanceled -> {
          yield TransitionResult.of(
            SorFsmState.CANCELED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteCanceled")));
        }
        case RouteCancelRejected -> {
          var payload = (SorFsmPayloads.RouteCancelRejectedPayload) rawPayload;
          if ("0".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              SorFsmState.WORKING,
              ctx,
              List.of(new SorFsmEffect.PublishEventLog("SorCancelRejected"), new SorFsmEffect.EmitEvent("OrderFsm", "CancelRejected")));
          }
          if ("1".equals(ctx.preCancelStatus())) {
            yield TransitionResult.of(
              SorFsmState.PARTIALLY_FILLED,
              ctx,
              List.of(new SorFsmEffect.PublishEventLog("SorCancelRejected"), new SorFsmEffect.EmitEvent("OrderFsm", "CancelRejected")));
          }
          yield TransitionResult.noTransition(state);
        }
        case RouteAnomaly -> {
          yield TransitionResult.of(
            SorFsmState.ANOMALY,
            ctx,
            List.of(new SorFsmEffect.Notify(Map.of("channel", "ops-alerts", "message", "SOR route anomaly in PENDING_CANCEL — manual triage required")), new SorFsmEffect.PublishEventLog("SorRouteAnomaly")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case PARTIALLY_FILLED -> switch (event) {
        case RouteCancelRequested -> {
          yield TransitionResult.of(
            SorFsmState.PENDING_CANCEL_AT_VENUE,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), ctx.cumQty(), ctx.leavesQty(), ctx.traceId(), ctx.initialOrderId(), "1", ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorCancelRequested")));
        }
        case RoutePartiallyFilled -> {
          var payload = (SorFsmPayloads.RoutePartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.PARTIALLY_FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), (ctx.leavesQty() - payload.lastQty()), ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorRoutePartiallyFilled"), new SorFsmEffect.EmitEvent("OrderFsm", "PartialFill")));
        }
        case RouteFilled -> {
          var payload = (SorFsmPayloads.RouteFilledPayload) rawPayload;
          yield TransitionResult.of(
            SorFsmState.FILLED,
            ctx.with(ctx.routeId(), ctx.orderId(), ctx.clOrdId(), ctx.origClOrdId(), ctx.venueMic(), ctx.instrumentId(), ctx.side(), ctx.routeQty(), ctx.price(), (ctx.cumQty() + payload.lastQty()), 0L, ctx.traceId(), ctx.initialOrderId(), ctx.preCancelStatus(), ctx.sorStrategyId()),
            List.of(new SorFsmEffect.PublishEventLog("SorRouteFilled"), new SorFsmEffect.EmitEvent("OrderFsm", "FullFill")));
        }
        case RouteExpired -> {
          yield TransitionResult.of(
            SorFsmState.EXPIRED,
            ctx,
            List.of(new SorFsmEffect.PublishEventLog("SorRouteExpired"), new SorFsmEffect.EmitEvent("OrderFsm", "OrderExpired")));
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
