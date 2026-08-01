// Slice runner tests — the same behavioural assertions as
// java/ems-it/.../SliceMainTest.java and the inline tests in
// rust/ems-slice/src/runner.rs.

#include "ems_it/slice_runner.hpp"

#include <gtest/gtest.h>

#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

#include "ems_core/journal.hpp"

namespace {

using ems::core::DeterministicIds;
using ems::core::encode;
using ems::core::JournalEvent;
using ems::it::run_slice;

JournalEvent event(std::uint64_t seq, const std::string& type,
                   std::map<std::string, std::string> fields = {}) {
    return JournalEvent{seq, type, std::move(fields)};
}

/// The nth output event of a given type.
///
/// Position-based assertions broke the moment the FSM started emitting an
/// FsmTransition before each outcome. Finding by type says what the test
/// actually means and survives the next component doing the same thing.
const JournalEvent& nth_of(const std::vector<JournalEvent>& out, std::string_view type,
                           std::size_t index = 0) {
    std::size_t seen = 0;
    for (const auto& e : out) {
        if (e.type == type && seen++ == index) {
            return e;
        }
    }
    throw std::runtime_error("no " + std::string(type) + " at index " +
                             std::to_string(index));
}

std::size_t count_of(const std::vector<JournalEvent>& out, std::string_view type) {
    std::size_t n = 0;
    for (const auto& e : out) {
        if (e.type == type) {
            ++n;
        }
    }
    return n;
}

/// A logon on session 7 granting exactly `tags`.
JournalEvent logon(const std::string& tags, const std::string& user = "trader1") {
    return event(1, "SessionLogon",
                 {{"desk", "DESK1"},
                  {"firm", "FIRM1"},
                  {"sessionId", "7"},
                  {"tags", tags},
                  {"user", user}});
}

TEST(SliceRunner, EmptyInputStillProducesARunSummary) {
    DeterministicIds ids{0};
    const auto out = run_slice({}, ids);

    ASSERT_EQ(out.size(), 1U);
    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"events\":\"0\",\"seed\":\"0\"},\"seq\":1,\"type\":\"RunSummary\"}");
}

TEST(SliceRunner, LogonThenOrderIsAccepted) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("order-entry"), event(2, "OrderNew", {{"account", "ACC1"}, {"sessionId", "7"}})},
        ids);

    const auto& accepted = nth_of(out, "OrderAccepted");
    EXPECT_EQ(accepted.fields.at("orderId"), "ORD-0000000001");
    EXPECT_EQ(accepted.fields.at("account"), "ACC1");
}

TEST(SliceRunner, LogonEchoesTheGrantedTagsAtEveryLayer) {
    DeterministicIds ids{0};
    const auto out = run_slice({logon("order-entry,market-data")}, ids);

    // Tags are echoed in lexicographic order so a corpus case can show why a
    // later rejection happened without re-reading the input. firmTags and
    // deskTags default to the user's tags.
    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"deskTags\":\"market-data,order-entry\","
              "\"firmTags\":\"market-data,order-entry\",\"sessionId\":\"7\","
              "\"tags\":\"market-data,order-entry\",\"user\":\"trader1\"},"
              "\"seq\":1,\"type\":\"SessionAccepted\"}");
}

TEST(SliceRunner, FirmDenialIsReportedWhenTheFirmLacksTheTag) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("market-data"), event(2, "OrderNew", {{"sessionId", "7"}, {"tag", "order-entry"}})},
        ids);

    // firmTags defaults to the user's tags, so the firm grant is missing too and
    // the gate reports firm (1003) rather than user (1001).
    const auto& rejected = nth_of(out, "OrderRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-PRM-1003");
    EXPECT_EQ(rejected.fields.at("reason"), "Firm `FIRM1` is not granted tag `#order-entry`.");
}

