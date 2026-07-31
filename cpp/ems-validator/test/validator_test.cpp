// Mirrored by rust/ems-validator/src/lib.rs and, on the Java side, by the
// pipeline this ports: io.crossasset.ems.validator.LayeredValidatorPipeline.
//
// Layer ORDER is the property most likely to drift between three
// implementations, because nothing about a single-fault test constrains it.
// Two tests here put two faults in one request and assert which one wins.

#include "ems_validator/validator.hpp"

#include <gtest/gtest.h>

#include <optional>
#include <set>
#include <string>

namespace {

using ems::aaa::AaaService;
using ems::aaa::Identity;
using ems::validator::Instrument;
using ems::validator::SecurityMaster;
using ems::validator::validate;
using ems::validator::ValidationLayer;
using ems::validator::ValidationRequest;

AaaService aaa_with(const std::set<std::string>& user_tags) {
    AaaService service;
    service.register_session(7, Identity{"FIRM1", "DESK1", "trader1", user_tags}, user_tags,
                             user_tags);
    return service;
}

SecurityMaster securities() {
    SecurityMaster master;
    master.add("BBG000B9XRY4", Instrument{true, "ACTIVE"});
    master.add("BBG000SUSPEND", Instrument{false, "SUSPENDED"});
    return master;
}

ValidationRequest request(std::optional<std::uint64_t> session_id,
                          std::optional<std::string> tag, std::optional<std::string> figi) {
    return ValidationRequest{"C-1", session_id, std::move(tag), std::move(figi)};
}

TEST(Validator, EverythingValidPasses) {
    const auto result =
        validate(request(7, "order-entry", "BBG000B9XRY4"), aaa_with({"order-entry"}), securities());

    EXPECT_TRUE(result.passed);
}

TEST(Validator, UnknownSessionFailsAtTheSessionLayer) {
    const auto result = validate(request(99, "order-entry", "BBG000B9XRY4"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.code, "EMS-SES-1002");
    EXPECT_EQ(result.layer, ValidationLayer::kSession);
    EXPECT_EQ(result.message, "Session 99 not found or has expired.");
}

TEST(Validator, SessionIsCheckedBeforeReference) {
    // Both the session and the instrument are bad. SESSION is layer 1, so it
    // wins — telling the trader "unknown instrument" would send them to
    // symbology onboarding for a login failure.
    const auto result = validate(request(99, "order-entry", "BBG000NOTREAL"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.layer, ValidationLayer::kSession);
}

TEST(Validator, UnknownFigiFailsAtTheReferenceLayer) {
    const auto result = validate(request(7, "order-entry", "BBG000NOTREAL"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.code, "EMS-REF-2001");
    EXPECT_EQ(result.layer, ValidationLayer::kReference);
    EXPECT_EQ(result.message, "FIGI BBG000NOTREAL not present in security master.");
    ASSERT_TRUE(result.admin_hint.has_value());
    EXPECT_EQ(*result.admin_hint, "Verify symbology onboarding for this instrument.");
}

TEST(Validator, InactiveInstrumentFailsAtTheReferenceLayer) {
    const auto result = validate(request(7, "order-entry", "BBG000SUSPEND"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.code, "EMS-REF-2002");
    EXPECT_EQ(result.message, "Instrument BBG000SUSPEND is not active (status: SUSPENDED).");
}

TEST(Validator, ReferenceIsCheckedBeforePermission) {
    // The instrument is unknown AND the tag is not granted. REFERENCE is layer
    // 3, PERMISSION layer 4.
    const auto result = validate(request(7, "algo-trading", "BBG000NOTREAL"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.layer, ValidationLayer::kReference);
}

TEST(Validator, MissingTagFailsAtThePermissionLayer) {
    const auto result = validate(request(7, "algo-trading", "BBG000B9XRY4"),
                                 aaa_with({"order-entry"}), securities());

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.code, "EMS-PRM-1003");
    EXPECT_EQ(result.layer, ValidationLayer::kPermission);
    ASSERT_TRUE(result.admin_hint.has_value());
    EXPECT_EQ(*result.admin_hint, "Talk to FIRM1 admin.");
}

TEST(Validator, AbsentFigiSkipsTheReferenceLayer) {
    const auto result =
        validate(request(7, "order-entry", std::nullopt), aaa_with({"order-entry"}), SecurityMaster{});

    EXPECT_TRUE(result.passed);
}

TEST(Validator, AbsentTagSkipsThePermissionLayer) {
    const auto result =
        validate(request(7, std::nullopt, "BBG000B9XRY4"), aaa_with({}), securities());

    EXPECT_TRUE(result.passed);
}

TEST(Validator, AbsentSessionIdFailsAtTheSessionLayer) {
    const auto result =
        validate(request(std::nullopt, std::nullopt, std::nullopt), aaa_with({}), SecurityMaster{});

    ASSERT_FALSE(result.passed);
    EXPECT_EQ(result.layer, ValidationLayer::kSession);
    EXPECT_EQ(result.message, "Session -1 not found or has expired.");
}

}  // namespace
