"use client";

import { useEffect, useMemo, useState } from "react";
import { ClusterInfo } from "@/lib/types";
import { fetchClusters } from "@/lib/api";
import { Card } from "@/components/Card";
import { StatCard } from "@/components/StatCard";
import { ClusterStatusCard } from "@/components/ClusterStatusCard";
import { StatusBadge } from "@/components/StatusBadge";

export default function ClustersPage() {
  const [clusters, setClusters] = useState<ClusterInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    fetchClusters()
      .then((c) => {
        setClusters(c);
        setSelectedId(c[0]?.id ?? null);
      })
      .finally(() => setLoading(false));
  }, []);

  const stats = useMemo(() => {
    const all = clusters.flatMap((c) => c.members);
    return {
      clusters: clusters.length,
      members: all.length,
      healthy: all.filter((m) => m.status === "healthy").length,
      degraded: all.filter((m) => m.status === "degraded").length,
      offline: all.filter((m) => m.status === "offline").length,
    };
  }, [clusters]);

  if (loading) {
    return <div className="text-sm text-zinc-500">Loading clusters...</div>;
  }

  const selected = clusters.find((c) => c.id === selectedId) ?? clusters[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-zinc-100">Clusters</h1>
        <p className="text-sm text-zinc-500">
          Cluster topology and member health across regions.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        <StatCard label="Clusters" value={stats.clusters} />
        <StatCard label="Members" value={stats.members} />
        <StatCard label="Healthy" value={stats.healthy} tone="ok" />
        <StatCard
          label="Degraded"
          value={stats.degraded}
          tone={stats.degraded > 0 ? "warn" : "ok"}
        />
        <StatCard
          label="Offline"
          value={stats.offline}
          tone={stats.offline > 0 ? "error" : "ok"}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-2">
          {clusters.map((c) => (
            <button
              key={c.id}
              onClick={() => setSelectedId(c.id)}
              className={`w-full rounded-lg border px-4 py-3 text-left transition ${
                selected?.id === c.id
                  ? "border-zinc-600 bg-zinc-900"
                  : "border-zinc-800 bg-zinc-900/40 hover:border-zinc-700"
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="text-sm font-medium text-zinc-100">
                  {c.name}
                </div>
                <StatusBadge status={c.status} />
              </div>
              <div className="mt-1 text-xs text-zinc-500">
                {c.members.length} members · leader {c.leader} ·{" "}
                {c.region ?? "—"}
              </div>
            </button>
          ))}
        </div>
        <div className="lg:col-span-2 space-y-4">
          {selected ? (
            <>
              <Card title={`${selected.name} members`}>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {selected.members.map((m) => (
                    <ClusterStatusCard key={m.id} member={m} />
                  ))}
                </div>
              </Card>
              <Card title="Topology">
                <div className="flex flex-wrap items-center gap-3 text-xs text-zinc-400">
                  <div className="rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2">
                    <div className="text-zinc-500">leader</div>
                    <div className="font-mono text-zinc-200">
                      {selected.leader}
                    </div>
                  </div>
                  {selected.members
                    .filter((m) => m.id !== selected.leader)
                    .map((m) => (
                      <div
                        key={m.id}
                        className="rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2"
                      >
                        <div className="text-zinc-500">{m.role}</div>
                        <div className="font-mono text-zinc-200">{m.id}</div>
                      </div>
                    ))}
                </div>
              </Card>
            </>
          ) : (
            <Card>
              <div className="text-sm text-zinc-500">No cluster selected.</div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}