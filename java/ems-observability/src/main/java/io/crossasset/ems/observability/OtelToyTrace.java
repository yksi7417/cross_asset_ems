/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;

/**
 * Toy trace producer for verifying the OTel collector + Jaeger pipeline.
 *
 * <p>Run against the dev stack (see {@code scripts/dev/start-dev-stack.sh}):
 *
 * <pre>{@code
 *   ./gradlew :ems-observability:run -PmainClass=io.crossasset.ems.observability.OtelToyTrace
 * }</pre>
 *
 * <p>After running, open Jaeger UI at <a href="http://localhost:16686">localhost:16686</a>
 * and look for service {@code ems-otel-toy}. The trace tree should show a root span with three
 * child spans nested 1-deep, exercising the {@code @trace.parent_id} relationship that drives
 * {@code arch-observability}'s distributed-trace correlation.
 *
 * <p>This toy intentionally does not depend on Aeron, the FSM, or any EMS module - it verifies
 * only the OTel collector + Jaeger pipeline. SDK init is delegated to
 * {@link EmsOpenTelemetry} so the toy exercises the production factory.
 */
public final class OtelToyTrace {

    private static final String SERVICE_NAME = "ems-otel-toy";

    private OtelToyTrace() {}

    public static void main(final String[] args) {
        final EmsOpenTelemetry otel =
                EmsOpenTelemetry.builder(SERVICE_NAME)
                        .serviceVersion("0.0.1-toy")
                        .deploymentEnv("dev")
                        .podName("dev-pod-default")
                        .build();

        final Tracer tracer = otel.getTracer("io.crossasset.ems.observability");

        runWorkload(tracer);

        // Force flush so the toy trace is exported even with a small workload.
        otel.close();
        System.out.println("Toy trace emitted. Inspect at http://localhost:16686 (service=" + SERVICE_NAME + ").");
    }

    private static OpenTelemetry init(final String otlpEndpoint) {
        final Resource resource =
                Resource.getDefault().toBuilder()
                        .put(AttributeKey.stringKey("service.name"), SERVICE_NAME)
                        .put(AttributeKey.stringKey("service.version"), "0.0.1-toy")
                        .put(AttributeKey.stringKey("deployment.environment"), "dev")
                        .put(AttributeKey.stringKey("ems.pod"), "dev-pod-default")
                        .build();

        final SdkTracerProvider tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(
                                BatchSpanProcessor.builder(
                                                OtlpGrpcSpanExporter.builder()
                                                        .setEndpoint(otlpEndpoint)
                                                        .setTimeout(Duration.ofSeconds(5))
                                                        .build())
                                        .setScheduleDelay(Duration.ofMillis(500))
                                        .build())
                        .setResource(resource)
                        .build();

        final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));
        return sdk;
    }

    private static void runWorkload(final Tracer tracer) {
        final Span root =
                tracer.spanBuilder("ems-toy-root")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("ems.workload", "toy")
                        .startSpan();
        final Scope rootScope = root.makeCurrent();
        try {
            stage("validate", tracer);
            stage("route", tracer);
            stage("ack", tracer);
        } finally {
            rootScope.close();
            root.end();
        }
    }

    private static void stage(final String name, final Tracer tracer) {
        final Span span =
                tracer.spanBuilder("stage:" + name)
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("ems.stage", name)
                        .setAttribute("ems.simulated_latency_ms", 10L)
                        .startSpan();
        final Scope spanScope = span.makeCurrent();
        try {
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
}
