/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * SEC 15c3-5 market-access pack (task 18.5): the named mapping from the rule's required controls to
 * this system's implementations, plus the attestation evidence export a CCO reviews annually. Every
 * control names its rule cite, its implementing component/task, and a live evidence supplier that
 * the export snapshots at attestation time — evidence is pulled from the running services (kill
 * audit journal, risk-limit amendment journal, Reg SHO attestation …), never hand-maintained.
 *
 * <p>A control that is not yet implemented appears with status {@code DEFERRED} and its rationale —
 * the gap is part of the attestation, not an omission. (Fat-finger 10.2 is deferred with Phase 9
 * per the 2026-06-10 decision; its compensating controls are named on the mapping.)
 */
public final class MarketAccessPack {

  public enum ControlStatus {
    IMPLEMENTED,
    DEFERRED
  }

  /** One named control mapping. {@code evidence} is snapshotted at export time. */
  public record ControlMapping(
      String controlId,
      String ruleCite,
      String title,
      ControlStatus status,
      String implementedBy,
      @Nullable String deferralRationale,
      @Nullable String compensatingControls,
      Supplier<JsonNode> evidence) {

    public ControlMapping {
      Objects.requireNonNull(controlId, "controlId");
      Objects.requireNonNull(ruleCite, "ruleCite");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(implementedBy, "implementedBy");
      Objects.requireNonNull(evidence, "evidence");
      if (status == ControlStatus.DEFERRED && deferralRationale == null) {
        throw new IllegalArgumentException("DEFERRED controls must state a rationale");
      }
    }
  }

  private final ObjectMapper mapper = new ObjectMapper();
  private final String firm;
  private final List<ControlMapping> controls = new ArrayList<>();

  public MarketAccessPack(String firm) {
    this.firm = Objects.requireNonNull(firm, "firm");
  }

  public synchronized void register(ControlMapping control) {
    controls.add(Objects.requireNonNull(control, "control"));
  }

  public synchronized List<ControlMapping> controls() {
    return List.copyOf(controls);
  }

  /**
   * The attestation evidence export: firm, as-of, every control with its status and a live evidence
   * snapshot, and a summary that counts deferred controls so a gap can never read as complete.
   */
  public synchronized JsonNode attestationExport(long asOfMillis) {
    ObjectNode out = mapper.createObjectNode();
    out.put("standard", "SEC Rule 15c3-5 (Market Access)");
    out.put("firm", firm);
    out.put("asOfMillis", asOfMillis);
    ArrayNode controlsNode = out.putArray("controls");
    int implemented = 0;
    int deferred = 0;
    for (ControlMapping control : controls) {
      ObjectNode node = controlsNode.addObject();
      node.put("controlId", control.controlId());
      node.put("ruleCite", control.ruleCite());
      node.put("title", control.title());
      node.put("status", control.status().name());
      node.put("implementedBy", control.implementedBy());
      if (control.deferralRationale() != null) {
        node.put("deferralRationale", control.deferralRationale());
      }
      if (control.compensatingControls() != null) {
        node.put("compensatingControls", control.compensatingControls());
      }
      JsonNode evidence;
      try {
        evidence = control.evidence().get();
      } catch (Exception e) {
        // Evidence collection failure is itself evidence — surface it, never blank it.
        ObjectNode failure = mapper.createObjectNode();
        failure.put("evidenceError", e.getMessage());
        evidence = failure;
      }
      node.set("evidence", evidence);
      if (control.status() == ControlStatus.IMPLEMENTED) {
        implemented++;
      } else {
        deferred++;
      }
    }
    ObjectNode summary = out.putObject("summary");
    summary.put("controls", controls.size());
    summary.put("implemented", implemented);
    summary.put("deferred", deferred);
    summary.put(
        "attestationNote",
        deferred == 0
            ? "All mapped controls implemented."
            : deferred
                + " control(s) DEFERRED — see deferralRationale and compensatingControls; the"
                + " attestation is complete only with these gaps acknowledged.");
    return out;
  }
}
