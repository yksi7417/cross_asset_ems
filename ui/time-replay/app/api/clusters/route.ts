import { NextResponse } from "next/server";
import { listClusters } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET() {
  const clusters = listClusters();
  return NextResponse.json({ clusters });
}