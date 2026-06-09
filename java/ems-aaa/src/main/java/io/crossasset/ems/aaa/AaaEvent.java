/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

/**
 * Accounting events emitted by the AAA layer for audit and capacity planning. Per
 * entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public sealed interface AaaEvent
    permits AaaEvent.ConnectAttempted,
        AaaEvent.Authenticated,
        AaaEvent.LogonRejected,
        AaaEvent.SessionLogout {

  /** Every inbound connection attempt, before credential validation. */
  record ConnectAttempted(CredentialKind kind, long timestampMicros) implements AaaEvent {}

  /** Credential validated; session established. */
  record Authenticated(long sessionId, Identity identity, long timestampMicros)
      implements AaaEvent {}

  /** Credential check failed; EMS-SES-1001 or similar. */
  record LogonRejected(String rejectCode, String message, long timestampMicros)
      implements AaaEvent {}

  /** Session terminated cleanly or forcibly. */
  record SessionLogout(long sessionId, String reason, long timestampMicros) implements AaaEvent {}
}
