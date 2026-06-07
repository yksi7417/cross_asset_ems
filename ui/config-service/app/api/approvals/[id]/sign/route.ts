import { NextResponse } from "next/server";
import { MOCK_CHANGES, REQUIRED_SIGNOFFS } from "@/lib/mock-data";
import { VALIDATOR_DESCRIPTIONS } from "@/lib/constants";
import type { Signoff, SignoffRole, ValidatorCode } from "@/lib/types";

export const dynamic = "force-dynamic";

interface SignBody {
  role: SignoffRole;
  userId: string;
  decision: "APPROVE" | "REJECT";
  comment?: string;
}

function raise(code: ValidatorCode, message?: string) {
  return { code, message: message ?? VALIDATOR_DESCRIPTIONS[code] ?? code, severity: "BLOCK" as const };
}

export async function POST(req: Request, ctx: { params: { id: string } }) {
  const body = (await req.json()) as SignBody;
  const chg = MOCK_CHANGES.find((c) => c.id === ctx.params.id);
  if (!chg) return NextResponse.json({ error: "Unknown change" }, { status: 404 });
  if (chg.status !== "PENDING_APPROVAL") {
    return NextResponse.json({ error: "Change is not pending approval" }, { status: 409 });
  }
  if (new Date(chg.expiresAt).getTime() < Date.now()) {
    chg.validators.push(raise("EMS-CFG-1501"));
    return NextResponse.json({ error: "Proposal expired", change: chg }, { status: 410 });
  }
  if (body.userId === chg.proposedBy) {
    chg.validators.push(raise("EMS-CFG-1201"));
    return NextResponse.json({ error: "Self-approval blocked", change: chg }, { status: 403 });
  }
  const required = REQUIRED_SIGNOFFS[chg.domain] ?? [];
  if (!required.includes(body.role)) {
    return NextResponse.json({ error: `Role ${body.role} not required for domain ${chg.domain}` }, { status: 400 });
  }

  const signoff: Signoff = {
    role: body.role,
    userId: body.userId,
    decision: body.decision,
    comment: body.comment,
    signedAt: new Date().toISOString()
  };
  chg.signoffs.push(signoff);

  if (body.decision === "REJECT") {
    chg.status = "REJECTED";
  } else {
    const approvedRoles = new Set(chg.signoffs.filter((s) => s.decision === "APPROVE").map((s) => s.role));
    if (required.every((r) => approvedRoles.has(r))) chg.status = "APPROVED";
  }

  return NextResponse.json({ ok: true, change: chg });
}