# 0005 — Study guide as vault notes with CI-enforced code anchors

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: ADR [0001](0001-reinstate-rust-three-language-port.md), `70_concepts/glossary/`

## Context

The port has two deliverables that constrain each other: a working system, and a study artifact.
The second exists because the port is meant to demonstrate technical depth — so any idiom that is
not immediately obvious has to be explained where a reader will actually find it.

An unenforced study guide rots into a lie within weeks: code moves, notes keep pointing at line
numbers that now hold something else, and a reader who follows a stale anchor trusts the guide
less than if it had never existed. A rotted study guide is worse than none.

## Decision

A new vault section, `70_concepts/idioms/`, one note per idiom, alongside the existing
`70_concepts/glossary/`. Each note states:

- **The idiom** — what it is, in general terms.
- **Why it was needed here** — the specific constraint in this codebase that forced it.
- **What the naive version gets wrong** — the failure mode, concretely. This is the part that makes
  it a study guide rather than a description.
- **Where it lives** — a `file:line` link to the real usage site.
- **Cross-language contrast** — how the other two languages solve the same problem when they solve
  it differently. Three solutions to one problem, side by side, is where most of the teaching value
  is.

The source carries a marker at the usage site:

```cpp
// STUDY: crtp-static-dispatch
```

CI asserts, bidirectionally:

- every `STUDY:` marker in source has a matching note in `70_concepts/idioms/`;
- every note's `file:line` anchor still resolves to a line carrying that marker.

## Consequences

- The guide cannot drift from the code. Moving marked code without updating the note fails the
  build — which is the point, and is itself part of what the project demonstrates.
- Notes are written **as** idioms land, not retroactively at the end. A `STUDY:` marker without a
  note fails CI, so the marker and the note land in the same PR by construction.
- Adding a marker carries a documentation obligation. That is intentional friction: it is the
  mechanism that keeps the guide honest.
- Expected candidates (confirmed during implementation, not committed to up front):
  - **C++** — CRTP vs. virtual dispatch on the FSM hot path; `std::expected` error propagation
    without exceptions; arena / monotonic allocation and whether `std::pmr` earned its place;
    strict aliasing and `std::bit_cast` in SBE decode; move semantics and the rule of zero/five;
    iterator invalidation in the route table; `std::span` at boundaries.
  - **Rust** — lifetime elision and where it stops working; `Cow` in the FIX decoder; typestate
    encoding of FSM states so illegal transitions do not compile; newtype wrappers over scaled
    integer prices; `From`/`TryFrom` chains; where `Rc<RefCell<>>` was needed and what it costs;
    sealed traits at module boundaries.
  - **Cross-language** — how each represents "an order that may or may not have a price"
    (`@Nullable Long` vs `std::optional<int64_t>` vs `Option<Price>`); how each encodes FSM state
    exhaustiveness; how each handles a validation failure without exceptions.

## Alternatives considered

**Standalone runnable examples.** Rejected: they drift from real usage immediately, and an example
that compiles in isolation teaches the idiom without the constraint that forced it — which is the
half that matters.

**Comments only, no notes.** Rejected: not browsable as a unit, and no place to put the
cross-language contrast, which is the highest-value part.

**Notes without CI enforcement.** Rejected: see Context. This is the failure mode the decision
exists to prevent.
