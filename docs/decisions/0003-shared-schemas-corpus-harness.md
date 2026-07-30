# 0003 — Share schemas, a golden corpus and one harness; share nothing at runtime

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: ADR [0001](0001-reinstate-rust-three-language-port.md), ADR [0002](0002-vertical-slice-cash-equity.md), [[arch-event-sourcing]]

## Context

Three implementations of the same system need a definition of "same". Without one, equivalence is
a claim in a README that decays silently. With the wrong one — a shared runtime core behind an FFI
boundary — equivalence is guaranteed by construction and therefore proves nothing, because there
is then one implementation and two wrappers.

The repo already has the seam that makes the right answer available: `schemas/` is a genuine
single source of truth (24 SBE instrument schemas, an envelope and event schema, 5 FSM YAML
definitions, a reject-code catalog, validator rules, FIX dictionaries, reference data), and
`tools/codegen/fsm_codegen.py` already emits Java and C++ from it.

## Decision

The three implementations share exactly three artifacts, and **nothing at runtime**.

**(a) `schemas/` as the single source of truth.** `tools/codegen/fsm_codegen.py` gains a Rust
emitter (`--rust-only`) alongside the existing Java and C++ ones. The FSM sync check already in CI
(`--java-only`, then `git diff --exit-code`) extends to all three languages, so a hand-edit to any
generated FSM fails the build.

**(b) A versioned golden corpus.** `conformance/corpus/<case-name>/` holds `input.jsonl` (events
fed in), `expected.jsonl` (the output journal Java produces), and `case.md` (one paragraph: what
this case covers and why it exists). Cases are reviewed like source.

**(c) One language-agnostic conformance harness.** Each implementation ships one binary,
`ems-slice`, with an identical CLI:

```
ems-slice --input <journal> --output <journal> [--seed <n>]
```

It consumes an input event journal, runs the full slice, writes an output event journal. No
network, no clock, no filesystem beyond those two paths — a pure function from input journal to
output journal. The harness runs every case against every implementation and diffs the output
against `expected.jsonl` **byte-for-byte**. Any difference fails.

Determinism sources are enumerated and closed:

| Source | Resolution |
|---|---|
| Floating point | Already absent. `OrderRequest.price` is a scaled `long`; quantities are `long`. The gate preserves the property. |
| Wall clock | No implementation reads the system clock. Timestamps are logical, carried on input events. |
| Identifier generation | Deterministic generator seeded from `--seed`, default 0. |
| Hash-map iteration order | Any container whose iteration order can reach the output journal must be ordered (`TreeMap` / `std::map` / `BTreeMap`). Lint-enforced where the language allows. |
| Concurrency | The slice binary is single-threaded. Concurrency is a later, separately-gated concern. |
| Text encoding | UTF-8, `\n` line endings, no locale-dependent formatting. |

## Consequences

- "Same system" becomes a testable claim, checked on every PR, at the byte level.
- Java is the corpus authority: it generates `expected.jsonl`. Conformance therefore proves
  *equivalence*, not *correctness* — a Java bug faithfully reproduced by both ports passes.
  Mitigated by reviewing corpus cases against the FSM schemas and reject-code catalog, so a case
  can fail review for asserting wrong behaviour.
- A residual byte difference that cannot be closed would be handled by a canonicalizing differ.
  That weakens the gate, so it would be recorded as a new decision — never applied quietly.
- A CI check asserts every FSM transition in `schemas/fsm/*.fsm.yaml` is reached by at least one
  corpus case, so FSM coverage cannot silently regress.

## Alternatives considered

**A shared C ABI core.** Rejected: it would guarantee agreement by construction and, in doing so,
delete the three implementations the project exists to demonstrate.

**A live polyglot Aeron cluster as the equivalence test.** Rejected: timing-dependent and flaky,
and a flaky blocking gate is a gate that gets disabled. Journal-file transport (ADR 0006) gives
the same coverage deterministically.

**Hand-authored `expected.jsonl` derived from the schemas instead of from Java.** Stricter — it
would catch Java bugs too — and considerably more work. Deferred, not rejected; recorded as an
open question in the design spec.
