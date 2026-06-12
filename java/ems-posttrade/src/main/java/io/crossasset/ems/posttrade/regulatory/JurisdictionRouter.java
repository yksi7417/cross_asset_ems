/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-pod / per-firm jurisdiction routing (task 12.11, [[arch-jurisdictional-compliance]]): each
 * firm (pod) is homed to a regulatory matrix — a US broker-dealer reports per {@link
 * RegulatorDeterminer#crossAssetUs}, an EU investment firm per {@link
 * RegulatorDeterminer#crossAssetEu} — and a DUAL-homed firm reports under BOTH (the
 * branch-of-a-US-firm-trading-EU-instruments case): the applicable regulators are the de-duped
 * union, in registration order. Unhomed firms fail loudly — silently unreported trades are how reg
 * findings happen.
 */
public final class JurisdictionRouter {

  private final Map<String, RegulatorDeterminer> byFirm = new LinkedHashMap<>();

  /** Home {@code firm} to one regulatory matrix (call again to add a second home — dual). */
  public JurisdictionRouter home(String firm, RegulatorDeterminer determiner) {
    byFirm.merge(
        Objects.requireNonNull(firm),
        Objects.requireNonNull(determiner),
        JurisdictionRouter::union);
    return this;
  }

  /** The regulators {@code firm} must report this trade to. */
  public java.util.List<Regulator> applicableRegulators(String firm, ReportableTrade trade) {
    RegulatorDeterminer determiner = byFirm.get(firm);
    if (determiner == null) {
      throw new IllegalStateException(
          "firm " + firm + " is not homed to any regulatory jurisdiction");
    }
    return determiner.applicableRegulators(trade);
  }

  private static RegulatorDeterminer union(RegulatorDeterminer a, RegulatorDeterminer b) {
    RegulatorDeterminer merged = new RegulatorDeterminer();
    // Determiners expose evaluation, not their rules: compose by delegation.
    for (Regulator regulator : Regulator.values()) {
      merged.addRule(
          t ->
              a.applicableRegulators(t).contains(regulator)
                  || b.applicableRegulators(t).contains(regulator),
          regulator);
    }
    return merged;
  }
}
