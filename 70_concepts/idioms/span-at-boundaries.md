---
type: idiom
status: draft
language: cpp
anchor: cpp/ems-it/src/slice_main.cpp:57
tags: [concept/idioms, lang/cpp, theme/memory-safety]
---

# `std::span` at boundaries instead of pointer-plus-length

## The idiom

A function that receives a contiguous sequence takes a `std::span<T>` — a pointer and a length
travelling together as one object — rather than two separate parameters the caller has to keep
consistent.

## Why it was needed here

`main(int argc, char** argv)` is the original pointer-plus-length API, and argument parsing is the
one place in `ems-slice` that reads an unbounded array supplied from outside the process. The
parser has to look ahead — `--input` is followed by its value — so every branch is a candidate for
reading one element past the end.

Converting once at the boundary means the length is carried by the type from that point on, and
`argv.size()` is the only bound any loop can consult. There is no second parameter to forget to
pass, and no arithmetic on `argc` to get wrong.

## What the naive version gets wrong

```cpp
// Don't.
for (int i = 1; i < argc; ++i) {
    if (std::string_view(argv[i]) == "--input") {
        args.input = argv[i + 1];   // reads argv[argc] when --input is last
    }
}
```

`ems-slice --input` — a plausible typo — reads one past the end of the array. In practice `argv` is
NULL-terminated, so this particular case tends to construct a `std::string` from a null pointer:
undefined behaviour that usually crashes, sometimes does not, and is exactly the class of defect
the ASan/UBSan lanes exist to catch. The `dangling_flag_value_is_an_error` test in the Rust binary
and `SliceMainTest` in Java cover the same input; the C++ version has to be safe by construction
rather than by remembering.

The span version has no `i + 1` outside a bounds check — the lookahead is a lambda that consults
`argv.size()` first and returns `std::nullopt` when there is nothing there.

The subtler mistake is converting to a span but keeping the raw index arithmetic anyway. A span
does not check `operator[]`; it makes the length *available*, it does not enforce it. The
protection comes from using `.size()`, or `.subspan()`, or a range-for — not from the type alone.

## Where it lives

`cpp/ems-it/src/slice_main.cpp:57` — `parse_args(std::span<const char* const> argv, …)`, with the
`argc`/`argv` conversion confined to `main`.

## Cross-language contrast

| | Boundary shape | Reading past the end |
|---|---|---|
| **Java** | `String[] args` — arrays carry their length | `ArrayIndexOutOfBoundsException`. Safe, but a runtime failure, and `SliceMain` still has to guard `++i` explicitly to produce a usage message rather than a stack trace. |
| **C++** | `std::span<const char* const>` | Unchecked. The span supplies the length; using it is the programmer's job. |
| **Rust** | `&[String]`, read via `.get(i)` returning `Option<&String>` | Impossible to ignore. `command_line.get(i).ok_or("--input requires a path")?` is the whole guard — the absent case is a value, not a branch you might forget. Indexing with `[i]` would panic rather than read out of bounds. |

All three binaries reject `ems-slice --input` with the same message and the same exit code. What
differs is what happens if the guard is *omitted*: Rust cannot express the unguarded version
without `[i]`, Java throws, and C++ reads memory it does not own.

## Related

[[option-vs-nullable]], [[unrepresentable-invalid-state]]
