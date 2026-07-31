/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CLI contract tests. The exit codes are part of the cross-language contract — the conformance
 * harness uses them to tell "this implementation rejected the input" apart from "this
 * implementation was invoked wrongly".
 */
class SliceMainTest {

  @TempDir Path tmp;

  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int run(String... args) {
    return SliceMain.run(args, new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String errText() {
    return err.toString(StandardCharsets.UTF_8);
  }

  @Test
  void missingInputArgumentIsAUsageError() {
    assertThat(run("--output", tmp.resolve("o.jsonl").toString())).isEqualTo(2);
    assertThat(errText()).contains("--input is required");
  }

  @Test
  void missingOutputArgumentIsAUsageError() {
    assertThat(run("--input", tmp.resolve("i.jsonl").toString())).isEqualTo(2);
    assertThat(errText()).contains("--output is required");
  }

  @Test
  void unknownArgumentIsAUsageError() {
    assertThat(run("--wat")).isEqualTo(2);
    assertThat(errText()).contains("unknown argument");
  }

  @Test
  void negativeSeedIsAUsageError() {
    assertThat(run("--input", "i", "--output", "o", "--seed", "-1")).isEqualTo(2);
    assertThat(errText()).contains("--seed must not be negative");
  }

  @Test
  void nonNumericSeedIsAUsageError() {
    assertThat(run("--input", "i", "--output", "o", "--seed", "abc")).isEqualTo(2);
    assertThat(errText()).contains("not a number");
  }

  @Test
  void malformedInputJournalExitsOneWithoutAStackTrace() throws IOException {
    Path input = tmp.resolve("bad.jsonl");
    Files.writeString(input, "not json\n");

    assertThat(run("--input", input.toString(), "--output", tmp.resolve("o.jsonl").toString()))
        .isEqualTo(1);
    assertThat(errText()).contains("line 1").doesNotContain("\tat ");
  }

  @Test
  void missingInputFileExitsOne() {
    assertThat(
            run(
                "--input",
                tmp.resolve("nope.jsonl").toString(),
                "--output",
                tmp.resolve("o.jsonl").toString()))
        .isEqualTo(1);
  }

  /** A logon that grants everything the order tests need. */
  private static final String LOGON =
      "{\"fields\":{\"desk\":\"DESK1\",\"firm\":\"FIRM1\",\"sessionId\":\"7\","
          + "\"tags\":\"order-entry\",\"user\":\"trader1\"},\"seq\":1,\"type\":\"SessionLogon\"}\n";

  @Test
  void defaultSeedIsZero() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input,
        LOGON
            + "{\"fields\":{\"account\":\"ACC1\",\"sessionId\":\"7\"},\"seq\":2,"
            + "\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"orderId\":\"ORD-0000000001\"")
        .contains("\"seed\":\"0\"");
  }