TEST(SliceRunner, UserDenialWhenTheOuterLayersGrantExplicitly) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {event(1, "SessionLogon",
               {{"desk", "DESK1"},
                {"deskTags", "order-entry"},
                {"firm", "FIRM1"},
                {"firmTags", "order-entry"},
                {"sessionId", "7"},
                {"tags", "market-data"},
                {"user", "trader3"}}),
         event(2, "OrderNew", {{"sessionId", "7"}, {"tag", "order-entry"}})},
        ids);

    const auto& rejected = nth_of(out, "OrderRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-PRM-1001");
    EXPECT_EQ(rejected.fields.at("reason"),
              "User `trader3` does not have permission tag `#order-entry`.");
}

TEST(SliceRunner, OrderWithoutAKnownSessionIsRejected) {
    DeterministicIds ids{0};
    const auto out = run_slice({event(1, "OrderNew", {{"sessionId", "99"}})}, ids);

    const auto& rejected = nth_of(out, "OrderRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-SES-1002");
    EXPECT_EQ(rejected.fields.at("layer"), "SESSION");
    EXPECT_EQ(rejected.fields.at("reason"), "Session 99 not found or has expired.");
}

TEST(SliceRunner, OrderMissingTheRequiredTagIsRejected) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("market-data"), event(2, "OrderNew", {{"sessionId", "7"}, {"tag", "order-entry"}})},
        ids);

    const auto& rejected = nth_of(out, "OrderRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-PRM-1003");
    EXPECT_EQ(rejected.fields.at("reason"), "Firm `FIRM1` is not granted tag `#order-entry`.");
}

TEST(SliceRunner, ARejectedOrderDoesNotConsumeAnIdentifier) {
    DeterministicIds ids{0};
    const auto out = run_slice({logon("order-entry"),
                                event(2, "OrderNew", {{"sessionId", "99"}}),
                                event(3, "OrderNew", {{"sessionId", "7"}})},
                               ids);

    // If a rejected order consumed an id this would be ORD-0000000002, and every
    // corpus case downstream of a rejection would shift.
    EXPECT_EQ(nth_of(out, "OrderAccepted").fields.at("orderId"), "ORD-0000000001");
}

TEST(SliceRunner, ANonNumericSessionIdIsARejectionNotACrash) {
    DeterministicIds ids{0};
    const auto out = run_slice({event(1, "OrderNew", {{"sessionId", "not-a-number"}})}, ids);

    const auto& rejected = nth_of(out, "OrderRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-SES-1002");
    EXPECT_EQ(rejected.fields.at("reason"), "Session -1 not found or has expired.");
}

TEST(SliceRunner, ReLogonReplacesTheIdentity) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("market-data"),
         event(2, "OrderNew", {{"sessionId", "7"}, {"tag", "order-entry"}}),
         event(3, "SessionLogon",
               {{"desk", "DESK1"},
                {"firm", "FIRM1"},
                {"sessionId", "7"},
                {"tags", "market-data,order-entry"},
                {"user", "trader2"}}),
         event(4, "OrderNew", {{"sessionId", "7"}, {"tag", "order-entry"}})},
        ids);

    EXPECT_EQ(count_of(out, "OrderRejected"), 1U);
    EXPECT_EQ(count_of(out, "OrderAccepted"), 1U);
}

TEST(SliceRunner, UnrecognisedFieldsAreNotEchoed) {
    // Only the agreed field list crosses to the output. A stray field reaching
    // the journal would diverge the moment another language ordered it
    // differently.
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("order-entry"),
         event(2, "OrderNew", {{"sessionId", "7"}, {"surprise", "x"}})},
        ids);

    // Only the agreed field list crosses to the output.
    const auto& accepted = nth_of(out, "OrderAccepted");
    EXPECT_EQ(accepted.fields.size(), 1U);
    EXPECT_EQ(accepted.fields.at("orderId"), "ORD-0000000001");
}

TEST(SliceRunner, OtherEventTypesPassThroughWithSequenceRenumbered) {
    DeterministicIds ids{0};
    const auto out = run_slice({event(41, "Heartbeat", {{"note", "hi"}})}, ids);

    ASSERT_EQ(out.size(), 2U);
    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"note\":\"hi\"},\"seq\":1,\"type\":\"Heartbeat\"}");
}

