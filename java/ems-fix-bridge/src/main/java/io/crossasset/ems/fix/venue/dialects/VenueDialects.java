/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue.dialects;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.venue.VenueDialect;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The venue dialect catalogue (tasks 11.3–11.10): each factory encodes what actually distinguishes
 * that venue's FIX surface — order-type restrictions, quantity conventions, mandatory tags —
 * sourced from the venues' public FIX specifications. Built and tested against the {@code
 * FixVenueSimulator}; certification against real UAT endpoints exercises these same rules when
 * credentials exist (the venues' cert scripts test exactly these behaviors).
 *
 * <p>Tag conventions used here: standard FIX where one exists (64 SettlDate, 76 ExecBroker, 18
 * ExecInst, 1028 ManualOrderIndicator); documented user-defined tags for venue extensions (20001
 * UTI, 20002 clearing house, 20003 self-trade-prevention key, 20004 relationship bank).
 */
public final class VenueDialects {

  private VenueDialects() {}

  private record Rules(
      String id,
      String mic,
      Set<Capability> caps,
      java.util.function.Function<VenueRouteRequest, Optional<String>> validator,
      java.util.function.BiConsumer<FixMessage.Builder, VenueRouteRequest> customizer)
      implements VenueDialect {

    @Override
    public Set<Capability> capabilities() {
      return caps;
    }

    @Override
    public Optional<String> validate(VenueRouteRequest request) {
      return validator.apply(request);
    }

    @Override
    public void customize(FixMessage.Builder builder, VenueRouteRequest request) {
      customizer.accept(builder, request);
    }
  }

  /**
   * 11.3 Tradeweb (TWEB): dealer-to-client FI marketplace. Orders carry the settlement date (bonds
   * settle T+1; the venue rejects undated tickets) and price is clean-per-100. Tradeweb is
   * RFQ-first: the adapter advertises SUPPORTS_RFQ so the router can prefer the 11.13 flow.
   */
  public static VenueDialect tradeweb(String settlDate) {
    return new Rules(
        "tradeweb",
        "TWEB",
        Set.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_RFQ, Capability.SUPPORTS_CANCEL),
        request ->
            request.isMarket()
                ? Optional.of("Tradeweb FI takes priced (limit/RFQ) orders only")
                : Optional.empty(),
        (b, request) -> b.field(64, settlDate));
  }

  /**
   * 11.4 BrokerTec (BTEC): the treasury CLOB. LIMIT ONLY — there is no market order on the book;
   * size trades in $1M-face increments with a $1M minimum; the venue has no cancel/replace (work it
   * as cancel + new, so SUPPORTS_REPLACE is absent).
   */
  public static VenueDialect brokerTec() {
    return new Rules(
        "brokertec",
        "BTEC",
        Set.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_CANCEL),
        request -> {
          if (request.isMarket()) {
            return Optional.of("BrokerTec is a limit-order book: no market orders");
          }
          if (request.qty() < 1_000_000L || request.qty() % 1_000_000L != 0) {
            return Optional.of("BrokerTec trades $1M-face increments, got " + request.qty());
          }
          return Optional.empty();
        },
        (b, request) -> b.field(59, 0)); // TimeInForce DAY mandatory on the book
  }

  /**
   * 11.5 EBS (EBSX): the FX spot CLOB. Quantity is BASE-currency millions ($1M minimum, $1M
   * increments — the EBS dealing convention), price is the spot rate, value date rides tag 64 (spot
   * = T+2 per pair convention, supplied by the calendar).
   */
  public static VenueDialect ebs(String valueDate) {
    return new Rules(
        "ebs",
        "EBSX",
        Set.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_CANCEL),
        request -> {
          if (request.isMarket()) {
            return Optional.of("EBS quotes a dealable book: priced orders only");
          }
          if (request.qty() < 1_000_000L || request.qty() % 1_000_000L != 0) {
            return Optional.of("EBS deals base-ccy millions, got " + request.qty());
          }
          return Optional.empty();
        },
        (b, request) -> b.field(64, valueDate));
  }

  /**
   * 11.6 FXall (FXAL): relationship-FX RFQ. Orders reference the quote being hit and carry the
   * value date; dealers retain last look, so the adapter never advertises firm-CLOB semantics — the
   * route can come back {@code rejected} after a hold (the 18.11 last-look stats apply).
   */
  public static VenueDialect fxall(String valueDate) {
    return new Rules(
        "fxall",
        "FXAL",
        Set.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_RFQ, Capability.SUPPORTS_CANCEL),
        request ->
            request.isMarket()
                ? Optional.of("FXall executes against quoted rates: priced orders only")
                : Optional.empty(),
        (b, request) -> {
          b.field(64, valueDate);
          b.field(18, "L"); // ExecInst: dealer last look acknowledged
        });
  }

  /**
   * 11.7 Bloomberg EMSX: the broker-routing hub — the order names its TARGET BROKER (tag 76) and
   * may carry broker-algo strategy parameters (the 11.16 FIXatdl catalog encodes 847/957; EMSX
   * forwards them verbatim). Routes without a broker have nowhere to go.
   */
  public static VenueDialect emsx(String broker, Map<String, String> strategyParams) {
    return new Rules(
        "emsx",
        "EMSX",
        Set.of(
            Capability.SUPPORTS_MARKET,
            Capability.SUPPORTS_LIMIT,
            Capability.SUPPORTS_CANCEL,
            Capability.SUPPORTS_REPLACE),
        request ->
            broker == null || broker.isBlank()
                ? Optional.of("EMSX routes to a named broker: none configured")
                : Optional.empty(),
        (b, request) -> {
          b.field(76, broker); // ExecBroker
          if (!strategyParams.isEmpty()) {
            b.field(957, strategyParams.size());
            for (Map.Entry<String, String> e : strategyParams.entrySet()) {
              b.repeatedField(958, e.getKey());
              b.repeatedField(960, e.getValue());
            }
          }
        });
  }

  /**
   * 11.8 Bloomberg SEF (BSEF): CFTC swaps execution. Every order carries its deterministic UTI (tag
   * 20001 — derived from reporting LEI + clOrdId exactly like the 12.8 SDR adapter, never random)
   * and the clearing house (tag 20002): SEF-executed MAT swaps clear mandatorily.
   */
  public static VenueDialect bloombergSef(String reportingLei, String clearingHouse) {
    return new Rules(
        "bbg-sef",
        "BSEF",
        Set.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_RFQ, Capability.SUPPORTS_CANCEL),
        request ->
            request.isMarket()
                ? Optional.of("SEF order book takes priced orders only")
                : Optional.empty(),
        (b, request) -> {
          String uti =
              reportingLei
                  + "-"
                  + Integer.toHexString((reportingLei + "|" + request.clOrdId()).hashCode())
                      .toUpperCase(java.util.Locale.ROOT);
          b.field(20001, uti);
          b.field(20002, clearingHouse);
        });
  }

  /**
   * 11.9 Bloomberg FX Bridge: relationship-bank FX routing — the order names the RELATIONSHIP BANK
   * (tag 20004) it settles bilaterally with, and carries the value date. No anonymous book: a route
   * without a bank has no counterparty.
   */
  public static VenueDialect bloombergBridge(String bank, String valueDate) {
    return new Rules(
        "bbg-bridge",
        "BBGB",
        Set.of(Capability.SUPPORTS_MARKET, Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_CANCEL),
        request ->
            bank == null || bank.isBlank()
                ? Optional.of("Bridge routes bilaterally: no relationship bank configured")
                : Optional.empty(),
        (b, request) -> {
          b.field(20004, bank);
          b.field(64, valueDate);
        });
  }

  /**
   * 11.10 US equity exchanges (NYSE Pillar / Nasdaq / CBOE BZX via their FIX surfaces),
   * parameterized by MIC. Reg-NMS conventions: an INTERMARKET SWEEP order sets ExecInst 'f' (the
   * router asserts protected-quote compliance itself — the 11.11 sweep); self-trade prevention
   * rides the venue's STP key (tag 20003); odd lots are accepted but flagged manual review off
   * (1028=N, fully automated flow).
   */
  public static VenueDialect usEquityExchange(String mic, String stpKey, boolean intermarketSweep) {
    return new Rules(
        "us-equity-" + mic.toLowerCase(java.util.Locale.ROOT),
        mic,
        Set.of(
            Capability.SUPPORTS_MARKET,
            Capability.SUPPORTS_LIMIT,
            Capability.SUPPORTS_CANCEL,
            Capability.SUPPORTS_REPLACE,
            Capability.SUPPORTS_HIDDEN),
        request ->
            request.qty() <= 0
                ? Optional.of("exchange rejects zero/negative qty")
                : Optional.empty(),
        (b, request) -> {
          if (intermarketSweep) {
            b.field(18, "f"); // ExecInst: Intermarket Sweep Order
          }
          b.field(20003, stpKey);
          b.field(1028, "N"); // ManualOrderIndicator: automated
        });
  }
}
