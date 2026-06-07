/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.time.Duration;

/**
 * Single entry point for building an OpenTelemetry SDK in any EMS JVM
 * component. Implements the configuration described in
 * {@code arch-observability.md}:
 *
 * <ul>
 *   <li>W3C trace context propagation (always enabled, never overridden)
 *   <li>OTLP gRPC exporter for both traces and metrics
 *   <li>Batch span processor with sensible defaults
 *   <li>Resource attributes matching {@link ResourceAttributes} semantic
 *       conventions + the {@code ems.audit.required} and {@code ems.pod}
 *       custom attributes
 *   <li>Periodic metric reader at 10s
 * </ul>
 *
 * <p>Callers should call {@link EmsOpenTelemetry#builder(String)} once
 * at process startup and call {@link Builder#buildAndRegisterGlobal()}.
 * For testing or pre-init reads, {@link Builder#build()} returns a
 * non-global instance.
 *
 * <p>Example:
 *
 * <pre>{@code
 * EmsOpenTelemetry otel = EmsOpenTelemetry.builder("ems-router")
 *     .deploymentEnv("prod")
 *     .podName(System.getenv("POD_NAME"))
 *     .otlpEndpoint(System.getenv().getOrDefault(
 *         "OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4317"))
 *     .buildAndRegisterGlobal();
 * Tracer tracer = otel.getTracer("io.crossasset.ems.router");
 * }</pre>
 */
public final class EmsOpenTelemetry {

    private final OpenTelemetrySdk sdk;
    private final String serviceName;
    private final String instrumentationScope;

    private EmsOpenTelemetry(
            final OpenTelemetrySdk sdk,
            final String serviceName,
            final String instrumentationScope) {
        this.sdk = sdk;
        this.serviceName = serviceName;
        this.instrumentationScope = instrumentationScope;
    }

    public String serviceName() {
        return serviceName;
    }

    public OpenTelemetry sdk() {
        return sdk;
    }

    public Tracer getTracer() {
        return getTracer(instrumentationScope);
    }

    public Tracer getTracer(final String scope) {
        return sdk.getTracer(scope);
    }

    public Meter getMeter() {
        return getMeter(instrumentationScope);
    }

    public Meter getMeter(final String scope) {
        return sdk.getMeter(scope);
    }

    /** Shutdown the SDK, flushing all pending spans and metrics. */
    public void close() {
        sdk.close();
    }

    public static Builder builder(final String serviceName) {
        return new Builder(serviceName);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final String serviceName;
        private String serviceVersion = "0.0.0-dev";
        private String deploymentEnv = "dev";
        private String podName = "dev-pod-default";
        private String hostName = null;
        private String otlpEndpoint =
                System.getenv().getOrDefault(
                        "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");
        private Duration batchTimeout = Duration.ofMillis(500);
        private Duration batchMaxQueueSize = Duration.ofSeconds(10);
        private Duration metricExportInterval = Duration.ofSeconds(10);
        private boolean registerGlobal = false;

        private Builder(final String serviceName) {
            this.serviceName = serviceName;
        }

        public Builder serviceVersion(final String v) {
            this.serviceVersion = v;
            return this;
        }

        public Builder deploymentEnv(final String env) {
            this.deploymentEnv = env;
            return this;
        }

        public Builder podName(final String name) {
            this.podName = name;
            return this;
        }

        public Builder hostName(final String name) {
            this.hostName = name;
            return this;
        }

        public Builder otlpEndpoint(final String endpoint) {
            this.otlpEndpoint = endpoint;
            return this;
        }

        public Builder batchTimeout(final Duration d) {
            this.batchTimeout = d;
            return this;
        }

        public Builder metricExportInterval(final Duration d) {
            this.metricExportInterval = d;
            return this;
        }

        public Builder buildAndRegisterGlobal() {
            this.registerGlobal = true;
            return this;
        }

        public EmsOpenTelemetry build() {
            final Resource resource = buildResource();
            final SdkTracerProvider tracerProvider = buildTracerProvider(resource);
            final SdkMeterProvider meterProvider = buildMeterProvider(resource);

            final OpenTelemetrySdk sdk =
                    OpenTelemetrySdk.builder()
                            .setTracerProvider(tracerProvider)
                            .setMeterProvider(meterProvider)
                            .setPropagators(
                                    ContextPropagators.create(
                                            TextMapPropagator.composite(
                                                    io.opentelemetry.api.trace.propagation
                                                            .W3CTraceContextPropagator
                                                            .getInstance(),
                                                    io.opentelemetry.context.propagation
                                                            .TextMapPropagator
                                                            .noop())))
                            .build();

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        tracerProvider.close();
                                        meterProvider.close();
                                    },
                                    "ems-otel-shutdown"));

            if (registerGlobal) {
                io.opentelemetry.api.GlobalOpenTelemetry.set(sdk);
            }

            return new EmsOpenTelemetry(sdk, serviceName, "io.crossasset.ems." + serviceName);
        }

        private Resource buildResource() {
            final var builder = Resource.getDefault().toBuilder()
                    .put(ResourceAttributes.SERVICE_NAME, serviceName)
                    .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                    .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, deploymentEnv)
                    .put("ems.pod", podName);
            if (hostName != null) {
                builder.put(ResourceAttributes.HOST_NAME, hostName);
            }
            return builder.build();
        }

        private SdkTracerProvider buildTracerProvider(final Resource resource) {
            final OtlpGrpcSpanExporter exporter =
                    OtlpGrpcSpanExporter.builder()
                            .setEndpoint(otlpEndpoint)
                            .setTimeout(batchMaxQueueSize)
                            .build();
            return SdkTracerProvider.builder()
                    .addSpanProcessor(
                            BatchSpanProcessor.builder(exporter)
                                    .setScheduleDelay(batchTimeout)
                                    .setMaxQueueSize(8192)
                                    .setMaxExportBatchSize(4096)
                                    .build())
                    .setResource(resource)
                    .build();
        }

        private SdkMeterProvider buildMeterProvider(final Resource resource) {
            final OtlpGrpcMetricExporter exporter =
                    OtlpGrpcMetricExporter.builder()
                            .setEndpoint(otlpEndpoint)
                            .build();
            return SdkMeterProvider.builder()
                    .registerMetricReader(
                            PeriodicMetricReader.builder(exporter)
                                    .setInterval(metricExportInterval)
                                    .build())
                    .setResource(resource)
                    .build();
        }
    }
}
