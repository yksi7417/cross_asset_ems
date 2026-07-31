# Traps

Every entry here is a real thing that went wrong while building the polyglot port, not a list of
things that could go wrong in principle. Each one cost time, and most of them looked **green** while
they were wrong — which is the property they have in common and the reason they are worth writing
down.

The column that matters is the last one. A trap that is now **enforced** cannot recur silently; a
trap that is only **documented** is still live, and knowing that is the point of splitting them.

**34 traps: 23 enforced, 11 documented.** The eleven are not a backlog to be embarrassed about — a
few genuinely cannot be checked (a misleading error message from a dependency, a squash rewriting
history). But the rest are ordinary gaps, and the ratio is the honest measure of how much of this
page is knowledge that will survive the people who learned it.

> **Adding to this file.** When something fools you, the fix is not finished until you have asked
> whether a check could have caught it. If one could, write the check and record it as enforced. If
> one genuinely cannot, add the row anyway and mark it documented — an honest "nothing stops this"
> is more use to the next person than silence.

---

## 1. Green builds over stale artefacts

The worst category, because every signal says the work succeeded.

| Trap | What actually happened | Now |
|---|---|---|
| **`2>/dev/null` on a code generator** | A hard `SyntaxError` — a duplicated `event_names=` kwarg — was swallowed. The generator ran, wrote nothing, and the golden test passed against **yesterday's output**. Caught only because a header did not contain a function that had just been added. | ⚠️ **Documented.** Never redirect a generator's stderr. No check enforces this. |
| **`git diff` sees only tracked files** | `fsm-sync` regenerated and diffed. A newly emitted file that had never been committed produced **no diff at all** and passed. | ✅ **Enforced** — `gate.sh:186` also runs `git ls-files --others --exclude-standard` over the generated paths. |
| **`.gitignore` hid generated output** | `rust/ems-fsm/src/generated/` matched an ignore rule, so the emitter's entire output was invisible to git — and therefore to the check above. | ✅ **Enforced** — negated with `!rust/ems-fsm/src/generated/`, and the untracked check would now catch a recurrence. |
| **A golden pin that did not cover the newest emitter** | `test_generated_golden.py` pinned Java and C++. Rust had been generated for several commits with **no pin at all** — so the emitter the test exists to protect was the one it did not cover. | ✅ **Enforced** — `RUST_GOLDEN` added, and watched failing on a deliberate tamper before being trusted. |
| **A gate step with no dispatch case** | `schema-lint` was listed in a lane array but had no `case` arm. It printed a note and carried on, doing nothing, for weeks. | ✅ **Enforced** — `gate.sh:489` makes an undispatched step **fail**. |
| **Sanitizers that built but never ran** | The ASan/TSan steps compiled the tree and stopped. A sanitizer that never executes a test finds nothing. | ✅ **Enforced** — `do_cpp_sanitize` now runs `ctest`, and `sanitizer_available()` link-probes so a missing runtime skips honestly instead of passing. |
| **A green `ctest` over zero tests** | Same shape: an empty test set is a pass. | ✅ **Enforced** — `--no-tests=error` on every `ctest` invocation. |

---

## 2. Tools that fail open

Defaults that quietly do less than the name suggests.

| Trap | What actually happened | Now |
|---|---|---|
| **gcovr 8.x ignores config-file `exclude`** | Coverage read 43% instead of 79%. The `exclude` entries in the config file were silently discarded — no warning, no error. | ✅ **Enforced** — excludes are passed on the command line (`gate.sh:313`), with a comment recording why. |
| **gcovr `filter` replaces the default root filter** | Adding an explicit `filter` dropped the implicit "project root only" one, so 49% of the report became libstdc++. | ✅ **Enforced** — same place, filters stated explicitly. |
| **cargo-deny allows no licences by default** | The default configuration rejected the project's **own crates**. | ✅ **Enforced** — `rust/deny.toml` states the allowlist. |
| **NullAway across the whole tree** | Enabling it repo-wide produced an unbuildable tree. | ✅ **Enforced** — `OnlyNullMarked` mode plus `nullmarked_ratchet.py`, so coverage only ever grows. |
| **NullAway 0.11.x fails at checker construction** | The error named `AnnotatedPackages` — nothing to do with the actual cause. Cost real time before the fix turned out to be a version bump to 0.12.3. | ⚠️ **Documented.** A misleading error message is not something a check can catch. |

---

## 3. Squash-merge

Bit us **three separate ways**. All three come from the same root: after a squash, the branch's
commits no longer exist on `main`, so anything reasoning about ancestry is lying.

| Trap | What actually happened | Now |
|---|---|---|
| **`gh pr view` says MERGED while the branch keeps growing** | Ancestry checks (`git log main..branch`) report commits that are already on `main` under a different hash. Only comparing **trees** or content is reliable. | ⚠️ **Documented.** Verify with a tree comparison, never with `git log`. |
| **A PR merged to the wrong base** | #67 went in against a stale base and had to be redone. | ⚠️ **Documented.** Check `baseRefName` before merging a stacked PR. |
| **`--delete-branch` closes the stacked child** | Merging #73 with `--delete-branch` **closed** #74 rather than retargeting it. GitHub does not move a stacked PR to `main` when its base branch disappears. | ⚠️ **Documented.** **Merge the child first, or omit `--delete-branch` on any PR that has something stacked on it.** Recovery is `git rebase --onto origin/main <old-base>` and a fresh PR. |

---

## 4. Cross-language divergence

The port's whole purpose, so these are expected — but the interesting ones are where **one language
noticed and the others did not**.

