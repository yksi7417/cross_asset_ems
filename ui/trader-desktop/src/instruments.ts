/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Security-name resolution (18.17 feedback #2): people read tickers/names, not FIGIs.
// Names come from GET /api/v1/instruments/{figi} (the security master) and are cached
// for the session; unknown instruments fall back to the FIGI so nothing renders blank.

const names = new Map<string, string>();
const inflight = new Map<string, Promise<string>>();

/** Resolved name if already cached (synchronous callers: dropdowns, labels). */
export function nameOfSync(figi: string): string {
  return names.get(figi) ?? figi;
}

/** Resolve a FIGI to its display name, fetching once per session. */
export function nameOf(figi: string): Promise<string> {
  const cached = names.get(figi);
  if (cached) {
    return Promise.resolve(cached);
  }
  let pending = inflight.get(figi);
  if (!pending) {
    pending = fetch(`/api/v1/instruments/${encodeURIComponent(figi)}`)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then((body: { name?: string }) => body.name || figi)
      .catch(() => figi)
      .then((name) => {
        names.set(figi, name);
        inflight.delete(figi);
        return name;
      });
    inflight.set(figi, pending);
  }
  return pending;
}

/** Add a resolved `name` field to a row that carries a `figi`. */
export async function withName<T extends Record<string, unknown>>(
  row: T,
): Promise<T & { name: string }> {
  return { ...row, name: await nameOf(row.figi as string) };
}
