---
type: architecture
layer: session
status: draft
tags: [architecture/session]
---

# Session, Sequence Numbers & Recovery

Every client (API, FIX, automation) has a **session** with monotonic sequence numbers and heartbeats. The same recovery semantics apply across surfaces — without running a third-party FIX engine.

## Session lifecycle

```
LOGON  →  ACTIVE  →  GAP_DETECTED  →  RECOVERY  →  ACTIVE
                                 ↘
                                  LOGOUT
```

A `LOGON` carries credentials, declared `next_expected_seq`, and capabilities. The session layer:

1. Authenticates and establishes [[arch-firm-desk-user|identity]].
2. Compares declared seq to its own record. Issues a `RESEND` if the client missed any, or a `RESET` if catastrophic mismatch.
3. Begins heartbeats.

## Sequence numbers — two scopes

| Scope | Field | Purpose |
|---|---|---|
| **Session** | `session_seq` | Per-direction monotonic. Detects gaps and duplicates. FIX-equivalent. |
| **Request** | `request_id` (UUID) | Client-assigned, idempotency key for any [[arch-api-first|batch operation]]. Replays of the same `request_id` are recognized and not double-applied. |

## Heartbeats

- Configurable interval (typical: 30s).
- A `TEST_REQUEST` is issued when an expected heartbeat is late.
- Two missed heartbeats → session marked `STALE` → recovery on reconnect.

## Gap detection

The session layer keeps a `next_expected_seq` per direction. Any inbound message with:

- `seq < expected` → **possible duplicate**. The request layer checks `request_id`; if seen, the prior `Response` is replayed; if not seen, the message is logged and dropped.
- `seq > expected` → **gap**. The session enters `GAP_DETECTED`, issues `RESEND(from, to)`, and queues subsequent messages until the gap closes.

## Recovery sources

| Recovery type | Source |
|---|---|
| Outbound resend to client | Session's per-client outbound buffer (sized by `MaxResendWindow`). |
| Inbound resend by client | Client must replay from its own outbound buffer. |
| Cold-restart (post-crash) | [[arch-event-sourcing|Event log]] replays state; session sequence numbers are restored from a periodic `SessionSnapshot` event. |

## No physical FIX engine

We deliberately do **not** run QuickFIX, OnixS, or similar. Reasons:

- Two implementations to keep in sync (FIX engine + API session manager).
- Engine-internal state (e.g. `MessageStore`, `MsgSeqNum` files) breaks the [[arch-event-sourcing|single audit log]] guarantee.
- FIX-engine recovery semantics differ subtly across implementations; owning the session layer means one set of rules.

Instead, the FIX bridge (see [[arch-fix-api-bridge]]) translates wire-level FIX to API operations, and the **same session layer** services both. The wire format differs; the recovery contract does not.

## Privileged operations

Two operations are restricted to admin identities:

- `inject_event(...)` — manually emit an event into the log (planned `[[arch-jmx-introspection]]`).
- `reset_sequence(session_id, to_seq)` — reset a session's expected seq number. Used only for triaged corruption; audited.

## Reject codes (subset)

| Code | Meaning |
|---|---|
| `EMS-SES-1001` | Logon failed — invalid credentials |
| `EMS-SES-1002` | Logon failed — sequence too low (possible replay) |
| `EMS-SES-1003` | Logon failed — duplicate session |
| `EMS-SES-2001` | Gap detected — resend issued |
| `EMS-SES-3001` | Heartbeat timeout |
| `EMS-SES-3002` | Forced disconnect by admin |

## See also

- [[arch-api-first]]
- [[arch-fix-api-bridge]]
- [[arch-sbe-aeron-transport]]
- [[arch-event-sourcing]]
- [[arch-validator]]
