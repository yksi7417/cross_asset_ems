# Polyglot Port — Sub-project 6: Study Guide — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the idiom notes accumulated during sub-projects 4 and 5 into a browsable, indexed,
cross-linked study guide, write the cross-language contrast notes that have no single home, and
harden the integrity check.

**Architecture:** The enforcement mechanism already exists (`scripts/ci/checks/study_guide.py`,
shipped in sub-project 1) and individual notes were written **as** their idioms landed — a marker
without a note fails the gate, so they could not have been deferred. What remains is the connective
tissue: the section index, the three cross-language contrast notes, vault wikilinks, and closing
the gaps this sub-project's own audit finds.

**Tech Stack:** Markdown (Obsidian vault conventions), Python 3 stdlib.

**Depends on:** sub-projects 4 and 5.

## Global Constraints

- Every note follows the template in `70_concepts/idioms/README.md`: the idiom, why it was needed
  here, what the naive version gets wrong, where it lives, cross-language contrast.
- **"What the naive version gets wrong" is not optional.** It is what makes this a study guide
  rather than a description. If it cannot be written for a note, the idiom probably was not needed
  and the marker should come out.
- Every `anchor:` resolves. The gate enforces this; do not defeat it by anchoring to a comment
  someone parked at the top of a file.
- Vault conventions: YAML front matter with `type`, `status`, `tags`; `[[wikilinks]]` to related
  notes.
- No note claims a performance property without a measurement. "CRTP because virtual dispatch was
  slow" needs a number or it is folklore.

---

## File Structure

| File | Responsibility |
|---|---|
| `70_concepts/idioms/idioms-index.md` | The section index — by language, by theme, and by which module the idiom lives in. |
| `70_concepts/idioms/nullable-price.md` | Cross-language: "an order that may or may not have a price". |
| `70_concepts/idioms/fsm-state-exhaustiveness.md` | Cross-language: how each language makes an unhandled FSM transition impossible, or fails to. |
| `70_concepts/idioms/validation-failure-without-exceptions.md` | Cross-language: `ValidationResult` / `std::expected` / `Result`. |
| `70_concepts/idioms/*.md` | The per-idiom notes from sub-projects 4 and 5 — audited and completed here, not written here. |
| `scripts/ci/checks/study_guide.py` | Gains the completeness checks from Task 4. |
| `70_concepts/glossary-index.md` | Gains a pointer to the idioms section. |
| `00_index/architecture-index.md` | Gains a pointer to the idioms section. |

---

### Task 1: Audit what actually landed

**Files:**
- Create: `/tmp/idiom-audit.md` (working notes, not committed)

- [ ] **Step 1: List every marker and every note**

```bash
grep -rn "STUDY:" java/ cpp/ rust/ --include=*.java --include=*.cpp --include=*.hpp --include=*.rs
ls 70_concepts/idioms/
python3 scripts/ci/checks/study_guide.py
```

- [ ] **Step 2: Check each note against the template, and write down the gaps**

For every note, answer:

1. Does it have all five sections?
2. Is "what the naive version gets wrong" **concrete** — actual wrong code and what it does — or is
   it a paraphrase of the idiom?
3. Is the cross-language contrast filled in, or a placeholder?
4. Does any performance claim carry a number?

The output is a list of specific gaps. Do not fix them yet — a full picture first tells you which
gaps are the same gap.

- [ ] **Step 3: Check for idioms that were used but never marked**

Read the diff of sub-projects 4 and 5 looking for constructs a reader would have to stop and work
out. Common misses: a lifetime annotation that took three attempts, a `const&` vs. value decision
that mattered, a place where the obvious API was rejected. These are exactly the ones worth
documenting and exactly the ones nobody remembers to mark.

- [ ] **Step 4: No commit** — this task produces a work list, not an artifact.

---

### Task 2: Fill the gaps

**Files:**
- Modify: the notes the audit flagged
- Create: notes for the unmarked idioms the audit found (with their `STUDY:` markers)

- [ ] **Step 1: For each gap, fix the note**

Work note by note. Each note is one commit at the end of this task's step 4 — or one commit each if
there are more than a handful.

- [ ] **Step 2: For each unmarked idiom, add the marker and write the note**

- [ ] **Step 3: Verify the integrity check still passes**

