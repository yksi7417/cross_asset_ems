# Plans

Implementation plans, one per sub-project. Each is written for someone with **zero context on this
codebase**: exact files, exact commands, test-first steps, and a commit at the end of every task.

Execute them with the `superpowers:subagent-driven-development` or `superpowers:executing-plans`
skill — each plan's header says so.

## The polyglot port

Six sub-projects. Sequenced so the gate exists before the code it governs. Full context, scope and
open questions: [`docs/polyglot/README.md`](../../polyglot/README.md).

| # | Plan | Delivers | Depends on | Status |
|---|---|---|---|---|
| 1 | [Gate skeleton](2026-07-30-polyglot-01-gate-skeleton.md) | `scripts/ci/gate.sh`, the anti-stub and study-guide checks, hooks rewired, CI restructured, ErrorProne + NullAway | — | **complete** |
| 2 | [Conformance harness + corpus](2026-07-30-polyglot-02-conformance-harness.md) | `ems-slice` in Java, the harness, the differ, the first corpus cases | 1 | not started |
| 3 | [Rust codegen emitter](2026-07-30-polyglot-03-rust-codegen-emitter.md) | Rust FSM emitter, three-way sync check, `rust/` workspace with `ems-fsm` | 1 | not started |
| 4 | [Rust slice](2026-07-30-polyglot-04-rust-slice.md) | The full order path in Rust, conformance green | 2, 3 | not started |
| 5 | [C++ slice](2026-07-30-polyglot-05-cpp-slice.md) | The full order path in C++, sanitizers and fuzzers green | 2 | not started |
| 6 | [Study guide](2026-07-30-polyglot-06-study-guide.md) | `70_concepts/idioms/` index, cross-language notes, completeness check | 4, 5 | not started |

Plan 1 carries a **"What actually shipped"** section at the end recording where the implementation
diverged from the plan and why. Later plans should gain the same section as they are executed —
a plan that silently disagrees with the tree is worse than no plan.

## Writing a new plan

Use the `superpowers:writing-plans` skill. The shape these follow:

- A header naming the goal, architecture and tech stack in a few sentences.
- **Global constraints** — the rules every task inherits, with exact values.
- A **file structure** table: every file the plan creates or modifies, and what it is responsible for.
- Tasks, each ending in an independently testable deliverable and a commit.
- Steps inside a task are single actions: write the failing test, run it and watch it fail,
  implement, run it and watch it pass, commit.
- A **definition of done** that someone else could check without asking you.

No placeholders. "Add appropriate error handling", "write tests for the above", "similar to Task 3"
are plan failures — the person executing may be reading tasks out of order and cannot see your
intent.

## Related

- [`docs/superpowers/specs/`](../specs/) — the designs these plans implement
- [`docs/polyglot/README.md`](../../polyglot/README.md) — the polyglot programme hub
- [`docs/polyglot/gate.md`](../../polyglot/gate.md) — the gate every plan ends against
