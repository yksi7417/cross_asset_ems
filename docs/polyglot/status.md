# Per-language status and test evidence

Measured on 2026-07-31 at `feat/polyglot-fsm`. Every number came from running the thing — commands
are given so you can re-derive any of them.

For what the port *is*, start at [`README.md`](README.md). This page answers two questions: where
does each language stand, and **what test proves it**.

Evidence is classified, because a matrix citing only happy-path tests proves less than it appears
to:

- 🟢 **happy** — the feature does what it is for
- 🟡 **edge** — boundary, empty, ordering, idempotence, encoding
- 🔴 **negative** — malformed, hostile, or absent input must be *refused*, not crash

---

## Test status at a glance

| | Java | Rust | C++ |
|---|---:|---:|---:|
| **Tests passing** | **2,374** | **78** | **65** |
| Failures / errors / skipped | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Suites | 14 modules | 9 | 7 ctest targets |

```bash
./gradlew allTests                                # Java
cargo test --manifest-path rust/Cargo.toml --all  # Rust
ctest --test-dir build/cpp --no-tests=error       # C++
```

**2,374 vs 78 vs 65 is a scope gap, not a quality gap.** Java implements the whole system — seven
asset classes, SOR, algos, multi-leg, compliance, the trader desktop. Rust and C++ implement the
cash-equity slice and nothing else (ADR 0002). The per-component tables below compare like with
like.

---

## Component 1 — journal codec + deterministic ids

The byte contract every later component inherits.

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 21 (15 codec + 6 ids) | 23 (18 + 5) | 25 (20 + 5) |
| File | [`JournalCodecTest.java`](../../java/ems-core/src/test/java/io/crossasset/ems/core/journal/JournalCodecTest.java) · [`DeterministicIdsTest.java`](../../java/ems-core/src/test/java/io/crossasset/ems/core/journal/DeterministicIdsTest.java) | [`journal_test.rs`](../../rust/ems-core/tests/journal_test.rs) · [`ids.rs`](../../rust/ems-core/src/ids.rs) | [`journal_test.cpp`](../../cpp/ems-core/test/journal_test.cpp) · [`ids_test.cpp`](../../cpp/ems-core/test/ids_test.cpp) |

| | Evidence (same test in all three unless noted) |
|---|---|
| 🟢 | `writesTopLevelAndFieldKeysInLexicographicOrder`, `roundTripsWithoutLoss`, `producesTheAgreedStringForm` |
| 🟡 | `everyLineEndsWithNewlineIncludingTheLast`, `emptyJournalRoundTrips`, `blankLinesAreIgnoredOnRead`, `escapesQuotesBackslashesAndControlCharacters`, `nonAsciiIsWrittenAsUtf8NotEscaped`, `fieldsAreOrderedRegardlessOfInsertionOrder`, `widthHoldsUntilTheCounterOutgrowsIt` (10-digit id overflow widens, does not wrap), `countersAreIndependentPerPrefix` |
| 🔴 | `malformedLineReportsItsLineNumber`, `unknownTopLevelKeyIsRejected`, `missingRequiredKeyIsRejected`, `duplicateTopLevelKeyIsRejected`, `nonStringFieldValueIsRejected`, `trailingContentAfterTheObjectIsRejected`, `negativeSequenceIsRejected`, `loneSurrogateEscapeIsRejected`, **`hostileInputNeverPanics`** (~20 truncated/malformed inputs), `veryLongInputIsHandledWithoutCrash` (100 kB value), `sequenceOverflowIsRejectedRatherThanWrapped` (C++), `negativeSeedIsRejected` (Java only — `u64` makes it unrepresentable in Rust/C++, see [[unrepresentable-invalid-state]]) |

Negative coverage is heaviest here on purpose: the journal parser is one of the three fuzz targets
in ADR 0004, so *every* malformed input must produce a diagnostic rather than a crash.

---

