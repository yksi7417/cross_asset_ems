# 0008 — MSan runs nightly, not per-PR

Status: accepted
Date: 2026-07-31
Deciders: Anthony Si
Context: ADR [0004](0004-defensive-gate-stack.md) — this settles an open question that ADR left explicit

## Context

ADR 0004 put MemorySanitizer in the C++ gate and immediately flagged the cost:

> MSan (libc++ built with MSan, or the job is honestly marked as not running rather than silently
> skipped)

and left the question open:

> **MSan.** Requires an MSan-instrumented libc++, which is real setup cost. Acceptable to defer it
> to nightly-only, or drop it and rely on ASan + Valgrind?

Today `cpp-msan` sits in the nightly lane and skips with the reason
`no MSan-instrumented libc++ (set EMS_MSAN_LIBCXX)`. That is honest but unresolved: a step that
has never run is not a check, it is an intention.

## Decision

**Keep MSan. Run it nightly only. Do not run it per-PR.**

Concretely:

- `cpp-msan` stays in `NIGHTLY_EXTRA_STEPS` in `scripts/ci/gate.sh` — it is not promoted to `full`.
- The MSan-instrumented libc++ is built once and cached, gated behind `EMS_MSAN_LIBCXX`. Until that
  lands, the step continues to skip **with the reason stated**, and this ADR is the record of why
  it is a scheduled task rather than an abandoned one.
- Tracked as **T-1** in [`docs/TODO.md`](../TODO.md).

### Why keep it rather than drop it

ASan and MSan catch different defects, and the difference is not marginal:

| | Catches |
|---|---|
| ASan | use-after-free, buffer overrun, double free — *spatial and lifetime* errors |
| MSan | reads of **uninitialised memory** — a value that was never written |
| Valgrind memcheck | both, but ~20× slower and with more false negatives on optimised builds |

An uninitialised read is precisely the defect class that produces a byte-level conformance failure
that reproduces *sometimes*. The journal encoder writes struct fields into a buffer; a field that
was never initialised yields whatever the stack held, which differs between runs, machines and
optimisation levels. That is the worst failure this project could have — the gate would flag a
divergence, and the divergence would vanish when investigated.

Valgrind does overlap MSan here, and it is already in the nightly lane. But Valgrind's uninitialised
-value tracking degrades on optimised builds, and the slice binary is built `RelWithDebInfo`. Having
both is not redundancy; it is two tools with different blind spots.

### Why nightly rather than per-PR

- **Cost.** MSan needs *every* library linked into the binary to be instrumented, including libc++.
  An uninstrumented libc++ produces false positives on every standard-library call, which is why
  the instrumented build is required rather than optional. That is a from-source build of libc++,
  and doing it per-PR would dominate the run.
- **The PR budget is ~10 minutes** (ADR 0004). The full lane is currently ~4 minutes. MSan would
  roughly double it for a defect class that ASan and TSan already partially cover on every PR.
- **Uninitialised reads do not appear and vanish between PRs.** They are introduced by a specific
  commit and persist. A nightly catch names the commit that introduced it via the previous night's
  green run; a per-PR catch is faster feedback for something rarely urgent.

## Consequences

- Uninitialised-memory defects are caught within 24 hours of introduction, not at PR time. That is
  the accepted cost of this decision and should be stated when one is eventually found.
- The nightly lane becomes the lane that can actually fail for a reason PRs did not catch. It needs
  someone to look at it — `docs/TODO.md` T-2 covers wiring a notification, because a red
  nightly nobody sees is worse than no nightly.
- If PR feedback ever needs shortening, MSan is not the thing to cut. It is already out.

## Alternatives considered

**Drop MSan; rely on ASan + Valgrind.** Rejected. Valgrind's uninitialised-value checking weakens
on optimised builds and the slice binary is optimised. The overlap is real but partial, and the
defect class is the one that would produce a heisenbug in the conformance gate.

**Promote MSan to the `full` lane.** Rejected on the PR-time budget above.

**Run MSan on an uninstrumented libc++ and filter the noise.** Rejected outright. A suppression
file large enough to silence libc++ is large enough to silence real findings, and nobody audits it.
