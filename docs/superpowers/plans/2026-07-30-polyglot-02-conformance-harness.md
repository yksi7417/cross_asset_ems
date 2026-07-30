# Polyglot Port — Sub-project 2: Conformance Harness + Corpus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "the three implementations are the same system" a testable claim — build the
`ems-slice` CLI in Java, the language-agnostic harness that runs it, the byte-exact differ, and the
first corpus cases.

**Architecture:** `ems-slice` is a pure function from an input event journal to an output event
journal — no network, no clock, no filesystem beyond the two paths it is given. Java ships it
first and generates `expected.jsonl` for every case. The harness is a shell script plus a Python
differ that neither knows nor cares which language produced the binary it is running; it discovers
implementations by looking for known binary paths and skips the ones that do not exist yet.

**Tech Stack:** Java 21 + Gradle (the `ems-slice` implementation), Python 3 stdlib (harness differ
and FSM coverage check), bash (harness runner), JSONL journals.

**Depends on:** sub-project 1 (the gate wires `conformance` and `fsm-coverage` as skips already).

## Global Constraints

- The output journal must be byte-identical across implementations: UTF-8, `\n` line endings, no
  locale-dependent formatting, keys emitted in a fixed order.
- No wall-clock reads, no `Math.random`, no unordered-map iteration reaching the output.
- Identifier generation is seeded from `--seed`, default `0`.
- The slice binary is single-threaded.
- Every corpus case has a `case.md` explaining what it covers and why it exists. A case without one
  fails review.
- `conformance/README.md` already states this contract — the implementation conforms to that
  document, and any deviation updates it in the same PR.

---

## File Structure

| File | Responsibility |
|---|---|
| `conformance/harness/run.sh` | Discover implementations, run every case against each, report. Called by `gate.sh`. |
| `conformance/harness/differ.py` | Byte-exact comparison with human-readable failure output (first differing line, context). |
| `conformance/harness/fsm_coverage.py` | Parse `schemas/fsm/*.fsm.yaml`, parse corpus outputs, assert every transition is reached. |
| `conformance/harness/test_differ.py` | Unit tests for the differ. |
| `conformance/harness/test_fsm_coverage.py` | Unit tests for the coverage check. |
| `conformance/corpus/<case>/…` | The cases themselves. |
| `java/ems-it/src/main/java/io/crossasset/ems/it/slice/SliceMain.java` | The `ems-slice` entry point: parse args, read journal, drive the slice, write journal. |
| `java/ems-it/src/main/java/io/crossasset/ems/it/slice/JournalCodec.java` | JSONL read/write with deterministic key order. |
| `java/ems-it/src/main/java/io/crossasset/ems/it/slice/SliceRunner.java` | Wires intake → authz → validation → FSM → routing → venue → fill → journal. |
| `java/ems-it/src/main/java/io/crossasset/ems/it/slice/DeterministicIds.java` | Seeded identifier generator. |
| `java/ems-transport/src/main/java/io/crossasset/ems/transport/JournalTransport.java` | File-backed transport implementation (ADR 0006). |

---

### Task 1: The journal format and its codec

**Files:**
- Create: `java/ems-it/src/main/java/io/crossasset/ems/it/slice/JournalCodec.java`
- Test: `java/ems-it/src/test/java/io/crossasset/ems/it/slice/JournalCodecTest.java`

**Interfaces:**
- Produces: `JournalCodec.read(Path) -> List<JournalEvent>`,
  `JournalCodec.write(Path, List<JournalEvent>)`,
  `record JournalEvent(long seq, String type, SortedMap<String, String> fields)`.

Format: one JSON object per line. Keys emitted in lexicographic order — that is what makes the
output byte-comparable across three languages without agreeing on a JSON library.

```
{"fields":{"account":"ACC1","figi":"BBG000B9XRY4","price":"1250000","qty":"100","side":"BUY"},"seq":1,"type":"OrderNew"}
```

- [ ] **Step 1: Write the failing test**

