# Polyglot Port — Sub-project 4: Rust Slice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full cash-equity order path in Rust and get `ems-slice` to produce output
byte-identical to Java's on every corpus case.

**Architecture:** One crate per module, mirroring `java/` names, added one at a time in dependency
order — a crate directory appears only when it has behaviour and tests. Each crate is built and
tested before the next begins, so the workspace is green at every commit. The final crate,
`ems-slice`, is the binary the conformance harness runs.

**Tech Stack:** Rust 2021, `#![forbid(unsafe_code)]`, `clippy::pedantic` + `clippy::nursery` at
deny, `BTreeMap`/`BTreeSet` only, `serde_json` avoided in favour of a hand-written encoder for the
journal (the byte layout is the contract).

**Depends on:** sub-projects 2 and 3.

## Global Constraints

- `#![forbid(unsafe_code)]` at every crate root. No exceptions in this sub-project.
- No `HashMap` / `HashSet` anywhere — `clippy.toml` enforces it. Iteration order reaches the journal.
- No `unwrap()` or `expect()` outside tests. Errors propagate as `Result`; the slice binary turns
  them into a journal event or an exit code, never a panic.
- No wall-clock reads. No RNG. No threads.
- Prices are `i64` in minor-unit-scaled form, wrapped in a newtype. No floats anywhere.
- Every crate lands with its tests in the same commit, and moves to `done` in
  `scripts/ci/slice-manifest.yaml` in that same commit — never ahead of it.
- Idiom notes (`STUDY:` markers) are written **as** idioms land, not at the end. A marker without a
  note fails the gate, which is what keeps them in the same commit.

---

## File Structure

Crates, in the order they are built. Each row is one task.

| Crate | Responsibility | Java reference |
|---|---|---|
| `rust/ems-core` | `JournalEvent`, the JSONL codec, `DeterministicIds`, scaled-price and quantity newtypes | `java/ems-core` |
| `rust/ems-transport` | `Transport` trait + `JournalTransport` | `java/ems-transport` |
| `rust/ems-aaa` | Subject resolution and the entitlement decision | `java/ems-aaa` |
| `rust/ems-validator` | The five validation layers | `java/ems-validator` |
| `rust/ems-oms` | `OrderRequest`, `StagedOrder`, `StagedOrderManager`, `RouteManager`, `Route` | `java/ems-oms` |
| `rust/ems-venue-connectivity` | FIX out, `ExecutionReport` in, session state | `java/ems-venue-connectivity` |
| `rust/ems-posttrade` | Allocation | `java/ems-posttrade` |
| `rust/ems-slice` | The `ems-slice` binary: CLI, wiring, run loop | `java/ems-it` slice package |

`rust/ems-fsm` already exists from sub-project 3.

---

### Task 1: `ems-core` — journal, ids, newtypes

**Files:**
- Create: `rust/ems-core/Cargo.toml`, `src/lib.rs`, `src/journal.rs`, `src/ids.rs`, `src/units.rs`
- Test: `rust/ems-core/tests/journal_test.rs`, `tests/ids_test.rs`, `tests/units_test.rs`

**Interfaces:**
- Produces:
  - `pub struct JournalEvent { pub seq: u64, pub event_type: String, pub fields: BTreeMap<String, String> }`
  - `pub fn read_journal(path: &Path) -> Result<Vec<JournalEvent>, JournalError>`
  - `pub fn write_journal(path: &Path, events: &[JournalEvent]) -> Result<(), JournalError>`
  - `pub struct DeterministicIds { … }` with `next_order_id()`, `next_route_id()`, `next_exec_id()`
  - `pub struct Price(i64)`, `pub struct Qty(i64)` with `TryFrom<&str>` and `Display`

- [ ] **Step 1: Write the failing tests**

