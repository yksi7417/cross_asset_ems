# Delegation Rules

Pick the cheapest tier that gets the work done. Cost/capability go up tier-by-tier. Default to the tier the task's tag in [[PLAN]] specifies, and escalate only on actual failure (test red, build red, integration fail).

Work is driven through two tools depending on tier:

| Tag | Tier | Tool | Model | Role |
|---|---|---|---|---|
| `(gemma)` | 1 — cheapest | **OpenCode** → Google | **Gemma 4 31B** | boilerplate, scaffolding, fixtures |
| `(minimax)` | 2 — mid | **OpenCode** → OpenCode Zen | **MiniMax 2.7 / 3** | review, research, drafting, ports |
| `(sonnet)` | 3 — strong | **OpenCode** → GitHub Copilot | **Sonnet 4.6** | architecture, correctness, orchestration |
| `(opus)` | 4 — apex | **Claude Code** (this session) | **Opus 4.x** | crown jewels: replay determinism, consensus, FIX races |

> Tiers 1–3 use OpenCode — switch the model with `/models` or `opencode run -m <provider>/<model>`.
> **Tier 4 (`(opus)`) is run directly in Claude Code**, not in OpenCode. Claude Code already has
> the Opus-backed `advisor` tool and full file/bash access; no provider switch needed.
> Use `/model opus` in the Claude Code session to confirm the model, then proceed.

---

## Tier 1 — Gemma 4 31B · `(gemma)`

**Provider:** Google (in OpenCode). Smallest/cheapest. Treat as a fast first-draft engine with limited reasoning depth.

**Use for:**
- Boilerplate code, scaffolding, templates.
- Unit tests for straightforward functions (one-input → one-output checks).
- FIX adapter boilerplate (4.4 wire format, standard tags).
- Docstrings, code comments, inline summaries.
- Simple code transforms (rename, reformat, convert style).
- Static configuration tables (calendars, day counts, tick sizes).
- Initial schema sketches for asset-class instrument templates.
- Sample data / fixtures / mocks.
- Simple regex or SQL generation.
- Validator rule tables and golden-test fixtures.
- Grafana dashboard JSON, OTel SDK boilerplate.

**Don't use for:**
- Multi-file architectural reasoning.
- Complex debugging or root-cause analysis.
- Security-sensitive code review.
- FIX edge cases (Appendix D race conditions).
- FSM design or state-machine correctness.

## Tier 2 — MiniMax 2.7/3 · `(minimax)`

**Provider:** OpenCode Zen. Strong mid model — larger context and better reasoning than Gemma; cheaper than Sonnet. Good at reviewing and researching across files.

**Use for:**
- Code review of a finished module (find bugs, suggest improvements).
- Multi-file research questions ("how do all the FIX adapters handle reject codes?").
- Drafting documentation for completed code.
- Exploring trade-offs between approaches (pick algo for X).
- Cross-language porting (Java → C++ for hot-path components).
- Second-opinion pass before a `(sonnet)` task, to cut Sonnet iterations.

**Don't use for:**
- Final sign-off on security or correctness-critical paths (escalate to Sonnet).
- Multi-step architectural decisions that need the full design-vault context.

## Tier 3 — Sonnet 4.6 · `(sonnet)`

**Provider:** GitHub Copilot. Strongest tier; highest cost. The only tier that should make architectural commitments or final correctness judgements.

**Use for:**
- Architecture and design tasks.
- FSM design and verification.
- FIX Appendix D race-condition handling.
- Multi-file refactors.
- Integration tests requiring orchestration.
- Reviewing / finishing weaker-model drafts (the local-first loop, below).
- Anything that dispatches to other tiers and assembles results.

**Don't use for:**
- Tasks a cheaper tier can complete reliably.

## Tier 4 — Opus 4.x · `(opus)` · **Claude Code only**

**Tool:** Claude Code (this session). Do NOT use OpenCode for opus tasks.

**Reserved for tasks where a subtle error is silent and catastrophic:**
- Event-sourcing replay determinism (wrong output = silent data corruption on replay).
- Distributed consensus (Aeron Cluster 3-node Raft, split-brain edge cases).
- FIX Appendix D cancel/replace race conditions (silent double-fill, wrong state).
- Codegen pipeline correctness (generated Java/C++ used everywhere — wrong template = systemic bugs).

**How to run:**
1. Open (or continue) a Claude Code session.
2. Call `advisor()` before committing to any design — the advisor is backed by Opus.
3. Implement, test, commit here in Claude Code.
4. Do not mark the task `[x]` until tests pass in this session.

**Don't dilute this tier.** If you're unsure whether a task needs Opus, it's `(sonnet)`.

## The first-draft loop (cheap draft → Sonnet review)

For non-trivial coding tasks:

1. **Draft (Gemma or MiniMax).** Ask the cheapest adequate tier for a first version + basic tests.
2. **Run tests** (yourself, in OpenCode / shell).
3. **Sonnet review (only if needed).** If tests fail or logic is wrong, hand Sonnet the draft + error output. Sonnet is far better at *fixing and critiquing* than writing from scratch, so a concrete draft means fewer expensive iterations.

**Applied to PLAN tasks:** any task tagged `(gemma first draft, sonnet review)` (or similar) follows this pattern.

## Tier-selection cheatsheet by task type

| Task | Tier |
|---|---|
| FSM YAML schema | sonnet |
| Per-asset SBE template, common shape | gemma draft, sonnet review |
| Per-asset SBE template, gnarly (NDF fixing, TBA, IRS legs, CDS reference entity) | sonnet |
| Reject code catalog | gemma |
| Validator rule code | gemma draft, sonnet review |
| Validator golden test generation | gemma |
| FIX adapter wire framing | gemma |
| FIX adapter business-logic (cancel/replace, reject handling) | sonnet |
| Architecture decisions | sonnet |
| End-to-end integration tests | sonnet |
| Boilerplate REST CRUD | gemma |
| Excel/CSV parsers | gemma |
| Calendars / day-count tables | gemma |
| Code review of completed module | minimax |
| Drafting docs from finished code | minimax |
| Multi-file research questions | minimax |
| Cross-language port | minimax |
| Grafana dashboard JSON templates | gemma |
| OTel SDK boilerplate | gemma |
| Best-ex audit logic | sonnet |
| Regulatory reporting submission profiles (per regulator) | sonnet (the rules are gnarly) |

## Escalation rule

If a task at tier N produces output that fails tests, **escalate to tier N+1 with the failed output as input** (Gemma → MiniMax → Sonnet → Claude Code/Opus). Don't loop at the same tier — escalation is the loop. If Opus (Claude Code) fails, mark the task `[!]` (blocked) and note it in [[CHECKPOINT]] — this is a genuine blocker requiring human design input.

## See also

- [[PLAN]] (the task queue)
- [[LOOP]] (how the /goal loop consumes this)
- [[CHECKPOINT]] (current cursor + blocked tasks)
