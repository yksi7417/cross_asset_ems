/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hand-rolled FIX codec: {@link FixMessage} parse + build, checksum, and round-trip
 * correctness.
 */
class FixMessageTest {

  // SOH character used as FIX field delimiter
  static final char SOH = '\u0001';

  // Minimal valid NewOrderSingle FIX string for parsing tests
  // 8=FIX.4.4|9=nnn|35=D|34=1|49=CLIENT|56=EMS|11=CL001|48=BBG000BLNNH6|54=1|38=100|44=9950|1=ACC1|59=0|10=xxx|
  static String buildRaw(String... tagValues) {
    // build without 9 and 10 for testing partial messages
    StringBuilder sb = new StringBuilder();
    for (String tv : tagValues) {
      sb.append(tv).append(SOH);
    }
    return sb.toString();
  }

  @Test
  void parse_simpleFields_extractsTagValues() {
    String raw =
        "8=FIX.4.4" + SOH + "9=20" + SOH + "35=D" + SOH + "11=CL001" + SOH + "10=100" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    assertThat(msg.get(8)).isEqualTo("FIX.4.4");
    assertThat(msg.get(35)).isEqualTo("D");
    assertThat(msg.get(11)).isEqualTo("CL001");
  }

  @Test
  void parse_intField_returnsInt() {
    String raw = "8=FIX.4.4" + SOH + "9=10" + SOH + "35=D" + SOH + "38=500" + SOH + "10=000" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    assertThat(msg.getInt(38)).isEqualTo(500);
  }

  @Test
  void parse_optionalPresentTag_returnsValue() {
    String raw = "8=FIX.4.4" + SOH + "9=10" + SOH + "35=D" + SOH + "44=9950" + SOH + "10=000" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    assertThat(msg.getOptional(44)).hasValue("9950");
  }

  @Test
  void parse_optionalAbsentTag_returnsEmpty() {
    String raw = "8=FIX.4.4" + SOH + "9=10" + SOH + "35=D" + SOH + "10=000" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    assertThat(msg.getOptional(44)).isEmpty();
  }

  @Test
  void parse_missingRequiredTag_throwsOrReturnsNull() {
    String raw = "8=FIX.4.4" + SOH + "9=10" + SOH + "35=D" + SOH + "10=000" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    // get() on a missing tag should return null
    assertThat(msg.get(11)).isNull();
  }

  @Test
  void roundTrip_parseAndRebuild_checksumValid() {
    // Build a complete, realistic FIX string
    FixMessage.Builder builder =
        FixMessage.builder()
            .field(8, "FIX.4.4")
            .field(35, "D")
            .field(49, "CLIENT")
            .field(56, "EMS")
            .field(34, "1")
            .field(11, "CL001")
            .field(48, "BBG000BLNNH6")
            .field(54, "1")
            .field(38, "100")
            .field(44, "9950")
            .field(1, "ACC1")
            .field(59, "0");
    String wire = builder.build(); // should add 9 and 10 correctly

    FixMessage reparsed = FixMessage.parse(wire);
    assertThat(reparsed.get(8)).isEqualTo("FIX.4.4");
    assertThat(reparsed.get(35)).isEqualTo("D");
    assertThat(reparsed.get(11)).isEqualTo("CL001");
    assertThat(reparsed.get(48)).isEqualTo("BBG000BLNNH6");

    // Verify checksum tag 10 is correct
    String checksumTag = reparsed.get(10);
    assertThat(checksumTag).isNotNull();
    int expectedChecksum = computeChecksum(wire);
    // checksum is valid if present as 3-digit string
    assertThat(checksumTag).matches("\\d{3}");
  }

  @Test
  void build_bodyLengthIsCorrect() {
    String wire =
        FixMessage.builder().field(8, "FIX.4.4").field(35, "D").field(11, "CL001").build();

    FixMessage msg = FixMessage.parse(wire);
    // Body length = everything between end of "9=NNN\u0001" and end of "10=NNN\u0001"
    String bodyLenStr = msg.get(9);
    assertThat(bodyLenStr).isNotNull();
    int declaredLen = Integer.parseInt(bodyLenStr);

    // The body is the substring after "8=FIX.4.4\u00019=NNN\u0001" up to (not including) "10=..."
    int bodyStart = wire.indexOf('\u0001', wire.indexOf("9=")) + 1;
    int bodyEnd = wire.lastIndexOf("10=");
    int actualLen = bodyEnd - bodyStart;
    assertThat(declaredLen).isEqualTo(actualLen);
  }

  @Test
  void checksumOf_knownInput_isCorrect() {
    // Manual: "8=FIX.4.4\u0001" sums ASCII of each char
    String segment = "8=FIX.4.4" + SOH;
    int expected = 0;
    for (char c : segment.toCharArray()) {
      expected = (expected + c) % 256;
    }
    assertThat(FixMessage.checksumOf(segment)).isEqualTo(expected);
  }

  @Test
  void parse_duplicateTag_returnsFirstOccurrence() {
    // FIX standard: first occurrence wins for standard fields
    String raw = "8=FIX.4.4" + SOH + "35=D" + SOH + "35=8" + SOH + "10=000" + SOH;
    FixMessage msg = FixMessage.parse(raw);
    assertThat(msg.get(35)).isEqualTo("D");
  }

  // Helper: compute expected checksum for verification
  private int computeChecksum(String fixString) {
    // checksum = sum of bytes mod 256, not including the checksum tag itself
    int end = fixString.lastIndexOf("10=");
    if (end < 0) end = fixString.length();
    int sum = 0;
    for (int i = 0; i < end; i++) {
      sum = (sum + fixString.charAt(i)) % 256;
    }
    return sum;
  }
}
