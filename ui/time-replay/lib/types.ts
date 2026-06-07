export type Role = 'LEADER' | 'FOLLOWER' | 'PASSIVE' | 'LOG_REPLICATOR';

export type ReplayStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'ABORTED';

export type ClockMode =
  | 'WALL_CLOCK_FAST'
  | 'WALL_CLOCK_REALTIME'
  | 'SIMULATED'
  | 'PAUSED';

export interface ClockTick {
  reference_time: string;
  publish_seq: number;
  offset_hint_ns: number;
}

export interface ReplayConfig {
  start_event_id: string;
  end_event_id: string;
  speed: number;
  clock_mode: ClockMode;
  rule_set_version: string;
  code_version_target: string;
}

export interface ClusterMember {
  node_id: string;
  host: string;
  port: number;
  role: Role;
  log_position: number;
  last_heartbeat_ms: number;
  lag_ms: number;
}

export interface ClusterTopology {
  cluster_id: string;
  leader_node_id: string;
  term: number;
  last_log_position: number;
  last_clock_tick: ClockTick;
  members: ClusterMember[];
}

export interface ReplaySession {
  id: string;
  name: string;
  status: ReplayStatus;
  config: ReplayConfig;
  started_at: string;
  completed_at: string | null;
  events_replayed: number;
  divergence_count: number;
  requested_by: string;
}

export interface ReplayEvent {
  event_id: string;
  stream: string;
  type: string;
  occurred_at: string;
  sequence: number;
  payload_preview: string;
  source_node: string;
}

export interface Divergence {
  sequence: number;
  type: 'payload_mismatch' | 'timestamp_skew' | 'missing_event' | 'extra_event';
  severity: 'low' | 'medium' | 'high';
  original: string;
  replayed: string;
  note: string;
}

export interface EventFilter {
  stream?: string;
  type?: string;
  from?: string;
  to?: string;
  node?: string;
  limit?: number;
}

export interface EventListResponse {
  events: ReplayEvent[];
  total: number;
  has_more: boolean;
  filter: EventFilter;
}

export interface CreateReplayRequest {
  name: string;
  start_event_id: string;
  end_event_id: string;
  speed: number;
  clock_mode: ClockMode;
  rule_set_version: string;
  code_version_target: string;
}

export interface CreateReplayResponse {
  session: ReplaySession;
}