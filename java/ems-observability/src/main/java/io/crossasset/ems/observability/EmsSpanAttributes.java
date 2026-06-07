/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Centralized OpenTelemetry attribute keys for the EMS. Every span,
 * every metric exemplar, every log record should attach these where
 * applicable. Mapping is per
 * {@code arch-observability.md §"Span attributes — required minimum"}.
 *
 * <p>Attribute naming follows OpenTelemetry semantic conventions
 * ({@code ems.*} custom namespace) and the domain spec
 * ({@code ems.session_id}, {@code ems.firm_id}, etc.).
 */
public final class EmsSpanAttributes {

    /** Ems domain namespace prefix. */
    public static final String NAMESPACE = "ems.";

    // ---- session / identity (arch-identity-chaining) ----
    public static final AttributeKey<String> SESSION_ID =
            AttributeKey.stringKey(NAMESPACE + "session_id");
    public static final AttributeKey<String> FIRM_ID =
            AttributeKey.stringKey(NAMESPACE + "firm_id");
    public static final AttributeKey<String> DESK_ID =
            AttributeKey.stringKey(NAMESPACE + "desk_id");
    public static final AttributeKey<String> USER_ID =
            AttributeKey.stringKey(NAMESPACE + "user_id");

    // ---- order / route / chain (arch-order-staged, arch-router-layer) ----
    public static final AttributeKey<String> ORDER_ID =
            AttributeKey.stringKey(NAMESPACE + "order_id");
    public static final AttributeKey<String> ROUTE_ID =
            AttributeKey.stringKey(NAMESPACE + "route_id");
    public static final AttributeKey<String> CHAIN_ID =
            AttributeKey.stringKey(NAMESPACE + "chain_id");
    public static final AttributeKey<String> CL_ORD_ID =
            AttributeKey.stringKey(NAMESPACE + "cl_ord_id");

    // ---- event classification (arch-event-sourcing) ----
    public static final AttributeKey<String> EVENT_TYPE =
            AttributeKey.stringKey(NAMESPACE + "event.type");
    public static final AttributeKey<String> EVENT_VERSION =
            AttributeKey.stringKey(NAMESPACE + "event.version");

    // ---- venue (arch-venue-connectivity) ----
    public static final AttributeKey<String> VENUE =
            AttributeKey.stringKey(NAMESPACE + "venue");
    public static final AttributeKey<String> MIC =
            AttributeKey.stringKey(NAMESPACE + "mic");

    // ---- asset class / instrument ----
    public static final AttributeKey<String> ASSET_CLASS =
            AttributeKey.stringKey(NAMESPACE + "asset_class");
    public static final AttributeKey<String> SYMBOL =
            AttributeKey.stringKey(NAMESPACE + "symbol");
    public static final AttributeKey<String> CUSIP =
            AttributeKey.stringKey(NAMESPACE + "cusip");
    public static final AttributeKey<String> ISIN =
            AttributeKey.stringKey(NAMESPACE + "isin");

    // ---- validator / compliance / risk (arch-validator) ----
    public static final AttributeKey<String> REJECT_CODE =
            AttributeKey.stringKey(NAMESPACE + "reject_code");
    public static final AttributeKey<String> REJECT_REASON =
            AttributeKey.stringKey(NAMESPACE + "reject_reason");
    public static final AttributeKey<String> COMPLIANCE_DECISION =
            AttributeKey.stringKey(NAMESPACE + "compliance.decision");
    public static final AttributeKey<String> RISK_DECISION =
            AttributeKey.stringKey(NAMESPACE + "risk.decision");

    // ---- audit (arch-observability §"Sampling" — audit always 100%) ----
    public static final AttributeKey<Boolean> AUDIT_REQUIRED =
            AttributeKey.booleanKey(NAMESPACE + "audit.required");
    public static final AttributeKey<String> AUDIT_RETENTION_TIER =
            AttributeKey.stringKey(NAMESPACE + "audit.retention_tier");

    // ---- deployment / pod (matches otel-collector-config.yaml) ----
    public static final AttributeKey<String> POD =
            AttributeKey.stringKey(NAMESPACE + "pod");
    public static final AttributeKey<String> HOST =
            AttributeKey.stringKey(NAMESPACE + "host");

    // ---- counter / measure attributes for high-cardinality fields ----
    public static final AttributeKey<String> DIRECTION =
            AttributeKey.stringKey(NAMESPACE + "direction"); // BUY/SELL/SHORT-SELL
    public static final AttributeKey<String> ORDER_TYPE =
            AttributeKey.stringKey(NAMESPACE + "order_type"); // MARKET/LIMIT/STOP
    public static final AttributeKey<String> TIME_IN_FORCE =
            AttributeKey.stringKey(NAMESPACE + "tif");
    public static final AttributeKey<String> CURRENCY =
            AttributeKey.stringKey(NAMESPACE + "currency");

    // ---- regulator/regime (arch-jurisdictional-compliance) ----
    public static final AttributeKey<String> REGULATOR =
            AttributeKey.stringKey(NAMESPACE + "regulator"); // SEC/FCA/MAS/ESMA/HKMA
    public static final AttributeKey<String> REGIME =
            AttributeKey.stringKey(NAMESPACE + "regime"); // MiFID-II/SEC-15c3/etc.

    private EmsSpanAttributes() {}
}
