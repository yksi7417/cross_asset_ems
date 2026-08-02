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
| [T-4](#t-4) | Digest-pinned CI container image | nothing | [gate.md](polyglot/gate.md) recorded deviation |
| [T-8](#t-8) | Java's `noTransition` returns a null context | nothing | found building component 6b-i |

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

## T-8 — Java's `noTransition` returns a null context; Rust and C++ return the unchanged one

`TransitionResult.noTransition(currentState)` passes `null` for `newContext`. The Rust and C++
generators both return the context unchanged instead. Three implementations of the same generated
contract disagree about what a declined event leaves behind.

**Why:** it is latent rather than live — every caller checks `isNoTransition()` before reading
`newContext()`, so nothing dereferences the null today. Fixing it means changing the generated Java
`TransitionResult`, which re-pins the Java golden hashes and touches the 861 pre-existing FSM tests.
That is a bigger blast radius than the component that found it (6b-i, effect generation) should
carry, and bundling it would make both changes harder to review.

Worth being precise about the risk: `newContext` is not `@Nullable`, so NullAway will not stop a
future caller from dereferencing it. The first person to read the context after a declined event
gets an NPE in Java and a valid value in the other two — and the conformance gate would only catch
it if a corpus case happened to reach that path.

**Done when:** the generator emits `noTransition(currentState, ctx)` in Java, all three languages
return the unchanged context for a declined event, a test in each language asserts it, and the Java
golden hashes are re-pinned with the diff reviewed.

---

## Completed

| Item | Outcome |
|---|---|
| T-6 — study-guide index + completeness check | Done — the Notes table in `70_concepts/idioms/README.md` is the index, and `study_guide.py` now enforces completeness: five required headings, no placeholders, no empty sections, every note linked from the README. Five new tests, each watched failing. |
| T-5 — `ReflectiveBlpapiDriver` takes a `TimeSource` | Done — injected, wall-clock singleton in the default constructor, baseline entry removed. The baseline now holds only I/O deadlines and demo entry points; the one honest-debt entry is gone. |
| T-3 — triangulate the corpus | Done — `triangulate.py` reports UNANIMOUS / **2-1 SPLIT naming the minority** / STALE EXPECTATION / NO AGREEMENT; wired into `run.sh` for every case; both blocking verdicts watched failing through the real harness before being trusted. The reference gets no special treatment — a test pins that Java itself can be the named minority. |
| T-7 — release routed quantity when a route dies unfilled | Done — `routedQty` skips `REJECTED`/`CANCELED`/`EXPIRED`/`SUPERSEDED` in all three languages; `component-07-route-lifecycle` re-routes an order whose first route the venue refused. `FILLED` stays counted. |
| Coverage instrumentation for Rust and C++ | Done — `cargo-llvm-cov` (91.9%) and `gcov`/`gcovr` (79%), both in `gate.sh full`. See [status.md](polyglot/status.md). |
| Make business logic clock-injectable | Done — `TimeSource`, and `no_raw_clock.py` enforces it. |
| Slice reuses the production AAA | Done — the duplicate `io.crossasset.ems.aaa.slice` package was deleted. |
