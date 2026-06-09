/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Firm/Desk/User hierarchy and Setting cascade. Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
class FirmDeskUserTest {

  private InMemoryIdentityRepository repo;

  @BeforeEach
  void setUp() {
    repo = new InMemoryIdentityRepository();
    repo.addFirm(new Firm("ACME", "ACME Capital", "firm_admin@acme"));
    repo.addDesk(new Desk("ACME", "EQ", "Equity Cash", "eq_admin@acme"));
    repo.addDesk(new Desk("ACME", "FX", "FX G10", "fx_admin@acme"));
    repo.addUser(new User("ACME", "EQ", "alice", "Alice Trader", "eq_admin@acme"));
    repo.addUser(new User("ACME", "FX", "bob", "Bob FX", "fx_admin@acme"));
  }

  // ── Firm lookup ──────────────────────────────────────────────────────────

  @Test
  void findFirm_known_returnsRecord() {
    Firm firm = repo.findFirm("ACME").orElseThrow();
    assertEquals("ACME", firm.firmId());
    assertEquals("ACME Capital", firm.name());
    assertEquals("firm_admin@acme", firm.adminRef());
  }

  @Test
  void findFirm_unknown_returnsEmpty() {
    assertTrue(repo.findFirm("UNKNOWN").isEmpty());
  }

  // ── Desk lookup ──────────────────────────────────────────────────────────

  @Test
  void findDesk_known_returnsRecord() {
    Desk desk = repo.findDesk("ACME", "EQ").orElseThrow();
    assertEquals("ACME", desk.firmId());
    assertEquals("EQ", desk.deskId());
    assertEquals("Equity Cash", desk.name());
  }

  @Test
  void findDesk_unknownFirm_returnsEmpty() {
    assertTrue(repo.findDesk("NOPE", "EQ").isEmpty());
  }

  @Test
  void findDesk_unknownDesk_returnsEmpty() {
    assertTrue(repo.findDesk("ACME", "CREDIT").isEmpty());
  }

  @Test
  void findDesk_differentFirmsSameId_isolated() {
    repo.addFirm(new Firm("BETA", "Beta Corp", "beta_admin"));
    repo.addDesk(new Desk("BETA", "EQ", "Beta Equity", "beta_eq_admin"));
    Desk acmeEq = repo.findDesk("ACME", "EQ").orElseThrow();
    Desk betaEq = repo.findDesk("BETA", "EQ").orElseThrow();
    assertNotEquals(acmeEq.name(), betaEq.name());
  }

  // ── User lookup ──────────────────────────────────────────────────────────

  @Test
  void findUser_known_returnsRecord() {
    User user = repo.findUser("ACME", "EQ", "alice").orElseThrow();
    assertEquals("alice", user.userId());
    assertEquals("ACME", user.firmId());
    assertEquals("EQ", user.deskId());
  }

  @Test
  void findUser_wrongDesk_returnsEmpty() {
    assertTrue(repo.findUser("ACME", "FX", "alice").isEmpty());
  }

  @Test
  void findUser_unknown_returnsEmpty() {
    assertTrue(repo.findUser("ACME", "EQ", "charlie").isEmpty());
  }

  // ── Setting cascade ──────────────────────────────────────────────────────

  @Test
  void setting_userOverride_resolvedToUser() {
    Setting<String> s =
        new Setting<>("default_account", "FIRM_DEFAULT", "DESK_DEFAULT", "USER_ACCT");
    assertEquals("USER_ACCT", s.resolve());
  }

  @Test
  void setting_userNull_resolvedToDesk() {
    Setting<String> s = new Setting<>("default_tif", "FIRM_DAY", "DESK_IOC", null);
    assertEquals("DESK_IOC", s.resolve());
  }

  @Test
  void setting_userAndDeskNull_resolvedToFirm() {
    Setting<String> s = new Setting<>("markup_bps", "FIRM_10", null, null);
    assertEquals("FIRM_10", s.resolve());
  }

  @Test
  void setting_allNull_resolvedToNull() {
    Setting<String> s = new Setting<>("absent_key", null, null, null);
    assertNull(s.resolve());
  }

  @Test
  void setting_preservesKey() {
    Setting<Integer> s = new Setting<>("max_size", 1000, null, null);
    assertEquals("max_size", s.key());
  }

  @Test
  void setting_integralType_resolves() {
    Setting<Integer> s = new Setting<>("max_single_order_notional", 10_000_000, 5_000_000, null);
    assertEquals(5_000_000, s.resolve());
  }
}
