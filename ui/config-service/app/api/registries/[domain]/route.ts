import { NextResponse } from "next/server";
import { MOCK_RECORDS } from "@/lib/mock-data";
import { REGISTRIES } from "@/lib/constants";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, ctx: { params: { domain: string } }) {
  const exists = REGISTRIES.some((r) => r.domain === ctx.params.domain);
  if (!exists) return NextResponse.json({ error: "Unknown registry" }, { status: 404 });
  const records = MOCK_RECORDS.filter((r) => r.domain === ctx.params.domain);
  return NextResponse.json(records);
}