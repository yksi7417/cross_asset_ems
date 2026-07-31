---
type: idiom
status: draft
language: cross
anchor: rust/ems-core/src/journal.rs:270
tags: [concept/idioms, lang/rust, lang/java, lang/cpp, theme/absence]
---

# Representing "this may not be there"

## The idiom

A value that may be absent is given a type that *says so*, and the language refuses to let you read
it without handling the absent case — rather than a reference that may or may not be null and a
comment hoping you noticed.

## Why it was needed here

The journal parser accumulates three top-level keys — `fields`, `seq`, `type` — as it walks a line,
and none of them is present until it has been seen. Between "started parsing" and "finished
parsing" every one of them is legitimately absent, and at the end all three must be present or the
line is malformed.

That is the whole shape of the problem: three slots that are temporarily empty, and one place where
emptiness becomes an error. Every language in this port has to express it, and they express it
differently enough that the same twelve lines look like three different programs.

## What the naive version gets wrong

The Java-shaped version, written in Rust:

```rust
// Don't. This is the null-reference bug with extra steps.
let mut seq: u64 = 0;                 // sentinel: "not seen yet"
let mut seen_seq = false;
```

Now the invariant "`seq` is meaningful only when `seen_seq`" lives in the programmer's head, and
the compiler will happily read `seq` without checking the flag. That is exactly the defect
`Option<u64>` makes unrepresentable — and it is not hypothetical: `seq: 0` is a *legal* sequence
number, so the sentinel is indistinguishable from real data.

The subtler mistake is reaching for `.unwrap()` at the end, once you "know" the value is there:

```rust
let seq = seq.unwrap();   // denied by this workspace's clippy lints, and rightly
```

That converts a malformed input — attacker-controlled data, in a fuzz target — into a panic. The
line the anchor points at does the opposite: `ok_or_else` turns absence into the `MalformedJournal`
value the caller already has to handle.

## Where it lives

`rust/ems-core/src/journal.rs:270` — where the three `Option`s collapse into either a
`JournalEvent` or a `MalformedJournal` naming the missing key.

## Cross-language contrast

| | Type | What happens if you ignore the absent case |
|---|---|---|
| **Java** | `@Nullable String adminHint` (`java/ems-validator/.../ValidationResult.java`), and a plain local initialised to `null` in `JournalCodec.Parser` | Nothing at compile time — *unless* the package is `@NullMarked` and NullAway is on. `io.crossasset.ems.core.journal` is, which is why the parser's `requireAbsent(@Nullable Object, …)` had to be annotated: NullAway rejected passing a maybe-null local to a non-null parameter. Outside the ratcheted packages (`scripts/ci/nullmarked-baseline.txt`), the annotation is documentation. |
| **C++** | `std::optional<std::map<…>>` in `cpp/ems-core/src/journal.cpp` | Compiles. `*opt` on an empty optional is undefined behaviour — not an exception, not a crash you can rely on. `.value()` throws, but nothing makes you use it. The safety is real but opt-in. |
| **Rust** | `Option<BTreeMap<…>>` | Will not compile. There is no way to reach the value without `match`, `if let`, `?`, `ok_or*` or an explicit unwrap — and `unwrap_used` / `expect_used` are `deny` in `rust/Cargo.toml`, so the escape hatch is closed too. |

The three implementations parse the same grammar and produce byte-identical output, so this is a
clean comparison: same problem, same data, three different answers to "what stops me getting this
wrong". Java's answer is a checker you have to opt into package by package. C++'s is a type that
helps if you use it carefully. Rust's is the type system refusing to proceed.

Worth noting what Java's `@NullMarked` ratchet buys: once a package opts in, Java's guarantee is
roughly as strong as Rust's for that package. The difference is that Rust's applies everywhere by
default and Java's applies to two packages so far.

## Related

[[expected-without-exceptions]], [[unrepresentable-invalid-state]]