Run: `python3 scripts/ci/checks/study_guide.py`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add 70_concepts/idioms/ java/ cpp/ rust/
git commit -m "docs(idioms): complete the notes from sub-projects 4 and 5"
```

---

### Task 3: The three cross-language contrast notes

These have no single usage site — they are about how the three languages differ, so each anchors at
the site that best illustrates the contrast and covers the other two in prose.

**Files:**
- Create: `70_concepts/idioms/nullable-price.md`
- Create: `70_concepts/idioms/fsm-state-exhaustiveness.md`
- Create: `70_concepts/idioms/validation-failure-without-exceptions.md`

- [ ] **Step 1: Write `nullable-price.md`**

The question: how does each language represent "an order that may or may not have a price"?

- Java: `@Nullable Long price` — the annotation is checked only where the package is `@NullMarked`,
  which is why `io.crossasset.ems.validator` was annotated first. Outside those packages the
  annotation is documentation.
- C++: `std::optional<std::int64_t>` — no null at all, but `operator*` on an empty optional is
  undefined behaviour rather than a thrown exception, so the safety is conditional on discipline.
- Rust: `Option<Price>` — unrepresentable to use without handling the `None` case, and `Price` is a
  newtype so an unscaled integer cannot be passed by mistake.

The interesting part is the ranking and why: all three express the same contract, and they differ
in *when* a violation is caught — compile time, run time, or never. Say that explicitly.

Anchor at the Rust `Option<Price>` field.

- [ ] **Step 2: Write `fsm-state-exhaustiveness.md`**

- Java: generated `switch` over an enum with a `default` arm that throws. A new state compiles fine
  and fails at run time.
- C++: generated `switch` with `-Werror=switch`, which catches a missing arm at compile time — but
  only if no `default` arm exists, and the generated code must not add one.
- Rust: exhaustive `match` on a `(state, event)` tuple. A new state fails to compile.

The teaching point: all three are generated from the same YAML, and the same generator produces
three different guarantees. That is the clearest single illustration of what the port is for.

Anchor at the generated Rust `match`.

- [ ] **Step 3: Write `validation-failure-without-exceptions.md`**

- Java: a sealed `ValidationResult` interface with `Pass` and `Reject` records — exhaustive
  `switch` at the call site, but nothing forces the caller to look at the result.
- C++: `std::expected<T, E>` with `[[nodiscard]]` — a dropped result is a warning, and `-Werror`
  makes it an error.
- Rust: `Result<T, E>` with `#[must_use]` by default.

Same shape, three different levels of enforcement, and the C++ one only works because of a compiler
flag set in `cpp/CMakeLists.txt` — which is worth saying out loud, because it means the guarantee
travels with the build configuration rather than with the code.

- [ ] **Step 4: Verify anchors and links**

Run: `python3 scripts/ci/checks/study_guide.py`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add 70_concepts/idioms/
git commit -m "docs(idioms): three cross-language contrast notes"
```

---

### Task 4: Harden the integrity check

**Files:**
- Modify: `scripts/ci/checks/study_guide.py`
- Modify: `scripts/ci/checks/test_study_guide.py`

The check currently proves markers and notes agree. It does not prove a note is *useful*. Three
mechanical additions close the gap between "a note exists" and "a note says something":

1. Every note has all five required headings.
2. No note contains a placeholder marker (`TODO`, `TBD`, `...`, an empty section).
3. Every note is reachable from `70_concepts/idioms/idioms-index.md`.

- [ ] **Step 1: Write the failing tests**

```python
def test_note_missing_a_required_heading_fails(self):
    root = tree({
        "cpp/a.cpp": "// STUDY: x\n",
        "70_concepts/idioms/x.md": "---\nanchor: cpp/a.cpp:1\n---\n\n# X\n\n## The idiom\nWords.\n",
        "70_concepts/idioms/idioms-index.md": "- [[x]]\n",
    })
    errors = check(root)
    self.assertTrue(any("What the naive version gets wrong" in e for e in errors), errors)

def test_note_with_a_todo_placeholder_fails(self): ...
def test_note_missing_from_the_index_fails(self): ...
def test_complete_note_passes(self): ...
```

- [ ] **Step 2: Run to verify failure**

Run: `python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' -v`
Expected: FAIL.

- [ ] **Step 3: Implement the three checks**

