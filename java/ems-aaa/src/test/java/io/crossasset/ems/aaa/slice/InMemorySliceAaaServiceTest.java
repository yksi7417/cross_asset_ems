/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Mirrored by {@code rust/ems-aaa/src/lib.rs} and {@code cpp/ems-aaa/test/aaa_test.cpp}. */
class InMemorySliceAaaServiceTest {

  private static SliceSession session(long id, String... tags) {
    return new SliceSession(
        id, new SliceIdentity("FIRM1", "DESK1", "trader1", new TreeSet<>(List.of(tags))));
  }

  @Test
  void unknownSessionIsEmpty() {
    assertThat(new InMemorySliceAaaService().session(42)).isEmpty();
  }

  @Test
  void registeredSessionIsFound() {
    InMemorySliceAaaService service = new InMemorySliceAaaService();
    service.register(session(42, "order-entry"));

    assertThat(service.session(42)).isPresent();
    assertThat(service.session(42).orElseThrow().identity().user()).isEqualTo("trader1");
  }

  @Test
  void reLogonReplacesTheSession() {
    InMemorySliceAaaService service = new InMemorySliceAaaService();
    service.register(session(42, "order-entry"));
    service.register(
        new SliceSession(
            42, new SliceIdentity("FIRM1", "DESK1", "trader2", new TreeSet<>(List.of()))));

    // Policing duplicate logons belongs to the FIX session layer, which ADR 0002
    // puts out of scope for this slice.
    assertThat(service.session(42).orElseThrow().identity().user()).isEqualTo("trader2");
  }

  @Test
  void heldTagIsAllowed() {
    InMemorySliceAaaService service = new InMemorySliceAaaService();
    SliceSession s = session(1, "order-entry");

    assertThat(service.authorize(s, "order-entry")).isInstanceOf(AuthorizationDecision.Allow.class);
  }

  @Test
  void missingTagIsDeniedWithTheCatalogCode() {
    InMemorySliceAaaService service = new InMemorySliceAaaService();
    SliceSession s = session(1, "market-data");

    AuthorizationDecision decision = service.authorize(s, "order-entry");

    assertThat(decision).isInstanceOf(AuthorizationDecision.Deny.class);
    AuthorizationDecision.Deny deny = (AuthorizationDecision.Deny) decision;
    // EMS-PRM-1001 is a real entry in schemas/reject-codes/catalog.yaml. It
    // reaches the output journal, so an invented code would diverge from the
    // catalog silently.
    assertThat(deny.code()).isEqualTo("EMS-PRM-1001");
    assertThat(deny.category()).isEqualTo("PRM");
    assertThat(deny.reason()).isEqualTo("User trader1 does not have permission tag #order-entry.");
  }

  @Test
  void emptyTagRequiresNoEntitlement() {
    InMemorySliceAaaService service = new InMemorySliceAaaService();

    assertThat(service.authorize(session(1), "")).isInstanceOf(AuthorizationDecision.Allow.class);
  }

  @Test
  void tagsIterateInLexicographicOrderRegardlessOfInsertionOrder() {
    SliceIdentity a =
        new SliceIdentity("F", "D", "u", new TreeSet<>(List.of("zeta", "alpha", "mid")));

    // The tag set can reach the output journal, so its order is a contract.
    assertThat(a.tags()).containsExactly("alpha", "mid", "zeta");
  }

  @Test
  void identityTagsAreImmutable() {
    SliceIdentity identity = new SliceIdentity("F", "D", "u", new TreeSet<>(List.of("a")));

    assertThatThrownBy(() -> identity.tags().add("b"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void negativeSessionIdIsRejected() {
    assertThatThrownBy(
            () -> new SliceSession(-1, new SliceIdentity("F", "D", "u", new TreeSet<>())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sessionId");
  }
}
