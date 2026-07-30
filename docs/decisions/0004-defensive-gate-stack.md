# 0004 — Full defensive gate stack behind a single entry point

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: ADR [0001](0001-reinstate-rust-three-language-port.md), `docs/CI_BRANCH_PROTECTION.md`

## Context

Two problems, one answer.

**Problem 1 — memory safety.** Adding a C++ implementation of an order path adds a class of defect
the Java tree cannot have: use-after-free, buffer overrun, data race, uninitialised read. Review
does not catch these reliably. Machines do.

**Problem 2 — local/CI divergence.** Today `.githooks/pre-push` runs `shellcheck` and nothing
else, while CI runs Gradle, Spotless, CMake, `ctest` and schema lint. Most CI failures are
therefore not reproducible before push. Adding two more toolchains to CI without fixing that
widens the gap threefold.

## Decision

**A. Per-language gates, blocking.**

*C++* — the heaviest, because memory corruption is the named risk:

- `-Wall -Wextra -Wpedantic -Werror` (already present), plus `-Wconversion -Wshadow
  -Wold-style-cast -Wnon-virtual-dtor -Wcast-qual`, plus `-Wuseless-cast` guarded on GCC (Clang
  lacks it, and `-Werror` makes an unknown warning flag fatal).
- `clang-tidy`: `bugprone-*`, `cppcoreguidelines-*`, `modernize-*`, `performance-*`,
  `readability-*`, `cert-*`, `misc-*`. Blocking. A suppression needs an inline justification; a
  bare `NOLINT` fails review.
- `include-what-you-use`.
- Hardening: `_GLIBCXX_ASSERTIONS`, `_FORTIFY_SOURCE=3`, `-fstack-protector-strong`,
  `-D_LIBCPP_HARDENING_MODE=fast` under libc++.
- Sanitizer matrix, separate build configurations, all blocking: ASan+UBSan
  (`-fno-sanitize-recover=all`), TSan, MSan (with an MSan-instrumented libc++, or the job is
  marked as not running — never silently skipped).
- libFuzzer targets on every parser (FIX decode, SBE decode, journal parse), seeded from the
  conformance corpus. Short per-PR run, long nightly run, corpus persisted as a CI artifact.
- Valgrind memcheck over a full slice run, nightly.

*Rust*:

- `#![forbid(unsafe_code)]` at every crate root. Exactly one crate may relax it — the optional
  `ems-transport-aeron` FFI crate, if ever built — recorded in the crate docs and reviewed line by
  line.
- `clippy::pedantic` + `clippy::nursery` with `-D warnings`; `clippy::disallowed_types` bans
  `HashMap`/`HashSet` on output-reaching paths (ADR 0003).
- `cargo fmt --check`.
- **Miri** on the unit test suite — the honest counterweight to the C++ sanitizer matrix.
- `cargo-deny` (advisories, licenses, duplicate versions).
- `cargo-fuzz` on the same three parsers as C++, sharing the same seed corpus.

*Java*: existing gates stay (Spotless, JaCoCo aggregate, FSM sync). Added **ErrorProne** and
**NullAway** — the tree already annotates with `org.jspecify` and nothing currently checks it.

**B. Cross-language gate**, after all three build:

1. Codegen sync — regenerate FSMs for all three languages, `git diff --exit-code`.
2. Conformance — every corpus case against every implementation, byte-exact.
3. FSM coverage — every transition in `schemas/fsm/*.fsm.yaml` reached by ≥1 corpus case.
4. Study-guide integrity — ADR 0005.
5. **Anti-stub check** — every module directory that the slice claims must contain compiled
   behaviour and at least one test that asserts something. A module cannot be listed as done while
   it is a header stub. This check exists because the repo already contains fifteen
   counter-examples.

**C. One entry point.** `scripts/ci/gate.sh` is the only gate, with three lanes:

```
scripts/ci/gate.sh fast      # compile + unit tests + fmt/lint, all 3 languages
scripts/ci/gate.sh full      # fast + sanitizers + conformance + study-guide check
scripts/ci/gate.sh nightly   # full + MSan + Valgrind + long fuzz
```

Every CI job invokes it. `.githooks/pre-push` invokes `fast`. Hook and CI run **the same script
with the same dependency set in the same container image** — a `.devcontainer`-derived image
pinned by digest.

`.githooks/pre-commit` keeps its current job (regenerate FSM Java, `spotlessApply`, re-stage) and
extends to the new languages (`fsm_codegen.py --cpp-only --rust-only`, then `clang-format` and
`cargo fmt`, re-staging what they touch). It stays a fast auto-fixer and deliberately does **not**
call `gate.sh` — a commit hook that runs sanitizers is a commit hook developers disable.
`.githooks/commit-msg` is untouched.

## Consequences

- A CI failure is reproducible locally by running one command, and adding a fourth language cannot
  reopen that gap.
- PR feedback time grows. Contained by lanes: PRs run `fast` plus ASan/UBSan; MSan, Valgrind and
  long fuzz are nightly. Revisit if PR feedback exceeds ~10 minutes.
- The container image is now a versioned artifact with its own update path. Pinned by digest, so
  a base-image change cannot silently alter gate behaviour.
- Some existing code may not survive the new Java checks (ErrorProne/NullAway). Those fixes are
  part of the gate-skeleton sub-project, not deferred.

## Alternatives considered

**Sanitizers without fuzzing.** Rejected: sanitizers only observe the inputs the tests supply, and
parser bugs live in the inputs nobody thought to write.

**Lint-only, no sanitizers.** Rejected: static analysis does not catch use-after-free or data
races in any dependable way. That is the entire named risk of adding C++.

**Separate hook and CI scripts that "mirror" each other.** Rejected: they diverge. That is the
failure mode being fixed, not a hypothetical.
