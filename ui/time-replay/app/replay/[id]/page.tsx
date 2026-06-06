"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ReplayEvent, ReplaySession } from "@/lib/types";
import { fetchReplay, fetchReplayEvents } from "@/lib/api";
import { Card } from "@/components/Card";
import { StatusBadge } from "@/components/StatusBadge";
import { ReplayTimeline } from "@/components/ReplayTimeline";
import { EventStream } from "@/components/EventStream";
import { formatAbsolute, formatDuration } from "@/lib/format";

type Tab = "stream" | "diff" | "metadata";

export default function ReplayDetailPage() {
  const params = useParams<{ id: string }>();
  const [session, setSession] = useState<ReplaySession | null>(null);
  const [events, setEvents] = useState<ReplayEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("stream");
  const [selected, setSelected] = useState<ReplayEvent | null>(null);

  useEffect(() => {
    if (!params?.id) return;
    Promise.all([fetchReplay(params.id), fetchReplayEvents(params.id)])
      .then(([s, e]) => {
        setSession(s);
        setEvents(e);
        setSelected(e[0] ?? null);
      })
      .finally(() => setLoading(false));
  }, [params?.id]);

  if (loading) {
    return <div className="text-sm text-zinc-500">Loading replay...</div>;
  }
  if (!session) {
    return (
      <div className="space-y-2">
        <Link
          href="/replay"
          className="text-xs uppercase tracking-wide text-zinc-500 hover:text-zinc-300"
        >
          ← Replays
        </Link>
        <div className="text-sm text-zinc-500">Replay not found.</div>
      </div>
    );
  }

  const duration = session.completed_at
    ? formatDuration(
        new Date(session.started_at),
        new Date(session.completed_at)
      )
    : "in progress";

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <Link
            href="/replay"
            className="text-xs uppercase tracking-wide text-zinc-500 hover:text-zinc-300"
          >
            ← Replays
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-zinc-100">
            {session.name}
          </h1>
          <p className="font-mono text-xs text-zinc-500">{session.id}</p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <StatusBadge status={session.status} />
          <span className="text-xs text-zinc-500">{duration}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Card title="Started">
          <div className="text-sm text-zinc-200">
            {formatAbsolute(new Date(session.started_at))}
          </div>
        </Card>
        <Card title="Completed">
          <div className="text-sm text-zinc-200">
            {session.completed_at
              ? formatAbsolute(new Date(session.completed_at))
              : "—"}
          </div>
        </Card>
        <Card title="Events">
          <div className="text-sm text-zinc-200">{events.length}</div>
        </Card>
        <Card title="Cluster">
          <div className="font-mono text-sm text-zinc-200">
            {session.cluster_id}
          </div>
        </Card>
      </div>

      <Card title="Timeline">
        <ReplayTimeline events={events} />
      </Card>

      <div className="flex gap-2">
        {(["stream", "diff", "metadata"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-md px-3 py-1 text-xs uppercase tracking-wide transition ${
              tab === t
                ? "bg-zinc-100 text-zinc-900"
                : "bg-zinc-900 text-zinc-400 hover:text-zinc-100"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {tab === "stream" && (
            <Card title="Event stream">
              <EventStream
                events={events}
                selectedId={selected?.id}
                onSelect={setSelected}
              />
            </Card>
          )}
          {tab === "diff" && (
            <Card title="Diff view">
              {selected ? (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <div className="mb-2 text-xs uppercase tracking-wide text-zinc-500">
                      Before
                    </div>
                    <pre className="overflow-auto rounded-md border border-zinc-800 bg-zinc-950 p-3 text-xs text-zinc-300">
                      {JSON.stringify(selected.before ?? {}, null, 2)}
                    </pre>
                  </div>
                  <div>
                    <div className="mb-2 text-xs uppercase tracking-wide text-zinc-500">
                      After
                    </div>
                    <pre className="overflow-auto rounded-md border border-zinc-800 bg-zinc-950 p-3 text-xs text-zinc-300">
                      {JSON.stringify(selected.after ?? {}, null, 2)}
                    </pre>
                  </div>
                </div>
              ) : (
                <div className="text-sm text-zinc-500">
                  Select an event to see its diff.
                </div>
              )}
            </Card>
          )}
          {tab === "metadata" && (
            <Card title="Metadata">
              <pre className="overflow-auto text-xs text-zinc-300">
                {JSON.stringify(session, null, 2)}
              </pre>
            </Card>
          )}
        </div>
        <div>
          <Card title="Selected event">
            {selected ? (
              <div className="space-y-2 text-xs text-zinc-300">
                <div>
                  <span className="text-zinc-500">type:</span> {selected.type}
                </div>
                <div>
                  <span className="text-zinc-500">stream:</span>{" "}
                  {selected.stream}
                </div>
                <div>
                  <span className="text-zinc-500">time:</span>{" "}
                  {formatAbsolute(new Date(selected.timestamp))}
                </div>
                <div>
                  <span className="text-zinc-500">id:</span>{" "}
                  <span className="font-mono">{selected.id}</span>
                </div>
              </div>
            ) : (
              <div className="text-xs text-zinc-500">No event selected.</div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}