```rust
// tests/journal_test.rs
#[test]
fn writes_keys_in_lexicographic_order_with_unix_line_endings() {
    let mut fields = BTreeMap::new();
    fields.insert("qty".to_string(), "100".to_string());
    fields.insert("account".to_string(), "ACC1".to_string());
    let path = tmp("out.jsonl");
    write_journal(&path, &[JournalEvent { seq: 1, event_type: "OrderNew".into(), fields }]).unwrap();
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        "{\"fields\":{\"account\":\"ACC1\",\"qty\":\"100\"},\"seq\":1,\"type\":\"OrderNew\"}\n"
    );
}

#[test]
fn output_is_byte_identical_to_the_java_reference_fixture() {
    // The single most important test in this crate: the fixture is a file the
    // Java implementation produced. If this passes, the encoder agrees with
    // Java at the byte level and every corpus case has a chance of passing.
    let expected = include_bytes!("fixtures/java_reference.jsonl");
    let path = tmp("rt.jsonl");
    write_journal(&path, &fixture_events()).unwrap();
    assert_eq!(std::fs::read(&path).unwrap(), expected);
}

#[test]
fn malformed_line_reports_its_line_number() {
    let path = tmp("bad.jsonl");
    std::fs::write(&path, "{\"seq\":1}\nnot json\n").unwrap();
    let err = read_journal(&path).unwrap_err();
    assert!(err.to_string().contains("line 2"), "{err}");
}
```

```rust
// tests/ids_test.rs
#[test]
fn matches_the_java_id_format_exactly() {
    let mut ids = DeterministicIds::new(0);
    assert_eq!(ids.next_order_id(), "ORD-0000000001");
    assert_eq!(ids.next_route_id(), "RTE-0000000001");
    assert_eq!(ids.next_exec_id(), "EXE-0000000001");
}
```

```rust
// tests/units_test.rs
#[test]
fn price_parses_scaled_integer_and_rejects_decimals() {
    assert_eq!(Price::try_from("1250000").unwrap().raw(), 1_250_000);
    assert!(Price::try_from("12.50").is_err());   // no floats, ever
}

#[test]
fn price_display_round_trips() {
    assert_eq!(Price::try_from("1250000").unwrap().to_string(), "1250000");
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cargo test --manifest-path rust/Cargo.toml -p ems-core`
Expected: FAIL — the crate does not exist. Create `Cargo.toml` and add it to the workspace members,
then the tests fail on missing items.

- [ ] **Step 3: Copy the Java reference fixture**

```bash
mkdir -p rust/ems-core/tests/fixtures
cp conformance/corpus/happy-path-new-order-to-fill/expected.jsonl \
   rust/ems-core/tests/fixtures/java_reference.jsonl
```

- [ ] **Step 4: Implement `journal.rs`, `ids.rs`, `units.rs`**

Write the JSON encoder by hand. `serde_json` would work, but the byte layout is the contract and a
dependency upgrade must not be able to change it — the same reasoning as the Java side.

- [ ] **Step 5: Run to verify pass**

Run: `cargo test --manifest-path rust/Cargo.toml -p ems-core`
Expected: PASS.

- [ ] **Step 6: Lint and format**

Run: `cargo clippy --manifest-path rust/Cargo.toml -p ems-core --all-targets -- -D warnings && cargo fmt --manifest-path rust/Cargo.toml --all --check`
Expected: clean.

- [ ] **Step 7: Manifest and commit**

Add `ems-core: done` to `scripts/ci/slice-manifest.yaml`, then:

```bash
python3 scripts/ci/checks/anti_stub.py
git add rust/ scripts/ci/slice-manifest.yaml
git commit -m "feat(rust): ems-core journal codec, deterministic ids, scaled-price newtypes"
```

---

### Task 2: `ems-transport` — the `Transport` trait and `JournalTransport`

**Files:**
- Create: `rust/ems-transport/Cargo.toml`, `src/lib.rs`
- Test: `rust/ems-transport/tests/journal_transport_test.rs`

**Interfaces:**
- Consumes: `ems_core::JournalEvent`, `read_journal`, `write_journal`.
- Produces:
  ```rust
  pub trait Transport {
      fn drain(&mut self) -> Result<Vec<JournalEvent>, TransportError>;
      fn publish(&mut self, event: JournalEvent) -> Result<(), TransportError>;
      fn flush(&mut self) -> Result<(), TransportError>;
  }
  pub struct JournalTransport { /* … */ }
  ```

The trait is the ADR 0006 seam: the gate uses `JournalTransport`, and an `AeronTransport` could
join later without the slice knowing.

- [ ] **Step 1: Write the failing tests**

```rust
#[test]
fn drain_returns_input_once_then_empty() { /* … */ }

#[test]
fn published_events_are_written_in_order_on_flush() { /* … */ }

#[test]
fn publish_before_flush_writes_nothing() {
    // Output must be atomic-ish: a crashed run should not leave a half journal
    // that looks like a legitimate short output.
}
```

