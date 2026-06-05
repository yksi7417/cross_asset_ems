# cross_asset_ems

Obsidian-compatible markdown vault documenting workflows across the asset
classes an Execution Management System (EMS) must support: cash equities,
equity derivatives, equity swaps, fixed income (Govt, IG/HY Corp, Muni,
MMkt, Repo, Whole Loans, Convertibles, MBS, ABS), interest-rate &
credit derivatives (IRS, CDS), FX (Spot, Forward, Swap, NDF, Options)
with the full FX workflow taxonomy, structured products, and commodities.

## How to open

Install Obsidian via Flatpak (Fedora):

```bash
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

Then **Open folder as vault** → point at this directory.

## Layout

```
00_index/                 HOME MOC, asset-class matrix, workflow index
10_asset_classes/         One note per sub-asset class (atomic)
20_workflows/             Common / Staging / Pre-Trade / Routing /
                          Corporate Treasury / Others
30_venues/                One note per venue (Bloomberg screens, MTFs,
                          SEFs, interdealer, triparty)
40_regulatory/            TRACE, MSRB, CFTC SDR, FINRA, etc.
50_clearing_settlement/   DTC, Fedwire, Euroclear, LCH, FICC, ...
60_documentation/         ISDA, CSA, GMRA, DVP, ...
70_concepts/              Glossary atoms — RFQ, ALLQ, OTR vs OFR, ...
templates/                Reusable note skeletons
attachments/              Diagrams, screenshots
```

## Conventions

- **Atomic notes** — one concept per file.
- **Wikilinks** — `[[slug]]`, resolved by basename (file names are unique vault-wide).
- **Frontmatter** — every note has `type`, `status`, `tags`. Use it to query
  via Dataview once you install the plugin.
- **Status workflow** — `stub` → `draft` → `reviewed`. Promote as you fill notes in.
- **Tags** — hierarchical: `asset/fixed_income/corp_ig`, `workflow/routing`,
  `venue/dealer_platform`. Never flat.

## Adding a new note

1. Copy the relevant file from `templates/` into the right folder.
2. Fill the frontmatter and the `# Title`.
3. Wikilink related notes from the matching domain folders.
4. Promote status when content matures.

## Out of scope (for now)

- Per-note content population (skeleton + stubs only).
- Mermaid sequence diagrams (add per workflow as it solidifies).
- Community plugins (Templater, Dataview) — defer until query needs surface.
- Publishing (Quartz / MkDocs) — structure already supports it.
