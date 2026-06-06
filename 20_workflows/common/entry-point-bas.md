---
type: workflow
category: common
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/common]
---

# Entry Point (BAS)

BAS — Bloomberg AAA-Server (or analogue: the authentication / authorization / accounting service at the edge of the EMS). The **single ingress** any client (UI, FIX, automation rule, scheduler, integration partner) hits before any business operation. Owns identity establishment, [[arch-sequence-recovery|sequence tracking]], and the initial routing into the API surface.

## Purpose

Make sure every operation that reaches the OMS has been:

- Authenticated (who is calling).
- Mapped to a known `(firm, desk, user)` identity per [[arch-firm-desk-user]].
- Bound to a sequence-tracked session per [[arch-sequence-recovery]].
- Throttled / rate-limited per session policy.
- Logged at the request boundary for audit.

Nothing past this point operates on unauthenticated traffic.

## Trigger / Entry Point

- Every inbound API connection (REST, websocket, gRPC, native).
- Every inbound FIX session (logon arrives here first).
- Every automation rule fires through here too — automation is a peer caller, not privileged.

## Actors

- Client / caller.
- BAS service (auth + session management).
- [[arch-fix-api-bridge]] (for FIX origin).
- [[arch-api-first|API]] downstream.

## Steps

```mermaid
sequenceDiagram
  participant C as Client
  participant B as BAS Entry Point
  participant ID as Identity Service<br/>[[arch-firm-desk-user]]
  participant S as Session Layer<br/>[[arch-sequence-recovery]]
  participant API as API Surface

  C->>B: connect + credentials (+ next_expected_seq for FIX)
  B->>ID: authenticate, resolve identity
  ID-->>B: Identity{firm, desk, user, effective_tags}
  B->>S: establish session, init seq numbers
  S-->>B: session_id
  B-->>C: LogonAccepted (session_id)
  Note over C,API: subsequent operations carry session_id; flow through API
  C->>B: operation request (carries session_id, client_seq)
  B->>S: seq check
  S-->>B: in-window | gap | duplicate
  alt in-window
    B->>API: forward operation
  else gap
    B->>C: ResendRequest
  else duplicate
    B->>C: replay prior Response
  end
```

## Inputs

- Credentials (API token / cert / FIX logon).
- Session intent (kind: API / FIX, capabilities).
- For FIX: declared `next_expected_seq`.

## Outputs / Side Effects

- `SessionLogon` event ([[arch-event-sourcing]]).
- Subsequent operations have established identity and seq tracking.
- Possible `SessionLogonRejected` events (auth fail, version mismatch, banned).

## Edge Cases & Nuances

- **Token rotation.** API tokens may rotate; BAS supports key-id-versioned tokens with overlap windows.
- **Inbound from automation.** Automation rules don't authenticate per-call; they run with their binder's identity through a system-internal session pre-established at startup.
- **Connection storm.** Throttling at BAS level. Excess returns `EMS-SES-1010 logon_throttled`.
- **Mid-session credential revoke.** Compliance can revoke a user mid-session; BAS receives a kill signal, sends `Logout`, the [[arch-sequence-recovery|session]] terminates.
- **Failed handshake during gap recovery.** If a logon attempt is in `RecoveringFromGap`, new connections from the same session-id are queued or rejected per policy.
- **Multi-region routing.** Some firms route BAS by region; cross-region session migration is supported via session-state replication.

## API mapping

```
operation: logon                        # establishes session
items: [{ credentials, capabilities, next_expected_seq?, fix_dictionary_version? }]

operation: logout
items: [{ reason? }]

operation: session_info                 # introspection
returns: { session_id, established_at, seq_state, identity }
```

## Validator codes touched

`EMS-SES-1001..1010` (all logon-related codes from [[arch-sequence-recovery]]).

## Permissions

- Logon: must present valid credentials. No tag needed for the act of logging on.
- Capability negotiation requires per-capability tags downstream.

## Related

- [[arch-api-first]] · [[arch-fix-api-bridge]] · [[arch-sequence-recovery]] · [[arch-firm-desk-user]] · [[arch-jmx-introspection]]
- [[actions-framework]] · [[order-manager]] · [[validation]] · [[permissioning-config]]
