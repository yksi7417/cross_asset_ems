/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Idempotent re-import (task 8.8, per arch-bulk-io.md § Idempotency / re-import): the "did my
 * upload get through?" recovery story. Same uploadId → the prior result verbatim, nothing
 * re-staged; new uploadId with the same primary keys → per-domain duplicate detection (EMS-ORD-2510
 * on client_order_id) rejects every row without creating orders.
 */
class IdempotentReimportTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String FILE =
      """
      client_order_id,figi,side,qty,account
      CL-R1,%s,BUY,100,acc-1
      CL-R2,%s,SELL,200,acc-1
      """
          .formatted(FIGI, FIGI);

  private BulkOrderImporter importer;
  private long sessionId;
  private long seq;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    InMemoryStagedOrderManager som = new InMemoryStagedOrderManager(pipeline);
    importer =
        new BulkOrderImporter(
            new ApiSurface(
                aaa,
                som,
                new InMemoryRouteManager(som),
                new SubscriptionRegistry(),
                (sid, subId, event) -> {}));

    aaa.registerCredential("tok-1", "firm-a", "desk-1", "ops-1", Set.of());
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-1"));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();
    seq = 1;

    InstrumentCore core =
        new InstrumentCore(
            FIGI,
            "IID-1",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Test Stock",
            "Test Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1_000_000L,
            Long.MAX_VALUE,
            1L,
            null,
            1_000_000L,
            1_000_000L);
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L)));
  }

  @Test
  void reimport_sameUploadId_returnsPriorResultWithoutRestaging() {
    UploadResult first = importer.importCsv("up-idem", sessionId, seq++, FILE);
    assertThat(first.accepted()).isEqualTo(2);

    // Retry with the same uploadId (same envelope) — e.g. after a dropped connection.
    UploadResult second = importer.importCsv("up-idem", sessionId, seq, FILE);
    assertThat(second.accepted()).isEqualTo(2);
    assertThat(second.rows().get(0).refId()).isEqualTo(first.rows().get(0).refId());
    assertThat(second.rows().get(1).refId()).isEqualTo(first.rows().get(1).refId());
  }

  @Test
  void reimport_newUploadId_samePrimaryKeys_rejectedAsDuplicates() {
    UploadResult first = importer.importCsv("up-a", sessionId, seq++, FILE);
    assertThat(first.accepted()).isEqualTo(2);

    UploadResult second = importer.importCsv("up-b", sessionId, seq++, FILE);
    assertThat(second.accepted()).isZero();
    assertThat(second.rejected()).isEqualTo(2);
    assertThat(second.rows().get(0).errorCode()).isEqualTo("EMS-ORD-2510");
    assertThat(second.rows().get(1).errorCode()).isEqualTo("EMS-ORD-2510");
  }

  @Test
  void reimport_newUploadId_mixedNewAndDuplicateRows_partialSuccess() {
    importer.importCsv("up-c", sessionId, seq++, FILE);
    String fileWithOneNewRow =
        """
        client_order_id,figi,side,qty,account
        CL-R1,%s,BUY,100,acc-1
        CL-R3,%s,BUY,300,acc-1
        """
            .formatted(FIGI, FIGI);
    UploadResult result = importer.importCsv("up-d", sessionId, seq++, fileWithOneNewRow);
    assertThat(result.rows().get(0).errorCode()).isEqualTo("EMS-ORD-2510");
    assertThat(result.rows().get(1).ok()).isTrue();
    assertThat(result.accepted()).isEqualTo(1);
  }
}
