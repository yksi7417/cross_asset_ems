import { NextResponse } from "next/server";
import { MOCK_RECORDS, MOCK_CHANGES } from "@/lib/mock-data";
import { REGISTRIES } from "@/lib/constants";
import type { DomainType } from "@/lib/types";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, ctx: { params: { domain: string; key: string } }) {
  const exists = REGISTRIES.some((r) => r.domain === ctx.params.domain);
  if (!exists) return NextResponse.json({ error: "Unknown registry" }, { status: 404 });
  const key = decodeURIComponent(ctx.params.key);
  const all = MOCK_RECORDS.filter((r) => r.domain === ctx.params.domain && r.key === key);
  if (!all.length) return NextResponse.json({ current: null, history: [], change: null });
  const current = all.reduce((a, b) => (a.version > b.version ? a : b));
  const history = [...all].sort((a, b) => b.version - a.version);
  const change = MOCK_CHANGES.find(
    (c) => c.domain === (ctx.params.domain as DomainType) && c.key === key && (c.status === "PENDING_APPROVAL" || c.status === "DRAFT")
  ) ?? null;
  return NextResponse.json({ current, history, change });
}