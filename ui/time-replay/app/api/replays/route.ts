import { NextRequest, NextResponse } from "next/server";
import { createReplaySession, listReplays } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ replays: listReplays() });
}

export async function POST(req: NextRequest) {
  const body = await req.json();
  if (!body?.name || !body?.stream) {
    return NextResponse.json(
      { error: "name and stream are required" },
      { status: 400 }
    );
  }
  const session = createReplaySession({
    name: String(body.name),
    cluster_id: String(body.cluster_id ?? "default"),
    stream: String(body.stream),
    from: body.from ? String(body.from) : undefined,
    to: body.to ? String(body.to) : undefined,
    speed: Number(body.speed ?? 1),
  });
  return NextResponse.json(session, { status: 201 });
}