/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

/**
 * The result of running a single {@link Projection} over a {@link ReplaySlice}.
 *
 * @param <S> the projection's state type
 * @param finalState the state after all in-slice records have been processed
 * @param eventsProcessed number of log records in the slice presented to the runner (includes both
 *     accepted and filtered records; excludes records outside the slice bounds)
 * @param lastConsumedGlobalSeq the {@code globalSeq} of the last record seen by the runner, or
 *     {@code -1} if no records were in the slice
 */
public record ReplayResult<S>(S finalState, long eventsProcessed, long lastConsumedGlobalSeq) {}
