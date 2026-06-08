// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: tools/codegen/fsm_codegen.py
package io.crossasset.ems.fsm.generated;

import java.util.List;

/**
 * Return value from the FSM transition function.
 *
 * @param <S> state enum type
 * @param <C> context type
 * @param <E> effect type
 */
public record TransitionResult<S, C, E>(
    S newState,
    C newContext,
    List<E> effects,
    boolean isNoTransition
) {

  /** Normal transition result. */
  public static <S, C, E> TransitionResult<S, C, E> of(S newState, C newContext, List<E> effects) {
    return new TransitionResult<>(newState, newContext, effects, false);
  }

  /** No matching transition — state + context unchanged, no effects. */
  public static <S, C, E> TransitionResult<S, C, E> noTransition(S currentState) {
    return new TransitionResult<>(currentState, null, List.of(), true);
  }
}
