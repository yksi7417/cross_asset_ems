---
type: architecture
layer: transport
status: draft
tags: [architecture/transport, aeron, internals]
---

# Aeron Internals — How the Transport Layer Actually Works

Companion to [[arch-sbe-aeron-transport]], which covers *what* Aeron does and *why* we chose it.
This doc covers *how* it works under the hood, how the EMS components use it, and how to observe
and debug it.

---

## The three-tier model

```
┌───────────────────────────────────────────┐
│  Application (your Java code)             │  ← Publications, Subscriptions, ClusteredService
│  e.g. PongService, OrderLayer, Router     │
├───────────────────────────────────────────┤
│  Aeron Client API                         │  ← Aeron, AeronCluster, AeronArchive
│  (aeron-client.jar, aeron-cluster.jar)    │
├───────────────────────────────────────────┤
│  MediaDriver                              │  ← The actual I/O engine
│  (one per machine, shared by all JVMs)    │
└───────────────────────────────────────────┘
        │
       UDP sockets, OS networking
        │
┌─── other machine or same machine ─────────┐
│  MediaDriver (remote / other service)     │
└───────────────────────────────────────────┘
```

The application never touches a socket. The **MediaDriver** owns all OS resources: UDP sockets,
off-heap buffers, CPU affinity. Application code interacts with the MediaDriver through
**shared memory ring buffers** — `mmap`-backed files in the Aeron directory.

---

## Shared memory: how messages move without copying

When your code calls `publication.offer(buffer, offset, length)`:

1. The Aeron client writes your bytes into a **term buffer** — a fixed-size circular log
   (`mmap`-backed file under `aeronDir/`) shared with the MediaDriver.
2. The MediaDriver's I/O thread reads the term buffer, wraps the bytes in an Aeron frame
   header, and calls `sendto()` on the UDP socket.
3. On the receiving side, the MediaDriver puts the payload into the subscription's term buffer.
4. Your `Subscription.poll()` call reads from that term buffer and calls your `FragmentHandler`.

No data is copied between JVM heap and kernel — the term buffer lives in off-heap memory
accessible to both the JVM and the MediaDriver process. This is why Aeron requires
`--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`: the Agrona library uses
`jdk.internal.misc.Unsafe` to map and access these off-heap regions directly.

**Implication for the EMS:** the hot path (staging an order, routing it, receiving an ack)
never allocates on the Java heap and never copies data through the kernel network stack
more than once per hop.

---

## What goes over the wire

Even between components on the same host, messages travel as **real UDP packets** on loopback.
The frame layout on the wire:

```
 0               1               2               3
 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
├───────────────────────────────────────────────────────────────────┤
│                       frame_length (4)                            │
├───────┬───────┬───────────────────────────────────────────────────┤
│version│ flags │                  type (2)                         │
├───────────────────────────────────────────────────────────────────┤
│                       term_offset (4)                             │
├───────────────────────────────────────────────────────────────────┤
│                       session_id (4)                              │
├───────────────────────────────────────────────────────────────────┤
│                       stream_id (4)                               │
├───────────────────────────────────────────────────────────────────┤
│                       term_id (4)                                 │
├───────────────────────────────────────────────────────────────────┤
│              payload (your SBE message or "PING")                 │
│                        ...                                        │
└───────────────────────────────────────────────────────────────────┘
```

The `session_id` and `stream_id` identify which publication/subscription pair this message
belongs to. The `term_offset` is the byte position within the current circular log term,
which is how the Archive records and replays with position precision.

Your SBE payload sits directly after the 24-byte Aeron header. The EMS adds its own
73-byte `SessionHeader` (see [[arch-sbe-aeron-transport]]) on top of that:

```
[Aeron 24B header][SBE 8B MessageHeader][SBE SessionHeader ~65B][SBE message body]
```

---

## Aeron Cluster — what Raft means in practice

Aeron Cluster wraps a replicated state machine over Aeron transport. The key moving parts:

```
┌──────────────────────────────────────────────────────────────┐
│  ClusteredMediaDriver  (one per cluster member)              │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  MediaDriver    │  │   Archive    │  │ ConsensusModule│  │
│  │  (I/O engine)  │  │ (log to disk)│  │ (Raft engine)  │  │
│  └─────────────────┘  └──────────────┘  └────────────────┘  │
└──────────────────────────────────────────────────────────────┘
         │ shared memory                     │ IPC
         └──────────────────────────────────►│
                                             ▼
┌──────────────────────────────────────────────────────────────┐
│  ClusteredServiceContainer                                   │
│  └── YourClusteredService (e.g. OrderLayer, PongService)     │
│       onSessionMessage() called only after quorum commit     │
└──────────────────────────────────────────────────────────────┘
```

**What "quorum commit" means for the EMS:**

A client sends a STAGE_ORDER message to the cluster ingress. The ConsensusModule receives it,
appends it to the Raft log, replicates that log entry to a majority of members, and only then
calls `onSessionMessage()` on the `OrderLayer` service. If the leader crashes after the quorum
write but before your service method runs, the new leader will replay the log entry and call
your method — exactly once, in order, deterministically.

This is why `onSessionMessage()` must be **side-effect deterministic**: it will be called on
all members and on restart-replay, so it must produce the same state given the same input.

**Single-member shortcut (toy and development):**

Setting `appointedLeaderId = 0` on a one-member cluster skips the Raft election wait and
immediately self-elects. Quorum of 1 means every write is instantly committed. Used by
`AeronToyPingPong` and by the local dev environment; the 3-node production setup is task 2.5.

---

## Aeron Archive — what it records and why

The Archive sits next to the MediaDriver and **records live Aeron streams to disk**:

```
ConsensusModule publishes to the cluster log stream
          │
          ▼
    Archive records every frame to disk
    (raw Aeron frames — not decoded, not filtered)
          │
          ├── warm standby catchup: new member fetches missing log segments
          ├── cold-start recovery: leader died, new leader replays from snapshot + tail
          ├── time-replay server: feed historical log through simulated clock
          └── best-execution audit: reconstruct exact market state at a past timestamp
```

Each recorded stream is identified by `(recordingId, streamId, sessionId)`. The Archive tracks
the **start and stop position** of each recording, enabling byte-precise seeks. When a node
restarts, it loads the latest cluster snapshot (which `onTakeSnapshot()` wrote) and then asks
the Archive to replay the log from the snapshot's position to the current end — this is how
state is reconstructed without replaying everything from the beginning.

**What the Archive is not:** it does not record your application's domain events. The
[[arch-event-sourcing|event log]] (`Event` SBE messages on the multicast tail stream) is the
application-level record. The Archive is the transport-level record — raw frames, lower
overhead, faster seek, no application decoding needed. Both exist; both must agree for
golden-replay correctness.

---

## Component map: toy → production

The `AeronToyPingPong` is a minimal but structurally correct implementation of the same pattern
used by all stateful EMS components:

| Toy | Production equivalent |
|---|---|
| `AeronToyPingPong.start()` | Each service's `main()` / container startup |
| `ClusteredMediaDriver.launch()` | Same — one per host, shared by co-located services |
| `PongService` (implements `ClusteredService`) | `OrderLayer`, `Router`, `AllocService`, etc. |
| `PongService.onStart(cluster, snapshot)` | Service loads its in-memory state from the snapshot |
| `PongService.onSessionMessage()` | Receives an SBE `StageOrders` / `RouteOrders` / etc. |
| `PongService.onTakeSnapshot()` | Writes FSM state to the snapshot publication for Archive |
| `AeronCluster.connect()` | FIX bridge / API gateway connecting to a service cluster |
| `client.offer(buffer)` | FIX bridge sends a staged order into the cluster |
| `EgressListener.onMessage()` | FIX bridge receives the ack / reject from the cluster |
| Archive dir non-empty check | Archive segment files = replicated log is being recorded |

---

## Port layout in the EMS

Each cluster member and service uses a set of reserved ports. The toy uses the `29xxx` range
to avoid conflicts with the dev stack (Grafana, OpenSearch, Prometheus, Jaeger):

| Port | Purpose |
|---|---|
| `29110` | Client ingress — clients send orders/requests here |
| `29111` | Member-to-member — Raft heartbeats and vote requests |
| `29112` | Log replication — leader streams log segments to followers |
| `29113` | File transfer — snapshot and log segment catchup |
| `29114` | Archive control — external clients request recordings/replays |
| `29115` | Archive replication — log pulled by remote Archive during catchup |
| `29120` | Client egress — cluster sends acks/rejects back to the client |

