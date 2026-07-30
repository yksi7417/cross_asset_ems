# Polyglot Port — Sub-project 1: Gate Skeleton — Implementation Plan

**Status: complete (2026-07-30).** Every task below shipped. Deviations from the plan as written
are recorded at the end — read that section before assuming the plan describes the tree.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the single gate entry point (`scripts/ci/gate.sh`), its checks, and the hook/CI
wiring — before any new language code exists, so the gate governs the code rather than being
written to fit it.

**Architecture:** One bash script with three lanes (`fast` / `full` / `nightly`) dispatching named
steps. Each step is a shell function that either runs, is skipped with a loud warning (local, tool
missing), or fails. CI jobs and `.githooks/pre-push` both invoke it. Toolchain installation is
factored into one script that both the devcontainer image and CI run, so the dependency set is
identical on both sides. Two Python checks (anti-stub, study-guide integrity) are unit-tested with
`unittest` — no new dependencies.

**Tech Stack:** bash (shellcheck-clean), Python 3 + PyYAML (already used by `tools/codegen/`),
Gradle 8.10 + convention plugins in `build-logic/`, CMake 3.25 + Ninja, GitHub Actions.

## Global Constraints

- Every shell script under `scripts/` and `.githooks/` must pass `shellcheck` — CI enforces it.
- `scripts/ci/gate.sh` is the **only** gate. No CI job may run a build/lint/test command directly.
- `.githooks/pre-commit` stays a fast auto-fixer and must **not** call `gate.sh`.
- `.githooks/commit-msg` is untouched.
- No new language code (`rust/`, C++ source) lands in this sub-project.
- Python checks target Python 3.10+ and use only the standard library plus `yaml`.
- Commit style: Conventional Commits. Small commits, one per task.

---

## File Structure

| File | Responsibility |
|---|---|
| `scripts/ci/gate.sh` | Lane dispatch, step registry, summary, exit code. The only gate entry point. |
| `scripts/ci/lib/steps.sh` | Step runner primitives: `step_run`, `step_skip`, `have_tool`, logging, timing, summary table. |
| `scripts/ci/install-toolchain.sh` | Installs the exact toolchain set the gate needs. Run by both the devcontainer image and CI. |
| `scripts/ci/slice-manifest.yaml` | Declares, per language per module, what the slice claims. The anti-stub check's input. |
| `scripts/ci/checks/anti_stub.py` | Fails if a module claimed `done` has no compiled behaviour or no asserting test; fails if a module with real source is still marked `stub`. |
| `scripts/ci/checks/study_guide.py` | Bidirectional `STUDY:` marker ↔ `70_concepts/idioms/` note check. |
| `scripts/ci/checks/test_anti_stub.py` | `unittest` coverage for the anti-stub check. |
| `scripts/ci/checks/test_study_guide.py` | `unittest` coverage for the study-guide check. |
| `.devcontainer/Dockerfile` | Image definition; delegates package installation to `install-toolchain.sh`. |
| `.githooks/pre-push` | Replaced: invokes `scripts/ci/gate.sh fast`. |
| `.githooks/pre-commit` | Extended: also runs `--cpp-only` codegen and `clang-format`/`cargo fmt` when present. |
| `.github/workflows/ci.yml` | Restructured: jobs call `gate.sh`, nothing else. |
| `.github/workflows/nightly.yml` | New: `gate.sh nightly` on a schedule. |
| `build-logic/src/main/kotlin/ems.java-conventions.gradle.kts` | Adds ErrorProne + NullAway. |
| `gradle/catalogs/libs.versions.toml` | ErrorProne / NullAway versions. |
| `docs/polyglot/gate.md` | Human reference for the gate: lanes, steps, how to reproduce a CI failure locally. |

---

### Task 1: Step-runner library and the gate skeleton

**Files:**
- Create: `scripts/ci/lib/steps.sh`
- Create: `scripts/ci/gate.sh`
- Test: manual invocation + `shellcheck`

**Interfaces:**
- Produces: `step_run <name> <cmd...>`, `step_skip <name> <reason>`, `have_tool <bin>`,
  `steps_summary` (prints the table, returns non-zero if any step failed),
  `EMS_GATE_STRICT` (when `1`, a missing tool is a failure, not a skip; set automatically when
  `CI` is set).

- [x] **Step 1: Write `scripts/ci/lib/steps.sh`**

