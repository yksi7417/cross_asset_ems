"use client";

import { FormEvent, useEffect, useState } from "react";
import { ReplayEvent } from "@/lib/types";
import { searchEvents } from "@/lib/api";
import { Card } from "@/components/Card";
import { EventStream } from "@/components/EventStream";
import { EVENT_STREAMS, EVENT_TYPES } from "@/lib/constants";

export default function EventsPage() {
  const [stream, setStream] = useState<string>("");
  const [type, setType] = useState<string>("");
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");
  const [query, setQuery] = useState<string>("");
  const [events, setEvents] = useState<ReplayEvent[]>([]);
  const [searching, setSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  useEffect(() => {
    runSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function runSearch(e?: FormEvent) {
    e?.preventDefault();
    setSearching(true);
    try {
      const result = await searchEvents({
        stream: stream || undefined,
        type: type || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        query: query || undefined,
      });
      setEvents(result);
      setHasSearched(true);
    } finally {
      setSearching(false);
    }
  }

  function reset() {
    setStream("");
    setType("");
    setFrom("");
    setTo("");
    setQuery("");
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-zinc-100">Events</h1>
        <p className="text-sm text-zinc-500">
          Search the event store across streams, types, and time.
        </p>
      </div>

      <Card title="Filters">
        <form
          onSubmit={runSearch}
          className="grid grid-cols-1 gap-3 sm:grid-cols-6"
        >
          <div className="sm:col-span-2">
            <label className="block text-xs uppercase tracking-wide text-zinc-500">
              Stream
            </label>
            <select
              value={stream}
              onChange={(e) => setStream(e.target.value)}
              className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100"
            >
              <option value="">Any</option>
              {EVENT_STREAMS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs uppercase tracking-wide text-zinc-500">
              Type
            </label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100"
            >
              <option value="">Any</option>
              {EVENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-zinc-500">
              From
            </label>
            <input
              type="datetime-local"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100"
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
              className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100"
            />
          </div>
          <div className="sm:col-span-5">
            <label className="block text-xs uppercase tracking-wide text-zinc-500">
              Payload contains
            </label>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="e.g. user_id=42"
              className="mt-1 w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-zinc-100"
            />
          </div>
          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={reset}
              className="w-full rounded-md border border-zinc-800 bg-zinc-900 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
            >
              Reset
            </button>
            <button
              type="submit"
              disabled={searching}
              className="w-full rounded-md bg-zinc-100 px-3 py-2 text-sm font-medium text-zinc-900 transition hover:bg-white disabled:opacity-50"
            >
              {searching ? "Searching..." : "Search"}
            </button>
          </div>
        </form>
      </Card>

      <Card title={`Results (${events.length})`}>
        {hasSearched && events.length === 0 ? (
          <div className="text-sm text-zinc-500">
            No events match the filters.
          </div>
        ) : (
          <EventStream events={events} />
        )}
      </Card>
    </div>
  );
}