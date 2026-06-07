import { cn } from '@/lib/utils';
import type { ReplayStatus, Role } from '@/lib/types';

type Status = ReplayStatus | Role | string;

const colorMap: Record<string, string> = {
  LEADER: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
  FOLLOWER: 'bg-sky-500/10 text-sky-300 border-sky-500/30',
  PASSIVE: 'bg-slate-500/10 text-slate-300 border-slate-500/30',
  LOG_REPLICATOR: 'bg-violet-500/10 text-violet-300 border-violet-500/30',
  PENDING: 'bg-slate-500/10 text-slate-300 border-slate-500/30',
  RUNNING: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30',
  COMPLETED: 'bg-sky-500/10 text-sky-300 border-sky-500/30',
  FAILED: 'bg-rose-500/10 text-rose-300 border-rose-500/30',
  ABORTED: 'bg-orange-500/10 text-orange-300 border-orange-500/30',
  WALL_CLOCK_FAST: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30',
  WALL_CLOCK_REALTIME: 'bg-sky-500/10 text-sky-300 border-sky-500/30',
  SIMULATED: 'bg-violet-500/10 text-violet-300 border-violet-500/30',
  PAUSED: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
};

export function StatusBadge({
  status,
  className,
  dot = true,
}: {
  status: Status;
  className?: string;
  dot?: boolean;
}) {
  const color = colorMap[status] ?? 'bg-slate-500/10 text-slate-300 border-slate-500/30';
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[10px] font-medium border font-mono uppercase tracking-wider',
        color,
        className,
      )}
    >
      {dot && <span className="w-1.5 h-1.5 rounded-full bg-current opacity-80" />}
      {status}
    </span>
  );
}