/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Objects;
import java.util.Set;

/**
 * Resolved identity envelope per arch-firm-desk-user.md.
 *
 * <p>{@code tags} = user-level granted tags. {@code effectiveTags} = AND-gate intersection of user
 * ∩ desk ∩ firm grants; computed by TagPermissionEvaluator (task 5.3). In the 5.1 skeleton {@code
 * effectiveTags} equals {@code tags}.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public record Identity(
    String firmId,
    String deskId,
    String userId,
    String authToken,
    Set<String> tags,
    Set<String> effectiveTags) {

  public Identity {
    Objects.requireNonNull(firmId, "firmId");
    Objects.requireNonNull(deskId, "deskId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(authToken, "authToken");
    tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
    effectiveTags = Set.copyOf(Objects.requireNonNull(effectiveTags, "effectiveTags"));
  }
}
