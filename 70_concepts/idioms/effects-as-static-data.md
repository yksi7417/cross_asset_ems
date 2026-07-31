---
type: idiom
status: draft
language: cross
anchor: cpp/fsm/generated/route_fsm.hpp:146
tags: [concept/idioms, lang/rust, lang/java, lang/cpp, theme/memory-safety, theme/codegen]
---

# Data that outlives its caller: returning generated effects

## The idiom

When a function returns a list whose contents are known at compile time, it can hand back a
**reference to static storage** rather than an owned container — no allocation on the hot path, and
nothing for the caller to free. The question each language answers differently is *who proves the
reference is still valid*.

## Why it was needed here

Every transition in `schemas/fsm/*.fsm.yaml` can declare effects: log this, cascade that event to
another machine, notify ops. All of it is written in the YAML, so by the time the generator has run,
a transition's effects are **literals**. Nothing about them depends on the order, the venue, or the
run.

That makes an owned `Vec`/`vector` per transition pure waste: `transition()` is on the path every
inbound venue message takes, and it would allocate to hand back a list whose contents were fixed
when the generator ran.

## What the naive version gets wrong

The obvious C++ generator returns `std::vector<Effect>`:

```cpp
// Don't. Allocates on every transition to return data fixed at codegen time.
struct RouteFsmTransitionResult {
  RouteFsmState newState;
  RouteFsmContext newContext;
  std::vector<RouteFsmEffect> effects;   // ← heap traffic per venue message
  bool isNoTransition;
};
```

It also costs `noexcept`, which the generated `transition()` currently carries: a vector can throw
`bad_alloc`, so the whole function stops being `noexcept` to return a list that was never dynamic.

The generated version emits one `inline constexpr` table per transition and returns a span over it:

```cpp
inline constexpr std::array<RouteFsmEffect, 2> kRouteFsmEffects2 = {{
  {RouteFsmEffectKind::PublishEventLog, {}, "RouteWorking", {}},
  {RouteFsmEffectKind::EmitEvent, "OrderFsm", "ValidationPassed", {}}}};
```

Rust reaches the same place with `&'static [RouteFsmEffect]`, and Java — where every `List.of(...)`
is already an immutable shared object — keeps `List<E>`.

## Where the three diverge, precisely

**Lifetime.** Rust's `&'static` is *checked*. A test can bind the effects to a `&'static` and let the
compiler prove they outlive the result:

```rust
let effects: &'static [RouteFsmEffect] = {
    let result = RouteFsmState::Sent.apply(RouteFsmEvent::RouteAcknowledged, &ctx(), None);
    result.effects
};
```

The C++ test asserting the same thing can only *observe* it. Nothing in the type says the span does
not dangle; a future emitter returning a span over a local would compile, and the failure would
surface under ASan at run time rather than at the keyboard.

**Sum type.** Rust and Java make an ill-formed effect unrepresentable — an `EmitEvent` carrying a
timer's arguments does not compile. C++ has a `kind` tag plus every field, so an `EmitEvent` still
*has* an empty `args` and a `PublishFixMessage` still has an empty `targetFsm`, and nothing stops a
caller reading them. `std::variant` could express it, at the cost of every read becoming a visit;
the tag was chosen because the only consumer switches on the kind anyway. See
[[unrepresentable-invalid-state]].

**Name collisions.** The C++ effect *kind* and *arg* types are prefixed per machine —
`RouteFsmEffectKind`, not `FsmEffectKind` — because a translation unit driving two machines includes
two generated headers into one namespace, and the unprefixed name would be a redefinition. Rust has
no such problem: each machine is its own module. This is not a safety property, just a reminder that
C++'s single namespace per header set is a constraint the generator has to design around.

## The property worth testing

**A no-transition carries no effects.** If a declined event still returned them, the slice would
cascade an event the machine explicitly refused, and the order machine would move on a route event
that never happened. Asserted in both
[`route_fsm_effects_test.rs`](../../rust/ems-fsm/tests/route_fsm_effects_test.rs) and
[`fsm_effects_test.cpp`](../../cpp/ems-fsm/test/fsm_effects_test.cpp) — including the separate
generator path where a *terminal* state has no rules at all, which is not the same code as "no rule
matched this event".

## Where it lives

`cpp/fsm/generated/route_fsm.hpp:146` — the `RouteFsmEffect` struct and the comment recording the
tag-versus-sum-type trade. Compare `rust/ems-fsm/src/generated/route_fsm.rs`
(`pub enum RouteFsmEffect`, all fields `&'static str`) and
`java/ems-fsm/src/main/generated/.../RouteFsmEffect.java` (a sealed interface of records).

## Cross-language contrast

| | Representation | Allocation per transition | Who proves the lifetime |
|---|---|---|---|
| **Java** | sealed interface of records, `List<E>` | none — `List.of` constants are shared | GC. The question does not arise. |
| **Rust** | `enum`, `&'static [Effect]` | none | **The compiler**, by lifetime. |
| **C++** | struct + `kind` tag, `std::span<const Effect>` | none | **Nobody.** Convention, checked by ASan at run time if it is ever broken. |

The row that matters is the last one. All three achieve the same zero-allocation result; only one of
them will tell you when a future change breaks it.

## Related

[[unrepresentable-invalid-state]], [[span-at-boundaries]], [[fsm-state-exhaustiveness]],
[[option-vs-nullable]]
