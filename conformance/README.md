# Conformance

This directory is how "the three implementations are the same system" becomes a testable claim
instead of a sentence in a README.

**Status: contract only.** The corpus and harness land in sub-project 2. This document is the
specification they are built to — written first on purpose, so the gate is not shaped to fit
whatever got built. See [`docs/polyglot/README.md`](../docs/polyglot/README.md) and
[ADR 0003](../docs/decisions/0003-shared-schemas-corpus-harness.md).

---

## The contract

Each implementation ships one binary, `ems-slice`, with an identical CLI:

```
ems-slice --input <journal> --output <journal> [--seed <n>]
```

It consumes an input event journal, runs the full cash-equity slice, and writes an output event
journal. **No network, no clock, no filesystem beyond those two paths.** It is a pure function from
input journal to output journal.

| Implementation | Binary | Built by |
|---|---|---|
| Java | `java/ems-it` → `ems-slice` launcher | Gradle |
| Rust | `rust/ems-slice` | Cargo |
| C++ | `cpp/ems-it` → `ems-slice` | CMake |

## Layout

```
conformance/
  corpus/<case-name>/
    input.jsonl      events fed in
    expected.jsonl   the output journal Java produces — the reference
    case.md          one paragraph: what this case covers and why it exists
  harness/
    run.sh           runs every case against every available implementation
    differ.py        byte-exact comparison, human-readable failure output
    fsm_coverage.py  asserts every FSM transition is reached by ≥1 case
```

Cases are versioned in git and reviewed like source.

## The gate

For every case and every implementation: run `ems-slice`, diff actual output against
`expected.jsonl` **byte-for-byte**. Any difference fails.

Java is the reference that generates `expected.jsonl`; C++ and Rust must match it exactly. This
proves *equivalence*, not *correctness* — a Java bug both ports faithfully reproduce passes. The
counterweight is review: corpus cases are checked against the FSM schemas and the reject-code
catalog, so a case can fail review for asserting the wrong behaviour.

Run it via the gate, never directly:

```bash
scripts/ci/gate.sh full     # includes the conformance and fsm-coverage steps
```

## Determinism

Byte-identical output across three languages requires every source of nondeterminism to be closed.
Most already are, which is what makes this tractable:

| Source | Resolution |
|---|---|
| Floating point | Already absent. `OrderRequest.price` is a scaled `long`; quantities are `long`. No floats anywhere in the order path. The gate's job is to keep it that way. |
| Wall clock | No implementation reads the system clock. Timestamps are logical, carried on input events. |
| Identifier generation | Deterministic generator seeded from `--seed`, default 0. Same seed, same IDs. |
| Hash-map iteration order | Any container whose iteration order can reach the output journal must be ordered: `TreeMap` / `std::map` / `BTreeMap`. Lint-enforced where the language allows (`clippy::disallowed_types`, a `clang-tidy` check). |
| Concurrency | The slice binary is single-threaded. Concurrency is a later, separately-gated concern. |
| Text encoding | UTF-8, `\n` line endings, no locale-dependent formatting. |

If a residual difference ever proves unclosable, the fallback is a canonicalizing differ. That
weakens the gate, so it would be recorded as a decision in `docs/decisions/` — never applied
quietly.

## Required corpus coverage

At minimum, cases must cover:

- happy path: new order through to fill;
- each validator rejection reason in `schemas/reject-codes/`;
- every transition in `schemas/fsm/order.fsm.yaml` and `schemas/fsm/route.fsm.yaml`;
- partial fills;
- cancel and amend;
- venue session drop mid-order;
- malformed input.

`harness/fsm_coverage.py` asserts the FSM-transition requirement mechanically, so coverage cannot
silently regress as transitions are added to the schemas.

## Adding a case

1. Write `corpus/<case-name>/input.jsonl`.
2. Generate the expectation from the reference implementation:
   `ems-slice --input input.jsonl --output expected.jsonl` (Java build).
3. **Read `expected.jsonl`.** Does it match what the FSM schema and reject-code catalog say should
   happen? If not, you have found a Java bug — fix that first. Blindly committing whatever Java
   emitted is how a bug becomes a specification.
4. Write `case.md`: what this case covers, and why it exists.
5. `scripts/ci/gate.sh full`.
