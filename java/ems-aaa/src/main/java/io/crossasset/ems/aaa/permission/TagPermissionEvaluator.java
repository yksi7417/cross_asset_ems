/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

import io.crossasset.ems.aaa.Identity;
import io.crossasset.ems.aaa.identity.IdentityRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates the three-layer AND-gate permission check per arch-tag-permissions.md.
 *
 * <p>Evaluation order is outermost-first: FIRM → DESK → USER. A FIRM denial is reported before
 * checking DESK, because a missing firm grant makes the user-level resolution irrelevant.
 *
 * <p>Admin hints are sourced from the {@link IdentityRepository} when present, falling back to a
 * generic string.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
public final class TagPermissionEvaluator {

  private final TagPermissionStore store;
  private final IdentityRepository identityRepository;

  public TagPermissionEvaluator(TagPermissionStore store, IdentityRepository identityRepository) {
    this.store = Objects.requireNonNull(store, "store");
    this.identityRepository = Objects.requireNonNull(identityRepository, "identityRepository");
  }

  /**
   * Returns {@link AuthorizationResult.Allow} if all three levels grant the tag; otherwise returns
   * {@link AuthorizationResult.Deny} citing the outermost missing level.
   */
  public AuthorizationResult authorize(Identity identity, String tag) {
    // Layer 1 — firm (outermost)
    if (!store.firmGranted(identity.firmId(), tag)) {
      String firmAdmin =
          identityRepository
              .findFirm(identity.firmId())
              .map(f -> f.adminRef())
              .orElse("firm admin");
      return new AuthorizationResult.Deny(
          "EMS-PRM-1003",
          "Firm `" + identity.firmId() + "` is not granted tag `#" + tag + "`.",
          DenialLevel.FIRM,
          firmAdmin);
    }

    // Layer 2 — desk
    if (!store.deskGranted(identity.firmId(), identity.deskId(), tag)) {
      String deskAdmin =
          identityRepository
              .findDesk(identity.firmId(), identity.deskId())
              .map(d -> d.adminRef())
              .orElse("desk admin");
      return new AuthorizationResult.Deny(
          "EMS-PRM-1002",
          "User `"
              + identity.userId()
              + "` has tag `#"
              + tag
              + "` but desk `"
              + identity.deskId()
              + "` is not granted.",
          DenialLevel.DESK,
          deskAdmin);
    }

    // Layer 3 — user (innermost)
    if (!identity.tags().contains(tag)) {
      return new AuthorizationResult.Deny(
          "EMS-PRM-1001",
          "User `" + identity.userId() + "` does not have permission tag `#" + tag + "`.",
          DenialLevel.USER,
          "tag admin for #" + tag);
    }

    return new AuthorizationResult.Allow();
  }

  /**
   * Returns the AND-gated effective tags: {@code identity.tags()} filtered to those that pass all
   * three layers.
   */
  public Set<String> computeEffectiveTags(Identity identity) {
    return identity.tags().stream()
        .filter(tag -> authorize(identity, tag) instanceof AuthorizationResult.Allow)
        .collect(Collectors.toUnmodifiableSet());
  }
}
