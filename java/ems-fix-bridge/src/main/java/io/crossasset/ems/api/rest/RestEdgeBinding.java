/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.ApiItem;
import io.crossasset.ems.api.ApiOperation;
import io.crossasset.ems.api.ApiRequest;
import io.crossasset.ems.api.ApiResponse;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.BatchOptions;
import io.crossasset.ems.api.ItemResult;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.basket.BasketService;
import io.crossasset.ems.api.control.KillSwitchService;
import io.crossasset.ems.api.control.KillSwitchState;
import io.crossasset.ems.api.notify.NotificationService;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.SecurityMasterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * REST edge binding for the browser UI (task 8.10): terminates HTTP-shaped requests and speaks the
 * 8.4 {@link ApiSurface} inward — one operation set, one identity, one recovery story, per
 * arch-api-first.md. Pure request → response mapping (no sockets, no clock); {@link RestHttpServer}
 * provides the JDK HTTP wiring and a WebSocket upgrade rides the same handler when the Perspective
 * desktop lands (18.1).
 *
 * <p>Routes: {@code POST /api/v1/logon} {token} → {sessionId}; {@code POST /api/v1/{operation}}
 * with the batch envelope JSON (header {@code X-EMS-Session} carries the session); {@code GET
 * /api/v1/events?topic=&from=&max=} — the resumable subscription stream as a cursor fetch: the
 * client holds its last delivered seq ({@code Last-Event-ID} semantics, {@code from} = lastId+1)
 * and the server holds no per-client state, so a refreshed browser resumes with no missed and no
 * doubled events.
 */
public final class RestEdgeBinding {

  /** Minimal HTTP response: status code + JSON body. */
  public record HttpResult(int status, String body) {}

  private final ObjectMapper mapper = new ObjectMapper();
  private final AaaService aaa;
  private final ApiSurface api;
  private final SubscriptionRegistry subscriptions;
  private final @org.jspecify.annotations.Nullable SecurityMasterService secMaster;
  private final @org.jspecify.annotations.Nullable BasketService baskets;
  private final @org.jspecify.annotations.Nullable KillSwitchService killSwitch;
  private final @org.jspecify.annotations.Nullable NotificationService notifications;

  public RestEdgeBinding(AaaService aaa, ApiSurface api, SubscriptionRegistry subscriptions) {
    this(aaa, api, subscriptions, null, null);
  }

  /**
   * With a security master, {@code GET /api/v1/instruments/{figi}} serves ticket lookups (18.2).
   */
  public RestEdgeBinding(
      AaaService aaa,
      ApiSurface api,
      SubscriptionRegistry subscriptions,
      @org.jspecify.annotations.Nullable SecurityMasterService secMaster) {
    this(aaa, api, subscriptions, secMaster, null);
  }

  /** With a basket service, the {@code /api/v1/baskets} routes serve program trading (18.3). */
  public RestEdgeBinding(
      AaaService aaa,
      ApiSurface api,
      SubscriptionRegistry subscriptions,
      @org.jspecify.annotations.Nullable SecurityMasterService secMaster,
      @org.jspecify.annotations.Nullable BasketService baskets) {
    this(aaa, api, subscriptions, secMaster, baskets, null);
  }

  /** With a kill-switch service, the {@code /api/v1/kill} routes serve the control path (18.4). */
  public RestEdgeBinding(
      AaaService aaa,
      ApiSurface api,
      SubscriptionRegistry subscriptions,
      @org.jspecify.annotations.Nullable SecurityMasterService secMaster,
      @org.jspecify.annotations.Nullable BasketService baskets,
      @org.jspecify.annotations.Nullable KillSwitchService killSwitch) {
    this(aaa, api, subscriptions, secMaster, baskets, killSwitch, null);
  }

