---
type: idiom
status: draft
language: cpp
anchor: cpp/ems-transport/include/ems_transport/transport.hpp:23
tags: [concept/idioms, lang/cpp, theme/lifetime]
---

# Polymorphic base classes: virtual destructor, and the rule of five spelled out

## The idiom

A class meant to be inherited from and deleted through a base pointer needs a `virtual` destructor.
Declaring *any* destructor suppresses the implicitly-generated move operations, so once you declare
one you are on the hook for all five special members — the "rule of five". For an interface the
usual answer is: virtual defaulted destructor, copy and move explicitly deleted.

## Why it was needed here

`Transport` is the ADR 0006 seam. The slice holds a `Transport` and must not know whether it is a
`JournalTransport` writing files or an Aeron-backed one talking to a media driver. That is the
whole point of the abstraction — and it is exactly the situation where a missing virtual destructor
is undefined behaviour rather than a style complaint.

Once the destructor is declared, the compiler stops generating move construction and move
assignment. Leaving that implicit would mean a `Transport&&` silently binding to a copy — and a
copied `JournalTransport` would hold the same output path with a *separate* pending buffer, so
whichever copy flushed last would silently overwrite the other's events. Deleting copy and move
makes that a compile error instead.

## What the naive version gets wrong

```cpp
// Don't.
class Transport {
public:
    virtual std::vector<JournalEvent> drain() = 0;
    virtual void publish(JournalEvent) = 0;
    // no destructor declared
};

std::unique_ptr<Transport> t = std::make_unique<JournalTransport>(in, out);
// ~JournalTransport never runs: pending_ is never freed, and any flush-on-destroy
// never happens. Undefined behaviour, and not one a test reliably catches.
```

`-Wnon-virtual-dtor` — in `cpp/CMakeLists.txt` since the gate skeleton — turns exactly this into a
build error, which is why it is in the warning set.

The second naive version declares the destructor and stops:

```cpp
virtual ~Transport() = default;   // and nothing else
```

Now copy construction is still generated. `JournalTransport a{in, out}; auto b = a;` compiles, and
two objects own the same output file with independent buffers. Nothing warns.

The third mistake goes the other way: `= default` on all five instead of `= delete`. That produces
a *sliceable* base — copying a `Transport&` copies only the base subobject, discarding the derived
state. Deleting is what says "this type is only ever used through a reference or a pointer".

## Where it lives

`cpp/ems-transport/include/ems_transport/transport.hpp:23` — the five declarations, in one block,
above the pure virtual interface.

## Cross-language contrast

| | Mechanism | Failure mode if you get it wrong |
|---|---|---|
| **Java** | `interface Transport extends AutoCloseable` | None available. There is no destructor to forget and no copy constructor to generate. The equivalent hazard — forgetting to close — is why the interface extends `AutoCloseable`: try-with-resources makes the correct thing the short thing. |
| **C++** | Virtual defaulted destructor, copy and move deleted | Undefined behaviour (missing destructor call), or two owners of one output file (implicit copy). Neither reliably crashes. |
| **Rust** | `trait Transport`, implemented by `JournalTransport` | None available. A trait object is dropped correctly through `Box<dyn Transport>` because `Drop` is dispatched by the vtable, and `JournalTransport` is not `Copy` or `Clone` unless it opts in. |

The interesting asymmetry: Java and Rust have nothing to write here at all, and C++ needs five
declarations to reach the same place. That is not C++ being verbose for its own sake — it is the
cost of the object model that also lets `JournalTransport` live on the stack with no allocation and
no indirection, which is what `ems-slice` actually does.

## Related

[[expected-without-exceptions]], [[span-at-boundaries]]
