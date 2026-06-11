# Cross-Asset EMS — Operator Guide

Running, watching, intervening in, and switching over the EMS. Written 2026-06-11 against the
completed v1 build-out (task 17.2); the companion [`USER_GUIDE.md`](USER_GUIDE.md) covers building
and driving the system, [`runbooks/`](runbooks/) holds environment-specific procedures.

---

## 1. The dev/ops stack

```bash
./scripts/dev/start-dev-stack.sh        # Postgres + OTel collector + Jaeger + Prometheus +
./scripts/dev/check-dev-stack.sh        #   Grafana + OpenSearch; 16 health checks, exit 0 = good
```

| Surface | URL | What you watch there |
|---|---|---|
| Grafana | <http://localhost:3000> | Golden-signals + per-asset latency dashboards |
| Jaeger | <http://localhost:16686> | Distributed traces — one trace ID from FIX-in to venue-out (tag 9700 carries it across the wire) |
| OpenSearch Dashboards | <http://localhost:5601> | Logs (`ems-logs*`) |
| Prometheus | <http://localhost:9091> | Raw metrics |

## 2. Introspection — what is the system doing right now?

Every component registers on the **IntrospectionRegistry** (`ems-ops`):

- `list()` — enumerate components; `find(id).dumpState()` — typed state snapshot;
  `listMetrics()` — counters/gauges.
- `aggregateHealth()` — worst-of across components with offenders named:
  **GREEN** all good · **YELLOW** degraded but functional (e.g. a venue adapter reconnecting) ·
  **RED** do not route through this component.

The blue/green pre-switch check and the ops dashboard read this surface.

## 3. Privileged intervention — the admin console

Emergency, audited, **never a remote shell**. `AdminConsole` (`ems-ops`) executes typed actions
(`inject_event`, `override_state`, `reset_sequence`, `disconnect_session`, …) against registered
injection targets. Every call requires **all three** of:

1. the `#superuser-inject` tag (three-layer firm/desk/user AND-gate),
2. a written rationale,
3. headroom under the per-identity rate limit (rolling minute).

Every attempt — granted or denied — lands on the admin journal (`AdminAction`) for the audit
stream. Four-eyes, where firm policy demands it, is composed by fronting the console with the
compliance override service.

## 4. Compliance blocks and overrides

A compliance **BLOCK** is not a reject: the operation suspends (`PendingCompliance`; FIX clients
see `OrdStatus=9`) and waits in the `ComplianceGate.pendingBlocks()` queue.

- Review the block: rule, rationale, the full per-rule audit trail of the decision.
- **Release** needs the override path on the block: the named tags (e.g.
  `#compliance-override-restricted-instrument`), N distinct sign-offs (four-eyes = 2), and a
  rationale per approval. One qualified denier closes a block.
- Releases are **time-bound** — an expired release re-runs the gate rather than resuming.

Lists (restricted / desk allow / watch) are versioned reference data; every mutation journals.
Risk limits amend only with a change reason + sign-off and the parameter version is pinned on
every decision.

## 5. Blue/green switchover (the 14.5 protocol)

One ACTIVE + one WARM-STANDBY lane per pod; the standby is *running and caught up*, not "ready to
start". The switchover is window-aware and never a one-way door:

1. Pre-checks (free to abort): asset-class maintenance window (override = emergency, logged) +
   standby self-check (golden replay matches, FSM version matches).
2. Old lane: drain new sessions → stop venue egress → snapshot at Archive position **P** →
   **release the lease at P** — the old lane is now FENCED.
3. New lane: must have replayed ≥ P → acquires the lease (new **fence token**) → resumes venues
   with fenced credentials → traffic switch → old lane becomes warm-standby.
4. If the standby is behind P, the protocol **rolls back**: the old lane re-acquires at P and
   resumes. Rollback in the healthy case is the same protocol run the other way.

Mechanics underneath:

- **Cluster lease** (`ClusterLeaseService`): exactly one holder per pod, heartbeat to retain,
  timeout = failure-path release, position-gated acquisition, monotonic fence tokens.
- **Fenced credentials** (`FencedCredentialService`): venue credentials are vault *references*
  bound to the issuing fence token; gateways check `authorizeEgress` on every outbound message,
  so a runaway old cluster cannot send an order — it keeps observer-only access for diagnosis.
- Every protocol step lands in a deterministic transcript for the audit record.

Preferred windows: crypto = none (must be sub-second invisible) · FX = weekend before Tokyo ·
futures = 17:00–18:00 NY · equities = after-hours. The console refuses non-emergency switches
outside the window.

## 6. Drills

| Cadence | Script | Exercises |
|---|---|---|
| Weekly | `scripts/drills/weekly-leader-kill.sh` | Raft leader failover within one cluster |
| Monthly | `scripts/drills/monthly-cold-start.sh` | Full state recovery from the Aeron Archive |
| Quarterly | `scripts/drills/quarterly-region-failover.sh` | Hard region loss → lease-fenced takeover → fingerprint + wire-smoke verification → RTO vs budget |

All three support `--dry-run`. The quarterly drill fails sign-off when the measured RTO exceeds
`RTO_BUDGET_SECONDS` (default 300).

## 7. Time/replay and configuration

- **Time/Replay server** (`ui/time-replay`, backend in `ems-ops`): drive the sim-clock, replay a
  log slice, verify byte-identical re-derivation. Replay determinism is the system's core
  guarantee — every service reads time through the clock interface, never the wall clock.
- **Config service** (`ui/config-service`): versioned configuration with schema-checked values;
  runtime config swaps at message boundaries without restarts. Maker-checker approvals are queued
  work (task 18.10).

## 8. Session recovery cheat-sheet

Both FIX edges and the API ride the same resumable channel (`SequenceRecoveryService`):

- Reconnect = `logon(declaredSeq)` **and** `resumeOutbound(fromSeq)` — logon alone leaves the
  client missing server-sent messages.
- If the resend buffer evicted the requested hole → the gateway issues **RESET**
  (`EMS-SES-2002`); do not silently resume over a gap.
- One TestRequest per silence window; liveness clears on any inbound traffic.
- The venue simulator honors ResendRequest with PossDup replay at original sequence numbers —
  use it to rehearse venue-side recovery (`./gradlew :ems-fix-bridge:runFixSimulator`).

## 9. Reject codes

Every reject carries an `EMS-<CAT>-<NNNN>` code from `schemas/reject-codes/catalog.yaml` — the
single source of truth (categories: SES, REF, PRM, ORD, RTE, AUT, CFG). Frequent operator
sightings: `EMS-SES-2001` sequence gap · `EMS-ORD-2510` duplicate ClOrdID (re-imported file) ·
`EMS-ORD-3003` wrong state for the operation · `EMS-PRM-3001` override tag required.
