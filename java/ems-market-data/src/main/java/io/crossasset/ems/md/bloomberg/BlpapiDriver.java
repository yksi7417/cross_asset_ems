/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import java.util.List;
import java.util.Map;

/**
 * Semantic seam over the BLPAPI {@code //blp/mktdata} subscription model (task 18.13). The build is
 * hermetic (Maven Central only) and the BLPAPI jar ships with the desk's terminal / SAPI install,
 * so the adapter logic in {@link BloombergFeed} talks to this interface: in production the {@link
 * ReflectiveBlpapiDriver} binds {@code com.bloomberglp.blpapi} at runtime; in CI a scripted fake
 * replays the same event protocol deterministically.
 *
 * <p>Event protocol (mirrors BLPAPI): {@code connect} eventually yields {@code onSessionUp} (the
 * session started <em>and</em> the mktdata service opened) or {@code onSessionDown}; a session may
 * bounce — every later {@code onSessionUp} means subscriptions must be re-issued by the caller.
 * Subscription outcomes arrive per correlation ID: {@code onSubscriptionStarted}, {@code
 * onSubscriptionFailure} (entitlement/permission/unknown-symbol), then a stream of {@code onTick}
 * carrying mnemonic-keyed numeric field values.
 */
public interface BlpapiDriver {

  /** Open the session asynchronously; outcomes arrive on {@code events}. */
  void connect(BloombergConfig config, DriverEvents events);

  /** Tear the session down. Idempotent. */
  void disconnect();

  /** Subscribe one security topic with the given real-time field mnemonics. */
  void subscribe(long correlationId, String security, List<String> mnemonics);

  /** Cancel one subscription by correlation ID. */
  void unsubscribe(long correlationId);

  /** Callbacks the driver raises from the BLPAPI event stream. */
  interface DriverEvents {

    /** Session started and {@code //blp/mktdata} opened — (re)issue subscriptions now. */
    void onSessionUp();

    /** Session lost or failed to start. The driver may reconnect and fire onSessionUp again. */
    void onSessionDown(String reason);

    /** Provider acknowledged the subscription; ticks follow. */
    void onSubscriptionStarted(long correlationId);

    /** Provider rejected the subscription (entitlement, permission, unknown symbol). */
    void onSubscriptionFailure(long correlationId, String category, String message);

    /** One market-data message: mnemonic-keyed numeric values present on the message. */
    void onTick(long correlationId, Map<String, Double> mnemonicValues, long atMillis);
  }
}