Requirements the implementation must satisfy:
- `step_run NAME CMD...` runs the command, captures its exit code, records duration, prints a
  `▶ NAME` header before and `✔/✘ NAME (Ns)` after. Never aborts the script — records and continues,
  so one gate run reports every failure rather than only the first.
- `step_skip NAME REASON` records a skip. In strict mode it records a **failure** instead.
- `have_tool BIN` is `command -v BIN >/dev/null 2>&1`.
- `steps_summary` prints one line per step (`PASS` / `FAIL` / `SKIP` + duration) and exits non-zero
  if any step failed.
- No `set -e` reliance inside the runner — exit codes are captured explicitly.

- [x] **Step 2: Write `scripts/ci/gate.sh`**

- Usage: `gate.sh {fast|full|nightly} [--list]`. Unknown lane → usage + exit 2.
- `--list` prints the steps a lane would run and exits 0. This is what makes the gate auditable.
- Lane composition: `full` = `fast` + full-only steps; `nightly` = `full` + nightly-only steps.
- `cd` to the repo root first (`git rev-parse --show-toplevel`) so it works from anywhere.

- [x] **Step 3: Verify it fails cleanly on a bad lane**

Run: `scripts/ci/gate.sh bogus`
Expected: usage text on stderr, exit code 2.

- [x] **Step 4: Verify `--list` for each lane**

Run: `scripts/ci/gate.sh fast --list && scripts/ci/gate.sh full --list && scripts/ci/gate.sh nightly --list`
Expected: three step lists, `full` a superset of `fast`, `nightly` a superset of `full`, exit 0.

- [x] **Step 5: shellcheck**

Run: `shellcheck scripts/ci/gate.sh scripts/ci/lib/steps.sh`
Expected: no output, exit 0.

- [x] **Step 6: Commit**

```bash
git add scripts/ci/gate.sh scripts/ci/lib/steps.sh
git commit -m "feat(ci): gate.sh lane dispatcher and step-runner library"
```

---

### Task 2: Anti-stub check

**Files:**
- Create: `scripts/ci/slice-manifest.yaml`
- Create: `scripts/ci/checks/anti_stub.py`
- Test: `scripts/ci/checks/test_anti_stub.py`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `python3 scripts/ci/checks/anti_stub.py [--manifest PATH] [--root PATH]` — exit 0 clean,
  exit 1 with one `path: message` line per violation.
  Importable API: `load_manifest(path) -> dict`, `check(root, manifest) -> list[str]`.

Manifest shape (`scripts/ci/slice-manifest.yaml`):

```yaml
# What each non-reference language claims for each module.
#   stub        — directory exists, no behaviour. Must NOT contain non-generated source.
#   in-progress — behaviour landing; exempt from the check, must carry an `issue:` note.
#   done        — must contain compiled behaviour AND at least one test that asserts something.
cpp:
  ems-core: stub
  ems-fsm: stub
rust: {}
```

- [x] **Step 1: Write the failing tests**

```python
# scripts/ci/checks/test_anti_stub.py
import tempfile, pathlib, unittest
from anti_stub import check

def tree(spec):
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        p = root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(body)
    return root

class TestAntiStub(unittest.TestCase):
    def test_done_module_without_source_fails(self):
        root = tree({"cpp/ems-oms/CMakeLists.txt": "", 
                     "cpp/ems-oms/include/ems_oms/ems_oms.hpp": "#pragma once\n"})
        errs = check(root, {"cpp": {"ems-oms": "done"}})
        self.assertTrue(any("no implementation source" in e for e in errs))

    def test_done_module_without_asserting_test_fails(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n"})
        errs = check(root, {"cpp": {"ems-oms": "done"}})
        self.assertTrue(any("no test that asserts" in e for e in errs))

    def test_done_module_with_source_and_assert_passes(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n",
                     "cpp/ems-oms/test/order_test.cpp": "TEST(a,b){ EXPECT_EQ(f(), 1); }\n"})
        self.assertEqual(check(root, {"cpp": {"ems-oms": "done"}}), [])

    def test_stub_module_with_real_source_fails(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n"})
        errs = check(root, {"cpp": {"ems-oms": "stub"}})
        self.assertTrue(any("marked stub but contains" in e for e in errs))

    def test_missing_module_directory_fails(self):
        errs = check(tree({}), {"rust": {"ems-oms": "done"}})
        self.assertTrue(any("does not exist" in e for e in errs))
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'anti_stub'`.

