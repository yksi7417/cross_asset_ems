---
type: architecture
layer: market_data
status: draft
tags: [architecture/market_data]
---

# Quote Server

The quote server consolidates outbound market data distribution into a single **publish/subscribe service**. Subscribers may be UIs, automation rules, the validator (for limit checks against last), and venue adapters (for routing decisions).

## Goals

- **One subscriber registry.** The server always knows who is on what topic, even when the transport is multicast.
- **Single source-of-truth fan-out.** Internal consumers do not connect directly to venue feeds; they consume from the server's topics.
- **Replayable.** Quote events are tailed into the [[arch-event-sourcing|event log]] (sampled) so historical replays can reproduce decisions.

## Topic scheme

Topics are keyed by FIGI plus a quote-level qualifier:

```
quote.{figi}.l1           // top of book
quote.{figi}.l2           // depth, N levels
quote.{figi}.trade        // last trade prints
quote.{figi}.rfq          // live RFQ responses, see [[arch-router-layer]]
quote.{composite_figi}.l1 // cross-venue composite, see [[arch-symbology-figi]]
```

A subscription includes:
- `topic` (or glob, e.g. `quote.BBG000B9XRY4.*`)
- `qos` — best-effort (multicast tail) or guaranteed (unicast replay)
- `throttle` — max msgs/sec downsampling

## Transport

- **Multicast tail** for hot, high-fanout topics — see [[arch-sbe-aeron-transport]].
- **Unicast replay** for catch-up after subscriber gap.
- The server maintains a **subscription registry** that records subscribers even when delivery is multicast (so it can answer "who is on this topic?" deterministically). Subscribers periodically heartbeat; missing heartbeats prune the registry.

## API operations

```
subscribe([ Subscription ])
unsubscribe([ subscription_id ])
list_subscriptions(filter)            // admin
snapshot([ topic ])                   // request latest state, useful at logon
```

## Subscription lifecycle events (logged)

- `Subscribed { subscription_id, topic, subscriber, qos }`
- `Snapshotted { subscription_id, sequence }`
- `Throttled { subscription_id, reason }`
- `Unsubscribed { subscription_id, reason }`

These appear in [[arch-event-sourcing]] under the `quote.{topic}` stream.

## Validator integration

[[arch-validator]] can check limit-vs-last sanity (e.g. reject a buy limit 30% through the market) by reading `quote.{figi}.l1` from the server. The validator never reads from a raw venue feed.

## Quote-driven automation

Many rules are `QuoteIncrement`-triggered — see [[arch-automation-layer]] examples. The rule's `actor` for permission evaluation is the rule binder; the quote event is the trigger.

## Permissioning

A subscriber must have the right [[arch-tag-permissions|tags]] to subscribe to a feed — e.g. `#lvl2-equity-na` for L2 North American equities. Denial messages explain which admin grants the tag.

## What's out of scope

- The act of pricing OTC instruments. That is a separate pricing service consumed by the quote server.
- Historical bar / time-series storage. The replay path uses the event log, not a TSDB.

## See also

- [[arch-sbe-aeron-transport]]
- [[arch-event-sourcing]]
- [[arch-validator]]
- [[arch-automation-layer]]
- [[arch-symbology-figi]]
- [[arch-tag-permissions]]
