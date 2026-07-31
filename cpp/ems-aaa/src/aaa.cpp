#include "ems_aaa/aaa.hpp"

#include <utility>

namespace ems::aaa {

void AaaService::register_session(Session session) {
    const auto id = session.session_id;
    sessions_.insert_or_assign(id, std::move(session));
}

const Session* AaaService::session(std::uint64_t session_id) const {
    const auto it = sessions_.find(session_id);
    return it == sessions_.end() ? nullptr : &it->second;
}

AuthorizationDecision AaaService::authorize(const Session& session, const std::string& tag) {
    if (tag.empty() || session.identity.holds(tag)) {
        return AuthorizationDecision::allow();
    }
    return AuthorizationDecision::deny(
        kCodeMissingTag, "PRM",
        "User " + session.identity.user + " does not have permission tag #" + tag + ".");
}

}  // namespace ems::aaa
