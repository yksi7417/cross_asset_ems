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

namespace ems::aaa {

/// Catalog code: session ID unknown or already logged out.
inline constexpr std::string_view kCodeSessionNotFound = "EMS-SES-1002";

/// Catalog code: user does not hold the required permission tag.
inline constexpr std::string_view kCodeMissingTag = "EMS-PRM-1001";

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
struct AuthorizationDecision {
    bool allowed{true};
    std::string code;
    std::string category;
    std::string reason;

    [[nodiscard]] static AuthorizationDecision allow() { return AuthorizationDecision{}; }

    [[nodiscard]] static AuthorizationDecision deny(std::string_view code,
                                                    std::string_view category,
                                                    std::string reason) {
        return AuthorizationDecision{false, std::string(code), std::string(category),
                                     std::move(reason)};
    }
};

/// Sessions registered from the journal, held for the length of one run.
///
/// Not thread-safe. The slice binary is single-threaded by design.
class AaaService {
public:
    /// Registers a session, replacing any existing one with the same identifier.
    ///
    /// Replacement rather than rejection: a second logon on the same identifier
    /// is a re-logon, and the FIX session layer that would police it is out of
    /// scope for this slice (ADR 0002).
    void register_session(Session session);

    /// Returns the session, or nullopt when the identifier is unknown.
    [[nodiscard]] const Session* session(std::uint64_t session_id) const;

    /// Decides whether `session` may act with `tag`. An empty tag means the
    /// action requires no entitlement.
    [[nodiscard]] static AuthorizationDecision authorize(const Session& session,
                                                         const std::string& tag);

private:
    std::map<std::uint64_t, Session> sessions_;
};

}  // namespace ems::aaa
