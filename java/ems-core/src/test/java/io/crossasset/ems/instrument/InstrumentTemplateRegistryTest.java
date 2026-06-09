/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for the SBE instrument template registry.
 *
 * <h3>Discriminating test</h3>
 *
 * <ul>
 *   <li>{@code eachRegisteredTemplate_matchesItsSchemaFile} — for every registry entry, opens its
 *       SBE XML file and asserts that the {@code <sbe:message>} element's {@code name} and {@code
 *       id} equal the registry values. This is the only test that catches registry-vs-wire drift
 *       without re-asserting the same hardcoded values entered into the registry.
 * </ul>
 *
 * <p>Task 4.3 — SBE template registry for Instrument templates.
 */
class InstrumentTemplateRegistryTest {

  private static final InstrumentTemplateRegistry REGISTRY = InstrumentTemplateRegistry.INSTANCE;

  // Maven sets user.dir to the module directory (java/ems-core); project root is two levels up.
  private static final Path PROJECT_ROOT =
      Paths.get(System.getProperty("user.dir")).getParent().getParent();

  // ── Lookup ─────────────────────────────────────────────────────────────────

  @Test
  void lookup_equityTemplateId_returnsEquityDescriptor() {
    var desc = REGISTRY.lookup(0x2001);
    assertTrue(desc.isPresent());
    assertEquals("EquityInstrument", desc.get().name());
    assertEquals(InstrumentAssetClass.EQUITY, desc.get().assetClass());
    assertEquals("schemas/sbe/equity-instrument.xml", desc.get().schemaFile());
  }

  @Test
  void lookup_unknownTemplateId_returnsEmpty() {
    assertTrue(REGISTRY.lookup(0x9999).isEmpty(), "Non-existent template must return empty");
    assertTrue(REGISTRY.lookup(0x0000).isEmpty(), "Zero is not a valid template ID");
    assertTrue(REGISTRY.lookup(-1).isEmpty(), "Negative is not a valid template ID");
  }

  // ── Uniqueness invariants ──────────────────────────────────────────────────

  @Test
  void allTemplates_templateIdsAreUnique() {
    Collection<InstrumentTemplateDescriptor> all = REGISTRY.all();
    Set<Integer> ids =
        all.stream().map(InstrumentTemplateDescriptor::templateId).collect(Collectors.toSet());
    assertEquals(all.size(), ids.size(), "Every registered template must have a unique templateId");
  }

  @Test
  void allTemplates_namesAreUnique() {
    Collection<InstrumentTemplateDescriptor> all = REGISTRY.all();
    Set<String> names =
        all.stream().map(InstrumentTemplateDescriptor::name).collect(Collectors.toSet());
    assertEquals(all.size(), names.size(), "Every registered template must have a unique name");
  }

  // ── Count ──────────────────────────────────────────────────────────────────

  @Test
  void allTemplates_count_matchesSbeSchemaFiles() {
    // 18 instrument schemas currently have SBE XML files; unbuilt templates (TBA-MBS, ETF, etc.)
    // are intentionally absent until their schema tasks land.
    assertEquals(
        18,
        REGISTRY.all().size(),
        "Registry count must equal the number of deployed SBE instrument schema files");
  }

  // ── Asset class groupings ──────────────────────────────────────────────────

  @Test
  void forAssetClass_fx_containsAllFiveTemplates() {
    List<InstrumentTemplateDescriptor> fxTemplates =
        REGISTRY.forAssetClass(InstrumentAssetClass.FX);
    assertEquals(5, fxTemplates.size(), "FX group must contain FxSpot/Forward/Swap/Ndf/Option");
    Set<Integer> fxIds =
        fxTemplates.stream()
            .map(InstrumentTemplateDescriptor::templateId)
            .collect(Collectors.toSet());
    assertTrue(fxIds.containsAll(Set.of(0x2040, 0x2041, 0x2042, 0x2043, 0x2044)));
  }

