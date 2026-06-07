import { ReactNode } from 'react';
import { Card } from './Card';
import { cn } from '@/lib/utils';

type Accent = 'default' | 'success' | 'warning' | 'danger' | 'info';

const accentClass: Record<Accent, string> = {
  default: 'text-slate-100',
  success: 'text-emerald-300',
  warning: 'text-amber-300',
  danger: 'text-rose-300',
  info: 'text-sky-300',
};

export function StatCard({
  label,
  value,
  sub,
  icon,
  accent = 'default',
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon?: ReactNode;
  accent?: Accent;
}) {
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] text-slate-400 uppercase tracking-widest font-medium">
          {label}
        </span>
        {icon && <span className="text-slate-500">{icon}</span>}
      </div>
      <div className={cn('text-2xl font-semibold mt-2 font-mono', accentClass[accent])}>
        {value}
      </div>
      {sub && <div className="text-xs text-slate-500 mt-1">{sub}</div>}
    </Card>
  );
}