## Component 2 — transport seam (`JournalTransport`)

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 11 | 7 | 7 |
| File | [`JournalTransportTest.java`](../../java/ems-transport/src/test/java/io/crossasset/ems/transport/journal/JournalTransportTest.java) | [`lib.rs`](../../rust/ems-transport/src/lib.rs) (inline `mod tests`) | [`journal_transport_test.cpp`](../../cpp/ems-transport/test/journal_transport_test.cpp) |

| | Evidence |
|---|---|
| 🟢 | `publishedEventsAppearInOrderAfterFlush`, `drainReturnsTheInputJournalOnceAndThenEmpty` |
| 🟡 | **`publishBeforeFlushWritesNothing`** — a crashed run must not leave a half journal that reads as a legitimately short one; **`flushIsIdempotentRatherThanAppending`** — a second flush must not duplicate the journal; `flushWithNothingPublishedWritesAnEmptyFile`; `closeIsIdempotent`, `closeFlushes` (Java) |
| 🔴 | `malformedInputSurfacesAsMalformedJournalException`, `missingInputSurfacesAsUncheckedIoException`, `publishAfterCloseIsRejected` (Java), `drainedEventsCannotBeMutatedByTheCaller` (Java) |

The two bolded edge cases are the ones that would otherwise surface as a *conformance* failure ten
lines later, sending a reader hunting for a logic bug that is not there.

---

## Component 3 — AAA: sessions + three-layer AND-gate

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 11 gate + 3 clock (+54 more in `ems-aaa`) | 9 | 9 |
| File | [`TagPermissionTest.java`](../../java/ems-aaa/src/test/java/io/crossasset/ems/aaa/permission/TagPermissionTest.java) · [`AaaClockInjectionTest.java`](../../java/ems-aaa/src/test/java/io/crossasset/ems/aaa/AaaClockInjectionTest.java) | [`lib.rs`](../../rust/ems-aaa/src/lib.rs) | [`aaa_test.cpp`](../../cpp/ems-aaa/test/aaa_test.cpp) |

| | Evidence |
|---|---|
| 🟢 | `heldTagIsAllowedAtAllThreeLayers`, `registeredSessionIsFound` |
| 🟡 | **`firmDenialIsReportedBeforeTheDeskIsConsulted`** — the gate reports the *outermost* missing layer; `deskDenialWhenTheFirmGrantsButTheDeskDoesNot`; `userDenialWhenBothOuterLayersGrant`; `reLogonReplacesTheSession`; `emptyTagRequiresNoEntitlement`; `tagsIterateInLexicographicOrderRegardlessOfInsertionOrder` (the set reaches the journal, so its order is a contract) |
| 🔴 | `unknownSessionIsNone` / `unknownSessionIsNull` |

The three denial tests are the *reason* this component is more than a `set.contains`: each asserts a
different catalog code (`EMS-PRM-1003` / `1002` / `1001`) and message. Telling a user "you lack the
tag" when their whole firm was never granted it sends them to the wrong administrator.

Clock injection (Java): `sessionTimestampsComeFromTheInjectedTimeSource`,
`twoRunsOnTheSameFixedTimeProduceTheSameTimestamp`, `theDefaultConstructorStillUsesWallTime`.

---

## Component 4 — validation pipeline (SESSION → IDENTITY → REFERENCE → PERMISSION)

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 33 (20 pipeline + 10 denial-message + 3 catalog) | 10 | 10 |
| File | [`LayeredValidatorPipelineTest.java`](../../java/ems-validator/src/test/java/io/crossasset/ems/validator/LayeredValidatorPipelineTest.java) | [`lib.rs`](../../rust/ems-validator/src/lib.rs) | [`validator_test.cpp`](../../cpp/ems-validator/test/validator_test.cpp) |

Java carries two extra suites the ports do not yet mirror:
[`PermissionDenialMessageTest`](../../java/ems-validator/src/test/java/io/crossasset/ems/validator/PermissionDenialMessageTest.java)
(10 tests on exact denial wording) and
[`RejectCodeCatalogConsistencyTest`](../../java/ems-validator/src/test/java/io/crossasset/ems/validator/RejectCodeCatalogConsistencyTest.java)
(3 tests asserting every emitted code exists in `schemas/reject-codes/catalog.yaml`). The second is
the one that stops an invented code reaching the journal — the conformance gate compares the three
implementations to *each other*, not to the schema, so nothing else would catch it.

