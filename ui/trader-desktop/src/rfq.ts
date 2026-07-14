/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// RFQ workflow (11.18): for RFQ-traded instruments (bonds, ETF blocks) the ticket fires a
// multi-dealer request and renders the QUOTE LADDER — competing quotes sorted BEST-FIRST for the
// side (the server sorts; the top row is highlighted with its price improvement vs the cover),
// each with dealer, price, size, qualifier badge (FIRM vs LAST LOOK) and a live countdown to
// quote expiry. Ineligible quotes (account lacks the dealer relationship) render greyed and
// un-hittable — visible on purpose: the better price you can't access is an onboarding action
// item, not something to hide. Accepting books through the same OMS path as any execution, so
// the fill lands in the blotter/P&L like everything else; a faded quote (last look) drops the
// RFQ back to ACTIVE and the ladder re-arms for another election.

interface RfqQuote {
  responseId: string;
  dealer: string;
  px: number;
  qty: number;
  qualifier: "FIRM" | "INDICATIVE" | "LAST_LOOK";
  validUntilMillis: number;
  eligible: boolean;
}

interface RfqImage {
  rfqId: string;
  state: string;
  expireAtMillis: number;
  executedResponseId?: string;
  quotes: RfqQuote[];
}

const PRICE_SCALE = 10_000;

let sessionHeader: Record<string, string> = {};
let pollTimer: ReturnType<typeof setInterval> | null = null;
let current: RfqImage | null = null;
const faded = new Set<string>(); // quotes that failed last-look — demoted, never re-offered
let onBooked: (() => void) | null = null;

const el = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

/** Wire the RFQ section of the ticket. Call once at logon. */
export function initRfq(sessionId: number, booked: () => void): void {
  sessionHeader = { "Content-Type": "application/json", "X-EMS-Session": String(sessionId) };
  onBooked = booked;
  el<HTMLButtonElement>("rfq-fire").addEventListener("click", () => void fire());
}

/** Enable/disable the REQUEST QUOTES button per the instrument's quote style (11.18). */
export function setQuoteStyle(quoteStyle: string | undefined): void {
  const fire = el<HTMLButtonElement>("rfq-fire");
  const supported = quoteStyle === "RFQ" || quoteStyle === "BOTH";
  fire.disabled = !supported;
  fire.textContent = supported ? "REQUEST QUOTES" : "REQUEST QUOTES (not RFQ-traded)";
}

async function fire(): Promise<void> {
  const figi = el<HTMLInputElement>("tk-figi").value.trim();
  const side = Number(el<HTMLSelectElement>("tk-side").value);
  const qty = Number(el<HTMLInputElement>("tk-qty").value);
  const account = el<HTMLInputElement>("tk-account").value.trim();
  if (!window.confirm(`Fire RFQ for ${qty} units? This will request quotes from multiple dealers.`)) {
    return;
  }
  const response = await fetch("/api/v1/rfq/request", {
    method: "POST",
    headers: sessionHeader,
    body: JSON.stringify({ figi, side, qty, account, ttlMillis: 25_000 }),
  });
  if (!response.ok) {
    setStatus(`RFQ failed (${response.status})`);
    return;
  }
  current = (await response.json()) as RfqImage;
  faded.clear();
  el("rfq-panel").classList.remove("hidden");
  render();
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => void refresh(), 700);
}

async function refresh(): Promise<void> {
  if (!current) {
    return;
  }
  const response = await fetch(`/api/v1/rfq/${encodeURIComponent(current.rfqId)}`, {
    headers: sessionHeader,
  });
  if (response.ok) {
    current = (await response.json()) as RfqImage;
  }
  render();
  if (current && ["EXECUTED", "EXPIRED", "CANCELLED", "NO_RESPONSES"].includes(current.state)) {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    if (current.state === "EXECUTED") {
      onBooked?.();
    }
  }
}

async function accept(responseId: string): Promise<void> {
  if (!current) {
    return;
  }
  const quote = current.quotes.find((q) => q.responseId === responseId);
  const qty = quote?.qty ?? 0;
  const dealer = quote?.dealer ?? "unknown";
  if (!window.confirm(`Accept quote from ${dealer} for ${qty} units? This will book the execution.`)) {
    return;
  }
  const response = await fetch(`/api/v1/rfq/${encodeURIComponent(current.rfqId)}/elect`, {
    method: "POST",
    headers: sessionHeader,
    body: JSON.stringify({ responseId }),
  });
  const body = (await response.json()) as RfqImage & { error?: string };
  if (!response.ok) {
    setStatus(body.error ?? `elect failed (${response.status})`);
    return;
  }
  current = body;
  render();
  if (current.state === "EXECUTED") {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    onBooked?.();
  } else if (current.state === "ACTIVE") {
    faded.add(responseId); // demote: a dealer that faded does not get re-elected
    render();
    setStatus("quote faded (last look) — RFQ re-armed, pick another");
  }
}

function setStatus(text: string): void {
  el("rfq-status").textContent = text;
}

function render(): void {
  if (!current) {
    return;
  }
  const now = Date.now();
  const ladder = el("rfq-ladder");
  ladder.replaceChildren();
  if (current.state === "EXECUTED") {
    const winner = current.quotes.find((q) => q.responseId === current!.executedResponseId);
    setStatus(
      `EXECUTED ${winner ? `${winner.dealer} @ ${(winner.px / PRICE_SCALE).toFixed(4)}` : ""} — booked to blotter`,
    );
    return;
  }
  if (current.state !== "ACTIVE") {
    setStatus(current.state.replace("_", " "));
    return;
  }
  const rfqTtl = Math.max(0, Math.ceil((current.expireAtMillis - now) / 1000));
  setStatus(`${current.quotes.length} quotes · RFQ expires in ${rfqTtl}s`);
  const bestEligible = current.quotes.find(
    (q) => q.eligible && q.validUntilMillis > now && !faded.has(q.responseId),
  );
  current.quotes.forEach((quote) => {
    const li = document.createElement("li");
    const live = quote.validUntilMillis > now;
    if (quote === bestEligible) {
      li.classList.add("best");
    }
    if (!quote.eligible) {
      li.classList.add("ineligible");
      li.title = "Account not eligible to trade with this dealer (onboarding required)";
    }
    const dealer = document.createElement("span");
    dealer.className = "rq-dealer";
    dealer.textContent = quote.dealer;
    const px = document.createElement("span");
    px.className = "rq-px";
    px.textContent = (quote.px / PRICE_SCALE).toFixed(4);
    const qual = document.createElement("span");
    qual.className = "rq-qual";
    qual.textContent = quote.qualifier === "LAST_LOOK" ? "LAST LOOK" : quote.qualifier;
    const ttl = document.createElement("span");
    ttl.className = "rq-ttl";
    ttl.textContent = live ? `${Math.ceil((quote.validUntilMillis - now) / 1000)}s` : "—";
    const hit = document.createElement("button");
    const isFaded = faded.has(quote.responseId);
    hit.textContent = !quote.eligible ? "NOT ELIGIBLE" : isFaded ? "FADED" : live ? "ACCEPT" : "EXPIRED";
    hit.disabled = !quote.eligible || !live || isFaded;
    hit.addEventListener("click", () => void accept(quote.responseId));
    li.append(dealer, px, qual, ttl, hit);
    ladder.append(li);
  });
}
