---
type: idiom
status: draft
language: cpp
anchor: cpp/ems-core/include/ems_core/result.hpp:3
tags: [concept/idioms, lang/cpp, theme/error-handling]
---

# `Result<T, E>` — expected-style error handling without exceptions

## The idiom

A function that can fail returns a value holding *either* the result *or* an error, rather than
throwing or returning an error code. The caller cannot reach the value without acknowledging the
error case. `std::expected<T, E>` is the standard spelling of this in C++23; `Result<T, E>` in Rust;
`Either` in ML-family languages.

## Why it was needed here

`cpp/CMakeLists.txt` targets C++20, so `std::expected` is not available — it landed in C++23. The
journal parser needs an error channel anyway, for two reasons that are specific to this codebase:

- It is one of the three fuzz targets in the polyglot gate
  (`docs/decisions/0004-defensive-gate-stack.md`). Every malformed input must produce a *value*
  describing what was wrong, on a path the fuzzer can observe. An uncaught exception is a crash,
  and a crash is what the fuzzer is looking for.
- The C++ coding rules ban exceptions in steady-state paths (`cpp/README.md`), so the parser cannot
  use the mechanism C++ would otherwise reach for.

So `ems::core::Result<T, E>` is a deliberately small stand-in, sized to what the parser needs and
no more. It is not a general-purpose `expected` clone, and it should be deleted the day this tree
moves to C++23.

## What the naive version gets wrong

The obvious alternative is `std::optional<T>` plus an out-parameter:

```cpp
std::optional<JournalEvent> decode(std::string_view line, MalformedJournal& error);
```

Three things break. The error object must be default-constructible and is left in an indeterminate
state on success, so a caller that reads it after a *successful* parse gets stale data with no
diagnostic. Nothing forces the caller to look at it. And the signature no longer says what the
failure modes are — `MalformedJournal&` is an out-parameter by convention only.

The other naive version is an `int` error code, and it fails the same way `errno` does: ignoring
the return compiles silently.

`[[nodiscard]]` on `Result` is what makes this idiom bite rather than merely document. With
`-Werror` (also in `cpp/CMakeLists.txt`), a dropped `Result` is a build failure, not a warning
somebody scrolls past.

## Where it lives

`cpp/ems-core/include/ems_core/result.hpp:3` — the type itself. Its first real use is
`cpp/ems-core/include/ems_core/journal.hpp`, where every parse entry point returns
`Result<T, MalformedJournal>` and `write_journal` returns `Status<MalformedJournal>` (the
no-value case).

## Cross-language contrast

| | Mechanism | What forces the caller to handle it |
|---|---|---|
| **Java** | `sealed interface ValidationResult permits Pass, Reject` — and `MalformedJournalException` for the parser | Nothing. An exhaustive `switch` is checked, but nothing requires the caller to switch at all. The parser uses an unchecked exception, so a caller can ignore the failure entirely. |
| **C++** | `Result<T, E>` with `[[nodiscard]]` | The attribute, plus `-Werror`. The guarantee lives in the *build configuration*, not the type — turn off `-Werror` and it evaporates. |
| **Rust** | `Result<T, E>`, `#[must_use]` by default | The language. Ignoring a `Result` is a warning out of the box, and the value cannot be read without `match`, `?` or an explicit `unwrap` — and `unwrap` is itself denied by this workspace's lints. |

Three encodings of the same shape, ranked by how hard they are to ignore: Rust by construction,
C++ by build flag, Java by convention. That ranking is the point — the polyglot port exists to make
differences like this concrete rather than theoretical.

## Related

[[option-vs-nullable]], [[unrepresentable-invalid-state]]