- [ ] **Step 2: Run to verify failure** — `cargo test -p ems-transport`

- [ ] **Step 3: Implement it**

- [ ] **Step 4: Run to verify pass, clippy and fmt clean**

- [ ] **Step 5: Manifest and commit**

```bash
git commit -am "feat(rust): Transport trait and file-backed JournalTransport"
```

---

### Task 3: `ems-aaa` — authorization decision

**Files:**
- Create: `rust/ems-aaa/Cargo.toml`, `src/lib.rs`
- Test: `rust/ems-aaa/tests/authz_test.rs`

**Interfaces:**
- Produces:
  ```rust
  pub struct SessionId(pub u64);
  pub struct Identity { pub firm: String, pub desk: String, pub user: String }
  pub struct Session { pub id: SessionId, pub identity: Identity }
  pub enum AuthorizationResult { Allow, Deny { reject_code: String, message: String, admin_hint: String } }
  pub trait AaaService {
      fn session_info(&self, id: SessionId) -> Option<&Session>;
      fn authorize(&self, identity: &Identity, tag: &str) -> AuthorizationResult;
  }
  pub struct InMemoryAaaService { /* BTreeMap-backed */ }
  ```

Scope: the entitlement decision only. No SSO, no SCIM — see ADR 0002.

- [ ] **Step 1: Write the failing tests**

Cover: unknown session returns `None`; a permitted tag returns `Allow`; a denied tag returns `Deny`
carrying the reject code from `schemas/reject-codes/`; deny messages match the Java wording
character for character (the journal carries them).

- [ ] **Step 2–5:** run-fail, implement, run-pass, clippy/fmt, manifest, commit.

```bash
git commit -am "feat(rust): ems-aaa session lookup and entitlement decision"
```

---

### Task 4: `ems-validator` — the five layers

**Files:**
- Create: `rust/ems-validator/Cargo.toml`, `src/lib.rs`, `src/layers.rs`
- Test: `rust/ems-validator/tests/pipeline_test.rs`

**Interfaces:**
- Consumes: `ems_aaa::{AaaService, AuthorizationResult, SessionId}`.
- Produces:
  ```rust
  pub enum ValidationLayer { Session, Identity, Reference, Permission, AssetClass }
  pub struct ValidationRequest { pub request_id: String, pub session_id: SessionId,
                                 pub tag: Option<String>, pub figi: Option<String> }
  pub enum ValidationResult {
      Pass { request_id: String },
      Reject { request_id: String, code: String, category: String,
               layer: ValidationLayer, message: String,
               admin_hint: Option<String>, field: Option<String> },
  }
  pub trait ValidatorPipeline { fn validate(&self, req: &ValidationRequest) -> ValidationResult; }
  pub struct LayeredValidatorPipeline { /* … */ }
  ```

`Option<String>` where Java has `@Nullable String` — this is exactly why
`io.crossasset.ems.validator` was made `@NullMarked` in sub-project 1: the Rust type is a
translation of a declared contract, not a guess.

**Idiom note due here:** `// STUDY: option-vs-nullable` at the `admin_hint` field, with the
cross-language contrast (`@Nullable String` / `std::optional<std::string>` / `Option<String>`).

- [ ] **Step 1: Write the failing tests**

One test per layer, each asserting the exact reject code from the catalog. Plus: the first failure
short-circuits — later layers do not run.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement, mirroring `LayeredValidatorPipeline` layer for layer**

Read the Java implementation next to it. Behavioural equivalence is the requirement; idiomatic Rust
is how you get there, not a licence to redesign the layering.

- [ ] **Step 4: Write the idiom note**

Create `70_concepts/idioms/option-vs-nullable.md` with the `anchor:` pointing at the `STUDY:` line.

Run: `python3 scripts/ci/checks/study_guide.py`
Expected: exit 0.

- [ ] **Step 5: Run tests, clippy, fmt; manifest; commit**

```bash
git commit -am "feat(rust): ems-validator five-layer pipeline"
```

---

### Task 5: `ems-oms` — staging and routing

**Files:**
- Create: `rust/ems-oms/Cargo.toml`, `src/lib.rs`, `src/staging.rs`, `src/routing.rs`
- Test: `rust/ems-oms/tests/staging_test.rs`, `tests/routing_test.rs`

