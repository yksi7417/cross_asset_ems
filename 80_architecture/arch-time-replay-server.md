---
type: architecture
layer: infrastructure
status: draft
tags: [architecture/infrastructure]
---

# Time Server & Replay Server

Components do not read `System.currentTimeMillis()` directly. They consume a **clock interface** that the **time server** drives. In live mode the clock is wall-clock-aligned with monotonic offset publication; in replay mode the **replay server** advances it deterministically. This lets historical sessions reproduce decisions byte-identically across machines.

## The clock interface

```
Clock {
  now() -> Timestamp        // monotonic, wall-aligned in live mode
  schedule(at, callback)    // single-shot
  schedule_periodic(start, period, callback)
}
```

Every component receives a `Clock` at construction. Tests and replay supply a `SimulatedClock`.

## Time server (live mode)

- Publishes a **wall-clock schedule with offset corrections** on a multicast topic, see [[arch-sbe-aeron-transport]].
- Each subscribing component computes `now() = monotonic_local + published_offset`.
- Survives a single physical host's clock drift — corrections re-sync within bounded latency.
- Health is observable per [[arch-jmx-introspection]].

```
ClockTick {
  reference_time: uint64       // server's authoritative wall time
  publish_seq:    uint64
  offset_hint:    int64        // adjustment for receivers
}
```

## Replay server

Consumes a slice of the [[arch-event-sourcing|event log]] and emits events back into the system in **sequence and clock order**:

```
ReplayConfig {
  start_event_id, end_event_id
  speed: REAL_TIME | AS_FAST_AS_POSSIBLE | FIXED_RATE
  clock_mode: SIMULATED        // forces SimulatedClock everywhere
  rule_set_version: ...        // pin the rule definitions
  code_version_target: ...     // the binary being verified
}
```

In replay mode the `Clock.now()` returned by all components is the **simulated time stamped on the current event**, not wall clock. Schedulers (e.g. heartbeats, timers in [[arch-automation-layer]] rules) fire on simulated time.

## Determinism rules (recap)

For replays to be byte-identical:

1. No direct wall-clock reads. Inject `Clock`.
2. No nondeterministic identifiers from time. Seed deterministically.
3. No inbound that isn't from the replay log (network calls to external venues are stubbed — see [[arch-venue-connectivity]] "shadow mode").
4. Map iteration order externalised → use sorted containers when output is observable.

## Use cases

| Use case | Mode |
|---|---|
| Post-mortem of a production incident | Replay over the incident window, pin old rules + new code, observe divergence. |
| Regression test for a rule change | Replay a representative day, diff event streams. |
| Backtest of a new automation rule | Replay with the rule bound; compare against null run. |
| Compliance regulator request | Replay with read-only state derivation. |

## Observability

- The clock interface is the **only** source of time in business logic.
- The time server publishes its own health (`uptime`, `last_correction_magnitude`) on [[arch-jmx-introspection]].
- A divergence detector on the event store flags any event whose `occurred_at` is implausibly distant from the publishing component's clock — a replay or wall-clock bug.

## See also

- [[arch-event-sourcing]]
- [[arch-sbe-aeron-transport]]
- [[arch-jmx-introspection]]
- [[arch-automation-layer]]
- [[arch-venue-connectivity]]
