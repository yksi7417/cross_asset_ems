'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Activity, History, LayoutDashboard, Search, Server } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ComponentType } from 'react';

interface NavItem {
  href: string;
  label: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  match: (pathname: string) => boolean;
}

const items: NavItem[] = [
  {
    href: '/',
    label: 'Dashboard',
    icon: LayoutDashboard,
    match: (p) => p === '/',
  },
  {
    href: '/replay',
    label: 'Replays',
    icon: History,
    match: (p) => p === '/replay' || p.startsWith('/replay/'),
  },
  {
    href: '/events',
    label: 'Events',
    icon: Search,
    match: (p) => p.startsWith('/events'),
  },
  {
    href: '/clusters',
    label: 'Clusters',
    icon: Server,
    match: (p) => p.startsWith('/clusters'),
  },
];

export function Nav() {
  const pathname = usePathname();

  return (
    <aside className="w-60 shrink-0 border-r border-slate-800 bg-slate-900/40 flex flex-col">
      <div className="px-4 py-4 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Activity size={16} className="text-sky-400" />
          <div>
            <div className="text-sm font-semibold tracking-wide text-slate-100">
              TIME / REPLAY
            </div>
            <div className="text-[10px] text-slate-500 uppercase tracking-widest">
              cross-asset-ems
            </div>
          </div>
        </div>
      </div>

      <nav className="flex-1 px-2 py-3 space-y-0.5">
        {items.map(({ href, label, icon: Icon, match }) => {
          const active = match(pathname);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                'flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors',
                active
                  ? 'bg-slate-800 text-slate-100'
                  : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/50',
              )}
            >
              <Icon size={16} />
              <span>{label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="px-4 py-3 border-t border-slate-800 text-[10px] text-slate-500 space-y-1">
        <div className="flex items-center justify-between">
          <span>env</span>
          <span className="font-mono text-slate-300">prod-eu-1</span>
        </div>
        <div className="flex items-center justify-between">
          <span>build</span>
          <span className="font-mono text-slate-300">ems-3.14.2</span>
        </div>
        <div className="flex items-center justify-between">
          <span>console</span>
          <span className="font-mono text-slate-300">v1.0.0</span>
        </div>
      </div>
    </aside>
  );
}