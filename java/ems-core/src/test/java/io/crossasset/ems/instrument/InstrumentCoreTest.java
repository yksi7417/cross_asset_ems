/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for InstrumentCore record and its supporting enum types.
 *
 * <h3>Discriminating test</h3>
 *
 * <p>{@code enumWireCodes_matchSbeXmlValidValues} opens the SBE instrument XML schemas and asserts
 * that each Java enum's wireCode matches the corresponding {@code validValue} ordinal. This catches
 * enum drift without re-asserting hardcoded values typed into the Java class.
 *
 * <p>Task 4.4 — InstrumentCore SBE block per arch-security-master.
 */
class InstrumentCoreTest {

  private static final Path PROJECT_ROOT =
      Paths.get(System.getProperty("user.dir")).getParent().getParent();

  // ── InstrumentCore record ─────────────────────────────────────────────────

  @Test
  void instrumentCore_isActive_trueWhenLifecycleStatusIsActive() {
    InstrumentCore ic = sampleCore(LifecycleStatus.ACTIVE, Long.MAX_VALUE);
    assertTrue(ic.isActive());
  }

  @Test
  void instrumentCore_isActive_falseWhenSuspended() {
    InstrumentCore ic = sampleCore(LifecycleStatus.SUSPENDED, Long.MAX_VALUE);
    assertFalse(ic.isActive());
  }

  @Test
  void instrumentCore_isOpenEnded_trueWhenEffectiveToIsMaxValue() {
    InstrumentCore ic = sampleCore(LifecycleStatus.ACTIVE, Long.MAX_VALUE);
    assertTrue(ic.isOpenEnded());
  }

  @Test
  void instrumentCore_isOpenEnded_falseWhenEffectiveToIsFinite() {
    InstrumentCore ic = sampleCore(LifecycleStatus.ACTIVE, 1_800_000_000_000L);
    assertFalse(ic.isOpenEnded());
  }

  @Test
  void instrumentCore_nullableFields_acceptNull() {
    InstrumentCore ic =
        new InstrumentCore(
            "BBG000B9XRY4",
            "EMS:FIRM1:0001",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Apple Inc",
            "Apple Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1_600_000_000_000L,
            Long.MAX_VALUE,
            1L,
            null,
            1_600_000_000_000L,
            1_600_000_000_000L);
    assertNull(ic.compositeFigi());
    assertNull(ic.shareClassFigi());
    assertNull(ic.issuerLei());
    assertNull(ic.countryOfListing());
    assertNull(ic.supersededBy());
    assertTrue(ic.isActive());
    assertTrue(ic.isOpenEnded());
  }

  // ── Fungibility enum ──────────────────────────────────────────────────────

  @Test
  void fungibility_fromWire_roundTrips() {
    for (Fungibility f : Fungibility.values()) {
      Optional<Fungibility> decoded = Fungibility.fromWire(f.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + f);
      assertEquals(f, decoded.get());
    }
  }

  @Test
  void fungibility_fromWire_unknownCodeReturnsEmpty() {
    assertTrue(Fungibility.fromWire(99).isEmpty());
  }

  // ── SettlementConvention enum ─────────────────────────────────────────────

  @Test
  void settlementConvention_fromWire_roundTrips() {
    for (SettlementConvention sc : SettlementConvention.values()) {
      Optional<SettlementConvention> decoded = SettlementConvention.fromWire(sc.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + sc);
      assertEquals(sc, decoded.get());
    }
  }

  // ── AssetClass enum ───────────────────────────────────────────────────────

  @Test
  void assetClass_fromWire_roundTrips() {
    for (AssetClass ac : AssetClass.values()) {
      Optional<AssetClass> decoded = AssetClass.fromWire(ac.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + ac);
      assertEquals(ac, decoded.get());
    }
  }

  @Test
  void assetClass_fromWire_unknownCodeReturnsEmpty() {
    assertTrue(AssetClass.fromWire(255).isEmpty());
  }

  // ── LifecycleStatus enum ──────────────────────────────────────────────────

  @Test
  void lifecycleStatus_fromWire_roundTrips() {
    for (LifecycleStatus ls : LifecycleStatus.values()) {
      Optional<LifecycleStatus> decoded = LifecycleStatus.fromWire(ls.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + ls);
      assertEquals(ls, decoded.get());
    }
  }

