import { ClusterMember } from '@/lib/types';
import { StatusBadge } from './StatusBadge';
import { cn } from '@/lib/utils';
import { Server } from 'lucide-react';

export function ClusterStatusCard({
  member,
  isLeader,
  compact = false,
}: {
  member: ClusterMember;
  isLeader: boolean;
  compact?: boolean;
}) {
  const lagClass =
    member.lag_ms === 0
      ? 'text-emerald-300'
      : member.lag_ms < 50
        ? 'text-slate-200'
        : member.lag_ms < 500
          ? 'text-amber-300'
          : 'text-rose-300';

  return (
    <div
      className={cn(
        'rounded-lg border bg-slate-900/50 p-4 transition-colors',
        isLeader ? 'border-amber-500/40' : 'border-slate-800',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Server size={14} className="text-slate-500 shrink-0" />
            <div className="text-sm font-mono font-semibold truncate">
              {member.node_id}
            </div>
          </div>
          <div className="text-[11px] text-slate-500 font-mono mt-1">
            {member.host}:{member.port}
          </div>
        </div>
        <StatusBadge status={member.role} />
      </div>

      {!compact && (
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 text-xs mt-4">
          <dt className="text-slate-500">log pos</dt>
          <dd className="font-mono text-right text-slate-200">
            {member.log_position.toLocaleString()}
          </dd>
          <dt className="text-slate-500">heartbeat</dt>
          <dd className="font-mono text-right text-slate-200">
            {member.last_heartbeat_ms} ms
          </dd>
          <dt className="text-slate-500">lag</dt>
          <dd className={cn('font-mono text-right', lagClass)}>
            {member.lag_ms} ms
          </dd>
        </dl>
      )}

      {compact && (
        <div className="mt-3 flex items-center justify-between text-[11px] font-mono">
          <span className="text-slate-500">lag</span>
          <span className={lagClass}>{member.lag_ms} ms</span>
        </div>
      )}
    </div>
  );
}