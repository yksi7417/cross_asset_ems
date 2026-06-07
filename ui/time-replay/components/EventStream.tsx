import { ReplayEvent } from '@/lib/types';
import { formatTimeOfDay } from '@/lib/format';
import { Inbox } from 'lucide-react';

function streamColor(stream: string): string {
  if (stream.startsWith('orders')) return 'text-sky-300';
  if (stream.startsWith('fills')) return 'text-emerald-300';
  if (stream.startsWith('config')) return 'text-violet-300';
  if (stream.startsWith('marketdata')) return 'text-amber-300';
  if (stream.startsWith('audit')) return 'text-rose-300';
  if (stream.startsWith('system')) return 'text-slate-400';
  return 'text-slate-300';
}

export function EventStream({
  events,
  maxHeight = 600,
  showPayload = true,
  emptyMessage = 'No events match the current filter.',
}: {
  events: ReplayEvent[];
  maxHeight?: number;
  showPayload?: boolean;
  emptyMessage?: string;
}) {
  if (events.length === 0) {
    return (
      <div className="border border-dashed border-slate-800 rounded-lg p-8 flex flex-col items-center justify-center text-slate-500 text-sm">
        <Inbox size={20} className="mb-2" />
        {emptyMessage}
      </div>
    );
  }

  return (
    <div
      className="border border-slate-800 rounded-lg overflow-hidden bg-slate-900/30"
      style={{ maxHeight }}
    >
      <div className="overflow-auto scrollbar-thin" style={{ maxHeight }}>
        <table className="w-full text-xs">
          <thead className="bg-slate-900/80 sticky top-0 z-10">
            <tr className="border-b border-slate-800">
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-24">
                seq
              </th>
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-40">
                event_id
              </th>
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-40">
                type
              </th>
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-32">
                stream
              </th>
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-28">
                occurred
              </th>
              <th className="text-left px-3 py-2 font-medium text-slate-400 w-36">
                source
              </th>
              {showPayload && (
                <th className="text-left px-3 py-2 font-medium text-slate-400">
                  payload
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr
                key={e.event_id}
                className="border-b border-slate-800/50 hover:bg-slate-900/50"
              >
                <td className="px-3 py-1.5 font-mono text-slate-400">
                  {e.sequence.toLocaleString()}
                </td>
                <td className="px-3 py-1.5 font-mono text-slate-200">
                  {e.event_id}
                </td>
                <td className="px-3 py-1.5 text-slate-200 font-mono">
                  {e.type}
                </td>
                <td className={`px-3 py-1.5 font-mono ${streamColor(e.stream)}`}>
                  {e.stream}
                </td>
                <td className="px-3 py-1.5 font-mono text-slate-400">
                  {formatTimeOfDay(e.occurred_at)}
                </td>
                <td className="px-3 py-1.5 font-mono text-slate-400 truncate max-w-[10rem]">
                  {e.source_node}
                </td>
                {showPayload && (
                  <td className="px-3 py-1.5 font-mono text-slate-500 truncate max-w-[28rem]">
                    {e.payload_preview}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}