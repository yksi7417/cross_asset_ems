# Polyglot EMS Port — program hub

**Start here** if you are picking this work up cold.

One sentence: *the cash-equity order path is being implemented three times — Java (already done),
Rust, and C++ — and a byte-exact conformance gate proves the three behave identically.*

| | |
|---|---|
| Design spec | [`docs/superpowers/specs/2026-07-30-polyglot-ems-port-design.md`](../superpowers/specs/2026-07-30-polyglot-ems-port-design.md) |
| Decisions | ADRs [0001](../decisions/0001-reinstate-rust-three-language-port.md) – [0006](../decisions/0006-abstract-transport-journal-first.md) |
| Gate reference | [`docs/polyglot/gate.md`](gate.md) |
| Conformance corpus | [`conformance/README.md`](../../conformance/README.md) |
| Study guide | [`70_concepts/idioms/`](../../70_concepts/idioms/) |
| Plans | [`docs/superpowers/plans/`](../superpowers/plans/README.md) — one per sub-project, indexed |

---

## Why this exists

The repo already contains a cautionary tale. `cpp/` has fifteen module directories, twenty-one
files, and a green CI check — and no behaviour whatsoever. Every file is a `#pragma once` stub or a
generated FSM header. CI configures, builds and `ctest`s it, and passes trivially because there is
nothing to compile.

Every structural decision in this program is aimed at making that outcome impossible a second
time:

- **A deep slice, not a broad one** (ADR 0002) — one order path at production depth beats fifteen
  module stubs.
- **A byte-exact differential gate** (ADR 0003) — equivalence is tested, not claimed.
- **An anti-stub check** (ADR 0004) — a module cannot be listed as done while it is a header stub.
- **No empty module stubs committed** — a directory under `rust/` appears when it has behaviour and
  tests, never before.
- **The gate is built before the code it governs** (see Sequencing) — otherwise the gate gets
  written to fit whatever was built.

## What is in scope

The cash-equity order path, end to end:

```
order intake → authorization → validation (5 layers) → order FSM
    → staging + routing → route FSM → venue edge (FIX out / ExecutionReport in)
    → fill handling (allocation) → journal append + replay
```

Everything else stays Java-only. The full exclusion list is in
[ADR 0002](../decisions/0002-vertical-slice-cash-equity.md) — SOR, algos, multi-leg, FX netting,
compliance, positions, pricing, analytics, STP, regulatory reporting, surveillance, the trader
desktop, and the six non-equity asset classes.

**Java is the reference implementation.** It is not being replaced, deprecated or frozen. It also
generates the expected output the other two are checked against.

## Sequencing

Six sub-projects. Each has its own plan and its own PR; each ends with the full gate green.

| # | Sub-project | Delivers | Depends on | Status |
|---|---|---|---|---|
| 1 | **[Gate skeleton](../superpowers/plans/2026-07-30-polyglot-01-gate-skeleton.md)** | `scripts/ci/gate.sh`, hooks rewired, CI restructured to call it, anti-stub and study-guide checks, ErrorProne/NullAway on Java. No new language code. | — | **complete** |
| 2 | **[Conformance harness + corpus](../superpowers/plans/2026-07-30-polyglot-02-conformance-harness.md)** | `conformance/`, the `ems-slice` CLI contract, harness, differ, first corpus cases generated from Java. | 1 | not started |
| 3 | **[Rust codegen emitter](../superpowers/plans/2026-07-30-polyglot-03-rust-codegen-emitter.md)** | Rust emitter in `fsm_codegen.py`, three-way sync check, `rust/` workspace with `ems-fsm` only. | 1 | not started |
| 4 | **[Rust slice](../superpowers/plans/2026-07-30-polyglot-04-rust-slice.md)** | Full slice in Rust. Rust gate + conformance green. | 2, 3 | not started |
| 5 | **[C++ slice](../superpowers/plans/2026-07-30-polyglot-05-cpp-slice.md)** | Full slice in C++, replacing the stubs. C++ gate + conformance green. | 2 | not started |
| 6 | **[Study guide](../superpowers/plans/2026-07-30-polyglot-06-study-guide.md)** | `70_concepts/idioms/`, notes for every idiom found in 4 and 5, integrity check. | 4, 5 | not started |

