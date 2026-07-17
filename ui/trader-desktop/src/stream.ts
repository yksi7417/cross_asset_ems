/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

/** One ApiEvent as framed by the WS edge (WsEventStreamServer). */
export interface ApiEvent {
  topic: string;
  seq: number;
  type: string;
  refId: string;
  payload: string;
}

export type StreamStatus = "connecting" | "live" | "reconnecting" | "down";

// L3-1: WsEventStreamServer (java/ems-fix-bridge) has no server-initiated heartbeat frame — it
// only pongs a client-sent WS ping, which browsers issue at the protocol level and never surface
// to JS — so a half-open socket (backend hung, TCP still up) never fires onclose/onerror and
// `onopen` alone cannot tell "live" from "frozen". Absent a heartbeat to key off, the fallback is
// a plain elapsed-time watchdog: if no frame has arrived within STALE_MS of the last one, treat
// the stream as no longer live and force a reconnect through the existing backoff path.
const STALE_MS = 10_000;
const STALE_CHECK_MS = 2_000;

/**
 * Resumable topic stream over the 8.10/18.1 WS edge. The client owns the cursor: every delivered
 * event advances `lastSeq`, and a reconnect asks for `from = lastSeq + 1`, so a dropped socket or
 * a refreshed tab resumes with no missed and no doubled rows (arch-api-first § Resume). Backoff is
 * exponential, capped at 10s; the server holds no per-client state.
 */
export class ResumableStream {
  private lastSeq = 0;
  private attempts = 0;
  private closed = false;
  private socket: WebSocket | null = null;
  private lastMessageAt = 0;
  private staleTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly topic: string,
    private readonly sessionId: number,
    private readonly onEvent: (event: ApiEvent) => void,
    private readonly onStatus: (status: StreamStatus) => void,
  ) {}

  connect(): void {
    if (this.closed) {
      return;
    }
    this.onStatus(this.attempts === 0 ? "connecting" : "reconnecting");
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const url =
      `${proto}://${location.host}/ws/events?session=${this.sessionId}` +
      `&topic=${encodeURIComponent(this.topic)}&from=${this.lastSeq + 1}`;
    const socket = new WebSocket(url);
    this.socket = socket;

    socket.onopen = () => {
      this.attempts = 0;
      this.lastMessageAt = Date.now();
      this.armStaleWatchdog();
      this.onStatus("live");
    };
    socket.onmessage = (message) => {
      this.lastMessageAt = Date.now();
      const event = JSON.parse(message.data as string) as ApiEvent;
      if (event.seq <= this.lastSeq) {
        return; // duplicate at a reconnect boundary
      }
      this.lastSeq = event.seq;
      this.onEvent(event);
    };
    socket.onclose = () => {
      this.disarmStaleWatchdog();
      this.scheduleReconnect();
    };
    socket.onerror = () => socket.close();
  }

  close(): void {
    this.closed = true;
    this.disarmStaleWatchdog();
    this.socket?.close();
  }

  private armStaleWatchdog(): void {
    this.disarmStaleWatchdog();
    this.staleTimer = setInterval(() => this.checkStale(), STALE_CHECK_MS);
  }

  private disarmStaleWatchdog(): void {
    if (this.staleTimer !== null) {
      clearInterval(this.staleTimer);
      this.staleTimer = null;
    }
  }

  private checkStale(): void {
    if (Date.now() - this.lastMessageAt < STALE_MS) {
      return;
    }
    this.disarmStaleWatchdog();
    // A half-open socket never fires close/error on its own — sever it ourselves so the chip
    // leaves "live" immediately and the existing backoff/reconnect path (plus recovery.ts'
    // not-live health poll) takes over from here, same as a hard disconnect would.
    const stale = this.socket;
    this.socket = null;
    if (stale) {
      stale.onclose = null;
      stale.onerror = null;
      stale.onmessage = null;
      stale.close();
    }
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (this.closed) {
      return;
    }
    this.onStatus(this.attempts < 3 ? "reconnecting" : "down");
    const delay = Math.min(10_000, 500 * 2 ** this.attempts++);
    setTimeout(() => this.connect(), delay);
  }
}
