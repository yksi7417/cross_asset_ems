# C++ modules

C++ counterpart to the Java tree — same layering, same module names. Used for components where
deterministic, GC-free latency is required (venue adapters, SOR hot path, market-data fan-out).
CMake workspace rooted at `cpp/CMakeLists.txt`; targets C++20.

The Java tree is the primary reference implementation; C++ modules mirror the schema and FSM but
may lag in coverage. SBE schemas and FSM YAML codegen for both languages from the same source in
`../schemas/`.

| Module | Equivalent Java module | Status |
|---|---|---|
| `ems-core` | `java/ems-core` | stub — populates at task 1.7 |
| `ems-fsm` | `java/ems-fsm` | stub — populates at task 1.7 |
| `ems-transport` | `java/ems-transport` | stub |
| `ems-aaa` | `java/ems-aaa` | stub |
| `ems-validator` | `java/ems-validator` | stub |
| `ems-oms` | `java/ems-oms` | stub |
| `ems-fix-bridge` | `java/ems-fix-bridge` | stub |
| `ems-market-data` | `java/ems-market-data` | stub |
| `ems-pretrade` | `java/ems-pretrade` | stub |
| `ems-venue-connectivity` | `java/ems-venue-connectivity` | stub |
| `ems-posttrade` | `java/ems-posttrade` | stub |
| `ems-observability` | `java/ems-observability` | stub |
| `ems-ops` | `java/ems-ops` | stub |
| `ems-bench` | `java/ems-bench` | stub |
| `ems-it` | `java/ems-it` | stub |

## Build

```bash
# Configure
cmake -S cpp -B build/cpp -G Ninja -DCMAKE_CXX_STANDARD=20

# Build (no-op until stubs fill in, but CMake configure must succeed)
cmake --build build/cpp

# Test
ctest --test-dir build/cpp --output-on-failure
```

## Coding rules

- C++20, `cmake_minimum_required(VERSION 3.25)`.
- `-Wall -Wextra -Wpedantic -Werror` on GCC/Clang — no warnings promoted to errors until source lands.
- `#pragma once` at every header.
- No exceptions in steady-state hot-path code; `std::expected` or result types preferred.
- No dynamic allocation on the hot path (venue adapter tick processing, FSM dispatch).
- All cross-component boundaries use SBE-encoded messages via `ems-transport`.
- Unit tests live in each module's own `tests/` subdirectory; cross-module tests in `ems-it`.

## Decision record

**2026-06-07 — replaced Rust with C++.**
Rust was present as empty scaffolding with no committed source. C++ was chosen instead because:
- The Aeron ecosystem (transport layer) has a mature, actively-maintained C++ media driver and client
  library (`aeron` C++ client). Java is the primary Aeron runtime; C++ is the natural second language
  for latency-critical components that need to share the same media driver process without a JVM.
- SBE has first-class C++ codegen support matching Java feature-parity.
- The team's hot-path expertise is C++, not Rust.
- Rust's `aeron-rs` crate was marked TBD in `Cargo.toml` with a note that it might need a custom
  FFI layer — meaning the transport binding was unproven.

The module structure and build layout mirror what the Rust workspace had (15 stubs, 1:1 with Java),
so the transition cost is minimal. Task 1.7 (codegen pipeline) is where C++ source first lands.
