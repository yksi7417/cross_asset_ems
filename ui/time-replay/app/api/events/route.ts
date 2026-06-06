import { NextRequest, NextResponse } from "next/server";
import { searchEvents } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  const url = new URL(req.url);
  const stream = url.searchParams.get("stream") ?? undefined;
  const type = url.searchParams.get("type") ?? undefined;
  const from = url.searchParams.get("from") ?? undefined;
  const to = url.searchParams.get("to") ?? undefined;
  const q = url.searchParams.get("q") ?? undefined;
  const limit = Number(url.searchParams.get("limit") ?? "200");

  const events = searchEvents({ stream, type, from, to, q, limit });
  return NextResponse.json({ events });
}