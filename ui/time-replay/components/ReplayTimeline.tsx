'use client';

import { useMemo, useState } from 'react';
import { ReplayConfig, ReplaySession } from '@/lib/types';
import {
  Pause,
  Play,
  RotateCcw,
  SkipBack,
  SkipForward,
  Square,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface Marker {
  sequence: number;
  kind: 'divergence' | 'checkpoint' | 'snapshot';
  label?: string;
}

export function ReplayTimeline({
  session,
  config,
  totalEvents,
  startSeq,
  endSeq,
  markers,
}: {
  session: ReplaySession;
  config: ReplayConfig;
  totalEvents: number;
  startSeq: number;
  endSeq: number;
  markers: Marker[];
}) {
  const [playing, setPlaying] = useState(false);
  const [position, setPosition] = useState<number>(session.events_replayed);

  const span = Math.max(1, endSeq - startSeq);
  const progressPct = useMemo(
    () => Math.min(100, Math.max(0, ((position - startSeq) / span) * 100)),
    [position, startSeq, span],
  );

  const onScrub = (e: React.ChangeEvent<HTMLInputElement>) => {
    const pct = Number(e.target.value);
    setPosition(startSeq + Math.round((pct / 100) * span));
  };

  const stepBy = (n: number) => {
    setPosition((p) => Math.min(endSeq, Math.max(startSeq, p + n)));
  };

  return (
    <div>
      <div className="relative h-16 bg-slate-900 border border-slate-800 rounded overflow-hidden select-none">
        {/* Progress fill */}
        <div
          className="absolute inset-y-0 left-0 bg-sky-500/10"
          style={{ width: `${progressPct}%` }}
        />

        {/* Markers */}
        {markers.map((m, i) => {
          const pct = ((m.sequence - startSeq) / span) * 100;
          if (pct < 0 || pct > 100) return null;
          const color =
            m.kind === 'divergence'
              ? 'bg-rose-500'
              : m.kind === 'snapshot'
                ? 'bg-violet-500'
                : 'bg-amber-500';
          return (
            <div
              key={i}
              className={cn('absolute top-0 bottom-0 w-px', color)}
              style={{ left: `${pct}%` }}
              title={m.label ?? m.kind}
            />
          );
        })}

        {/* Start / end caps */}
        <div className="absolute top-0 bottom-0 left-0 w-1 bg-slate-700" />
        <div className="absolute top-0 bottom-0 right-0 w-1 bg-slate-700" />

        {/* Playhead */}
        <div
          className="absolute top-0 bottom-0 w-0.5 bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]"
          style={{ left: `${progressPct}%` }}
        />

        {/* Labels */}
        <div className="absolute top-1 left-2 text-[10px] font-mono text-slate-400">
          {startSeq.toLocaleString()}
        </div>
        <div className="absolute top-1 right-2 text-[10px] font-mono text-slate-400">
          {endSeq.toLocaleString()}
        </div>
        <div className="absolute bottom-1 left-2 text-[10px] font-mono text-slate-500">
          start
        </div>
        <div className="absolute bottom-1 right-2 text-[10px] font-mono text-slate-500">
          end
        </div>
      </div>

      {/* Scrubber */}
      <div className="mt-3">
        <input
          type="range"
          min={0}
          max={100}
          value={progressPct}
          onChange={onScrub}
          className="w-full h-1 bg-slate-800 rounded appearance-none cursor-pointer accent-sky-400"
        />
      </div>

      {/* Controls */}
      <div className="mt-3 flex items-center gap-2">
        <button
          onClick={() => setPosition(startSeq)}
          className="p-2 rounded border border-slate-800 hover:bg-slate-800/60 text-slate-300"
          title="restart"
        >
          <RotateCcw size={14} />
        </button>
        <button
          onClick={() => stepBy(-10)}
          className="p-2 rounded border border-slate-800 hover:bg-slate-800/60 text-slate-300"
          title="back 10"
        >
          <SkipBack size={14} />
        </button>
        <button
          onClick={() => setPlaying((p) => !p)}
          className={cn(
            'p-2 rounded border text-white',
            playing
              ? 'bg-amber-500/20 border-amber-500/40 text-amber-200'
              : 'bg-emerald-500/20 border-emerald-500/40 text-emerald-200',
          )}
          title={playing ? 'pause' : 'play'}
        >
          {playing ? <Pause size={14} /> : <Play size={14} />}
        </button>
        <button
          onClick={() => stepBy(10)}
          className="p-2 rounded border border-slate-800 hover:bg-slate-800/60 text-slate-300"
          title="forward 10"
        >
          <SkipForward size={14} />
        </button>
        <button
          onClick={() => {
            setPlaying(false);
            setPosition(endSeq);
          }}
          className="p-2 rounded border border-slate-800 hover:bg-slate-800/60 text-slate-300"
          title="stop"
        >
          <Square size={14} />
        </button>

        <div className="ml-4 text-xs text-slate-400 font-mono">
          {position.toLocaleString()} / {endSeq.toLocaleString()} · {totalEvents}{' '}
          events replayed
        </div>

        <div className="ml-auto text-[11px] text-slate-500 font-mono">
          speed: {config.speed}x · {config.clock_mode}
        </div>
      </div>
    </div>
  );
}