/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Objects;

/**
 * Credentials presented at logon. Per entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public record LogonCredentials(CredentialKind kind, String token) {

  public LogonCredentials {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(token, "token");
  }
}
