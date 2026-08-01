# component-09-allocation

**Covers:** allocation — an order's filled quantity distributed across accounts by weight, using the
largest-remainder method. The last component of the slice: after this, a fill has an owner.

**Why it exists:** allocation is pure integer arithmetic, which makes it the easiest component to
get *silently* wrong in three languages. There is no FSM to generate, no schema to share — just
division, remainders and tie-breaks, written three times by hand. A one-line difference in a
tie-break produces journals that agree on every case except the one where two remainders happen to
be equal. That is exactly the shape of bug the byte-exact gate exists for, so the corpus hits every
branch of the arithmetic on purpose.

## The method

Each account gets the **floor** of `filled × weight / totalWeight`. Floors under-allocate — the lots
lost go one each to the accounts with the **largest division remainders**, ties broken by **larger
weight**, then **instruction order**. The parts then sum exactly to the whole, always.

Every rule in that sentence is load-bearing. An unstated tie-break is a divergence waiting for the
first case that hits it, which is why cases 2 and 3 below exist.

## The six allocations

| # | Filled | Shares | Result | Pins |
|---|---|---|---|---|
| 1 | 1000 | `ACC1:5000,ACC2:5000` | 500 / 500 | the trivial case |
| 2 | 100 | `ACC1:3333,ACC2:3333,ACC3:3334` | 33 / 33 / **34** | floors lose a lot; the largest remainder absorbs it |
| 3 | 5 | `ACC1:5000,ACC2:5000` | **3** / 2 | remainders tie *and* weights tie — instruction order decides, and ACC1 gets the odd lot |
| 4 | 900 | `ALPHA:1,BETA:2` | 300 / 600 | weights are relative, not a fixed 10000 |
| 5 | 250 of 1000 | `ACC1:5000,ACC2:5000` | 125 / 125 | a partial fill allocates `cumQty`, not order quantity |
| 6 | — | — | — | see negatives |

**Case 5 is the integration point.** `cumQty` is maintained by the order FSM's own `update_context`
effects on fills — the allocation reads what the machine says was executed, not a number re-derived
from the fill events by a second code path. The fill itself arrived as an `ExecutionReport` through
the component-8 edge, so this one case exercises the whole chain: FIX in → route FSM → cascade →
order FSM context → allocation.

## Three negatives

| Input | Code |
|---|---|
| an order that does not exist | `EMS-ALC-6001` |
| an order acknowledged but never filled | `EMS-ALC-6002` — there is nothing to allocate |
| empty shares, and `garbage,also:notanumber,:5000` | `EMS-ALC-6003` — malformed entries are dropped, and a list with nothing left is a refusal |

A refused allocation records nothing against any account. There is no partial application: either
every account gets its record or none does.

**Seed:** 0.

**Scope, stated plainly:** allocation to accounts by weight only. No allocation templates, no prime
broker give-ups, no drop-copy — ADR 0007 records those as out of the slice. This is the last slice
component; what remains is out of scope by decision (ADR 0002), not by omission.