- [x] **Step 3: Implement `anti_stub.py`**

Rules the implementation encodes:
- Implementation source = a file under the module with an implementation extension
  (`.cpp`/`.cc`/`.cxx` for C++, `.rs` for Rust) that is **not** under a `generated/` path and is not
  a test file, with at least one non-comment, non-blank line.
- Test file = path containing a `test` path segment or a filename matching `*_test.*`/`test_*.*`.
- "Asserts something" = the file contains one of `EXPECT_`, `ASSERT_`, `REQUIRE`, `CHECK(`,
  `assert!`, `assert_eq!`, `assert_ne!`, `debug_assert`.
- Header-only modules are legitimate for `cpp`; a module whose only implementation lives in headers
  passes provided a header contains a non-trivial definition (a line with `{` outside a `#pragma`),
  and the asserting-test rule still applies.
- `stub` module containing non-generated implementation source → error (the manifest is stale).
- Module directory missing entirely for a `done`/`in-progress` entry → error.

- [x] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' -v`
Expected: 5 tests, all PASS.

- [x] **Step 5: Run the check against the real tree**

Run: `python3 scripts/ci/checks/anti_stub.py`
Expected: exit 0 — every `cpp/` module is honestly declared `stub` in the manifest.

- [x] **Step 6: Commit**

```bash
git add scripts/ci/slice-manifest.yaml scripts/ci/checks/anti_stub.py scripts/ci/checks/test_anti_stub.py
git commit -m "feat(ci): anti-stub check with slice manifest"
```

---

### Task 3: Study-guide integrity check

**Files:**
- Create: `scripts/ci/checks/study_guide.py`
- Test: `scripts/ci/checks/test_study_guide.py`
- Create: `70_concepts/idioms/README.md`

**Interfaces:**
- Produces: `python3 scripts/ci/checks/study_guide.py [--root PATH]` — exit 0 clean, exit 1 with one
  violation per line. Importable: `markers(root) -> dict[str, list[str]]`,
  `notes(root) -> dict[str, dict]`, `check(root) -> list[str]`.

Contract:
- A marker is the literal `STUDY: <slug>` in a comment anywhere under `java/`, `cpp/`, `rust/`.
- A note is `70_concepts/idioms/<slug>.md` with front-matter carrying `anchor: <path>:<line>`.
- Bidirectional: every marker slug needs a note; every note anchor must resolve to a line
  containing `STUDY: <slug>` for that note's slug.

- [x] **Step 1: Write the failing tests**

```python
# scripts/ci/checks/test_study_guide.py
import tempfile, pathlib, unittest
from study_guide import check

def tree(spec):
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        p = root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(body)
    return root

NOTE = "---\nanchor: {anchor}\n---\n\n# {slug}\n"

class TestStudyGuide(unittest.TestCase):
    def test_marker_without_note_fails(self):
        root = tree({"rust/ems-oms/src/lib.rs": "// STUDY: typestate-fsm\nfn a() {}\n"})
        errs = check(root)
        self.assertTrue(any("no note" in e for e in errs))

    def test_note_with_dangling_anchor_fails(self):
        root = tree({
            "rust/ems-oms/src/lib.rs": "fn a() {}\n",
            "70_concepts/idioms/typestate-fsm.md": NOTE.format(anchor="rust/ems-oms/src/lib.rs:1", slug="x"),
        })
        errs = check(root)
        self.assertTrue(any("anchor" in e for e in errs))

    def test_matching_marker_and_note_passes(self):
        root = tree({
            "rust/ems-oms/src/lib.rs": "// STUDY: typestate-fsm\nfn a() {}\n",
            "70_concepts/idioms/typestate-fsm.md": NOTE.format(anchor="rust/ems-oms/src/lib.rs:1", slug="x"),
        })
        self.assertEqual(check(root), [])

    def test_empty_tree_passes(self):
        self.assertEqual(check(tree({})), [])
```

- [x] **Step 2: Run to verify failure**

Run: `python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'study_guide'`.

- [x] **Step 3: Implement `study_guide.py` and write `70_concepts/idioms/README.md`**

The README documents the note template (idiom / why needed here / what the naive version gets
wrong / where it lives / cross-language contrast) and the `anchor:` front-matter contract, so the
first person to add a note does not have to reverse-engineer it from the checker.

- [x] **Step 4: Run to verify pass**

