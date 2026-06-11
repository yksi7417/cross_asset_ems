/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The cluster-of-clusters active lease (task 14.6, arch-deployment § What "active lease" means):
 * per pod, exactly one holder at any time; the holder must heartbeat to retain; release is explicit
 * (graceful switchover at an Archive position) or by heartbeat timeout (failure); every grant
 * carries a monotonically increasing fence token; acquisition is position-gated — a new holder must
 * have replayed at least to the last release position, so the no-dual-writer guarantee holds even
 * across crash handoffs.
 *
 * <p>This in-memory realization is the single-node stand-in for the strongly-consistent backing
 * (etcd / ZooKeeper / a dedicated Aeron cluster); the semantics — and the tests — are the contract
 * that backing must honor. Implements the 14.5 protocol's {@link SwitchoverProtocol.LeaseService}
 * seam.
 */
public final class ClusterLeaseService implements SwitchoverProtocol.LeaseService {

  /** A grant: who holds the pod, under which fence token, last heartbeat time. */
  public record Lease(String holder, long fenceToken, long lastHeartbeatMillis) {}

  private static final class PodLease {
    Lease current;
    long releasedAtPosition = -1;
    long nextToken = 1;
  }

  private final ConcurrentHashMap<String, PodLease> pods = new ConcurrentHashMap<>();
  private final LongSupplier clockMillis;
  private final long heartbeatTimeoutMillis;

  public ClusterLeaseService(LongSupplier clockMillis, long heartbeatTimeoutMillis) {
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    this.heartbeatTimeoutMillis = heartbeatTimeoutMillis;
  }

  @Override
  public Optional<Long> acquire(String pod, String holder, long atPosition) {
    PodLease lease = pods.computeIfAbsent(pod, k -> new PodLease());
    synchronized (lease) {
      expireIfStale(lease);
      if (lease.current != null || atPosition < lease.releasedAtPosition) {
        return Optional.empty();
      }
      long token = lease.nextToken++;
      lease.current = new Lease(holder, token, clockMillis.getAsLong());
      return Optional.of(token);
    }
  }

  @Override
  public boolean release(String pod, String holder, long atPosition) {
    PodLease lease = pods.get(pod);
    if (lease == null) {
      return false;
    }
    synchronized (lease) {
      if (lease.current == null || !lease.current.holder().equals(holder)) {
        return false;
      }
      lease.current = null;
      lease.releasedAtPosition = atPosition;
      return true;
    }
  }

  @Override
  public Optional<String> holder(String pod) {
    return lease(pod).map(Lease::holder);
  }

  /** Heartbeat to retain; false if the caller no longer holds the lease (must stop writing). */
  public boolean heartbeat(String pod, String holder) {
    PodLease lease = pods.get(pod);
    if (lease == null) {
      return false;
    }
    synchronized (lease) {
      expireIfStale(lease);
      if (lease.current == null || !lease.current.holder().equals(holder)) {
        return false;
      }
      lease.current =
          new Lease(lease.current.holder(), lease.current.fenceToken(), clockMillis.getAsLong());
      return true;
    }
  }

  /** The live lease, if any (expiry applied lazily on read). */
  public Optional<Lease> lease(String pod) {
    PodLease lease = pods.get(pod);
    if (lease == null) {
      return Optional.empty();
    }
    synchronized (lease) {
      expireIfStale(lease);
      return Optional.ofNullable(lease.current);
    }
  }

  /**
   * True when {@code fenceToken} is the pod's current grant — the check venue gateways, the Archive
   * writer, and downstream services run on every operation (outdated tokens rejected).
   */
  public boolean isCurrentToken(String pod, long fenceToken) {
    return lease(pod).map(l -> l.fenceToken() == fenceToken).orElse(false);
  }

  private void expireIfStale(PodLease lease) {
    if (lease.current != null
        && clockMillis.getAsLong() - lease.current.lastHeartbeatMillis()
            >= heartbeatTimeoutMillis) {
      // Heartbeat timeout: failure-path release. Position gate stays at the last explicit
      // release; the failed holder's position is unknown, which is exactly why the next
      // acquirer must have replayed the Archive (it reads everything the old active wrote).
      lease.current = null;
    }
  }
}
