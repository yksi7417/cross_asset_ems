/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterService;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;

/**
 * The security master, populated from the journal.
 *
 * <p>Instruments arrive as {@code InstrumentCreated} events rather than from reference data on
 * disk, for the same reason sessions do: it keeps the slice a pure function of its input, so a
 * corpus case can describe an unknown or suspended instrument without an external fixture.
 *
 * <p>The journal carries only what the REFERENCE validation layer actually consults — the FIGI and
 * the lifecycle status. {@link InstrumentCore} has twenty-two fields; the rest are filled with
 * fixed, obviously-inert values. That is deliberate: putting all twenty-two on the wire would imply
 * the slice validates against them, and it does not. When a later component starts consulting
 * currency or tick size, those fields join the event and the corpus changes with them.
 */
final class SliceSecurityMaster {

  /**
   * Version 1 for every instrument.
   *
   * <p>The slice has no corporate actions, so nothing ever supersedes anything. Versioning is real
   * in {@code ems-core} and out of scope here (ADR 0002).
   */
  private static final long VERSION_SEQ = 1L;

  private final InMemorySecurityMasterService service = new InMemorySecurityMasterService();

  SecurityMasterService service() {
    return service;
  }

  /**
   * Adds or replaces an instrument.
   *
   * @param figi the identifier orders reference
   * @param status lifecycle status; anything other than {@code ACTIVE} makes the REFERENCE layer
   *     reject an order on this instrument
   */
  void add(String figi, LifecycleStatus status) {
    InstrumentCore core =
        new InstrumentCore(
            figi,
            figi,
            figi,
            figi,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            figi,
            figi,
            "LEI-" + figi,
            CurrencyCode.USD,
            "US",
            "US",
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            status,
            0L,
            Long.MAX_VALUE,
            VERSION_SEQ,
            "",
            0L,
            0L);

    SecurityMasterSnapshot next =
        service
            .currentSnapshot()
            .apply(
                new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 0L));
    service.publish(next);
  }
}
