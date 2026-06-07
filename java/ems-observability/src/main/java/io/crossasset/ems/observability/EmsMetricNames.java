/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability;

/**
 * Centralized metric names for the EMS Prometheus / Grafana stack.
 * Naming follows Prometheus conventions and
 * {@code arch-observability.md §"Metric taxonomy"}:
 *
 * <ul>
 *   <li>Counters: {@code ems_*_total}
 *   <li>Histograms: {@code ems_*_duration_seconds} (time) or
 *       {@code ems_*_size_bytes} (size)
 *   <li>Gauges: {@code ems_*_active} (in-flight) or
 *       {@code ems_*_current} (state)
 * </ul>
 *
 * <p>Reference these constants instead of stringly-typed literals so
 * renaming and querying tooling can stay in sync.
 */
public final class EmsMetricNames {

    private static final String PREFIX = "ems_";

    // ---- orders (arch-order-staged) ----
    public static final String ORDERS_STAGED_TOTAL = PREFIX + "orders_staged_total";
    public static final String ORDERS_REPLACED_TOTAL = PREFIX + "orders_replaced_total";
    public static final String ORDERS_CANCELLED_TOTAL = PREFIX + "orders_cancelled_total";
    public static final String ORDERS_WORKING = PREFIX + "orders_working";
    public static final String ORDER_STAGE_DURATION_SECONDS = PREFIX + "order_stage_duration_seconds";

    // ---- routes (arch-router-layer) ----
    public static final String ROUTES_SENT_TOTAL = PREFIX + "routes_sent_total";
    public static final String ROUTES_FILLED_TOTAL = PREFIX + "routes_filled_total";
    public static final String ROUTES_PENDING_REPLACE = PREFIX + "routes_pending_replace";
    public static final String ROUTE_TO_FIRST_FILL_SECONDS = PREFIX + "route_to_first_fill_seconds";
    public static final String ROUTE_ROUND_TRIP_LATENCY_SECONDS = PREFIX + "route_round_trip_latency_seconds";

    // ---- fills (arch-venue-connectivity) ----
    public static final String FILLS_RECEIVED_TOTAL = PREFIX + "fills_received_total";
    public static final String FILL_LATENCY_SECONDS = PREFIX + "fill_latency_seconds";
    public static final String FILL_QUANTITY = PREFIX + "fill_quantity";

    // ---- validator / compliance / risk (arch-validator, arch-compliance) ----
    public static final String VALIDATOR_REJECTS_TOTAL = PREFIX + "validator_rejects_total";
    public static final String VALIDATOR_PASSES_TOTAL = PREFIX + "validator_passes_total";
    public static final String VALIDATOR_DURATION_SECONDS = PREFIX + "validator_duration_seconds";
    public static final String COMPLIANCE_BLOCKS_TOTAL = PREFIX + "compliance_blocks_total";
    public static final String COMPLIANCE_DECISIONS_TOTAL = PREFIX + "compliance_decisions_total";
    public static final String COMPLIANCE_DURATION_SECONDS = PREFIX + "compliance_duration_seconds";
    public static final String RISK_DECISIONS_TOTAL = PREFIX + "risk_decisions_total";
    public static final String RISK_DURATION_SECONDS = PREFIX + "risk_duration_seconds";

    // ---- SOR (arch-smart-order-router) ----
    public static final String SOR_DECISION_DURATION_SECONDS = PREFIX + "sor_decision_duration_seconds";
    public static final String SOR_DECISIONS_TOTAL = PREFIX + "sor_decisions_total";
    public static final String SOR_WORKLOAD_TOTAL = PREFIX + "sor_workload_total";
    public static final String SOR_OPEN_CHILD_ORDERS = PREFIX + "sor_open_child_orders";

    // ---- sessions (arch-venue-connectivity) ----
    public static final String SESSIONS_ACTIVE = PREFIX + "sessions_active";
    public static final String SESSION_RECONNECTS_TOTAL = PREFIX + "session_reconnects_total";
    public static final String SESSION_HEARTBEAT_LATENCY_SECONDS = PREFIX + "session_heartbeat_latency_seconds";
    public static final String SESSION_SEQUENCE_GAPS_TOTAL = PREFIX + "session_sequence_gaps_total";

    // ---- audit (arch-observability) ----
    public static final String AUDIT_EVENTS_TOTAL = PREFIX + "audit_events_total";
    public static final String AUDIT_STORE_LAG_SECONDS = PREFIX + "audit_store_lag_seconds";

    // ---- FSM (arch-fsm) ----
    public static final String FSM_TRANSITIONS_TOTAL = PREFIX + "fsm_transitions_total";
    public static final String FSM_INVALID_TRANSITIONS_TOTAL = PREFIX + "fsm_invalid_transitions_total";
    public static final String FSM_STATE_DURATION_SECONDS = PREFIX + "fsm_state_duration_seconds";

    // ---- SBE / Aeron (arch-sbe-aeron-transport) ----
    public static final String SBE_MESSAGES_PUBLISHED_TOTAL = PREFIX + "sbe_messages_published_total";
    public static final String SBE_MESSAGES_CONSUMED_TOTAL = PREFIX + "sbe_messages_consumed_total";
    public static final String SBE_PUBLISH_LATENCY_SECONDS = PREFIX + "sbe_publish_latency_seconds";
    public static final String SBE_QUEUE_DEPTH = PREFIX + "sbe_queue_depth";
    public static final String SBE_BACKPRESSURE_TOTAL = PREFIX + "sbe_backpressure_total";

    // ---- event log (arch-event-sourcing) ----
    public static final String EVENT_LOG_APPEND_TOTAL = PREFIX + "event_log_append_total";
    public static final String EVENT_LOG_APPEND_LATENCY_SECONDS = PREFIX + "event_log_append_latency_seconds";
    public static final String EVENT_LOG_SIZE_BYTES = PREFIX + "event_log_size_bytes";

    // ---- AAA (entry-point-aaa) ----
    public static final String AAA_REQUESTS_TOTAL = PREFIX + "aaa_requests_total";
    public static final String AAA_AUTH_FAILURES_TOTAL = PREFIX + "aaa_auth_failures_total";
    public static final String AAA_DURATION_SECONDS = PREFIX + "aaa_duration_seconds";

    private EmsMetricNames() {}
}
