/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.aaa.Identity;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.aaa.TraceContext;
import io.crossasset.ems.aaa.identity.Desk;
import io.crossasset.ems.aaa.identity.Firm;
import io.crossasset.ems.aaa.identity.InMemoryIdentityRepository;
import io.crossasset.ems.aaa.permission.AuthorizationResult;
import io.crossasset.ems.aaa.permission.InMemoryTagPermissionStore;
import io.crossasset.ems.aaa.permission.TagPermissionEvaluator;
import io.crossasset.ems.core.clock.FixedTimeSource;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

/**
 * The slice's view of AAA, assembled entirely from production components.
 *
 * <p>This class holds no authorization logic of its own. {@link InMemoryAaaService} owns sessions,
 * {@link TagPermissionEvaluator} owns the three-layer firm → desk → user AND-gate, and the reject
 * codes come from the catalog the evaluator already cites. All this type does is wire them together
 * deterministically and register sessions off the journal.
 *
 * <p>It replaced a hand-written {@code io.crossasset.ems.aaa.slice} package that reimplemented a
 * cut-down version of the same decision. Three things had to change in production code before the
 * real service could be used at all, and each was a defect worth fixing on its own terms:
 *
 * <ul>
 *   <li>the service read the wall clock directly — now takes a {@code TimeSource};
 *   <li>there was no way to establish a session other than authenticating a credential — now {@link
 *       InMemoryAaaService#registerSession(long, Identity, TraceContext)};
 *   <li>the trace context was minted from {@code UUID.randomUUID()} — now supplied by the caller,
 *       so a replay produces the same bytes.
 * </ul>
 *
 * <p>The payoff is that the Rust and C++ ports now mirror the real evaluator, three catalog codes
 * and all, rather than a simplification of it.
 */
final class SliceAaa {

  /**
   * Time is pinned rather than read.
   *
   * <p>Session timestamps do not reach the output journal today, so any value would produce
   * identical bytes. Pinning it anyway means that when {@code establishedAtMicros} does reach the
   * journal, it is already deterministic instead of quietly breaking the conformance gate on the
   * commit that exposes it.
   */
  private static final long FIXED_EPOCH_MILLIS = 0L;

  private final InMemoryAaaService sessions =
      new InMemoryAaaService(
          new InMemoryAaaEventLog(), null, null, new FixedTimeSource(FIXED_EPOCH_MILLIS));
  private final InMemoryTagPermissionStore grants = new InMemoryTagPermissionStore();
  private final InMemoryIdentityRepository identities = new InMemoryIdentityRepository();
  private final TagPermissionEvaluator evaluator = new TagPermissionEvaluator(grants, identities);

  /**
   * Registers a session established upstream, replacing any session with the same id.
   *
   * @param userTags tags granted to the user
   * @param firmTags tags granted at firm level; the AND-gate's outermost layer
   * @param deskTags tags granted at desk level
   */
  void register(
      long sessionId,
      String firm,
      String desk,
      String user,
      SortedSet<String> userTags,
      SortedSet<String> firmTags,
      SortedSet<String> deskTags) {
    identities.addFirm(new Firm(firm, firm, firm + " admin"));
    identities.addDesk(new Desk(firm, desk, desk, desk + " admin"));
    for (String tag : firmTags) {
      grants.grantFirmTag(firm, tag);
    }
    for (String tag : deskTags) {
      grants.grantDeskTag(firm, desk, tag);
    }

    Identity identity =
        new Identity(firm, desk, user, "journal-established", Set.copyOf(userTags), Set.of());
    Identity resolved =
        new Identity(
            firm,
            desk,
            user,
            "journal-established",
            Set.copyOf(userTags),
            evaluator.computeEffectiveTags(identity));

    sessions.registerSession(sessionId, resolved, deterministicTrace(sessionId));
  }

  Optional<Session> session(long sessionId) {
    return sessions.sessionInfo(sessionId);
  }

  /** Runs the production three-layer AND-gate. An empty tag requires no entitlement. */
  AuthorizationResult authorize(Session session, String tag) {
    if (tag.isEmpty()) {
      return new AuthorizationResult.Allow();
    }
    return evaluator.authorize(session.identity(), tag);
  }

  /**
   * A trace context derived from the session id.
   *
   * <p>{@code TraceContextFactory.mint()} draws from {@code UUID.randomUUID()} and {@code
   * ThreadLocalRandom}. Neither belongs anywhere near a binary whose output is compared
   * byte-for-byte across three languages — even though the trace id does not reach the journal
   * today, drawing from a random source at all would break the "no RNG" rule the determinism table
   * in {@code conformance/README.md} sets out.
   */
  private static TraceContext deterministicTrace(long sessionId) {
    return new TraceContext(0L, sessionId, sessionId, TraceContext.FLAG_AUDIT);
  }
}
