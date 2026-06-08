// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.List;
import java.util.Map;

/**
 * Pure transition function for MultiLegFsm.
 *
 * <p>Call {@link #transition} with the current state, event, context, and optional
 * payload. The method returns a {@link TransitionResult} with the new state,
 * updated context, and list of effect descriptors to dispatch.
 *
 * <p>This class is generated from schemas/fsm/multilegfsm.fsm.yaml — do not hand-edit.
 */
public final class MultiLegFsmRunner {

  private MultiLegFsmRunner() {}

  /**
   * Execute one FSM transition.
   *
   * @param state   current state
   * @param event   incoming event
   * @param ctx     current context (will not be mutated; new context in result)
   * @param rawPayload event payload (may be null for zero-payload events)
   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row
   */
  public static TransitionResult<MultiLegFsmState, MultiLegFsmContext, MultiLegFsmEffect>
      transition(
          MultiLegFsmState state,
          MultiLegFsmEvent event,
          MultiLegFsmContext ctx,
          Object rawPayload) {

    return switch (state) {
      case STAGED -> switch (event) {
        case LegsValidated -> {
          yield TransitionResult.of(
            MultiLegFsmState.READY,
            ctx,
            List.of(new MultiLegFsmEffect.PublishEventLog("MultiLegValidated")));
        }
        case LegsValidationFailed -> {
          yield TransitionResult.of(
            MultiLegFsmState.REJECTED,
            ctx,
            List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "j")), new MultiLegFsmEffect.PublishEventLog("MultiLegRejected")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case READY -> switch (event) {
        case FirstLegDispatched -> {
          yield TransitionResult.of(
            MultiLegFsmState.LEGS_WORKING,
            ctx,
            List.of(new MultiLegFsmEffect.PublishEventLog("MultiLegExecutionStarted")));
        }
        case CancelRequested -> {
          yield TransitionResult.of(
            MultiLegFsmState.CANCELED,
            ctx,
            List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "4", "ord_status", "4")), new MultiLegFsmEffect.PublishEventLog("MultiLegCanceled")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case LEGS_WORKING -> switch (event) {
        case LegPartiallyFilled -> {
          var payload = (MultiLegFsmPayloads.LegPartiallyFilledPayload) rawPayload;
          yield TransitionResult.of(
            MultiLegFsmState.LEGS_WORKING,
            ctx,
            List.of(new MultiLegFsmEffect.PublishEventLog("LegPartiallyFilled")));
        }
        case LegFilled -> {
          var payload = (MultiLegFsmPayloads.LegFilledPayload) rawPayload;
          if ((((ctx.legsFilled() + 1) == ctx.totalLegs()) && (ctx.legsRejected() == 0) && (ctx.legsCanceled() == 0))) {
            yield TransitionResult.of(
              MultiLegFsmState.FILLED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), (ctx.legsFilled() + 1), ctx.legsRejected(), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "2")), new MultiLegFsmEffect.PublishEventLog("MultiLegFilled")));
          }
          if (("LEGS_INDEPENDENT".equals(ctx.executionMode()) && ((((ctx.legsFilled() + 1) + ctx.legsRejected()) + ctx.legsCanceled()) == ctx.totalLegs()) && (((ctx.legsRejected() > 0) || (ctx.legsCanceled() > 0))))) {
            yield TransitionResult.of(
              MultiLegFsmState.PARTIALLY_FILLED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), (ctx.legsFilled() + 1), ctx.legsRejected(), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new MultiLegFsmEffect.PublishEventLog("MultiLegPartiallyFilled")));
          }
          if (((((ctx.legsFilled() + 1) + ctx.legsRejected()) + ctx.legsCanceled()) < ctx.totalLegs())) {
            yield TransitionResult.of(
              MultiLegFsmState.LEGS_WORKING,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), (ctx.legsFilled() + 1), ctx.legsRejected(), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishEventLog("LegFilled")));
          }
          yield TransitionResult.noTransition(state);
        }
        case LegRejected -> {
          var payload = (MultiLegFsmPayloads.LegRejectedPayload) rawPayload;
          if ("ALL_OR_NONE".equals(ctx.executionMode())) {
            yield TransitionResult.of(
              MultiLegFsmState.REJECTED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), (ctx.legsRejected() + 1), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "8", "ord_status", "8")), new MultiLegFsmEffect.PublishEventLog("MultiLegRejected"), new MultiLegFsmEffect.EmitEvent("RouteFsm", "RouteCancelRequested")));
          }
          if ("SEQUENCED".equals(ctx.executionMode())) {
            yield TransitionResult.of(
              MultiLegFsmState.REJECTED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), (ctx.legsRejected() + 1), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "8", "ord_status", "8")), new MultiLegFsmEffect.PublishEventLog("MultiLegRejected")));
          }
          if (("LEGS_INDEPENDENT".equals(ctx.executionMode()) && (ctx.legsFilled() > 0) && ((((ctx.legsFilled() + ctx.legsRejected()) + 1) + ctx.legsCanceled()) == ctx.totalLegs()))) {
            yield TransitionResult.of(
              MultiLegFsmState.PARTIALLY_FILLED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), (ctx.legsRejected() + 1), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new MultiLegFsmEffect.PublishEventLog("MultiLegPartiallyFilled")));
          }
          if (("LEGS_INDEPENDENT".equals(ctx.executionMode()) && (ctx.legsFilled() == 0) && (((ctx.legsRejected() + 1) + ctx.legsCanceled()) == ctx.totalLegs()))) {
            yield TransitionResult.of(
              MultiLegFsmState.CANCELED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), (ctx.legsRejected() + 1), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "4", "ord_status", "4")), new MultiLegFsmEffect.PublishEventLog("MultiLegCanceled")));
          }
          if (("LEGS_INDEPENDENT".equals(ctx.executionMode()) && ((((ctx.legsFilled() + ctx.legsRejected()) + 1) + ctx.legsCanceled()) < ctx.totalLegs()))) {
            yield TransitionResult.of(
              MultiLegFsmState.LEGS_WORKING,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), (ctx.legsRejected() + 1), ctx.legsCanceled(), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishEventLog("LegRejected")));
          }
          yield TransitionResult.noTransition(state);
        }
        case LegCanceled -> {
          var payload = (MultiLegFsmPayloads.LegCanceledPayload) rawPayload;
          if (((ctx.legsFilled() > 0) && ((((ctx.legsFilled() + ctx.legsRejected()) + ctx.legsCanceled()) + 1) == ctx.totalLegs()))) {
            yield TransitionResult.of(
              MultiLegFsmState.PARTIALLY_FILLED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), ctx.legsRejected(), (ctx.legsCanceled() + 1), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "F", "ord_status", "1")), new MultiLegFsmEffect.PublishEventLog("MultiLegPartiallyFilled")));
          }
          if (((ctx.legsFilled() == 0) && (((ctx.legsRejected() + ctx.legsCanceled()) + 1) == ctx.totalLegs()))) {
            yield TransitionResult.of(
              MultiLegFsmState.CANCELED,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), ctx.legsRejected(), (ctx.legsCanceled() + 1), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "4", "ord_status", "4")), new MultiLegFsmEffect.PublishEventLog("MultiLegCanceled")));
          }
          if (((((ctx.legsFilled() + ctx.legsRejected()) + ctx.legsCanceled()) + 1) < ctx.totalLegs())) {
            yield TransitionResult.of(
              MultiLegFsmState.LEGS_WORKING,
              ctx.with(ctx.orderId(), ctx.multilegKind(), ctx.executionMode(), ctx.totalLegs(), ctx.legsFilled(), ctx.legsRejected(), (ctx.legsCanceled() + 1), ctx.packageId()),
              List.of(new MultiLegFsmEffect.PublishEventLog("LegCanceled")));
          }
          yield TransitionResult.noTransition(state);
        }
        case CancelRequested -> {
          yield TransitionResult.of(
            MultiLegFsmState.CANCELED,
            ctx,
            List.of(new MultiLegFsmEffect.PublishFixMessage(Map.of("msg_type", "8", "exec_type", "4", "ord_status", "4")), new MultiLegFsmEffect.PublishEventLog("MultiLegCanceled"), new MultiLegFsmEffect.EmitEvent("RouteFsm", "RouteCancelRequested")));
        }
        default -> TransitionResult.noTransition(state);
      };
      case FILLED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case PARTIALLY_FILLED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case CANCELED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
      case REJECTED -> switch (event) {
        default -> TransitionResult.noTransition(state);
      };
    };
  }
}
