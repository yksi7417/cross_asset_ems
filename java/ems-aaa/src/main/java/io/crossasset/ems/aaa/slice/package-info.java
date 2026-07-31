/**
 * The entitlement decision, sized for the deterministic slice.
 *
 * <p><strong>Why this is not {@code io.crossasset.ems.aaa.InMemoryAaaService}.</strong> Three
 * things stood in the way. One is now fixed:
 *
 * <ul>
 *   <li><s>The service read the wall clock directly</s> — fixed. It now takes a {@link
 *       io.crossasset.ems.core.clock.TimeSource}, and {@code scripts/ci/checks/no_raw_clock.py}
 *       keeps it that way. A deterministic slice can supply a fixed or journal-driven time source.
 *   <li><strong>Logon is credential-based.</strong> {@code logon(LogonCredentials)} authenticates a
 *       token against a credential store. The slice authenticates nothing — sessions arrive on the
 *       journal already established, which is what ADR 0002 means by "authz decision only, no
 *       SSO/SCIM".
 *   <li><strong>Session ids are generated internally.</strong> The production service allocates
 *       them from an {@code AtomicLong}; the slice must honour the id the journal carries, or a
 *       corpus case could not reference a session at all.
 * </ul>
 *
 * <p>So the duplication is now down to those two, and it is bounded: a session record, an identity,
 * and a tag check. Closing the remaining gap means adding a {@code registerSession(long, Identity)}
 * entry point to the production service — widening its API for the slice's benefit, which is a
 * decision worth making deliberately rather than drifting into. Until then this package stays, and
 * it does not grow.
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
