/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory identity repository for testing and skeleton use. Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public final class InMemoryIdentityRepository implements IdentityRepository {

  private final Map<String, Firm> firms = new ConcurrentHashMap<>();
  private final Map<String, Desk> desks = new ConcurrentHashMap<>();
  private final Map<String, User> users = new ConcurrentHashMap<>();

  public void addFirm(Firm firm) {
    firms.put(firm.firmId(), firm);
  }

  public void addDesk(Desk desk) {
    desks.put(deskKey(desk.firmId(), desk.deskId()), desk);
  }

  public void addUser(User user) {
    users.put(userKey(user.firmId(), user.deskId(), user.userId()), user);
  }

  @Override
  public Optional<Firm> findFirm(String firmId) {
    return Optional.ofNullable(firms.get(firmId));
  }

  @Override
  public Optional<Desk> findDesk(String firmId, String deskId) {
    return Optional.ofNullable(desks.get(deskKey(firmId, deskId)));
  }

  @Override
  public Optional<User> findUser(String firmId, String deskId, String userId) {
    return Optional.ofNullable(users.get(userKey(firmId, deskId, userId)));
  }

  private static String deskKey(String firmId, String deskId) {
    return firmId + '\0' + deskId;
  }

  private static String userKey(String firmId, String deskId, String userId) {
    return firmId + '\0' + deskId + '\0' + userId;
  }
}
