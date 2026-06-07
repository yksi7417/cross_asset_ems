import { NextResponse } from "next/server";
import { getReplay, getReplayEvents } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET(
  _req: Request,
  { params }: { params: { id: string } }
) {
  const replay = getReplay(params.id);
  if (!replay) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  const events = getReplayEvents(params.id);
  return NextResponse.json({ replay, events });
}