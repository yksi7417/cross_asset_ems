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

  private static String venueSession(String venue, String name) {
    return "{\"fields\":{\"event\":\""
        + name
        + "\",\"venueMic\":\""
        + venue
        + "\"},\"seq\":2,\"type\":\"VenueSession\"}\n";
  }

  /**
   * Logs the venues the routing tests use on. The venue gate (component 8) refuses a route to
   * anything but an {@code ACTIVE} session.
   */
  private static String venuesUp() {
    StringBuilder events = new StringBuilder();
    for (String venue : new String[] {"XNAS", "XNYS", "XLON"}) {
      for (String name : new String[] {"ConnectRequested", "TcpConnected", "LogonAcknowledged"}) {
        events.append(venueSession(venue, name));
      }
    }
    return events.toString();
  }

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
    Files.writeString(input, LOGON + INSTRUMENT + venuesUp() + journal);
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

  // ── Route lifecycle (component 6b) ────────────────────────────────────────

  private static String routeEvent(String routeId, String name) {
    return "{\"fields\":{\"event\":\""
        + name
        + "\",\"execId\":\"E-1\",\"lastPx\":\"15000\",\"lastQty\":\"100\","
        + "\"routeId\":\""
        + routeId
        + "\"},\"seq\":5,\"type\":\"RouteEvent\"}\n";
  }

  /** A live route with its parent order, ready for venue events. */
  private static String workingRoute() {
    return orderNew("C-A", "1000")
        + routeNew("C-A", "400", "")
        + routeEvent("RTE-0000000001", "RouteAcknowledged");
  }

  /**
   * The cascade: a venue fill on a route moves the parent <em>order</em>, and the mapping comes
   * from the schema's {@code emit_event} effects rather than any table in the runner.
   */
  @Test
  void aRouteFillCascadesToTheParentOrder() throws IOException {
    String out = routed(workingRoute() + routeEvent("RTE-0000000001", "RouteFilled"));

    assertThat(out)
        .contains("\"event\":\"RouteFilled\",\"from\":\"WORKING\",\"fsm\":\"route\"")
        // The order moved because the route did, on an event the client never sent.
        .contains(
            "\"clOrdId\":\"C-A\",\"event\":\"FullFill\",\"from\":\"NEW\","
                + "\"fsm\":\"order\",\"to\":\"FILLED\"");
    // Route transition first, then the order transition it cascaded to.
    assertThat(out.indexOf("\"fsm\":\"route\",\"routeId\":\"RTE-0000000001\",\"to\":\"FILLED\""))
        .isLessThan(out.indexOf("\"event\":\"FullFill\""));
  }

  /**
   * A declined event cascades nothing. The generated effects are empty on a no-transition, so a
   * route that refuses an event cannot move the order machine.
   */
  @Test
  void aDeclinedRouteEventCascadesNothing() throws IOException {
    // A route in SENT has no rule for RouteCanceled.
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "400", "")
                + routeEvent("RTE-0000000001", "RouteCanceled"));

    assertThat(out).contains("\"applied\":\"false\",\"event\":\"RouteCanceled\"");
    // Exactly one order transition: the ValidationPassed from OrderNew.
    assertThat(out.split("\"fsm\":\"order\"", -1)).hasSize(2);
  }

  @Test
  void anEventForAnUnknownRouteIsIgnored() throws IOException {
    String out = routed(workingRoute() + routeEvent("RTE-9999999999", "RouteFilled"));

    assertThat(out).contains("\"type\":\"RouteEventIgnored\"").contains("unknown route");
  }

  @Test
  void anUnknownRouteEventNameIsIgnored() throws IOException {
    String out = routed(workingRoute() + routeEvent("RTE-0000000001", "NotARouteEvent"));

    assertThat(out).contains("\"type\":\"RouteEventIgnored\"").contains("unknown FSM event");
  }

  /**
   * T-7: a route the venue refused holds no quantity, so the order can be re-routed for the full
   * amount. Before component 6b this was refused with {@code EMS-RTE-4003}.
   */
  @Test
  void aRejectedRouteReleasesItsQuantity() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "1000", "")
                + routeEvent("RTE-0000000001", "RouteRejected")
                + routeNew("C-A", "1000", ""));

    assertThat(out).contains("RTE-0000000002").doesNotContain("EMS-RTE-4003");
  }

  /**
   * A {@code FILLED} route keeps its quantity — releasing it would let the order be over-filled.
   * The mirror of the test above, and why the releasing set is an allowlist rather than "any
   * terminal state".
   */
  @Test
  void aFilledRouteDoesNotReleaseItsQuantity() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "1000", "")
                + routeEvent("RTE-0000000001", "RouteAcknowledged")
                + routeEvent("RTE-0000000001", "RouteFilled")
                + routeNew("C-A", "1000", ""));

    // The order is FILLED by the cascade, so it is refused as un-routable before
    // the quantity check is even reached.
    assertThat(out).contains("\"code\":\"EMS-RTE-4002\"").doesNotContain("RTE-0000000002");
  }

  // ── Venue edge (component 8) ───────────────────────────────────────────────

  private static String executionReport(String clOrdId, String execType, String extra) {
    return "{\"fields\":{\"clOrdId\":\""
        + clOrdId
        + "\",\"execType\":\""
        + execType
        + "\""
        + extra
        + "},\"seq\":6,\"type\":\"ExecutionReport\"}\n";
  }

  /**
   * The gate: a route to a venue that is not {@code ACTIVE} is refused with 5001 before any
   * order-side check runs. {@code LOGON_SENT} is the dangerous half-open case — a socket exists,
   * sequence numbers do not, and it looks usable.
   */
  @Test
  void aRouteToAnInactiveVenueIsRefused() throws IOException {
    Path input = tmp.resolve("in.jsonl");
    Path output = tmp.resolve("out.jsonl");
    Files.writeString(
        input,
        LOGON
            + INSTRUMENT
            + venueSession("XNAS", "ConnectRequested")
            + venueSession("XNAS", "TcpConnected")
            + orderNew("C-A", "1000")
            + routeNew("C-A", "400", ""));
    assertThat(run("--input", input.toString(), "--output", output.toString())).isZero();

    assertThat(Files.readString(output, StandardCharsets.UTF_8))
        .contains("\"code\":\"EMS-VEN-5001\"")
        .contains("venue session is LOGON_SENT")
        .doesNotContain("RouteAccepted");
  }

  /** Never-connected and disconnected read differently, though the gate refuses both. */
  @Test
  void aNeverConnectedVenueReadsDifferentlyFromADeadOne() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + "{\"fields\":{\"clOrdId\":\"C-A\",\"qty\":\"100\","
                + "\"venueMic\":\"XJPX\"},\"seq\":5,\"type\":\"RouteNew\"}\n");

    assertThat(out).contains("venue session is never connected");
  }

  /** An accepted route emits the outbound 35=D, after the acceptance. */
  @Test
  void anAcceptedRouteEmitsFixOut() throws IOException {
    String out = routed(orderNew("C-A", "1000") + routeNew("C-A", "400", ""));

    assertThat(out)
        .contains("\"type\":\"FixOut\"")
        .contains("\"msgType\":\"D\"")
        .contains("\"orderQty\":\"400\"")
        .contains("\"symbol\":\"BBG1\"");
    // Message follows acceptance: a consequence, not a cause.
    assertThat(out.indexOf("RouteAccepted")).isLessThan(out.indexOf("FixOut"));
  }

  /**
   * The full inbound chain: one ExecutionReport moves two machines, and neither mapping is written
   * in the runner — ExecType comes from the explicit table, the cascade from the schema's effects.
   */
  @Test
  void anExecutionReportDrivesRouteAndOrder() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "1000", "")
                + executionReport("C-A-1", "0", ",\"ordStatus\":\"0\"")
                + executionReport(
                    "C-A-1",
                    "F",
                    ",\"execId\":\"X-1\",\"lastPx\":\"15000\",\"lastQty\":\"1000\","
                        + "\"ordStatus\":\"2\""));

    assertThat(out)
        .contains("\"event\":\"RouteFilled\"")
        .contains("\"clOrdId\":\"C-A\",\"event\":\"FullFill\"");
  }

  /**
   * {@code ExecType=F} needs {@code OrdStatus} to disambiguate: 2 is the final fill, anything else
   * leaves the route open. Getting this wrong strands quantity forever.
   */
  @Test
  void aTradeWithLeavesIsAPartialFill() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "1000", "")
                + executionReport("C-A-1", "0", ",\"ordStatus\":\"0\"")
                + executionReport(
                    "C-A-1",
                    "F",
                    ",\"execId\":\"X-1\",\"lastPx\":\"15000\",\"lastQty\":\"100\","
                        + "\"ordStatus\":\"1\""));

    assertThat(out)
        .contains("\"event\":\"RoutePartiallyFilled\"")
        .doesNotContain("\"event\":\"RouteFilled\"");
  }

  @Test
  void anUnmappedExecTypeIsIgnored() throws IOException {
    String out =
        routed(
            orderNew("C-A", "1000")
                + routeNew("C-A", "400", "")
                + executionReport("C-A-1", "Z", ""));

    assertThat(out).contains("\"type\":\"ExecutionReportIgnored\"").contains("unmapped ExecType");
  }

  @Test
  void aReportForAnUnknownClOrdIdIsIgnored() throws IOException {
    String out = routed(orderNew("C-A", "1000") + executionReport("NOT-A-ROUTE", "0", ""));

    assertThat(out)
        .contains("\"type\":\"ExecutionReportIgnored\"")
        .contains("unknown route ClOrdID");
  }

  // ── Allocation (component 9) ───────────────────────────────────────────────

  /** A filled order ready to allocate: routed, acknowledged, fully executed. */
  private static String filledOrder() {
    return orderNew("C-A", "1000")
        + routeNew("C-A", "1000", "")
        + executionReport("C-A-1", "0", ",\"ordStatus\":\"0\"")
        + executionReport(
            "C-A-1",
            "F",
            ",\"execId\":\"X-1\",\"lastPx\":\"15000\",\"lastQty\":\"1000\","
                + "\"ordStatus\":\"2\"");
  }

  private static String allocate(String clOrdId, String shares) {
    return "{\"fields\":{\"clOrdId\":\""
        + clOrdId
        + "\",\"shares\":\""
        + shares
        + "\"},\"seq\":7,\"type\":\"Allocate\"}\n";
  }

  /**
   * Conservation: the parts sum exactly to the filled quantity. 3333/3333/3334 floors to 33+33+33
   * over 100 — the lost lot goes to the largest remainder.
   */
  @Test
  void allocationsSumExactlyToTheFilledQuantity() throws IOException {
    String out = routed(filledOrder() + allocate("C-A", "A:3333,B:3333,C:3334"));

    assertThat(out).contains("\"account\":\"A\"").contains("\"account\":\"C\",\"clOrdId\":\"C-A\"");
    long total =
        out.lines()
            .filter(line -> line.contains("AllocationRecord"))
            .mapToLong(
                line -> {
                  int at = line.indexOf("\"qty\":\"") + 7;
                  return Long.parseLong(line.substring(at, line.indexOf('\"', at)));
                })
            .sum();
    assertThat(total).isEqualTo(1000L);
  }

  /** An unfilled order has nothing to allocate — 6002, not an empty success. */
  @Test
  void anUnfilledOrderCannotBeAllocated() throws IOException {
    String out = routed(orderNew("C-A", "1000") + allocate("C-A", "A:10000"));

    assertThat(out).contains("\"code\":\"EMS-ALC-6002\"").doesNotContain("AllocationRecord");
  }

  /** Malformed share entries are dropped; a list with nothing left is 6003. */
  @Test
  void aShareListWithNothingUsableIsRefused() throws IOException {
    String out = routed(filledOrder() + allocate("C-A", "garbage,x:notanumber,:5000"));

    assertThat(out).contains("\"code\":\"EMS-ALC-6003\"");
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
