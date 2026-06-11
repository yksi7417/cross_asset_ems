/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production {@link BlpapiDriver}: binds {@code com.bloomberglp.blpapi} by reflection at {@link
 * #connect} time, because the BLPAPI jar is licensed with the desk's terminal/SAPI install and is
 * not in the hermetic build (Maven Central only). Deploy by placing the desk's {@code blpapi-*.jar}
 * on the runtime classpath; a missing jar fails fast with a clear message via {@code
 * onSessionDown}.
 *
 * <p>Deliberately thin — protocol demux only (BLPAPI events → {@link BlpapiDriver.DriverEvents}),
 * zero business logic — because this class cannot run in CI. Everything testable lives in {@link
 * BloombergFeed}. Verified manually on a terminal machine via {@link BloombergFeedMain}.
 */
public final class ReflectiveBlpapiDriver implements BlpapiDriver {

  private static final String PKG = "com.bloomberglp.blpapi.";

  private Object session;
  private Class<?> sessionClass;
  private Class<?> correlationIdClass;
  private DriverEvents events;

  @Override
  public synchronized void connect(BloombergConfig config, DriverEvents events) {
    this.events = Objects.requireNonNull(events, "events");
    try {
      Class<?> optionsClass = Class.forName(PKG + "SessionOptions");
      Object options = optionsClass.getConstructor().newInstance();
      invoke(options, "setServerHost", new Class<?>[] {String.class}, config.host());
      invoke(options, "setServerPort", new Class<?>[] {int.class}, config.port());
      if (!config.authOptions().isEmpty()) {
        invoke(
            options,
            "setAuthenticationOptions",
            new Class<?>[] {String.class},
            config.authOptions());
      }

      Class<?> handlerInterface = Class.forName(PKG + "EventHandler");
      Object handler =
          Proxy.newProxyInstance(
              handlerInterface.getClassLoader(),
              new Class<?>[] {handlerInterface},
              (proxy, method, args) -> {
                if ("processEvent".equals(method.getName())) {
                  handleEvent(args[0]);
                }
                return null;
              });

      sessionClass = Class.forName(PKG + "Session");
      correlationIdClass = Class.forName(PKG + "CorrelationID");
      session =
          sessionClass.getConstructor(optionsClass, handlerInterface).newInstance(options, handler);
      invoke(session, "startAsync", new Class<?>[] {});
    } catch (ClassNotFoundException e) {
      events.onSessionDown(
          "BLPAPI jar not on classpath (com.bloomberglp.blpapi); "
              + "install the desk's blpapi jar to use the Bloomberg feed");
    } catch (ReflectiveOperationException e) {
      events.onSessionDown("BLPAPI binding failed: " + cause(e));
    }
  }

  @Override
  public synchronized void disconnect() {
    if (session == null) {
      return;
    }
    try {
      invoke(session, "stop", new Class<?>[] {});
    } catch (ReflectiveOperationException e) {
      // Already stopping/stopped — nothing to surface.
    }
    session = null;
  }

  @Override
  public synchronized void subscribe(long correlationId, String security, List<String> mnemonics) {
    try {
      Class<?> subscriptionClass = Class.forName(PKG + "Subscription");
      Class<?> subscriptionListClass = Class.forName(PKG + "SubscriptionList");
      Object correlation = correlationIdClass.getConstructor(long.class).newInstance(correlationId);
      Object subscription =
          subscriptionClass
              .getConstructor(String.class, String.class, correlationIdClass)
              .newInstance(security, String.join(",", mnemonics), correlation);
      Object list = subscriptionListClass.getConstructor().newInstance();
      invoke(list, "add", new Class<?>[] {subscriptionClass}, subscription);
      invoke(session, "subscribe", new Class<?>[] {subscriptionListClass}, list);
    } catch (ReflectiveOperationException e) {
      events.onSubscriptionFailure(correlationId, "SUBSCRIBE_ERROR", cause(e));
    }
  }

  @Override
  public synchronized void unsubscribe(long correlationId) {
    try {
      Object correlation = correlationIdClass.getConstructor(long.class).newInstance(correlationId);
      invoke(session, "cancel", new Class<?>[] {correlationIdClass}, correlation);
    } catch (ReflectiveOperationException e) {
      // Cancel of a dead session — nothing to surface.
    }
  }

  // ── BLPAPI event demux ───────────────────────────────────────────────────────

  private void handleEvent(Object event) {
    try {
      boolean subscriptionData = "SUBSCRIPTION_DATA".equals(eventTypeName(event));
      for (Object message : (Iterable<?>) event) {
        if (subscriptionData) {
          tick(message);
          continue;
        }
        String type = invoke(message, "messageType", new Class<?>[] {}).toString();
        switch (type) {
          case "SessionStarted" ->
              invoke(session, "openServiceAsync", new Class<?>[] {String.class}, "//blp/mktdata");
          case "ServiceOpened" -> events.onSessionUp();
          case "SessionStartupFailure", "SessionTerminated", "SessionConnectionDown" ->
              events.onSessionDown(type);
          case "SubscriptionStarted" -> events.onSubscriptionStarted(correlationOf(message));
          case "SubscriptionFailure", "SubscriptionTerminated" ->
              events.onSubscriptionFailure(correlationOf(message), type, message.toString());
          default -> {
            // Heartbeats, service status, slow-consumer warnings — not protocol-relevant here.
          }
        }
      }
    } catch (ReflectiveOperationException e) {
      events.onSessionDown("BLPAPI event decode failed: " + cause(e));
    }
  }

  /** Numeric elements of a SUBSCRIPTION_DATA message, keyed by mnemonic. */
  private void tick(Object message) throws ReflectiveOperationException {
    long correlationId = correlationOf(message);
    Object element = invoke(message, "asElement", new Class<?>[] {});
    int n = (Integer) invoke(element, "numElements", new Class<?>[] {});
    Map<String, Double> values = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) {
      Object child = invoke(element, "getElement", new Class<?>[] {int.class}, i);
      boolean isNull = (Boolean) invoke(child, "isNull", new Class<?>[] {});
      if (isNull) {
        continue;
      }
      try {
        double value = (Double) invoke(child, "getValueAsFloat64", new Class<?>[] {});
        String name = invoke(child, "name", new Class<?>[] {}).toString();
        values.put(name, value);
      } catch (ReflectiveOperationException nonNumeric) {
        // Strings, dates, enums — only numeric fields cross the SPI.
      }
    }
    if (!values.isEmpty()) {
      events.onTick(correlationId, values, System.currentTimeMillis());
    }
  }

  private long correlationOf(Object message) throws ReflectiveOperationException {
    Object correlation = invoke(message, "correlationID", new Class<?>[] {});
    return (Long) invoke(correlation, "value", new Class<?>[] {});
  }

  private String eventTypeName(Object event) throws ReflectiveOperationException {
    return invoke(event, "eventType", new Class<?>[] {}).toString();
  }

  private static Object invoke(Object target, String name, Class<?>[] sig, Object... args)
      throws ReflectiveOperationException {
    Method method = target.getClass().getMethod(name, sig);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private static String cause(ReflectiveOperationException e) {
    Throwable t = e instanceof InvocationTargetException ite ? ite.getCause() : e;
    return t.getClass().getSimpleName() + ": " + t.getMessage();
  }
}