```java
@Test
void writesKeysInLexicographicOrderAndUnixLineEndings() throws Exception {
  Path out = tmp.resolve("out.jsonl");
  JournalCodec.write(out, List.of(
      new JournalEvent(1, "OrderNew", new TreeMap<>(Map.of("qty", "100", "account", "ACC1")))));
  String raw = Files.readString(out, StandardCharsets.UTF_8);
  assertThat(raw).isEqualTo(
      "{\"fields\":{\"account\":\"ACC1\",\"qty\":\"100\"},\"seq\":1,\"type\":\"OrderNew\"}\n");
}

@Test
void roundTripsWithoutLoss() throws Exception {
  Path p = tmp.resolve("rt.jsonl");
  List<JournalEvent> events = List.of(
      new JournalEvent(1, "OrderNew", new TreeMap<>(Map.of("figi", "BBG000B9XRY4"))),
      new JournalEvent(2, "OrderAccepted", new TreeMap<>(Map.of("orderId", "ORD-1"))));
  JournalCodec.write(p, events);
  assertThat(JournalCodec.read(p)).isEqualTo(events);
}

@Test
void rejectsMalformedLineWithLineNumber() throws Exception {
  Path p = tmp.resolve("bad.jsonl");
  Files.writeString(p, "{\"seq\":1}\nnot json\n");
  assertThatThrownBy(() -> JournalCodec.read(p))
      .isInstanceOf(JournalCodec.MalformedJournalException.class)
      .hasMessageContaining("line 2");
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew --no-daemon :ems-it:test --tests '*JournalCodecTest*'`
Expected: FAIL — `JournalCodec` does not exist.

- [ ] **Step 3: Implement `JournalCodec`**

