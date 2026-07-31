/**
 * The entitlement decision, sized for the deterministic slice.
 *
 * <p><strong>Why this is not {@code io.crossasset.ems.aaa.InMemoryAaaService}.</strong> That
 * service is the production AAA surface: logon credentials, an event log, trace-context
 * propagation, and {@code establishedAtMicros} read from a clock. The slice has none of those and
 * must not acquire them — it is a pure function from an input journal to an output journal, and a
 * clock read would make byte-identical replay impossible. ADR 0002 scopes this component to "authz
 * decision only, no SSO/SCIM"; this package is exactly that and nothing more.
 *
 * <p>The duplication is deliberate and bounded: a session record, an identity, and a tag check. If
 * it grows past that, the right answer is to make the production service clock-injectable and
 * delete this package — not to keep widening it.
 *
 * <p>Sessions arrive on the journal as {@code SessionLogon} events rather than from configuration.
 * That keeps the whole entitlement state derivable from the input, which is what lets a corpus case
 * describe an authorization failure without any external fixture.
 *
 * <p>Mirrored by {@code rust/ems-aaa} and {@code cpp/ems-aaa}.
 */
@NullMarked
package io.crossasset.ems.aaa.slice;

import org.jspecify.annotations.NullMarked;
