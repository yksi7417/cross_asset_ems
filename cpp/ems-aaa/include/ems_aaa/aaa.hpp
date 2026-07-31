#pragma once

// Session lookup and the entitlement decision, sized for the deterministic
// slice.
//
// This is the authorization decision only — no logon credentials, no SSO, no
// SCIM, no session sequence recovery. ADR 0002 scopes the component that way.
//
// Sessions arrive on the journal as SessionLogon events rather than from
// configuration, which keeps the whole entitlement state derivable from the
// input. That is what lets a corpus case describe an authorization failure
// with no external fixture.

#include <cstdint>
#include <map>
#include <optional>
#include <set>
#include <string>
#include <string_view>
#include <utility>

namespace ems::aaa {

/// Catalog code: session ID unknown or already logged out.
inline constexpr std::string_view kCodeSessionNotFound = "EMS-SES-1002";

/// Catalog code: the user does not hold the tag (innermost layer).
inline constexpr std::string_view kCodeUserMissingTag = "EMS-PRM-1001";

/// Catalog code: the user holds the tag but the desk is not granted it.
inline constexpr std::string_view kCodeDeskNotGranted = "EMS-PRM-1002";

/// Catalog code: the firm is not granted the tag (outermost layer).
inline constexpr std::string_view kCodeFirmNotGranted = "EMS-PRM-1003";

/// Which layer of the AND-gate refused.
enum class DenialLevel { kFirm, kDesk, kUser };

/// Who is acting, and what they are entitled to.
///
/// `tags` is a std::set for the same reason journal fields are a std::map: its
/// iteration order can reach the output journal, and the conformance gate
/// compares that journal byte-for-byte across three languages.
struct Identity {
    std::string firm;
    std::string desk;
    std::string user;
    std::set<std::string> tags;

    /// True when this identity holds `tag`.
    [[nodiscard]] bool holds(const std::string& tag) const { return tags.contains(tag); }

    friend bool operator==(const Identity&, const Identity&) = default;
};

/// An established session.
///
/// No established_at: the production session record carries one read from a
/// clock, and the slice cannot read a clock without giving up byte-identical
/// replay. Logical ordering comes from the journal's sequence numbers.
struct Session {
    std::uint64_t session_id{};
    Identity identity;

    friend bool operator==(const Session&, const Session&) = default;
};

/// The outcome of an entitlement check.
///
/// The reject `code` is a real entry in schemas/reject-codes/catalog.yaml — it
/// reaches the output journal, so an invented code would diverge from the
/// catalog silently and the conformance gate would not notice.
struct AuthorizationResult {
    bool allowed{true};
    std::string code;
    std::string message;
    DenialLevel level{DenialLevel::kUser};
    /// Who can grant the missing permission. Reaches the journal via the
    /// validator, which wraps it as "Talk to {admin_hint}."
    std::string admin_hint;

    [[nodiscard]] static AuthorizationResult allow() { return AuthorizationResult{}; }

    [[nodiscard]] static AuthorizationResult deny(std::string_view code, std::string message,
                                                  DenialLevel level, std::string admin_hint) {
        return AuthorizationResult{false, std::string(code), std::move(message), level,
                                   std::move(admin_hint)};
    }
};

/// Firm- and desk-level tag grants. User-level grants live on Identity::tags.
class TagPermissionStore {
public:
    void grant_firm_tag(const std::string& firm, const std::string& tag);
    void grant_desk_tag(const std::string& firm, const std::string& desk, const std::string& tag);

    [[nodiscard]] bool firm_granted(const std::string& firm, const std::string& tag) const;
    [[nodiscard]] bool desk_granted(const std::string& firm, const std::string& desk,
                                    const std::string& tag) const;

private:
    std::map<std::string, std::set<std::string>> firm_grants_;
    std::map<std::pair<std::string, std::string>, std::set<std::string>> desk_grants_;
};

/// Sessions registered from the journal, held for the length of one run.
///
/// Not thread-safe. The slice binary is single-threaded by design.
class AaaService {
public:
    /// Establishes a session, replacing any existing one with the same identifier.
    ///
    /// Replacement rather than rejection: a second logon on the same identifier
    /// is a re-logon, and the FIX session layer that would police it is out of
    /// scope for this slice (ADR 0002).
    void register_session(std::uint64_t session_id, Identity identity,
                          const std::set<std::string>& firm_tags,
                          const std::set<std::string>& desk_tags);

    /// Returns the session, or nullptr when the identifier is unknown.
    [[nodiscard]] const Session* session(std::uint64_t session_id) const;

    /// Runs the three-layer AND-gate, outermost first.
    ///
    /// Order matters and is part of the contract: a firm denial is reported
    /// before the desk is consulted, because a missing firm grant makes the
    /// inner resolution irrelevant — and reporting "you lack the tag" would
    /// send the user to the wrong administrator. An empty tag requires no
    /// entitlement.
    [[nodiscard]] AuthorizationResult authorize(const Session& session,
                                                const std::string& tag) const;

private:
    std::map<std::uint64_t, Session> sessions_;
    TagPermissionStore grants_;
};

}  // namespace ems::aaa
