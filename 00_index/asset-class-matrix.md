# Asset Class Matrix

One row per sub-asset class. Venues column lists the **routable execution destinations** (not terminal monitor screens — those live in [[70_concepts/terminal_screens]]). See [[_venue-index]] for the venue categorization.

| Asset | Venues | RFQ | Netting |
|---|---|---|---|
| [[cash-equity\|Cash Equity]] | [[nyse]] · [[nasdaq]] · [[cboe-bzx]] · [[iex]] · [[memx]] · [[lse]] · [[xetra]] · [[euronext]] · [[cboe-europe]] · [[jpx-tse]] · [[hkex]] + [[_brokers-overview\|Brokers]] | [[bloomberg-rfqe]] (ETF block) · [[route-to-rfq]] | n/a |
| [[equity-derivatives\|Equity Derivatives]] | [[nasdaq]] options · [[cboe-bzx]] options · [[nyse]] options + [[_brokers-overview\|Brokers]] (capital commitment, dark pools) | [[route-to-rfq]] | [[netting-swap-net]] |
| [[equity-swaps\|Equity Swaps]] | Dealer-direct bilateral; ISDA-documented | [[route-to-rfq]] | [[netting-swap-net]] |
| [[govt-bonds\|Government Bonds]] | [[brokertec]] · [[tradeweb]] · [[mts]] · [[opendoor]] · [[treasury-direct]] (primary) · [[yieldbroker]] (AUD) | [[route-to-rfq]] | [[netting-swap-net]] |
| [[corp-bonds-ig\|Corporate Bonds — IG]] | [[marketaxess]] · [[tradeweb]] · [[trumid]] · [[bloomberg-bridge]] (EM/global) · [[neptune]] (axes) | [[route-to-rfq]] | [[netting-swap-net]] |
| [[corp-bonds-hy\|Corporate Bonds — HY]] | [[marketaxess]] (OT) · [[trumid]] (Swarms) · [[bloomberg-bridge]] · [[tradeweb]] · [[bloomberg-ib]] (chat) | [[route-to-rfq]] · [[bloomberg-bwic-owic]] | [[netting-swap-net]] |
| [[municipal-bonds\|Municipal Bonds]] | [[municenter]] · [[ice-bondpoint]] · [[marketaxess]] | [[route-to-rfq]] · [[bloomberg-bwic-owic]] | [[netting-swap-net]] |
| [[money-market-tbills\|MMkt — T-Bills]] | [[treasury-direct]] (primary) · [[brokertec]] · [[tradeweb]] | [[route-to-rfq]] | [[netting-swap-net]] |
| [[money-market-cp-cd\|MMkt — CP / CD]] | Dealer-direct · [[marketaxess]] (limited) · [[ice-bondpoint]] (CD) | [[route-to-rfq]] | [[netting-swap-net]] |
| [[money-market-repo\|MMkt — Repo]] | [[triparty-bnym-jpm]] · [[brokertec]] · [[mts]] (EU) | dealer-direct | [[netting-swap-net]] |
| [[whole-loans\|Whole Loans]] | Dealer-direct via [[bloomberg-ib]] (chat); BWICs via [[bloomberg-bwic-owic]] | list-RFQ | n/a |
| [[convertibles\|Convertibles]] | Dealer-direct · [[marketaxess]] (selected) | [[route-to-rfq]] | [[netting-swap-net]] |
| [[mbs\|MBS]] | [[bloomberg-tba]] (TBA) · [[tradeweb]] (D2C TBA + spec pools) · dealer-direct | [[route-to-rfq]] | [[netting-swap-net]] |
| [[abs\|ABS]] | Dealer-direct · [[marketaxess]] (selected) · BWICs via [[bloomberg-bwic-owic]] | list-RFQ | [[netting-swap-net]] |
| [[interest-rate-swaps\|IRS]] | [[bloomberg-sef]] · [[tradeweb]] · [[bloomberg-bmtf]] (EU) · [[sef-platforms]] | RFQ-to-3 | n/a (cleared) |
| [[credit-default-swaps\|CDS]] | [[bloomberg-sef]] · [[tradeweb]] · ICE Swap Trade · [[sef-platforms]] | RFQ-to-3 | n/a (cleared) |
| [[structured-products\|Structured Products]] | Dealer-direct · [[bloomberg-ib]] (chat) | bespoke | n/a |
| [[fx-spot\|FX Spot]] | [[ebs]] · [[hotspot-fx]] (CLOB) · [[refinitiv-fxall]] · [[360t]] · [[currenex]] · [[fxspotstream]] · [[fx-connect]] | [[route-to-rfq]] | [[netting-swap-net]] |
| [[fx-forward\|FX Forward]] | [[refinitiv-fxall]] · [[360t]] · [[currenex]] · [[fx-connect]] | [[route-to-rfq]] | [[netting-swap-net]] |
| [[fx-swap\|FX Swap]] | [[refinitiv-fxall]] · [[360t]] · [[currenex]] · [[fx-connect]] | [[route-to-rfq]] | [[netting-swap-net]] |
| [[fx-ndf\|FX NDF]] | [[bloomberg-sef]] · [[refinitiv-fxall]] · [[360t]] · [[sef-platforms]] | RFQ | [[netting-swap-net]] |
| [[fx-options\|FX Options]] | [[refinitiv-fxall]] · [[360t]] · dealer-direct via [[bloomberg-ib]] | [[route-to-rfq]] | [[netting-swap-net]] |
| [[commodity-futures\|Commodity Futures]] | CME / ICE / EUREX (futures exchanges — not in venue notes yet) | n/a | n/a (cleared) |
| [[commodity-physical\|Commodity Physical]] | Bilateral via [[bloomberg-ib]] (chat); broker-platforms | bespoke | n/a |

## Notes

- "n/a (cleared)" — netting is replaced by CCP novation; CCP handles position netting.
- "n/a" — pre-trade netting concept doesn't apply (single instrument is the trade).
- Reg-reporting / clearing / documentation columns deferred to per-asset notes — see each asset's individual file.
- Terminal monitor screens (ALLQ, BTMM, FIT, CDSW, SWPM, CBND, CP/CD, TBILL, REPO) are explicitly **not** in this matrix — see [[70_concepts/terminal_screens/]].

## Related

- [[_venue-index]] (venue categorization map)
- [[_brokers-overview]] (broker routing concept + dual-role disclaimer)
- [[architecture-index]] · [[workflow-index]]
