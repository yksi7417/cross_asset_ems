# Using This Vault

A how-to for working with the cross-asset EMS knowledge base. Treat this as the runbook: install → open → navigate → author → maintain.

> This file lives **inside the vault** so you can read it in Obsidian itself. From the file explorer: `00_index/USAGE.md`. From the Quick Switcher: <kbd>Ctrl+O</kbd> → type `usage`.

---

## 1. Install & open

### Fedora (Flatpak — recommended)

```bash
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

### Open this folder as a vault

1. Launch Obsidian.
2. Click **"Open folder as vault"** in the start screen.
3. Choose `/home/yksi7417/dvlp/cross_asset_ems`.
4. Obsidian will pick up the committed `.obsidian/` settings (wikilinks on, attachments folder set, core plugins enabled).

### Optional: pin the launcher

If you want Obsidian on your Activities overview:

```bash
flatpak --user run md.obsidian.Obsidian &
# Right-click the icon in Activities → "Pin to Dash"
```

---

## 2. Vault tour — where things live

| Folder | What's in it | When to add a note here |
|---|---|---|
| `00_index/` | Home page, matrix, workflow index, this usage doc | Almost never — these are the navigational scaffolding. |
| `10_asset_classes/` | One note per **sub-asset class** (e.g. `corp-bonds-ig`, `fx-spot`). Folder per family. | Adding a new sub-asset class. |
| `20_workflows/` | One note per **workflow topic**. Subfolders: `common/`, `staging/`, `pre_trade/`, `routing/`, `corporate_treasury/`, `others/`. | New workflow you discover or new variant. |
| `30_venues/` | One note per **venue or screen** (Bloomberg FIT, Tradeweb, BrokerTec, SEFs, etc.). | New trading venue, dealer platform, or Bloomberg screen worth referencing. |
| `40_regulatory/` | TRACE, MSRB, CFTC SDR, FINRA, FICC reporting, Fed reporting, etc. | New regulator/regime touchpoint. |
| `50_clearing_settlement/` | DTC, Fedwire, Euroclear, Clearstream, LCH, FICC clearing, triparty agents. | New clearer or settlement system. |
| `60_documentation/` | ISDA, CSA, GMRA, DVP, SIFMA TBA, PSA, loan agreements. | New legal/documentation artefact relevant to trading. |
| `70_concepts/` | Glossary atoms — RFQ, ALLQ, OTR vs OFR, TBA vs specified pool, CLOB vs RFQ. | A concept you keep wanting to link to from several other notes. |
| `80_architecture/` | System architecture spine — API-first, FIX bridge, SBE/Aeron, OMS layers, validator, permissions, time/replay, venue connectivity. See [[architecture-index]]. | When you add or amend a cross-cutting system concept that workflows need to cite. |
| `templates/` | Skeletons for every note type. | Don't put real notes here. |
| `attachments/` | Diagrams, screenshots, PDFs. | Anything binary that a note references. |

---

## 3. Find your way around

### Start at HOME

Open [[HOME]] (Quick Switcher: <kbd>Ctrl+O</kbd> → `home`). It's the **Map of Content** — every domain entry point is one click away.

### The asset-class matrix

[[asset-class-matrix]] is a wide table comparing every sub-asset class on the same axes (venues, RFQ, netting, reporting, clearing, doc, trade type, liquidity). Cells contain wikilinks where helpful — click through to drill in.

### The workflow index

[[workflow-index]] groups every workflow under the FX taxonomy headings (Staging / Pre-Trade / Routing / Corporate Treasury / Others). Use this when you want to find "where's the note on partial routes?" rather than browsing the folder.

### The architecture index

[[architecture-index]] lists the system-level architecture notes. **Every expanded workflow links back here** — when reading a workflow, follow the architecture wikilinks to understand the surrounding mechanics (event sourcing, validator codes, FIX-API bridge rules, tag permissions, etc.).

### Navigation hotkeys

| Action | Hotkey |
|---|---|
| Quick Switcher (open any note by name) | <kbd>Ctrl+O</kbd> |
| Global search | <kbd>Ctrl+Shift+F</kbd> |
| Backlinks panel | <kbd>Ctrl+Shift+B</kbd> |
| Graph view (whole vault graph) | <kbd>Ctrl+G</kbd> |
| Local graph (this note's neighbours only) | <kbd>Ctrl+Shift+G</kbd> |
| Outline of current note | <kbd>Ctrl+Shift+O</kbd> |
| Reveal current file in explorer | <kbd>Ctrl+Shift+E</kbd> |

### Three navigation patterns to learn

1. **Top-down**: HOME → matrix → click into asset class → click into linked venue/workflow.
2. **Bottom-up**: any note → Backlinks panel → see everywhere it's referenced. Find a workflow note, see which asset classes depend on it.
3. **Spatial**: open the graph (<kbd>Ctrl+G</kbd>). Clusters show your knowledge structure. Isolated nodes are notes with no inbound links — usually means a stub nobody references yet.

---

## 4. Authoring — add a new note

### Step-by-step

1. **Pick the template** from `templates/`:
   - New asset class? `asset-class-template.md`
   - New workflow topic? `workflow-template.md`
   - New venue / screen? `venue-template.md`
   - New regulator / clearer / doc / concept? Use the matching template.
2. **Copy** the template into the right destination folder. Give it a kebab-case filename — that filename **becomes the wikilink target** anywhere in the vault.
3. **Fill the frontmatter**:
   - `type` matches the template (don't change).
   - `status: stub` initially.
   - `tags: [domain/sub]` — hierarchical, never flat.
4. **Write the body**. Section headings come from the template; leave them in place so cross-asset comparison stays mechanical.
5. **Link liberally** — `[[other-note]]`. A wikilink to a non-existent note is fine; it shows up red and marks something worth writing.

### Filename rules

- **Vault-wide unique basenames.** Obsidian resolves `[[foo]]` by basename alone. If two files are both called `fit.md`, links break. Prefix when needed: `bloomberg-fit.md`, not `fit.md`.
- **Kebab-case**, lowercase: `staging-via-fix.md`, not `Staging Via FIX.md`.
- **No version suffixes** (`-v2`, `-new`) — rewrite in place, git keeps history.

### Link, don't duplicate

If a fact lives in another note, **link to it** — don't restate. Example: in `corp-bonds-ig.md` under Clearing, write `Cleared via [[dtc]] (T+1)` rather than re-explaining DTC. The single source of truth for DTC is its own note.

---

## 5. Conventions

### Atomic notes

One concept per file. Don't bundle. `route-to-algo.md` and `route-to-rfq.md` are separate notes even though they're both routing — they have independent backlink graphs, independent edit histories, and independent maturity.

### Frontmatter is the source of truth

The body of a note is for humans. The frontmatter is for queries. Keep these fields accurate:

```yaml
---
type: asset_class      # or workflow, venue, regulatory, clearing_settlement, documentation, concept
status: stub           # stub → draft → reviewed
tags: [asset/fixed_income/corp_ig]
---
```

When you install Dataview later you can query e.g. "show me every `status: stub` note tagged `workflow/routing`" and get a live to-do list.

### Status workflow

| Status | Meaning | When to promote |
|---|---|---|
| `stub` | Skeleton with TODOs | Body has real content (not TODOs) for at least 3 sections |
| `draft` | Substantive content, not yet peer-reviewed | A second person has read it and you've addressed feedback |
| `reviewed` | Trusted, current as of frontmatter date | Use this for canonical reference notes |

### Expanded-workflow pattern

When promoting a workflow stub to `draft`, include these sections beyond the template default:

- **API mapping** — which `operation` and `items[]` shape this workflow uses, per [[arch-api-first]].
- **Validator codes touched** — list `EMS-XXX-NNNN` codes the workflow can trigger (see [[arch-validator]]).
- **Permissions** — which tags are evaluated under [[arch-tag-permissions]].
- **Architecture links** — every "Related" section should wikilink at least 2–3 `arch-*` notes.

The five workflows already at `draft` ([[staging-via-fix]], [[two-step-approval]], [[route-to-rfq]], [[route-to-algo]], [[netting-auto-via-excel]]) are reference examples for this pattern.

### Tag scheme (hierarchical)

| Domain | Pattern | Example |
|---|---|---|
| Asset class | `asset/<family>/<sub>` | `#asset/fixed_income/corp_ig` |
| Workflow | `workflow/<category>` | `#workflow/routing` |
| Venue | `venue/<kind>` | `#venue/dealer_platform` |
| Regulatory | `regulatory` | `#regulatory` |
| Clearing | `clearing/<kind>` | `#clearing/ccp` |
| Documentation | `documentation` | `#documentation` |
| Concept | `concept` | `#concept` |

