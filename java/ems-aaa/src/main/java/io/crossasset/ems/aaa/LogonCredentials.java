/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Objects;

/**
 * Credentials presented at logon. Per entry-point-aaa.md.
 *
 * <p>{@code declaredSeq} is the client's claimed next sequence number at logon — the FIX MsgSeqNum
 * in a 35=A message. Use {@link #fresh(CredentialKind, String)} when sequence tracking is not
 * relevant (REST/API callers or tests).
 *
 * <p>Task 5.1 — AAA service skeleton. declaredSeq added in task 5.5.
 */
public record LogonCredentials(CredentialKind kind, String token, long declaredSeq) {

  public LogonCredentials {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(token, "token");
  }

  /** Factory for callers that don't participate in sequence recovery (e.g. REST, tests). */
  public static LogonCredentials fresh(CredentialKind kind, String token) {
    return new LogonCredentials(kind, token, 1L);
  }
}
