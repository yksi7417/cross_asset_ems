'use client';

import Link from 'next/link';
import { useClusters, useEvents, useReplays } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/Card';
import { StatCard } from '@/components/StatCard';
import { StatusBadge } from '@/components/StatusBadge';
import { formatDateTime, formatNumber } from '@/lib/format';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Clock,
  Database,
  Server,
  Users,
} from 'lucide-react';
import { Loader2 } from 'lucide-react';

function LoadingBlock({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2 text-slate-400 text-sm p-4">
      <Loader2 size={14} className="animate-spin" />
      {label}
    </div>
  );
}

function ErrorBlock({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-2 text-rose-300 text-sm p-4 border border-rose-500/30 rounded bg-rose-500/5">
      <AlertTriangle size={14} />
      {message}
    </div>
  );
}

export default function DashboardPage() {
  const { data: cluster, isLoading: cLoading, error: cError } = useClusters();
  const { data: replays, isLoading: rLoading, error: rError } = useReplays();
  const { data: events, isLoading: eLoading, error: eError } = useEvents({
    limit: 1,
  });

  const followers = cluster?.members.filter((m) => m.role === 'FOLLOWER') ?? [];
  const logReplicators = cluster?.members.filter((m) => m.role === 'LOG_REPLICATOR') ?? [];
  const recent = (replays ?? [])
    .slice()
    .sort((a, b) => (a.started_at < b.started_at ? 1 : -1))
    .slice(0, 5);

  const runningCount = replays?.filter((r) => r.status === 'RUNNING').length ?? 0;
  const failedCount = replays?.filter((r) => r.status === 'FAILED').length ?? 0;

  return (
    <div className="p-6 space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-semibold">Dashboard</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Cross-cluster view of the Time/Replay Server. Auto-refreshes every
            few seconds.
          </p>
        </div>
        <div className="text-xs text-slate-500 font-mono">
          ref · {formatDateTime(cluster?.last_clock_tick.reference_time ?? new Date().toISOString())}
        </div>
      </header>

      <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Cluster members"
          value={cluster ? cluster.members.length : '—'}
          sub={cluster ? cluster.cluster_id : ''}
          icon={<Server size={14} />}
        />
        <StatCard
          label="Last log position"
          value={cluster ? formatNumber(cluster.last_log_position) : '—'}
          sub={cluster ? `term ${cluster.term} · ${cluster.leader_node_id}` : ''}
          icon={<Database size={14} />}
          accent="info"
        />
        <StatCard
          label="Active replays"
          value={runningCount}
          sub={replays ? `${replays.length} total` : ''}
          icon={<Activity size={14} />}
          accent={runningCount > 0 ? 'success' : 'default'}
        />
        <StatCard
          label="Failed replays (all time)"
          value={failedCount}
          sub={replays ? `${replays.length} sessions` : ''}
          icon={<AlertTriangle size={14} />}
          accent={failedCount > 0 ? 'danger' : 'default'}
        />
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Cluster status</CardTitle>
            <CardDescription>
              {cluster
                ? `Leader: ${cluster.leader_node_id} · ${followers.length} follower(s) · ${logReplicators.length} log replicator(s)`
                : ''}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {cError && <ErrorBlock message={`Failed to load cluster: ${cError.message}`} />}
            {cLoading && !cError && <LoadingBlock label="Loading cluster topology…" />}
            {cluster && (
              <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                {cluster.members.map((m) => (
                  <div
                    key={m.node_id}
                    className="rounded border border-slate-800 bg-slate-900/50 p-3"
                  >
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-mono font-semibold truncate">
                        {m.node_id}
                      </div>
                      <StatusBadge status={m.role} />
                    </div>
                    <div className="mt-2 flex justify-between text-[11px] font-mono text-slate-400">
                      <span>pos {m.log_position.toLocaleString()}</span>
                      <span
                        className={
                          m.lag_ms === 0
                            ? 'text-emerald-300'
                            : m.lag_ms < 50
                              ? 'text-slate-200'
                              : m.lag_ms < 500
                                ? 'text-amber-300'
                                : 'text-rose-300'
                        }
                      >
                        lag {m.lag_ms}ms
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Last clock tick</CardTitle>
            <CardDescription>Cluster leader&apos;s PTP time</CardDescription>
          </CardHeader>
          <CardContent>
            {cluster && (
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-[10px] text-slate-500 uppercase tracking-widest">
                    Reference
                  </dt>
                  <dd className="font-mono text-slate-200">
                    {formatDateTime(cluster.last_clock_tick.reference_time)}
                  </dd>
                </div>
                <div>
                  <dt className="text-[10px] text-slate-500 uppercase tracking-widest">
                    Publish seq
                  </dt>
                  <dd className="font-mono text-slate-200">
                    {cluster.last_clock_tick.publish_seq.toLocaleString()}
                  </dd>
                </div>
                <div>
                  <dt className="text-[10px] text-slate-500 uppercase tracking-widest">
                    Offset hint
                  </dt>
                  <dd
                    className={
                      cluster.last_clock_tick.offset_hint_ns === 0
                        ? 'font-mono text-emerald-300'
                        : 'font-mono text-amber-300'
                    }
                  >
                    {cluster.last_clock_tick.offset_hint_ns} ns
                  </dd>
                </div>
              </dl>
            )}
            {!cluster && cLoading && <LoadingBlock label="Loading…" />}
          </CardContent>
        </Card>
      </section>

      <section>
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <CardTitle>Recent replay sessions</CardTitle>
              <CardDescription>Newest 5 sessions across all requesters</CardDescription>
            </div>
            <Link
              href="/replay"
              className="text-xs text-sky-400 hover:text-sky-300 flex items-center gap-1"
            >
              view all <ArrowRight size={12} />
            </Link>
          </CardHeader>
          <CardContent>
            {rError && <ErrorBlock message={`Failed to load replays: ${rError.message}`} />}
            {rLoading && !rError && <LoadingBlock label="Loading replays…" />}
            {replays && (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[10px] text-slate-500 uppercase tracking-widest">
                    <th className="text-left py-2 font-medium">id</th>
                    <th className="text-left py-2 font-medium">name</th>
                    <th className="text-left py-2 font-medium">status</th>
                    <th className="text-left py-2 font-medium">speed</th>
                    <th className="text-right py-2 font-medium">events</th>
                    <th className="text-right py-2 font-medium">divergence</th>
                    <th className="text-left py-2 font-medium">started</th>
                  </tr>
                </thead>
                <tbody>
                  {recent.map((s) => (
                    <tr
                      key={s.id}
                      className="border-t border-slate-800/60 hover:bg-slate-900/40"
                    >
                      <td className="py-2 pr-3 font-mono text-xs text-slate-300">
                        <Link
                          href={`/replay/${s.id}`}
                          className="hover:text-sky-300"
                        >
                          {s.id}
                        </Link>
                      </td>
                      <td className="py-2 pr-3 text-slate-200">{s.name}</td>
                      <td className="py-2 pr-3">
                        <StatusBadge status={s.status} />
                      </td>
                      <td className="py-2 pr-3 font-mono text-xs text-slate-400">
                        {s.config.speed}x
                      </td>
                      <td className="py-2 pr-3 font-mono text-xs text-right text-slate-300">
                        {s.events_replayed.toLocaleString()}
                      </td>
                      <td
                        className={
                          s.divergence_count === 0
                            ? 'py-2 pr-3 font-mono text-xs text-right text-emerald-300'
                            : 'py-2 pr-3 font-mono text-xs text-right text-rose-300'
                        }
                      >
                        {s.divergence_count}
                      </td>
                      <td className="py-2 font-mono text-xs text-slate-400">
                        {formatDateTime(s.started_at)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardContent>
        </Card>
      </section>

      <section>
        <Card>
          <CardHeader>
            <CardTitle>Log position timeline</CardTitle>
            <CardDescription>
              Cluster-wide last log position (events) and approximate replay
              queue
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between text-xs text-slate-400">
              <div className="flex items-center gap-2">
                <Clock size={12} />
                <span>
                  last position{' '}
                  <span className="font-mono text-slate-200">
                    {cluster ? formatNumber(cluster.last_log_position) : '—'}
                  </span>
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Users size={12} />
                <span>
                  visible events{' '}
                  <span className="font-mono text-slate-200">
                    {eLoading ? '…' : formatNumber(events?.total ?? 0)}
                  </span>
                </span>
              </div>
            </div>
            <div className="mt-4 h-2 bg-slate-800 rounded overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-sky-500 to-emerald-400"
                style={{
                  width: cluster
                    ? `${Math.min(100, (cluster.last_log_position % 1000) / 10)}%`
                    : '0%',
                }}
              />
            </div>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}