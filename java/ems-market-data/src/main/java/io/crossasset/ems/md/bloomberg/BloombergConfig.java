/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import io.crossasset.ems.md.MdField;
import java.util.Map;
import java.util.Objects;

/**
 * Bloomberg adapter configuration (task 18.13). Two modes: {@code DESKTOP} rides the terminal's
 * local bbcomm endpoint (127.0.0.1:8194, terminal identity); {@code SERVER} targets a SAPI host
 * with application authentication. Symbology and field mnemonics are configuration, not code: the
 * defaults cover the common real-time set, and a desk overrides {@code fieldMap} from its BLPAPI
 * field catalog without touching the adapter.
 *
 * @param mode Desktop API vs Server API
 * @param host BLPAPI endpoint host
 * @param port BLPAPI endpoint port (8194 default for both modes)
 * @param authOptions BLPAPI authentication-options string; empty for Desktop (terminal identity)
 * @param symbologyPrefix subscription-topic prefix mapping the system FIGI key into Bloomberg
 *     symbology ({@code /bbgid/} — FIGI is the native key)
 * @param priceScale fixed-point multiplier for double price fields (10_000 = 4 implied decimals,
 *     the system price convention)
 * @param fieldMap {@link MdField} → real-time field mnemonic
 */
public record BloombergConfig(
    Mode mode,
    String host,
    int port,
    String authOptions,
    String symbologyPrefix,
    long priceScale,
    Map<MdField, String> fieldMap) {

  /** Desktop API (terminal on the same machine) vs Server API (SAPI/B-PIPE host). */
  public enum Mode {
    DESKTOP,
    SERVER
  }

  /** Default real-time mnemonics; override per the desk's BLPAPI field catalog. */
  public static final Map<MdField, String> DEFAULT_FIELD_MAP =
      Map.ofEntries(
          Map.entry(MdField.BID, "BID"),
          Map.entry(MdField.ASK, "ASK"),
          Map.entry(MdField.BID_SIZE, "BID_SIZE"),
          Map.entry(MdField.ASK_SIZE, "ASK_SIZE"),
          Map.entry(MdField.LAST, "LAST_PRICE"),
          Map.entry(MdField.LAST_SIZE, "SIZE_LAST_TRADE"),
          Map.entry(MdField.VOLUME, "VOLUME"),
          Map.entry(MdField.OPEN, "OPEN"),
          Map.entry(MdField.HIGH, "HIGH"),
          Map.entry(MdField.LOW, "LOW"),
          Map.entry(MdField.PREV_CLOSE, "PREV_SES_LAST_PRICE"));

  public BloombergConfig {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(authOptions, "authOptions");
    Objects.requireNonNull(symbologyPrefix, "symbologyPrefix");
    if (priceScale <= 0) {
      throw new IllegalArgumentException("priceScale must be positive: " + priceScale);
    }
    fieldMap = Map.copyOf(Objects.requireNonNull(fieldMap, "fieldMap"));
  }

  /** Desktop API against the local terminal (127.0.0.1:8194, terminal identity). */
  public static BloombergConfig desktop() {
    return new BloombergConfig(
        Mode.DESKTOP, "127.0.0.1", 8194, "", "/bbgid/", 10_000, DEFAULT_FIELD_MAP);
  }

  /** Server API with APPNAME_AND_KEY application authentication. */
  public static BloombergConfig server(String host, int port, String applicationName) {
    return new BloombergConfig(
        Mode.SERVER,
        host,
        port,
        "AuthenticationMode=APPLICATION_ONLY;"
            + "ApplicationAuthenticationType=APPNAME_AND_KEY;"
            + "ApplicationName="
            + Objects.requireNonNull(applicationName, "applicationName"),
        "/bbgid/",
        10_000,
        DEFAULT_FIELD_MAP);
  }

  /** Feed identity: distinguishes terminal-backed vs SAPI-backed instances on health topics. */
  public String feedId() {
    return mode == Mode.DESKTOP ? "bloomberg-dapi" : "bloomberg-sapi";
  }
}
