# Polyglot Port — Sub-project 5: C++ Slice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the eight slice-relevant C++ stubs with a real implementation of the cash-equity
order path, byte-identical to Java, and clean under the full sanitizer and static-analysis stack.

**Architecture:** Each module gets real source under `cpp/<module>/src` and `include/`, plus a
GoogleTest target. Modules land in dependency order, each green before the next starts. The final
target is `ems-slice` under `cpp/ems-it`. The design questions — ownership, aliasing, iterator
invalidation — were already answered by the Rust port in sub-project 4; this port implements the
answers rather than rediscovering them under a sanitizer.

**Tech Stack:** C++20, CMake 3.25 + Ninja, GoogleTest via `FetchContent`, `std::expected` (or a
vendored equivalent if the toolchain lacks it), clang-tidy, ASan/UBSan/TSan/MSan, libFuzzer,
Valgrind.

**Depends on:** sub-project 2. Sequenced after sub-project 4 on purpose — see
`docs/polyglot/README.md` §Sequencing.

## Global Constraints

- No exceptions in steady-state code paths. Errors are `std::expected<T, E>`.
- No dynamic allocation in the per-event hot path. Allocate at construction, reuse thereafter.
- No `HashMap`-equivalent whose iteration order can reach the output journal — `std::map` /
  `std::set` only. A `clang-tidy` check enforces it.
- `#pragma once` in every header. No include guards, no `using namespace` at namespace scope in a
  header.
- Prices are `std::int64_t` in scaled minor units, wrapped in a strong type. No floats.
- Every `NOLINT` carries an inline justification. A bare `NOLINT` fails review.
- Each module lands with its tests in the same commit and flips to `done` in
  `scripts/ci/slice-manifest.yaml` in that same commit — never ahead of it.
- Idiom notes are written as idioms land.

---

## File Structure

| Module | Responsibility | Lands in task |
|---|---|---|
| `cpp/ems-core` | `JournalEvent`, JSONL codec, `DeterministicIds`, scaled-price strong types | 2 |
| `cpp/ems-transport` | `Transport` interface + `JournalTransport` | 3 |
| `cpp/ems-aaa` | Session lookup + entitlement decision | 4 |
| `cpp/ems-validator` | Five validation layers | 5 |
| `cpp/ems-oms` | Staging + routing | 6 |
| `cpp/ems-venue-connectivity` | FIX out, ExecutionReport in, session state | 7 |
| `cpp/ems-posttrade` | Allocation | 8 |
| `cpp/ems-it` | The `ems-slice` binary | 9 |

---

### Task 1: Test infrastructure and the tightened toolchain

**Files:**
- Modify: `cpp/CMakeLists.txt` (GoogleTest via `FetchContent`, an `ems_add_module` helper)
- Create: `cpp/cmake/EmsModule.cmake`
- Create: `.clang-tidy`
- Create: `.clang-format`

**Interfaces:**
- Produces: `ems_add_module(<name> SOURCES … HEADERS … TEST_SOURCES …)` — one call per module,
  so a module cannot accidentally be added without a test target.

`.clang-tidy` (from ADR 0004):

```yaml
Checks: >
  bugprone-*, cppcoreguidelines-*, modernize-*, performance-*,
  readability-*, cert-*, misc-*,
  -modernize-use-trailing-return-type
WarningsAsErrors: '*'
HeaderFilterRegex: '.*/(ems-[a-z-]+)/(include|src)/.*'
CheckOptions:
  - key: readability-identifier-naming.ClassCase
    value: CamelCase
```

- [ ] **Step 1: Write `EmsModule.cmake` and wire GoogleTest**

- [ ] **Step 2: Prove the helper refuses a module with no tests**

Add a temporary `ems_add_module(ems-scratch SOURCES scratch.cpp)` with no `TEST_SOURCES`, configure,
confirm CMake errors, then remove it. A guard you have not seen fire is a guard you do not have.

- [ ] **Step 3: Verify the existing tree still builds**

Run: `scripts/ci/gate.sh fast`
Expected: `cpp-build` and `cpp-test` PASS.

