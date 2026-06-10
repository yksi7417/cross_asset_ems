/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.jspecify.annotations.Nullable;

/**
 * Input for creating a route from a staged order.
 *
 * <p>If {@code clOrdId} is null the router auto-generates one as {@code CL-<routeId>}. The caller
 * may supply a specific value (e.g. venue-assigned) subject to dedup via EMS-RTE-2005.
 */
public record RouteRequest(
    String requestId,
    String orderId,
    String venueMic,
    long qty,
    @Nullable Long price,
    @Nullable String clOrdId) {}
