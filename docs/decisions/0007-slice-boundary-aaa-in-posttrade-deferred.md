# 0007 — AAA is in the slice; post-trade and drop-copy are deferred

Status: accepted
Date: 2026-07-31
Deciders: Anthony Si
Context: ADR [0002](0002-vertical-slice-cash-equity.md) — this settles an open question that ADR left explicit

## Context

ADR 0002 drew the slice boundary and flagged two of its own calls as unresolved:

> **Slice boundary.** §3.2 puts AAA in and post-trade drop-copy out. Both are judgment calls.

The question stayed open through components 1–5. It is now answerable from evidence rather than
from intuition, because AAA has actually been built in all three languages.

## Decision

**AAA stays in.** Post-trade and drop-copy are **deferred** — not cancelled, and not "out of scope
forever", but not part of the slice that has to reach conformance first.

### Why AAA earned its place

It was the first component that made the slice *refuse* something, and that turned out to matter
more than expected:

- It produced the first corpus case with rejections in it
  (`component-03-aaa-rejections`). Every case before it was a happy path, and an implementation
  that agreed on success while diverging on failure would have passed the whole corpus.
- Rejections are where three languages have the most room to disagree — an exception in one, a
  sentinel in another, an error value in the third. Without AAA the port would have proved
  agreement only on the path where nothing goes wrong.
- It forced three real production fixes that stand on their own merit: the clock became injectable
  (ADR 0004's `no_raw_clock` check exists because of it), `registerSession` was added, and the
  trace context stopped being drawn from `UUID.randomUUID()`.
- The three-layer firm → desk → user AND-gate gave the corpus three distinct reject codes to
  compare, which is a stronger test than one.

None of that was foreseeable when ADR 0002 was written. It is the retrospective justification, and
it is worth recording as such rather than pretending the original call was obviously right.

### Why post-trade and drop-copy wait

- **Nothing downstream depends on them.** Order intake → validation → FSM → routing → venue is a
  chain; allocation hangs off the end of it. Deferring the tail blocks nothing, whereas deferring
  AAA would have left every order unauthorised.
- **Drop-copy is a second consumer of an event stream the slice already emits.** It exercises no
  new decision logic — it re-publishes. A differential gate has little to bite on there, and ADR
  0002 already recorded it as "a recorded stub".
- **Allocation is genuinely in scope and genuinely later.** It is component 8 of 8 in
  `docs/polyglot/README.md` and will be built; it simply does not need to be built before the FSM,
  routing and the venue edge, all of which produce the fills it would allocate.

## Consequences

- The slice boundary in ADR 0002 stands unchanged. This ADR records that it was examined and kept,
  which is different from never having asked.
- `docs/polyglot/README.md` loses this from its open-questions list. An answered question sitting
  in a "questions" section gets re-litigated by the next reader.
- Post-trade allocation remains component 8. Drop-copy stays a recorded stub with a comment saying
  so — not silently absent.

## Alternatives considered

**Move AAA out and build the order path first.** Rejected on the evidence above: the corpus would
have had no rejection case until the validator landed, and the three production defects AAA
surfaced would have stayed hidden longer.

**Pull post-trade in now, for completeness.** Rejected. It would add a component with no new
decision logic for the gate to compare, ahead of components the fills actually depend on. Doing
work in dependency order is not the same as doing it in importance order, and here they agree.
