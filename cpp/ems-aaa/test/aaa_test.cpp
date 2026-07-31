// Mirrored by java/ems-aaa/.../TagPermissionTest.java (the production evaluator
// this ports) and the inline tests in rust/ems-aaa/src/lib.rs.
//
// The codes and message wording are a cross-language contract: both reach the
// output journal the conformance gate compares byte-for-byte.

#include "ems_aaa/aaa.hpp"

#include <gtest/gtest.h>

#include <set>
#include <string>
#include <vector>

namespace {

using ems::aaa::AaaService;
using ems::aaa::DenialLevel;
using ems::aaa::Identity;

Identity identity(const std::string& user, const std::set<std::string>& tags) {
    return Identity{"FIRM1", "DESK1", user, tags};
}

/// Registers session 7 granting the same tags at all three layers.
AaaService service_with(const std::string& user, const std::set<std::string>& all_tags) {
    AaaService service;
    service.register_session(7, identity(user, all_tags), all_tags, all_tags);
    return service;
}

TEST(Aaa, UnknownSessionIsNull) { EXPECT_EQ(AaaService{}.session(42), nullptr); }

TEST(Aaa, RegisteredSessionIsFound) {
    const auto service = service_with("trader1", {"order-entry"});

    const auto* found = service.session(7);
    ASSERT_NE(found, nullptr);
    EXPECT_EQ(found->identity.user, "trader1");
}

TEST(Aaa, ReLogonReplacesTheSession) {
    AaaService service;
    service.register_session(7, identity("trader1", {"order-entry"}), {"order-entry"},
                             {"order-entry"});
    service.register_session(7, identity("trader2", {"order-entry"}), {"order-entry"},
                             {"order-entry"});

    // Policing duplicate logons belongs to the FIX session layer, which ADR 0002
    // puts out of scope for this slice.
    const auto* found = service.session(7);
    ASSERT_NE(found, nullptr);
    EXPECT_EQ(found->identity.user, "trader2");
}

TEST(Aaa, HeldTagIsAllowedAtAllThreeLayers) {
    const auto service = service_with("trader1", {"order-entry"});

    EXPECT_TRUE(service.authorize(*service.session(7), "order-entry").allowed);
}

TEST(Aaa, EmptyTagRequiresNoEntitlement) {
    const auto service = service_with("trader1", {});

    EXPECT_TRUE(service.authorize(*service.session(7), "").allowed);
}

TEST(Aaa, FirmDenialIsReportedBeforeTheDeskIsConsulted) {
    // Outermost-first is part of the contract: a missing firm grant makes the
    // inner resolution irrelevant, so reporting "you lack the tag" would send
    // the user to the wrong administrator.
    AaaService service;
    service.register_session(7, identity("trader1", {"order-entry"}), {}, {});

    const auto decision = service.authorize(*service.session(7), "order-entry");

    ASSERT_FALSE(decision.allowed);
    EXPECT_EQ(decision.code, "EMS-PRM-1003");
    EXPECT_EQ(decision.level, DenialLevel::kFirm);
    EXPECT_EQ(decision.message, "Firm `FIRM1` is not granted tag `#order-entry`.");
}

TEST(Aaa, DeskDenialWhenTheFirmGrantsButTheDeskDoesNot) {
    AaaService service;
    service.register_session(7, identity("trader2", {"order-entry"}), {"order-entry"}, {});

    const auto decision = service.authorize(*service.session(7), "order-entry");

    ASSERT_FALSE(decision.allowed);
    EXPECT_EQ(decision.code, "EMS-PRM-1002");
    EXPECT_EQ(decision.level, DenialLevel::kDesk);
    EXPECT_EQ(decision.message,
              "User `trader2` has tag `#order-entry` but desk `DESK1` is not granted.");
}

TEST(Aaa, UserDenialWhenBothOuterLayersGrant) {
    AaaService service;
    service.register_session(7, identity("trader3", {"market-data"}), {"order-entry"},
                             {"order-entry"});

    const auto decision = service.authorize(*service.session(7), "order-entry");

    ASSERT_FALSE(decision.allowed);
    EXPECT_EQ(decision.code, "EMS-PRM-1001");
    EXPECT_EQ(decision.level, DenialLevel::kUser);
    EXPECT_EQ(decision.message, "User `trader3` does not have permission tag `#order-entry`.");
}

TEST(Aaa, TagsIterateInLexicographicOrderRegardlessOfInsertionOrder) {
    const auto id = identity("trader1", {"zeta", "alpha", "mid"});

    // The tag set reaches the output journal, so its order is a contract.
    const std::vector<std::string> ordered(id.tags.begin(), id.tags.end());
    EXPECT_EQ(ordered, (std::vector<std::string>{"alpha", "mid", "zeta"}));
}

}  // namespace