Flat tags (`#corp-bonds`, `#fx`) clutter the tag pane and don't compose. Don't use them.

---

## 6. Working in Obsidian — day-to-day

### Adding a wikilink

Type `[[`. Obsidian shows a fuzzy-search dropdown of every note in the vault. Hit Enter on the match. Use `|` to set link text: `[[corp-bonds-ig|IG Corp]]` renders as "IG Corp" but still links to the file.

### Embedding a note inside another

`![[note-name]]` embeds the entire content (live, not a copy). Useful for embedding shared callouts or images.

### Inserting a template

With the **Templates** core plugin enabled (it is): <kbd>Ctrl+P</kbd> → "Insert template" → choose. Inserts at cursor.

### Properties panel

Frontmatter renders as a structured **Properties** panel at the top of every note. Edit frontmatter visually there — type changes propagate to the YAML on save.

### Graph view tips

<kbd>Ctrl+G</kbd> opens the global graph. Filter controls (top-right):

- **Filter** by `tag:#workflow/routing` to see only routing workflows.
- **Colour groups**: add a group `tag:#asset` → all asset notes one colour.
- **Forces**: lower "Link force" if clusters overlap.

---

## 7. Promoting a note (stub → draft)

A focused 4-step loop you can run for any note:

1. **Open the note.** Skim the section headings.
2. **Replace TODOs** with content in 2–3 sections that you can write from memory or reference.
3. **Wikilink** to every related note in the vault. Open backlinks panel — does this note now show up where it should?
4. **Set `status: draft`** in frontmatter. Commit.

