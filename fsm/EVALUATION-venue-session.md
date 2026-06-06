# Evaluation — Local LLM Trial: Venue Session FSM YAML

**Trial date**: 2026-06-06
**Local model**: Gemma-4-31B (UD-Q4_K_XL.gguf) via llama.cpp at `localhost:8080`
**Reviewer**: Claude Opus 4.7

## Goal

Demonstrate the local-first loop documented in `~/.claude/CLAUDE.md`: delegate routine structured-output work to the local model, then refine. Specific task: generate a starter FSM YAML for the venue-session state machine following the convention in `80_architecture/arch-fix-fsm-design.md`.

## Process

1. **First attempt** (initial system prompt, max_tokens=4096, temp=0.2): local llm-router returned `500 Internal Server Error`. Suspected: prompt size or service issue.
2. **Retry** (smaller prompt, max_tokens=2048, temp=0.1): succeeded. 2,809 chars / 118 lines of YAML.

Per the skill's "do not retry more than once" rule, after the first retry succeeded I proceeded to evaluation rather than further retries.

## Quantitative result

Asked for at minimum:

| Element | Asked | Got from Gemma | Verdict |
|---|---|---|---|
| States | 8 | 6 | Under |
| Events | 14 | 12 | Under |
| Transitions | 18 | 14 | Under |

Asked Gemma to parse cleanly as YAML — it did. `yaml.safe_load()` succeeds.

## Qualitative review

### What Gemma got right

- Format follows the requested shape closely (states/events/transitions sections).
- States are reasonable basic categories: `Disconnected`, `Connecting`, `LogonSent`, `Active`, `ResendInProgress`, `LogoutInProgress`.
- Happy-path transitions are correctly modelled: connect → logon → active → logout.
- Identifies key error paths: tcp_failed, logon_reject, unexpected_disconnect.
- Names effects sensibly (`initiate_tcp_handshake`, `reset_heartbeat_timer`).
- Uses lowercase snake_case for events as requested.

### What Gemma got wrong or missed

1. **No TestRequest sub-state.** FIX convention is that a missed heartbeat triggers a TestRequest (`35=1`), and only after the test-request timeout does the session declare disconnect. Gemma's draft goes `Active → Disconnected` directly on `heartbeat_missed`, which is over-aggressive and not FIX-compliant.

2. **Inbound ResendRequest not modelled.** When the venue's seq is behind ours, the venue sends us a `35=2` ResendRequest and we must reply with the relevant `35=4 GapFill`. Gemma only modelled the outbound case (we send 35=2 on `gap_detected`).

3. **SequenceReset (35=4) handling missing.** A real FIX session has explicit semantics for inbound SequenceReset with `GapFill (123=Y)` vs. `Reset (123=N)`; the latter requires the new seq to be **higher** than the current expected — a critical FIX rule.

4. **No `context_schema` despite being in the prompt.** The prompt explicitly requested a `context_schema` block; Gemma omitted it.

5. **No `guard` field in transitions.** Some transitions need guards (e.g. SequenceReset must check `event.new_seq > current_expected_seq` per FIX rule). Gemma omitted guards entirely.

6. **`logout_request` listed as `direction: out`** but used as `on:` (an inbound event triggering a transition). Semantic confusion — for the FSM, "request from operator to logout" is inbound to the FSM; "send 35=5 Logout to venue" is the outbound effect.

7. **No version-string discipline.** Gemma wrote `version: 1.0.0` (semver-ish string) where the convention from `arch-fix-fsm-design` is `version: 1` (integer).

## Refinement performed

I refined Gemma's draft into `venue_session.fsm.yaml` adding:

- `TestRequestSent` sub-state with the proper `heartbeat_missed → send 35=1 → test_request_response | test_request_timeout` flow.
- `inbound_resend_request` event + transition for the venue-asks-us case.
- `SequenceResetting` state + handling of `sequence_reset_received` with a `validate_reset_seq_not_lower` effect (placeholder for the guard logic).
- `context_schema` block per the original prompt.
- Normalised version to integer.
- Logout direction-of-event corrected (`operator` source for `logout_request`).
- Inbound `logout_received` event added (venue can initiate logout too).
- Reconnect scheduling effect on `tcp_failed`.

The verbatim Gemma output is preserved at `venue_session.fsm.gemma-draft.yaml` for comparison.

## Cost/benefit assessment

**Net positive.** Gemma produced a syntactically correct draft in seconds; my refinement effort was a tight pass adding the few FIX-protocol-aware bits Gemma missed. Writing from scratch would have cost roughly the same Claude-tokens as the refinement, but the local draft anchored the work — the *shape* of the file matched conventions, the obvious states + transitions were present, and refining a draft is faster than greenfield drafting (matches the skill's premise: "Claude is better at fixing and critiquing than writing from scratch").

**Conclusion**: Use the local model for structured-output drafting tasks where the format is well-specified and the domain knowledge for refinement lives with Claude. For tasks requiring deep FIX-protocol awareness (or any deep domain expertise) the local draft is a starting scaffold, not a finished product.

## Recommendations for future trials

- **Be more explicit in the prompt about ranges and missing-element types** (e.g. "include both inbound and outbound ResendRequest events").
- **Provide a one-paragraph FIX-session protocol summary** in the system prompt next time — Gemma's gap on TestRequest suggests it didn't apply the convention spontaneously.
- **For format strictness** (versions as ints, etc.), provide a one-line example in the system prompt.
- **For larger artefacts**, chunk the prompt — e.g. ask for states first, then events, then transitions in separate calls.

## See also

- The shared FSM design these definitions conform to: `[[arch-fix-fsm-design]]`.
- The sibling Order FSM and Route FSM live alongside under `fsm/` (planned).
- Skill: `~/.claude/skills/local-llm/SKILL.md`.