**Interfaces:**
- Consumes: `ems_core::{Price, Qty, DeterministicIds}`, `ems_fsm::{OrderState, OrderEvent, RouteState, RouteEvent}`.
- Produces:
  ```rust
  pub struct OrderRequest { pub account: String, pub figi: String, pub side: Side,
                            pub qty: Qty, pub price: Option<Price> }
  pub struct StagedOrder { pub order_id: String, pub state: OrderState, /* … */ }
  pub enum StageResult { Staged(StagedOrder), Rejected { code: String, message: String } }
  pub struct StagedOrderManager { /* BTreeMap<String, StagedOrder> */ }
  pub struct Route { pub route_id: String, pub state: RouteState, /* … */ }
  pub enum RouteResult { Created(Route), Rejected { code: String, message: String } }
  pub trait RouteManager { fn route(&mut self, req: &RouteRequest) -> RouteResult; }
  pub struct InMemoryRouteManager { /* … */ }
  ```

**Idiom notes likely due here:** `// STUDY: newtype-scaled-price` on `Price`, and
`// STUDY: typestate-order-fsm` if the state machine is encoded in the type system rather than as a
field. If typestate turns out to fight the journal-replay requirement, that is a finding worth
writing up in the note's "what the naive version gets wrong" section — a note explaining why an
idiom was *rejected* here is as useful as one explaining why it was used.

- [ ] **Step 1: Write the failing tests**

Staging: accept, reject on unknown account, order-id assignment matches Java's sequence.
Routing: route creation, route-id sequence, route state transitions driven through `ems-fsm`.

- [ ] **Step 2–5:** run-fail, implement, run-pass, clippy/fmt, idiom notes, manifest, commit.

```bash
git commit -am "feat(rust): ems-oms staging and in-memory routing"
```

---

### Task 6: `ems-venue-connectivity` — the venue edge

**Files:**
- Create: `rust/ems-venue-connectivity/Cargo.toml`, `src/lib.rs`, `src/fix_out.rs`, `src/exec_report.rs`, `src/session.rs`
- Test: `rust/ems-venue-connectivity/tests/venue_test.rs`

**Interfaces:**
- Consumes: `ems_oms::Route`, `ems_fsm::{VenueSessionState, VenueSessionEvent}`.
- Produces:
  ```rust
  pub struct FixMessage(String);
  pub fn encode_new_order_single(route: &Route) -> FixMessage;
  pub fn decode_execution_report(raw: &str) -> Result<ExecutionReport, FixDecodeError>;
  pub struct VenueSession { pub state: VenueSessionState }
  ```

The FIX decoder is one of the three fuzz targets (ADR 0004), so its error type must be total — every
malformed input produces a `FixDecodeError`, never a panic, and never an index out of bounds.

**Idiom note likely due here:** `// STUDY: cow-in-fix-decoder` if borrowed-vs-owned handling in the
decoder is where the lifetime work lands.

- [ ] **Step 1: Write the failing tests**

Include a decoder test fed deliberately hostile input: empty string, unterminated field, a length
field claiming more bytes than exist, embedded NUL, 10 MB of `=`. All must return `Err`.

- [ ] **Step 2–5:** run-fail, implement, run-pass, clippy/fmt, idiom note, manifest, commit.

```bash
git commit -am "feat(rust): ems-venue-connectivity FIX edge and session state"
```

---

### Task 7: `ems-posttrade` — allocation

**Files:**
- Create: `rust/ems-posttrade/Cargo.toml`, `src/lib.rs`
- Test: `rust/ems-posttrade/tests/allocation_test.rs`

**Interfaces:**
- Produces: `pub struct Allocation { … }`, `pub fn allocate(fill: &Fill, order: &StagedOrder) -> Allocation`.

Drop-copy is explicitly out of scope (ADR 0002) and is recorded as a stub with a comment saying so
— not silently absent.

- [ ] **Step 1–5:** tests, run-fail, implement, run-pass, clippy/fmt, manifest, commit.

```bash
git commit -am "feat(rust): ems-posttrade allocation"
```

---

### Task 8: `ems-slice` — the binary

