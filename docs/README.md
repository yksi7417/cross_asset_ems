# docs/

Implementation documentation — distinct from the design vault.

The **vault** (root + `10_asset_classes/` + `20_workflows/` + `30_venues/` + `40_regulatory/` + `50_clearing_settlement/` + `60_documentation/` + `70_concepts/` + `80_architecture/`) is the design + reference knowledge. It's read by humans designing the system.

**This folder** is the implementation documentation. It's read by humans operating or contributing to the running code.

## Layout

| Folder / file | Contents |
|---|---|
| `USER_GUIDE.md` | **How to use the system**: build, run, test, drive the end-to-end flows, module map, what's coming. Start here. |
| `TODO.md` | The repo-wide deferred-work register. Decided-and-deferred work only, never a wish list — see [CONTRIBUTING.md § Deferring work](../CONTRIBUTING.md#deferring-work). Enforced by `scripts/ci/checks/deferred_work.py`. |
| `decisions/` | ADRs (Architecture Decision Records). One markdown file per decision, numbered `NNNN-<slug>.md`. Records what was decided, why, and what was rejected. |
| `polyglot/` | The Java/Rust/C++ port: program hub (`README.md`) and the gate reference (`gate.md`). Start at `polyglot/README.md` to pick that work up cold. |
| `runbooks/` | Operational runbooks: how to deploy, how to switchover, how to handle incidents, how to onboard a new pod. |
| `onboarding/` | New-engineer guide: setup, code tour, first-PR walkthrough, where to find what. |

## ADR template

Use this for every decision in `decisions/`:

```markdown
# NNNN — Decision title

Status: proposed | accepted | superseded by NNNN
Date: YYYY-MM-DD
Deciders: <names>
Context: vault wikilinks to the design notes this implements

## Context
What's the issue?

## Decision
What did we decide?

## Consequences
What follows from this decision?

## Alternatives considered
What did we reject and why?
```

## See also

- [[architecture-index]] (the design vault entry point)
- [[HOME]] (the vault entry point)
- [[USAGE]] (vault runbook)
