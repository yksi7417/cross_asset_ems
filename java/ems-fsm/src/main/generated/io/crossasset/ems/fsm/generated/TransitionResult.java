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

  /**
   * No matching transition — state and context unchanged, no effects.
   *
   * <p>The context comes back as-is rather than as {@code null}. Rust and C++ always returned the
   * unchanged context for a declined event; Java returning {@code null} was a three-way divergence
   * in one generated contract (T-8), latent only because every caller checked
   * {@code isNoTransition()} first — and {@code newContext} is not {@code @Nullable}, so nothing
   * would have stopped the first caller that did not.
   */
  public static <S, C, E> TransitionResult<S, C, E> noTransition(S currentState, C ctx) {
    return new TransitionResult<>(currentState, ctx, List.of(), true);
  }
}