| | Evidence |
|---|---|
| 🟢 | `everythingValidPasses` |
| 🟡 | **`sessionIsCheckedBeforeReference`** and **`referenceIsCheckedBeforePermission`** — two faults in one request, asserting *which* is reported; `absentFigiSkipsTheReferenceLayer`; `absentTagSkipsThePermissionLayer` |
| 🔴 | `unknownSessionFailsAtTheSessionLayer`, `unknownFigiFailsAtTheReferenceLayer`, `inactiveInstrumentFailsAtTheReferenceLayer`, `missingTagFailsAtThePermissionLayer`, `absentSessionIdFailsAtTheSessionLayer` |

**The two ordering tests are the point of this component.** Every other test here would still pass
if one language checked PERMISSION first — nothing about a single-fault test constrains layer order,
and layer order is the property most likely to drift between three implementations.

---

## Component 5 — FSM codegen from `schemas/fsm/`

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 861 (pre-existing, all five machines + effects) | 13 | 1 (compile-only) |
| File | `java/ems-fsm/src/test/…` | [`order_fsm_test.rs`](../../rust/ems-fsm/tests/order_fsm_test.rs) | [`fsm_compile_test.cpp`](../../cpp/ems-fsm/test/fsm_compile_test.cpp) |
| Generator | [`test_generated_golden.py`](../../tools/codegen/test_generated_golden.py) — 4 tests pinning sha256 of all 36 generated files | | |

| | Evidence (Rust) |
|---|---|
| 🟢 | `initial_state_is_pending_new`, `validation_passed_moves_pending_new_to_new`, `validation_failed_moves_pending_new_to_rejected`, `state_names_match_the_schema_spelling` |
| 🟡 | `terminal_states_are_marked_terminal` (asserts `FILLED` is **not** terminal — a fill can still be busted); `a_terminal_state_accepts_nothing`; `a_guarded_transition_picks_the_arm_whose_guard_holds`; `an_unguarded_transition_fires_regardless_of_context`; `a_payload_carrying_transition_updates_the_context`; `the_context_is_not_mutated_in_place` |
| 🔴 | `an_event_with_no_matching_transition_is_ignored_not_an_error`; **`a_payload_carrying_transition_without_its_payload_is_a_no_transition`** — a truncated venue message is data, not a denial of service; `a_guard_that_holds_for_no_arm_is_a_no_transition` |

**C++ has one compile-only test here and no behavioural coverage**, because the FSM is not wired
into the C++ slice runner yet. Stating that plainly rather than letting the ✅ in the matrix imply
otherwise.

The golden test is the negative case for the *generator*: it pins the Java and C++ output so adding
a third emitter cannot silently perturb the other two.

---

## Slice runner / CLI (integration)

The end-to-end path, exercised through the actual binary.

| | Java | Rust | C++ |
|---|---|---|---|
| Tests | 15 | 16 | 14 |
| File | [`SliceMainTest.java`](../../java/ems-it/src/test/java/io/crossasset/ems/it/slice/SliceMainTest.java) | [`runner.rs`](../../rust/ems-slice/src/runner.rs) · [`main.rs`](../../rust/ems-slice/src/main.rs) | [`slice_runner_test.cpp`](../../cpp/ems-it/test/slice_runner_test.cpp) |

