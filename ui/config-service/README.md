# Config Service — Admin Console

Internal admin tool for the **Configuration Service**: reference-data
registries, change-workflow sign-off, and version history. See
`arch-configuration-service.md` (line 99 for scope levels, §"Approval
workflow" for the sign-off model).

## Stack

- Next.js 14 (App Router) + React 18 + TypeScript
- Tailwind CSS (shadcn-style primitives in `components/`)
- SWR for data fetching, zustand for UI state
- lucide-react for icons
- Mock data + Next.js route handlers in `app/api/*`

## Run

```bash
cd ui/config-service
npm install
npm run dev
# open http://localhost:3000
```

## Layout

```text
app/
  layout.tsx              shell: nav + top bar
  page.tsx                dashboard
  registries/             browse registries and records
    page.tsx              all registries
    [domain]/page.tsx     records in one registry
    [domain]/[key]/page.tsx  record detail + open diff
  approvals/page.tsx      sign-off queue (DRAFT + PENDING_APPROVAL)
  changes/page.tsx        audit log
  api/                    mock REST surface (Next route handlers)
components/
  Nav, Card, StatusBadge, RegistryTable, VersionDiff, ApprovalCard, Providers
  Skeleton, Tabs          tiny local primitives
lib/
  types.ts                DomainType, RefDataRecord, Change, Signoff, AuditEntry
  constants.ts            REGISTRIES, SCOPE_LEVELS, ROLE_LABELS, VALIDATOR_DESCRIPTIONS
  mock-data.ts            MOCK_RECORDS, MOCK_CHANGES, MOCK_AUDIT, REQUIRED_SIGNOFFS
  api.ts                  SWR hooks
  store.ts                zustand: current user, sidebar
  format.ts, utils.ts     helpers
```

## Domain model

- **Registries** are versioned, scoped collections of records.
- **Scope levels** (9 + override): `global.default`, `environment.{dev|qa|uat|prod}`,
  `region.{ny|ldn|tyo|hkg}`, `pod.{id}`, `asset_class.{equity|fi|fx|...}`,
  `firm.{id}`, `desk.{firm}.{desk}`, `user.{firm}.{desk}.{user}`,
  `order-override`.
- **Change proposals** carry a `diff` and a list of required signoffs
  derived from the domain.
- **Validators** are BLOCK- or WARN-level. The UI surfaces them inline
  on the change card and on the record detail page.

## Approval workflow

Each registry domain has a list of required signoff roles
(`lib/mock-data.ts :: REQUIRED_SIGNOFFS`):

| Domain            | Required signoffs                                         |
|-------------------|-----------------------------------------------------------|
| counterparty      | COMPLIANCE_OFFICER                                        |
| broker            | BEST_EXEC_COMMITTEE                                       |
| compliance        | COMPLIANCE_OFFICER, RISK                                  |
| allocation        | BEST_EXEC_COMMITTEE                                       |
| microstructure    | BEST_EXEC_COMMITTEE, RISK                                 |
| curves            | RISK                                                      |
| wheel             | COMPLIANCE_OFFICER, RISK, BEST_EXEC_COMMITTEE             |
| validator_rules   | COMPLIANCE_OFFICER                                        |
| calendars, fsm_definitions, sbe_schemas | (none — internal)                       |

The current user is held in `lib/store.ts` and defaults to holding the
**COMPLIANCE_OFFICER** role, so by default you can sign off on
counterparty, compliance, validator_rules, and wheel proposals (and
co-sign wheel/compliance). The role chip in the sidebar shows what you
hold.

The Approvals page enforces:

- **EMS-CFG-1201** — self-approval is blocked; the UI hides the buttons
  and the API returns 403 if the proposer tries to sign.
- **EMS-CFG-1501** — expired proposals (TTL exceeded) are marked
  blocked in the UI; the API returns 410.
- **EMS-CFG-1301 / 1302** — production-scope changes outside the
  maintenance window or with an effective-in-past time are surfaced
  as warnings/blocks (see `MOCK_CHANGES`).

The approve/reject buttons are role-gated: if the current user does
not hold any of the required roles, the buttons render disabled with
a hint.

## Mock data

`lib/mock-data.ts` ships with realistic records across all 12
registries (counterparty, broker, compliance, allocation, microstructure,
calendars, curves, wheel, validator_rules, fsm_definitions, sbe_schemas,
firm_settings), firms `acme-capital` and `globex-trading`, desks
`acme-fx-tokyo` and `globex-equities-ny`, and six counterparties
(goldman-sachs, jp-morgan, citi, hsbc, barclays, morgan-stanley).

Six change proposals cover the full lifecycle: PENDING with no
signoffs, PENDING with one signoff, EXPIRED, APPROVED, REJECTED, and
DRAFT.

## Replacing the mock backend

The route handlers in `app/api/*` are the only boundary with the
real Configuration Service. Swap the `MOCK_*` imports for calls to
your service (e.g. via `fetch` to a gRPC-gateway) — the SWR hooks in
`lib/api.ts` don't need to change.

> Note: the `Skeleton` and `Tabs` components referenced by the pages
> are intentionally minimal — they live alongside `components/`
> and use the same Tailwind + `cn` helper. If you bootstrap shadcn
> (`npx shadcn@latest init`), replace these with the generated
> `<Skeleton />` and `<Tabs />` primitives from shadcn/ui — the
> call sites already match the shadcn API.