**Files:**
- Create: `rust/ems-slice/Cargo.toml`, `src/main.rs`, `src/runner.rs`
- Test: `rust/ems-slice/tests/cli_test.rs`

**Interfaces:**
- Consumes: every crate above.
- Produces: the `ems-slice` binary with the ADR 0003 CLI —
  `ems-slice --input <journal> --output <journal> [--seed <n>]`.

- [ ] **Step 1: Write the failing CLI tests**

```rust
#[test]
fn missing_input_argument_is_a_usage_error() {
    assert_eq!(run(&["--output", "o.jsonl"]), 2);
}

#[test]
fn malformed_journal_exits_one_without_panicking() { /* … */ }

#[test]
fn default_seed_is_zero() { /* … */ }
```

Argument parsing is hand-written — `clap` would be three dependencies for four flags, and
`cargo-deny` has to justify every one of them.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement `runner.rs`, mirroring Java's `SliceRunner` event-for-event**

- [ ] **Step 4: Run the conformance harness against Rust alone**

```bash
cargo build --manifest-path rust/Cargo.toml --release
conformance/harness/run.sh --impl rust
```
Expected: failures at first. This is the real work of this sub-project. Each failure is a byte
difference; the differ names the line. Fix Rust to match Java — **not** the expectation to match
Rust. If you become convinced Java is wrong, stop and fix Java in its own commit with its own test,
then regenerate the expectation and say so in the case's `case.md`.

- [ ] **Step 5: Run the full harness**

```bash
conformance/harness/run.sh
```
Expected: every case passes against both Java and Rust.

- [ ] **Step 6: Manifest and commit**

```bash
git commit -am "feat(rust): ems-slice binary passes the conformance corpus"
```

---

### Task 9: Fuzz targets

**Files:**
- Create: `rust/fuzz/Cargo.toml`, `rust/fuzz/fuzz_targets/fix_decode.rs`, `sbe_decode.rs`, `journal_parse.rs`
- Modify: `scripts/ci/gate.sh` (the `fuzz-long` step stops skipping)

- [ ] **Step 1: Write the three targets**

Each seeded from the conformance corpus, sharing the seed corpus with the C++ targets
(sub-project 5) so found inputs benefit both.

- [ ] **Step 2: Run each briefly**

```bash
cargo +nightly fuzz run fix_decode -- -max_total_time=60
```
Expected: no crashes. If one crashes, that is a real bug — fix it, and add the crashing input as a
unit test before moving on.

- [ ] **Step 3: Wire the gate step and commit**

```bash
git commit -am "test(rust): cargo-fuzz targets for FIX, SBE and journal parsers"
```

---

### Task 10: Miri, and closing out

**Files:**
- Modify: `docs/polyglot/README.md`, `docs/polyglot/gate.md`, `rust/README.md`

- [ ] **Step 1: Run Miri over the test suite**

```bash
cargo +nightly miri test --manifest-path rust/Cargo.toml
```
Expected: clean. Miri is slow; if a test is too slow under it, mark it `#[cfg_attr(miri, ignore)]`
**with a comment saying why**, and check that the ignored set stays small.

- [ ] **Step 2: Run the full gate**

Run: `EMS_GATE_STRICT=1 scripts/ci/gate.sh full`
Expected: exit 0 with `conformance` PASS covering Java and Rust.

- [ ] **Step 3: Verify every idiom note landed**

Run: `python3 scripts/ci/checks/study_guide.py && ls 70_concepts/idioms/`
Expected: exit 0, and one note per `STUDY:` marker added during this sub-project.

- [ ] **Step 4: Update status docs**

- [ ] **Step 5: Push and confirm CI is green**

Run: `git push && gh run watch`

- [ ] **Step 6: Commit**

```bash
git commit -am "docs(rust): sub-project 4 complete"
```

---

## Definition of done

- Every corpus case passes against Rust, byte-for-byte, with no canonicalizing differ.
- `cargo clippy --all-targets -- -D warnings` clean with `pedantic` and `nursery` on.
- `cargo +nightly miri test` clean.
- `grep -r "unsafe" rust/ --include=*.rs` finds only the `forbid` attributes.
- `grep -rE "HashMap|HashSet" rust/ --include=*.rs` finds nothing.
- Every crate in `rust/` is `done` in the manifest and has tests that assert.
- One idiom note per `STUDY:` marker, each with a resolving anchor.