TEST(SliceRunner, SeedShiftsGeneratedIdentifiers) {
    DeterministicIds ids{41};
    const auto out = run_slice(
        {logon("order-entry"), event(2, "OrderNew", {{"sessionId", "7"}})}, ids);

    EXPECT_EQ(nth_of(out, "OrderAccepted").fields.at("orderId"), "ORD-0000000042");
    EXPECT_EQ(nth_of(out, "RunSummary").fields.at("seed"), "41");
}

// ── Routing (component 6a) ───────────────────────────────────────────────────

/// A logon, an active instrument and one accepted order of `qty`.
///
/// Every routing test needs the same three events before it can say anything
/// about a route, and spelling them out per test buries the one line that
/// differs.
JournalEvent venue_session(const std::string& venue, const std::string& name) {
    return event(0, "VenueSession", {{"event", name}, {"venueMic", venue}});
}

std::vector<JournalEvent> routable_order(const std::string& cl_ord_id, const std::string& qty) {
    std::vector<JournalEvent> input = {
        logon("order-entry"),
        event(2, "InstrumentCreated", {{"figi", "BBG1"}, {"status", "ACTIVE"}})};
    // The venue gate (component 8) refuses a route to anything but an ACTIVE
    // session, so every routing fixture logs the venues on first.
    for (const auto* venue : {"XNAS", "XNYS", "XLON"}) {
        for (const auto* name : {"ConnectRequested", "TcpConnected", "LogonAcknowledged"}) {
            input.push_back(venue_session(venue, name));
        }
    }
    input.push_back(event(3, "OrderNew",
                          {{"clOrdId", cl_ord_id},
                           {"figi", "BBG1"},
                           {"qty", qty},
                           {"sessionId", "7"},
                           {"side", "BUY"},
                           {"tag", "order-entry"}}));
    return input;
}

TEST(SliceRunner, RoutingAnAcceptedOrderDispatchesIt) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew",
                          {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}}));
    const auto out = run_slice(input, ids);

    const auto& accepted = nth_of(out, "RouteAccepted");
    EXPECT_EQ(accepted.fields.at("routeId"), "RTE-0000000001");
    EXPECT_EQ(accepted.fields.at("orderId"), "ORD-0000000001");
    EXPECT_EQ(accepted.fields.at("routeClOrdId"), "C-A-1");
    EXPECT_EQ(accepted.fields.at("venueMic"), "XNAS");
    // A market route carries no price rather than a zero one: absent and zero
    // are different orders to a venue.
    EXPECT_EQ(accepted.fields.count("price"), 0U);

    // The route is dispatched on creation, not merely created. Found by
    // machine, not by position — the venue-session transitions land first.
    const JournalEvent* transition = nullptr;
    for (const auto& e : out) {
        if (e.type == "FsmTransition" && e.fields.at("fsm") == "route") {
            transition = &e;
            break;
        }
    }
    ASSERT_NE(transition, nullptr);
    EXPECT_EQ(transition->fields.at("fsm"), "route");
    EXPECT_EQ(transition->fields.at("from"), "PENDING");
    EXPECT_EQ(transition->fields.at("to"), "SENT");
    EXPECT_EQ(transition->fields.at("applied"), "true");
}

TEST(SliceRunner, RoutingMoreThanTheOrderHoldsIsRefused) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew",
                          {{"clOrdId", "C-A"}, {"qty", "600"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "RouteNew",
                          {{"clOrdId", "C-A"}, {"qty", "600"}, {"venueMic", "XNYS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteAccepted"), 1U);
    const auto& rejected = nth_of(out, "RouteRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-RTE-4003");
    EXPECT_EQ(rejected.fields.at("reason"), "qty 600 not routable against 400 remaining");
}

TEST(SliceRunner, ZeroQuantityIsNotARoute) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "0"}, {"venueMic", "XNAS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteAccepted"), 0U);
    EXPECT_EQ(nth_of(out, "RouteRejected").fields.at("code"), "EMS-RTE-4003");
}

