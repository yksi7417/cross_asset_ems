# Delegation Rules

Pick the cheapest tier that gets the work done. Cost goes up tier-by-tier; capability goes up too. Default to the cheapest tier that the task's tag in [[PLAN]] specifies, and escalate only on actual failure (test red, build red, integration fail).

## Tiers

### Tier 1 — Local Gemma 31B (`local`)
**Endpoint:** `http://localhost:8080/v1/chat/completions` (llm-router → llama.cpp). Model id `local`. **Cost: free, runs on the box.** No tool access, 262K context, no networking from inside the model.

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
- Migrating data formats (CSV → SBE-bound struct).

**Don't use for:**
- Multi-file architectural reasoning.
- Anything requiring tool use (file I/O, grep, edit).
- Complex debugging or root-cause analysis.
- Security-sensitive code review.
- FIX edge cases (Appendix D race conditions).
- FSM design or state-machine correctness.

**How to invoke** (one-shot prompt-to-text):
```bash
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-no-key-required" \
  -d '{
    "model": "local",
    "messages": [
      {"role": "system", "content": "<task-specific system prompt>"},
      {"role": "user", "content": "<the actual task>"}
    ],
    "max_tokens": 4096,
    "temperature": 0.2,
    "stream": false
  }' | jq -r .choices[0].message.content
```

Or invoke via the [`local-llm` skill](~/.claude/skills/local-llm/SKILL.md).

### Tier 2 — Gemini via Hermes (`gemini`)
**Route:** through Hermes Agent (`/home/yksi7417/.local/bin/hermes chat --provider google -z '<prompt>'`). **Cost: free quota** (rate-limited by Google's free tier). Larger context than Gemma; supports tool-use via Hermes' tool surface; can do multi-file research within a single invocation.

**Use for:**
- Code review of a finished module (find bugs, suggest improvements).
- Multi-file research questions ("show me how all the FIX adapters handle reject codes").
- Drafting documentation for completed code.
- Exploring trade-offs between approaches (pick algo for X).
- Cross-language porting (Java → C++ for hot-path components).

**Don't use for:**
- Anything that mutates the repo without Claude's review.
- Multi-step architectural decisions (you need Claude's full conversation context).
- Time-sensitive iterations (free-quota rate limits make iteration slow).

**How to invoke** (research-style, no repo mutation):
```bash
hermes chat --provider google -z 'You are reviewing a Java module at /path. Read it and report: (a) any obvious bugs, (b) any FIX-protocol concerns, (c) one suggested improvement. Reply under 400 words.'
```

Or use Hermes' streaming chat for longer interactions.

### Tier 3 — Claude Code (`claude`)
**Route:** the current Claude Code session. **Cost: highest per token.** Tool access, multi-file edits, full conversation context, the only tier that integrates with the architecture notes properly.

**Use for:**
- Architecture and design tasks.
- FSM design and verification.
- FIX Appendix D race-condition handling.
- Multi-file refactors.
- Integration tests requiring orchestration.
- Anything that needs to dispatch to other tiers and assemble results.

**Don't use for:**
- Tasks any cheaper tier can complete reliably.

## The Local-First Loop

Per global CLAUDE.md, for non-trivial coding tasks, follow this loop:

1. **Local draft.** Ask local Gemma to write the first version of the code (use Tier 1).
2. **Local test.** Ask Gemma to write basic tests for its own draft.
3. **Run tests.** Claude executes via Bash.
4. **Claude review (only if needed).** If tests fail or logic is wrong, Claude fixes it. The draft + error output is the starting context.

Why: Claude is much better at **fixing and critiquing** than writing from scratch. A concrete draft from Gemma gives Claude a precise starting point, which results in fewer iterations.

**Applied to PLAN tasks:** any task tagged `(local first draft, claude review)` follows this exact pattern.

## Tier-selection cheatsheet by task type

| Task | Tier |
|---|---|
| FSM YAML schema | claude |
| Per-asset SBE template, common shape | local first, claude review |
| Per-asset SBE template, gnarly (NDF fixing, TBA, IRS legs, CDS reference entity) | claude |
| Reject code catalog | local |
| Validator rule code | local first, claude review |
| Validator golden test generation | local |
| FIX adapter wire framing | local |
| FIX adapter business-logic (cancel/replace, reject handling) | claude |
| Architecture decisions | claude |
| End-to-end integration tests | claude |
| Boilerplate REST CRUD | local |
| Excel/CSV parsers | local |
| Calendars / day-count tables | local |
| Code review of completed module | gemini |
| Drafting docs from finished code | gemini |
| Multi-file research questions | gemini |
| Cross-language port | gemini |
| Grafana dashboard JSON templates | local |
| OTel SDK boilerplate | local |
| Best-ex audit logic | claude |
| Regulatory reporting submission profiles (per regulator) | claude (the rules are gnarly) |

## Escalation rule

If a task at tier N produces incorrect output that fails tests, **escalate to tier N+1 with the failed output as input**. Do not loop at the same tier — escalation is the loop.

Example:
1. Local Gemma drafts `marketaxess-fix-adapter.java`.
2. Tests fail on cancel-replace handling.
3. Escalate to Claude with the draft + test failure output.
4. Claude fixes; tests pass; commit; mark done.

If Claude fails on a task, escalate to Gemini via Hermes for a fresh perspective. If Gemini fails, mark the task `[~]` (blocked) and post a Hermes notification per [[HERMES]].

## See also

- [[PLAN]] (the task queue)
- [[LOOP]] (how the /goal loop consumes this)
- [[HERMES]] (notifications when a task escalates or stalls)
