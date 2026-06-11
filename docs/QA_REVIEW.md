# QA Review — Trader Desktop (2026-06-11)

Systematic interaction review of the Phase 18 desktop against the workflow specs
(`20_workflows/`), the demo walkthrough, and the running app. Each finding was reproduced before
classification; evidence is in the linked tests. **S1** = blocks/misleads a core workflow,
**S2** = degrades a workflow, **S3** = polish/backlog.

## Findings

| # | Sev | Area | Finding | Status |
|---|---|---|---|---|
| 1 | S1 | Feedback | Batch actions (cancel/route/ready from the context menu) reported bare counts — "CANCEL: 0/1 accepted" — while the server returned precise catalog codes (`EMS-ORD-3001` already-terminal, `EMS-RTE-4002` not-ready, `EMS-RTE-4003` exceeds-remaining). The operator could not tell *why* anything failed. | **Fixed** `013e7eb` — toasts carry the first rejection's code + message (+N more) |
| 2 | S1 | Context menus | Menus offered actions that could never succeed for the selected rows (Cancel on FILLED rows, Route on NEW orders). Most demo-bot rows are terminal, so "everything I click fails". | **Fixed** — actions filter by row state; inapplicable entries render disabled with "no applicable rows" |
| 3 | S1 | Protocol | The UI fired sequenced requests concurrently (a debounced preview racing a stage). The demo edge has no sequence recovery wired so it tolerates this, but the real channel gap-checks `sessionSeq` — latent breakage on the production wiring. | **Fixed** — `ApiClient` serializes sequenced operations through a promise queue; `EMS-SES-2001` now renders re-logon advice |
| 4 | S2 | Amend | AMEND silently reused whatever was last typed in the ticket (e.g. the new-order default qty 100) — an accidental amend-to-100 waiting to happen. No prefill, no indication of what was being edited. | **Fixed** — selecting a working order prefills its qty/px and shows "editing ‹name› ‹id›" |
| 5 | S2 | Selection | Single-row selection was invisible (chip only appeared for 2+). Users couldn't tell what a right-click would act on. | **Fixed** — chip always shows "▸ ‹name› ‹id›" / "▸ N selected" |
| 6 | S2 | Watchlist | Per-desk symbol lists (the 18.14 feature) had no UI or REST surface to add/remove symbols — the server supported it, the desk couldn't reach it. | **Fixed** — header input adds, right-click removes; `GET/POST/DELETE /api/v1/watchlist/{desk}` |
| 7 | S2 | Layout | On shorter viewports the left column squeezed WATCHLIST and NOTIFICATIONS to 0 px (flex children with no min-height under a grown ticket panel). Found by automated probe. | **Fixed** — min-heights + scrollable left column |
| 8 | S2 | API/UI contract | `perspective-click` rows only carry **visible** columns — twice now an interaction silently lost its key when a column set omitted the ID. | **Fixed** for all grids; rule documented in the walkthrough + pinned by E2E tests |
| 9 | S3 | Linking | Baskets grid is not linked to its constituent orders (clicking a basket should filter the blotter). Order rows don't carry a basket ID — needs a server-side field. | Backlog (server change) |
| 10 | S3 | Kill UX | Kill engage/release use native `prompt()`/`alert()` — inconsistent with the inline UI language, and blocked in some embedded browsers. | Backlog |
| 11 | S3 | Session | No logout affordance; no session-expiry handling (in-memory sessions never expire today). | Backlog |
| 12 | S3 | Selection | No visual row highlight inside the datagrid (Perspective owns the grid DOM). The chip mitigates; a custom datagrid style hook could do better. | Backlog |
| 13 | S3 | Working orders | The ticket dropdown polls on a 1s tick and can momentarily lose selection when the list refreshes mid-interaction. | Backlog (move to stream-driven refresh) |
| 14 | S3 | P&L | The `TOTAL (USD)` row sorts lexicographically among accounts. Cosmetic. | Backlog |
| 15 | S3 | A11y | Context menus and tiles are mouse-only; no keyboard path or ARIA roles. | Backlog |
| 16 | S2 | Routes | Cancel of a `SENT` route (no venue ack yet) is rejected by the Route FSM (`EMS-RTE-5002`) — found by the contract test. UI now only offers route-cancel on `WORKING`/`PARTIALLY_FILLED`; supporting cancel-from-SENT (FIX pending-cancel-before-ack) needs a Route FSM transition. | **UI fixed**; FSM extension backlog |

## Test architecture (the prevention half)

Defects 1–8 all share a shape: the server behaved correctly, the UI lost information or offered
the wrong affordance. The remedy is contract pinning at two levels — see
[`TESTING.md`](TESTING.md) for the strategy and decision rules:

- **API workflow-contract tests** (`UiWorkflowContractTest`, Java, real HTTP) pin the exact
  error codes and shapes the UI renders — many, fast, no browser.
- **Playwright E2E specs** (one per trader workflow, `ui/trader-desktop/tests/e2e/`) run against
  a deterministic `--quiet` edge and assert the *user-visible* outcome: the toast text, the
  filtered row counts, the disabled menu entries, the banner.

The recorded demo (bot on) stays a smoke/showcase, not an assertion surface — it is
intentionally non-deterministic.
