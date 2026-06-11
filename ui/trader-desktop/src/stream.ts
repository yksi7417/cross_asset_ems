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
      this.onStatus("live");
    };
    socket.onmessage = (message) => {
      const event = JSON.parse(message.data as string) as ApiEvent;
      if (event.seq <= this.lastSeq) {
        return; // duplicate at a reconnect boundary
      }
      this.lastSeq = event.seq;
      this.onEvent(event);
    };
    socket.onclose = () => this.scheduleReconnect();
    socket.onerror = () => socket.close();
  }

  close(): void {
    this.closed = true;
    this.socket?.close();
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