Don't try to fill an entire note in one sitting. The vault is designed for incremental promotion.

---

## 8. Optional plugins (install when needed)

Don't install these on day 1 — wait until you have a concrete reason.

| Plugin | What it does | When to install |
|---|---|---|
| **Dataview** | SQL-like queries over frontmatter (`LIST FROM #workflow WHERE status = "stub"`) | When you want auto-generated index pages or to-do lists. |
| **Templater** | Smarter templates with variables and prompts | When manual template insertion gets tedious. |
| **Excalidraw** | Sketch diagrams in-vault | First time you reach for a sequence diagram. |
| **Advanced Tables** | Better markdown table editing | When the matrix becomes painful to hand-edit. |
| **Mermaid** (core, just enable) | Mermaid diagrams in fenced code blocks | First workflow you want to flow-chart. |

Install via: Settings → Community plugins → Browse. **Turn off safe mode first.**

---

## 9. Git workflow

The vault is a git repo. Treat commits like checkpoints, not perfection gates.

### Committed

- All markdown content
- `templates/`
- `.obsidian/app.json`, `appearance.json`, `core-plugins.json`, `templates.json` (so the vault opens consistently)
- `attachments/`

### Gitignored (per-machine state)

- `.obsidian/workspace*` — your open-tabs layout
- `.obsidian/cache` — index cache
- `.obsidian/plugins/*/data.json` — per-plugin local state
- `.trash/`
- OS junk (`.DS_Store`, `Thumbs.db`)

### Sensible commit cadence

- One commit per note promoted (`stub → draft`) or per topical session.
- Commit message format: `<type>: <what>` — e.g. `notes: flesh out corp-bonds-ig venues` or `index: rebuild workflow-index`.

### Day-to-day commands

```bash
git add 10_asset_classes/fx/fx-spot.md
git commit -m "notes: fx-spot draft"
git push
```

---

## 10. Publishing later (out of scope today)

Structure is already publish-ready. When you want a web view:

- **Quartz** (recommended for Obsidian vaults): `npx quartz create` in a sibling folder, point at this vault.
- **MkDocs Material**: needs minor frontmatter adjustment for nav.

Both honour wikilinks and frontmatter. Don't restructure to support publishing — these tools adapt to vault structure, not the other way round.

---

## 11. When you're stuck

- **"Where do I add this?"** → check the table in §2. If it crosses domains, put it where the dominant audience would look first; cross-link from elsewhere.
- **"Should this be one note or two?"** → if you'd ever want to link to just half of it, make it two.
- **"Should I delete a stub I'll never fill?"** → no, leave it. A red wikilink is a TODO. Empty space is invisible.
- **"How do I undo a change?"** → git. Always git. Obsidian's local-only file-recovery (core plugin) is a safety net for crashes, not a versioning tool.

---

## See also

- [[HOME]] — the entry point
- [[asset-class-matrix]] — comparison view
- [[workflow-index]] — workflow catalogue
- `README.md` (repo root) — the public-facing project description
