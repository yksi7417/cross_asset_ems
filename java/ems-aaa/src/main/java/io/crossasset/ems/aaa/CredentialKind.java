/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

/**
 * Three credential flavours the AAA service normalises at the trust boundary. Per
 * entry-point-aaa.md.
 */
public enum CredentialKind {
  TOKEN,
  FIX_LOGON,
  MTLS
}