In production, each cluster (Order Layer, Router, etc.) uses a distinct port range. The
[[arch-deployment|deployment spec]] assigns non-overlapping ranges per service.

---

## Observing the data

### 1. Watch real UDP packets (tcpdump)

```bash
# Run while tests are executing with --rerun-tasks
sudo tcpdump -i lo -n udp portrange 29110-29120 -X
```

You will see Aeron frames. The payload bytes `50 49 4e 47` are ASCII "PING";
`50 4f 4e 47` are "PONG". In production these bytes would be an SBE-encoded message.

### 2. Live counters (AeronStat)

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
     -cp $(find ~/.gradle/caches -name "aeron-driver-1.51.0.jar" | head -1) \
     io.aeron.samples.AeronStat
```

Reads counters from the shared memory directory without touching the wire. Shows:
publication/subscription positions, backpressure events, NAK/retransmit counts,
and heartbeat state. Useful for diagnosing slow consumers or loss.

### 3. Inspect Archive recordings (xxd)

```bash
# Run the toy first: ./gradlew :ems-transport:runToy
# While it is paused (or after it exits), inspect the archive:

ls -lh /tmp/ems-aeron-demo/archive/
# 0-0.rec         — raw recording, pre-allocated to 128 MB (sparse)
# archive.catalog — index: recording IDs, stream IDs, start/stop positions
# archive-mark.dat — crash-recovery high-water mark for the catalog

# Search for PING/PONG bytes in the recording segment:
strings /tmp/ems-aeron-demo/archive/0-0.rec | grep -E "PING|PONG"
hexdump -C /tmp/ems-aeron-demo/archive/0-0.rec | grep -A1 "PING\|PONG"

# Note: Aeron pre-allocates the full 128 MB segment file even when only a
# few bytes are written. The actual data occupies only the written frames;
# the rest of the file is zeroed. This is intentional — it avoids
# filesystem metadata operations on the hot path.
```

In production the log segments live in the archive directory mounted on fast local NVMe.
Older segments tier to S3/Azure per [[arch-jurisdictional-compliance|retention policy]].

### 4. Check active sockets

```bash
ss -lunp | grep -E "2911[0-5]|29120"
```

Shows the bound UDP sockets while the cluster is running. Confirms real OS-level I/O.

---

## Lifecycle summary

```
start()
  ClusteredMediaDriver.launch()
    MediaDriver starts        → aeronDir/ created, shared memory mapped
    Archive starts            → archiveDir/ created, recording socket bound on :29114
    ConsensusModule starts    → clusterDir/ created, Raft state initialized
    [appointedLeaderId=0]     → self-elects immediately (single-node shortcut)

  ClusteredServiceContainer.launch()
    waits for ConsensusModule LEADER state
    calls PongService.onStart(cluster, null)   ← null = no snapshot to restore

runPingPong()
  Aeron.connect()            → connects to aeronDir/ shared memory (no network)
  AeronCluster.connect()     → sends session-open request to :29110; blocks until CONNECTED
  client.offer("PING")       → writes to term buffer → MediaDriver → UDP → :29110
  ConsensusModule receives   → appends to Raft log → Archive records frame
  PongService.onSessionMessage() → writes "PONG" to session → UDP → :29120
  client.pollEgress()        → reads from :29120 subscription → EgressListener.onMessage()

close()
  ClusteredServiceContainer → closes service cleanly
  ClusteredMediaDriver      → closes ConsensusModule, Archive, MediaDriver
                              aeronDir/ deleted (dirDeleteOnShutdown=true)
```

---

## See also

- [[arch-sbe-aeron-transport]] — channel layout, SBE envelope, why Aeron
- [[arch-resilience-24x7]] — Raft cluster sizing, hot-warm-cold model, rolling restart
- [[arch-event-sourcing]] — application event log vs Archive (different layers, both needed)
- [[arch-time-replay-server]] — golden replay using Archive + simulated clock
- [[arch-fix-fsm-design]] — determinism requirements that make Archive replay safe
- `java/ems-transport/` — `AeronToyPingPong.java`, `PongService.java` (working reference impl)
