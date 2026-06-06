---
type: architecture
layer: auxiliary
status: draft
tags: [architecture/auxiliary]
---

# Borrow Service

Equity-specific service handling **stock borrow** for short-selling and other share-locating workflows. Tracks borrow availability, sources locates, returns borrowed shares, computes borrow costs, and feeds pre-trade [[arch-compliance|compliance]] short-sale eligibility checks.

US Reg SHO and similar regimes worldwide require demonstrating a "locate" before short-selling — proving you can borrow the shares. The borrow service is the EMS's accounting of locates and live borrow positions.

## Scope

| Function | Purpose |
|---|---|
| **Locate** | Pre-trade: confirm shares available to borrow at quoted rate. |
| **Borrow execution** | Trade: actually borrow the located shares. |
| **Return** | Post-trade: return borrowed shares when short position covered. |
| **Recall handling** | Lender may recall borrowed shares; manage forced cover. |
| **Cost tracking** | Borrow rate (typically per-day basis points) accumulated as financing cost. |
| **Hard-to-borrow flags** | Some securities are "HTB" → high rate, scarce supply, restricted. |

## Architecture

```mermaid
flowchart LR
  subgraph Inputs
    OS[Order Layer<br/>short-sell intent]
    LE[Lending sources<br/>PB feeds, agent lender, internal book]
    REGSHO[Reg SHO data<br/>threshold securities, etc.]
  end

  subgraph "Borrow Service"
    AVL[Availability registry<br/>per instrument from feeds]
    LOC[Locate engine]
    EXE[Borrow execution]
    POS[Borrow position tracker]
    REC[Recall handler]
    COST[Cost accrual]
  end

  subgraph Out
    CMP[Compliance check]
    POSS[[[arch-position-service]] short positions]
    OPS[Recall ops queue]
  end

  OS --> LOC
  LE --> AVL
  REGSHO --> AVL
  AVL --> LOC
  LOC --> EXE
  EXE --> POS
  POS --> POSS
  POS --> COST
  REC --> OPS
  POS --> CMP
```

## Pre-trade locate flow

```mermaid
sequenceDiagram
  participant T as Trader
  participant API as API
  participant C as Compliance
  participant B as Borrow Service
  participant L as Lending Source

  T->>API: stage_orders([{ side: SELL_SHORT, instrument, qty }])
  API->>C: pre_trade_check
  C->>B: locate_request(instrument, qty)
  B->>B: check internal book first
  alt internal sufficient
    B-->>C: LOCATED { source: internal, rate }
  else need external
    B->>L: request external locate
    L-->>B: response (available qty, rate)
    alt sufficient + acceptable rate
      B-->>C: LOCATED { source: external_PB, rate }
    else insufficient or rejected
      B-->>C: NOT_LOCATED
      C-->>API: BLOCK { reason: locate_failed,
                       override: required_tags=[#compliance-override-naked-short] }
    end
  end
  C-->>API: ALLOW (if located) / BLOCK
```

Locate result captured on the order envelope. Naked short selling (no locate) is blocked unless override; the override is heavily audited.

## Borrow execution

When a fill occurs on a short order:

- `BorrowExecuted` event: locate converts to actual borrow.
- Shares delivered (via PB or internal lender).
- Borrow position tracked: instrument, qty, lender, rate, since_date.

## Recall handling

Lenders may recall borrowed shares:

- `BorrowRecall` event from lender.
- Service tries to roll the borrow (find another lender for same shares).
- If unable, trader must cover (buy back the shares) within the recall window (typically T+3 for US equities).
- Forced cover events flagged in [[arch-notification-service]].

## Cost accrual

Borrow rate (annualized) accrues daily:

```
daily_cost = borrow_qty * mark_price * rate / 365
```

Accumulated cost feeds:

- P&L attribution per position.
- TCA: financing cost component.
- Trader-visible "carry" on short positions.

## Hard-to-borrow (HTB) handling

Securities flagged HTB:

- Locate rates dramatically higher (sometimes 100%+ annualized).
- Limited availability; first-come first-served.
- Some firms restrict HTB shorting to senior traders via `#htb-short-permitted`.
- Surveillance flags HTB activity for pattern review.

## Reg SHO interactions (US equity)

US Reg SHO compliance:

- **Threshold securities list**: instruments with persistent fail-to-deliver; close-out within 13 days.
- **Locate requirement**: documented locate before any short sale (Rule 203(b)).
- **Mark short / short-exempt**: orders marked correctly per FIX `OrderCapacity` and `Side`.
- **Affirmative determination**: documented basis for the locate.

The service produces a daily Reg SHO compliance attestation for ops.

## Data model

```
BorrowAvailability {
  instrument, source_lender, available_qty, indicative_rate, updated_at
}

LocateRecord {
  locate_id, requested_by, instrument, qty, rate, source_lender,
  located_at, expires_at, status: ACTIVE | CONSUMED | EXPIRED | CANCELLED
}

BorrowPosition {
  position_id, instrument, borrowed_qty, lender, rate, since,
  status: OPEN | RECALLED | RETURNED
}

BorrowCostAccrual {
  position_id, period, accrued_amount, mark_price_used
}
```

## Determinism / replay

Borrow decisions depend on availability snapshots at decision time. The service snapshots availability on each locate event for replay reproducibility.

## See also

- [[arch-compliance]] (locate check + naked-short override)
- [[arch-position-service]] (short positions integration)
- [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-reference-data-service]]
- [[arch-notification-service]] (recall alerts) · [[arch-tag-permissions]]
- [[cash-equity]] · [[allocation-prime-broker]]
