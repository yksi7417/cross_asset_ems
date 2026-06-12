/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Backend-restart recovery (user feedback 2026-06-11): when the backend dies the chips go
// orange and the streams retry forever — but a RESTARTED backend has fresh in-memory sessions
// and topic sequences starting from 1, so the old sessionId is rejected on every retry and the
// old cursor points past the new world. The only correct resume is a clean re-image: reload the
// page (auto-logon in main.ts makes that seamless).
//
// Rule: while any stream is not live, poll a cheap REST endpoint; after two consecutive healthy
// responses with the streams STILL not live, the server is up but our session/cursors are dead —
// reload. A brief network blip never trips this: the WS reconnects (500ms backoff) long before
// the second 3s health poll lands.

import type { StreamStatus } from "./stream";

const HEALTH_URL = "/api/v1/instruments/BBG000B9XRY4"; // any HTTP answer = edge alive
const POLL_MS = 3_000;

const statuses = new Map<string, StreamStatus>();
let timer: ReturnType<typeof setInterval> | null = null;
let healthyStreak = 0;

/** Feed every stream-status change here (main.ts setChip). */
export function reportStreamStatus(id: string, status: StreamStatus): void {
  statuses.set(id, status);
  const anyNotLive = [...statuses.values()].some((s) => s !== "live");
  if (anyNotLive && timer === null) {
    healthyStreak = 0;
    timer = setInterval(() => void checkHealth(), POLL_MS);
  } else if (!anyNotLive && timer !== null) {
    clearInterval(timer);
    timer = null;
  }
}

async function checkHealth(): Promise<void> {
  try {
    const r = await fetch(HEALTH_URL, { signal: AbortSignal.timeout(2_000) });
    healthyStreak = r.status < 500 ? healthyStreak + 1 : 0;
  } catch {
    healthyStreak = 0; // backend still unreachable — keep waiting
    return;
  }
  const anyNotLive = [...statuses.values()].some((s) => s !== "live");
  if (healthyStreak >= 2 && anyNotLive) {
    location.reload(); // server is back, our session/cursors are not — re-image
  }
}
