/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JDK {@code com.sun.net.httpserver} wiring for the {@link RestEdgeBinding} (task 8.10) — a real,
 * dependency-free HTTP edge for the browser UI. All protocol logic lives in the binding; this class
 * only adapts exchanges. Bind to port 0 for an ephemeral port (tests).
 */
public final class RestHttpServer {

  private final RestEdgeBinding binding;
  private final HttpServer server;

  public RestHttpServer(RestEdgeBinding binding, int port) throws IOException {
    this.binding = Objects.requireNonNull(binding, "binding");
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/api/", this::dispatch);
    server.createContext("/scim/", this::dispatch);
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  /** The bound port (useful when constructed with port 0). */
  public int port() {
    return server.getAddress().getPort();
  }

  private void dispatch(HttpExchange exchange) throws IOException {
    try (exchange) {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Map<String, String> headers = new HashMap<>();
      exchange
          .getRequestHeaders()
          .forEach((k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0)));
      RestEdgeBinding.HttpResult result =
          binding.handle(
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              parseQuery(exchange.getRequestURI().getRawQuery()),
              headers,
              body);
      byte[] payload = result.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(result.status(), payload.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(payload);
      }
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> query = new HashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return query;
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        query.put(
            URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
      }
    }
    return query;
  }
}
