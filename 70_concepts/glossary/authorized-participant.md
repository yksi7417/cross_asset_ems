---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Authorized Participant (AP)

An **Authorized Participant** is a designated broker-dealer authorised to **create and redeem ETF shares with the ETF issuer**. APs are the arbitrage link between an ETF's market price and its underlying net asset value (NAV) — when the ETF trades rich, APs redeem shares for the underlying basket; when it trades cheap, APs create shares by delivering the basket.

Creation/redemption happens in **creation units** (typically 25,000-100,000 ETF shares) directly with the ETF issuer at end-of-day NAV. The AP delivers (or receives) the basket of underlying securities per the published creation basket. This is the **primary market** for ETFs; secondary market is the lit exchange tape.

For large institutional ETF blocks, the buy-side trader often works directly with an AP — the AP can create shares to deliver, source the underlying basket more efficiently than the buy-side could, and charge a small spread. This is often more efficient than working a block through the secondary lit market.

## Example

A buy-side fund needs to buy 500K shares of SPY (~$200M). Working the secondary market would take hours and leak signal. Instead, the fund engages Goldman (an SPY AP) for a "create": Goldman buys the S&P 500 basket on the buy-side's behalf, delivers it to State Street (SPY issuer) in exchange for 500K SPY shares, and delivers them to the fund. Total transaction: 1 day, no secondary-market impact.

## Why it matters in an EMS

- ETF RFQ workflows ([[bloomberg-rfqe]]) often involve AP participation.
- The EMS must support primary (AP creation/redemption) vs secondary (exchange) ETF execution.
- TCA benchmarks differ (NAV-based for primary vs NBBO for secondary).

## Related

- [[inav]] · [[cash-equity]] · [[bloomberg-rfqe]]
- [[arch-rfq]] (AP-workflow specifics) · [[arch-best-execution]]
- [[goldman-sachs]] · [[morgan-stanley]] · [[jpmorgan]] (major APs)
