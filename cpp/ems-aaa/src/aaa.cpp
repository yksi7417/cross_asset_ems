#include "ems_aaa/aaa.hpp"

#include <utility>

namespace ems::aaa {

void TagPermissionStore::grant_firm_tag(const std::string& firm, const std::string& tag) {
    firm_grants_[firm].insert(tag);
}

void TagPermissionStore::grant_desk_tag(const std::string& firm, const std::string& desk,
                                        const std::string& tag) {
    desk_grants_[std::make_pair(firm, desk)].insert(tag);
}

bool TagPermissionStore::firm_granted(const std::string& firm, const std::string& tag) const {
    const auto it = firm_grants_.find(firm);
    return it != firm_grants_.end() && it->second.contains(tag);
}

bool TagPermissionStore::desk_granted(const std::string& firm, const std::string& desk,
                                      const std::string& tag) const {
    const auto it = desk_grants_.find(std::make_pair(firm, desk));
    return it != desk_grants_.end() && it->second.contains(tag);
}

void AaaService::register_session(std::uint64_t session_id, Identity identity,
                                  const std::set<std::string>& firm_tags,
                                  const std::set<std::string>& desk_tags) {
    for (const auto& tag : firm_tags) {
        grants_.grant_firm_tag(identity.firm, tag);
    }
    for (const auto& tag : desk_tags) {
        grants_.grant_desk_tag(identity.firm, identity.desk, tag);
    }
    sessions_.insert_or_assign(session_id, Session{session_id, std::move(identity)});
}

const Session* AaaService::session(std::uint64_t session_id) const {
    const auto it = sessions_.find(session_id);
    return it == sessions_.end() ? nullptr : &it->second;
}

AuthorizationResult AaaService::authorize(const Session& session, const std::string& tag) const {
    if (tag.empty()) {
        return AuthorizationResult::allow();
    }
    const Identity& identity = session.identity;

    if (!grants_.firm_granted(identity.firm, tag)) {
        return AuthorizationResult::deny(
            kCodeFirmNotGranted, "Firm `" + identity.firm + "` is not granted tag `#" + tag + "`.",
            DenialLevel::kFirm);
    }

    if (!grants_.desk_granted(identity.firm, identity.desk, tag)) {
        return AuthorizationResult::deny(kCodeDeskNotGranted,
                                         "User `" + identity.user + "` has tag `#" + tag +
                                             "` but desk `" + identity.desk + "` is not granted.",
                                         DenialLevel::kDesk);
    }

    if (!identity.holds(tag)) {
        return AuthorizationResult::deny(
            kCodeUserMissingTag,
            "User `" + identity.user + "` does not have permission tag `#" + tag + "`.",
            DenialLevel::kUser);
    }

    return AuthorizationResult::allow();
}

}  // namespace ems::aaa
