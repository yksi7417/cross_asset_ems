# Time/Replay Server Console

A Next.js 14 admin UI for inspecting clusters, searching the event store, and
managing time/replay sessions.

## Run

npm install
npm run dev

Open http://localhost:3000.

Build / start:

npm run build
npm start

Lint:

npm lint

## Pages

| Path              | Purpose                                                                                |
| ----------------- | -------------------------------------------------------------------------------------- |
| `/`               | Overview: cluster health, recent replays, event throughput stats.                      |
| `/clusters`       | Cluster topology. Pick a cluster to inspect member status, leader, region, lag.        |
| `/events`         | Event search. Filter by stream, type, time range, and free-text payload match.        |
| `/replay`         | Replay list with status filters and a form to start a new replay.                      |
| `/replay/[id]`    | Replay detail: header stats, timeline, event stream, side-by-side diff, raw metadata.  |

## Components

- `Nav` — top navigation.
- `Card`, `StatCard` — content containers.
- `StatusBadge` — colored badge for a status string.
- `ClusterStatusCard` — one cluster member's host/role/status/lag.
- `ReplayTimeline` — visual timeline of events for a replay.
- `EventStream` — scrollable list of events with optional selection + diff.

## API routes (mock)

| Route                        | Methods   | Description                                      |
| ---------------------------- | --------- | ------------------------------------------------ |
| `/api/clusters`              | `GET`     | List clusters with members.                      |
| `/api/replays`               | `GET`     | List replay sessions.                            |
| `/api/replays`               | `POST`    | Create a new replay session.                     |
| `/api/replays/[id]`          | `GET`     | Single replay + its events.                      |
| `/api/events`                | `GET`     | Filtered event search (stream/type/from/to/q).   |

All API handlers currently delegate to `lib/mock-data.ts` so the UI is fully
runnable without a backend.

## Wiring to a real backend

1. Implement the same function signatures in `lib/api.ts` as HTTP fetches
   (`fetchReplays`, `fetchReplay`, `createReplay`, `fetchReplayEvents`,
   `searchEvents`, `fetchClusters`) pointing at your server. Keep the return
   types from `lib/types.ts` (`ReplaySession`, `ReplayEvent`, `ClusterInfo`,
   `ClusterMember`).
2. Replace the bodies in `lib/mock-data.ts` with the real client, or delete
   the file once `lib/api.ts` no longer references it.
3. Map the real `/api/...` route handlers in `app/api/**/route.ts` onto your
   backend's endpoints (proxy, or just delete the route files and point
   `lib/api.ts` directly at the backend URL).
4. Environment: set `NEXT_PUBLIC_API_BASE` (read it in `lib/api.ts`) when the
   backend lives on a different origin. The included `lib/api.ts` already
   prefixes requests with this variable.

## Layout

app/
  layout.tsx, page.tsx, globals.css
  clusters/page.tsx
  events/page.tsx
  replay/page.tsx
  replay/[id]/page.tsx
  api/
    clusters/route.ts
    events/route.ts
    replays/route.ts
    replays/[id]/route.ts
components/
  Nav.tsx, Providers.tsx,
  Card.tsx, StatCard.tsx, StatusBadge.tsx,
  ClusterStatusCard.tsx, ReplayTimeline.tsx, EventStream.tsx
lib/
  types.ts, api.ts, store.ts, utils.ts, format.ts,
  constants.ts, mock-data.ts