| | Evidence |
|---|---|
| 🟢 | `logonThenOrderIsAccepted`, `defaultSeedIsZero`, `seedShiftsGeneratedIdentifiers`, `logonEchoesTheGrantedTagsAtEveryLayer` |
| 🟡 | **`aRejectedOrderDoesNotConsumeAnIdentifier`** — ids must not depend on how many earlier orders failed, or every corpus case downstream of a rejection shifts; **`producesByteIdenticalOutputOnRepeatedRuns`** (Java); `emptyInputStillProducesARunSummary`; `outputSequenceIsContiguousFromOne`; `unrecognisedFieldsAreNotEchoed`; `reLogonReplacesTheIdentity`; `otherEventTypesPassThroughWithSequenceRenumbered` |
| 🔴 | `orderWithoutAKnownSessionIsRejected`, `orderMissingTheRequiredTagIsRejected`, **`aNonNumericSessionIdIsARejectionNotACrash`**, `malformedInputJournalExitsOneWithoutAStackTrace` (Java), `missingInputFileExitsOne` (Java), `missingInputArgumentIsAUsageError`, `unknownArgumentIsAUsageError`, `negativeSeedIsAUsageError`, `nonNumericSeedIsAUsageError`, `danglingFlagValueIsAnError` (Rust) |

---

## Cross-language integration: the conformance corpus

Unit tests prove each language agrees with its own expectations. **These prove the three agree with
each other, at the byte level.**

| Case | Covers | Kind |
|---|---|---|
| [`component-01-journal-and-ids`](../../conformance/corpus/component-01-journal-and-ids/case.md) | id ordering, field-echo filtering, UTF-8 raw vs escaped quotes/backslashes, `RunSummary` | 🟢🟡 |
| [`component-03-aaa-rejections`](../../conformance/corpus/component-03-aaa-rejections/case.md) | order before logon; all three denial layers; re-logon widening tags; non-numeric session id | 🔴🟡 |
| [`component-04-validation-layers`](../../conformance/corpus/component-04-validation-layers/case.md) | unknown FIGI; suspended instrument; **two faults where SESSION wins**; ungranted tag | 🔴🟡 |

**9 of 9** — three cases × three implementations, byte-identical.

```bash
conformance/harness/run.sh
```

The differ normalises nothing — not line endings, not key order, not trailing whitespace. Its own
11 tests ([`test_differ.py`](../../conformance/harness/test_differ.py)) assert that:
`trailing_newline_difference_is_a_difference`, `crlf_is_a_difference`,
`key_order_difference_is_a_difference`, `invalid_utf8_does_not_crash_the_report`.

And it has been **watched failing**: `scripts/dev/demo-polyglot.sh` re-runs Rust with a different
seed at the end so you can see a one-character divergence caught, rather than taking the green on
trust.

---

## Gate's own tests

The checks that police the tree are themselves tested — 47 tests, run by `ci-check-tests` in every
lane.

| Check | Tests | Notable negative cases |
|---|---:|---|
| [`anti_stub.py`](../../scripts/ci/checks/test_anti_stub.py) | 16 | `test_done_module_without_asserting_test_fails`, `test_pragma_once_only_header_is_not_behaviour`, `test_generated_source_does_not_count_as_behaviour`, `test_cfg_test_module_without_an_assertion_does_not_count` |
| [`study_guide.py`](../../scripts/ci/checks/test_study_guide.py) | 10 | `test_note_with_dangling_anchor_fails`, `test_anchor_pointing_at_a_different_marker_fails`, `test_note_anchor_past_end_of_file_fails` |
| [`no_raw_clock.py`](../../scripts/ci/checks/test_no_raw_clock.py) | 13 | `test_whitespace_between_tokens_still_matches` (caught a real hole — `System . nanoTime ()` would have slipped past), `test_stale_baseline_entry_is_a_violation` |
| [`nullmarked_ratchet.py`](../../scripts/ci/checks/test_nullmarked_ratchet.py) | 7 | `test_removing_the_annotation_fails`, `test_marking_without_baseline_entry_fails` |
| [`differ.py`](../../conformance/harness/test_differ.py) | 11 | listed above |

---

## Feature matrix

Eight components make up the cash-equity slice. Five are done in all three languages.