TEST(SliceRunner, RoutingAnUnknownOrderIsRefused) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew",
                          {{"clOrdId", "C-NOPE"}, {"qty", "100"}, {"venueMic", "XNAS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "RouteRejected").fields.at("code"), "EMS-RTE-4001");
}

/// A rejected order is in the book but cannot take quantity — 4002, not 4001.
TEST(SliceRunner, RoutingARejectedOrderSaysTheOrderIsRejected) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("order-entry"),
         venue_session("XNAS", "ConnectRequested"),
         venue_session("XNAS", "TcpConnected"),
         venue_session("XNAS", "LogonAcknowledged"),
         event(2, "OrderNew",
               {{"clOrdId", "C-Z"},
                {"figi", "BBG-NOT-LISTED"},
                {"qty", "100"},
                {"sessionId", "7"},
                {"tag", "order-entry"}}),
         event(3, "RouteNew", {{"clOrdId", "C-Z"}, {"qty", "100"}, {"venueMic", "XNAS"}})},
        ids);

    const auto& rejected = nth_of(out, "RouteRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-RTE-4002");
    EXPECT_EQ(rejected.fields.at("reason"), "order is REJECTED");
}

TEST(SliceRunner, ARouteClOrdIdCannotBeReused) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew",
                          {{"clOrdId", "C-A"},
                           {"qty", "100"},
                           {"routeClOrdId", "MINE"},
                           {"venueMic", "XNAS"}}));
    input.push_back(event(5, "RouteNew",
                          {{"clOrdId", "C-A"},
                           {"qty", "100"},
                           {"routeClOrdId", "MINE"},
                           {"venueMic", "XNYS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteAccepted"), 1U);
    EXPECT_EQ(nth_of(out, "RouteRejected").fields.at("code"), "EMS-RTE-2005");
}

/// The routing analogue of aRejectedOrderDoesNotConsumeAnIdentifier: if refusals
/// burned route ids, every identifier downstream of a refusal would shift.
TEST(SliceRunner, ARefusedRouteDoesNotConsumeAnIdentifier) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew",
                          {{"clOrdId", "C-NOPE"}, {"qty", "100"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "RouteNew",
                          {{"clOrdId", "C-A"}, {"qty", "9999"}, {"venueMic", "XNAS"}}));
    input.push_back(event(6, "RouteNew",
                          {{"clOrdId", "C-A"}, {"qty", "100"}, {"venueMic", "XNAS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteRejected"), 2U);
    EXPECT_EQ(nth_of(out, "RouteAccepted").fields.at("routeId"), "RTE-0000000001");
}

// ── Route lifecycle (component 6b) ───────────────────────────────────────────

JournalEvent route_event(std::uint64_t seq, const std::string& route_id,
                         const std::string& name) {
    return event(seq, "RouteEvent",
                 {{"event", name},
                  {"execId", "E-1"},
                  {"lastPx", "15000"},
                  {"lastQty", "100"},
                  {"routeId", route_id}});
}

/// A live route with its parent order, ready for venue events.
std::vector<JournalEvent> working_route() {
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}}));
    input.push_back(route_event(5, "RTE-0000000001", "RouteAcknowledged"));
    return input;
}

/// The last transition of a given machine.
const JournalEvent& last_transition(const std::vector<JournalEvent>& out, std::string_view fsm) {
    const JournalEvent* found = nullptr;
    for (const auto& e : out) {
        if (e.type == "FsmTransition" && e.fields.at("fsm") == fsm) {
            found = &e;
        }
    }
    if (found == nullptr) {
        throw std::runtime_error("no transition for " + std::string(fsm));
    }
    return *found;
}

std::size_t transitions_of(const std::vector<JournalEvent>& out, std::string_view fsm) {
    std::size_t n = 0;
    for (const auto& e : out) {
        if (e.type == "FsmTransition" && e.fields.at("fsm") == fsm) {
            ++n;
        }
    }
    return n;
}

