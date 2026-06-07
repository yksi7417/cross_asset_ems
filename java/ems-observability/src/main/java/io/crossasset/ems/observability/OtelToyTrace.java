/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Toy telemetry producer for verifying the OTel pipeline end-to-end.
 *
 * <p>Run against the dev stack (see {@code scripts/dev/start-dev-stack.sh}):
 *
 * <pre>{@code
 * ./gradlew :ems-observability:run
 * # or: ./scripts/dev/run-otel-toy.sh
 * }</pre>
 *
 * <p>Exercises all three telemetry signals through the collector:
 *
 * <ul>
 *   <li><b>Traces</b> → OTLP → collector → Jaeger. A root span {@code ems-toy-root} with three
 *       child spans ({@code stage:validate/route/ack}). Inspect at <a
 *       href="http://localhost:16686">localhost:16686</a>, service {@code ems-otel-toy}.
 *   <li><b>Logs</b> → OTLP → collector → OpenSearch ({@code ems-logs} index). One INFO record per
 *       stage plus a completion record, each emitted inside its span scope so the log carries the
 *       active {@code trace_id}/{@code span_id} for trace-log correlation (see {@code
 *       arch-observability}). View in OpenSearch Dashboards (<a
 *       href="http://localhost:5601">localhost:5601</a>) via index pattern {@code ems-logs*}.
 *   <li><b>Metrics</b> → OTLP → collector → Prometheus. A counter {@code ems.toy.stages.processed}
 *       incremented once per stage, re-exported by the collector on :8889 and scraped by Prometheus
 *       as {@code ems_toy_stages_processed_total}. Query at <a
 *       href="http://localhost:9091">localhost:9091</a>.
 * </ul>
 *
 * <p>This toy intentionally does not depend on Aeron, the FSM, or any EMS module - it verifies only
 * the collector → Jaeger / OpenSearch / Prometheus wiring. SDK init is delegated to {@link
 * EmsOpenTelemetry} so the toy exercises the production factory.
 */
public final class OtelToyTrace {

  private static final String SERVICE_NAME = "ems-otel-toy";
  private static final String SCOPE = "io.crossasset.ems.observability";

  private OtelToyTrace() {}

  public static void main(final String[] args) {
    final EmsOpenTelemetry otel =
        EmsOpenTelemetry.builder(SERVICE_NAME)
            .serviceVersion("0.0.1-toy")
            .deploymentEnv("dev")
            .podName("dev-pod-default")
            .build();

    final Tracer tracer = otel.getTracer(SCOPE);
    final Logger logger = otel.getLogger(SCOPE);
    final Meter meter = otel.getMeter(SCOPE);
    final LongCounter stagesProcessed =
        meter
            .counterBuilder("ems.toy.stages.processed")
            .setDescription("Toy stages processed by OtelToyTrace")
            .setUnit("1")
            .build();

    runWorkload(tracer, logger, stagesProcessed);

    // Force flush so the toy trace + logs + metrics are exported even with a small workload.
    otel.close();
    System.out.println(
        "Toy telemetry emitted. Traces: http://localhost:16686 (service="
            + SERVICE_NAME
            + "). Logs: http://localhost:5601 (index ems-logs)."
            + " Metrics: http://localhost:9091 (ems_toy_stages_processed_total).");
  }

  private static void runWorkload(
      final Tracer tracer, final Logger logger, final LongCounter stagesProcessed) {
    final Span root =
        tracer
            .spanBuilder("ems-toy-root")
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("ems.workload", "toy")
            .startSpan();
    final Scope rootScope = root.makeCurrent();
    try {
      stage("validate", tracer, logger, stagesProcessed);
      stage("route", tracer, logger, stagesProcessed);
      stage("ack", tracer, logger, stagesProcessed);
      emitLog(logger, Severity.INFO, "toy workload complete: 3 stages", "complete");
    } finally {
      rootScope.close();
      root.end();
    }
  }

  private static void stage(
      final String name,
      final Tracer tracer,
      final Logger logger,
      final LongCounter stagesProcessed) {
    final Span span =
        tracer
            .spanBuilder("stage:" + name)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("ems.stage", name)
            .setAttribute("ems.simulated_latency_ms", 10L)
            .startSpan();
    final Scope spanScope = span.makeCurrent();
    try {
      // Emitted inside the span scope: the log record captures the current
      // trace context, so it correlates with this span in OpenSearch.
      emitLog(logger, Severity.INFO, "stage " + name + " processed (10ms)", name);
      stagesProcessed.add(1, Attributes.of(AttributeKey.stringKey("ems.stage"), name));
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        span.recordException(e);
      }
    } finally {
      spanScope.close();
      span.end();
    }
  }

  private static void emitLog(
      final Logger logger, final Severity severity, final String body, final String stage) {
    logger
        .logRecordBuilder()
        .setSeverity(severity)
        .setSeverityText(severity.name())
        .setBody(body)
        .setAttribute(AttributeKey.stringKey("ems.stage"), stage)
        .setAttribute(AttributeKey.stringKey("ems.workload"), "toy")
        .emit();
  }
}
