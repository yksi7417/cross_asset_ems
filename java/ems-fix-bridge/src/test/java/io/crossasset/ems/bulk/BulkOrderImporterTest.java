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
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BulkOrderImporter}: header aliases, spreadsheet-reality coercion, file-level
 * structural rejection, per-row partial success through the API surface. Per arch-bulk-io.md, task
 * 8.6.
 */
class BulkOrderImporterTest {

  private static final String FIGI = "BBG000BLNNH6";

  private InMemoryStagedOrderManager som;
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
    som = new InMemoryStagedOrderManager(pipeline);
    ApiSurface api =
        new ApiSurface(
            aaa,
            som,
            new InMemoryRouteManager(som),
            new SubscriptionRegistry(),
            (sid, subId, event) -> {});
    importer = new BulkOrderImporter(api);

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
  void importCsv_twoValidRows_stagesBoth() {
    UploadResult result =
        importer.importCsv(
            "up-1",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account,price
            CL-1,%s,BUY,100,acc-1,101.25
            CL-2,%s,SELL,200,acc-1,
            """
                .formatted(FIGI, FIGI));
    assertThat(result.fileError()).isNull();
    assertThat(result.accepted()).isEqualTo(2);
    StagedOrder first = som.findOrder(result.rows().get(0).refId()).orElseThrow();
    assertThat(first.fsmContext().price()).isEqualTo(1_012_500L); // 101.25 @ 4dp
    StagedOrder second = som.findOrder(result.rows().get(1).refId()).orElseThrow();
    assertThat(second.fsmContext().price()).isNull(); // blank price = market
  }

  @Test
  void importCsv_headerAliases_map() {
    UploadResult result =
        importer.importCsv(
            "up-2",
            sessionId,
            seq++,
            """
            ClOrdID,Security_Id,Buy_Sell,Quantity,Acct
            CL-3,%s,B,1.5M,acc-1
            """
                .formatted(FIGI));
    assertThat(result.fileError()).isNull();
    assertThat(result.accepted()).isEqualTo(1);
    StagedOrder order = som.findOrder(result.rows().get(0).refId()).orElseThrow();
    assertThat(order.fsmContext().orderQty()).isEqualTo(1_500_000L);
    assertThat(order.fsmContext().side()).isEqualTo(1);
  }

  @Test
  void importCsv_commaThousandsAndSuffixes_coerce() {
    UploadResult result =
        importer.importCsv(
            "up-3",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-4,%s,Sell,"1,000",acc-1
            CL-5,%s,buy,250k,acc-1
            """
                .formatted(FIGI, FIGI));
    assertThat(result.accepted()).isEqualTo(2);
    assertThat(som.findOrder(result.rows().get(0).refId()).orElseThrow().fsmContext().orderQty())
        .isEqualTo(1_000L);
    assertThat(som.findOrder(result.rows().get(1).refId()).orElseThrow().fsmContext().orderQty())
        .isEqualTo(250_000L);
  }

  @Test
  void importCsv_leadingApostropheFigi_stripped() {
    UploadResult result =
        importer.importCsv(
            "up-4",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-6,'%s,BUY,100,acc-1
            """
                .formatted(FIGI));
    assertThat(result.accepted()).isEqualTo(1);
  }

  @Test
  void importCsv_missingRequiredColumn_rejectsFile() {
    UploadResult result =
        importer.importCsv(
            "up-5",
            sessionId,
            seq++,
            """
            client_order_id,figi,qty,account
            CL-7,%s,100,acc-1
            """
                .formatted(FIGI));
    assertThat(result.fileError()).contains("side");
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importCsv_badCell_rejectsOnlyThatRowWithLocation() {
    UploadResult result =
        importer.importCsv(
            "up-6",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-8,%s,BUY,abc,acc-1
            CL-9,%s,BUY,100,acc-1
            """
                .formatted(FIGI, FIGI));
    assertThat(result.accepted()).isEqualTo(1);
    assertThat(result.rejected()).isEqualTo(1);
    assertThat(result.rows().get(0).errorMessage()).contains("row 2").contains("abc");
    assertThat(result.rows().get(1).ok()).isTrue();
  }

  @Test
  void importCsv_unknownInstrument_apiRejectMergedByRow() {
    UploadResult result =
        importer.importCsv(
            "up-7",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-10,BBG000UNKNOWN,BUY,100,acc-1
            CL-11,%s,BUY,100,acc-1
            """
                .formatted(FIGI));
    assertThat(result.rows().get(0).ok()).isFalse();
    assertThat(result.rows().get(0).errorCode()).startsWith("EMS-");
    assertThat(result.rows().get(1).ok()).isTrue();
  }

  @Test
  void importCsv_quotedFieldWithComma_parses() {
    UploadResult result =
        importer.importCsv(
            "up-8",
            sessionId,
            seq++,
            """
            client_order_id,figi,side,qty,account
            "CL-12",%s,BUY,"2,500",acc-1
            """
                .formatted(FIGI));
    assertThat(result.accepted()).isEqualTo(1);
    assertThat(som.findOrder(result.rows().get(0).refId()).orElseThrow().fsmContext().orderQty())
        .isEqualTo(2_500L);
  }

  @Test
  void importCsv_emptyFile_rejected() {
    UploadResult result = importer.importCsv("up-9", sessionId, seq++, "");
    assertThat(result.fileError()).isNotNull();
  }
}