| # | Component | Java | Rust | C++ | Cross-language proof |
|---|---|:---:|:---:|:---:|---|
| 1 | Journal codec + deterministic ids | ✅ | ✅ | ✅ | `component-01` |
| 2 | Transport seam | ✅ | ✅ | ✅ | corpus unchanged across the refactor |
| 3 | AAA — sessions, 3-layer AND-gate | ✅ | ✅ | ✅ | `component-03` |
| 4 | Validation pipeline | ✅ | ✅ | ✅ | `component-04` |
| 5 | FSM codegen | ✅ | ✅ | ✅ | `fsm-sync` diffs all three |
| 5b | FSM wired into the slice runner | ❌ | ❌ | ❌ | — `fsm-coverage` still skips |
| 6 | Staging + routing (`ems-oms`) | ✅ † | ❌ | ❌ | — |
| 7 | Venue edge (FIX out, ExecutionReport in) | ✅ † | ❌ | ❌ | — |
| 8 | Fill handling / allocation | ✅ † | ❌ | ❌ | — |

† Java's ✅ in rows 6–8 is the pre-existing production tree, not slice work — no cross-language claim
attaches to it.

### Cross-cutting properties

| | Java | Rust | C++ |
|---|---|---|---|
| **Memory safety** | GC | `#![forbid(unsafe_code)]` workspace-wide | ASan + UBSan + TSan in CI |
| **Null / absence** | `@NullMarked` + NullAway, 4 packages, ratcheted | `Option<T>`; `unwrap`/`expect` denied | `std::optional`, unchecked deref |
| **Errors** | sealed interfaces; unchecked exceptions in the codec | `Result<T, E>`, `#[must_use]` by default | vendored `Result<T,E>` + `[[nodiscard]]` + `-Werror` |
| **Lint** | ErrorProne (ERROR-severity), Spotless | `clippy::pedantic` + `nursery`, `-D warnings` | `-Wall -Wextra -Wconversion -Wold-style-cast -Werror` |
| **Dependencies** | version catalog | `cargo-deny`: one licence allowed, no git sources | CMake `FetchContent`, GoogleTest pinned |
| **Clock** | `TimeSource` injected, `no_raw_clock.py` enforces | slice reads no clock | slice reads no clock |
| **FSM exhaustiveness** | `default:` — new state fails at **run time** | **compile error** | `default:` — same as Java |

---

## Gate coverage

`scripts/ci/gate.sh full` — 23 steps: 6 Java, 4 Rust, 4 C++, 6 cross-language, 3 hygiene.

**Currently skipping, with the reason:**

| Step | Why | Cleared by |
|---|---|---|
| `fsm-coverage` | no FSM in the slice runner yet | component 5b |
| `cpp-msan` (nightly) | needs an MSan-instrumented libc++ | open question in [`README.md`](README.md) |
| `cpp-valgrind`, `fuzz-long` (nightly) | no slice binary or fuzz target yet | components 7–8 |

Under `EMS_GATE_STRICT=1` (set in CI) a step skipped for a **missing tool** is a failure — so every
skip above is "this does not exist yet", never "we could not be bothered".

---

## Size

| | Hand-written | Generated | Total |
|---|---:|---:|---:|
| Java | 33,472 | 2,378 | 35,850 |
| Rust | 2,757 | 1,312 | 4,069 |
| C++ | 2,617 | 2,034 | 4,651 |

Excludes tests. Java's figure is the whole system; Rust's and C++'s are the slice.

**Rust and C++ land within 5% of each other** on hand-written slice code (2,757 vs 2,617) for the
same five components. Both folk claims — "Rust is more verbose", "C++ is more verbose" — fail here;
the gap is under 150 lines and is mostly C++ header declarations against Rust doc comments.

---

## What is deliberately not measured

- **Coverage percentages.** JaCoCo runs on Java only; Rust and C++ have no coverage instrumentation
  wired up. One number beside two blanks would imply a comparison that does not exist.
- **Performance.** Out of scope per ADR 0001. No benchmarks have been run, so there is no basis for
  a claim in either direction.
- **Mutation score.** Not run in any language.

---

## Re-deriving all of this

```bash
scripts/ci/gate.sh full            # everything below, one command
./gradlew allTests                 # Java: 2,374
cargo test --manifest-path rust/Cargo.toml --all
ctest --test-dir build/cpp --output-on-failure --no-tests=error
conformance/harness/run.sh         # 9/9
scripts/dev/demo-polyglot.sh       # watch the three agree, then watch one diverge
```
