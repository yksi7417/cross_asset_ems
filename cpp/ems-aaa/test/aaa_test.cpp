// Mirrored by java/ems-aaa/.../InMemorySliceAaaServiceTest.java and the inline
// tests in rust/ems-aaa/src/lib.rs.

#include "ems_aaa/aaa.hpp"

#include <gtest/gtest.h>

#include <set>
#include <string>
#include <vector>

namespace {

using ems::aaa::AaaService;
using ems::aaa::Identity;
using ems::aaa::Session;

Session session(std::uint64_t id, const std::string& user, const std::set<std::string>& tags) {
    return Session{id, Identity{"FIRM1", "DESK1", user, tags}};
}

TEST(Aaa, UnknownSessionIsNull) { EXPECT_EQ(AaaService{}.session(42), nullptr); }

TEST(Aaa, RegisteredSessionIsFound) {
    AaaService service;
    service.register_session(session(42, "trader1", {"order-entry"}));

    const auto* found = service.session(42);
    ASSERT_NE(found, nullptr);
    EXPECT_EQ(found->identity.user, "trader1");
}

TEST(Aaa, ReLogonReplacesTheSession) {
    AaaService service;
    service.register_session(session(42, "trader1", {"order-entry"}));
    service.register_session(session(42, "trader2", {}));

    // Policing duplicate logons belongs to the FIX session layer, which ADR 0002
    // puts out of scope for this slice.
    const auto* found = service.session(42);
    ASSERT_NE(found, nullptr);
    EXPECT_EQ(found->identity.user, "trader2");
}

TEST(Aaa, HeldTagIsAllowed) {
    const auto s = session(1, "trader1", {"order-entry"});
    EXPECT_TRUE(AaaService::authorize(s, "order-entry").allowed);
}

TEST(Aaa, MissingTagIsDeniedWithTheCatalogCode) {
    const auto s = session(1, "trader1", {"market-data"});

    const auto decision = AaaService::authorize(s, "order-entry");

    ASSERT_FALSE(decision.allowed);
    // EMS-PRM-1001 is a real entry in schemas/reject-codes/catalog.yaml. It
    // reaches the output journal, so an invented code would diverge silently.
    EXPECT_EQ(decision.code, "EMS-PRM-1001");
    EXPECT_EQ(decision.category, "PRM");
    EXPECT_EQ(decision.reason, "User trader1 does not have permission tag #order-entry.");
}

TEST(Aaa, EmptyTagRequiresNoEntitlement) {
    EXPECT_TRUE(AaaService::authorize(session(1, "trader1", {}), "").allowed);
}

TEST(Aaa, TagsIterateInLexicographicOrderRegardlessOfInsertionOrder) {
    const auto s = session(1, "trader1", {"zeta", "alpha", "mid"});

    // The tag set can reach the output journal, so its order is a contract.
    const std::vector<std::string> ordered(s.identity.tags.begin(), s.identity.tags.end());
    EXPECT_EQ(ordered, (std::vector<std::string>{"alpha", "mid", "zeta"}));
}

}  // namespace