  @Test
  void orderWithoutAKnownSessionIsRejected() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input,
        "{\"fields\":{\"account\":\"ACC1\",\"sessionId\":\"99\"},\"seq\":1,"
            + "\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"type\":\"OrderRejected\"")
        .contains("\"code\":\"EMS-SES-1002\"")
        .doesNotContain("orderId");
  }

  @Test
  void orderMissingTheRequiredTagIsRejected() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input,
        "{\"fields\":{\"desk\":\"DESK1\",\"firm\":\"FIRM1\",\"sessionId\":\"7\","
            + "\"tags\":\"market-data\",\"user\":\"trader1\"},\"seq\":1,"
            + "\"type\":\"SessionLogon\"}\n"
            + "{\"fields\":{\"sessionId\":\"7\",\"tag\":\"order-entry\"},\"seq\":2,"
            + "\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"code\":\"EMS-PRM-1003\"")
        // Outermost-first: the firm grant is missing, so the production gate
        // reports firm rather than user. That is more useful than "you lack the
        // tag" when the tag was never available to your firm at all.
        .contains("is not granted tag `#order-entry`");
  }

  @Test
  void aRejectedOrderDoesNotConsumeAnIdentifier() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input,
        LOGON
            + "{\"fields\":{\"sessionId\":\"99\"},\"seq\":2,\"type\":\"OrderNew\"}\n"
            + "{\"fields\":{\"sessionId\":\"7\"},\"seq\":3,\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    // If a rejected order consumed an id, this would be ORD-0000000002 and every
    // corpus case downstream of a rejection would shift.
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"orderId\":\"ORD-0000000001\"");
  }

  @Test
  void aNonNumericSessionIdIsARejectionNotACrash() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input, "{\"fields\":{\"sessionId\":\"not-a-number\"},\"seq\":1,\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"code\":\"EMS-SES-1002\"");
  }

  @Test
  void seedShiftsGeneratedIdentifiers() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input, LOGON + "{\"fields\":{\"sessionId\":\"7\"},\"seq\":2,\"type\":\"OrderNew\"}\n");

    assertThat(run("--input", input.toString(), "--output", output.toString(), "--seed", "41"))
        .isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"orderId\":\"ORD-0000000042\"")
        .contains("\"seed\":\"41\"");
  }

  @Test
  void producesByteIdenticalOutputOnRepeatedRuns() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Files.writeString(
        input,
        LOGON
            + """
            {"fields":{"account":"ACC1","figi":"BBG000B9XRY4","price":"1250000","qty":"100","sessionId":"7","side":"BUY"},"seq":2,"type":"OrderNew"}
            {"fields":{"note":"passthrough"},"seq":3,"type":"Heartbeat"}
            """);
    Path first = tmp.resolve("a.jsonl");
    Path second = tmp.resolve("b.jsonl");

    assertThat(run("--input", input.toString(), "--output", first.toString())).isZero();
    assertThat(run("--input", input.toString(), "--output", second.toString())).isZero();

    assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
  }

  // ── Routing (component 6a) ─────────────────────────────────────────────────

  /** An active instrument, so the REFERENCE layer lets a routable order through. */
  private static final String INSTRUMENT =
      "{\"fields\":{\"figi\":\"BBG1\",\"status\":\"ACTIVE\"},\"seq\":2,"
          + "\"type\":\"InstrumentCreated\"}\n";

  /** An order on that instrument for {@code qty}, accepted as {@code ORD-0000000001}. */
  private static String orderNew(String clOrdId, String qty) {
    return "{\"fields\":{\"clOrdId\":\""
        + clOrdId
        + "\",\"figi\":\"BBG1\",\"qty\":\""
        + qty
        + "\",\"sessionId\":\"7\",\"side\":\"BUY\",\"tag\":\"order-entry\"},\"seq\":3,"
        + "\"type\":\"OrderNew\"}\n";
  }

  private static String routeNew(String clOrdId, String qty, String extra) {
    return "{\"fields\":{\"clOrdId\":\""
        + clOrdId
        + "\",\"qty\":\""
        + qty
        + "\""
        + extra
        + ",\"venueMic\":\"XNAS\"},\"seq\":4,\"type\":\"RouteNew\"}\n";
  }

  private String routed(String journal) throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(input, LOGON + INSTRUMENT + journal);
    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    return Files.readString(output, StandardCharsets.UTF_8);
  }

  @Test
  void routingAnAcceptedOrderDispatchesIt() throws IOException {
    String out = routed(orderNew("C-A", "1000") + routeNew("C-A", "400", ""));

    assertThat(out)
        .contains("\"type\":\"RouteAccepted\"")
        .contains("\"routeId\":\"RTE-0000000001\"")
        .contains("\"orderId\":\"ORD-0000000001\"")
        // Numbered per order, not per run — a venue reconciling a ClOrdID chain
        // expects the count of routes on this order.
        .contains("\"routeClOrdId\":\"C-A-1\"")
        // The route is dispatched on creation, not merely created.
        .contains("\"event\":\"RouteSent\",\"from\":\"PENDING\",\"fsm\":\"route\"")
        .contains("\"to\":\"SENT\"");
  }

  @Test
  void aMarketRouteCarriesNoPrice() throws IOException {
    String out = routed(orderNew("C-A", "1000") + routeNew("C-A", "400", ""));

    // Absent and zero are different orders to a venue.
    assertThat(out).doesNotContain("\"price\"");
  }

  @Test
  void routingMoreThanTheOrderHoldsIsRefused() throws IOException {
    String out =
        routed(orderNew("C-A", "1000") + routeNew("C-A", "600", "") + routeNew("C-A", "600", ""));

    assertThat(out)
        .contains("\"code\":\"EMS-RTE-4003\"")
        .contains("qty 600 not routable against 400 remaining");
  }

  @Test
  void zeroQuantityIsNotARoute() throws IOException {
    String out = routed(orderNew("C-A", "1000") + routeNew("C-A", "0", ""));

    assertThat(out).contains("\"code\":\"EMS-RTE-4003\"").doesNotContain("RouteAccepted");
  }

  @Test
  void routingAnUnknownOrderIsRefused() throws IOException {
    String out = routed(orderNew("C-A", "1000") + routeNew("C-NOPE", "100", ""));

    assertThat(out).contains("\"code\":\"EMS-RTE-4001\"").contains("no such order");
  }

  /** A rejected order is in the book but cannot take quantity — 4002, not 4001. */
  @Test
  void routingARejectedOrderSaysTheOrderIsRejected() throws IOException {
    String out =
        routed(
            "{\"fields\":{\"clOrdId\":\"C-Z\",\"figi\":\"BBG-NOT-LISTED\",\"qty\":\"100\","
                + "\"sessionId\":\"7\",\"tag\":\"order-entry\"},\"seq\":3,\"type\":\"OrderNew\"}\n"
                + routeNew("C-Z", "100", ""));

    assertThat(out).contains("\"code\":\"EMS-RTE-4002\"").contains("order is REJECTED");
  }

  @Test
  void aRouteClOrdIdCannotBeReused() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "100", ",\"routeClOrdId\":\"MINE\"")
                + routeNew("C-A", "100", ",\"routeClOrdId\":\"MINE\""));

    assertThat(out).contains("\"code\":\"EMS-RTE-2005\"").contains("ClOrdID MINE in use");
  }

  /**
   * The routing analogue of {@link #aRejectedOrderDoesNotConsumeAnIdentifier}.
   *
   * <p>If refusals burned route ids, every identifier downstream of a refusal would shift — and the
   * ClOrdID collision check in particular has to run <em>before</em> an id is drawn for that to
   * hold.
   */
  @Test
  void aRefusedRouteDoesNotConsumeAnIdentifier() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-NOPE", "100", "")
                + routeNew("C-A", "9999", "")
                + routeNew("C-A", "100", ""));

    assertThat(out).contains("\"routeId\":\"RTE-0000000001\"").doesNotContain("RTE-0000000002");
  }

  @Test
  void emptyInputStillProducesARunSummary() throws IOException {
    Path input = tmp.resolve("empty.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(input, "");

    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"fields\":{\"events\":\"0\",\"seed\":\"0\"},\"seq\":1,\"type\":\"RunSummary\"}\n");
  }
}