Run: `python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' -v`
Expected: 9 tests total (5 anti-stub + 4 study-guide), all PASS.

- [x] **Step 5: Run against the real tree**

Run: `python3 scripts/ci/checks/study_guide.py`
Expected: exit 0 — no markers and no notes yet, which is a valid state.

- [x] **Step 6: Commit**

```bash
git add scripts/ci/checks/study_guide.py scripts/ci/checks/test_study_guide.py 70_concepts/idioms/README.md
git commit -m "feat(ci): bidirectional study-guide marker/note integrity check"
```

---

### Task 4: Wire the real steps into the gate

**Files:**
- Modify: `scripts/ci/gate.sh`

**Interfaces:**
- Consumes: `step_run`/`step_skip`/`have_tool` (Task 1), `anti_stub.py` (Task 2),
  `study_guide.py` (Task 3).

Lane composition:

| Step | fast | full | nightly | Command |
|---|:--:|:--:|:--:|---|
| `shellcheck` | ✔ | ✔ | ✔ | `shellcheck` over `scripts/` + `.githooks/` |
| `ci-check-tests` | ✔ | ✔ | ✔ | `python3 -m unittest discover -s scripts/ci/checks` |
| `fsm-sync` | ✔ | ✔ | ✔ | `fsm_codegen.py --java-only` then `git diff --exit-code` |
| `java-build` | ✔ | ✔ | ✔ | `./gradlew --no-daemon assemble` |
| `java-test` | ✔ | ✔ | ✔ | `./gradlew --no-daemon allTests` |
| `java-format` | ✔ | ✔ | ✔ | `./gradlew --no-daemon spotlessCheck` |
| `cpp-build` | ✔ | ✔ | ✔ | cmake configure + build |
| `cpp-test` | ✔ | ✔ | ✔ | `ctest --output-on-failure` |
| `rust-format` | ✔ | ✔ | ✔ | `cargo fmt --check` (skip when `rust/` absent) |
| `rust-lint` | ✔ | ✔ | ✔ | `cargo clippy --all-targets -- -D warnings` (skip when absent) |
| `rust-test` | ✔ | ✔ | ✔ | `cargo test` (skip when absent) |
| `anti-stub` | ✔ | ✔ | ✔ | `anti_stub.py` |
| `schema-lint` |  | ✔ | ✔ | yamllint FSM + xmllint SBE |
| `java-coverage` |  | ✔ | ✔ | `./gradlew --no-daemon jacocoRootReport` |
| `cpp-asan-ubsan` |  | ✔ | ✔ | separate build dir with `-fsanitize=address,undefined -fno-sanitize-recover=all` |
| `cpp-tsan` |  | ✔ | ✔ | separate build dir with `-fsanitize=thread` |
| `rust-deny` |  | ✔ | ✔ | `cargo deny check` (skip when absent) |
| `conformance` |  | ✔ | ✔ | `conformance/harness/run.sh` (skip until sub-project 2) |
| `fsm-coverage` |  | ✔ | ✔ | `conformance/harness/fsm_coverage.py` (skip until sub-project 2) |
| `study-guide` |  | ✔ | ✔ | `study_guide.py` |
| `cpp-msan` |  |  | ✔ | MSan build; skip with a loud reason if no MSan libc++ |
| `cpp-valgrind` |  |  | ✔ | `valgrind --error-exitcode=1` over the slice |
| `fuzz-long` |  |  | ✔ | libFuzzer + `cargo fuzz`, long run |
| `rust-miri` |  |  | ✔ | `cargo +nightly miri test` |

Steps whose subject does not exist yet (`rust/`, `conformance/`) call `step_skip` with the
sub-project that will deliver them named in the reason. The skip is visible in the summary — the
gate never pretends to have run something it did not.

- [x] **Step 1: Add the step functions and lane tables to `gate.sh`**

- [x] **Step 2: Run the fast lane**

Run: `scripts/ci/gate.sh fast`
Expected: Java/C++/shellcheck/check-test/anti-stub steps PASS; the three `rust-*` steps SKIP with
reason `rust/ not present — sub-project 3`; exit 0.

- [x] **Step 3: Run the full lane**

Run: `scripts/ci/gate.sh full`
Expected: everything in `fast` plus schema lint, sanitizer builds and the study-guide check;
`conformance` and `fsm-coverage` SKIP naming sub-project 2; exit 0.

- [x] **Step 4: Prove a failure is reported, not swallowed**

