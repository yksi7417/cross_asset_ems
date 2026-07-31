# 0009 — Java is the corpus authority for now; triangulation is a recorded follow-up

Status: accepted
Date: 2026-07-31
Deciders: Anthony Si
Context: ADR [0003](0003-shared-schemas-corpus-harness.md) — this settles an open question that ADR left explicit

## Context

ADR 0003 made Java the reference that generates `expected.jsonl`, and named the weakness in the
same breath:

> Java is the corpus authority: it generates `expected.jsonl`. Conformance therefore proves
> *equivalence*, not *correctness* — a Java bug faithfully reproduced by both ports passes.

and left the question open:

> **Corpus authority.** Java generates `expected.jsonl`, making Java correct by definition. The
> alternative — hand-authored expectations from the schemas — is stricter and considerably more
> work. Worth it?

## Decision

**Java remains the authority. Keep the review discipline that compensates for it. Add
triangulation later — recorded, not promised vaguely.**

Three parts:

1. **Java generates `expected.jsonl`.** Unchanged from ADR 0003.
2. **Every case is reviewed against the schemas**, and each `case.md` records what it was checked
   against. This is already happening and is the thing that makes the arrangement tolerable —
   `component-04-validation-layers/case.md`, for instance, cites the four catalog entries its codes
   come from by name.
3. **Triangulation is tracked as T-3** in [`docs/TODO.md`](../TODO.md), to be
   picked up once all three implementations are complete.

### What "triangulate" means here, precisely

Once Rust and C++ implement the whole slice, the interesting property is not "do they match Java"
but "**do all three agree**". At that point:

- A **2-1 disagreement** is strong evidence the odd one out is wrong. Two independent
  implementations of the same spec rarely make the same mistake.
- A **3-way agreement** on something the schema forbids means the *spec* was read the same wrong
  way three times — which is a finding about the schema's clarity, not the code.

That is a genuinely stronger check than "Rust and C++ match Java", and it costs nothing extra to
run: the harness already executes all three. What it needs is a *differ mode* that treats the three
outputs symmetrically instead of privileging one, and a policy for what a 2-1 split means. That is
the work T-3 captures.

### Why not hand-author expectations now

- **It would slow every component down for a benefit that arrives later.** A hand-authored
  expectation must be derived from the FSM YAML, the reject-code catalog and the validator rules by
  hand, per case. At three corpus cases the cost is small; the value is also small, because Java's
  behaviour on those paths is well-tested (2,374 tests).
- **It moves the failure mode rather than removing it.** A hand-authored expectation encodes the
  *author's* reading of the schema. If that reading is wrong, all three implementations get marked
  wrong and the author is the single point of failure — which is the same problem as Java being the
  authority, with a less-tested authority.
- **The review step already catches the case that matters.** The realistic Java bug is not "the
  whole order path is wrong"; it is "this reject code should be 1002, not 1001". A reviewer holding
  `case.md` next to `catalog.yaml` catches exactly that, and has.

### The honest limit

**This does not prove correctness, and no amount of conformance will.** It proves that three
implementations agree. `conformance/README.md` says so, ADR 0003 says so, and this ADR says so
again because it is the single most misreadable claim in the project:

> Java is the reference that generates `expected.jsonl` […] This proves *equivalence*, not
> *correctness* — a Java bug both ports faithfully reproduce passes.

Correctness comes from the Java test suite, the schema review on each case, and — later —
triangulation. Not from the byte comparison.

## Consequences

- A Java defect on a path a corpus case exercises will be faithfully reproduced by Rust and C++ and
  will pass the gate. That is a known, accepted, documented gap.
- `case.md` files must keep stating what they were reviewed against. A case whose `case.md` says
  only "covers the happy path" has skipped the compensating control, and should fail review.
- Triangulation is a follow-up with an owner-shaped entry in the TODO, not an aspiration in prose.

## Alternatives considered

**Hand-author `expected.jsonl` from the schemas now.** Rejected on cost-versus-timing above.
Deferred rather than dismissed: T-3 revisits it.

**Make the newest implementation the authority.** Rejected — it inverts the problem, making the
*least*-tested implementation definitive.

**Generate expectations from the schemas mechanically.** Interesting, and genuinely stronger than
either option, but it amounts to writing a fourth implementation — one that reads the FSM YAML and
the catalog and produces expected output. If that existed it would *be* the reference, and the
question would become who checks it. Recorded here so the next person does not have to rediscover
the regress.
