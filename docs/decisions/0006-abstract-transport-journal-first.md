# 0006 — Abstract transport: journal-file implementation for the gate, Aeron optional for Rust

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: ADR [0001](0001-reinstate-rust-three-language-port.md), ADR [0003](0003-shared-schemas-corpus-harness.md), [[arch-sbe-aeron-transport]], `docs/transport/`

## Context

The one technical objection in the 2026-06-07 decision to drop Rust was real and is still
unanswered on its own terms:

> Rust's `aeron-rs` crate was marked TBD in `Cargo.toml` with a note that it might need a custom
> FFI layer — meaning the transport binding was unproven.

Overruling it would be dishonest. It needs an answer.

Separately, the conformance gate (ADR 0003) requires the slice binary to be a pure function from
input journal to output journal — no network, no media driver, no timing. A live transport cannot
provide that.

## Decision

`ems-transport` becomes an **interface** in each language with two implementations:

- **`JournalTransport`** — reads and writes journal files. Deterministic, no media driver, no
  network. This is what the conformance gate uses and what all three implementations ship first.
- **`AeronTransport`** — the live path. Java has it already. C++ uses the mature Aeron C++ client.
  Rust's remains an isolated, optional, separately-scheduled task.

## Consequences

- The unproven `aeron-rs` binding is no longer on the critical path of anything. If it proves out,
  Rust joins the live transport. If it never does, the Rust port is unaffected and complete on its
  own terms. The objection is resolved rather than overruled.
- A swappable transport is the correct design independently: it is what makes byte-identical
  replay testable at all — a capability the Java tree already claims and would benefit from having
  under test.
- Java gains a `JournalTransport` implementation it did not previously have. That is a net
  addition to the reference tree, not scaffolding for the ports.
- Latency benchmarks over the live path are out of scope for the port and are not a gate.

## Alternatives considered

**`aeron-rs` first.** Rejected: puts all the risk up front and blocks the port on the one
component nothing needs to depend on.

**FFI-wrap the C++ Aeron client from Rust.** Rejected: it would put every Rust message across an
`unsafe` boundary, defeating the `forbid(unsafe_code)` posture in ADR 0004 and gutting the
memory-safety half of the demonstration.

**Drop Aeron entirely from the port.** Rejected: it loses the latency story the original
2026-06-07 ADR cared about, and Java/C++ can carry `AeronTransport` at no cost to the gate.
