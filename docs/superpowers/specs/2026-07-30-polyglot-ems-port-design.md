# Polyglot EMS Port — Design

**Date:** 2026-07-30
**Status:** Draft — awaiting review
**Supersedes (in part):** the "2026-06-07 — replaced Rust with C++" decision record in `cpp/README.md`

---

## 1. Context

### 1.1 What exists today

| Tree | State |
|---|---|
| `java/` | 684 source files across 15 modules. The working system: 195-task plan code-complete, ~1,750 unit tests, cash equity and FX trading-complete, trader desktop, event sourcing, byte-identical replay. |
| `cpp/` | 15 module directories, **21 files total** — every one a `#pragma once` stub or a generated FSM header. No behaviour. CI configures, builds and `ctest`s it, and passes trivially because there is nothing to compile. |
| `rust/` | Does not exist. Removed 2026-06-07. |
| `schemas/` | Single source of truth already: SBE XML (24 instrument schemas + envelope + event), 5 FSM YAML definitions, reject-code catalog, validator rules, FIX dictionaries, reference data. |
| `tools/codegen/fsm_codegen.py` | 1,300 lines. Emits Java and C++ from `schemas/fsm/*.fsm.yaml`. No Rust emitter. |
| `tests/e2e`, `tests/integration`, `tests/smoke` | Directories exist. Contents: `.gitkeep`. Empty. |

Two facts matter more than the rest:

1. **The schema-first spine already exists.** A shared integration layer is a seam that is already
   cut, not a new abstraction to invent.
2. **The `cpp/` tree is a cautionary tale in-repo.** Fifteen module stubs and a green CI check that
   proves nothing. Any plan that produces more of that is a failure, and the gate design in §4 is
   written specifically to make that outcome impossible.

### 1.2 The ADR being reversed

`cpp/README.md` records that Rust was dropped on 2026-06-07 for four reasons. Three were
preference or staffing (`Aeron's Java/C++ maturity`, `SBE C++ codegen parity`, `team hot-path
expertise is C++`). One was a genuine technical objection:

> Rust's `aeron-rs` crate was marked TBD in `Cargo.toml` with a note that it might need a custom
> FFI layer — meaning the transport binding was unproven.

That objection is still unanswered and this design answers it directly (§3.5) rather than
overruling it. A superseding record goes in `docs/decisions/` — the original stays put, because
it is load-bearing context for why `cpp/` is shaped the way it is.

### 1.3 Purpose

This project has two deliverables that constrain each other:

- **A working system**, ported to two more languages with provable behavioural equivalence.
- **A study artifact.** The port exists to demonstrate technical depth, so any idiom that is not
  immediately obvious must be explained where a reader will find it.

The second requirement is why the design invests in enforcement (§4, §5) rather than
convention. An unenforced study guide rots into a lie within weeks, and a rotted study guide is
worse than none.

---

## 2. Goals and non-goals

### Goals

- One complete cash-equity order path implemented to production depth in Java, C++ and Rust.
- Behavioural equivalence between the three, proven by a blocking CI gate — not asserted.
- Memory-safety defects in the C++ port caught mechanically, not by review.
- Every non-trivial idiom in any of the three languages documented, linked to its real usage site,
  and kept honest by CI.

### Non-goals

- Feature parity with the full Java tree. Explicitly rejected in §3.2.
- Replacing or deprecating the Java implementation. Java remains the reference.
- Performance competition between the three. Benchmarks may follow later; they are not in scope
  and are not a gate.
- Porting the trader desktop, the observability stack, or any non-equity asset class.

---

## 3. Design

### 3.1 Repository layout

```
schemas/               # unchanged — single source of truth for wire + FSM
tools/codegen/         # fsm_codegen.py gains a Rust emitter (--rust-only)
java/                  # unchanged. Reference implementation.
cpp/                   # existing 15 stubs; slice modules gain real source
rust/                  # NEW cargo workspace, module names 1:1 with java/ and cpp/
conformance/           # NEW — see §3.4
  corpus/              #   versioned golden input/output journals
  harness/             #   language-agnostic runner + differ
  README.md
scripts/ci/gate.sh     # NEW — the single gate entry point (§4.4)
docs/decisions/        # NEW ADR superseding the 2026-06-07 Rust removal
70_concepts/idioms/    # NEW study-guide vault section (§5)
```