/// The cascade: a venue fill on a route moves the parent ORDER, and the mapping
/// comes from the schema's emit_event effects rather than a table in this file.
TEST(SliceRunner, ARouteFillCascadesToTheParentOrder) {
    DeterministicIds ids{0};
    auto input = working_route();
    input.push_back(route_event(6, "RTE-0000000001", "RouteFilled"));
    const auto out = run_slice(input, ids);

    const auto& route = last_transition(out, "route");
    EXPECT_EQ(route.fields.at("to"), "FILLED");

    const auto& order = last_transition(out, "order");
    EXPECT_EQ(order.fields.at("event"), "FullFill");
    EXPECT_EQ(order.fields.at("to"), "FILLED");
    // The route moves first, then the order it cascaded to. Order is part of the
    // cross-language contract.
    EXPECT_LT(route.seq, order.seq);
}

/// **A declined event cascades nothing.** The generated effects are empty on a
/// no-transition, so a route that refuses an event cannot move the order.
TEST(SliceRunner, ADeclinedRouteEventCascadesNothing) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}}));
    // A route in SENT has no rule for RouteCanceled.
    input.push_back(route_event(5, "RTE-0000000001", "RouteCanceled"));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(last_transition(out, "route").fields.at("applied"), "false");
    // Exactly one order transition: the ValidationPassed from OrderNew.
    EXPECT_EQ(transitions_of(out, "order"), 1U);
}

TEST(SliceRunner, AnEventForAnUnknownRouteIsIgnored) {
    DeterministicIds ids{0};
    auto input = working_route();
    input.push_back(route_event(6, "RTE-9999999999", "RouteFilled"));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "RouteEventIgnored").fields.at("reason"), "unknown route");
}

TEST(SliceRunner, AnUnknownRouteEventNameIsIgnored) {
    DeterministicIds ids{0};
    auto input = working_route();
    input.push_back(route_event(6, "RTE-0000000001", "NotARouteEvent"));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "RouteEventIgnored").fields.at("reason"), "unknown FSM event");
}

/// T-7: a route the venue refused holds no quantity, so the order can be
/// re-routed for the full amount. Before component 6b this was EMS-RTE-4003.
TEST(SliceRunner, ARejectedRouteReleasesItsQuantity) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNAS"}}));
    input.push_back(route_event(5, "RTE-0000000001", "RouteRejected"));
    input.push_back(event(6, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNYS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteAccepted"), 2U);
    EXPECT_EQ(count_of(out, "RouteRejected"), 0U);
}

/// A FILLED route keeps its quantity — releasing it would let the order be
/// over-filled. The mirror of the test above, and the reason the releasing set is
/// an allowlist rather than "any terminal state".
TEST(SliceRunner, AFilledRouteDoesNotReleaseItsQuantity) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNAS"}}));
    input.push_back(route_event(5, "RTE-0000000001", "RouteAcknowledged"));
    input.push_back(route_event(6, "RTE-0000000001", "RouteFilled"));
    input.push_back(event(7, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNYS"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(count_of(out, "RouteAccepted"), 1U);
    // The order is FILLED by the cascade, so it is refused as un-routable before
    // the quantity check is even reached.
    EXPECT_EQ(nth_of(out, "RouteRejected").fields.at("code"), "EMS-RTE-4002");
}

// ── Venue edge (component 8) ─────────────────────────────────────────────────

/// The gate: a route to a venue that is not ACTIVE is refused with 5001, before
/// any order-side check runs. LOGON_SENT is the dangerous half-open case — a
/// socket exists, sequence numbers do not, and it looks usable.
TEST(SliceRunner, ARouteToAnInactiveVenueIsRefused) {
    DeterministicIds ids{0};
    const auto out = run_slice(
        {logon("order-entry"),
         event(2, "InstrumentCreated", {{"figi", "BBG1"}, {"status", "ACTIVE"}}),
         venue_session("XNAS", "ConnectRequested"),
         venue_session("XNAS", "TcpConnected"),
         event(3, "OrderNew",
               {{"clOrdId", "C-A"},
                {"figi", "BBG1"},
                {"qty", "1000"},
                {"sessionId", "7"},
                {"side", "BUY"},
                {"tag", "order-entry"}}),
         event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}})},
        ids);

    const auto& rejected = nth_of(out, "RouteRejected");
    EXPECT_EQ(rejected.fields.at("code"), "EMS-VEN-5001");
    EXPECT_EQ(rejected.fields.at("reason"), "venue session is LOGON_SENT");
    EXPECT_EQ(count_of(out, "RouteAccepted"), 0U);
}

