---
type: asset_class
asset_class: fixed_income
sub_class: money-market-repo
trade_type: financing_txn
liquidity: very_high
status: draft
tags: [asset/fixed_income/money-market-repo]
---

# Money Market — Repo / Reverse Repo

Repo = sale of a security with an agreement to repurchase. Economically a **collateralized loan**. The cash lender (reverse repo) earns interest; the cash borrower (repo) gets funding while pledging the security as collateral. The dominant short-end funding mechanism for fixed-income dealers, hedge funds, and money-market funds.

## Venues

- **Interdealer GCF**: [[brokertec]] GCF, Dealerweb GCF — FICC-cleared.
- **Tri-party**: [[triparty-bnym-jpm]] (BNY Mellon, JPM as agents).
- **Bilateral**: dealer-direct via FIX / chat.
- **EU repo**: [[mts]] Repo, [[brokertec]] EU, Eurex Repo.
- **Reference**: [[bloomberg-repo]] (monitor screen).

## How to Access Market

Buy-side EMS connects via FIX for bilateral term repo. Tri-party trades via the tri-party agent's API + bilateral price negotiation. See [[tri-party-vs-bilateral-repo]] for the operational split, [[gcf-repo]] for the cleared interdealer model.

## RFQ vs CLOB

Mix: [[clob-vs-rfq|CLOB]] for GCF repo on BrokerTec; [[rfq]] for term repo and specials. Bilateral by chat for very specific collateral.

## Aggregations / Basket / Netting

Repo positions [[netting|net]] at the dealer level (one set of GC trades, multiple specifics). FICC GCF clearing provides multilateral interdealer netting.

## Regulatory Reporting

US: TRACE doesn't cover repo. Federal Reserve Form OFR-100 for primary dealers; SEC Form PF for hedge funds. EU/UK: [[emir-sftr-csdr|SFTR]] within T+1 for every leg.

## Clearing / Settlement

T+0 or T+1 typically. **Tri-party**: agent ([[triparty-bnym-jpm]]) handles collateral movement and marks. **GCF**: FICC clearing — see [[ficc-clearing]]. **Bilateral**: settle via [[dtc]] / Fedwire.

## Documentation Required

[[gmra]] (Global Master Repurchase Agreement) for institutional bilateral repo. Tri-party master agreement with the agent.

## Market Notes

- **Fungibility**: The repo trade itself is **bilateral and non-fungible** (each contract has specific economics). The **underlying collateral** is fungible for GC (any acceptable UST/Agency) or non-fungible for specials (a specific CUSIP). See [[fungible-vs-non-fungible]].
- **GC vs special** — GC means any acceptable collateral; "special" means a specific CUSIP is in demand and trades richer.
- **Repo specials** signal short interest in the underlying — useful information for the cash market.
- **Reverse repo with the Fed** (overnight RRP) provides a floor for short rates.
- **Year-end / quarter-end** turn — dealer balance-sheet pressure causes repo rates to spike around month-end and quarter-end.
- **September 2019 repo spike** is a textbook example of system-wide funding stress.
- **Standing Repo Facility (SRF)** — Fed standing facility for primary dealers, addresses repo-spike concerns post-2019.

## Typical Counterparties

Money-market funds, hedge funds, corporate treasuries (lenders); primary dealers (borrowers). [[triparty-bnym-jpm|BNY Mellon and JPM]] as tri-party agents.

## Related Workflows

[[staging-via-fix]] · [[allocation-prime-broker]] · related to [[arch-borrow-service]] for sec-lending.
