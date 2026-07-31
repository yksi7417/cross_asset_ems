# Contributing

How to pick up work, implement a task, and keep the loop running.

---

## How the work is organised

The implementation plan lives in [`IMPL/PLAN.md`](IMPL/PLAN.md) — a 14-phase, ~150-task queue. Each task has:

- A phase-scoped ID (`1.3`, `2.7`, …)
- A short description
- A delegation tier — who should do it (local model, Gemini, Claude)
- A `← blocks:` prerequisite list

Tasks are ordered. The first `[ ]` line whose prerequisites are all `[x]` is the next one to pick up.

---

## Picking up a task

1. Find the first unblocked `[ ]` task in `IMPL/PLAN.md`.
2. Mark it `[~]` and commit: `task(<id>): claim <description>`.
3. Implement it — see [`IMPL/DELEGATION.md`](IMPL/DELEGATION.md) for which tier to use (local Gemma draft → Claude review, vs straight Claude, vs straight local).
4. Run the relevant tests (see [`DEVELOPMENT.md`](DEVELOPMENT.md) for commands).
5. Commit the work: `feat(<id>): <what changed>`.
6. Mark `[x] (sha)` in PLAN and advance [`IMPL/CHECKPOINT.md`](IMPL/CHECKPOINT.md).

On phase boundary completion, post a Hermes Discord notification per [`IMPL/HERMES.md`](IMPL/HERMES.md).

---

## The `/goal` loop (Claude Code automation)

The loop in [`IMPL/LOOP.md`](IMPL/LOOP.md) automates the above for Claude Code sessions:

1. Read the `/goal` text (first code block in `IMPL/LOOP.md`).
2. Paste it into Claude Code's `/goal` command.
3. The loop picks the next unblocked task → delegates per tier → implements → commits → marks `[x]` → advances `CHECKPOINT.md` → repeats.

Sessions wrap up gracefully when commit-count or context thresholds are hit. The next session resumes from `CHECKPOINT.md` automatically.

---

## Before you push: one command

```bash
scripts/ci/gate.sh fast
```

That is the same script CI runs, and `.githooks/pre-push` runs it for you. There is no separate
list of "things CI checks" to keep in your head — if the gate is green, the fast lane in CI is
green. Run `scripts/ci/gate.sh full` to also cover what a PR to `main` will run.

Full reference: [`docs/polyglot/gate.md`](docs/polyglot/gate.md).

---

## The polyglot port

The cash-equity order path is being reimplemented in Rust and C++ alongside the Java reference,
with a byte-exact conformance gate proving the three behave identically. If you are picking that
work up, start at [`docs/polyglot/README.md`](docs/polyglot/README.md) — it carries the scope, the
sub-project sequence, the plans and the open questions. Its work is tracked there, not in
`IMPL/PLAN.md`.

---

## Deferring work

**When you consciously decide not to do something now, register it in the same commit that defers
it.** Not in the commit message, not in a PR comment, not in your head.

The register is [`docs/polyglot/TODO.md`](docs/polyglot/TODO.md). An entry needs three things:

```markdown
## T-7 — Short imperative title

**Why:** what forced the deferral, and what breaks if it is never done.

**Done when:** a condition someone else could check without asking you.
```

`scripts/ci/checks/deferred_work.py` enforces both sections, and runs in every gate lane. An entry
without a **Why** cannot be prioritised; one without a **Done when** never closes, because nobody
can tell whether it is finished.

**If a comment in code or docs points at deferred work, mark it:**

```
# DEFERRED: T-4 — pinned by digest once the publish workflow exists
```

The check is bidirectional: a `DEFERRED:` marker with no register entry fails, and so does an entry
that has lost its sections. So a comment cannot outlive the work it points at — when T-4 lands, the
marker has to go with it or the build breaks.

Use `DEFERRED: T-n` rather than a bare `T-n`: `CorporateActionState.java` already says
`LOCKED (T-1 before ex_date)`, meaning T minus one day.

### What belongs there, and what does not

The register is **decided-and-deferred work**, not a wish list.

