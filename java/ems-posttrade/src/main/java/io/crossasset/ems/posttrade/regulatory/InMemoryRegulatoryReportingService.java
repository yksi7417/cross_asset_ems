/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportAcked;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportAmended;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportBuilt;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportDeferred;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportFailed;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportNacked;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportSubmitted;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportTriggered;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportVoided;
import io.crossasset.ems.posttrade.regulatory.RegulatorAdapter.SubmitResponse;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile.AmendmentProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RegulatoryReportingService}. Deterministic: regulator determination, payload
 * build, and the ack/nack outcome (from the mock adapter) are pure functions of the trade and the
 * registered profile/adapter versions, so replay reproduces the same reporting events.
 */
public final class InMemoryRegulatoryReportingService implements RegulatoryReportingService {

  private final RegulatorDeterminer determiner;
  private final Map<Regulator, ReportingProfile> profiles = new ConcurrentHashMap<>();
  private final Map<Regulator, RegulatorAdapter> adapters = new ConcurrentHashMap<>();
  private final Map<String, ReportState> states = new ConcurrentHashMap<>();

  public InMemoryRegulatoryReportingService(RegulatorDeterminer determiner) {
    this.determiner = determiner;
  }

  @Override
  public void registerProfile(ReportingProfile profile) {
    profiles.put(profile.regulator(), profile);
  }

  @Override
  public void registerAdapter(RegulatorAdapter adapter) {
    adapters.put(adapter.regulator(), adapter);
  }

  @Override
  public List<RegReportEvent> report(ReportableTrade trade, String triggeredByEvent) {
    List<RegReportEvent> events = new ArrayList<>();
    for (Regulator regulator : determiner.applicableRegulators(trade)) {
      String reportId = reportId(trade.tradeRef(), regulator);
      submitReport(reportId, trade, regulator, triggeredByEvent, events);
    }
    return events;
  }

  @Override
  public List<RegReportEvent> amend(
      Regulator regulator, ReportableTrade correctedTrade, String reason) {
    List<RegReportEvent> events = new ArrayList<>();
    String originalId = reportId(correctedTrade.tradeRef(), regulator);
    ReportingProfile profile = profiles.get(regulator);
    AmendmentProtocol protocol =
        profile == null ? AmendmentProtocol.VOID_AND_REPLACE : profile.amendmentProtocol();

    if (protocol == AmendmentProtocol.VOID_AND_REPLACE) {
      String voidId = originalId + ":void";
      events.add(new RegReportVoided(originalId, voidId, reason));
      states.put(originalId, ReportState.VOIDED);
      String replacementId = originalId + ":rep";
      submitReport(replacementId, correctedTrade, regulator, "amend:" + reason, events);
      events.add(new RegReportAmended(originalId, replacementId, reason));
    } else {
      // Amend-in-place: resubmit on the same report id.
      events.add(new RegReportAmended(originalId, originalId, reason));
      submitReport(originalId, correctedTrade, regulator, "amend:" + reason, events);
    }
    return events;
  }

  @Override
  public Optional<ReportState> stateOf(String reportId) {
    return Optional.ofNullable(states.get(reportId));
  }

  // ── internals ────────────────────────────────────────────────────────────────

  private void submitReport(
      String reportId,
      ReportableTrade trade,
      Regulator regulator,
      String triggeredByEvent,
      List<RegReportEvent> events) {
    events.add(new RegReportTriggered(reportId, triggeredByEvent, regulator));
    states.put(reportId, ReportState.TRIGGERED);

    ReportingProfile profile = profiles.get(regulator);
    if (profile == null) {
      states.put(reportId, ReportState.DEFERRED);
      events.add(new RegReportDeferred(reportId, List.of("no reporting profile registered")));
      return;
    }

    List<String> missing =
        profile.requiredFields().stream().filter(f -> !trade.hasField(f)).toList();
    if (!missing.isEmpty()) {
      states.put(reportId, ReportState.DEFERRED);
      events.add(new RegReportDeferred(reportId, missing));
      return;
    }

    RegulatorAdapter adapter = adapters.get(regulator);
    String payload = adapter.buildPayload(trade);
    events.add(new RegReportBuilt(reportId, payloadHash(payload)));
    states.put(reportId, ReportState.BUILT);

    int maxAttempts = 1 + profile.maxRetries();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      events.add(new RegReportSubmitted(reportId, attempt));
      states.put(reportId, ReportState.SUBMITTED);
      SubmitResponse response = adapter.submit(payload);
      if (response.acked()) {
        states.put(reportId, ReportState.ACKED);
        events.add(new RegReportAcked(reportId, response.ackRef()));
        return;
      }
      if (attempt == maxAttempts) {
        events.add(new RegReportNacked(reportId, response.errorCode(), attempt));
        states.put(reportId, ReportState.FAILED);
        events.add(new RegReportFailed(reportId, attempt));
        return;
      }
      events.add(new RegReportNacked(reportId, response.errorCode(), attempt + 1));
      states.put(reportId, ReportState.RETRYING);
    }
  }

  private static String reportId(String tradeRef, Regulator regulator) {
    return tradeRef + ":" + regulator;
  }

  /** Deterministic content hash of the payload (String.hashCode is stable across JVMs). */
  private static String payloadHash(String payload) {
    return Integer.toHexString(payload.hashCode());
  }
}
