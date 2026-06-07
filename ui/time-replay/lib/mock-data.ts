import {
  ClusterMember,
  ClusterTopology,
  ClockTick,
  Divergence,
  ReplayEvent,
  ReplaySession,
} from './types';

// Fixed reference "now" so that relative displays are deterministic.
export const MOCK_NOW = '2024-09-23T14:32:18.451Z';

export const mockClockTick: ClockTick = {
  reference_time: MOCK_NOW,
  publish_seq: 1_840_293,
  offset_hint_ns: -12_500,
};

export const mockClusterMembers: ClusterMember[] = [
  {
    node_id: 'ems-core-01.lon',
    host: '10.42.1.11',
    port: 5540,
    role: 'LEADER',
    log_position: 1_840_293,
    last_heartbeat_ms: 12,
    lag_ms: 0,
  },
  {
    node_id: 'ems-core-02.lon',
    host: '10.42.1.12',
    port: 5540,
    role: 'FOLLOWER',
    log_position: 1_840_292,
    last_heartbeat_ms: 24,
    lag_ms: 8,
  },
  {
    node_id: 'ems-core-03.lon',
    host: '10.42.1.13',
    port: 5540,
    role: 'FOLLOWER',
    log_position: 1_840_290,
    last_heartbeat_ms: 41,
    lag_ms: 24,
  },
  {
    node_id: 'ems-core-04.fra',
    host: '10.42.2.11',
    port: 5540,
    role: 'FOLLOWER',
    log_position: 1_840_288,
    last_heartbeat_ms: 58,
    lag_ms: 42,
  },
  {
    node_id: 'ems-core-05.fra',
    host: '10.42.2.12',
    port: 5540,
    role: 'FOLLOWER',
    log_position: 1_840_289,
    last_heartbeat_ms: 35,
    lag_ms: 18,
  },
  {
    node_id: 'ems-log-01.nyc',
    host: '10.50.1.11',
    port: 5560,
    role: 'LOG_REPLICATOR',
    log_position: 1_840_293,
    last_heartbeat_ms: 22,
    lag_ms: 0,
  },
  {
    node_id: 'ems-log-02.nyc',
    host: '10.50.1.12',
    port: 5560,
    role: 'LOG_REPLICATOR',
    log_position: 1_840_293,
    last_heartbeat_ms: 28,
    lag_ms: 0,
  },
  {
    node_id: 'ems-archive-01.ams',
    host: '10.60.1.11',
    port: 5580,
    role: 'PASSIVE',
    log_position: 1_835_000,
    last_heartbeat_ms: 1_240,
    lag_ms: 5_293,
  },
];

export const mockClusterTopology: ClusterTopology = {
  cluster_id: 'ems-prod-eu-1',
  leader_node_id: 'ems-core-01.lon',
  term: 47,
  last_log_position: 1_840_293,
  last_clock_tick: mockClockTick,
  members: mockClusterMembers,
};

