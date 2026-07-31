#pragma once

// The layered validation pipeline.
//
// A port of io.crossasset.ems.validator.LayeredValidatorPipeline. Layers run in
// a fixed order and the first failure short-circuits, so the reject a caller
// sees names the OUTERMOST thing that was wrong — which is the one worth
// telling a trader about. An order against an unknown session and an unknown
// instrument is a session problem; saying "unknown instrument" would send them
// to symbology onboarding for a login failure.
//
// Layers 5-8 of the Java pipeline (ASSET_CLASS, LIMITS, MARKET, ROUTE) are
// stubs there and absent here. When they land they land in both.

#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <string_view>

#include "ems_aaa/aaa.hpp"

namespace ems::validator {

/// Catalog code: FIGI not present in the security master.
inline constexpr std::string_view kCodeUnknownFigi = "EMS-REF-2001";

/// Catalog code: instrument present but not active.
inline constexpr std::string_view kCodeInactiveInstrument = "EMS-REF-2002";

/// Which layer produced a decision.
///
/// Two rejections can share a code and differ in the layer that produced them
/// once later layers land, so the layer is carried explicitly rather than
/// inferred from the code.
enum class ValidationLayer { kSession, kIdentity, kReference, kPermission };

/// The layer name as it reaches the journal. Matches the Java enum constant.
[[nodiscard]] std::string_view layer_name(ValidationLayer layer);

/// Whether an instrument is known, and whether it is tradable.
///
/// Carries only what the REFERENCE layer consults. The Java InstrumentCore has
/// twenty-two fields; modelling all of them here would imply the slice
/// validates against them, and it does not.
struct Instrument {
    bool active{true};
    /// Lifecycle status name, quoted in the rejection when not active.
    std::string status{"ACTIVE"};
};

/// The security master, as the REFERENCE layer consumes it.
class SecurityMaster {
public:
    void add(const std::string& figi, Instrument instrument);

    [[nodiscard]] const Instrument* lookup(const std::string& figi) const;

private:
    std::map<std::string, Instrument> instruments_;
};

/// What the pipeline is asked to validate.
///
/// `tag` and `figi` are optional because "not supplied" is a real state the
/// journal expresses by omitting the field — and it means "skip that layer",
/// not "validate against nothing".
struct ValidationRequest {
    std::string request_id;
    std::optional<std::uint64_t> session_id;
    std::optional<std::string> tag;
    std::optional<std::string> figi;
};

/// The outcome of a validation pass.
struct ValidationResult {
    bool passed{true};
    std::string request_id;
    std::string code;
    std::string category;
    ValidationLayer layer{ValidationLayer::kSession};
    std::string message;
    std::optional<std::string> admin_hint;

    [[nodiscard]] static ValidationResult pass(std::string request_id) {
        ValidationResult result;
        result.request_id = std::move(request_id);
        return result;
    }
};

/// Runs the layers in order, short-circuiting on the first failure.
///
/// `aaa` supplies the SESSION and PERMISSION layers; `securities` supplies
/// REFERENCE. IDENTITY is a pass-through, as in Java — session logon already
/// resolved the identity, and there is no separate catalog code for
/// identity-not-found.
[[nodiscard]] ValidationResult validate(const ValidationRequest& request,
                                        const aaa::AaaService& aaa_service,
                                        const SecurityMaster& securities);

}  // namespace ems::validator
