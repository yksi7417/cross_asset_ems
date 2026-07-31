// Slice runner tests — the same behavioural assertions as
// java/ems-it/.../SliceMainTest.java and rust/ems-slice/src/runner.rs.

#include "ems_it/slice_runner.hpp"

#include <gtest/gtest.h>

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

TEST(SliceRunner, EmptyInputStillProducesARunSummary) {
    DeterministicIds ids{0};
    const auto out = run_slice({}, ids);

    ASSERT_EQ(out.size(), 1U);
    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"events\":\"0\",\"seed\":\"0\"},\"seq\":1,\"type\":\"RunSummary\"}");
}

TEST(SliceRunner, OrderNewBecomesOrderAcceptedWithAGeneratedId) {
    DeterministicIds ids{0};
    const auto out = run_slice({event(1, "OrderNew", {{"account", "ACC1"}, {"qty", "100"}})}, ids);

    ASSERT_EQ(out.size(), 2U);
    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"account\":\"ACC1\",\"orderId\":\"ORD-0000000001\",\"qty\":\"100\"},"
              "\"seq\":1,\"type\":\"OrderAccepted\"}");
}

TEST(SliceRunner, UnrecognisedFieldsAreNotEchoed) {
    // Only the agreed field list crosses to the output. A stray field reaching
    // the journal would diverge the moment another language ordered it
    // differently.
    DeterministicIds ids{0};
    const auto out = run_slice({event(1, "OrderNew", {{"surprise", "x"}})}, ids);

    EXPECT_EQ(encode(out.at(0)),
              "{\"fields\":{\"orderId\":\"ORD-0000000001\"},\"seq\":1,\"type\":\"OrderAccepted\"}");
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
    const auto out = run_slice({event(1, "OrderNew")}, ids);

    EXPECT_EQ(out.at(0).fields.at("orderId"), "ORD-0000000042");
    EXPECT_EQ(out.at(1).fields.at("seed"), "41");
}

TEST(SliceRunner, OutputSequenceIsContiguousFromOne) {
    DeterministicIds ids{0};
    const auto out = run_slice({event(7, "OrderNew"), event(9, "Heartbeat")}, ids);

    ASSERT_EQ(out.size(), 3U);
    EXPECT_EQ(out.at(0).seq, 1U);
    EXPECT_EQ(out.at(1).seq, 2U);
    EXPECT_EQ(out.at(2).seq, 3U);
}

}  // namespace
