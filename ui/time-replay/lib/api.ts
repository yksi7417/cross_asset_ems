import useSWR from 'swr';
import {
  ClusterTopology,
  CreateReplayRequest,
  CreateReplayResponse,
  EventFilter,
  EventListResponse,
  ReplaySession,
} from './types';

const DEFAULT_LIMIT = 100;

function buildQuery(filter: EventFilter): string {
  const params = new URLSearchParams();
  if (filter.stream) params.set('stream', filter.stream);
  if (filter.type) params.set('type', filter.type);
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  if (filter.node) params.set('node', filter.node);
  params.set('limit', String(filter.limit ?? DEFAULT_LIMIT));
  return params.toString();
}

export function useClusters(): {
  data: ClusterTopology | undefined;
  isLoading: boolean;
  error: Error | undefined;
} {
  const { data, error, isLoading } = useSWR<ClusterTopology>('/api/clusters', {
    refreshInterval: 5_000,
  });
  return { data, isLoading, error };
}

export function useReplays(): {
  data: ReplaySession[] | undefined;
  isLoading: boolean;
  error: Error | undefined;
  mutate: () => void;
} {
  const { data, error, isLoading, mutate } = useSWR<ReplaySession[]>('/api/replays', {
    refreshInterval: 3_000,
  });
  return { data, isLoading, error, mutate: () => void mutate() };
}

export function useReplay(id: string | null): {
  data: ReplaySession | undefined;
  isLoading: boolean;
  error: Error | undefined;
  mutate: () => void;
} {
  const { data, error, isLoading, mutate } = useSWR<ReplaySession>(
    id ? `/api/replays/${id}` : null,
    { refreshInterval: 2_000 },
  );
  return { data, isLoading, error, mutate: () => void mutate() };
}

export function useEvents(filter: EventFilter): {
  data: EventListResponse | undefined;
  isLoading: boolean;
  error: Error | undefined;
} {
  const qs = buildQuery(filter);
  const { data, error, isLoading } = useSWR<EventListResponse>(
    `/api/events?${qs}`,
    { refreshInterval: 4_000 },
  );
  return { data, isLoading, error };
}

export async function createReplay(
  payload: CreateReplayRequest,
): Promise<CreateReplayResponse> {
  const res = await fetch('/api/replays', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`createReplay failed: ${res.status} ${text}`);
  }
  return (await res.json()) as CreateReplayResponse;
}