Write the encoder by hand rather than reaching for Jackson's default serialisation: the byte layout
is the contract, and a library upgrade must not be able to change it. Escape per RFC 8259
(`"` `\` and control characters), no non-ASCII escaping beyond that, no trailing whitespace, `\n`
terminator on every line including the last.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew --no-daemon :ems-it:test --tests '*JournalCodecTest*'`
Expected: PASS.

- [ ] **Step 5: Mark the package `@NullMarked` and add it to the ratchet**

Create `package-info.java` with `@NullMarked`, fix what NullAway reports, add
`io.crossasset.ems.it.slice` to `scripts/ci/nullmarked-baseline.txt`.

Run: `python3 scripts/ci/checks/nullmarked_ratchet.py`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add java/ems-it scripts/ci/nullmarked-baseline.txt
git commit -m "feat(conformance): deterministic JSONL journal codec"
```

---

### Task 2: Deterministic identifier generation

**Files:**
- Create: `java/ems-it/src/main/java/io/crossasset/ems/it/slice/DeterministicIds.java`
- Test: `java/ems-it/src/test/java/io/crossasset/ems/it/slice/DeterministicIdsTest.java`

**Interfaces:**
- Produces: `DeterministicIds(long seed)`, `String nextOrderId()`, `String nextRouteId()`,
  `String nextExecId()`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void sameSeedProducesSameSequence() {
  DeterministicIds a = new DeterministicIds(0);
  DeterministicIds b = new DeterministicIds(0);
  for (int i = 0; i < 100; i++) {
    assertThat(a.nextOrderId()).isEqualTo(b.nextOrderId());
  }
}

@Test
void differentSeedsDiverge() {
  assertThat(new DeterministicIds(0).nextOrderId())
      .isNotEqualTo(new DeterministicIds(1).nextOrderId());
}

@Test
void idsAreStableAcrossRuns() {
  DeterministicIds ids = new DeterministicIds(0);
  assertThat(ids.nextOrderId()).isEqualTo("ORD-0000000001");
  assertThat(ids.nextRouteId()).isEqualTo("RTE-0000000001");
  assertThat(ids.nextExecId()).isEqualTo("EXE-0000000001");
}
```

The third test is the important one: it pins the exact string form, because the Rust and C++ ports
have to reproduce it character for character. A counter formatted with a fixed width beats a hash —
it is trivially reimplementable in any language, which a seeded PRNG is not.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew --no-daemon :ems-it:test --tests '*DeterministicIdsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement it**

Per-prefix counters starting at `seed + 1`, formatted `%s-%010d`. No PRNG.

- [ ] **Step 4: Run to verify pass**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(conformance): seeded deterministic identifier generation"
```

---

### Task 3: `JournalTransport` in Java

**Files:**
- Create: `java/ems-transport/src/main/java/io/crossasset/ems/transport/JournalTransport.java`
- Test: `java/ems-transport/src/test/java/io/crossasset/ems/transport/JournalTransportTest.java`

**Interfaces:**
- Consumes: `JournalCodec` (Task 1) — moved to `ems-transport` if `ems-it` cannot be a dependency
  of `ems-transport`; check the module graph first and put the codec wherever both can see it.
- Produces: `interface Transport { void publish(JournalEvent e); List<JournalEvent> drain(); }`
  and `JournalTransport implements Transport` backed by two files.

This is the ADR 0006 split: the gate uses `JournalTransport`, the live path uses `AeronTransport`,
and nothing in the slice knows which it has.

- [ ] **Step 1: Write the failing test**

```java
@Test
void publishedEventsAppearInOutputJournalInOrder() throws Exception {
  JournalTransport t = new JournalTransport(input, output);
  t.publish(new JournalEvent(1, "A", new TreeMap<>()));
  t.publish(new JournalEvent(2, "B", new TreeMap<>()));
  t.close();
  assertThat(JournalCodec.read(output))
      .extracting(JournalEvent::type).containsExactly("A", "B");
}

@Test
void drainReturnsInputJournalOnceAndThenEmpty() throws Exception {
  JournalCodec.write(input, List.of(new JournalEvent(1, "OrderNew", new TreeMap<>())));
  JournalTransport t = new JournalTransport(input, output);
  assertThat(t.drain()).hasSize(1);
  assertThat(t.drain()).isEmpty();
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew --no-daemon :ems-transport:test --tests '*JournalTransportTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `Transport` and `JournalTransport`**

- [ ] **Step 4: Run to verify pass**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(transport): file-backed JournalTransport for deterministic replay"
```

---

### Task 4: `SliceRunner` — the order path end to end

**Files:**
- Create: `java/ems-it/src/main/java/io/crossasset/ems/it/slice/SliceRunner.java`
- Test: `java/ems-it/src/test/java/io/crossasset/ems/it/slice/SliceRunnerTest.java`

**Interfaces:**
- Consumes: `JournalCodec`, `DeterministicIds`, `Transport`, and the existing production types:
  `OrderRequest`, `StagedOrder`, `StageResult`, `StagedOrderManager`, `RouteManager`,
  `InMemoryRouteManager`, `Route`, `RouteRequest`, `RouteResult`, `LayeredValidatorPipeline`,
  `ValidationRequest`, `ValidationResult`, `AaaService`, and the generated `OrderFsm` / `RouteFsm`.
- Produces: `SliceRunner(Transport transport, DeterministicIds ids, …)`,
  `void run()` — drains input, emits output.

Input event types the runner handles: `OrderNew`, `OrderCancel`, `OrderAmend`, `ExecutionReport`,
`VenueSessionDown`. Output event types: `OrderAccepted`, `OrderRejected`, `RouteCreated`,
`RouteSent`, `Fill`, `OrderFilled`, `OrderCancelled`, `AllocationCreated`, `ValidationRejected`.

- [ ] **Step 1: Write the failing test — happy path**

```java
@Test
void newOrderIsValidatedStagedRoutedAndFilled() {
  List<JournalEvent> out = runSlice(List.of(
      event(1, "OrderNew", Map.of(
          "account", "ACC1", "figi", "BBG000B9XRY4",
          "side", "BUY", "qty", "100", "price", "1250000", "sessionId", "1")),
      event(2, "ExecutionReport", Map.of(
          "routeId", "RTE-0000000001", "execType", "FILL",
          "lastQty", "100", "lastPx", "1250000"))));

  assertThat(out).extracting(JournalEvent::type)
      .containsExactly("OrderAccepted", "RouteCreated", "RouteSent", "Fill",
                       "OrderFilled", "AllocationCreated");
}
```

- [ ] **Step 2: Write the failing test — validation reject**

```java
@Test
void unknownFigiIsRejectedAtTheReferenceLayer() {
  List<JournalEvent> out = runSlice(List.of(
      event(1, "OrderNew", Map.of(
          "account", "ACC1", "figi", "BBG-NOT-IN-MASTER",
          "side", "BUY", "qty", "100", "price", "1250000", "sessionId", "1"))));

  assertThat(out).hasSize(1);
  assertThat(out.get(0).type()).isEqualTo("ValidationRejected");
  assertThat(out.get(0).fields()).containsEntry("code", "EMS-REF-2001");
}
```

- [ ] **Step 3: Run to verify both fail**

Run: `./gradlew --no-daemon :ems-it:test --tests '*SliceRunnerTest*'`
Expected: FAIL — `SliceRunner` does not exist.

- [ ] **Step 4: Implement `SliceRunner`**

Wire the existing production components; do not reimplement them. Where a component needs a
collaborator the slice does not have (market data, positions), pass the narrowest stub that makes
the path work and record it in `case.md` for any case that touches it.

Every map whose iteration order can reach the output journal is a `TreeMap`. This is not a style
preference — it is the determinism requirement from ADR 0003.

- [ ] **Step 5: Run to verify pass**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(conformance): SliceRunner drives the cash-equity order path"
```

---

### Task 5: The `ems-slice` CLI

**Files:**
- Create: `java/ems-it/src/main/java/io/crossasset/ems/it/slice/SliceMain.java`
- Modify: `java/ems-it/build.gradle.kts` (add an `installDist`-style `ems-slice` launcher)
- Test: `java/ems-it/src/test/java/io/crossasset/ems/it/slice/SliceMainTest.java`

**Interfaces:**
- Produces: `ems-slice --input <path> --output <path> [--seed <n>]`.
  Exit 0 on success; exit 2 on a usage error; exit 1 on a malformed input journal.

- [ ] **Step 1: Write the failing test**

```java
@Test
void missingInputArgumentIsAUsageError() {
  assertThat(SliceMain.run(new String[] {"--output", "o.jsonl"})).isEqualTo(2);
}

@Test
void defaultSeedIsZero() throws Exception {
  JournalCodec.write(input, List.of(orderNew()));
  SliceMain.run(new String[] {"--input", input.toString(), "--output", output.toString()});
  assertThat(JournalCodec.read(output).get(0).fields().get("orderId"))
      .isEqualTo("ORD-0000000001");
}

@Test
void malformedJournalExitsOneWithoutStackTrace() throws Exception {
  Files.writeString(input, "not json\n");
  assertThat(SliceMain.run(new String[] {
      "--input", input.toString(), "--output", output.toString()})).isEqualTo(1);
}
```

- [ ] **Step 2: Run to verify failure**

Expected: FAIL.

- [ ] **Step 3: Implement `SliceMain` and the Gradle launcher**

`main` delegates to `run(String[]): int` so it is testable without `System.exit`.

- [ ] **Step 4: Run to verify pass, then run the binary by hand**

```bash
./gradlew --no-daemon :ems-it:installDist
./java/ems-it/build/install/ems-it/bin/ems-slice --input /tmp/in.jsonl --output /tmp/out.jsonl
```
Expected: exit 0, `/tmp/out.jsonl` written.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(conformance): ems-slice CLI"
```

---

### Task 6: The differ

**Files:**
- Create: `conformance/harness/differ.py`
- Test: `conformance/harness/test_differ.py`

**Interfaces:**
- Produces: `python3 conformance/harness/differ.py <expected> <actual>` — exit 0 identical, exit 1
  with a report. Importable: `diff(expected_bytes, actual_bytes) -> str | None`.

- [ ] **Step 1: Write the failing tests**

```python
def test_identical_returns_none(self):
    self.assertIsNone(diff(b"a\nb\n", b"a\nb\n"))

def test_reports_first_differing_line_number(self):
    report = diff(b"a\nb\n", b"a\nc\n")
    self.assertIn("line 2", report)

def test_reports_length_mismatch(self):
    report = diff(b"a\n", b"a\nb\n")
    self.assertIn("actual has 1 extra line", report)

def test_trailing_newline_difference_is_a_difference(self):
    self.assertIsNotNone(diff(b"a\n", b"a"))

def test_crlf_is_a_difference(self):
    self.assertIsNotNone(diff(b"a\n", b"a\r\n"))
```

The last two matter: a differ that normalises line endings would hide exactly the class of
cross-platform divergence this gate exists to catch.

- [ ] **Step 2: Run to verify failure**

Run: `python3 -m unittest discover -s conformance/harness -p 'test_*.py'`
Expected: FAIL — no module named `differ`.

- [ ] **Step 3: Implement `differ.py`**

Byte comparison first; only if they differ, split into lines to produce a readable report.

- [ ] **Step 4: Run to verify pass**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add conformance/harness
git commit -m "feat(conformance): byte-exact journal differ"
```

---

### Task 7: The harness runner

**Files:**
- Create: `conformance/harness/run.sh` (executable)

**Interfaces:**
- Consumes: `differ.py` (Task 6), the `ems-slice` binaries.
- Produces: `conformance/harness/run.sh [--case <name>] [--impl java|rust|cpp]` — exit 0 when every
  discovered implementation matches every case.

Implementation discovery — each entry is a language name and a binary path:

| Language | Path |
|---|---|
| java | `java/ems-it/build/install/ems-it/bin/ems-slice` |
| rust | `rust/target/release/ems-slice` |
| cpp | `build/cpp/ems-it/ems-slice` |

An implementation whose binary is absent is **reported as not run**, not silently passed. If *no*
implementation is found, that is a failure — an empty conformance run that exits 0 is the same
class of lie as a green `ctest` over zero tests.

- [ ] **Step 1: Write `run.sh`**

- [ ] **Step 2: Verify it fails loudly with no implementations built**

Run: `conformance/harness/run.sh`
Expected: non-zero, message naming which binaries were looked for.

- [ ] **Step 3: Verify it passes against Java**

Run: `./gradlew --no-daemon :ems-it:installDist && conformance/harness/run.sh`
Expected: exit 0, one line per case.

- [ ] **Step 4: shellcheck**

Run: `shellcheck conformance/harness/run.sh`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add conformance/harness/run.sh
git commit -m "feat(conformance): language-agnostic harness runner"
```

---

### Task 8: First corpus cases

**Files:**
- Create: `conformance/corpus/happy-path-new-order-to-fill/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/reject-unknown-figi/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/partial-fills/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/cancel-before-fill/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/amend-quantity/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/venue-session-drop-mid-order/{input.jsonl,expected.jsonl,case.md}`
- Create: `conformance/corpus/malformed-input/{input.jsonl,expected.jsonl,case.md}`

- [ ] **Step 1: Write `input.jsonl` for each case**

- [ ] **Step 2: Generate each `expected.jsonl` from the Java build**

```bash
for c in conformance/corpus/*/; do
  ./java/ems-it/build/install/ems-it/bin/ems-slice \
      --input "$c/input.jsonl" --output "$c/expected.jsonl"
done
```

- [ ] **Step 3: Review every generated expectation against the schemas**

For each case, open `expected.jsonl` next to `schemas/fsm/order.fsm.yaml`,
`schemas/fsm/route.fsm.yaml` and `schemas/reject-codes/`. Does the transition sequence match what
the schema says should happen? Does the reject code match the catalog?

This step is not a formality. Java generates the expectation, which makes Java correct by
definition unless a human checks. A bug that survives this review becomes the specification for two
more languages.

- [ ] **Step 4: Write `case.md` for each case**

One paragraph: what this case covers, and why it exists. "Covers the happy path" is not enough —
say which transitions and which components it exercises, so a future reader can tell whether a new
case duplicates it.

- [ ] **Step 5: Run the harness**

Run: `conformance/harness/run.sh`
Expected: 7 cases, all pass against Java.

- [ ] **Step 6: Commit**

```bash
git add conformance/corpus
git commit -m "test(conformance): first seven corpus cases"
```

---

### Task 9: FSM coverage check

**Files:**
- Create: `conformance/harness/fsm_coverage.py`
- Test: `conformance/harness/test_fsm_coverage.py`

**Interfaces:**
- Produces: `python3 conformance/harness/fsm_coverage.py` — exit 0 when every transition in
  `schemas/fsm/order.fsm.yaml` and `schemas/fsm/route.fsm.yaml` is reached by ≥1 corpus case.
  Importable: `transitions(schema_path) -> set[tuple[str, str, str]]`,
  `covered(corpus_dir) -> set[tuple[str, str, str]]`, `check(root) -> list[str]`.

Coverage is read from the output journals: `SliceRunner` emits an `FsmTransition` event
(`{"fsm":"order","from":"NEW","to":"ACCEPTED","event":"Accept"}`) for every transition it takes, so
coverage is measured from what the corpus actually exercised rather than inferred.

- [ ] **Step 1: Add `FsmTransition` emission to `SliceRunner` and regenerate expectations**

Regenerate every `expected.jsonl` (Task 8 Step 2) and re-review the diff — the new events should be
the only change.

- [ ] **Step 2: Write the failing tests**

```python
def test_uncovered_transition_is_reported(self):
    root = tree({
        "schemas/fsm/order.fsm.yaml": ORDER_FSM_WITH_TWO_TRANSITIONS,
        "conformance/corpus/c1/expected.jsonl":
            '{"fields":{"event":"Accept","fsm":"order","from":"NEW","to":"ACCEPTED"},'
            '"seq":1,"type":"FsmTransition"}\n',
    })
    errors = check(root)
    self.assertTrue(any("order: NEW -Reject-> REJECTED" in e for e in errors))

def test_full_coverage_passes(self): ...
def test_missing_corpus_directory_is_an_error(self): ...
```

- [ ] **Step 3: Run to verify failure**

Expected: FAIL.

- [ ] **Step 4: Implement `fsm_coverage.py`**

- [ ] **Step 5: Run against the real corpus**

Run: `python3 conformance/harness/fsm_coverage.py`
Expected: it will fail, listing genuinely-unreached transitions. Add corpus cases until it passes;
each new case gets a `case.md` and the Step-3 schema review from Task 8.

- [ ] **Step 6: Commit**

```bash
git add conformance/
git commit -m "feat(conformance): FSM transition coverage gate"
```

---

### Task 10: Turn the gate steps on

**Files:**
- Modify: `scripts/ci/gate.sh` (the `conformance` and `fsm-coverage` steps stop skipping on their own
  once the files exist — verify, do not re-plumb)
- Modify: `docs/polyglot/README.md` (status table: sub-project 2 → done)
- Modify: `conformance/README.md` (drop the "contract only" status banner)

- [ ] **Step 1: Verify the steps now run**

Run: `scripts/ci/gate.sh full --list && scripts/ci/gate.sh full`
Expected: `conformance` and `fsm-coverage` show `PASS`, not `SKIP`.

- [ ] **Step 2: Verify strict mode**

Run: `EMS_GATE_STRICT=1 scripts/ci/gate.sh full`
Expected: exit 0.

- [ ] **Step 3: Update the status docs**

- [ ] **Step 4: Push and confirm CI is green**

Run: `git push && gh run watch`
Expected: green. If red, fix and re-push — a red CI is not done.

- [ ] **Step 5: Commit**

```bash
git add docs/ conformance/README.md
git commit -m "docs(conformance): sub-project 2 complete, gate steps live"
```

---

## Definition of done

- `ems-slice` exists in Java, is a pure function from input journal to output journal, and reads no
  clock, no network and no other file.
- The same input and seed produce byte-identical output on repeated runs and on a different machine.
- ≥7 corpus cases, each with a reviewed `case.md`.
- `conformance/harness/run.sh` fails loudly when no implementation is built, and reports any
  implementation it could not find rather than passing over it.
- `fsm_coverage.py` passes over `order.fsm.yaml` and `route.fsm.yaml`.
- `scripts/ci/gate.sh full` runs both steps for real and exits 0.
