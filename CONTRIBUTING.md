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