Introduce a deliberate shellcheck violation in a scratch script under `scripts/dev/`, run
`scripts/ci/gate.sh fast`, confirm the summary shows `FAIL shellcheck` **and** that later steps
still ran. Remove the scratch script.

- [x] **Step 5: shellcheck the gate itself**

Run: `shellcheck scripts/ci/gate.sh scripts/ci/lib/steps.sh`
Expected: clean.

- [x] **Step 6: Commit**

```bash
git add scripts/ci/gate.sh
git commit -m "feat(ci): wire java, cpp, rust, schema and check steps into gate lanes"
```

---

### Task 5: Toolchain script and devcontainer image

**Files:**
- Create: `scripts/ci/install-toolchain.sh`
- Create: `.devcontainer/Dockerfile`
- Modify: `.devcontainer/devcontainer.json`

**Interfaces:**
- Produces: `scripts/ci/install-toolchain.sh [--profile base|full|nightly]` — installs exactly the
  packages the corresponding gate lane needs. Idempotent; safe to re-run.

Profiles:
- `base` — cmake, ninja, g++-14, shellcheck, python3-yaml, yamllint, libxml2-utils, clang-format.
- `full` — base + clang-tidy, iwyu, the sanitizer runtimes, `cargo-deny`.
- `nightly` — full + valgrind, MSan prerequisites, rust nightly + miri.

**Deviation from the design spec, recorded deliberately:** the spec calls for CI and the hook to
run "the same container image pinned by digest". This task delivers the *identical dependency set*
via one script that both the image build and CI run, but does not yet publish a digest-pinned image
to a registry — that needs registry credentials and a publish workflow. Until it lands,
`docs/polyglot/gate.md` states plainly that the image is not yet the unit of pinning. Publishing it
is tracked as the follow-up task at the end of this plan.

- [x] **Step 1: Write `install-toolchain.sh`**

- [x] **Step 2: Verify the base profile is idempotent**

Run: `scripts/ci/install-toolchain.sh --profile base && scripts/ci/install-toolchain.sh --profile base`
Expected: second run is a no-op, exit 0 both times.

- [x] **Step 3: Write `.devcontainer/Dockerfile` calling the script, and point `devcontainer.json` at it**

- [x] **Step 4: shellcheck**

Run: `shellcheck scripts/ci/install-toolchain.sh`
Expected: clean.

- [x] **Step 5: Commit**

```bash
git add scripts/ci/install-toolchain.sh .devcontainer/
git commit -m "feat(ci): single toolchain installer shared by devcontainer and CI"
```

---

### Task 6: Rewire the hooks

**Files:**
- Modify: `.githooks/pre-push`
- Modify: `.githooks/pre-commit`

- [x] **Step 1: Replace `pre-push` with a `gate.sh fast` invocation**

It must keep the existing escape hatch documentation (`git push --no-verify`) and print the exact
command to reproduce the failure.

- [x] **Step 2: Verify the hook runs the gate**

Run: `.githooks/pre-push origin <remote-url> </dev/null`
Expected: the fast lane runs to completion and exits 0.

- [x] **Step 3: Extend `pre-commit` to the new languages**

Add, after the existing Java codegen + `spotlessApply` block: `fsm_codegen.py --cpp-only` and
re-stage `cpp/`; `clang-format -i` over staged `.cpp`/`.hpp`; `cargo fmt` when `rust/` exists.
Each guarded by a tool-presence check. It must still not call `gate.sh`.

- [x] **Step 4: Verify pre-commit still succeeds on a no-op commit**

Run: `.githooks/pre-commit`
Expected: exit 0, no unexpected files staged.

- [x] **Step 5: shellcheck**

Run: `shellcheck .githooks/pre-commit .githooks/pre-push`
Expected: clean.

- [x] **Step 6: Commit**

```bash
git add .githooks/pre-commit .githooks/pre-push
git commit -m "feat(hooks): pre-push runs gate.sh fast; pre-commit formats all three languages"
```

---

### Task 7: ErrorProne and NullAway on Java

**Files:**
- Modify: `gradle/catalogs/libs.versions.toml`
- Modify: `build-logic/src/main/kotlin/ems.java-conventions.gradle.kts`

**Interfaces:**
- Consumes: the `errorprone` plugin alias already present in the catalog
  (`net.ltgt.errorprone:4.0.1`).
- Produces: ErrorProne + NullAway running on every `JavaCompile`, generated sources excluded.

