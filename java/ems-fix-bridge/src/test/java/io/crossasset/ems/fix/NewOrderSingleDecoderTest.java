/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.oms.OrderRequest;
import org.junit.jupiter.api.Test;

/** Tests for {@link NewOrderSingleDecoder}: FIX 35=D → OrderRequest conversion. */
class NewOrderSingleDecoderTest {

  static final char SOH = '\u0001';

  private static final long SESSION_ID = 42L;

  private String buildNos(String... extraTagValues) {
    StringBuilder sb = new StringBuilder();
    // Standard header
    sb.append("8=FIX.4.4").append(SOH);
    sb.append("35=D").append(SOH);
    sb.append("34=1").append(SOH);
    sb.append("49=CLIENT").append(SOH);
    sb.append("56=EMS").append(SOH);
    // Mandatory NOS fields
    sb.append("11=CL001").append(SOH);
    sb.append("48=BBG000BLNNH6").append(SOH);
    sb.append("54=1").append(SOH); // Buy
    sb.append("38=100").append(SOH); // Qty
    sb.append("1=ACC1").append(SOH); // Account
    // extras
    for (String tv : extraTagValues) {
      sb.append(tv).append(SOH);
    }
    sb.append("10=000").append(SOH);
    return sb.toString();
  }

  @Test
  void decode_limitOrder_populatesAllFields() {
    String raw = buildNos("44=9950", "59=0"); // price + TIF=Day
    FixMessage msg = FixMessage.parse(raw);
    NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();

    DecodeResult result = decoder.decode(msg, SESSION_ID);

    assertThat(result).isInstanceOf(DecodeResult.Ok.class);
    OrderRequest req = ((DecodeResult.Ok) result).request();
    assertThat(req.clOrdId()).isEqualTo("CL001");
    assertThat(req.figi()).isEqualTo("BBG000BLNNH6");
    assertThat(req.side()).isEqualTo(1);
    assertThat(req.qty()).isEqualTo(100L);
    assertThat(req.price()).isEqualTo(9950L);
    assertThat(req.account()).isEqualTo("ACC1");
    assertThat(req.tif()).isEqualTo(0);
    assertThat(req.sessionId()).isEqualTo(SESSION_ID);
    assertThat(req.requestId()).isEqualTo("CL001"); // deterministic from clOrdId
  }

  @Test
  void decode_marketOrder_priceIsNull() {
    String raw = buildNos(); // no tag 44
    FixMessage msg = FixMessage.parse(raw);
    NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();

    DecodeResult result = decoder.decode(msg, SESSION_ID);

    assertThat(result).isInstanceOf(DecodeResult.Ok.class);
    OrderRequest req = ((DecodeResult.Ok) result).request();
    assertThat(req.price()).isNull();
  }

  @Test
  void decode_missingClOrdId_returnsMissingField() {
    // Build a NOS without tag 11
    String raw =
        "8=FIX.4.4"
            + SOH
            + "35=D"
            + SOH
            + "34=1"
            + SOH
            + "49=C"
            + SOH
            + "56=EMS"
            + SOH
            + "48=BBG000BLNNH6"
            + SOH
            + "54=1"
            + SOH
            + "38=100"
            + SOH
            + "1=ACC1"
            + SOH
            + "10=000"
            + SOH;
    FixMessage msg = FixMessage.parse(raw);
    NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();

    DecodeResult result = decoder.decode(msg, SESSION_ID);

    assertThat(result).isInstanceOf(DecodeResult.Missing.class);
    assertThat(((DecodeResult.Missing) result).missingTag()).isEqualTo(FixTags.CL_ORD_ID);
  }

  @Test
  void decode_missingSide_returnsMissingField() {
    String raw =
        "8=FIX.4.4"
            + SOH
            + "35=D"
            + SOH
            + "34=1"
            + SOH
            + "49=C"
            + SOH
            + "56=EMS"
            + SOH
            + "11=CL001"
            + SOH
            + "48=BBG000BLNNH6"
            + SOH
            + "38=100"
            + SOH
            + "1=ACC1"
            + SOH
            + "10=000"
            + SOH;
    FixMessage msg = FixMessage.parse(raw);
    DecodeResult result = new NewOrderSingleDecoder().decode(msg, SESSION_ID);
    assertThat(result).isInstanceOf(DecodeResult.Missing.class);
    assertThat(((DecodeResult.Missing) result).missingTag()).isEqualTo(FixTags.SIDE);
  }

  @Test
  void decode_missingQty_returnsMissingField() {
    String raw =
        "8=FIX.4.4"
            + SOH
            + "35=D"
            + SOH
            + "34=1"
            + SOH
            + "49=C"
            + SOH
            + "56=EMS"
            + SOH
            + "11=CL001"
            + SOH
            + "48=BBG000BLNNH6"
            + SOH
            + "54=1"
            + SOH
            + "1=ACC1"
            + SOH
            + "10=000"
            + SOH;
    FixMessage msg = FixMessage.parse(raw);
    DecodeResult result = new NewOrderSingleDecoder().decode(msg, SESSION_ID);
    assertThat(result).isInstanceOf(DecodeResult.Missing.class);
    assertThat(((DecodeResult.Missing) result).missingTag()).isEqualTo(FixTags.ORDER_QTY);
  }

  @Test
  void decode_tifDefaultsToDay_whenAbsent() {
    String raw = buildNos(); // no tag 59
    FixMessage msg = FixMessage.parse(raw);
    DecodeResult result = new NewOrderSingleDecoder().decode(msg, SESSION_ID);
    assertThat(result).isInstanceOf(DecodeResult.Ok.class);
    assertThat(((DecodeResult.Ok) result).request().tif()).isEqualTo(0); // default = Day
  }

  @Test
  void decode_sameClOrdId_yieldsSameRequestId() {
    String raw = buildNos("44=9950");
    FixMessage msg = FixMessage.parse(raw);
    NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();
    String id1 = ((DecodeResult.Ok) decoder.decode(msg, SESSION_ID)).request().requestId();
    String id2 = ((DecodeResult.Ok) decoder.decode(msg, SESSION_ID)).request().requestId();
    assertThat(id1).isEqualTo(id2);
  }
}
