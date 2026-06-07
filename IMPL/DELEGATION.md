# Delegation Rules

Pick the cheapest tier that gets the work done. Cost/capability go up tier-by-tier. Default to the tier the task's tag in [[PLAN]] specifies, and escalate only on actual failure (test red, build red, integration fail).

Work is now driven through **OpenCode**, with one model pinned per connected provider:

| Tag | Tier | OpenCode provider | Model | Role |
|---|---|---|---|---|
| `(gemma)` | 1 — cheapest | **Google** | **Gemma 4 31B** | boilerplate, scaffolding, fixtures |
| `(minimax)` | 2 — mid | **OpenCode Zen** | **MiniMax 2.7 / 3** | review, research, drafting, ports |
| `(sonnet)` | 3 — strongest | **GitHub Copilot** | **Sonnet 4.6** | architecture, correctness, orchestration |

> Switch models with OpenCode's model selector (the `/models` picker, or `opencode run -m <provider>/<model>`). The provider→model bindings above are configured once in your OpenCode auth/config; the tags below name the *tier*, not a hard-coded model — re-point a provider and the tiers still hold.

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

If a task at tier N produces output that fails tests, **escalate to tier N+1 with the failed output as input** (Gemma → MiniMax → Sonnet). Don't loop at the same tier — escalation is the loop. If Sonnet fails, mark the task `[~]` (blocked) and note it in [[CHECKPOINT]].

## See also

- [[PLAN]] (the task queue)
- [[LOOP]] (how the /goal loop consumes this)
- [[CHECKPOINT]] (current cursor + blocked tasks)
