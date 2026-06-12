/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.algo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The broker-algo catalog (task 11.16): strategies keyed by broker, ingested from FIXatdl
 * documents (the industry's algo-definition XML — each broker ships one describing its algos and
 * their parameter panels) or registered programmatically. The ticket lists {@link #strategies}
 * per broker, renders {@link AlgoStrategy#parameters}, validates via {@link
 * AlgoStrategy#validate}, and the FIX path encodes with {@link StrategyParameterEncoder}.
 *
 * <p>FIXatdl ingestion reads the core subset that drives routing — {@code <Strategy name
 * wireValue>} and {@code <Parameter name xsi:type use minValue maxValue>} (+ {@code <EnumPair
 * wireValue>}) — and ignores the layout/flow sections (those drive vendor GUI rendering, not the
 * wire). XXE is disabled: broker files are external input.
 */
public final class AlgoCatalog {

  private final Map<String, Map<String, AlgoStrategy>> byBroker = new LinkedHashMap<>();

  public void register(AlgoStrategy strategy) {
    byBroker
        .computeIfAbsent(strategy.broker(), k -> new LinkedHashMap<>())
        .put(strategy.wireValue(), strategy);
  }

  /** Ingest one broker's FIXatdl document; returns the strategies loaded. */
  public List<AlgoStrategy> ingestFixatdl(String broker, String fixatdlXml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setNamespaceAware(false);
      Document document =
          factory
              .newDocumentBuilder()
              .parse(new ByteArrayInputStream(fixatdlXml.getBytes(StandardCharsets.UTF_8)));
      List<AlgoStrategy> loaded = new ArrayList<>();
      NodeList strategyNodes = document.getElementsByTagName("Strategy");
      for (int i = 0; i < strategyNodes.getLength(); i++) {
        Element strategyEl = (Element) strategyNodes.item(i);
        List<AlgoStrategy.Parameter> parameters = new ArrayList<>();
        NodeList parameterNodes = strategyEl.getElementsByTagName("Parameter");
        for (int j = 0; j < parameterNodes.getLength(); j++) {
          Element p = (Element) parameterNodes.item(j);
          List<String> enums = new ArrayList<>();
          NodeList enumNodes = p.getElementsByTagName("EnumPair");
          for (int k = 0; k < enumNodes.getLength(); k++) {
            enums.add(((Element) enumNodes.item(k)).getAttribute("wireValue"));
          }
          parameters.add(
              new AlgoStrategy.Parameter(
                  p.getAttribute("name"),
                  valueType(p.getAttribute("xsi:type")),
                  "required".equals(p.getAttribute("use")),
                  longAttr(p, "minValue"),
                  longAttr(p, "maxValue"),
                  enums,
                  p.hasAttribute("initValue") ? p.getAttribute("initValue") : null));
        }
        AlgoStrategy strategy =
            new AlgoStrategy(
                broker,
                strategyEl.getAttribute("wireValue"),
                strategyEl.getAttribute("name"),
                parameters);
        register(strategy);
        loaded.add(strategy);
      }
      return loaded;
    } catch (Exception e) {
      throw new IllegalArgumentException("FIXatdl parse failed for " + broker, e);
    }
  }

  /** The broker's strategies in definition order (the algo ticket's dropdown). */
  public List<AlgoStrategy> strategies(String broker) {
    return List.copyOf(byBroker.getOrDefault(broker, Map.of()).values());
  }

  public Optional<AlgoStrategy> find(String broker, String wireValue) {
    return Optional.ofNullable(byBroker.getOrDefault(broker, Map.of()).get(wireValue));
  }

  private static Long longAttr(Element element, String attribute) {
    return element.hasAttribute(attribute) ? Long.parseLong(element.getAttribute(attribute)) : null;
  }

  private static AlgoStrategy.ValueType valueType(String xsiType) {
    // FIXatdl types arrive as "Int_t"/"Qty_t"/"Price_t"/"Percentage_t"/"UTCTimestamp_t"/
    // "Boolean_t"/"Char_t"/"String_t"; default unknowns to STRING (validates enums only).
    return switch (xsiType.replace("_t", "").toUpperCase(java.util.Locale.ROOT)) {
      case "INT" -> AlgoStrategy.ValueType.INT;
      case "QTY" -> AlgoStrategy.ValueType.QTY;
      case "PRICE" -> AlgoStrategy.ValueType.PRICE;
      case "PERCENTAGE" -> AlgoStrategy.ValueType.PERCENTAGE;
      case "UTCTIMESTAMP", "UTCTIMEONLY" -> AlgoStrategy.ValueType.UTC_TIME;
      case "BOOLEAN" -> AlgoStrategy.ValueType.BOOLEAN;
      case "CHAR" -> AlgoStrategy.ValueType.CHAR;
      default -> AlgoStrategy.ValueType.STRING;
    };
  }
}