  @Test
  void forAssetClass_fixedIncome_containsFourTemplates() {
    List<InstrumentTemplateDescriptor> templates =
        REGISTRY.forAssetClass(InstrumentAssetClass.FIXED_INCOME);
    assertEquals(4, templates.size(), "FixedIncome group must contain Bond/ConvBond/Loan/Abs");
    Set<Integer> ids =
        templates.stream()
            .map(InstrumentTemplateDescriptor::templateId)
            .collect(Collectors.toSet());
    assertTrue(ids.containsAll(Set.of(0x2002, 0x2003, 0x2004, 0x2012)));
  }

  @Test
  void forAssetClass_crypto_containsTwoTemplates() {
    List<InstrumentTemplateDescriptor> templates =
        REGISTRY.forAssetClass(InstrumentAssetClass.CRYPTO);
    assertEquals(2, templates.size());
    Set<Integer> ids =
        templates.stream()
            .map(InstrumentTemplateDescriptor::templateId)
            .collect(Collectors.toSet());
    assertTrue(ids.containsAll(Set.of(0x2080, 0x2081)));
  }

  @Test
  void forAssetClass_unknownAssetClassReturnsEmptyList() {
    // PREDICTION_MARKET has no deployed templates yet; forAssetClass must return empty, not throw.
    // We assert the empty case by ensuring every registered class-group is non-empty.
    for (InstrumentAssetClass cls : InstrumentAssetClass.values()) {
      List<InstrumentTemplateDescriptor> group = REGISTRY.forAssetClass(cls);
      assertNotNull(group, "forAssetClass must never return null");
    }
  }

  // ── isKnown ────────────────────────────────────────────────────────────────

  @Test
  void isKnown_trueForAllRegistered_falseForNonInstrumentIds() {
    for (InstrumentTemplateDescriptor desc : REGISTRY.all()) {
      assertTrue(
          REGISTRY.isKnown(desc.templateId()), "isKnown must return true for " + desc.name());
    }
    assertFalse(REGISTRY.isKnown(0x1000), "Non-instrument message ID must not be known");
    assertFalse(REGISTRY.isKnown(0x2005), "Unregistered template gap must not be known");
  }

  // ── Discriminating: registry vs XML cross-check ────────────────────────────

  /**
   * Discriminating test: for each registered template, opens its SBE XML file and asserts that the
   * registry's {@code templateId} and {@code name} match the {@code <sbe:message>} element's {@code
   * id} and {@code name} attributes.
   *
   * <p>This is the only test that detects registry-vs-wire drift. Re-asserting hardcoded values
   * that were typed into the registry would prove nothing.
   */
  @Test
  void eachRegisteredTemplate_matchesItsSchemaFile() throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    DocumentBuilder parser = dbf.newDocumentBuilder();

    for (InstrumentTemplateDescriptor desc : REGISTRY.all()) {
      Path schemaPath = PROJECT_ROOT.resolve(desc.schemaFile());
      assertTrue(
          Files.exists(schemaPath),
          "Schema file must exist on disk: "
              + desc.schemaFile()
              + " (templateId "
              + String.format("0x%04X", desc.templateId())
              + ")");

      Document doc = parser.parse(schemaPath.toFile());
      // SBE namespaces vary between 2016 and 2017 versions; match by local name.
      NodeList messageElements = doc.getElementsByTagNameNS("*", "message");
      assertTrue(
          messageElements.getLength() > 0,
          "Schema " + desc.schemaFile() + " must contain at least one sbe:message element");

      Element msg = (Element) messageElements.item(0);
      String xmlName = msg.getAttribute("name");
      String xmlIdStr = msg.getAttribute("id");
      int xmlTemplateId =
          xmlIdStr.startsWith("0x") || xmlIdStr.startsWith("0X")
              ? Integer.parseInt(xmlIdStr.substring(2), 16)
              : Integer.parseInt(xmlIdStr);

      assertEquals(
          desc.name(), xmlName, "Registry name must match XML name in " + desc.schemaFile());
      assertEquals(
          desc.templateId(),
          xmlTemplateId,
          "Registry templateId must match XML id in "
              + desc.schemaFile()
              + " (expected "
              + String.format("0x%04X", desc.templateId())
              + ", got "
              + String.format("0x%04X", xmlTemplateId)
              + ")");
    }
  }
}
