import { Card } from "@/components/Card";
import { StatusBadge, ScopeBadge } from "@/components/StatusBadge";
import { MOCK_RECORDS, MOCK_CHANGES } from "@/lib/mock-data";
import { REGISTRIES, ROLE_LABELS } from "@/lib/constants";
import { useRegistries } from "@/lib/api";
import Link from "next/link";
import { fmtDate, relativeTime } from "@/lib/format";
import { ArrowRight, CheckSquare, Clock, Layers, ShieldAlert } from "lucide-react";

export default function DashboardPage() {
  const { data: stats } = useRegistries();

  const pending = MOCK_CHANGES.filter((c) => c.status === "PENDING_APPROVAL");
  const drafts = MOCK_CHANGES.filter((c) => c.status === "DRAFT");
  const blocked = MOCK_CHANGES.filter((c) => c.validators.some((v) => v.severity === "BLOCK"));

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">Reference-data registries, change-workflow queue, and audit overview.</p>
        </div>
        <Link href="/approvals" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
          Go to approvals <ArrowRight className="h-4 w-4" />
        </Link>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat label="Registries" value={REGISTRIES.length} icon={Layers} hint="12 domains governed" />
        <Stat label="Records" value={MOCK_RECORDS.length} icon={Layers} hint={`${MOCK_RECORDS.filter((r) => r.status === "ACTIVE").length} active`} />
        <Stat label="Pending approvals" value={pending.length} icon={CheckSquare} hint={`${drafts.length} draft, ${blocked.length} blocked`} />
        <Stat label="Open validators" value={MOCK_CHANGES.reduce((n, c) => n + c.validators.filter((v) => v.severity === "BLOCK").length, 0)} icon={ShieldAlert} hint="block-level" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card title="Registries" className="lg:col-span-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {REGISTRIES.map((r) => {
              const recs = MOCK_RECORDS.filter((x) => x.domain === r.domain);
              const active = recs.filter((x) => x.status === "ACTIVE").length;
              const draft = recs.filter((x) => x.status === "DRAFT").length;
              return (
                <Link key={r.domain} href={`/registries/${r.domain}`}
                  className="rounded-md border border-border p-3 hover:bg-accent/40 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="font-medium text-sm">{r.title}</div>
                    <div className="text-xs text-muted-foreground font-mono">{recs.length}</div>
                  </div>
                  <div className="text-xs text-muted-foreground mt-1 line-clamp-2">{r.description}</div>
                  <div className="mt-2 flex items-center gap-2 text-[11px]">
                    <span className="px-1.5 py-0.5 rounded bg-success/10 text-success border border-success/30">{active} active</span>
                    {draft > 0 && <span className="px-1.5 py-0.5 rounded bg-muted text-muted-foreground border border-border">{draft} draft</span>}
                  </div>
                </Link>
              );
            })}
          </div>
        </Card>

        <Card title="Pending approvals" subtitle={<span className="text-xs text-muted-foreground">Sorted by proposed time</span>}>
          <ul className="divide-y divide-border">
            {pending.length === 0 && <li className="text-sm text-muted-foreground py-2">Nothing in the queue.</li>}
            {pending.map((c) => (
              <li key={c.id} className="py-2.5">
                <Link href="/approvals" className="block hover:bg-accent/40 -mx-2 px-2 rounded">
                  <div className="flex items-center justify-between">
                    <div className="font-mono text-sm">{c.domain}/{c.key}</div>
                    <div className="text-xs text-muted-foreground">v{c.currentVersion}→v{c.proposedVersion}</div>
                  </div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    by <span className="font-mono text-foreground">{c.proposedBy}</span> · {relativeTime(c.proposedAt)}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      <Card title="Recent activity" subtitle={<span className="text-xs text-muted-foreground">Latest proposals and sign-offs</span>}>
        <ul className="text-sm space-y-1.5">
          {MOCK_CHANGES.slice(0, 6).map((c) => (
            <li key={c.id} className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 min-w-0">
                <StatusBadge status={c.status === "PENDING_APPROVAL" ? "PENDING_APPROVAL" : c.status === "APPROVED" ? "ACTIVE" : c.status === "REJECTED" ? "DEPRECATED" : "DRAFT"} />
                <span className="font-mono truncate">{c.domain}/{c.key}</span>
                <ScopeBadge scope={c.scope} />
              </div>
              <div className="text-xs text-muted-foreground whitespace-nowrap">
                {fmtDate(c.proposedAt)} · {c.proposedBy}
              </div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

function Stat({ label, value, icon: Icon, hint }: { label: string; value: number; icon: any; hint: string }) {
  return (
    <Card>
      <div className="flex items-center justify-between">
        <div>
          <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
          <div className="text-2xl font-semibold tabular-nums mt-1">{value}</div>
          <div className="text-xs text-muted-foreground mt-0.5">{hint}</div>
        </div>
        <Icon className="h-5 w-5 text-muted-foreground" />
      </div>
    </Card>
  );
}