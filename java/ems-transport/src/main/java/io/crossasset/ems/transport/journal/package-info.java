/**
 * File-backed transport: the deterministic half of the {@code ems-transport} seam.
 *
 * <p>{@link io.crossasset.ems.transport.journal.Transport} is an interface with two intended
 * implementations. {@link io.crossasset.ems.transport.journal.JournalTransport} reads and writes
 * journal files — no media driver, no network, no timing — and is what the conformance gate runs.
 * {@code AeronTransport} is the live path, and the slice cannot tell them apart.
 *
 * <p>That split is the answer to the one genuine technical objection in the 2026-06-07 decision to
 * drop Rust: it takes the unproven {@code aeron-rs} binding off the critical path of everything.
 * See {@code docs/decisions/0006-abstract-transport-journal-first.md}.
 *
 * <p>A swappable transport is also the right design independently — it is what makes byte-identical
 * replay testable at all, which is a capability the Java tree already claims.
 */
@NullMarked
package io.crossasset.ems.transport.journal;

import org.jspecify.annotations.NullMarked;
