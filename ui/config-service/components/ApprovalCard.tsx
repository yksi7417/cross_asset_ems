"use client";
import { useState } from "react";
import type { Change, SignoffRole } from "@/lib/types";
import { useUi } from "@/lib/store";
import { REQUIRED_SIGNOFFS } from "@/lib/mock-data";
import { signChange } from "@/lib/api";
import { StatusBadge, ScopeBadge } from "./StatusBadge";
import { VersionDiff } from "./VersionDiff";
import { Card } from "./Card";
import { VALIDATOR_DESCRIPTIONS, ROLE_LABELS } from "@/lib/constants";
import { fmtDate, relativeTime } from "@/lib/format";
import { ShieldAlert, ShieldCheck, X, Check, Clock } from "lucide-react";
import { cn } from "@/lib/utils";

export function ApprovalCard({ change, onChanged }: { change: Change; onChanged: () => void }) {
  const user = useUi((s) => s.user);
  const required = REQUIRED_SIGNOFFS[change.domain] ?? [];
  const signedRoles = new Set(change.signoffs.filter((s) => s.decision === "APPROVE").map((s) => s.role));
  const rejectedRoles = new Set(change.signoffs.filter((s) => s.decision === "REJECT").map((s) => s.role));
  const missing = required.filter((r) => !signedRoles.has(r) && !rejectedRoles.has(r));
  const blocked = change.validators.some((v) => v.severity === "BLOCK");
  const selfApprovalAttempt = user.userId === change.proposedBy;
  const canActAsRole = (role: SignoffRole) => user.roles.includes(role);
  const canAct = !blocked && !selfApprovalAttempt && change.status === "PENDING_APPROVAL";

  const [busy, setBusy] = useState<SignoffRole | null>(null);
  const [comment, setComment] = useState("");

  async function act(role: SignoffRole, decision: "APPROVE" | "REJECT") {
    setBusy(role);
    try {
      await signChange(change.id, { role, userId: user.userId, decision, comment: comment || undefined });
      setComment("");
      onChanged();
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card
      title={
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono">{change.domain}/{change.key}</span>
          <ScopeBadge scope={change.scope} />
          <StatusBadge status={change.status === "PENDING_APPROVAL" ? "PENDING_APPROVAL" : "DRAFT"} />
        </div>
      }
      subtitle={
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="font-mono">{change.id}</span>
          <span>proposed by <span className="font-mono text-foreground">{change.proposedBy}</span></span>
          <span>· {relativeTime(change.proposedAt)} ({fmtDate(change.proposedAt)})</span>
          <span className="inline-flex items-center gap-1"><Clock className="h-3 w-3" /> expires {relativeTime(change.expiresAt)}</span>
        </div>
      }
      actions={
        <div className="flex items-center gap-3 text-xs">
          <span className="text-muted-foreground">v{change.currentVersion} →</span>
          <span className="font-semibold">v{change.proposedVersion}</span>
        </div>
      }
    >
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 space-y-3">
          <div>
            <div className="text-xs uppercase tracking-wide text-muted-foreground mb-1">Rationale</div>
            <p className="text-sm leading-relaxed">{change.rationale}</p>
          </div>
          <div>
            <div className="text-xs uppercase tracking-wide text-muted-foreground mb-1">Diff</div>
            <VersionDiff diff={change.diff} />
          </div>
        </div>

        <div className="space-y-4">
          <div>
            <div className="text-xs uppercase tracking-wide text-muted-foreground mb-2">Required signoffs</div>
            {required.length === 0 && <div className="text-sm text-muted-foreground italic">No signoff required.</div>}
            <ul className="space-y-2">
              {required.map((r) => {
                const approved = signedRoles.has(r);
                const rejected = rejectedRoles.has(r);
                return (
                  <li key={r} className="flex items-center justify-between gap-2 rounded-md border border-border p-2 text-sm">
                    <div>
                      <div className="font-medium">{ROLE_LABELS[r].title}</div>
                      <div className="text-[11px] text-muted-foreground">{r}</div>
                    </div>
                    {approved && <span className="text-success inline-flex items-center gap-1 text-xs"><Check className="h-3.5 w-3.5" /> approved</span>}
                    {rejected && <span className="text-destructive inline-flex items-center gap-1 text-xs"><X className="h-3.5 w-3.5" /> rejected</span>}
                    {!approved && !rejected && <span className="text-warning text-xs">pending</span>}
                  </li>
                );
              })}
            </ul>
          </div>

          {change.validators.length > 0 && (
            <div>
              <div className="text-xs uppercase tracking-wide text-muted-foreground mb-2">Validators</div>
              <ul className="space-y-1.5">
                {change.validators.map((v, i) => (
                  <li key={i} className={cn("rounded-md border p-2 text-xs flex items-start gap-2",
                    v.severity === "BLOCK" ? "border-destructive/30 bg-destructive/5 text-destructive" : "border-warning/30 bg-warning/5 text-warning"
                  )}>
                    <ShieldAlert className="h-4 w-4 mt-0.5 shrink-0" />
                    <div>
                      <div className="font-mono font-semibold">{v.code}</div>
                      <div className="text-foreground/80">{VALIDATOR_DESCRIPTIONS[v.code] ?? v.message}</div>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div>
            <div className="text-xs uppercase tracking-wide text-muted-foreground mb-2">Your action</div>
            {selfApprovalAttempt && (
              <div className="text-xs rounded-md border border-warning/30 bg-warning/5 text-warning p-2 mb-2">
                You proposed this change — self-approval is blocked (EMS-CFG-1201).
              </div>
            )}
            {blocked && !selfApprovalAttempt && (
              <div className="text-xs rounded-md border border-destructive/30 bg-destructive/5 text-destructive p-2 mb-2">
                Change is blocked by a validator.
              </div>
            )}
            <textarea
              className="w-full text-sm rounded-md border border-input bg-background p-2 focus:outline-none focus:ring-2 focus:ring-ring"
              rows={2}
              placeholder="Optional comment (visible in audit log)"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              disabled={!canAct}
            />
            <div className="mt-2 flex flex-wrap gap-2">
              {required.map((r) => {
                const can = canAct && canActAsRole(r);
                return (
                  <div key={r} className="flex items-center gap-1">
                    <button
                      disabled={!can || busy !== null}
                      onClick={() => act(r, "APPROVE")}
                      className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-md bg-success text-success-foreground disabled:opacity-40 hover:opacity-90"
                    >
                      <Check className="h-3.5 w-3.5" /> Approve · {r.split("_")[0]}
                      {busy === r && "…"}
                    </button>
                    <button
                      disabled={!can || busy !== null}
                      onClick={() => act(r, "REJECT")}
                      className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-md bg-destructive text-destructive-foreground disabled:opacity-40 hover:opacity-90"
                    >
                      <X className="h-3.5 w-3.5" /> Reject
                    </button>
                  </div>
                );
              })}
              {required.length > 0 && !canActAsRole(required[0]) && (
                <div className="text-xs text-muted-foreground italic">
                  You don't hold any of the required roles ({required.join(", ")}).
                </div>
              )}
            </div>
          </div>

          {change.signoffs.length > 0 && (
            <div>
              <div className="text-xs uppercase tracking-wide text-muted-foreground mb-2">Signoff history</div>
              <ul className="space-y-1.5 text-xs">
                {change.signoffs.map((s, i) => (
                  <li key={i} className="flex items-center gap-2">
                    {s.decision === "APPROVE"
                      ? <ShieldCheck className="h-3.5 w-3.5 text-success" />
                      : <X className="h-3.5 w-3.5 text-destructive" />}
                    <span className="font-medium">{s.role}</span>
                    <span className="text-muted-foreground">by {s.userId} · {relativeTime(s.signedAt)}</span>
                    {s.comment && <span className="text-muted-foreground italic">— {s.comment}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}