- [ ] **Step 4: Add the `cpp-tidy` step to `gate.sh`**

Requires a compilation database: add `set(CMAKE_EXPORT_COMPILE_COMMANDS ON)`.

- [ ] **Step 5: Commit**

```bash
git add cpp/ .clang-tidy .clang-format scripts/ci/gate.sh
git commit -m "build(cpp): GoogleTest, module helper, clang-tidy configuration"
```

---

### Task 2: `ems-core`

**Files:**
- Create: `cpp/ems-core/include/ems_core/{journal.hpp,ids.hpp,units.hpp}`
- Create: `cpp/ems-core/src/{journal.cpp,ids.cpp}`
- Test: `cpp/ems-core/test/{journal_test.cpp,ids_test.cpp,units_test.cpp}`

**Interfaces:**
- Produces:
  ```cpp
  namespace ems::core {
  struct JournalEvent {
      std::uint64_t seq{};
      std::string type;
      std::map<std::string, std::string> fields;   // ordered: iteration reaches the journal
  };
  [[nodiscard]] std::expected<std::vector<JournalEvent>, JournalError> read_journal(const std::filesystem::path&);
  [[nodiscard]] std::expected<void, JournalError> write_journal(const std::filesystem::path&, std::span<const JournalEvent>);

  class DeterministicIds {
  public:
      explicit DeterministicIds(std::uint64_t seed);
      [[nodiscard]] std::string next_order_id();
      [[nodiscard]] std::string next_route_id();
      [[nodiscard]] std::string next_exec_id();
  };

  class Price { public: explicit Price(std::int64_t raw); [[nodiscard]] std::int64_t raw() const; };
  }
  ```

**Idiom notes due here:** `// STUDY: expected-without-exceptions` at the first
`std::expected` return, and `// STUDY: span-at-boundaries` at `write_journal`'s `std::span`
parameter.

- [ ] **Step 1: Write the failing tests**

Same three properties as the Rust crate, including the byte-identical fixture test:

```cpp
TEST(Journal, OutputIsByteIdenticalToTheJavaReferenceFixture) {
  const auto expected = read_file("test/fixtures/java_reference.jsonl");
  write_journal(tmp_path(), fixture_events());
  EXPECT_EQ(read_file(tmp_path()), expected);
}

TEST(Journal, MalformedLineReportsItsLineNumber) {
  write_file(tmp_path(), "{\"seq\":1}\nnot json\n");
  const auto result = read_journal(tmp_path());
  ASSERT_FALSE(result.has_value());
  EXPECT_THAT(result.error().message, testing::HasSubstr("line 2"));
}

TEST(Ids, MatchesTheJavaIdFormatExactly) {
  DeterministicIds ids{0};
  EXPECT_EQ(ids.next_order_id(), "ORD-0000000001");
}
```

- [ ] **Step 2: Run to verify failure**

Run: `scripts/ci/gate.sh fast` (or `cmake --build build/cpp && ctest --test-dir build/cpp`)
Expected: FAIL — nothing to link.

- [ ] **Step 3: Implement**

If the toolchain lacks `std::expected`, vendor a minimal equivalent in `ems_core` under an
`ems::core::expected` alias and record why in a `STUDY:` note — do not fall back to exceptions or
to `std::optional` plus an out-parameter.

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Run the sanitizer builds on this module**

```bash
scripts/ci/gate.sh full
```
Expected: `cpp-asan-ubsan` and `cpp-tsan` PASS. Anything they report is fixed **now**, while the
module is small enough to reason about.

- [ ] **Step 6: Write the idiom notes, then manifest and commit**

```bash
python3 scripts/ci/checks/study_guide.py
git commit -am "feat(cpp): ems-core journal codec, deterministic ids, scaled-price types"
```

---

### Task 3: `ems-transport`

**Files:**
- Create: `cpp/ems-transport/include/ems_transport/transport.hpp`,
  `include/ems_transport/journal_transport.hpp`, `src/journal_transport.cpp`
- Test: `cpp/ems-transport/test/journal_transport_test.cpp`