/// Never-connected and disconnected read differently in the journal, even
/// though the gate refuses both.
TEST(SliceRunner, ANeverConnectedVenueReadsDifferentlyFromADeadOne) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(0, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "100"}, {"venueMic", "XJPX"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "RouteRejected").fields.at("reason"),
              "venue session is never connected");
}

/// An accepted route emits the outbound 35=D, after the acceptance.
TEST(SliceRunner, AnAcceptedRouteEmitsFixOut) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}}));
    const auto out = run_slice(input, ids);

    const auto& fix = nth_of(out, "FixOut");
    EXPECT_EQ(fix.fields.at("msgType"), "D");
    EXPECT_EQ(fix.fields.at("clOrdId"), "C-A-1");
    EXPECT_EQ(fix.fields.at("orderQty"), "400");
    EXPECT_EQ(fix.fields.at("symbol"), "BBG1");
    // Message follows acceptance: a consequence, not a cause.
    EXPECT_LT(nth_of(out, "RouteAccepted").seq, fix.seq);
}

/// The full inbound chain: one ExecutionReport moves two machines, and neither
/// mapping is written in the runner.
TEST(SliceRunner, AnExecutionReportDrivesRouteAndOrder) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "ExecutionReport",
                          {{"clOrdId", "C-A-1"}, {"execType", "0"}, {"ordStatus", "0"}}));
    input.push_back(event(6, "ExecutionReport",
                          {{"clOrdId", "C-A-1"},
                           {"execId", "X-1"},
                           {"execType", "F"},
                           {"lastPx", "15000"},
                           {"lastQty", "1000"},
                           {"ordStatus", "2"}}));
    const auto out = run_slice(input, ids);

    const auto& route = last_transition(out, "route");
    EXPECT_EQ(route.fields.at("event"), "RouteFilled");
    EXPECT_EQ(route.fields.at("to"), "FILLED");

    const auto& order = last_transition(out, "order");
    EXPECT_EQ(order.fields.at("event"), "FullFill");
    EXPECT_EQ(order.fields.at("to"), "FILLED");
}

/// ExecType=F needs OrdStatus to disambiguate: 2 is the final fill, anything
/// else leaves the route open. Getting this wrong strands quantity forever.
TEST(SliceRunner, ATradeWithLeavesIsAPartialFill) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "ExecutionReport",
                          {{"clOrdId", "C-A-1"}, {"execType", "0"}, {"ordStatus", "0"}}));
    input.push_back(event(6, "ExecutionReport",
                          {{"clOrdId", "C-A-1"},
                           {"execId", "X-1"},
                           {"execType", "F"},
                           {"lastPx", "15000"},
                           {"lastQty", "100"},
                           {"ordStatus", "1"}}));
    const auto out = run_slice(input, ids);

    const auto& route = last_transition(out, "route");
    EXPECT_EQ(route.fields.at("event"), "RoutePartiallyFilled");
    EXPECT_EQ(route.fields.at("to"), "PARTIALLY_FILLED");
}

TEST(SliceRunner, AnUnmappedExecTypeIsIgnored) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "400"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "ExecutionReport", {{"clOrdId", "C-A-1"}, {"execType", "Z"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "ExecutionReportIgnored").fields.at("reason"), "unmapped ExecType");
}

TEST(SliceRunner, AReportForAnUnknownClOrdIdIsIgnored) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(5, "ExecutionReport", {{"clOrdId", "NOT-A-ROUTE"}, {"execType", "0"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "ExecutionReportIgnored").fields.at("reason"), "unknown route ClOrdID");
}

// ── Allocation (component 9) ─────────────────────────────────────────────────

/// A filled order ready to allocate.
std::vector<JournalEvent> filled_order() {
    auto input = routable_order("C-A", "1000");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "1000"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "ExecutionReport",
                          {{"clOrdId", "C-A-1"}, {"execType", "0"}, {"ordStatus", "0"}}));
    input.push_back(event(6, "ExecutionReport",
                          {{"clOrdId", "C-A-1"},
                           {"execId", "X-1"},
                           {"execType", "F"},
                           {"lastPx", "15000"},
                           {"lastQty", "1000"},
                           {"ordStatus", "2"}}));
    return input;
}