  /** With a notification service, {@code POST /api/v1/notifications/{id}/ack} works (18.8). */
  public RestEdgeBinding(
      AaaService aaa,
      ApiSurface api,
      SubscriptionRegistry subscriptions,
      @org.jspecify.annotations.Nullable SecurityMasterService secMaster,
      @org.jspecify.annotations.Nullable BasketService baskets,
      @org.jspecify.annotations.Nullable KillSwitchService killSwitch,
      @org.jspecify.annotations.Nullable NotificationService notifications) {
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.api = Objects.requireNonNull(api, "api");
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.secMaster = secMaster;
    this.baskets = baskets;
    this.killSwitch = killSwitch;
    this.notifications = notifications;
  }

  /**
   * Handle one HTTP-shaped call. {@code path} excludes the query string; {@code query} carries
   * decoded query params; {@code headers} are case-insensitive-normalized to lower-case keys.
   */
  public HttpResult handle(
      String method,
      String path,
      Map<String, String> query,
      Map<String, String> headers,
      String body) {
    try {
      if ("POST".equals(method) && "/api/v1/logon".equals(path)) {
        return logon(body);
      }
      if ("GET".equals(method) && "/api/v1/events".equals(path)) {
        return events(query);
      }
      if ("GET".equals(method) && path.startsWith("/api/v1/instruments/")) {
        return instrument(path.substring("/api/v1/instruments/".length()));
      }
      if (path.startsWith("/api/v1/baskets")) {
        return basketsRoute(method, path, headers, body);
      }
      if (path.startsWith("/api/v1/kill")) {
        return killRoute(method, path, headers, body);
      }
      if ("POST".equals(method)
          && path.startsWith("/api/v1/notifications/")
          && path.endsWith("/ack")) {
        return ackNotification(path, headers);
      }
      if ("POST".equals(method) && path.startsWith("/api/v1/")) {
        return operation(path.substring("/api/v1/".length()), headers, body);
      }
      return error(404, "Unknown route: " + method + " " + path);
    } catch (BadRequest e) {
      return error(400, e.getMessage());
    } catch (Exception e) {
      return error(500, "Internal error: " + e.getMessage());
    }
  }

  // ── Routes ───────────────────────────────────────────────────────────────────

