# 0001 — Reinstate Rust as a third implementation language

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: [[arch-sbe-aeron-transport]], [[arch-fix-fsm-design]], `docs/superpowers/specs/2026-07-30-polyglot-ems-port-design.md`

Supersedes: the "2026-06-07 — replaced Rust with C++" decision record in `cpp/README.md`
(that record stays in place — it is load-bearing context for why `cpp/` is shaped the way it is).

## Context

On 2026-06-07 the Rust tree was deleted and C++ took its place. Four reasons were recorded.
Three were preference or staffing:

- Aeron's Java/C++ client maturity.
- SBE C++ codegen parity with Java.
- Team hot-path expertise is C++, not Rust.

One was a genuine technical objection:

> Rust's `aeron-rs` crate was marked TBD in `Cargo.toml` with a note that it might need a custom
> FFI layer — meaning the transport binding was unproven.

Fourteen months later the state of the repo is:

| Tree | State |
|---|---|
| `java/` | 684 source files, 15 modules, ~1,750 unit tests. The working system. |
| `cpp/` | 15 module directories, 21 files — every one a `#pragma once` stub or generated FSM header. CI builds and `ctest`s it and passes because there is nothing to compile. |
| `rust/` | Does not exist. |

So the language swap did not produce a second implementation. It produced fifteen module stubs
and a green CI check that proves nothing. That outcome — not the choice of language — is the
problem this ADR responds to.

## Decision

Reinstate Rust as a third implementation language, alongside Java and C++, under a design whose
gates make the stub outcome mechanically impossible:

1. One vertical slice — the cash-equity order path — implemented to production depth in all three
   languages (ADR [0002](0002-vertical-slice-cash-equity.md)).
2. Behavioural equivalence proven by a blocking, byte-exact conformance gate, not asserted
   (ADR [0003](0003-shared-schemas-corpus-harness.md)).
3. An anti-stub check that fails the build if a module claimed by the slice contains no compiled
   behaviour and no test that asserts something (ADR [0004](0004-defensive-gate-stack.md)).
4. Transport abstracted so nothing depends on `aeron-rs`
   (ADR [0006](0006-abstract-transport-journal-first.md)).

The `aeron-rs` objection is answered rather than overruled: see ADR 0006. The three
preference/staffing reasons are not disputed — they are simply not reasons to have zero
implementations instead of two.

## Consequences

- Java stays the reference implementation. It is not deprecated, replaced, or frozen.
- `rust/` is created as a Cargo workspace whose module names are 1:1 with `java/` and `cpp/`.
  **No empty module stubs are committed** — a module directory appears only when it has behaviour
  and tests. This is the direct lesson of the `cpp/` tree.
- Rust is ported **before** C++ (see `docs/polyglot/README.md` §Sequencing). The Rust compiler
  catches at build time the class of error the C++ sanitizer matrix catches at run time, so doing
  Rust first surfaces the design's aliasing and ownership problems early and cheaply.
- Three languages means three lint/build toolchains in CI. Contained by a single gate entry point
  (ADR 0004) so the marginal cost of language number three is one lane in one script.
- The port is also a study artifact: every non-trivial idiom gets a note whose code anchor CI
  verifies (ADR [0005](0005-study-guide-with-enforced-anchors.md)).

## Alternatives considered

**Leave `cpp/` as stubs and do nothing.** Rejected: the tree already claims fifteen modules and a
green check. Leaving it is not neutral — it actively misrepresents the state of the repo.

**Fill in `cpp/` broadly, no Rust.** Rejected: a broad, shallow port across fifteen modules
reproduces the current failure at larger scale. And with two languages where one is the reference,
a differential gate has only one comparison to make; the third implementation is what turns
"C++ matches Java" into "the specification is language-independent".

**Rust only, delete `cpp/`.** Rejected: the C++ memory-safety story is half the demonstration, and
the sanitizer/fuzz matrix in ADR 0004 has no purpose without C++ source to run it on.

**Wait for `aeron-rs` to mature first.** Rejected: it puts all the risk up front and blocks the
entire port on a dependency nothing needs to depend on. ADR 0006 removes the dependency instead.
