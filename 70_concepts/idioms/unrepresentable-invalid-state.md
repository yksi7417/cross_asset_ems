---
type: idiom
status: draft
language: cross
anchor: cpp/ems-core/include/ems_core/ids.hpp:29
tags: [concept/idioms, lang/cpp, lang/rust, lang/java, theme/types]
---

# Make the invalid state unrepresentable, and the check disappears

## The idiom

Choose a type that cannot hold a value you would have to reject, and the validation you would have
written stops existing. No check to forget, no check to test, no check to keep in sync between
three implementations.

## Why it was needed here

`DeterministicIds` takes a seed. A negative seed is meaningless — the seed is a starting offset for
a counter that only ever increases.

In Java the parameter is `long`, so a negative value is representable and has to be rejected:

```java
if (seed < 0) {
  throw new IllegalArgumentException("seed must not be negative: " + seed);
}
```

That check needs a unit test (`DeterministicIdsTest.negativeSeedIsRejected`), and every port has to
decide whether to reproduce it. In C++ and Rust the parameter is `std::uint64_t` / `u64` and the
question does not arise: there is no negative value to pass.

This matters more than the three lines it saves, because the three implementations must agree
byte-for-byte. Every behavioural rule expressed as a *runtime check* is a rule that can be
implemented three ways and drift. A rule expressed in the *type* cannot drift, because two of the
three languages will not compile the divergence.

## What the naive version gets wrong

The naive version is to mirror the Java signature faithfully:

```cpp
// Don't. This imports a bug Java had to defend against.
explicit DeterministicIds(std::int64_t seed) {
    if (seed < 0) { /* now what? no exceptions on this path */ }
}
```

Two failures. First, it recreates the invalid state on purpose, then needs an error channel to
reject it — on a constructor, where C++ has no good answer under a no-exceptions rule. Second, the
port now has a behaviour the Java version has and the Rust version does not, so "the three agree"
becomes "the three agree except here", which is the beginning of the end for a differential gate.

The mirror-image mistake is to keep the unsigned type but silently accept a wrapped value:

```cpp
DeterministicIds ids{static_cast<std::uint64_t>(-1)};  // 18446744073709551615
```

`-Wconversion` and `-Wold-style-cast` (both in `cpp/CMakeLists.txt`) make the accidental version of
this a build error. The deliberate `static_cast` is still expressible — no type system stops a
programmer determined to lie — but it is now visible in review rather than hidden in an implicit
conversion.

## Where it lives

`cpp/ems-core/include/ems_core/ids.hpp:29` — the constructor, with no validation in it. The
corresponding Java constructor
(`java/ems-core/src/main/java/io/crossasset/ems/core/journal/DeterministicIds.java`) has the check
this one does not need.

## Cross-language contrast

| | Signature | Negative seed |
|---|---|---|
| **Java** | `DeterministicIds(long seed)` | Representable. Rejected at runtime, with a test to prove it. |
| **C++** | `explicit DeterministicIds(std::uint64_t seed)` | Not representable. An implicit narrowing conversion is a build error under `-Wconversion`; an explicit cast is a deliberate lie. |
| **Rust** | `DeterministicIds::new(seed: u64)` | Not representable. `-1` does not compile as a `u64` literal. |

The CLI is where the difference resurfaces: all three binaries accept `--seed` as text, so all
three must reject `--seed -1` at the argument-parsing boundary. The type only protects the interior
of the program. That is the honest limit of this idiom — it moves the check to the edge, it does
not delete it.

## Related

[[option-vs-nullable]], [[expected-without-exceptions]]
