// The exact string form asserted here is a cross-language contract: Java and
// Rust must produce these identifiers character for character, because they
// reach the output journal the conformance gate compares byte-for-byte.

#include "ems_core/ids.hpp"

#include <gtest/gtest.h>

namespace {

using ems::core::DeterministicIds;

TEST(DeterministicIds, ProducesTheAgreedStringForm) {
    DeterministicIds ids{0};
    EXPECT_EQ(ids.next_order_id(), "ORD-0000000001");
    EXPECT_EQ(ids.next_route_id(), "RTE-0000000001");
    EXPECT_EQ(ids.next_exec_id(), "EXE-0000000001");
}

TEST(DeterministicIds, CountersAreIndependentPerPrefix) {
    DeterministicIds ids{0};
    EXPECT_EQ(ids.next_order_id(), "ORD-0000000001");
    EXPECT_EQ(ids.next_order_id(), "ORD-0000000002");
    EXPECT_EQ(ids.next_route_id(), "RTE-0000000001");
    EXPECT_EQ(ids.next_order_id(), "ORD-0000000003");
}

TEST(DeterministicIds, SameSeedProducesTheSameSequence) {
    DeterministicIds a{7};
    DeterministicIds b{7};
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ(a.next_order_id(), b.next_order_id());
    }
}

TEST(DeterministicIds, SeedOffsetsTheCounter) {
    DeterministicIds ids{41};
    EXPECT_EQ(ids.next_order_id(), "ORD-0000000042");
}

TEST(DeterministicIds, WidthHoldsUntilTheCounterOutgrowsIt) {
    DeterministicIds ids{9999999998ULL};
    EXPECT_EQ(ids.next_order_id(), "ORD-9999999999");
    // Past ten digits the value widens rather than wrapping or truncating.
    EXPECT_EQ(ids.next_order_id(), "ORD-10000000000");
}

}  // namespace
