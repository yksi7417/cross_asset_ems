---
type: architecture
layer: ops
status: draft
tags: [architecture/ops]
---

# Component Introspection (JMX-style)

Every component exposes a standardized **introspection surface** — read-side state inspection plus privileged write-side state injection. The model is JMX-like in spirit but rides the same [[arch-sbe-aeron-transport|SBE / Aeron transport]] as everything else.

## Goals

- **Diagnose live systems** without attaching a debugger.
- **Reproduce production state** in test labs by exporting and re-importing snapshots.
- **Privileged intervention** in emergencies — superuser event injection, controlled session resets — fully audited.

## Read surface

Every component implements:

```
describe_state() -> StateDescriptor       // schema of inspectable state
dump_state(selector?) -> StateSnapshot    // current values
list_metrics() -> [Metric]                // counters, gauges, histograms
trace(level: INFO|DEBUG, duration) -> stream<TraceEvent>
```

State descriptors are typed and stable (versioned like the rest of the [[arch-sbe-aeron-transport|SBE schema]]).

## Write surface (privileged)

```
inject_event(event)                       // emit an event as if from this component
override_state(key, value)                // direct state mutation (last resort)
reset_sequence(session_id, to)            // see [[arch-sequence-recovery]]
disable_rule(rule_id, reason)
disconnect_session(session_id, reason)
```

All write operations:

- Require an identity with the `#superuser-inject` tag (see [[arch-tag-permissions]]).
- Emit an `AdminAction` event on the admin stream of [[arch-event-sourcing]].
- Are subject to rate limits and a four-eyes policy where configured.

## Standard metrics every component exposes

| Metric | Type | Notes |
|---|---|---|
| `messages_in_total{template}` | counter | per SBE template |
| `messages_out_total{template}` | counter | |
| `process_lag_ns` | histogram | end-to-end |
| `aeron_publication_back_pressure` | counter | |
| `event_log_lag` | gauge | events behind authoritative log |
| `sessions_active` | gauge | for components that hold sessions |
| `validation_rejects_total{code}` | counter | per [[arch-validator]] code |

## Health checks

```
health() -> { status: GREEN|YELLOW|RED, components: [ ... ], reason: string }
```

`YELLOW` is allowed for degraded but functional — e.g. one venue adapter reconnecting. `RED` means do not route through this component.

## Audit shape

```
AdminAction {
  who:         Identity
  action:      enum
  target:      string
  payload:     SBE payload (if write)
  rationale:   string                       // required for write ops
  occurred_at: timestamp
}
```

## Discovery

Components self-register at startup with a discovery service (also Aeron-based). UIs and CLIs can enumerate available components and their introspection schemas without hard-coding.

## What this is not

- Not a generic remote-shell. Operations are explicit, typed, and audited.
- Not a SQL-over-state interface. Read state is structured per the descriptor.
- Not a hot-reload mechanism. Code updates go through normal deployment.

## See also

- [[arch-event-sourcing]]
- [[arch-tag-permissions]]
- [[arch-sequence-recovery]]
- [[arch-validator]]
- [[arch-time-replay-server]]
