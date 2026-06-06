import type { ScopeLevel } from "./types";

export function fmtDate(iso: string): string {
  const d = new Date(iso);
  return d.toISOString().replace("T", " ").slice(0, 19) + "Z";
}

export function fmtDateShort(iso: string): string {
  return new Date(iso).toISOString().slice(0, 10);
}

export function relativeTime(iso: string, now = new Date()): string {
  const ms = now.getTime() - new Date(iso).getTime();
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.round(h / 24);
  if (d < 30) return `${d}d ago`;
  const mo = Math.round(d / 30);
  return `${mo}mo ago`;
}

export function scopeLabel(s: ScopeLevel): string {
  return s.replace(/\./g, " › ");
}

export function scopeBadge(s: ScopeLevel): { tone: "global" | "env" | "region" | "asset" | "firm" | "desk" | "user" | "override"; label: string } {
  const head = s.split(".")[0];
  const tail = s.split(".").slice(1).join(".");
  if (head === "global") return { tone: "global", label: "global" };
  if (head === "environment") return { tone: "env", label: `env:${tail}` };
  if (head === "region") return { tone: "region", label: `region:${tail}` };
  if (head === "asset_class") return { tone: "asset", label: `class:${tail}` };
  if (head === "firm") return { tone: "firm", label: `firm:${tail}` };
  if (head === "desk") return { tone: "desk", label: `desk:${tail}` };
  if (head === "user") return { tone: "user", label: `user:${tail}` };
  if (head === "order-override") return { tone: "override", label: "order-override" };
  return { tone: "firm", label: s };
}

export function fmtNumber(n: number, opts: Intl.NumberFormatOptions = {}): string {
  return new Intl.NumberFormat("en-US", opts).format(n);
}

export function fmtJson(v: unknown): string {
  return JSON.stringify(v, null, 2);
}