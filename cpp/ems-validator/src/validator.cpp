#include "ems_validator/validator.hpp"

#include <utility>

namespace ems::validator {

std::string_view layer_name(ValidationLayer layer) {
    switch (layer) {
        case ValidationLayer::kSession:
            return "SESSION";
        case ValidationLayer::kIdentity:
            return "IDENTITY";
        case ValidationLayer::kReference:
            return "REFERENCE";
        case ValidationLayer::kPermission:
            return "PERMISSION";
    }
    return "UNKNOWN";
}

void SecurityMaster::add(const std::string& figi, Instrument instrument) {
    instruments_.insert_or_assign(figi, std::move(instrument));
}

const Instrument* SecurityMaster::lookup(const std::string& figi) const {
    const auto it = instruments_.find(figi);
    return it == instruments_.end() ? nullptr : &it->second;
}

namespace {

ValidationResult reject(const ValidationRequest& request, std::string_view code,
                        std::string_view category, ValidationLayer layer, std::string message,
                        std::string admin_hint) {
    ValidationResult result;
    result.passed = false;
    result.request_id = request.request_id;
    result.code = std::string(code);
    result.category = std::string(category);
    result.layer = layer;
    result.message = std::move(message);
    result.admin_hint = std::move(admin_hint);
    return result;
}

}  // namespace

ValidationResult validate(const ValidationRequest& request, const aaa::AaaService& aaa_service,
                          const SecurityMaster& securities) {
    // Layer 1 — SESSION
    const aaa::Session* session =
        request.session_id.has_value() ? aaa_service.session(*request.session_id) : nullptr;
    if (session == nullptr) {
        const std::string shown =
            request.session_id.has_value() ? std::to_string(*request.session_id) : "-1";
        return reject(request, aaa::kCodeSessionNotFound, "SES", ValidationLayer::kSession,
                      "Session " + shown + " not found or has expired.", "Talk to session admin.");
    }

    // Layer 2 — IDENTITY: pass-through placeholder, matching Java.

    // Layer 3 — REFERENCE
    if (request.figi.has_value()) {
        const Instrument* instrument = securities.lookup(*request.figi);
        if (instrument == nullptr) {
            return reject(request, kCodeUnknownFigi, "REF", ValidationLayer::kReference,
                          "FIGI " + *request.figi + " not present in security master.",
                          "Verify symbology onboarding for this instrument.");
        }
        if (!instrument->active) {
            return reject(request, kCodeInactiveInstrument, "REF", ValidationLayer::kReference,
                          "Instrument " + *request.figi + " is not active (status: " +
                              instrument->status + ").",
                          "Verify symbology onboarding for this instrument.");
        }
    }

    // Layer 4 — PERMISSION
    if (request.tag.has_value()) {
        const auto decision = aaa_service.authorize(*session, *request.tag);
        if (!decision.allowed) {
            return reject(request, decision.code, "PRM", ValidationLayer::kPermission,
                          decision.message, "Talk to " + decision.admin_hint + ".");
        }
    }

    return ValidationResult::pass(request.request_id);
}

}  // namespace ems::validator
