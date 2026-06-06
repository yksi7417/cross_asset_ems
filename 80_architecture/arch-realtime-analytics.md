---
type: architecture
layer: analytics
status: draft
tags: [architecture/analytics]
---

# Real-time Analytics

A streaming **benchmark price service** that computes VWAP, PWP, TWAP, arrival price, NBBO, and other reference prices continuously from market data and own trades. Subscribers consume benchmarks via the same PubSub fabric as [[arch-quote-server|quotes]] but on dedicated benchmark topics.

## Purpose

Many components need a benchmark price at any moment:

- [[arch-compliance|Compliance]]'s fat-finger check needs a reference price.
- [[arch-smart-order-router|SOR]] strategies and [[arch-automation-layer|automation rules]] reference benchmarks for slicing and timing.
- [[arch-tca|TCA]] needs benchmarks for slippage decomposition.
- The blotter shows benchmarks alongside fills for trader feedback.

Computing them in N places is wrong (drift, inconsistency, cost). Computing them once in a dedicated service and broadcasting is right.

## Benchmark catalogue

| Benchmark | Definition | Key consumers |
|---|---|---|
| **VWAP** | Volume-Weighted Average Price over [start, now] | TCA, algo strategies |
| **TWAP** | Time-Weighted Average Price over [start, now] | algo schedulers |
| **PWP** (Participation-Weighted Price) | What an algo running at X% would have achieved | best-ex analysis, [[arch-tca]] |
| **Arrival Price** | Mid (or NBBO) at order-arrival timestamp | TCA, IS-style algos |
| **NBBO** | National Best Bid/Offer (US equity) or equivalent | Reg-NMS compliance ([[arch-smart-order-router]]), validators |
| **EBBO** | European Best Bid/Offer | MiFID II SI / pre-trade transparency |
| **Mid** | (Bid + Offer) / 2 from primary venue | quick reference |
| **Last** | Last reported trade | TCA, fallback price |
| **Reference VWAP (period)** | Daily / weekly / monthly closing VWAP | research, mandates |
| **iNAV** (intraday net asset value) | Real-time ETF NAV approximation | [[arch-rfq|ETF RFQ]] benchmark |
| **Close** | Official closing print | mark-to-market, [[arch-position-service]] |

## Architecture

```mermaid
flowchart LR
  subgraph Inputs
    MD[Market Data Feeds<br/>via arch-quote-server]
    OF[Own Fills<br/>from event log]
  end

  subgraph "Real-time Analytics Service"
    CALC[Benchmark Calculators<br/>per benchmark kind]
    STORE[Time-bucketed store<br/>(rolling windows)]
    PUB[Publisher]
  end

  subgraph Outputs
    BUS[SBE/Aeron topics<br/>analytics.benchmark.{kind}.{figi}.{window}]
    SNAP[Snapshot API]
  end

  MD --> CALC
  OF --> CALC
  CALC --> STORE
  STORE --> PUB
  PUB --> BUS
  STORE --> SNAP
```

## Topic scheme

Topics follow the [[arch-quote-server|quote-topic]] convention:

```
analytics.vwap.{figi}.{window}              # window: today | 5m | 30m | session
analytics.twap.{figi}.{window}
analytics.pwp.{figi}.{participation_rate}   # e.g. 0.10 for 10%
analytics.arrival.{order_id}                # arrival reference per order
analytics.nbbo.{figi}                       # depth-1 cross-venue NBBO
analytics.mid.{figi}.{venue}
analytics.inav.{etf_figi}
```

Subscribers receive incremental updates; the service maintains state per topic so newcomers get a snapshot followed by the tail.

## Calculation properties

- **Pure projection from market data + own fills.** [[arch-time-replay-server|Replay]] re-derives identical benchmarks given the same input.
- **Bounded compute.** Each benchmark has a fixed-cost incremental update; windows are circular buffers with monotonic time advance via the clock interface.
- **Single source of truth.** If a consumer needs VWAP, it subscribes — no consumer maintains its own VWAP calculator.

### PWP specifics

PWP estimates the price an algo running at a specific participation rate would have achieved over a window. It's a counterfactual:

```
PWP(rate, window) = sum(market_volume_i * price_i)
                  / sum(market_volume_i)
                  filtered to volume sample where own_share <= rate
```

The exact formula varies (Almgren-Chriss-style adjustments, dark-pool inclusion choices). The implementation captures **the formula version** on the published benchmark so [[arch-tca|TCA]] knows which PWP model it's comparing against.

## Subscribers

| Subscriber | Topic | Why |
|---|---|---|
| [[arch-compliance]] | mid / last / NBBO per FIGI | fat-finger reference |
| [[arch-smart-order-router]] | NBBO, mid, VWAP | strategy decisions |
| [[arch-automation-layer]] | arrival, mid, VWAP | rule conditions |
| [[arch-tca]] | every benchmark | post-trade decomposition |
| [[arch-quote-server]] integration | — | not a subscriber; an upstream input |
| Blotter UI | per-order benchmarks | trader feedback |

## Determinism / replay

In [[arch-time-replay-server|replay mode]], the benchmark service replays the same market data feed and own-fill stream and produces byte-identical benchmark publications. This makes TCA replayable for any historical session against any benchmark version.

## Versioning

Each benchmark calculator has a `version`. Published benchmark events include the version. A formula change (e.g. a new PWP variant) is a new version, not an in-place update — old subscribers continue to use the old version until they switch.

## See also

- [[arch-quote-server]] · [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-sbe-aeron-transport]]
- [[arch-tca]] · [[arch-pretrade-analytics]] · [[arch-smart-order-router]] · [[arch-compliance]]
- [[arch-symbology-figi]] · [[arch-rfq]]
