# The gate

`scripts/ci/gate.sh` is the only quality gate in this repository. Every CI job invokes it.
`.githooks/pre-push` invokes it. Nothing else runs a build, lint or test command.

```bash
scripts/ci/gate.sh fast      # compile + unit tests + fmt/lint, all languages
scripts/ci/gate.sh full      # fast + sanitizers + conformance + study guide
scripts/ci/gate.sh nightly   # full + MSan + Valgrind + Miri + long fuzz

scripts/ci/gate.sh full --list   # print the steps the lane would run, run nothing
```

**Why one script.** Before this existed, `.githooks/pre-push` ran `shellcheck` and nothing else
while CI ran Gradle, CMake, Spotless and schema lint — so most CI failures were not reproducible
before push. Adding two more language toolchains to CI without fixing that would have widened the
gap threefold. See [ADR 0004](../decisions/0004-defensive-gate-stack.md).

---

## Seeing it work

```bash
scripts/dev/demo-polyglot.sh          # build, run all three, compare, then break one on purpose
scripts/dev/demo-polyglot.sh --no-build
conformance/harness/run.sh            # just the byte-exact comparison
conformance/harness/run.sh --list     # what would run, and what is not built
```

## Reproducing a CI failure

Whatever CI reported, run the same lane locally:

```bash
scripts/ci/gate.sh full
```

The summary at the end lists every step with `PASS` / `FAIL` / `SKIP` and a duration. A failing
step does **not** abort the run — one invocation reports every failure, because a developer fixing
three things wants to see three things.

If a step you expected did not run, `--list` shows the lane composition and the summary shows why
it was skipped.

---

## Lanes and steps

| Step | fast | full | nightly | What it runs |
|---|:--:|:--:|:--:|---|
| `exec-bits` | ✔ | ✔ | ✔ | Every tracked `*.sh` outside a `lib/` and every hook is mode `100755` |
| `shellcheck` | ✔ | ✔ | ✔ | `shellcheck -x` over `scripts/**/*.sh` and `.githooks/*`; falls back to the `koalaman/shellcheck` container under podman or docker |
| `ci-check-tests` | ✔ | ✔ | ✔ | `python3 -m unittest discover -s scripts/ci/checks` — the gate's own checks are tested |
| `fsm-sync` | ✔ | ✔ | ✔ | Regenerate FSMs (Java + C++, Rust when present), `git diff --exit-code` |
| `java-build` | ✔ | ✔ | ✔ | `./gradlew assemble` — includes ErrorProne + NullAway |
| `java-test` | ✔ | ✔ | ✔ | `./gradlew allTests` |
| `java-format` | ✔ | ✔ | ✔ | `./gradlew spotlessCheck` |
| `java-nullmarked` | ✔ | ✔ | ✔ | `@NullMarked` coverage ratchet (see below) |
| `java-clock` | ✔ | ✔ | ✔ | business logic takes a `TimeSource` rather than reading the wall clock (see below) |
| `cpp-build` | ✔ | ✔ | ✔ | CMake configure + build into `build/cpp` |
| `cpp-test` | ✔ | ✔ | ✔ | `ctest --no-tests=error` |
| `rust-format` | ✔ | ✔ | ✔ | `cargo fmt --check` |
| `rust-lint` | ✔ | ✔ | ✔ | `cargo clippy --all-targets -- -D warnings` |
| `rust-test` | ✔ | ✔ | ✔ | `cargo test --all` |
| `anti-stub` | ✔ | ✔ | ✔ | `scripts/ci/checks/anti_stub.py` (see below) |
| `schema-lint` | | ✔ | ✔ | yamllint over `schemas/fsm/`; xmllint well-formedness over `schemas/sbe/` (XSD validation only when `schemas/sbe/sbe.xsd` is present — see below) |
| `java-coverage` | | ✔ | ✔ | `./gradlew jacocoRootReport` |
| `cpp-coverage` | | ✔ | ✔ | `gcov` + `gcovr` over a separate `-DEMS_COVERAGE=ON` build; HTML + Cobertura into `build/coverage/` |
| `rust-coverage` | | ✔ | ✔ | `cargo llvm-cov` (LLVM source-based instrumentation); HTML into `build/coverage/rust/` |
| `cpp-asan-ubsan` | | ✔ | ✔ | Separate build **and `ctest` run** with `-fsanitize=address,undefined -fno-sanitize-recover=all` |
| `cpp-tsan` | | ✔ | ✔ | Separate build **and `ctest` run** with `-fsanitize=thread` |
| `rust-deny` | | ✔ | ✔ | `cargo deny check` — advisories, licenses, duplicate versions |
| `conformance` | | ✔ | ✔ | Every corpus case against every implementation, byte-exact |
| `fsm-coverage` | | ✔ | ✔ | Every FSM transition reached by ≥1 corpus case |
| `study-guide` | | ✔ | ✔ | `scripts/ci/checks/study_guide.py` (see below) |
| `cpp-msan` | | | ✔ | MSan build — requires an MSan-instrumented libc++ |
| `cpp-valgrind` | | | ✔ | `valgrind --error-exitcode=1` over a full slice run |
| `rust-miri` | | | ✔ | `cargo +nightly miri test` |
| `fuzz-long` | | | ✔ | libFuzzer + `cargo fuzz`, long run, corpus persisted as an artifact |

