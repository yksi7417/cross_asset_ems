---
type: index
status: active
tags: [concept/idioms, polyglot]
---

# Language idioms — the study guide

One note per non-obvious idiom used in the polyglot port, sitting alongside
[[glossary-index]]. The port exists partly to demonstrate technical depth, so anything a reader
would have to reverse-engineer from the source gets explained here, next to a link to the real
usage site.

**This section is CI-enforced.** `scripts/ci/checks/study_guide.py` runs in the `full` and
`nightly` gate lanes and asserts, in both directions:

- every `STUDY: <slug>` marker in `java/`, `cpp/` or `rust/` has a note at
  `70_concepts/idioms/<slug>.md`;
- every note's `anchor:` still resolves to a line carrying that note's marker.

Move marked code without updating the note and the build fails. That is the point — an unenforced
study guide rots into a lie within weeks, and a rotted study guide is worse than none. See
[ADR 0005](../../docs/decisions/0005-study-guide-with-enforced-anchors.md).

## Adding a note

**1. Mark the usage site.** One comment, at the line the idiom actually lives on:

```cpp
// STUDY: crtp-static-dispatch
template <typename Derived>
class FsmDispatch { /* ... */ };
```

```rust
// STUDY: typestate-order-fsm
pub struct Order<S: OrderState> { /* ... */ }
```

Slug rules: lowercase, digits and hyphens only, and it must match the note filename exactly.

**2. Write `70_concepts/idioms/<slug>.md`** from this template:

```markdown
---
type: idiom
status: draft
language: cpp | rust | java | cross
anchor: cpp/ems-fsm/include/ems_fsm/dispatch.hpp:42
tags: [concept/idioms, lang/cpp]
---

# <Idiom name>

## The idiom
What it is, in general terms. Two or three sentences, no codebase specifics.

## Why it was needed here
The specific constraint in *this* codebase that forced it. Name the module, the
gate, or the determinism requirement that left no alternative.

## What the naive version gets wrong
The failure mode, concretely — the code someone would write instead, and what
breaks. This is the part that makes the note a study guide rather than a
description. If you cannot write this section, the idiom probably was not
needed.

## Where it lives
`cpp/ems-fsm/include/ems_fsm/dispatch.hpp:42` — one sentence on what that call
site is doing.

## Cross-language contrast
How the other two languages solve the same problem, when they solve it
differently. Three solutions to one problem, side by side, is where most of the
teaching value is.

## Related
[[some-other-idiom]], [[an-architecture-note]]
```

**3. Verify before committing:**

```bash
python3 scripts/ci/checks/study_guide.py
```

Exit 0 means the marker and the note agree.

## When the anchored code moves

The check fails with `anchor <path>:<line> carries no STUDY marker`. Fix the note's `anchor:`
line — do not delete the marker to silence it. If the idiom genuinely no longer applies, remove
the marker **and** the note in the same commit.

## Notes

No idiom notes yet — they land as the Rust and C++ slices land (sub-projects 4 and 5). Expected
candidates are listed in [ADR 0005](../../docs/decisions/0005-study-guide-with-enforced-anchors.md):
CRTP vs. virtual dispatch, `std::expected` without exceptions, `std::bit_cast` in SBE decode,
typestate FSM encoding, `Cow` in the FIX decoder, newtype-wrapped scaled prices, and the
cross-language contrast for "an order that may or may not have a price".

## Related

- [[glossary-index]] — domain vocabulary, not language idioms
- `docs/polyglot/README.md` — the port this section documents