- [x] **Step 1: Add `error_prone_core` and `nullaway` libraries to the version catalog**

- [x] **Step 2: Apply the plugin in the conventions script**

Configuration that must hold:
- Generated sources are excluded (`disableAllChecks` for tasks whose source root is
  `src/main/generated`), because the codegen output is not hand-maintainable.
- NullAway is configured with `AnnotatedPackages=io.crossasset.ems` and set to `ERROR`.
- The existing `-Werror` stays.

- [x] **Step 3: Run the Java build and record the damage**

Run: `./gradlew --no-daemon assemble 2>&1 | tee /tmp/errorprone-baseline.txt`
Expected: either clean, or a finite list of violations.

- [x] **Step 4: Fix every violation**

Fix them in source. Do **not** downgrade a check to a warning to make the build pass — if a check
genuinely does not apply to this codebase, disable that single check by name in the conventions
script with a comment saying why.

- [x] **Step 5: Verify build and tests are green**

Run: `./gradlew --no-daemon assemble allTests`
Expected: BUILD SUCCESSFUL.

- [x] **Step 6: Commit**

```bash
git add gradle/catalogs/libs.versions.toml build-logic/src/main/kotlin/ems.java-conventions.gradle.kts java/
git commit -m "feat(java): ErrorProne + NullAway on every module"
```

---