`full` is a superset of `fast`; `nightly` is a superset of `full`. There is no way to run a step
that is not in a lane, and no way for a lane to run something the table does not list — the lane
arrays at the top of `gate.sh` are the only source.

### Steps that skip today

The gate is deliberately built **before** the code it governs, so several steps have no subject
yet. They skip with the sub-project that will deliver them named in the reason:

| Step | Skips because | Delivered by |
|---|---|---|
| `rust-format`, `rust-lint`, `rust-test`, `rust-deny`, `rust-miri` | `rust/Cargo.toml` does not exist | sub-project 3 |
| `conformance`, `fsm-coverage` | `conformance/harness/` does not exist | sub-project 2 |
| `cpp-valgrind`, `fuzz-long` | there is no slice binary or fuzz target to run | sub-projects 4, 5 |
| `cpp-msan` | no MSan-instrumented libc++ (`EMS_MSAN_LIBCXX` unset) | [T-1](TODO.md#t-1) — nightly-only by [ADR 0008](../decisions/0008-msan-nightly-only.md) |
| `cpp-asan-ubsan`, `cpp-tsan` | the toolchain has the compiler but not the runtime (`libasan`, `libtsan`) | a missing-tool skip: local nudge, CI failure |

**Two known gaps the gate states rather than hides:**

- `schemas/sbe/sbe.xsd` is not vendored, so SBE XML is checked for well-formedness but **not**
  validated against the SBE schema. The step prints this on every run. The previous CI job hid the
  same gap behind `|| true`.
- The sanitizer steps probe whether the toolchain can actually *link* a sanitized binary before
  running. A distro that ships `g++` without `libasan` would otherwise produce a link error that
  reads like a code defect.

A skip is always visible in the summary. The gate never reports having run something it did not.

---

## Skip semantics and `EMS_GATE_STRICT`

There are two kinds of skip, and they are treated differently on purpose:

| Kind | Example | Local | CI (`EMS_GATE_STRICT=1`) |
|---|---|---|---|
| **Subject missing** | `rust/` does not exist yet | `SKIP` | `SKIP` |
| **Tool missing** | `clang-tidy` not installed | `SKIP` (with a nudge) | **`FAIL`** |

A few steps avoid the second row entirely by falling back to a container: `shellcheck` runs from
`docker.io/koalaman/shellcheck:stable` under podman or docker when the binary is absent, so a
laptop without it still gets the check rather than a skip.

`EMS_GATE_STRICT` is set automatically whenever `CI` is set. The reason for the split: a laptop
without `clang-tidy` should still be able to run the gate, but a CI image that quietly loses a
package must not quietly stop enforcing a check.

Force strict mode locally to see exactly what CI will do:

```bash
EMS_GATE_STRICT=1 scripts/ci/gate.sh full
```

---

## Toolchain

`scripts/ci/install-toolchain.sh` is the single definition of what the gate needs. The devcontainer
image runs it, and every CI job runs it.

```bash
scripts/ci/install-toolchain.sh --profile base      # what `fast` needs
scripts/ci/install-toolchain.sh --profile full      # + what `full` needs
scripts/ci/install-toolchain.sh --profile nightly   # + Valgrind, Miri, cargo-fuzz
scripts/ci/install-toolchain.sh --profile full --dry-run   # print, install nothing
```

| Profile | Adds |
|---|---|
| `base` | build-essential, cmake, ninja, g++-14, shellcheck, clang-format, python3 + PyYAML, yamllint, libxml2-utils, rustup stable + clippy + rustfmt |
| `full` | clang, clang-tidy, include-what-you-use, sanitizer runtimes, `cargo-deny` |
| `nightly` | valgrind, rust nightly + miri, `cargo-fuzz` |

It is idempotent — already-installed packages are skipped, so re-running costs a `dpkg-query`.

> **Recorded deviation from the design spec.** The spec calls for CI and the hook to run "the same
> container image pinned by digest". What exists today is the identical *dependency set* via one
> script that both the image build and CI run — not a digest-pinned image, which needs registry
> credentials and a publish workflow. Publishing it is the follow-up item at the end of the
> [gate-skeleton plan](../superpowers/plans/2026-07-30-polyglot-01-gate-skeleton.md) and as
> [T-4](TODO.md#t-4). Until then, the image is not the unit of pinning and this document says so
> rather than implying otherwise.

---

## The hooks

| Hook | Runs | Why |
|---|---|---|
| `.githooks/pre-commit` | FSM codegen (Java + C++), `spotlessApply`, `clang-format`, `cargo fmt`, re-stage | A fast auto-fixer. It must stay fast — a commit hook that runs sanitizers is a commit hook developers disable. It deliberately does **not** call `gate.sh`. |
| `.githooks/pre-push` | `gate.sh fast` | The gap-closer. Whatever CI's fast lane will say, you hear it here first. |
| `.githooks/commit-msg` | unchanged | Conventional Commit enforcement. |

Install them once per clone:

```bash
./scripts/dev/install-hooks.sh     # or: git config core.hooksPath .githooks
```

Bypass once, when you know what you are doing: `git push --no-verify`.

---

## The checks the gate owns

Four Python checks live in `scripts/ci/checks/`, each with unit tests that the `ci-check-tests`
step runs. They use the standard library plus PyYAML — nothing else.

### `anti_stub.py` — a module cannot be "done" while it is a stub

Reads `scripts/ci/slice-manifest.yaml`, which declares per language and per module what the port
claims: `stub`, `in-progress` or `done`.

- A `done` module must contain implementation source **and** at least one test that actually
  asserts something (`EXPECT_`, `ASSERT_`, `REQUIRE`, `assert_eq!`, …). A `#pragma once` header is
  not behaviour; a test that calls a function without asserting is not a test.
- A `stub` module must **not** contain implementation source. If it does, the manifest is stale and
  the build fails.
- Generated sources never count as behaviour.

This check exists because `cpp/` already contains fifteen counter-examples: module directories with
a green `ctest` and no behaviour whatsoever.

### `study_guide.py` — the study guide cannot drift from the code

Bidirectional: every `STUDY: <slug>` marker in `java/`, `cpp/` or `rust/` needs a note at
`70_concepts/idioms/<slug>.md`, and every note's `anchor: <path>:<line>` must still resolve to a
line carrying that marker. See [`70_concepts/idioms/README.md`](../../70_concepts/idioms/README.md)
for the note template.

### `no_raw_clock.py` — business logic cannot read the wall clock

`io.crossasset.ems.core.clock.Clock` has said business logic must take an injected clock since task
3.6. Nothing checked it, and four services called `System.currentTimeMillis()` anyway *while their
own javadoc claimed the clock was injected*.

A component that reads the wall clock cannot be replayed, cannot be tested against a fixed instant,
and cannot join the conformance gate — which compares three implementations byte-for-byte and would
see a different timestamp every run.

`SystemTimeSource` is the single class permitted to read the clock. Everything else takes a
`TimeSource` (`now()`, `nowMicros()`); `Clock extends TimeSource` and adds scheduling, so a
component that only needs the time does not drag in a timer thread.

Genuine exceptions — I/O deadlines around Aeron offers, demo entry points — are listed in
`scripts/ci/clock-baseline.txt`. The list can shrink but not grow, and a **stale** entry (a file
that no longer offends) is also a failure: a baseline that outlives the problem stops describing
reality and starts granting blanket permission.

### `nullmarked_ratchet.py` — null-correctness coverage only grows

NullAway runs in `OnlyNullMarked` mode, so it checks exactly the packages that opt in with
JSpecify's `@NullMarked`. The obvious failure mode of an opt-in check is deleting the opt-in to
make a build pass, so `scripts/ci/nullmarked-baseline.txt` lists the opted-in packages and the
check fails in both directions — a baseline package that loses the annotation, and an annotated
package missing from the baseline.

Adding a package: annotate it, fix what NullAway reports (annotate genuinely-nullable references
`@Nullable`; do not remove `@NullMarked`), add the package name to the baseline.

---

## C++ build configuration

`cpp/CMakeLists.txt` carries the warning and hardening set from ADR 0004:

- `-Wall -Wextra -Wpedantic -Werror -Wconversion -Wshadow -Wold-style-cast -Wnon-virtual-dtor
  -Wcast-qual`, plus `-Wuseless-cast` **only on GCC** — Clang does not have it and `-Werror` makes
  an unknown warning flag fatal.
- `_GLIBCXX_ASSERTIONS`, `-fstack-protector-strong`, and `_FORTIFY_SOURCE=3` on optimised
  configurations only (at `-O0` it emits a `#warning`, which `-Werror` turns into a build failure).
- The default build type is `RelWithDebInfo`, so hardening that requires optimisation is actually
  in effect.

Sanitizer builds are selected with `-DEMS_SANITIZER=address-undefined|thread|memory|none`, each in
its own build directory so they never share a cache.

---

## Adding a step

1. Write the step function in `gate.sh` (`do_<name>`), or call a check directly.
2. Add the name to `FAST_STEPS`, `FULL_EXTRA_STEPS` or `NIGHTLY_EXTRA_STEPS`.
3. Add a `run_step` case that dispatches it — use `step_needs_tool` when it depends on a binary, and
   `step_skip` when its subject may legitimately not exist yet.
4. Add a row to the lane table in this file.
5. `shellcheck scripts/ci/gate.sh scripts/ci/lib/steps.sh`.

Which lane? If it takes seconds and catches a mistake a developer makes routinely, `fast`. If it
takes minutes, `full`. If it takes tens of minutes or needs exotic setup, `nightly`. The budget to
protect is PR feedback under ~10 minutes.

---

## Related

- [`docs/polyglot/README.md`](README.md) — the port this gate governs
- [ADR 0004](../decisions/0004-defensive-gate-stack.md) — why the gate is shaped this way
- [`docs/CI_BRANCH_PROTECTION.md`](../CI_BRANCH_PROTECTION.md) — which checks are required on `main`
