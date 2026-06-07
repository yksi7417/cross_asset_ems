export const KNOWN_STREAMS = [
  'orders.lon',
  'orders.nyc',
  'orders.tok',
  'orders.fra',
  'orders.ams',
  'fills.lon',
  'fills.nyc',
  'fills.tok',
  'config.global',
  'marketdata.l1',
  'marketdata.l2',
  'audit',
  'system',
] as const;

export const KNOWN_EVENT_TYPES = [
  'OrderReceived',
  'OrderRouted',
  'OrderCancelled',
  'OrderRejected',
  'OrderAmended',
  'FillReceived',
  'PartialFill',
  'FillCancelled',
  'ConfigChanged',
  'RuleVersionChanged',
  'RiskLimitUpdated',
  'Heartbeat',
  'LeaderElected',
  'FollowerJoined',
  'ClockTick',
  'SegmentSealed',
  'SnapshotTaken',
  'MarketDataUpdated',
] as const;

export const CLOCK_MODES = [
  'WALL_CLOCK_FAST',
  'WALL_CLOCK_REALTIME',
  'SIMULATED',
  'PAUSED',
] as const;