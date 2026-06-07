"use client";
import { use } from "react";
import Link from "next/link";
import { useRecord } from "@/lib/api";
import { Card } from "@/components/Card";
import { StatusBadge, ScopeBadge } from "@/components/StatusBadge";
import { VersionDiff } from "@/components/VersionDiff";
import { Skeleton } from "@/components/Skeleton";
import { REGISTRIES } from "@/lib/constants";
import type { DomainType } from "@/lib/types";
import { fmtDate, fmtJson, relativeTime } from "@/lib/format";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";

export default function RecordDetailPage({ params }: { params: Promise<{ domain: string; key: string }> }) {
  const { domain, key } = use(params);
  const meta = REGISTRIES.find((r) => r.domain === domain);
  if (!meta) notFound();
  const { data, isLoading, error } = useRecord(domain as DomainType, decodeURIComponent(key));

  return (
    <div className="space-y-4">
      <Link href={`/registries/${domain}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Back to {meta.title}
      </Link>

      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold font-mono">{decodeURIComponent(key)}</h1>
          <div className="text-sm text-muted-foreground">{meta.title} · <span className="font-mono">{meta.domain}</span></div>
        </div>
      </header>

      {isLoading && <Skeleton rows={6} />}
      {error && <Card><div className="text-sm text-destructive">Failed to load: {String(error)}</div></Card>}

      {data && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <Card title="Current value"
              subtitle={
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  {data.current ? <><ScopeBadge scope={data.current.scope} /> <StatusBadge status={data.current.status} /> v{data.current.version} · hash <span className="font-mono">{data.current.hash}</span></> : <span>No active record</span>}
                </div>
              }>
              {data.current ? (
                <pre className="text-xs font-mono bg-muted/40 rounded-md p-3 overflow-auto">{fmtJson(data.current.value)}</pre>
              ) : <div className="text-sm text-muted-foreground">Record has not been created yet.</div>}
            </Card>

            {data.change && (
              <Card title="Open change proposal"
                subtitle={
                  <div className="text-xs text-muted-foreground">
                    {data.change.id} · v{data.change.currentVersion} → v{data.change.proposedVersion} · proposed {relativeTime(data.change.proposedAt)} by <span className="font-mono">{data.change.proposedBy}</span>
                  </div>
                }>
                <p className="text-sm mb-3">{data.change.rationale}</p>
                <VersionDiff diff={data.change.diff} />
                <div className="mt-3">
                  <Link href="/approvals" className="text-sm text-primary hover:underline">View in approvals queue →</Link>
                </div>
              </Card>
            )}
          </div>

          <div className="space-y-4">
            <Card title="Metadata">
              {data.current ? (
                <dl className="text-sm space-y-1.5">
                  <Row k="Domain" v={<span className="font-mono">{data.current.domain}</span>} />
                  <Row k="Key" v={<span className="font-mono">{data.current.key}</span>} />
                  <Row k="Scope" v={<ScopeBadge scope={data.current.scope} />} />
                  <Row k="Status" v={<StatusBadge status={data.current.status} />} />
                  <Row k="Version" v={`v${data.current.version}`} />
                  <Row k="Hash" v={<span className="font-mono">{data.current.hash}</span>} />
                  <Row k="Updated" v={<span title={data.current.updatedAt}>{fmtDate(data.current.updatedAt)} · {relativeTime(data.current.updatedAt)}</span>} />
                  <Row k="Updated by" v={<span className="font-mono">{data.current.updatedBy}</span>} />
                  {data.current.effectiveFrom && <Row k="Effective from" v={fmtDate(data.current.effectiveFrom)} />}
                </dl>
              ) : <div className="text-sm text-muted-foreground">—</div>}
            </Card>

            <Card title="Version history">
              {data.history?.length ? (
                <ol className="text-sm space-y-1.5">
                  {data.history.map((h) => (
                    <li key={h.version} className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-mono">v{h.version}</span>
                        <StatusBadge status={h.status} />
                      </div>
                      <div className="text-xs text-muted-foreground">{fmtDate(h.updatedAt)}</div>
                    </li>
                  ))}
                </ol>
              ) : <div className="text-sm text-muted-foreground">No prior versions.</div>}
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <dt className="text-xs text-muted-foreground">{k}</dt>
      <dd className="text-right">{v}</dd>
    </div>
  );
}