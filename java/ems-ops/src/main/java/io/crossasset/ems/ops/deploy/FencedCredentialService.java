/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fence-token venue credential rotation (task 14.7, arch-deployment § Why no duplication or loss):
 * every venue credential carries the issuing cluster's fence token, and venue gateways check the
 * token against the live lease on every egress — so after a switchover (or a crash handoff) the old
 * cluster's credentials are dead <i>by construction</i>, with no revocation round-trip to lose a
 * race against. Even a runaway thread on the fenced cluster cannot send an order; it may still
 * observe (market data) for diagnosis.
 *
 * <p>Credentials reference vault paths, never secret values (arch-deployment § Configuration
 * injection); rotation is therefore a token change, not a secret redistribution. Issuance is
 * journaled for audit.
 */
public final class FencedCredentialService {

  /** A venue credential: a vault reference bound to the issuing holder's fence token. */
  public record VenueCredential(String pod, String holder, String vaultRef, long fenceToken) {}

  /** One journaled issuance. */
  public record Issuance(String pod, String holder, long fenceToken) {}

  private final ClusterLeaseService leases;
  private final List<Issuance> journal = java.util.Collections.synchronizedList(new ArrayList<>());

  public FencedCredentialService(ClusterLeaseService leases) {
    this.leases = Objects.requireNonNull(leases, "leases");
  }

  /**
   * Issue venue credentials to {@code holder} — granted only to the pod's <b>current</b> lease
   * holder, bound to its fence token. A standby (or a fenced old cluster) gets nothing.
   */
  public Optional<VenueCredential> issue(String pod, String holder) {
    return leases
        .lease(pod)
        .filter(lease -> lease.holder().equals(holder))
        .map(
            lease -> {
              VenueCredential credential =
                  new VenueCredential(pod, holder, "vault://venues/" + pod, lease.fenceToken());
              journal.add(new Issuance(pod, holder, lease.fenceToken()));
              return credential;
            });
  }

  /**
   * The egress gate venue gateways run on every outbound operation: true only while the
   * credential's fence token is the pod's current grant. Rotation is implicit — a lease handoff
   * invalidates every credential of the old holder instantly.
   */
  public boolean authorizeEgress(VenueCredential credential) {
    return leases.isCurrentToken(credential.pod(), credential.fenceToken());
  }

  /**
   * Observer-only downgrade (arch: "credentials downgraded to observer-only"): a previously issued
   * credential may keep consuming market data for diagnosis even when fenced — it just cannot emit
   * orders.
   */
  public boolean authorizeObserve(VenueCredential credential) {
    synchronized (journal) {
      return journal.stream()
          .anyMatch(
              i ->
                  i.pod().equals(credential.pod())
                      && i.holder().equals(credential.holder())
                      && i.fenceToken() == credential.fenceToken());
    }
  }

  /** Immutable issuance journal. */
  public List<Issuance> journal() {
    synchronized (journal) {
      return List.copyOf(journal);
    }
  }
}
