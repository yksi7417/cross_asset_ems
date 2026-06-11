/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.ops.deploy.FencedCredentialService.VenueCredential;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FencedCredentialService}: holder-only issuance, per-egress fence checks,
 * implicit rotation on lease handoff, observer-only downgrade for fenced clusters. Per
 * arch-deployment.md, task 14.7.
 */
class FencedCredentialServiceTest {

  private final long[] now = {0L};
  private final ClusterLeaseService leases = new ClusterLeaseService(() -> now[0], 10_000L);
  private final FencedCredentialService credentials = new FencedCredentialService(leases);

  @Test
  void onlyTheCurrentHolder_getsCredentials() {
    leases.acquire("pod-a", "A1", 0);
    assertThat(credentials.issue("pod-a", "A2")).isEmpty(); // standby refused
    VenueCredential credential = credentials.issue("pod-a", "A1").orElseThrow();
    assertThat(credential.fenceToken()).isEqualTo(1);
    assertThat(credential.vaultRef()).isEqualTo("vault://venues/pod-a"); // ref, never the secret
  }

  @Test
  void egress_authorizedWhileTokenIsCurrent() {
    leases.acquire("pod-a", "A1", 0);
    VenueCredential credential = credentials.issue("pod-a", "A1").orElseThrow();
    assertThat(credentials.authorizeEgress(credential)).isTrue();
  }

  @Test
  void handoff_invalidatesOldCredentialInstantly_newOneAuthorized() {
    leases.acquire("pod-a", "A1", 0);
    VenueCredential oldCredential = credentials.issue("pod-a", "A1").orElseThrow();
    leases.release("pod-a", "A1", 100);
    leases.acquire("pod-a", "A2", 100);
    VenueCredential newCredential = credentials.issue("pod-a", "A2").orElseThrow();

    assertThat(credentials.authorizeEgress(oldCredential)).isFalse(); // runaway thread fenced
    assertThat(credentials.authorizeEgress(newCredential)).isTrue();
  }

  @Test
  void crashHandoff_heartbeatTimeout_alsoFencesOldCredential() {
    leases.acquire("pod-a", "A1", 0);
    VenueCredential credential = credentials.issue("pod-a", "A1").orElseThrow();
    now[0] = 10_000L; // heartbeat timeout
    assertThat(credentials.authorizeEgress(credential)).isFalse();
  }

  @Test
  void fencedCluster_keepsObserverAccess() {
    leases.acquire("pod-a", "A1", 0);
    VenueCredential oldCredential = credentials.issue("pod-a", "A1").orElseThrow();
    leases.release("pod-a", "A1", 100);
    leases.acquire("pod-a", "A2", 100);
    assertThat(credentials.authorizeEgress(oldCredential)).isFalse();
    assertThat(credentials.authorizeObserve(oldCredential)).isTrue(); // diagnosis still possible
    VenueCredential forged = new VenueCredential("pod-a", "A3", "vault://venues/pod-a", 99);
    assertThat(credentials.authorizeObserve(forged)).isFalse(); // never issued
  }

  @Test
  void issuance_isJournaled() {
    leases.acquire("pod-a", "A1", 0);
    credentials.issue("pod-a", "A1");
    assertThat(credentials.journal()).hasSize(1);
    assertThat(credentials.journal().get(0).holder()).isEqualTo("A1");
  }
}
