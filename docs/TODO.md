# Deferred work

**Repo-wide register.** Work that is *decided and deferred*, not undecided. Each item exists
because someone concluded it is needed and chose the timing, and links whatever made that call.

This is deliberately **not a wish list**. If an item turns out not to be needed, delete it with a
note saying why — an entry nobody intends to do teaches readers the register is fiction, and then
the real items get skipped too.

**Enforced, not trusted.** `scripts/ci/checks/deferred_work.py` runs in every gate lane and
requires:

- each entry to carry a **Why** and a **Done when**;
- each `DEFERRED: T-n` marker anywhere in the tree to resolve to an entry here.

Both directions have been observed failing. See
[CONTRIBUTING.md § Deferring work](../CONTRIBUTING.md#deferring-work) for when to add an entry and
how to phrase it.

Everything currently listed happens to come from the polyglot port — it is simply the work in
flight. The register is not scoped to it; anything deferred anywhere in the repo belongs here.
For what is *being built next* in the port, see its
[sequencing table](polyglot/README.md#sequencing), which is a plan rather than a deferral list.

| ID | Item | Blocked by | Source |
|---|---|---|---|
| [T-1](#t-1) | MSan-instrumented libc++ so `cpp-msan` actually runs | nothing | [ADR 0008](decisions/0008-msan-nightly-only.md) |
| [T-2](#t-2) | Nightly failure notification | T-1 landing makes it matter | [ADR 0008](decisions/0008-msan-nightly-only.md) |
| [T-3](#t-3) | Triangulate the corpus instead of privileging Java | all three slices complete | [ADR 0009](decisions/0009-corpus-authority-java-with-triangulation-later.md) |
| [T-4](#t-4) | Digest-pinned CI container image | nothing | [gate.md](polyglot/gate.md) recorded deviation |
| [T-5](#t-5) | `ReflectiveBlpapiDriver` takes a `TimeSource` | nothing | [clock-baseline.txt](../scripts/ci/clock-baseline.txt) |
| [T-6](#t-6) | Study-guide index + completeness check | components 6–8 | [ADR 0005](decisions/0005-study-guide-with-enforced-anchors.md) |

---

## T-1 — MSan-instrumented libc++

**Why:** `cpp-msan` is in the nightly lane and has never run. It skips with
`no MSan-instrumented libc++ (set EMS_MSAN_LIBCXX)`, which is honest but means the check is an
intention rather than a check. ADR 0008 decided to keep MSan nightly rather than drop it, because
uninitialised reads are the defect class that would produce a conformance failure that reproduces
*sometimes* — the worst kind this project could have.

**What it takes:**
- Build libc++ from source with `-fsanitize=memory`, once, cached as a CI artifact or a layer in
  the toolchain image.
- Set `EMS_MSAN_LIBCXX` in the nightly workflow; `gate.sh` already reads it.
- Confirm the step actually fails on a deliberate uninitialised read before trusting it — the same
  discipline applied to `fsm-sync` and the conformance differ.

**Done when:** `scripts/ci/gate.sh nightly` runs `cpp-msan` for real, and it has been observed
failing on a planted defect.

---

## T-2 — Nightly failure notification

**Why:** the nightly lane is the only lane that can fail for a reason PRs did not catch. **A red
nightly that nobody sees is worse than no nightly** — it converts a real signal into background
noise and teaches people to ignore the badge.

Not urgent while every nightly-only step skips. It becomes urgent the moment T-1 lands and the lane
can genuinely fail.

**What it takes:** a failure notification from `nightly.yml` to wherever the team actually reads
things. `IMPL/HERMES.md` documents an existing Discord path; reuse it rather than inventing a
second channel.

**Done when:** a failing nightly produces a message someone receives.

---

## T-3 — Triangulate the corpus

**Why:** Java generates `expected.jsonl`, so a Java bug faithfully reproduced by both ports passes
the gate. ADR 0009 accepted that for now and recorded the fix.

Once all three implementations are complete, the stronger question is not "do they match Java" but
"do all three agree":

- **2-1 disagreement** → strong evidence the odd one out is wrong. Two independent implementations
  rarely make the same mistake.
- **3-way agreement on something the schema forbids** → the spec was read the same wrong way three
  times. That is a finding about the *schema's clarity*, not the code.

**What it takes:**
- A differ mode that treats three outputs symmetrically rather than diffing two against a
  privileged third.
- A policy for what a 2-1 split means procedurally — it should block, and it should name the
  minority implementation without asserting it is wrong.
- No new binaries: `conformance/harness/run.sh` already runs all three.

**Blocked by:** components 6–8. Triangulating three implementations of five components proves less
than triangulating three of eight.

**Done when:** the harness reports agreement three ways, and a deliberate one-language divergence
is reported as a 2-1 split rather than "Rust ≠ Java".

---

## T-4 — Digest-pinned CI container image

**Why:** the design spec asks for CI and `.githooks/pre-push` to run "the same container image
pinned by digest". What exists is the identical *dependency set* via `scripts/ci/install-toolchain.sh`,
which both the devcontainer build and every CI job run. That closes the local/CI divergence gap for
package **contents** but not for package **versions** — an upstream apt update can still change
what CI sees without anything in this repo changing.

Recorded as a deviation in [`gate.md`](polyglot/gate.md) rather than glossed over.

**What it takes:** registry credentials, a publish workflow, and a digest reference in both
`ci.yml` and the devcontainer. The `Dockerfile` already exists and already delegates to
`install-toolchain.sh`.

**Done when:** `gate.md`'s recorded deviation can be deleted rather than reworded.

---

## T-5 — `ReflectiveBlpapiDriver` takes a `TimeSource`

**Why:** it is the one entry in [`scripts/ci/clock-baseline.txt`](../scripts/ci/clock-baseline.txt)
that is **honest debt rather than an exemption on merit**. Every other baselined file reads the wall
clock for an I/O deadline or is a demo entry point; this one stamps a market-data tick with
`System.currentTimeMillis()`, which is a genuine business timestamp.

It is baselined only because `ems-market-data` is outside the cash-equity slice (ADR 0002), so
fixing it now would be scope creep.

**What it takes:** inject a `TimeSource`, thread it to the `onTick` call, remove the baseline entry.
The `no_raw_clock` check will fail on a *stale* baseline entry, so the removal is enforced rather
than optional.

**Done when:** the entry is gone from the baseline and the check still passes.

---

## T-6 — Study-guide index and completeness check

**Why:** there are six idiom notes and no index, so the section is a directory listing rather than
something browsable. ADR 0005 also specified checks the integrity checker does not yet do: it
verifies a note *exists* and its anchor resolves, but not that the note *says* anything.

**What it takes** (from [plan 06](superpowers/plans/2026-07-30-polyglot-06-study-guide.md)):
- `70_concepts/idioms/idioms-index.md`, grouped three ways — by language, by theme, by module —
  because three kinds of reader arrive three different ways.
- Extend `study_guide.py`: every note has all five required headings; no note contains `TODO`/`TBD`
  or an empty section; every note is reachable from the index.
- The three cross-language contrast notes that have no single usage site.

**Blocked by:** components 6–8, which is where the remaining idioms will surface. Writing the index
before the notes exist means writing it twice.

**Done when:** `study_guide.py` enforces completeness, not just existence, and its own tests prove
it.

---

## Completed

| Item | Outcome |
|---|---|
| Coverage instrumentation for Rust and C++ | Done — `cargo-llvm-cov` (91.9%) and `gcov`/`gcovr` (79%), both in `gate.sh full`. See [status.md](polyglot/status.md). |
| Make business logic clock-injectable | Done — `TimeSource`, and `no_raw_clock.py` enforces it. |
| Slice reuses the production AAA | Done — the duplicate `io.crossasset.ems.aaa.slice` package was deleted. |
