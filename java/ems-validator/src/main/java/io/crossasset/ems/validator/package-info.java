/**
 * Layered pre-trade validation pipeline.
 *
 * <p>This package is {@link org.jspecify.annotations.NullMarked} — every type use is non-null
 * unless explicitly annotated {@code @Nullable}, and NullAway enforces that at compile time (see
 * {@code docs/decisions/0004-defensive-gate-stack.md}). It is one of the modules the polyglot
 * cash-equity slice ports, so its null-contract has to be explicit before the Rust and C++ ports
 * try to reproduce it: {@code Option<T>} and {@code std::optional<T>} are not guesses about which
 * Java references could be null.
 */
@NullMarked
package io.crossasset.ems.validator;

import org.jspecify.annotations.NullMarked;
