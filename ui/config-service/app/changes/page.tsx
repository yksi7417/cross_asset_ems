"use client";
import { useChanges } from "@/lib/api";
import { Card } from "@/components/Card";
import { ScopeBadge, StatusBadge } from "@/components/StatusBadge";
import { Skeleton } from "@/components/Skeleton";
import { fmtDate, relativeTime } from "@/lib/format";

const actionTone: Record<string, string> = {
  PROPOSE: "text-primary",
  APPROVE: "text-success",
  REJECT: "text-destructive",
  APPLY: "text-foreground",
  EXPIRE: "text-muted-foreground",
  WITHDRAW: "text-warning"
};

export default function ChangesPage() {
  const { data, isLoading, error } = useChanges();
  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-2xl font-semibold">Audit log</h1>
        <p className="text-sm text-muted-foreground">Every propose, sign-off, apply, reject, expire, or withdraw is recorded with actor, role, and timestamp.</p>
      </header>
      <Card>
        {isLoading && <Skeleton rows={6} />}
        {error && <div className="text-sm text-destructive">Failed to load: {String(error)}</div>}
        {data && (
          <ul className="divide-y divide-border">
            {data.map((e) => (
              <li key={e.id} className="py-2.5 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2 min-w-0">
                  <span className={`text-xs font-mono w-20 ${actionTone[e.action] ?? "text-foreground"}`}>{e.action}</span>
                  <span className="font-mono text-sm truncate">{e.domain}/{e.key}</span>
                  <ScopeBadge scope={(e as any).scope ?? "global.default"} />
                  {e.fromVersion !== undefined && (
                    <span className="text-xs text-muted-foreground">v{e.fromVersion}→v{e.toVersion}</span>
                  )}
                </div>
                <div className="text-xs text-muted-foreground whitespace-nowrap text-right">
                  <div className="font-mono">{e.actor}{e.role ? ` · ${e.role}` : ""}</div>
                  <div title={e.at}>{fmtDate(e.at)} · {relativeTime(e.at)}</div>
                  {e.note && <div className="italic">{e.note}</div>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}