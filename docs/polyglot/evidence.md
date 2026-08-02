# Evidence — one order path, three languages, identical bytes

**As of commit `9b40e72`, 2026-08-02.** The cash-equity slice — intake, AAA, five-layer
validation, order FSM, routing, route lifecycle, venue edge, allocation — runs in Java, Rust and
C++, and the three binaries produce **byte-identical journals** for every corpus case. Every claim
below has a command next to it; nothing here is asserted that a reader cannot re-run.

> **This page is a snapshot, not a live instrument.** The numbers were true at the commit above and
> nothing keeps them current — that is the doc-drift trap
> ([traps.md §6](traps.md)), named rather than hidden. When this page and a command disagree,
> trust the command. The always-current equivalents are `scripts/ci/gate.sh full`,
> `conformance/harness/run.sh` and `scripts/dev/demo-polyglot.sh`.

## Scoreboard

| | | Verify with |
|---|---|---|
| Slice components, all three languages | **8 / 8** | [status.md](status.md) feature matrix |
| Conformance runs, byte-identical | **24 / 24** | `conformance/harness/run.sh` |
| Triangulations, `UNANIMOUS` | **8 / 8** | same — runs after the reference diffs |
| FSM transitions reached by corpus | **84 / 84** (31 order + 29 route + 24 session) | `python3 conformance/harness/fsm_coverage.py` |
| Tests | **2,617** (2,399 Java · 120 Rust · 98 C++) | per-stream commands below |
| Gate, full lane | **24 pass / 0 fail / 2 skip** | `scripts/ci/gate.sh full` |
| CI on this commit | push **green** · nightly **green** | [push run](https://github.com/yksi7417/cross_asset_ems/actions/runs/30755582507) · [nightly run](https://github.com/yksi7417/cross_asset_ems/actions/runs/30755582304) |

The two local skips are `cpp-asan-ubsan` and `cpp-tsan` — missing runtimes on the dev machine only.
CI runs both, and ran both green on this commit.

## Per-stream evidence

Same slice, three toolchains, each held to its own strictest settings.

### Java — the reference

Generates each case's `expected.jsonl`. Triangulation removes its privilege: a unit test pins that
Java itself can be the named minority in a 2-1 split.

| | |
|---|---|
| Tests | **2,399 passing**, 14 modules — `./gradlew test` |
| Static analysis | ErrorProne at ERROR severity · NullAway `OnlyNullMarked` + ratchet, so null-safety coverage only grows |
| Format | Spotless, gate-enforced |
| Coverage | JaCoCo per-module ratchet — gate step `java-coverage` |
| Clock discipline | `no_raw_clock.py`: business logic takes a `TimeSource`; the baseline's last honest-debt entry was paid (T-5) |
| T-8 contract | `aDeclinedEventReturnsTheUnchangedContext` — `assertSame`, identity not equality |

### Rust — port one

The compiler is a reviewer: FSM state exhaustiveness is a **compile error**, effect lifetimes are
`&'static` and checked, and `dead_code` found a real design flaw
([status.md](status.md)) that the other two toolchains never mentioned.

| | |
|---|---|
| Tests | **120 passing**, 14 suites — `cargo test` |
| Lint | `clippy::pedantic` + `nursery`, `-D warnings` · `#![forbid(unsafe_code)]` workspace-wide |
| UB detection | **Miri, whole workspace, exit 0** — nightly lane. Fixed at this commit after a toolchain update turned the lane red with nobody watching: T-2's scenario, observed rather than predicted |
| Coverage | cargo-llvm-cov: **93.5% region · 93.5% line** — gate-enforced |
| Supply chain | cargo-deny: one licence allowlisted, no git sources |
| T-8 contract | `a_declined_event_returns_the_unchanged_context` |

### C++ — port two

Same behaviour under the widest warning set in the repo, with the sanitizer matrix standing in for
what Rust's compiler proves statically.

| | |
|---|---|
| Tests | **98 gtest cases**, 8 ctest targets — `ctest --test-dir build/cpp` |
| Warnings | `-Wall -Wextra -Wpedantic -Wconversion -Wshadow -Wold-style-cast -Wcast-qual -Wuseless-cast -Wnon-virtual-dtor -Werror` |
| Sanitizers | ASan + UBSan and TSan run the full suite in CI — green on this commit. MSan is nightly-gated behind an instrumented libc++ (T-1, open) |
| Coverage | gcovr: **83.2% lines · 93.1% functions** — excludes passed on the command line, because gcovr 8.x silently ignores config-file excludes ([traps.md §2](traps.md)) |
| Effects | generated `constexpr` tables returned as `std::span` — zero allocation, `noexcept` kept |
| T-8 contract | `ADeclinedEventReturnsTheUnchangedContext` |

## The cross-language proof

Unit tests prove each stream agrees with itself. These prove the three agree with **each other**,
at the byte level, with no normalisation — the differ treats a trailing space, a CRLF or a reordered
key as the divergence it is.

One sha256 for three binaries (component-01, seed 0, demo step 4/6):

```
615e515c57b4e3c4901e814f7e24abd4908efc4f1cfef713307bb1748ea6f675  java.jsonl
615e515c57b4e3c4901e814f7e24abd4908efc4f1cfef713307bb1748ea6f675  rust.jsonl
615e515c57b4e3c4901e814f7e24abd4908efc4f1cfef713307bb1748ea6f675  cpp.jsonl
```

| Corpus case | Covers | In → out | 3-way |
|---|---|---|---|
| [component-01-journal-and-ids](../../conformance/corpus/component-01-journal-and-ids/case.md) | journal codec, deterministic ids, UTF-8 edges | 6 → 10 | ✔ |
| [component-03-aaa-rejections](../../conformance/corpus/component-03-aaa-rejections/case.md) | session gate, three denial layers | 11 → 19 | ✔ |
| [component-04-validation-layers](../../conformance/corpus/component-04-validation-layers/case.md) | SESSION → IDENTITY → REFERENCE → PERMISSION | 8 → 14 | ✔ |
| [component-05-order-fsm](../../conformance/corpus/component-05-order-fsm/case.md) | all 31 order transitions, 12 orders | 58 → 72 | ✔ |
| [component-06-routing](../../conformance/corpus/component-06-routing/case.md) | route creation, 4 reject codes, id conservation | 24 → 34 | ✔ |
| [component-07-route-lifecycle](../../conformance/corpus/component-07-route-lifecycle/case.md) | all 29 route transitions + the cross-FSM cascade | 101 → 192 | ✔ |
| [component-08-venue-edge](../../conformance/corpus/component-08-venue-edge/case.md) | all 24 session transitions, FIX out, ExecutionReport in | 88 → 109 | ✔ |
| [component-09-allocation](../../conformance/corpus/component-09-allocation/case.md) | largest-remainder split, tie-breaks pinned | 37 → 73 | ✔ |

Every case is also **triangulated** ([conformance/README.md](../../conformance/README.md)): after
the reference diffs, the harness asks whether the three agree with *each other*. A divergence is a
named 2-1 split — "rust stands alone; java and cpp agree" — and "all three agree but the committed
expectation differs" is its own blocking verdict, the one the reference diff can never see. Both
blocking verdicts were watched failing through the real harness before being trusted.

## The demo

```bash
scripts/dev/demo-polyglot.sh                     # everything, ~90s cold
scripts/dev/demo-polyglot.sh --no-build          # seconds, on existing binaries
scripts/dev/demo-polyglot.sh --no-build --case component-07-route-lifecycle   # deep-dive one stage
```

Six steps: build all three binaries, show the input journal, run each implementation, prove the
sha256s match, **break Rust on purpose** (`--seed 1`) and watch the differ report the exact line —
then walk **every stage the slice has reached**, one row per corpus case, each checked against its
committed expectation *and* against the other two implementations. Step 6 reads the corpus
directory, not a list kept in the script, so a new component's case appears the moment it lands.

The transcript on this commit ends:

```
  ✔ component-01-journal-and-ids     6 events → 10 events
  ✔ component-03-aaa-rejections      11 events → 19 events
  ✔ component-04-validation-layers   8 events → 14 events
  ✔ component-05-order-fsm           58 events → 72 events
  ✔ component-06-routing             24 events → 34 events
  ✔ component-07-route-lifecycle     101 events → 192 events
  ✔ component-08-venue-edge          88 events → 109 events
  ✔ component-09-allocation          37 events → 73 events

Every stage agrees, three ways, against its committed expectation.
```

## What this does not prove

Everything [ADR 0002](../decisions/0002-vertical-slice-cash-equity.md) excludes stays excluded by
decision, not omission: SOR, algos, multi-leg, compliance, positions, the trader desktop, the six
other asset classes. `FixOut` records intent and identifying tags, not wire-format FIX — a
byte-exact encoder would be its own component. Three register items remain open in
[TODO.md](../TODO.md), each blocked on something real: T-1 needs an MSan-instrumented libc++
build, T-2 waits on T-1, T-4 needs registry credentials.