Required headings, as a constant so the list is greppable:

```python
REQUIRED_HEADINGS = (
    "## The idiom",
    "## Why it was needed here",
    "## What the naive version gets wrong",
    "## Where it lives",
    "## Cross-language contrast",
)
```

- [ ] **Step 4: Run against the real tree**

Run: `python3 scripts/ci/checks/study_guide.py`
Expected: it will fail on notes with placeholder sections. Fix the notes, not the check. If a note
genuinely has no cross-language contrast — an idiom with no counterpart in the other two — the
section says so in one sentence and explains why, which is itself informative.

- [ ] **Step 5: Commit**

```bash
git add scripts/ci/checks/
git commit -m "feat(ci): study-guide check asserts notes are complete, not merely present"
```

---

### Task 5: The index and the vault links

**Files:**
- Create: `70_concepts/idioms/idioms-index.md`
- Modify: `70_concepts/glossary-index.md`
- Modify: `00_index/architecture-index.md`
- Modify: `70_concepts/idioms/README.md` (the "no notes yet" section comes out)
- Modify: `docs/polyglot/README.md` (status: sub-project 6 done)

- [ ] **Step 1: Write `idioms-index.md`**

Three groupings, because three different readers arrive three different ways:

- **By language** — someone learning Rust wants the Rust notes.
- **By theme** — error handling, ownership, dispatch, encoding, determinism.
- **By module** — someone reading `ems-validator` wants what applies there.

Each entry is a wikilink plus a one-line hook. The one-line hook is what makes an index browsable
rather than a directory listing.

- [ ] **Step 2: Add pointers from the glossary index and architecture index**

The idioms section is about language, the glossary is about domain. Say that distinction explicitly
in both directions so a reader lands in the right one.

- [ ] **Step 3: Add `[[wikilinks]]` between related notes**

At minimum: each cross-language contrast note links to the per-language notes it summarises, and
each per-language note links back.

- [ ] **Step 4: Verify**

```bash
python3 scripts/ci/checks/study_guide.py
EMS_GATE_STRICT=1 scripts/ci/gate.sh full
```
Expected: both exit 0.

- [ ] **Step 5: Commit**

```bash
git add 70_concepts/ 00_index/ docs/
git commit -m "docs(idioms): section index and vault cross-links"
```

---

### Task 6: Close the programme out

**Files:**
- Modify: `docs/polyglot/README.md`
- Create: `docs/polyglot/retrospective.md`

- [ ] **Step 1: Update the status table — all six sub-projects done**

- [ ] **Step 2: Resolve or re-record every open question**

`docs/polyglot/README.md` carries three open questions (slice boundary, MSan, corpus authority).
Each is now either answered by what was built or still open. Answered ones move into
`docs/decisions/` as an ADR; still-open ones stay listed with what was learned. **Silently dropping
one is not an option** — an open question that disappears reads as answered.

- [ ] **Step 3: Write `docs/polyglot/retrospective.md`**

Not a victory lap. What it must contain:

- Which gate steps ever actually caught something, and which never fired. A gate that has never
  failed is either unnecessary or untested, and you now know which.
- Where the byte-identical requirement cost more than it was worth, if anywhere.
- Whether Rust-before-C++ paid off, with evidence — did the C++ port hit fewer ownership problems?
- What the anti-stub check would have caught in the original `cpp/` tree, and how much earlier.
- Which idioms turned out to be the same idiom under three names.

- [ ] **Step 4: Final full-gate run**

Run: `EMS_GATE_STRICT=1 scripts/ci/gate.sh nightly`
Expected: exit 0, every skip having a recorded decision behind it.

- [ ] **Step 5: Push and confirm CI is green**

Run: `git push && gh run watch`

- [ ] **Step 6: Commit**

```bash
git add docs/
git commit -m "docs(polyglot): programme complete, retrospective recorded"
```

---

## Definition of done

- One note per `STUDY:` marker; every note has all five sections filled with real content.
- The integrity check enforces completeness, not just existence, and its tests prove it.
- `idioms-index.md` reaches every note, three ways.
- The glossary index and architecture index both point at the idioms section, with the
  language-vs-domain distinction stated.
- Every open question from `docs/polyglot/README.md` is either an ADR or still explicitly listed.
- The retrospective names at least one gate step that never fired, and says what should happen to it.
