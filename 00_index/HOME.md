# Cross-Asset EMS Knowledge Base

Atomic-notes Obsidian vault covering execution workflows across every asset class an EMS must support.

**New here?** → [[USAGE]] — install, navigate, author, maintain.

## Reference
- [[USAGE]] — how to use this vault (runbook)
- [[architecture-index]] — system architecture (API, FIX bridge, SBE/Aeron, OMS layers, validator, perms)
- [[asset-class-matrix]] — side-by-side comparison of all asset classes
- [[workflow-index]] — every workflow grouped by category

## Asset Classes

### Equity
- [[cash-equity|Cash Equity]]
- [[equity-options|Equity Options]]
- [[equity-futures|Equity Futures (Index + SSF)]]
- [[equity-swaps|Equity Swaps]]
- [[equity-derivatives|Equity Derivatives (overview)]]

### Fixed Income
- [[govt-bonds|Government Bonds]]
- [[corp-bonds-ig|Corporate Bonds — Investment Grade]]
- [[corp-bonds-hy|Corporate Bonds — High Yield]]
- [[municipal-bonds|Municipal Bonds]]
- [[money-market-tbills|Money Market — Treasury Bills]]
- [[money-market-cp-cd|Money Market — CP / CD]]
- [[money-market-repo|Money Market — Repo / Reverse Repo]]
- [[whole-loans|Whole Loans / Mortgages]]
- [[convertibles|Convertible Bonds]]
- [[mbs|Mortgage-Backed Securities (MBS)]]
- [[abs|Asset-Backed Securities (ABS)]]

### Rates / Credit Derivatives
- [[interest-rate-swaps|Interest Rate Swaps (IRS)]]
- [[credit-default-swaps|Credit Default Swaps (CDS)]]
- [[structured-products|Structured Products]]

### FX
- [[fx-spot|FX Spot]]
- [[fx-forward|FX Forward]]
- [[fx-swap|FX Swap]]
- [[fx-ndf|FX NDF]]
- [[fx-options|FX Options]]
- [[fx-futures|FX Futures (CME, EUREX, ICE)]]

### Crypto / Digital Assets
- [[crypto-spot|Crypto Spot]]
- [[crypto-perpetual|Crypto Perpetuals (Perp Swaps)]]
- [[crypto-futures|Crypto Futures (CME, Deribit, etc.)]]
- [[crypto-options|Crypto Options (Deribit, CME, DeFi)]]

### Commodity
- [[commodity-futures|Commodity Futures]]
- [[commodity-physical|Commodity Physical]]

### CFD (Contract For Difference)
- [[cfd-overview|CFD Overview — Equity / Index / FX / Commodity / Crypto / Bond CFDs]]

### Event Contracts / Prediction Markets
- [[prediction-markets|Prediction Markets — Polymarket / Kalshi / CME event contracts]]

## Common Reference Domains
- Venues — see `30_venues/`
- Regulatory — see `40_regulatory/`
- Clearing & Settlement — see `50_clearing_settlement/`
- Documentation — see `60_documentation/`
- Concepts / Glossary — see `70_concepts/`
- **System Architecture** — see `80_architecture/` and [[architecture-index]]
- FSM definitions — see `fsm/` (per [[arch-fix-fsm-design]])
- Evaluations (local-LLM trial outputs etc.) — see `fsm/EVALUATION-*` and `evaluation/`
