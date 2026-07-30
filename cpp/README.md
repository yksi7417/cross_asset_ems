# C++ modules

C++ counterpart to the Java tree — same layering, same module names. Used for components where
deterministic, GC-free latency is required (venue adapters, SOR hot path, market-data fan-out).
CMake workspace rooted at `cpp/CMakeLists.txt`; targets C++20.

The Java tree is the primary reference implementation; C++ modules mirror the schema and FSM but
may lag in coverage. SBE schemas and FSM YAML codegen for both languages from the same source in
`../schemas/`.

> **Status, stated plainly.** Every module below is a stub. There are 21 files in this tree and no
> behaviour: `#pragma once` headers and generated FSM headers. CI configured, built and `ctest`ed
> it for months and passed, because there was nothing to compile.
>
> That is the failure the polyglot port is designed around. `scripts/ci/slice-manifest.yaml` is now
> the authority on what each module claims, and `scripts/ci/checks/anti_stub.py` fails the build if
> a module is marked `done` without implementation source and a test that actually asserts. `ctest`
> now runs with `--no-tests=error`.
>
> Real C++ source lands in sub-project 5 — see [`docs/polyglot/README.md`](../docs/polyglot/README.md).

Status column mirrors `scripts/ci/slice-manifest.yaml`, which is what the gate reads. Update both
in the same commit — the anti-stub check enforces the manifest, and this table is for humans.

| Module | Equivalent Java module | Status | In the cash-equity slice? |
|---|---|---|---|
| `ems-core` | `java/ems-core` | stub | yes |
| `ems-fsm` | `java/ems-fsm` | stub | yes |
| `ems-transport` | `java/ems-transport` | stub | yes |
| `ems-aaa` | `java/ems-aaa` | stub | yes |
| `ems-validator` | `java/ems-validator` | stub | yes |
| `ems-oms` | `java/ems-oms` | stub | yes |
| `ems-fix-bridge` | `java/ems-fix-bridge` | stub | no |
| `ems-market-data` | `java/ems-market-data` | stub | no |
| `ems-pretrade` | `java/ems-pretrade` | stub | no |
| `ems-venue-connectivity` | `java/ems-venue-connectivity` | stub | yes |
| `ems-posttrade` | `java/ems-posttrade` | stub | yes |
| `ems-observability` | `java/ems-observability` | stub | no |
| `ems-ops` | `java/ems-ops` | stub | no |
| `ems-bench` | `java/ems-bench` | stub | no |
| `ems-it` | `java/ems-it` | stub | no |

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
- Warning set per [ADR 0004](../docs/decisions/0004-defensive-gate-stack.md): `-Wall -Wextra
  -Wpedantic -Werror -Wconversion -Wshadow -Wold-style-cast -Wnon-virtual-dtor -Wcast-qual`, plus
  `-Wuseless-cast` on GCC only.
- Hardening on by default: `_GLIBCXX_ASSERTIONS`, `-fstack-protector-strong`, and
  `_FORTIFY_SOURCE=3` on optimised builds. Default build type is `RelWithDebInfo`.
- Sanitizer builds: `-DEMS_SANITIZER=address-undefined|thread|memory`, each in its own build
  directory. Driven by `scripts/ci/gate.sh`.
- `#pragma once` at every header.
- No exceptions in steady-state hot-path code; `std::expected` or result types preferred.
- No dynamic allocation on the hot path (venue adapter tick processing, FSM dispatch).
- All cross-component boundaries use SBE-encoded messages via `ems-transport`.
- Unit tests live in each module's own `tests/` subdirectory; cross-module tests in `ems-it`.

## Decision record

**Superseded in part on 2026-07-30** by
[ADR 0001 — Reinstate Rust as a third implementation language](../docs/decisions/0001-reinstate-rust-three-language-port.md).
The record below stays because it is load-bearing context for why this tree is shaped the way it
is. Its one genuine technical objection — the unproven `aeron-rs` binding — is answered rather than
overruled by [ADR 0006](../docs/decisions/0006-abstract-transport-journal-first.md), which takes
`aeron-rs` off the critical path of everything.

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

*(Retrospect, 2026-07-30: it did not. Fourteen months later the tree is still fifteen stubs. The
lesson is recorded in ADR 0001 — a language swap does not produce an implementation, and a gate
that passes over an empty tree is worse than no gate. Unlike this tree, `rust/` commits no empty
module stubs: a directory appears when it has behaviour and tests.)*