**Rust before C++ is deliberate.** Rust's compiler catches at build time the class of error the
C++ sanitizer matrix catches at run time. Doing Rust first surfaces the design's aliasing and
ownership problems early and cheaply; the C++ port then benefits from having had those questions
already answered. The reverse order pays for the same lessons in sanitizer debugging.

Sub-project 6 is listed last, but the notes are written **as** idioms land in 4 and 5. The
sub-project itself is the section index, the cross-language contrast notes, and the integrity
check.

## Repository layout

```
schemas/               # unchanged — single source of truth for wire + FSM
tools/codegen/         # fsm_codegen.py; gains a Rust emitter (--rust-only) in sub-project 3
java/                  # unchanged. Reference implementation.
cpp/                   # existing 15 stubs; slice modules gain real source in sub-project 5
rust/                  # NEW cargo workspace, module names 1:1 with java/ and cpp/
conformance/           # NEW — corpus + harness + differ
  corpus/<case>/       #   input.jsonl, expected.jsonl, case.md
  harness/             #   language-agnostic runner + differ
scripts/ci/gate.sh     # NEW — the single gate entry point
docs/decisions/        # ADRs 0001–0006
docs/polyglot/         # this hub + gate reference
70_concepts/idioms/    # study-guide vault section
```

Module names in `rust/` mirror the existing ones exactly: `ems-core`, `ems-fsm`, `ems-transport`,
`ems-aaa`, `ems-validator`, `ems-oms`, `ems-venue-connectivity`, `ems-posttrade`. Only the modules
the slice touches are created.

## The gate in one paragraph

`scripts/ci/gate.sh <lane>` is the only gate. `fast` is compile + unit tests + format/lint across
all three languages and is what `.githooks/pre-push` runs. `full` adds sanitizers, conformance and
the study-guide check, and is what PR CI runs. `nightly` adds MSan, Valgrind and long fuzz. CI and
the hook run the same script with the same dependency set — installed by one script,
`scripts/ci/install-toolchain.sh`, that both the devcontainer image and every CI job run — so a CI
failure is reproducible locally by running one command. The design spec asks for a digest-pinned
container image on both sides; that is not yet built, and [`gate.md`](gate.md) records the gap
rather than implying otherwise.

## How to pick this up

1. Read the [design spec](../superpowers/specs/2026-07-30-polyglot-ems-port-design.md) once, end to
   end. It is the source of everything else here.
2. Read [ADR 0001](../decisions/0001-reinstate-rust-three-language-port.md) for why Rust came back,
   and skim 0002–0006 for the shape of each decision.
3. Run `scripts/ci/gate.sh fast` to see where the tree stands. It takes ~3 minutes cold and tells
   you, step by step, what is enforced and what is still skipping.
4. Find the first sub-project in the table above that is not done, open its plan, and execute it
   task by task. Sub-project 2 is next.

Each plan is written for someone with zero context on this codebase: exact files, exact commands,
test-first steps, and a commit at the end of every task.

## Known risks

| Risk | Mitigation |
|---|---|
| The ports become stubs like `cpp/` is today | Anti-stub check + conformance gate. A module cannot be "done" without behaviour a corpus case exercises. |
| Byte-identical output proves unachievable across three languages | Nondeterminism sources enumerated and closed (ADR 0003); the hardest one — floating point — is already absent. A residual difference would be handled by a canonicalizing differ, recorded as a decision, never applied quietly. |
| The sanitizer matrix makes CI too slow to live with | Lanes. PRs run `fast` plus ASan/UBSan; MSan, Valgrind and long fuzz are nightly. Revisit above ~10 min PR feedback. |
| Scope creeps back toward full parity | ADR 0002 lists exclusions explicitly. Anything added is a new sub-project with its own spec. |
| Java reference has bugs the ports faithfully reproduce | Conformance proves equivalence, not correctness. Corpus cases are reviewed against the FSM schemas and reject-code catalog. |
| `aeron-rs` never proves out | By construction nothing depends on it (ADR 0006). |

## Open questions

Carried from the design spec §9, unresolved:

1. **Slice boundary.** AAA is in, post-trade drop-copy is out. Both are judgment calls.
2. **MSan.** Requires an MSan-instrumented libc++ — real setup cost. Defer to nightly-only, or drop
   it and rely on ASan + Valgrind?
3. **Corpus authority.** Java generates `expected.jsonl`, making Java correct by definition.
   Hand-authored expectations from the schemas would be stricter and considerably more work.