**Interfaces:**
- Produces:
  ```cpp
  class Transport {
  public:
      virtual ~Transport() = default;
      [[nodiscard]] virtual std::expected<std::vector<JournalEvent>, TransportError> drain() = 0;
      [[nodiscard]] virtual std::expected<void, TransportError> publish(JournalEvent) = 0;
      [[nodiscard]] virtual std::expected<void, TransportError> flush() = 0;
  };
  ```

**Idiom note due here:** `// STUDY: virtual-dtor-and-rule-of-zero` on the base class — this is the
canonical place to explain why the destructor is virtual and why the derived types declare no
special members at all.

- [ ] **Step 1–6:** as Task 2 — failing tests, run-fail, implement, run-pass, sanitizers, note,
      manifest, commit.

```bash
git commit -am "feat(cpp): Transport interface and file-backed JournalTransport"
```

---

### Task 4: `ems-aaa`

Mirrors Rust Task 3. Same interfaces, same reject codes, same message wording.

- [ ] **Step 1–6:** failing tests, run-fail, implement, run-pass, sanitizers, manifest, commit.

```bash
git commit -am "feat(cpp): ems-aaa session lookup and entitlement decision"
```

---

### Task 5: `ems-validator`

Mirrors Rust Task 4. `std::optional<std::string>` where Java has `@Nullable String` and Rust has
`Option<String>`.

**Idiom note due here:** extend `70_concepts/idioms/option-vs-nullable.md` with the C++ column and
add a second `STUDY: option-vs-nullable` marker at the C++ usage site. The checker supports several
markers per note; the note's anchor points at whichever site best explains it, and the
cross-language contrast section covers the rest.

- [ ] **Step 1–6:** failing tests, run-fail, implement, run-pass, sanitizers, note, manifest, commit.

```bash
git commit -am "feat(cpp): ems-validator five-layer pipeline"
```

---

### Task 6: `ems-oms`

Mirrors Rust Task 5.

**Idiom notes likely due here:**

- `// STUDY: iterator-invalidation-route-table` — the route table is mutated while being iterated
  in at least one path. Whatever the resolution (index-based loop, deferred mutation queue, stable
  container), the note explains what the naive `for (auto& r : routes) routes.emplace(...)` version
  does, which is undefined behaviour that happens to work until it does not.
- `// STUDY: crtp-static-dispatch` if the FSM dispatch path ends up using CRTP rather than virtual
  calls. The note must say what measurement or constraint justified it — "it is faster" without a
  number is exactly the kind of claim a study guide should not contain.

- [ ] **Step 1–6:** failing tests, run-fail, implement, run-pass, sanitizers, notes, manifest,
      commit.

```bash
git commit -am "feat(cpp): ems-oms staging and in-memory routing"
```

---

### Task 7: `ems-venue-connectivity`

Mirrors Rust Task 6, including the hostile-input decoder tests.

**Idiom note likely due here:** `// STUDY: bit-cast-strict-aliasing` in the SBE decode path —
`reinterpret_cast` over a byte buffer is undefined behaviour that UBSan will not always catch;
`std::bit_cast` is the defined way to do it. The note explains the difference concretely.

- [ ] **Step 1–6:** failing tests, run-fail, implement, run-pass, sanitizers, note, manifest, commit.

```bash
git commit -am "feat(cpp): ems-venue-connectivity FIX edge and session state"
```

---

### Task 8: `ems-posttrade`

Mirrors Rust Task 7.

- [ ] **Step 1–6:** failing tests, run-fail, implement, run-pass, sanitizers, manifest, commit.

```bash
git commit -am "feat(cpp): ems-posttrade allocation"
```

---

### Task 9: The `ems-slice` binary and conformance

**Files:**
- Create: `cpp/ems-it/src/{slice_main.cpp,slice_runner.cpp}`, `include/ems_it/slice_runner.hpp`
- Test: `cpp/ems-it/test/slice_runner_test.cpp`

- [ ] **Step 1: Write the failing CLI tests**

