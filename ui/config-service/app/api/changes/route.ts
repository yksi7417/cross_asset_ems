import { NextResponse } from "next/server";
import { MOCK_AUDIT } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json(MOCK_AUDIT);
}