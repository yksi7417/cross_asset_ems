/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The 6.4 reconciliation contract, pinned: every per-asset validation rule's reject code resolves
 * in the catalog, codes are globally unique, and the per-asset-class 6xxx blocks stay in their
 * lanes (equity 60xx, bond 61xx, fx 62xx, derivative 63xx, commodity 64xx, crypto 65xx, abs 66xx,
 * loan 67xx). The original draft reused EMS-ORD-1001 for three different rules across three
 * files — this test is why that can never happen again.
 */
class RejectCodeCatalogConsistencyTest {

  private static final Map<String, Integer> BLOCKS =
      Map.of(
          "equity", 6000, "bond", 6100, "fx", 6200, "derivative", 6300,
          "commodity", 6400, "crypto", 6500, "abs", 6600, "loan", 6700);

  private static Path repoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null && !Files.exists(p.resolve("schemas/reject-codes/catalog.yaml"))) {
      p = p.getParent();
    }
    assertThat(p).as("repo root with schemas/").isNotNull();
    return p;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rulesOf(Path file) throws IOException {
    Map<String, Object> doc = new Yaml().load(Files.readString(file));
    return (List<Map<String, Object>>) doc.get("rules");
  }

  @Test
  void everyRuleCodeResolvesInTheCatalog_uniquely_andInItsClassBlock() throws IOException {
    Path root = repoRoot();

    // Catalog: every code exactly once.
    String catalog = Files.readString(root.resolve("schemas/reject-codes/catalog.yaml"));
    Map<String, Integer> catalogCodes = new HashMap<>();
    Matcher matcher = Pattern.compile("code: (EMS-[A-Z]+-\\d+)").matcher(catalog);
    while (matcher.find()) {
      catalogCodes.merge(matcher.group(1), 1, Integer::sum);
    }
    assertThat(catalogCodes.entrySet().stream().filter(e -> e.getValue() > 1))
        .as("duplicate catalog codes")
        .isEmpty();

    // Rules: in-block, catalog-resolving, globally unique.
    List<String> allRuleCodes = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : BLOCKS.entrySet()) {
      Path file = root.resolve("schemas/validator-rules/" + entry.getKey() + ".yaml");
      assertThat(file).exists();
      List<Map<String, Object>> rules = rulesOf(file);
      assertThat(rules).as(entry.getKey() + " rules").isNotEmpty();
      for (Map<String, Object> rule : rules) {
        String code = String.valueOf(rule.get("reject_code"));
        allRuleCodes.add(code);
        assertThat(code)
            .as("rule %s in %s", rule.get("id"), entry.getKey())
            .matches("EMS-ORD-6\\d{3}");
        int number = Integer.parseInt(code.substring("EMS-ORD-".length()));
        assertThat(number)
            .as("%s must stay in the %s block", code, entry.getKey())
            .isBetween(entry.getValue() + 1, entry.getValue() + 99);
        assertThat(catalogCodes)
            .as("%s (rule %s) must resolve in the catalog", code, rule.get("id"))
            .containsKey(code);
      }
    }
    assertThat(allRuleCodes).hasSize(185); // the full 6.4 rule set
    assertThat(allRuleCodes).doesNotHaveDuplicates();
  }

  @Test
  void catalogMetadataCountMatchesReality() throws IOException {
    String catalog =
        Files.readString(repoRoot().resolve("schemas/reject-codes/catalog.yaml"));
    long actual = Pattern.compile("- \\{ code:").matcher(catalog).results().count();
    Matcher meta = Pattern.compile("total_codes: (\\d+)").matcher(catalog);
    assertThat(meta.find()).isTrue();
    assertThat(Long.parseLong(meta.group(1))).isEqualTo(actual);
  }

  @Test
  void rulesCarryWhatATicketNeeds_descriptionSeverityAndFix() throws IOException {
    Path root = repoRoot();
    try (Stream<Path> files = Files.list(root.resolve("schemas/validator-rules"))) {
      for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
        for (Map<String, Object> rule : rulesOf(file)) {
          assertThat(rule.get("description")).as("description of " + rule.get("id")).isNotNull();
          assertThat(String.valueOf(rule.get("severity"))).isIn("error", "warning");
          assertThat(rule.get("fix_suggestion"))
              .as("fix_suggestion of " + rule.get("id"))
              .isNotNull();
        }
      }
    }
  }
}
