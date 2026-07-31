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

TEST(SliceRunner, OutputSequenceIsContiguousFromOne) {
    DeterministicIds ids{0};
    const auto out = run_slice({logon("order-entry"), event(9, "Heartbeat")}, ids);

    // Sequence numbers are contiguous from 1 however many events the run emits.
    for (std::size_t i = 0; i < out.size(); ++i) {
        EXPECT_EQ(out.at(i).seq, i + 1U);
    }
}

}  // namespace