The `rust/` workspace mirrors the existing module names exactly (`ems-core`, `ems-fsm`,
`ems-transport`, `ems-aaa`, `ems-validator`, `ems-oms`, `ems-venue-connectivity`,
`ems-posttrade`). Only the modules the slice touches are created; unlike `cpp/`, **no empty
module stubs are committed.** A module directory appears when it has behaviour and tests.

### 3.2 The vertical slice

One asset class (cash equity), one order path, full production depth. Traced from the actual Java
call path, not invented:

| Stage | Java reference | Ported |
|---|---|---|
| Order intake | `ems-oms`: `OrderRequest`, `StagedOrder`, `StageResult` | Yes |
| Authorization | `ems-aaa`: subject resolution + entitlement decision | Yes — authz decision only, no SSO/SCIM |
| Validation | `ems-validator`: `LayeredValidatorPipeline`, `ValidationLayer`, `ValidationRequest`, `ValidationResult`, `ValidatorPipeline` | Yes — all 5 |
| Order lifecycle | `ems-fsm`: generated from `schemas/fsm/order.fsm.yaml` | Yes — code-generated, never hand-written |
| Staging + routing | `ems-oms`: `StagedOrderManager`, `RouteManager`, `InMemoryRouteManager`, `Route`, `RouteRequest`, `RouteResult` | Yes |
| Route lifecycle | `ems-fsm`: generated from `schemas/fsm/route.fsm.yaml` | Yes — code-generated |
| Venue edge | `ems-venue-connectivity`: single-destination FIX out, ExecutionReport in, session state | Yes |
| Fill handling | `ems-posttrade`: allocation | Yes — allocation only; drop-copy is a recorded stub |
| Transport | `ems-transport` | Yes — as an interface with two implementations (§3.5) |
| Event sourcing + replay | `ems-core`: journal append, replay | Yes — this is what the conformance gate exercises |

**Out of scope, and staying Java-only:** SOR, algo wheel, sweep, broker algos, multi-leg, FX
netting, compliance gate, override desk, positions, pricing, analytics, borrow/locate, STP,
confirmation, regulatory reporting, CAT, TCA, surveillance, jurisdiction router, IOI, quote
multicast, ops console, blue/green switchover, cluster lease, the trader desktop, and the six
non-equity asset classes.

Rough size: 45–60 source files per new language, plus tests.

**Why a deep slice rather than a broad one.** A broad, shallow port produces exactly the `cpp/`
tree that already exists — many modules, no behaviour, a green check that means nothing. A deep
slice produces three real implementations of the same non-trivial logic, which is the only
configuration where a differential gate has anything to bite on and the only one where the study
guide has real idioms to document.

### 3.3 What is shared

Nothing at runtime. The three implementations share three artifacts:

**(a) `schemas/` as the single source of truth.** `tools/codegen/fsm_codegen.py` gains a Rust
emitter alongside the existing Java and C++ ones. The FSM sync check already in CI
(`--java-only` then `git diff --exit-code`) extends to all three languages, so a hand-edit to any
generated FSM fails the build.

**(b) A versioned golden corpus.** See §3.4.

**(c) One language-agnostic conformance harness.** See §3.4.

Deliberately **not** shared: any runtime library, FFI boundary, or common core. A shared C ABI
core would guarantee agreement by construction — and in doing so would remove the three
implementations the project exists to demonstrate, leaving one implementation and two wrappers.

### 3.4 The conformance gate

This is the mechanism that makes "same system" a testable claim.

**Wire contract.** Each implementation ships one binary, `ems-slice`, with an identical CLI:

```
ems-slice --input <journal> --output <journal> [--seed <n>]
```

It consumes an input event journal, runs the full slice, and writes an output event journal.
No network, no clock, no filesystem beyond those two paths. This is a pure function from input
journal to output journal.

**Corpus format.** `conformance/corpus/<case-name>/` contains `input.jsonl` (the events fed in),
`expected.jsonl` (the output journal Java produces), and `case.md` (one paragraph: what this case
covers and why it exists). Cases are versioned in git and reviewed like source.

**The gate.** For every case and every implementation: run `ems-slice`, diff actual against
`expected.jsonl` byte-for-byte. Any difference fails. Java is the reference that generates
`expected.jsonl`; C++ and Rust must match it exactly.

**Determinism.** Byte-identical output across three languages needs every source of nondeterminism
closed. Most are already closed by existing design, which is why this is tractable:

| Source | Resolution |
|---|---|
| Floating point | **Already solved.** `OrderRequest.price` is a scaled `long`; quantities are `long`. No floats anywhere in the order path. Nothing to specify — the property just has to be preserved, and the gate enforces that. |
| Wall clock | No implementation reads the system clock. Timestamps are logical, carried on input events. |
| Identifier generation | Deterministic generator seeded from `--seed`, defaulting to 0. Same seed, same IDs. |
| Hash-map iteration order | Any container whose iteration order can reach the output journal must be ordered (`TreeMap` / `std::map` / `BTreeMap`). Enforced by lint where the language allows: `clippy::disallowed_types` for Rust, a `clang-tidy` check for C++. |
| Concurrency | The slice binary is single-threaded. Concurrency is a later, separately-gated concern. |
| Text encoding | UTF-8, `\n` line endings, no locale-dependent formatting. |

**Corpus coverage.** Cases must cover, at minimum: happy-path new order to fill; each validator
rejection reason in `schemas/reject-codes/`; every transition in `order.fsm.yaml` and
`route.fsm.yaml`; partial fills; cancel and amend; venue session drop mid-order; and malformed
input. A CI check asserts every FSM transition in the schemas is reached by at least one corpus
case — so FSM coverage cannot silently regress.

### 3.5 Transport, and the aeron-rs objection

`ems-transport` becomes an interface in each language, with two implementations:

- **`JournalTransport`** — reads and writes journal files. Deterministic, no media driver, no
  network. This is what the conformance gate uses, and what all three implementations ship first.
- **`AeronTransport`** — the live path. Java has it already. C++ uses the mature Aeron C++ client.
  Rust's remains an isolated, optional, separately-scheduled task.

This resolves the ADR's objection rather than overruling it: the unproven `aeron-rs` binding is no
longer on the critical path of anything. If it proves out, Rust joins the live transport; if it
does not, the Rust port is unaffected and complete on its own terms. A swappable transport is also
the correct design independently — it is what makes byte-identical replay testable at all, which
is a capability the Java tree already claims and would benefit from.

An FFI wrap of the C++ Aeron client was rejected: it would put every Rust message across an
`unsafe` boundary, defeating the `forbid(unsafe_code)` posture in §4.2 and gutting the
memory-safety half of the demonstration.

---

## 4. Quality gates

### 4.1 C++

Memory corruption is the named risk, so the C++ gate is the heaviest.

- **Warnings:** `-Wall -Wextra -Wpedantic -Werror` (already in `cpp/CMakeLists.txt`), plus
  `-Wconversion -Wshadow -Wold-style-cast -Wnon-virtual-dtor -Wcast-qual`, plus `-Wuseless-cast`
  guarded on GCC (Clang does not have it, and `-Werror` makes an unknown warning flag fatal).
- **Static analysis:** `clang-tidy` with `bugprone-*`, `cppcoreguidelines-*`, `modernize-*`,
  `performance-*`, `readability-*`, `cert-*`, `misc-*`. Blocking. Suppressions require an inline
  justification comment; a bare `NOLINT` fails review.
- **Include hygiene:** `include-what-you-use`.
- **Hardening:** `_GLIBCXX_ASSERTIONS`, `_FORTIFY_SOURCE=3`, `-fstack-protector-strong`,
  `-D_LIBCPP_HARDENING_MODE=fast` under libc++.
- **Sanitizer matrix** — separate build configurations, all blocking:
  - ASan + UBSan (`-fsanitize=address,undefined -fno-sanitize-recover=all`)
  - TSan
  - MSan (libc++ built with MSan, or the job is honestly marked as not running rather than
    silently skipped)
- **Fuzzing:** libFuzzer targets on every parser — FIX message decode, SBE decode, journal
  parse. Seeded from the conformance corpus. Short per-PR run, long nightly run, corpus persisted
  as a CI artifact so found inputs accumulate.
- **Valgrind:** full slice run under memcheck, nightly.

### 4.2 Rust

- `#![forbid(unsafe_code)]` at every crate root. Exactly one crate may relax it — the optional
  `ems-transport-aeron` FFI crate, if it is ever built — and that exception is recorded in the
  crate docs and reviewed line by line.
- `clippy::pedantic` and `clippy::nursery` with `-D warnings`. `clippy::disallowed_types` bans
  `HashMap`/`HashSet` on output-reaching paths (§3.4).
- `cargo fmt --check`.
- **Miri** on the unit test suite — catches UB the borrow checker cannot, and is the honest
  counterweight to the C++ sanitizer matrix.
- `cargo-deny` for advisories, licenses and duplicate dependency versions.
- `cargo-fuzz` on the same three parsers as C++, sharing the same seed corpus.

### 4.3 Java

