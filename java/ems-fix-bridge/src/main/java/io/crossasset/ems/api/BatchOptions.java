/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

/**
 * Batch behaviour of an {@link ApiRequest} (task 8.5), per the arch-api-first envelope's {@code
 * options: { partial_ok, on_error }}.
 *
 * <p>{@code onError} controls what happens to the items after the first rejection: CONTINUE
 * attempts them all; STOP leaves the remainder DEFERRED (not attempted). {@code partialOk=false}
 * makes the batch all-or-nothing: if any item rejects, the already-applied items are compensated
 * (staged orders are canceled) and reported REJECTED — supported only for operations with a clean
 * compensating action (STAGE_ORDERS); other operations reject the whole request up front rather
 * than pretend.
 */
public record BatchOptions(boolean partialOk, OnError onError) {

  public enum OnError {
    STOP,
    CONTINUE
  }

  /** The default: partial success allowed, every item attempted. */
  public static final BatchOptions DEFAULT = new BatchOptions(true, OnError.CONTINUE);
}