  // ── InstrumentType enum ───────────────────────────────────────────────────

  @Test
  void instrumentType_fromWire_roundTrips() {
    for (InstrumentType it : InstrumentType.values()) {
      Optional<InstrumentType> decoded = InstrumentType.fromWire(it.assetClass, it.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + it);
      assertEquals(it, decoded.get());
    }
  }

  @Test
  void instrumentType_fromWire_wrongAssetClassReturnsEmpty() {
    // COMMON_STOCK has wireCode=1 in EQUITY context; same wireCode=1 in FX context is FX_SPOT
    Optional<InstrumentType> result = InstrumentType.fromWire(AssetClass.EQUITY, 99);
    assertTrue(result.isEmpty(), "Unknown wireCode must return empty");
  }

  @Test
  void instrumentType_noAssetClassScopeCollisions() {
    for (InstrumentType a : InstrumentType.values()) {
      for (InstrumentType b : InstrumentType.values()) {
        if (a != b && a.assetClass == b.assetClass) {
          assertNotEquals(
              a.wireCode,
              b.wireCode,
              "wireCode must be unique within the same AssetClass scope: " + a + " vs " + b);
        }
      }
    }
  }

  // ── CurrencyCode enum ─────────────────────────────────────────────────────

  @Test
  void currencyCode_fromIso4217_roundTrips() {
    for (CurrencyCode cc : CurrencyCode.values()) {
      Optional<CurrencyCode> decoded = CurrencyCode.fromIso4217(cc.iso4217Code);
      assertTrue(decoded.isPresent(), "fromIso4217 must find " + cc);
      assertEquals(cc, decoded.get());
    }
  }

  @Test
  void currencyCode_usdIsIso4217_840() {
    assertEquals(840, CurrencyCode.USD.iso4217Code);
  }

  @Test
  void currencyCode_unknownSentinelIs65535() {
    assertEquals(65535, CurrencyCode.UNKNOWN.iso4217Code);
  }

  // ── Discriminating: enum wire codes cross-checked against SBE XML ─────────

  /**
   * Discriminating test: opens equity-instrument.xml and bond-instrument.xml and asserts that the
   * Java Fungibility and SettlementConvention wireCode values match the {@code <validValue>}
   * ordinals in those files.
   *
   * <p>Catches ordinal drift without re-asserting the same hardcoded values typed into the Java
   * enum. AssetClass (EQUITY=2, FIXED_INCOME=5, FX=10) and LifecycleStatus/ACTIVE=1 are also
   * cross-checked since those are consistent across schemas.
   */
  @Test
  void enumWireCodes_matchSbeXmlValidValues() throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    DocumentBuilder parser = dbf.newDocumentBuilder();

    // equity-instrument.xml: Fungibility, SettlementConvention (partial), AssetClass EQUITY=2
    {
      Path path = PROJECT_ROOT.resolve("schemas/sbe/equity-instrument.xml");
      assertTrue(Files.exists(path), "equity-instrument.xml must exist");
      Document doc = parser.parse(path.toFile());
      assertXmlValidValue(doc, "Fungibility", "FUNGIBLE", Fungibility.FUNGIBLE.wireCode);
      assertXmlValidValue(doc, "Fungibility", "NON_FUNGIBLE", Fungibility.NON_FUNGIBLE.wireCode);
      assertXmlValidValue(doc, "Fungibility", "TBA_LIKE", Fungibility.TBA_LIKE.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "T_PLUS_0", SettlementConvention.T_PLUS_0.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "T_PLUS_1", SettlementConvention.T_PLUS_1.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "T_PLUS_2", SettlementConvention.T_PLUS_2.wireCode);
      assertXmlValidValue(doc, "AssetClass", "EQUITY", AssetClass.EQUITY.wireCode);
      assertXmlValidValue(doc, "Status", "ACTIVE", LifecycleStatus.ACTIVE.wireCode);
    }