std::vector<std::pair<std::string, std::uint64_t>> allocations(
    const std::vector<JournalEvent>& out) {
    std::vector<std::pair<std::string, std::uint64_t>> result;
    for (const auto& e : out) {
        if (e.type == "AllocationRecord") {
            result.emplace_back(e.fields.at("account"),
                                std::stoull(e.fields.at("qty")));
        }
    }
    return result;
}

/// Conservation: the parts sum exactly to the filled quantity, whatever the
/// weights. Floors alone under-allocate; naive rounding over-allocates.
TEST(SliceRunner, AllocationsSumExactlyToTheFilledQuantity) {
    DeterministicIds ids{0};
    auto input = filled_order();
    input.push_back(
        event(7, "Allocate", {{"clOrdId", "C-A"}, {"shares", "A:3333,B:3333,C:3334"}}));
    const auto out = run_slice(input, ids);

    std::uint64_t total = 0;
    for (const auto& [account, qty] : allocations(out)) {
        total += qty;
    }
    EXPECT_EQ(total, 1000U);
}

/// The tie-break chain, end to end: equal remainders, equal weights —
/// instruction order decides, and the first account gets the odd lot.
TEST(SliceRunner, AFullTieIsBrokenByInstructionOrder) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "5");
    input.push_back(
        event(4, "RouteNew", {{"clOrdId", "C-A"}, {"qty", "5"}, {"venueMic", "XNAS"}}));
    input.push_back(event(5, "ExecutionReport",
                          {{"clOrdId", "C-A-1"}, {"execType", "0"}, {"ordStatus", "0"}}));
    input.push_back(event(6, "ExecutionReport",
                          {{"clOrdId", "C-A-1"},
                           {"execId", "X-1"},
                           {"execType", "F"},
                           {"lastPx", "15000"},
                           {"lastQty", "5"},
                           {"ordStatus", "2"}}));
    input.push_back(
        event(7, "Allocate", {{"clOrdId", "C-A"}, {"shares", "FIRST:5000,SECOND:5000"}}));
    const auto out = run_slice(input, ids);

    const auto allocs = allocations(out);
    ASSERT_EQ(allocs.size(), 2U);
    EXPECT_EQ(allocs[0], (std::pair<std::string, std::uint64_t>{"FIRST", 3U}));
    EXPECT_EQ(allocs[1], (std::pair<std::string, std::uint64_t>{"SECOND", 2U}));
}

/// An unfilled order has nothing to allocate — 6002, not an empty success.
TEST(SliceRunner, AnUnfilledOrderCannotBeAllocated) {
    DeterministicIds ids{0};
    auto input = routable_order("C-A", "1000");
    input.push_back(event(5, "Allocate", {{"clOrdId", "C-A"}, {"shares", "A:10000"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "AllocationRejected").fields.at("code"), "EMS-ALC-6002");
    EXPECT_EQ(count_of(out, "AllocationRecord"), 0U);
}

/// Malformed share entries are dropped; a list with nothing left is 6003.
TEST(SliceRunner, AShareListWithNothingUsableIsRefused) {
    DeterministicIds ids{0};
    auto input = filled_order();
    input.push_back(
        event(7, "Allocate", {{"clOrdId", "C-A"}, {"shares", "garbage,x:notanumber,:5000"}}));
    const auto out = run_slice(input, ids);

    EXPECT_EQ(nth_of(out, "AllocationRejected").fields.at("code"), "EMS-ALC-6003");
}

TEST(SliceRunner, OutputSequenceIsContiguousFromOne) {
    DeterministicIds ids{0};
    const auto out = run_slice({logon("order-entry"), event(9, "Heartbeat")}, ids);

    // Sequence numbers are contiguous from 1 however many events the run emits.
    for (std::size_t i = 0; i < out.size(); ++i) {
        EXPECT_EQ(out.at(i).seq, i + 1U);
    }
}

}  // namespace