// Mutable in-memory store so POST /api/replays persists within a dev session.
export const mockReplaySessions: ReplaySession[] = [
  {
    id: 'rpl-2024-09-23-001',
    name: 'london-open-bug-repro',
    status: 'RUNNING',
    config: {
      start_event_id: 'evt-1_839_900',
      end_event_id: 'evt-1_840_100',
      speed: 50,
      clock_mode: 'WALL_CLOCK_FAST',
      rule_set_version: 'rv-2024.09.18',
      code_version_target: 'ems-3.14.2',
    },
    started_at: '2024-09-23T08:14:22.000Z',
    completed_at: null,
    events_replayed: 142,
    divergence_count: 3,
    requested_by: 'a.morgan@desk.lon',
  },
  {
    id: 'rpl-2024-09-22-014',
    name: 'mifid-ii-tca-regression',
    status: 'COMPLETED',
    config: {
      start_event_id: 'evt-1_820_000',
      end_event_id: 'evt-1_821_500',
      speed: 100,
      clock_mode: 'WALL_CLOCK_FAST',
      rule_set_version: 'rv-2024.09.15',
      code_version_target: 'ems-3.14.2',
    },
    started_at: '2024-09-22T17:45:11.000Z',
    completed_at: '2024-09-22T17:52:48.000Z',
    events_replayed: 1_500,
    divergence_count: 0,
    requested_by: 'compliance@ems.lon',
  },
  {
    id: 'rpl-2024-09-22-013',
    name: 'fx-rollover-audit',
    status: 'COMPLETED',
    config: {
      start_event_id: 'evt-1_815_200',
      end_event_id: 'evt-1_815_900',
      speed: 25,
      clock_mode: 'SIMULATED',
      rule_set_version: 'rv-2024.09.15',
      code_version_target: 'ems-3.14.1',
    },
    started_at: '2024-09-22T21:00:00.000Z',
    completed_at: '2024-09-22T21:01:12.000Z',
    events_replayed: 700,
    divergence_count: 2,
    requested_by: 's.ito@fx.lon',
  },
  {
    id: 'rpl-2024-09-22-012',
    name: 'nyc-options-expiry-test',
    status: 'FAILED',
    config: {
      start_event_id: 'evt-1_810_000',
      end_event_id: 'evt-1_810_800',
      speed: 200,
      clock_mode: 'WALL_CLOCK_FAST',
      rule_set_version: 'rv-2024.09.14',
      code_version_target: 'ems-3.14.0',
    },
    started_at: '2024-09-22T15:00:08.000Z',
    completed_at: '2024-09-22T15:00:31.000Z',
    events_replayed: 412,
    divergence_count: 17,
    requested_by: 'j.chen@desk.nyc',
  },
  {
    id: 'rpl-2024-09-21-011',
    name: 'risk-limit-bump-validation',
    status: 'ABORTED',
    config: {
      start_event_id: 'evt-1_800_100',
      end_event_id: 'evt-1_801_000',
      speed: 10,
      clock_mode: 'WALL_CLOCK_REALTIME',
      rule_set_version: 'rv-2024.09.12',
      code_version_target: 'ems-3.13.9',
    },
    started_at: '2024-09-21T11:22:00.000Z',
    completed_at: '2024-09-21T11:24:30.000Z',
    events_replayed: 88,
    divergence_count: 0,
    requested_by: 'r.kumar@risk.lon',
  },
  {
    id: 'rpl-2024-09-20-010',
    name: 'eod-snapshot-replay',
    status: 'PENDING',
    config: {
      start_event_id: 'evt-1_790_000',
      end_event_id: 'evt-1_790_500',
      speed: 1,
      clock_mode: 'WALL_CLOCK_REALTIME',
      rule_set_version: 'rv-2024.09.18',
      code_version_target: 'ems-3.14.2',
    },
    started_at: '2024-09-20T20:00:00.000Z',
    completed_at: null,
    events_replayed: 0,
    divergence_count: 0,
    requested_by: 'eod-batch@ems.lon',
  },
];