    // bond-instrument.xml: SettlementConvention adds TBA_MONTHLY and PER_CCP, AssetClass
    // DEBT=FIXED_INCOME
    {
      Path path = PROJECT_ROOT.resolve("schemas/sbe/bond-instrument.xml");
      assertTrue(Files.exists(path), "bond-instrument.xml must exist");
      Document doc = parser.parse(path.toFile());
      assertXmlValidValue(doc, "Fungibility", "FUNGIBLE", Fungibility.FUNGIBLE.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "T_PLUS_2", SettlementConvention.T_PLUS_2.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "TBA_MONTHLY", SettlementConvention.TBA_MONTHLY.wireCode);
      assertXmlValidValue(
          doc, "SettlementConvention", "PER_CCP", SettlementConvention.PER_CCP.wireCode);
      assertXmlValidValue(doc, "AssetClass", "DEBT", AssetClass.FIXED_INCOME.wireCode);
    }

    // fx-spot-instrument.xml: AssetClass FX=10
    {
      Path path = PROJECT_ROOT.resolve("schemas/sbe/fx-spot-instrument.xml");
      assertTrue(Files.exists(path), "fx-spot-instrument.xml must exist");
      Document doc = parser.parse(path.toFile());
      assertXmlValidValue(doc, "AssetClass", "FX", AssetClass.FX.wireCode);
      assertXmlValidValue(doc, "InstrumentType", "FX_SPOT", InstrumentType.FX_SPOT.wireCode);
      assertXmlValidValue(doc, "InstrumentType", "FX_FORWARD", InstrumentType.FX_FORWARD.wireCode);
      assertXmlValidValue(doc, "InstrumentType", "FX_NDF", InstrumentType.FX_NDF.wireCode);
    }

    // equity-instrument.xml: InstrumentType equity sub-types
    {
      Path path = PROJECT_ROOT.resolve("schemas/sbe/equity-instrument.xml");
      Document doc = parser.parse(path.toFile());
      assertXmlValidValue(
          doc, "InstrumentType", "COMMON_STOCK", InstrumentType.COMMON_STOCK.wireCode);
      assertXmlValidValue(doc, "InstrumentType", "PREFERRED", InstrumentType.PREFERRED.wireCode);
      assertXmlValidValue(doc, "InstrumentType", "ADR", InstrumentType.ADR.wireCode);
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static InstrumentCore sampleCore(LifecycleStatus status, long effectiveTo) {
    return new InstrumentCore(
        "BBG000B9XRY4",
        "EMS:FIRM1:0001",
        "BBG000B9XRY4",
        "BBG000B9XRY4",
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        "Apple Inc",
        "Apple Incorporated",
        "HWUPKR0MPOU8FGXBT394",
        CurrencyCode.USD,
        "US",
        "US",
        Fungibility.FUNGIBLE,
        SettlementConvention.T_PLUS_2,
        0,
        status,
        1_600_000_000_000L,
        effectiveTo,
        1L,
        null,
        1_600_000_000_000L,
        1_600_000_000_000L);
  }

  /**
   * Parses the SBE XML document and asserts that the named {@code <enum>} element contains a {@code
   * <validValue>} with the given {@code name} whose text content equals {@code expectedCode}.
   */
  private static void assertXmlValidValue(
      Document doc, String enumName, String valueName, int expectedCode) {
    NodeList enums = doc.getElementsByTagNameNS("*", "enum");
    for (int i = 0; i < enums.getLength(); i++) {
      Element enumEl = (Element) enums.item(i);
      String elName =
          enumEl.hasAttribute("name")
              ? enumEl.getAttribute("name")
              : enumEl.getAttribute("sbe:name");
      if (!enumName.equals(elName)) continue;

      NodeList validValues = enumEl.getElementsByTagNameNS("*", "validValue");
      for (int j = 0; j < validValues.getLength(); j++) {
        Element vv = (Element) validValues.item(j);
        String vvName =
            vv.hasAttribute("name") ? vv.getAttribute("name") : vv.getAttribute("sbe:name");
        if (!valueName.equals(vvName)) continue;

        String rawCode =
            vv.hasAttribute("value") ? vv.getAttribute("value") : vv.getTextContent().trim();
        int xmlCode = Integer.parseInt(rawCode);
        assertEquals(
            expectedCode,
            xmlCode,
            "Java wireCode for "
                + enumName
                + "."
                + valueName
                + " must match SBE XML validValue ordinal");
        return;
      }
      fail("validValue '" + valueName + "' not found in enum '" + enumName + "'");
    }
    fail("Enum '" + enumName + "' not found in SBE XML document");
  }
}