| Trap | What actually happened | Now |
|---|---|---|
| **`rustc` found state stored and never read** | `RouteBook` wrote an FSM state nothing consulted. The fix was not to delete the field: the runner now journals **what the book actually holds** rather than what the transition result predicted. Java and C++ said nothing. | ✅ **Enforced in Rust only** — `-D warnings` on `dead_code`. Java and C++ have no equivalent. |
| **ODR violation across generated headers** | An unprefixed `FsmEffectKind` would be a redefinition in any translation unit including two generated headers — and `slice_runner.cpp` includes both. Rust has no such problem; each machine is its own module. | ✅ **Enforced** — generated types are prefixed per machine, and `fsm_compile_test.cpp` includes all five headers in one TU specifically to catch this. |
| **`-Wdangling-reference`** | A test helper took `const std::string&` and was called with a literal. GCC caught it; nothing else would have. | ✅ **Enforced** — `-Wall -Wextra -Wconversion -Wold-style-cast -Werror`. |
| **Java's `noTransition` returns a null context** | Rust and C++ return the context unchanged. Three implementations of one generated contract disagree about what a declined event leaves behind. Latent only because every caller checks `isNoTransition()` first — and `newContext` is **not** `@Nullable`, so NullAway will not stop the first person who reads it. | ⚠️ **Documented** — registered as [T-8](../TODO.md). Live divergence, not yet fixed. |
| **Position-based test assertions** | Rust and C++ tests asserted on `out[3]`. The moment the FSM began emitting an `FsmTransition` before each outcome, every one broke. | ✅ **Enforced by construction** — `nth_of` / `count_of` helpers in both languages; assertions now say what they mean. |
| **Temp directories reused across runs** | A Rust test failed on a file it had not created. | ✅ **Enforced by construction** — pid-suffixed paths plus `remove_dir_all`. |

---

## 5. Checks that do not check what their name suggests

| Trap | What actually happened | Now |
|---|---|---|
| **A deferral recorded as a comment** | `// effects: deferred — C++ effect dispatch not yet generated` sat in the generator for months. `deferred_work.py` never saw it, because the register only catches deferrals that **opt in** with the `DEFERRED: T-n` form. A comment saying "deferred" is not a deferral anyone tracks. | ⚠️ **Documented.** The register cannot find deferrals that do not announce themselves. |
| **`T-1` matched prose** | The marker regex hit "T minus 1" in `CorporateActionState.java`. | ✅ **Enforced** — markers require the explicit `DEFERRED:` prefix. |
| **A check that failed on itself** | `deferred_work.py`'s own test file contains marker strings, so the check flagged its own fixtures. | ✅ **Enforced** — excluded by exact path, not by a `test_*.py` glob that would have hidden real violations in other test files. |
| **`no_raw_clock.py` fast path keyed on `"System."`** | `System . nanoTime ()` is legal Java and would have slipped past. | ✅ **Enforced** — keys on `"System"`, with `test_whitespace_between_tokens_still_matches` pinning it. |
| **`fsm-coverage` scope as a permanent hole** | Tempting to mark `route` in-scope at 1 of 29 transitions with a partial-coverage exemption. **An exemption written once is an exemption nobody removes.** | ✅ **Enforced by design** — scope is binary. In-scope means *every* transition; out-of-scope carries a stated reason and a component number. |
| **File mode 100644 on a script** | `install-toolchain.sh` was committed non-executable. Worked locally, "Permission denied" in CI only. | ✅ **Enforced** — the `exec-bits` gate step. |

---

## 6. Documentation drift

Every one of these was a doc asserting something false while the gate was green. Prose is the part
of the repo with no compiler.

| Trap | What actually happened | Now |
|---|---|---|
| **`status.md` corpus table** | Claimed "**9 of 9** — three cases" while the harness was running four. `component-05` never got a row. | ⚠️ **Documented.** Nothing checks that the table matches the corpus directory. |
| **Idioms `README.md`** | Said "**No idiom notes yet**" while seven notes existed. | ⚠️ **Documented** — and the README now says so explicitly. Closing it is [T-6](../TODO.md). |
| **`SliceRunner` class doc** | Said "covers components 1–3 … no validation pipeline, no FSM, no routing" long after 4, 5 and 6a had landed. | ⚠️ **Documented.** Three copies of this doc exist, one per language, and nothing keeps them in step. |
| **A study-guide anchor pointing at a moved line** | Inserting the effect enum shifted `order_fsm.rs` and the anchor went stale. | ✅ **Enforced** — `study_guide.py` resolves every anchor to a line carrying its marker. It caught this one. |

---

## 7. Domain traps

Not tooling — the business logic itself.

| Trap | What actually happened | Now |
|---|---|---|
| **Keying the order book on OrderID** | Taking the order id before validation meant a **rejected order consumed one**, so identifiers depended on how many earlier orders failed and every corpus case downstream of a rejection shifted. The fix was the domain answer: key on **ClOrdID**, which exists before the order reaches us. | ✅ **Enforced** — `aRejectedOrderDoesNotConsumeAnIdentifier`, which is the test that caught it. |
| **The same trap, one layer down** | Route creation could have burned a route id on a refusal. The ClOrdID collision check therefore runs **before** an id is drawn. | ✅ **Enforced** — `aRefusedRouteDoesNotConsumeAnIdentifier`, in all three languages. |
| **Counting terminal routes as committed quantity** | `routedQty` sums every route. Right for a filled route, wrong for a rejected one — an order whose only route the venue refused can never be re-routed. | ⚠️ **Documented** — registered as [T-7](../TODO.md). The rule cannot be tested until component 6b makes those states reachable. |

---

## Related

- [`gate.md`](gate.md) — what each step does and which lane runs it
- [`status.md`](status.md) — per-language feature matrix and test evidence
- [`../TODO.md`](../TODO.md) — the deferral register (T-1 … T-8)
- [`../decisions/`](../decisions/) — the ADRs, several of which exist because of a trap above