export const mockEvents: ReplayEvent[] = [
  { event_id: 'evt-1_840_001', stream: 'orders.lon', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:00.123Z', sequence: 1_840_001, payload_preview: '{order_id:ORD-99231, side:BUY, qty:250000, instr:GB00B03MLX29}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_002', stream: 'orders.lon', type: 'OrderRouted', occurred_at: '2024-09-23T14:30:00.131Z', sequence: 1_840_002, payload_preview: '{order_id:ORD-99231, venue:LSE-SETS, ack:OK}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_003', stream: 'orders.lon', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:00.412Z', sequence: 1_840_003, payload_preview: '{order_id:ORD-99232, side:SELL, qty:100000, instr:GB00B03MLX29}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_004', stream: 'fills.lon', type: 'PartialFill', occurred_at: '2024-09-23T14:30:00.578Z', sequence: 1_840_004, payload_preview: '{order_id:ORD-99231, filled:120000, avg_px:248.55, leaves:130000}', source_node: 'ems-core-02.lon' },
  { event_id: 'evt-1_840_005', stream: 'system', type: 'Heartbeat', occurred_at: '2024-09-23T14:30:01.000Z', sequence: 1_840_005, payload_preview: '{node:ems-core-01.lon, term:47, leader:true}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_006', stream: 'orders.nyc', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:01.222Z', sequence: 1_840_006, payload_preview: '{order_id:ORD-99233, side:BUY, qty:500, instr:US0378331005}', source_node: 'ems-core-03.lon' },
  { event_id: 'evt-1_840_007', stream: 'orders.nyc', type: 'OrderRouted', occurred_at: '2024-09-23T14:30:01.241Z', sequence: 1_840_007, payload_preview: '{order_id:ORD-99233, venue:NASDAQ, ack:OK}', source_node: 'ems-core-03.lon' },
  { event_id: 'evt-1_840_008', stream: 'system', type: 'ClockTick', occurred_at: '2024-09-23T14:30:01.500Z', sequence: 1_840_008, payload_preview: '{seq:1840008, offset_ns:-12500}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_009', stream: 'fills.lon', type: 'FillReceived', occurred_at: '2024-09-23T14:30:02.044Z', sequence: 1_840_009, payload_preview: '{order_id:ORD-99231, filled:130000, avg_px:248.61}', source_node: 'ems-core-02.lon' },
  { event_id: 'evt-1_840_010', stream: 'orders.lon', type: 'OrderCancelled', occurred_at: '2024-09-23T14:30:02.401Z', sequence: 1_840_010, payload_preview: '{order_id:ORD-99232, reason:CLIENT_CANCEL}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_011', stream: 'config.global', type: 'ConfigChanged', occurred_at: '2024-09-23T14:30:03.000Z', sequence: 1_840_011, payload_preview: '{key:venue.lse.fee_schedule, prev:v18, next:v19}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_012', stream: 'system', type: 'Heartbeat', occurred_at: '2024-09-23T14:30:04.000Z', sequence: 1_840_012, payload_preview: '{node:ems-core-02.lon, term:47, leader:false}', source_node: 'ems-core-02.lon' },
  { event_id: 'evt-1_840_013', stream: 'marketdata.l1', type: 'MarketDataUpdated', occurred_at: '2024-09-23T14:30:04.512Z', sequence: 1_840_013, payload_preview: '{instr:GB00B03MLX29, bid:248.60, ask:248.62, src:EXCHANGE}', source_node: 'ems-core-04.fra' },
  { event_id: 'evt-1_840_014', stream: 'orders.tok', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:05.020Z', sequence: 1_840_014, payload_preview: '{order_id:ORD-99234, side:BUY, qty:1000, instr:JP3756600007}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_015', stream: 'orders.tok', type: 'OrderRejected', occurred_at: '2024-09-23T14:30:05.041Z', sequence: 1_840_015, payload_preview: '{order_id:ORD-99234, reason:RISK_LIMIT_EXCEEDED}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_016', stream: 'fills.nyc', type: 'FillReceived', occurred_at: '2024-09-23T14:30:05.780Z', sequence: 1_840_016, payload_preview: '{order_id:ORD-99233, filled:500, avg_px:189.45}', source_node: 'ems-core-03.lon' },
  { event_id: 'evt-1_840_017', stream: 'system', type: 'ClockTick', occurred_at: '2024-09-23T14:30:06.500Z', sequence: 1_840_017, payload_preview: '{seq:1840017, offset_ns:-13100}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_018', stream: 'audit', type: 'RiskLimitUpdated', occurred_at: '2024-09-23T14:30:07.110Z', sequence: 1_840_018, payload_preview: '{desk:RATES-LDN, limit:50000000, prev:40000000, by:r.kumar}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_019', stream: 'orders.fra', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:08.250Z', sequence: 1_840_019, payload_preview: '{order_id:ORD-99235, side:BUY, qty:50000, instr:DE0001102580}', source_node: 'ems-core-04.fra' },
  { event_id: 'evt-1_840_020', stream: 'orders.fra', type: 'OrderRouted', occurred_at: '2024-09-23T14:30:08.268Z', sequence: 1_840_020, payload_preview: '{order_id:ORD-99235, venue:XETRA, ack:OK}', source_node: 'ems-core-04.fra' },
  { event_id: 'evt-1_840_021', stream: 'system', type: 'Heartbeat', occurred_at: '2024-09-23T14:30:09.000Z', sequence: 1_840_021, payload_preview: '{node:ems-core-01.lon, term:47, leader:true}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_022', stream: 'config.global', type: 'RuleVersionChanged', occurred_at: '2024-09-23T14:30:10.000Z', sequence: 1_840_022, payload_preview: '{prev:rv-2024.09.15, next:rv-2024.09.18, by:compliance}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_023', stream: 'marketdata.l2', type: 'MarketDataUpdated', occurred_at: '2024-09-23T14:30:10.444Z', sequence: 1_840_023, payload_preview: '{instr:US0378331005, levels:10, top_bid:189.44, top_ask:189.46}', source_node: 'ems-core-05.fra' },
  { event_id: 'evt-1_840_024', stream: 'orders.lon', type: 'OrderAmended', occurred_at: '2024-09-23T14:30:11.020Z', sequence: 1_840_024, payload_preview: '{order_id:ORD-99236, qty:300000, prev:200000}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_025', stream: 'system', type: 'SnapshotTaken', occurred_at: '2024-09-23T14:30:12.000Z', sequence: 1_840_025, payload_preview: '{segment:seg-2024-09-23-1430, size_bytes:4194304}', source_node: 'ems-log-01.nyc' },
  { event_id: 'evt-1_840_026', stream: 'system', type: 'SegmentSealed', occurred_at: '2024-09-23T14:30:12.500Z', sequence: 1_840_026, payload_preview: '{segment:seg-2024-09-23-1429, checksum:sha256:7e1b...}', source_node: 'ems-log-01.nyc' },
  { event_id: 'evt-1_840_027', stream: 'fills.lon', type: 'FillCancelled', occurred_at: '2024-09-23T14:30:13.220Z', sequence: 1_840_027, payload_preview: '{fill_id:F-551203, reason:VENUE_BUST}', source_node: 'ems-core-02.lon' },
  { event_id: 'evt-1_840_028', stream: 'orders.ams', type: 'OrderReceived', occurred_at: '2024-09-23T14:30:14.010Z', sequence: 1_840_028, payload_preview: '{order_id:ORD-99237, side:SELL, qty:75000, instr:NL0011794037}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_029', stream: 'system', type: 'ClockTick', occurred_at: '2024-09-23T14:30:14.500Z', sequence: 1_840_029, payload_preview: '{seq:1840029, offset_ns:-11800}', source_node: 'ems-core-01.lon' },
  { event_id: 'evt-1_840_030', stream: 'audit', type: 'ConfigChanged', occurred_at: '2024-09-23T14:30:15.000Z', sequence: 1_840_030, payload_preview: '{key:session.lon.open, prev:false, next:true}', source_node: 'ems-core-01.lon' },
];

export const mockDivergences: Divergence[] = [
  {
    sequence: 1_840_018,
    type: 'payload_mismatch',
    severity: 'medium',
    original: 'desk=RATES-LDN, limit=40000000, by=r.kumar',
    replayed: 'desk=RATES-LDN, limit=50000000, by=r.kumar',
    note: 'Risk limit was already mutated before snapshot start; replay starts from new state but original snapshot had the old value.',
  },
  {
    sequence: 1_840_022,
    type: 'timestamp_skew',
    severity: 'low',
    original: 'occurred_at=2024-09-23T14:30:09.998Z',
    replayed: 'occurred_at=2024-09-23T14:30:10.000Z',
    note: 'Wall-clock sub-millisecond skew from SIMULATED clock mode rebase.',
  },
  {
    sequence: 1_840_027,
    type: 'extra_event',
    severity: 'high',
    original: '<absent>',
    replayed: 'FillCancelled fill_id=F-551203',
    note: 'Replayed VENUE_BUST cancellation that did not exist in original due to race in venue ACK.',
  },
];

// Helpers
export function generateId(prefix: string): string {
  const t = Date.now().toString(36);
  const r = Math.floor(Math.random() * 1e6).toString(36);
  return `${prefix}-${t}-${r}`;
}

export function findSession(id: string): ReplaySession | undefined {
  return mockReplaySessions.find((s) => s.id === id);
}

export function nextSequenceForSession(_session: ReplaySession): number {
  // Find the max sequence used in any session, else start at the latest event seq.
  return mockEvents.length > 0 ? mockEvents[mockEvents.length - 1].sequence + 1 : 1_840_100;
}