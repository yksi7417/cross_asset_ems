import { NextResponse } from "next/server";
import { MOCK_RECORDS, MOCK_CHANGES } from "@/lib/mock-data";
import { REGISTRIES } from "@/lib/constants";
import type { DomainType } from "@/lib/types";

export const dynamic = "force-dynamic";

export async function GET() {
  const summary = REGISTRIES.map((r) => {
    const recs = MOCK_RECORDS.filter((x) => x.domain === r.domain);
    const pending = MOCK_CHANGES.filter((c) => c.domain === r.domain && c.status === "PENDING_APPROVAL").length;
    return {
      domain: r.domain as DomainType,
      count: recs.length,
      active: recs.filter((x) => x.status === "ACTIVE").length,
      draft: recs.filter((x) => x.status === "DRAFT").length,
      pending
    };
  });
  return NextResponse.json(summary);
}