| Belongs | Does not |
|---|---|
| "We decided to do X, but after Y" | "It'd be nice if X" |
| A skipped gate step with a stated reason | A vague quality concern |
| Honest debt — a rule bent with a reason | Work already in the sequencing table |

If it turns out an item is not needed, **delete it with a note saying why**. An item nobody intends
to do teaches readers the register is fiction, and then the real items get skipped too.

### Why this is enforced rather than trusted

This repository ran the experiment. `io.crossasset.ems.core.clock.Clock` documented "components
must never call `System.currentTimeMillis()` directly" in task 3.6. Nothing checked it. Four
services called it anyway — *while their own javadoc claimed the clock was injected*. The rule was
true, written down, and ignored for months.

Same reasoning as [ADR 0005](docs/decisions/0005-study-guide-with-enforced-anchors.md) applies to
the study guide: an unenforced rule rots into a lie, and a rotted register is worse than none —
it tells you the work is tracked when it is not.

### Related practice: decide in the open

A deferral is a decision. If it is a *design* decision — scope, tooling, an accepted trade-off —
write an ADR in [`docs/decisions/`](docs/decisions/) and link it from the register entry. ADRs
0007–0009 were written exactly this way, each settling a question that had been sitting in a
"open questions" list.

And when a question is answered, **take it out of the questions list**. An answered question left
under a "questions" heading gets re-litigated by the next reader who finds it.

---

## Commit conventions

Pre-commit and commit-msg hooks enforce **Conventional Commits**:

```text
<type>(<scope>): <subject under 72 chars>
```

Valid types: `feat fix docs style refactor perf test build ci chore task impl arch venues glossary home notes index revert`

When implementing a task, use its ID as the scope:

```text
feat(1.3): SBE codegen for OrderNew message
task(1.3): claim codegen pipeline
```

The hooks also guard against:

- Accidental secrets (`AKIA…`, PEM keys, `sk-*`, `ghp_*`)
- Files > 10 MB
- Trailing whitespace + CRLF

Bypass with `git commit --no-verify` only after explicit review.

---

## Delegation tiers

See [`IMPL/DELEGATION.md`](IMPL/DELEGATION.md) for the full matrix. In short:

| Tier | When | Model |
|---|---|---|
| `(local)` | Boilerplate, scaffolding, simple codegen, fixtures | Local Gemma (via `local-llm` skill) |
| `(local first draft, claude review)` | Non-trivial code where a concrete starting point helps | Local draft → Claude fixes |
| `(claude)` | Multi-file design, complex debugging, security review, FSM logic | Claude directly |

---

## Adding an architecture note

Notes live in `80_architecture/arch-<slug>.md`. Use existing notes as templates — they follow a common shape: lead paragraph (what + why), components, data model, mechanics, worked examples, anti-patterns, `See also`.

After writing:

1. Add the note to [`00_index/architecture-index.md`](00_index/architecture-index.md) under the matching section.
2. Add wikilinks **into** the new note from any older notes that should reference it.
3. Commit: `arch: add arch-<slug>`.

---

## Adding a task to the plan

`IMPL/PLAN.md` is canonical. To insert a new task:

1. Find the right phase and insert before any tasks that should follow it (the loop reads in file order).
2. Use the format: `- [ ] **<phase>.<n>** Description (delegation-tier) ← blocks: <prereq-ids>`
3. If the new task changes phase boundary semantics, update [`IMPL/HERMES.md`](IMPL/HERMES.md) and [`IMPL/CHECKPOINT.md`](IMPL/CHECKPOINT.md).

---

## Three docs, three audiences

| File | Audience |
|---|---|
| [`README.md`](README.md) | Anyone — 5-minute get-started on Fedora/Podman |
| [`SETUP.md`](SETUP.md) | Developers on any platform — full prerequisites, dev container, Obsidian |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Contributors — build commands, project structure, coding rules, CI |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | This file — task workflow, delegation, commit conventions |
| [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) | Anyone — understanding the design, architecture, asset classes |