Existing gates stay (Spotless, JaCoCo aggregate, FSM sync check). Added: **ErrorProne** and
**NullAway** — the tree already annotates with `org.jspecify`, so NullAway has something to
enforce and currently nothing checks it.

### 4.4 The single entry point

`scripts/ci/gate.sh` is the only gate. It takes a lane argument:

```
scripts/ci/gate.sh fast      # compile + unit tests + fmt/lint, all 3 languages
scripts/ci/gate.sh full      # fast + sanitizers + conformance + study-guide check
scripts/ci/gate.sh nightly   # full + MSan + Valgrind + long fuzz
```

Every CI job invokes it. `.githooks/pre-push` invokes `fast`. The hook and the CI job run **the
same script with the same dependency set in the same container image** — a
`.devcontainer`-derived image pinned by digest.

`.githooks/pre-commit` keeps its current job (regenerate FSM Java, then `spotlessApply` and
re-stage) and extends it to the new languages: `fsm_codegen.py --cpp-only --rust-only`, then
`clang-format` and `cargo fmt`, re-staging what they touch. It stays a fast auto-fixer and does
**not** call `gate.sh` — a commit hook that runs sanitizers is a commit hook developers disable.
`.githooks/commit-msg` is untouched.

This is deliberate. Your standing rule is that CI failures must be reproducible locally, and that
local/CI environment divergence is the most common cause of surprise breakage. Today
`.githooks/pre-push` runs shellcheck and nothing else, while CI runs Gradle, CMake, Spotless and
schema lint — so most CI failures are currently unreproducible before push. One script closes
that gap permanently, and adding a language cannot reopen it.

### 4.5 Cross-language gate

Runs after all three build:

1. **Codegen sync** — regenerate FSMs for all three languages, `git diff --exit-code`.
2. **Conformance** — every corpus case against every implementation, byte-exact (§3.4).
3. **FSM coverage** — every transition in `schemas/fsm/*.fsm.yaml` reached by ≥1 corpus case.
4. **Study-guide integrity** — §5.
5. **Anti-stub check** — every module directory in `cpp/` and `rust/` that the slice claims must
   contain compiled behaviour and at least one test that asserts something. A module cannot be
   listed as done while it is a header stub. This check exists because the repo already contains
   fifteen counter-examples.

---

## 5. Study guide

### 5.1 Shape

A new vault section, `70_concepts/idioms/`, one note per idiom, sitting alongside the existing
`70_concepts/glossary/`. Each note states:

- **The idiom** — what it is, in general terms.
- **Why it was needed here** — the specific constraint in this codebase that forced it.
- **What the naive version gets wrong** — the failure mode, concretely. This is the part that
  makes it a study guide rather than a description.
- **Where it lives** — a `file:line` link to the real usage site.
- **Cross-language contrast** — how the other two languages solve the same problem, when they do
  it differently. This is where most of the teaching value is: three solutions to one problem,
  side by side.

### 5.2 Enforcement

The source carries a marker at the usage site:

```cpp
// STUDY: crtp-static-dispatch
```

CI asserts, bidirectionally:

- every `STUDY:` marker in source has a matching note in `70_concepts/idioms/`;
- every note's `file:line` anchor still resolves to a line carrying that marker.

So the guide cannot drift from the code, and moving code without updating the note fails the
build. The enforcement mechanism is itself part of what the project demonstrates.

### 5.3 Expected candidates

Recorded as expectations to be confirmed during implementation, not as a commitment to a fixed
list. Notes are written when the idiom actually lands.

- **C++:** CRTP vs. virtual dispatch on the FSM hot path; `std::expected` error propagation
  without exceptions; arena / monotonic allocation and why `std::pmr` was or was not used; strict
  aliasing and `std::bit_cast` in SBE decode; move semantics and the rule of zero/five; iterator
  invalidation in the route table; `std::span` over raw pointer-plus-length at boundaries.
- **Rust:** lifetime elision and where it stops working; `Cow` in the FIX decoder; typestate
  encoding of FSM states so illegal transitions do not compile; newtype wrappers over scaled
  integer prices; `From`/`TryFrom` conversion chains; why a given structure needed `Rc<RefCell<>>`
  and what that costs; sealed traits at module boundaries.
- **Cross-language:** how each represents "an order that may or may not have a price"
  (`@Nullable Long` vs. `std::optional<int64_t>` vs. `Option<Price>`); how each encodes FSM state
  exhaustiveness; how each handles a validation failure without exceptions.

---

## 6. Sequencing

