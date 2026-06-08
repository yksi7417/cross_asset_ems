# Aeron Channel Layout & Transport Configuration

This document defines the logical and physical Aeron channel layout for the Cross-Asset EMS.
It serves as the authoritative reference for configuring `Aeron` and `SBE` components across the system.

## 1. Architectural Principles

The EMS uses a **hybrid transport model**:
- **Unicast (UDP)**: Point-to-point request/response between specific components (e.g., Order Layer $\rightarrow$ Router).
- **Multicast (UDP)**: High-fanout distribution for state updates, quotes, and the audit tail.
- **Clustered (Raft)**: Replicated state machines via Aeron Cluster for critical services.

### 1.1 Determinism and Order
Every event on the bus is sequenced. The combination of `(stream_id, position)` in Aeron Archive provides the absolute timeline used for:
- Replay determinism ([[arch-time-replay-server]])
- Event sourcing audit trails ([[arch-event-sourcing]])
- Sequence recovery ([[arch-sequence-recovery]])

## 2. Channel Layout

### 2.1 Internal Bus (Component-to-Component)

| Logical Path | Physical Endpoint (Staging) | Direction | Schema(s) | Purpose |
|---|---|---|---|---|
| `Edge $\rightarrow$ Order` | `aeron:udp?endpoint=staging.in:40001` | Inbound | `StageOrders`, `AmendOrders` | Entry point for FIX/API orders |
| `Order $\rightarrow$ Router` | `aeron:udp?endpoint=router.in:40002` | Outbound | `RouteOrders`, `CancelRoutes` | Dispatched orders to routing layer |
| `Router $\rightarrow$ Venue` | `aeron:udp?endpoint=venue-out:40010+` | Outbound | Per-venue dialect | Outbound FIX/Binary to venues |

### 2.2 Distribution Bus (Multicast)

| Topic | Physical Endpoint (Staging) | Type | Schema(s) | Purpose |
|---|---|---|---|---|
| `Quote Bus` | `aeron:udp?endpoint=224.0.1.10:50000` | Multicast | `QuoteSnapshot`, `QuoteIncrement` | Real-time market data distribution |
| `Event Tail` | `aeron:udp?endpoint=224.0.1.20:50010` | Multicast | `Event` (Sealed Union) | Replay-capable audit stream for all components |

## 3. SBE Envelope Specification

All messages on the Aeron bus are wrapped in a standard envelope to ensure observability and auditability without decoding the inner payload.

### 3.1 MessageHeader (Base)
Fixed at the start of every message.
- `block_length` (uint16): Length of the current block.
- `template_id` (uint16): Identifies the message type (e.g., `0xD001` for `StageOrders`).
- `schema_id` (uint16): Version of the SBE schema.
- `version` (uint16): Message version.

### 3.2 SessionHeader (Extended)
Added to every internal EMS message.
- `session_id` (uint64): Session identifier for sequence recovery.
- `seq_num` (uint64): Monotonic sequence number.
- `send_time` (uint64): Clock-injected timestamp (nanos).
- `source` (enum): `FIX`, `API`, `AUTOMATION`, `REPLAY`, `CLUSTER`.
- **Observability (W3C Trace Context)**:
  - `trace_id` (bytes[16]): Global trace ID.
  - `parent_span_id` (bytes[8]): Parent span ID for correlation.
  - `trace_flags` (uint8): Sampling and debug flags.
- **Identity Chaining (Audit)**:
  - `initial_order_id` (bytes[16]): UUID of the original order.
  - `initial_route_id` (bytes[16]): UUID of the original route.

## 4. Configuration Guidelines

### 4.1 Tuning for Low Latency
For production environments, the following `Aeron` settings are recommended:
- `aeron.client.dir`: Mount to a high-performance NVMe or RAMDisk.
- `aeron.mtpa.idle.strategy`: Use `BusySpinIdleStrategy` for hot-path threads.
- `aeron.udp.max.mtu`: Set to `1400` to avoid fragmentation over AWS/Azure VPCs.

### 4.2 Schema Evolution
To maintain backward compatibility:
1. **Append-Only**: Never reorder or delete fields.
2. **SinceVersion**: New fields must include a `sinceVersion` attribute.
3. **Nullable**: Mark deprecated fields as `deprecated=true`.

## 5. See Also
- [[arch-sbe-aeron-transport]] — High-level architecture.
- [[arch-observability]] — Trace correlation.
- [[arch-identity-chaining]] — Stable identifiers.
- [[arch-event-sourcing]] — Event log integration.
