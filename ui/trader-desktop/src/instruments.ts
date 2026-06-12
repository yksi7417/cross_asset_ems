/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Security-master resolution (18.17 feedback #2, extended for 18.21): people read tickers/names,
// not FIGIs — and a cross-asset blotter needs the asset class on every row. Both come from
// GET /api/v1/instruments/{figi} (the security master) and are cached for the session; unknown
// instruments fall back to the FIGI so nothing renders blank.

export interface InstrumentInfo {
  name: string;
  assetClass: string;
  /** Issuer display name (18.29) — empty for products with no single issuer (FX, IRS, index). */
  issuer: string;
}

const cache = new Map<string, InstrumentInfo>();
const inflight = new Map<string, Promise<InstrumentInfo>>();

/** Resolved name if already cached (synchronous callers: dropdowns, labels). */
export function nameOfSync(figi: string): string {
  return cache.get(figi)?.name ?? figi;
}

/** Resolve a FIGI to its security-master info, fetching once per session. */
export function infoOf(figi: string): Promise<InstrumentInfo> {
  const cached = cache.get(figi);
  if (cached) {
    return Promise.resolve(cached);
  }
  let pending = inflight.get(figi);
  if (!pending) {
    pending = fetch(`/api/v1/instruments/${encodeURIComponent(figi)}`)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then((body: { name?: string; assetClass?: string; issuer?: string }) => ({
        name: body.name || figi,
        assetClass: body.assetClass ?? "",
        issuer: body.issuer ?? "",
      }))
      .catch(() => ({ name: figi, assetClass: "", issuer: "" }))
      .then((info) => {
        cache.set(figi, info);
        inflight.delete(figi);
        return info;
      });
    inflight.set(figi, pending);
  }
  return pending;
}

/** Resolve a FIGI to its display name, fetching once per session. */
export function nameOf(figi: string): Promise<string> {
  return infoOf(figi).then((info) => info.name);
}

/** Add resolved `name` + `assetClass` fields to a row that carries a `figi`. */
export async function withInstrument<T extends Record<string, unknown>>(
  row: T,
): Promise<T & InstrumentInfo> {
  return { ...row, ...(await infoOf(row.figi as string)) };
}