Missing `--input` exits 2; malformed journal exits 1 without a crash or an uncaught exception;
default seed is 0.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement, mirroring Java's `SliceRunner` event for event**

- [ ] **Step 4: Run the harness against C++ alone**

```bash
cmake --build build/cpp
conformance/harness/run.sh --impl cpp
```
Expected: failures at first, then convergence. Same rule as sub-project 4: fix C++ to match Java,
never the expectation to match C++.

- [ ] **Step 5: Run the harness against all three**

```bash
conformance/harness/run.sh
```
Expected: every case passes against Java, Rust and C++. **This is the moment the project's central
claim becomes true.**

- [ ] **Step 6: Manifest and commit**

```bash
git commit -am "feat(cpp): ems-slice binary passes the conformance corpus"
```

---

### Task 10: The heavy lane

**Files:**
- Create: `cpp/fuzz/{fix_decode_fuzz.cpp,sbe_decode_fuzz.cpp,journal_parse_fuzz.cpp}`
- Modify: `cpp/CMakeLists.txt` (libFuzzer targets, guarded on Clang)
- Modify: `scripts/ci/gate.sh` (`cpp-valgrind` and `fuzz-long` stop skipping)

- [ ] **Step 1: Write the three libFuzzer targets, seeded from the conformance corpus**

Share the seed corpus directory with the Rust targets.

- [ ] **Step 2: Run each briefly**

```bash
build/cpp-asan/cpp/fuzz/fix_decode_fuzz -max_total_time=60 conformance/corpus-seeds/
```
Expected: no crashes. A crash is a real bug — fix it and add the input as a unit test.

- [ ] **Step 3: Run under Valgrind**

```bash
valgrind --error-exitcode=1 --leak-check=full \
  build/cpp/ems-it/ems-slice --input <case>/input.jsonl --output /tmp/out.jsonl
```
Expected: no errors, no leaks.

- [ ] **Step 4: Decide MSan**

Either build an MSan-instrumented libc++ and set `EMS_MSAN_LIBCXX`, or record the decision to rely
on ASan + Valgrind in `docs/decisions/`. **Do not leave it as a permanently-skipped step with no
decision behind it** — that is the open question from the design spec, and this is where it gets
answered.

- [ ] **Step 5: Run the nightly lane end to end**

Run: `EMS_GATE_STRICT=1 scripts/ci/gate.sh nightly`
Expected: exit 0, with any remaining skip having a decision recorded against it.

- [ ] **Step 6: Commit**

```bash
git commit -am "test(cpp): libFuzzer targets, Valgrind clean, nightly lane green"
```

---

### Task 11: Closing out

**Files:**
- Modify: `cpp/README.md` (the stub banner comes off; status table reflects reality)
- Modify: `docs/polyglot/README.md`, `docs/polyglot/gate.md`

- [ ] **Step 1: Rewrite the `cpp/README.md` status banner**

It currently says, accurately, that every module is a stub. Replace it with what is now true —
including which modules are still stubs, because seven of them are and will stay that way (ADR
0002).

- [ ] **Step 2: Verify the anti-stub check agrees with the README**

Run: `python3 scripts/ci/checks/anti_stub.py`
Expected: exit 0.

- [ ] **Step 3: Run the full gate and push**

Run: `EMS_GATE_STRICT=1 scripts/ci/gate.sh full && git push && gh run watch`

- [ ] **Step 4: Commit**

```bash
git commit -am "docs(cpp): sub-project 5 complete"
```

---

## Definition of done

- Every corpus case passes against all three implementations, byte-for-byte.
- `clang-tidy` clean with the ADR 0004 check set, `WarningsAsErrors: '*'`, and no bare `NOLINT`.
- ASan+UBSan and TSan clean over the full test suite and a full slice run.
- Valgrind memcheck clean over a full slice run.
- Fuzz targets run without crashing; any crash found is fixed and captured as a unit test.
- MSan is either running or has a recorded decision saying why it is not.
- The eight slice modules are `done` in the manifest; the other seven are honestly still `stub`.
- One idiom note per `STUDY:` marker, each with a resolving anchor and a filled-in cross-language
  contrast section.