Six sub-projects. Each gets its own plan and its own PR; each ends with the full gate green. The
ordering is chosen so the gate exists **before** the code it governs — otherwise the gate is
written to fit whatever was built, which is how `cpp/` got fifteen green stubs.

| # | Sub-project | Delivers | Depends on |
|---|---|---|---|
| 1 | **Gate skeleton** | `scripts/ci/gate.sh`, container image, hooks rewired, CI restructured to call it, anti-stub check, ErrorProne/NullAway on Java. No new language code. | — |
| 2 | **Conformance harness + corpus** | `conformance/`, the `ems-slice` CLI contract, harness, differ, first corpus cases generated from Java. Java passes its own gate. | 1 |
| 3 | **Rust codegen emitter** | Rust emitter in `fsm_codegen.py`, three-way sync check, `rust/` workspace with `ems-fsm` only. | 1 |
| 4 | **Rust slice** | Full slice in Rust. Gate 4.2 + conformance green. | 2, 3 |
| 5 | **C++ slice** | Full slice in C++, replacing the stubs. Gate 4.1 + conformance green. | 2 |
| 6 | **Study guide** | `70_concepts/idioms/`, notes for every idiom found in 4 and 5, integrity check. | 4, 5 |

**Rust before C++** is deliberate. Rust's compiler catches at build time the class of error the
C++ sanitizer matrix catches at run time. Doing Rust first surfaces the design's aliasing and
ownership problems early and cheaply, and the C++ port then benefits from having already had those
questions answered. The reverse order pays for the same lessons in sanitizer debugging.

Sub-project 6 is listed last but the notes are written **as** idioms land in 4 and 5, not
retroactively. The sub-project is the section index, the cross-language contrast notes, and the
integrity check.

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| The ports become stubs like `cpp/` is today | Anti-stub check (§4.5) and conformance gate. A module cannot be "done" without behaviour a corpus case exercises. |
| Byte-identical output proves unachievable across three languages | Nondeterminism sources are enumerated and closed (§3.4), and the hardest one — floating point — is already absent from the codebase. If a residual difference appears, the fallback is a canonicalizing differ, which weakens the gate and would be recorded as a decision, not applied quietly. |
| Full sanitizer matrix makes CI too slow to live with | Lanes (§4.4). PRs run `fast` plus ASan/UBSan; MSan, Valgrind and long fuzz run nightly. Revisit if PR feedback exceeds ~10 minutes. |
| Scope creeps back toward full parity | §3.2 lists exclusions explicitly. Anything added is a new sub-project with its own spec. |
| Java reference has bugs the ports faithfully reproduce | Not a defect of this design — conformance proves equivalence, not correctness. Corpus cases are reviewed against the FSM schemas and reject-code catalog, so a case can fail review for asserting wrong behaviour. |
| `aeron-rs` never proves out | By construction, nothing depends on it (§3.5). |

---

## 8. Decisions recorded

| ID | Decision | Alternatives rejected |
|---|---|---|
| D1 | Vertical slice at full depth, cash equity only | Hot-path-only (nothing for an e2e gate to test); full 7-class parity (1,000+ files, becomes stubs); core-spine-only (no end-to-end order). |
| D2 | Share schemas + golden corpus + one harness; nothing at runtime | Shared C ABI core (leaves one implementation and two wrappers); live polyglot Aeron cluster (flaky as a blocking gate). |
| D3 | Full defensive gate stack, blocking per-PR with a nightly heavy lane | Sanitizers-without-fuzzing (misses parser bugs); lint-only (misses use-after-free and races entirely). |
| D4 | Study guide as vault notes with CI-enforced code anchors | Standalone runnable examples (drift from real usage); comments only (not browsable as a unit). |
| D5 | Abstract transport; journal-file impl for the gate, Aeron optional for Rust | `aeron-rs` first (all risk up front); FFI-wrap the C++ client (breaks `forbid(unsafe_code)`); drop Aeron entirely (loses the latency story the original ADR cared about). |

---

## 9. Open questions for review

1. **Slice boundary.** §3.2 puts AAA in and post-trade drop-copy out. Both are judgment calls.
2. **Rust-before-C++.** Justified in §6, but it front-loads the language with no existing tree.
3. **MSan.** Requires an MSan-instrumented libc++, which is real setup cost. Acceptable to defer
   it to nightly-only, or drop it and rely on ASan + Valgrind?
4. **Corpus authority.** Java generates `expected.jsonl`, making Java correct by definition. The
   alternative — hand-authored expectations from the schemas — is stricter and considerably more
   work. Worth it?
