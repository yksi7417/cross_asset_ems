# Rust crates

Rust counterpart to the Java tree — same layering. Used for components where deterministic latency or memory ownership argue for it (likely: venue adapters, SOR hot path, market-data fan-out). Cargo workspace; targets stable Rust.

The Java tree is the primary reference implementation; Rust modules port the schema and FSM but may lag in coverage. SBE schemas codegen for both languages from the same source XML in `../schemas/sbe/`.

| Crate | Equivalent to Java module | Status |
|---|---|---|
| `ems-core` | `java/ems-core` | TBD |
| `ems-fsm` | `java/ems-fsm` | TBD |
| `ems-transport` | `java/ems-transport` | TBD |
| `ems-aaa` | `java/ems-aaa` | TBD |
| `ems-validator` | `java/ems-validator` | TBD |
| `ems-oms` | `java/ems-oms` | TBD |
| `ems-fix-bridge` | `java/ems-fix-bridge` | TBD |
| `ems-market-data` | `java/ems-market-data` | TBD |
| `ems-pretrade` | `java/ems-pretrade` | TBD |
| `ems-venue-connectivity` | `java/ems-venue-connectivity` | TBD |
| `ems-posttrade` | `java/ems-posttrade` | TBD |
| `ems-observability` | `java/ems-observability` | TBD |
| `ems-ops` | `java/ems-ops` | TBD |
| `ems-bench` | `java/ems-bench` | TBD |
| `ems-it` | `java/ems-it` | TBD |

Build system: Cargo workspace (see `Cargo.toml` — added in task 0.2).

Coding rules:

- `#![deny(clippy::all)]` at each crate root.
- No `unwrap()` in production code outside `main()`.
- `unsafe` is review-gated and documented per occurrence.
- Tests are tier-1: every crate has unit tests; cross-crate tests in `ems-it`.