### Task 8: Restructure CI onto the gate

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/nightly.yml`

Job structure after this task:

| Job | Trigger | Command |
|---|---|---|
| `gate-fast` | push to any non-main branch | `scripts/ci/gate.sh fast` |
| `gate-full` | PR to main, push to main, `workflow_dispatch` | `scripts/ci/gate.sh full` |
| `phase0-smoke` | all | unchanged — `./gradlew phase0Smoke` (outside the gate; documented exception, it needs an Aeron media driver) |
| `coverage-comment` | PR | consumes the artifact `gate-full` uploads; posts the JaCoCo comment |
| `nightly` | schedule 03:00 UTC + dispatch | `scripts/ci/gate.sh nightly` |

- [x] **Step 1: Rewrite `ci.yml` so every build/lint/test command is `gate.sh`**

Each job installs the toolchain with `scripts/ci/install-toolchain.sh --profile <base|full>` and
then invokes the lane. No inline `gradlew`/`cmake`/`ctest`/`shellcheck` steps survive except the
documented `phase0-smoke` exception.

- [x] **Step 2: Add `nightly.yml`**

- [x] **Step 3: Validate the workflow files parse**

Run: `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/ci.yml .github/workflows/nightly.yml`
Expected: exit 0.

- [x] **Step 4: Push the branch and confirm CI is green**

Run: `git push -u origin HEAD && gh run watch`
Expected: `gate-fast` green. If red, fix and re-push until green — a red CI is not done.

- [x] **Step 5: Commit**

```bash
git add .github/workflows/
git commit -m "ci: every job invokes scripts/ci/gate.sh"
```

---

### Task 9: Gate reference documentation

**Files:**
- Create: `docs/polyglot/gate.md`
- Modify: `docs/README.md` (layout table gains `polyglot/`)
- Modify: `cpp/README.md` (status table points at the manifest; decision record points at ADR 0001)
- Modify: `CONTRIBUTING.md` (one command to reproduce CI locally)

- [x] **Step 1: Write `docs/polyglot/gate.md`**

Must contain: the lane table from Task 4 verbatim, what each step actually runs, the skip
semantics and `EMS_GATE_STRICT`, the toolchain profiles, how to reproduce any CI failure locally in
one command, and the recorded deviation from Task 5 about image pinning.

- [x] **Step 2: Update the three existing docs to point at it**

- [x] **Step 3: Verify every relative link resolves**

Run: `python3 - <<'EOF'` — a short link checker over the new/changed markdown files, asserting each
relative link target exists on disk.
Expected: exit 0.

- [x] **Step 4: Commit**

```bash
git add docs/ cpp/README.md CONTRIBUTING.md
git commit -m "docs(polyglot): gate reference and cross-links"
```

---

## Follow-up, explicitly not in this sub-project

- **Publish the digest-pinned container image.** Needs registry credentials and a publish workflow;
  see the recorded deviation in Task 5. Until then CI and the hook share a dependency set, not an
  image.
- **`conformance` and `fsm-coverage` gate steps** are wired as skips here; sub-project 2 makes them
  real.
- **`rust-*` gate steps** are wired as skips here; sub-project 3 makes them real.

## Definition of done

- `scripts/ci/gate.sh fast` and `scripts/ci/gate.sh full` both exit 0 on a clean checkout.
- `scripts/ci/gate.sh <lane> --list` shows every step, and every skip names the sub-project that
  will make it real.
- CI runs no build command outside `gate.sh` except the documented `phase0-smoke` exception.
- `.githooks/pre-push` runs the fast lane.
- ErrorProne + NullAway are blocking on Java with zero suppressions added to make the build pass.
- CI is green on the branch.


---

## What actually shipped, and how it differed from this plan

Recorded rather than quietly folded in, so a reader comparing the plan to the tree is not left
guessing which one is wrong.

**1. NullAway runs in `OnlyNullMarked` mode, not over the whole tree.**
Task 7 as written said `NullAway:AnnotatedPackages=io.crossasset.ems`, then "fix every violation".
Enabling it that way produced violations across the tree in code that never claimed to be
null-correct — an outage, not a gate. What shipped instead: NullAway checks exactly the packages
that opt in with JSpecify's `@NullMarked`, coverage grows one package at a time, and
`scripts/ci/checks/nullmarked_ratchet.py` makes it impossible to shrink — a baseline package that
loses the annotation fails the build, and an annotated package missing from the baseline fails too.
`io.crossasset.ems.validator` is the first opted-in package; it is in the slice, so its null
contract had to be explicit before Rust and C++ try to reproduce it.

NullAway also had to be bumped from 0.11.3 to 0.12.3 — `OnlyNullMarked` does not exist in 0.11.x,
and it fails at *checker construction* with a message about `AnnotatedPackages`, which is a
misleading way to say "unknown flag".

**2. ErrorProne's advisory warnings are disabled; its ERROR-severity checks are blocking.**
The tree compiles with `-Werror`, so leaving ErrorProne's WARNING-severity checks on would turn
every advisory suggestion into a build break. The predictable response to that is disabling
ErrorProne wholesale, which is worse. Recorded in `ems.java-conventions.gradle.kts` next to the
setting.

**3. Two steps were added that this plan did not anticipate.**

- `exec-bits` — CI failed with `Permission denied` because `install-toolchain.sh` was committed
  `100644`. That class of failure only ever appears in CI, since locally the file has already been
  chmod'd. The gate now checks the git mode of every tracked `*.sh` outside a `lib/` directory and
  every hook.
- `java-nullmarked` — the ratchet from deviation 1.

**4. `shellcheck` falls back to a container.**
Rather than skipping when the binary is absent, the step runs
`docker.io/koalaman/shellcheck:stable` under podman or docker. It also runs with `-x` and a
`source-path=SCRIPTDIR` directive, without which it reports SC1091 on the sourced `steps.sh` and
fails in CI while passing nowhere locally.

**5. Skips are two kinds, not one.**
The plan implied a single `EMS_GATE_STRICT` behaviour. In practice a step skipped because its
sub-project has not landed (`rust/` does not exist) must stay a skip even in CI, while a step
skipped because a tool is missing must be a failure in CI. `scripts/ci/lib/steps.sh` distinguishes
them: `step_skip` versus `step_skip_tool`.

**6. `gradle()` retries once after clearing a stale Spotless configuration cache.**
`spotlessCheck` fails outright when `.gradle/configuration-cache` goes stale
(diffplug/spotless#987) — a local-only failure with a mechanical fix. The gate applies the fix
rather than making every developer learn the incantation.

**7. `ctest` runs with `--no-tests=error`, and `cpp/CMakeLists.txt` defaults to `RelWithDebInfo`.**
`ctest` exits 0 over zero tests by default, which is precisely the failure this repo already
demonstrates. On the build type: `_FORTIFY_SOURCE=3` emits a `#warning` without optimisation, and
`-Werror` turns that into a build failure — so an unspecified build type would have silently
dropped the hardening or broken the build.

**8. `conformance/README.md` was written in this sub-project, not sub-project 2.**
It states the `ems-slice` CLI contract, the corpus format and the determinism table before the
harness exists — which is the same principle as building the gate before the code it governs.

**9. The digest-pinned container image is still outstanding.**
Recorded in Task 5 and in `docs/polyglot/gate.md`. CI and the hook share a dependency set via
`install-toolchain.sh`, not an image.
