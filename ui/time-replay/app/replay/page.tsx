"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { ReplaySession } from "@/lib/types";
import { createReplay, fetchReplays } from "@/lib/api";
import { formatDuration, formatRelativeTime } from "@/lib/format";
import { Card } from "@/components/Card";
import { StatusBadge } from "@/components/StatusBadge";
import { DEFAULT_CLUSTER, REPLAY_STREAMS } from "@/lib/constants";

function ReplayRow({ session }: { session: ReplaySession }) {
  const duration = session.completed_at
    ? formatDuration(
        new Date(session.started_at),
        new Date(session.completed_at)
      )
    : "running";

  return (
    <Link
      href={`/replay/${session.id}`}
      className="grid grid-cols-12 items-center gap-3 rounded-lg border border-zinc-800 bg-zinc-900/50 px-4 py-3 transition hover:border-zinc-700 hover:bg-zinc-900"
    >
      <div className="col-span-4">
        <div className="truncate text-sm font-medium text-zinc-100">
          {session.name}
        </div>
        <div className="truncate font-mono text-xs text-zinc-500">
          {session.id}
        </div>
      </div>
      <div className="col-span-2">
        <StatusBadge status={session.status} />
      </div>
      <div className="col-span-2 text-xs text-zinc-400">
        {formatRelativeTime(new Date(session.started_at))}
      </div>
      <div className="col-span-2 text-xs text-zinc-400">{duration}</div>
      <div className="col-span-2 text-right text-xs text-zinc-500">
        {session.event_count} events
      </div>
    </Link>
  );
}

type Filter = "all" | "running" | "completed" | "failed";

function NewReplayForm({
  onCreated,
}: {
  onCreated: (session: ReplaySession) => void;
}) {
  const [name, setName] = useState("");
  const [stream, setStream] = useState(REPLAY_STREAMS[0]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [speed, setSpeed] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const session = await createReplay({
        name,
        cluster_id: DEFAULT_CLUSTER,
        stream,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        speed,
      });
      onCreated(session);
      setName("");
      setFrom("");
      setTo("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create replay");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div>
        <label className="block text-xs uppercase tracking-wide text-zinc-500">
          Name
        </label>
        <input
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. checkout-flow-debug"
          className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 focus:border-zinc-600 focus:outline-none"
        />
      </div>
      <div>
        <label className="block text-xs uppercase tracking-wide text-zinc-500">
          Stream
        </label>
        <select
          value={stream}
          onChange={(e) => setStream(e.target.value)}
          className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 focus:border-zinc-600 focus:outline-none"
        >
          {REPLAY_STREAMS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs uppercase tracking-wide text-zinc-500">
            From
          </label>
          <input
            type="datetime-local"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 focus:border-zinc-600 focus:outline-none"
          />
        </div>
        <div>
          <label className="block text-xs uppercase tracking-wide text-zinc-500">
            To
          </label>
          <input
            type="datetime-local"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 focus:border-zinc-600 focus:outline-none"
          />
        </div>
      </div>
      <div>
        <label className="block text-xs uppercase tracking-wide text-zinc-500">
          Speed ({speed}x)
        </label>
        <input
          type="range"
          min={1}
          max={16}
          step={1}
          value={speed}
          onChange={(e) => setSpeed(Number(e.target.value))}
          className="mt-2 w-full accent-zinc-100"
        />
      </div>
      {error && <div className="text-xs text-red-400">{error}</div>}
      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-md bg-zinc-100 px-3 py-2 text-sm font-medium text-zinc-900 transition hover:bg-white disabled:opacity-50"
      >
        {submitting ? "Starting..." : "Start replay"}
      </button>
    </form>
  );
}

export default function ReplayPage() {
  const [sessions, setSessions] = useState<ReplaySession[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<Filter>("all");

  useEffect(() => {
    fetchReplays()
      .then(setSessions)
      .finally(() => setLoading(false));
  }, []);

  const visible = sessions.filter((s) =>
    filter === "all" ? true : s.status === filter
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-zinc-100">Replays</h1>
        <p className="text-sm text-zinc-500">
          Replay event streams from any point in history.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-3 lg:col-span-2">
          <div className="flex gap-2">
            {(["all", "running", "completed", "failed"] as const).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`rounded-md px-3 py-1 text-xs uppercase tracking-wide transition ${
                  filter === f
                    ? "bg-zinc-100 text-zinc-900"
                    : "bg-zinc-900 text-zinc-400 hover:text-zinc-100"
                }`}
              >
                {f}
              </button>
            ))}
          </div>
          {loading ? (
            <Card>
              <div className="text-sm text-zinc-500">Loading replays...</div>
            </Card>
          ) : visible.length === 0 ? (
            <Card>
              <div className="text-sm text-zinc-500">No replays found.</div>
            </Card>
          ) : (
            <div className="space-y-2">
              {visible.map((s) => (
                <ReplayRow key={s.id} session={s} />
              ))}
            </div>
          )}
        </div>
        <div>
          <Card title="New replay">
            <NewReplayForm
              onCreated={(s) => setSessions((prev) => [s, ...prev])}
            />
          </Card>
        </div>
      </div>
    </div>
  );
}