  private HttpResult logon(String body) throws Exception {
    JsonNode json = mapper.readTree(body);
    String token = requireText(json, "token");
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, token));
    if (outcome instanceof LogonOutcome.Rejected rejected) {
      ObjectNode out = mapper.createObjectNode();
      out.put("error", rejected.rejectCode() + ": " + rejected.message());
      return new HttpResult(401, out.toString());
    }
    var session = ((LogonOutcome.Accepted) outcome).session();
    ObjectNode out = mapper.createObjectNode();
    out.put("sessionId", session.sessionId());
    out.put("firm", session.identity().firmId());
    out.put("desk", session.identity().deskId());
    out.put("user", session.identity().userId());
    return new HttpResult(200, out.toString());
  }

  private HttpResult events(Map<String, String> query) {
    String topic = query.get("topic");
    if (topic == null || topic.isBlank()) {
      throw new BadRequest("Query param 'topic' is required.");
    }
    long from = query.containsKey("from") ? Long.parseLong(query.get("from")) : 1L;
    int max = query.containsKey("max") ? Integer.parseInt(query.get("max")) : 100;
    List<ApiEvent> events = subscriptions.fetch(topic, from, max);
    ObjectNode out = mapper.createObjectNode();
    ArrayNode array = out.putArray("events");
    for (ApiEvent event : events) {
      ObjectNode node = array.addObject();
      node.put("topic", event.topic());
      node.put("seq", event.seq());
      node.put("type", event.type());
      node.put("refId", event.refId());
      node.put("payload", event.payload());
    }
    out.put("nextFrom", events.isEmpty() ? from : events.get(events.size() - 1).seq() + 1);
    return new HttpResult(200, out.toString());
  }

  /** Instrument reference lookup for the ticket (18.2): asset class drives the field layout. */
  private HttpResult instrument(String figi) {
    if (secMaster == null) {
      return error(404, "Instrument lookup not configured on this edge.");
    }
    var found = secMaster.currentSnapshot().lookup(figi);
    if (found.isEmpty()) {
      return error(404, "Unknown instrument: " + figi);
    }
    InstrumentVersioned instrument = found.get();
    ObjectNode out = mapper.createObjectNode();
    out.put("figi", instrument.core().figi());
    out.put("name", instrument.core().displayName());
    out.put("assetClass", instrument.core().assetClass().name());
    out.put("type", instrument.core().instrumentType().name());
    out.put("currency", instrument.core().currency().name());
    out.put("settlement", instrument.core().settlementConvention().name());
    return new HttpResult(200, out.toString());
  }

  /**
   * Basket routes (18.3): {@code GET /api/v1/baskets} lists; {@code POST /api/v1/baskets} creates
   * from CSV ({name, sessionSeq, csv}) or staged orders ({name, orderIds}); {@code POST
   * /api/v1/baskets/{id}/wave} routes a wave ({fractionBp, venueMic}). Session-gated; wave slices
   * flow through the same RouteManager as single-order tickets.
   */
  private HttpResult basketsRoute(
      String method, String path, Map<String, String> headers, String body) throws Exception {
    if (baskets == null) {
      return error(404, "Basket service not configured on this edge.");
    }
    long sessionId = requireSession(headers);
    if ("GET".equals(method) && "/api/v1/baskets".equals(path)) {
      ObjectNode out = mapper.createObjectNode();
      ArrayNode array = out.putArray("baskets");
      for (BasketService.Basket basket : baskets.list()) {
        ObjectNode node = array.addObject();
        node.put("basketId", basket.basketId());
        node.put("name", basket.name());
        node.put("orders", basket.orderIds().size());
        node.put("waves", basket.waves());
      }
      return new HttpResult(200, out.toString());
    }
    if ("POST".equals(method) && "/api/v1/baskets".equals(path)) {
      JsonNode json = mapper.readTree(body);
      String name = requireText(json, "name");
      if (json.hasNonNull("csv")) {
        var result =
            baskets.createFromCsv(
                name,
                requireText(json, "uploadId"),
                sessionId,
                json.path("sessionSeq").asLong(0),
                json.path("csv").asText());
        ObjectNode out = mapper.createObjectNode();
        out.put("uploadId", result.uploadId());
        out.put("fileError", result.fileError());
        out.put("accepted", result.accepted());
        out.put("rejected", result.rejected());
        return new HttpResult(result.fileError() == null ? 200 : 400, out.toString());
      }
      List<String> orderIds = new ArrayList<>();
      for (JsonNode id : json.path("orderIds")) {
        orderIds.add(id.asText());
      }
      try {
        BasketService.Basket basket = baskets.createFromOrders(name, orderIds);
        ObjectNode out = mapper.createObjectNode();
        out.put("basketId", basket.basketId());
        return new HttpResult(200, out.toString());
      } catch (IllegalArgumentException e) {
        return error(400, e.getMessage());
      }
    }
    if ("POST".equals(method) && path.endsWith("/wave")) {
      String basketId =
          path.substring("/api/v1/baskets/".length(), path.length() - "/wave".length());
      JsonNode json = mapper.readTree(body);
      try {
        BasketService.WaveResult result =
            baskets.waveRoute(
                basketId,
                json.path("fractionBp").asInt(0),
                requireText(json, "venueMic"),
                sessionId);
        ObjectNode out = mapper.createObjectNode();
        out.put("basketId", result.basketId());
        out.put("wave", result.wave());
        ArrayNode lines = out.putArray("lines");
        for (BasketService.WaveLine line : result.lines()) {
          ObjectNode node = lines.addObject();
          node.put("orderId", line.orderId());
          node.put("ok", line.ok());
          node.put("detail", line.detail());
        }
        return new HttpResult(200, out.toString());
      } catch (IllegalArgumentException e) {
        return error(400, e.getMessage());
      }
    }
    return error(404, "Unknown basket route: " + method + " " + path);
  }

  /**
   * Kill-switch routes (18.4): {@code GET /api/v1/kill} status; {@code POST /api/v1/kill} engages
   * ({kind, value, reason}); {@code POST /api/v1/kill/release} releases. Authorization (the
   * #kill-switch tag) is enforced by the service; the audit (who/scope/reason/outcomes) is the
   * response body.
   */
  private HttpResult killRoute(String method, String path, Map<String, String> headers, String body)
      throws Exception {
    if (killSwitch == null) {
      return error(404, "Kill switch not configured on this edge.");
    }
    long sessionId = requireSession(headers);
    if ("GET".equals(method) && "/api/v1/kill".equals(path)) {
      ObjectNode out = mapper.createObjectNode();
      ArrayNode engaged = out.putArray("engaged");
      for (KillSwitchState.Scope scope : killSwitch.engaged()) {
        ObjectNode node = engaged.addObject();
        node.put("kind", scope.kind().name());
        node.put("value", scope.value());
      }
      return new HttpResult(200, out.toString());
    }
    if ("POST".equals(method)
        && ("/api/v1/kill".equals(path) || "/api/v1/kill/release".equals(path))) {
      JsonNode json = mapper.readTree(body);
      KillSwitchState.Scope scope =
          new KillSwitchState.Scope(
              KillSwitchState.Kind.valueOf(requireText(json, "kind")), requireText(json, "value"));
      String reason = requireText(json, "reason");
      KillSwitchService.KillResult result =
          path.endsWith("/release")
              ? killSwitch.release(scope, sessionId, reason)
              : killSwitch.engage(scope, sessionId, reason);
      if (result instanceof KillSwitchService.KillResult.Rejected rejected) {
        ObjectNode out = mapper.createObjectNode();
        out.put("error", rejected.code() + ": " + rejected.message());
        return new HttpResult(403, out.toString());
      }
      KillSwitchService.KillAudit audit = ((KillSwitchService.KillResult.Done) result).audit();
      ObjectNode out = mapper.createObjectNode();
      out.put("action", audit.action());
      out.put("by", audit.by());
      out.put("targets", audit.outcomes().size());
      out.put("failures", audit.failures());
      ArrayNode outcomes = out.putArray("outcomes");
      for (KillSwitchService.TargetOutcome outcome : audit.outcomes()) {
        ObjectNode node = outcomes.addObject();
        node.put("targetId", outcome.targetId());
        node.put("action", outcome.action());
        node.put("ok", outcome.ok());
        node.put("detail", outcome.detail());
      }
      return new HttpResult(200, out.toString());
    }
    return error(404, "Unknown kill route: " + method + " " + path);
  }

  /** Acknowledge a notification (18.8); the acker is the session identity. */
  private HttpResult ackNotification(String path, Map<String, String> headers) {
    if (notifications == null) {
      return error(404, "Notification service not configured on this edge.");
    }
    long sessionId = requireSession(headers);
    String id = path.substring("/api/v1/notifications/".length(), path.length() - "/ack".length());
    String by =
        aaa.sessionInfo(sessionId).map(s -> s.identity().userId()).orElse("session-" + sessionId);
    boolean acked = notifications.ack(id, by, System.currentTimeMillis());
    ObjectNode out = mapper.createObjectNode();
    out.put("acked", acked);
    return new HttpResult(acked ? 200 : 409, out.toString());
  }

  private long requireSession(Map<String, String> headers) {
    String sessionHeader = headers.get("x-ems-session");
    if (sessionHeader == null) {
      throw new BadRequest("Header X-EMS-Session is required.");
    }
    long sessionId = Long.parseLong(sessionHeader);
    if (aaa.sessionInfo(sessionId).isEmpty()) {
      throw new BadRequest("Session not found or expired.");
    }
    return sessionId;
  }

  private HttpResult operation(String opName, Map<String, String> headers, String body)
      throws Exception {
    ApiOperation operation;
    try {
      operation = ApiOperation.valueOf(opName.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequest("Unknown operation: " + opName);
    }
    String sessionHeader = headers.get("x-ems-session");
    if (sessionHeader == null) {
      throw new BadRequest("Header X-EMS-Session is required.");
    }
    long sessionId = Long.parseLong(sessionHeader);

    JsonNode json = mapper.readTree(body);
    String requestId = requireText(json, "requestId");
    long sessionSeq = json.path("sessionSeq").asLong(0);
    JsonNode itemsNode = json.path("items");
    if (!itemsNode.isArray() || itemsNode.isEmpty()) {
      throw new BadRequest("'items' must be a non-empty array.");
    }
    List<ApiItem> items = new ArrayList<>();
    for (JsonNode item : itemsNode) {
      items.add(toItem(operation, item));
    }
    BatchOptions options = toOptions(json.path("options"));

    ApiResponse response =
        api.execute(new ApiRequest(requestId, sessionId, sessionSeq, operation, items, options));
    return new HttpResult(200, render(response));
  }

  // ── JSON mapping ─────────────────────────────────────────────────────────────

  private ApiItem toItem(ApiOperation operation, JsonNode n) {
    return switch (operation) {
      case PREVIEW_VALIDATE -> new ApiItem.PreviewOrder(requireText(n, "figi"));
      case STAGE_ORDERS ->
          new ApiItem.StageOrder(
              requireText(n, "clOrdId"),
              requireText(n, "figi"),
              n.path("side").asInt(),
              n.path("qty").asLong(),
              optionalLong(n, "price"),
              requireText(n, "account"),
              n.path("tif").asInt(0));
      case AMEND_ORDERS ->
          new ApiItem.AmendOrder(
              requireText(n, "orderId"), optionalLong(n, "qty"), optionalLong(n, "price"));
      case CANCEL_ORDERS -> new ApiItem.CancelOrder(requireText(n, "orderId"));
      case MARK_READY -> new ApiItem.MarkReady(requireText(n, "orderId"));
      case ROUTE_ORDERS ->
          new ApiItem.RouteOrder(
              requireText(n, "orderId"),
              requireText(n, "venueMic"),
              n.path("qty").asLong(),
              optionalLong(n, "price"));
      case CANCEL_ROUTES -> new ApiItem.CancelRoute(requireText(n, "routeId"));
      case SUBSCRIBE -> new ApiItem.Subscribe(requireText(n, "topic"), n.path("fromSeq").asLong(1));
      case UNSUBSCRIBE -> new ApiItem.Unsubscribe(requireText(n, "subscriptionId"));
    };
  }

  private BatchOptions toOptions(JsonNode n) {
    if (n.isMissingNode() || n.isNull()) {
      return BatchOptions.DEFAULT;
    }
    boolean partialOk = n.path("partialOk").asBoolean(true);
    BatchOptions.OnError onError =
        "STOP".equalsIgnoreCase(n.path("onError").asText("CONTINUE"))
            ? BatchOptions.OnError.STOP
            : BatchOptions.OnError.CONTINUE;
    return new BatchOptions(partialOk, onError);
  }

  private String render(ApiResponse response) {
    ObjectNode out = mapper.createObjectNode();
    out.put("requestId", response.requestId());
    ObjectNode summary = out.putObject("summary");
    summary.put("ok", response.summary().ok());
    summary.put("rejected", response.summary().rejected());
    summary.put("deferred", response.summary().deferred());
    ArrayNode results = out.putArray("results");
    for (ItemResult result : response.results()) {
      ObjectNode node = results.addObject();
      node.put("status", result.status().name());
      node.put("refId", result.refId());
      node.put("errorCode", result.errorCode());
      node.put("errorMessage", result.errorMessage());
    }
    return out.toString();
  }

  private String requireText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
      throw new BadRequest("Field '" + field + "' is required.");
    }
    return value.asText();
  }

  private static Long optionalLong(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asLong();
  }

  private HttpResult error(int status, String message) {
    ObjectNode out = mapper.createObjectNode();
    out.put("error", message);
    return new HttpResult(status, out.toString());
  }

  private static final class BadRequest extends RuntimeException {
    private static final long serialVersionUID = 1L;

    BadRequest(String message) {
      super(message);
    }
  }
}
