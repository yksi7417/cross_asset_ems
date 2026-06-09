/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Optional;

/**
 * Read model for the Firm/Desk/User hierarchy. Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public interface IdentityRepository {

  Optional<Firm> findFirm(String firmId);

  Optional<Desk> findDesk(String firmId, String deskId);

  Optional<User> findUser(String firmId, String deskId, String userId);
}
