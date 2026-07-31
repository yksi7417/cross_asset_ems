#include "ems_it/slice_runner.hpp"

#include <array>
#include <optional>
#include <set>
#include <string>
#include <string_view>

#include "ems_aaa/aaa.hpp"

namespace ems::it {
namespace {

/// Input event registering a session and its entitlements.
constexpr std::string_view kTypeSessionLogon = "SessionLogon";
/// Output event acknowledging a logon.
constexpr std::string_view kTypeSessionAccepted = "SessionAccepted";
/// Input event that opens an order.
constexpr std::string_view kTypeOrderNew = "OrderNew";
/// Output event acknowledging one.
constexpr std::string_view kTypeOrderAccepted = "OrderAccepted";
/// Output event refusing one.
constexpr std::string_view kTypeOrderRejected = "OrderRejected";
/// Final output event: makes the seed and the input size visible in the journal.
constexpr std::string_view kTypeRunSummary = "RunSummary";

/// Fields copied from `OrderNew` onto `OrderAccepted`.
///
/// An explicit list rather than "copy everything": an unknown field silently
/// reaching the output would be a divergence that only shows up once some other
/// language's map happens to order it differently.
constexpr std::array<std::string_view, 5> kEchoedFields = {"account", "figi", "price", "qty",
                                                           "side"};

std::string field(const core::JournalEvent& event, std::string_view key) {
    const auto it = event.fields.find(std::string(key));
    return it == event.fields.end() ? std::string{} : it->second;
}

/// nullopt for a missing or non-numeric session id.
///
/// Malformed data on the wire is a rejection, not a defect: the order is
/// refused as "session not found" rather than crashing the run.
std::optional<std::uint64_t> parse_session_id(const core::JournalEvent& event) {
    const std::string raw = field(event, "sessionId");
    if (raw.empty()) {
        return std::nullopt;
    }
    std::uint64_t value = 0;
    for (const char c : raw) {
        if (c < '0' || c > '9') {
            return std::nullopt;
        }
        const auto digit = static_cast<std::uint64_t>(c - '0');
        if (value > ((UINT64_MAX - digit) / 10U)) {
            return std::nullopt;
        }
        value = (value * 10U) + digit;
    }
    return value;
}

/// An unparseable id is rendered as -1, matching Java's parse fallback.
std::string session_id_text(std::optional<std::uint64_t> session_id) {
    return session_id.has_value() ? std::to_string(*session_id) : std::string("-1");
}

/// Splits a comma-separated tag list. Empty entries are dropped; order comes
/// from the set.
std::set<std::string> split_tags(const std::string& raw) {
    std::set<std::string> tags;
    std::size_t start = 0;
    while (start <= raw.size()) {
        const std::size_t comma = raw.find(',', start);
        const std::size_t stop = (comma == std::string::npos) ? raw.size() : comma;
        std::string tag = raw.substr(start, stop - start);
        const auto first = tag.find_first_not_of(" \t");
        const auto last = tag.find_last_not_of(" \t");
        if (first != std::string::npos) {
            tags.insert(tag.substr(first, last - first + 1U));
        }
        if (comma == std::string::npos) {
            break;
        }
        start = comma + 1U;
    }
    return tags;
}

std::string join_tags(const std::set<std::string>& tags) {
    std::string out;
    for (const auto& tag : tags) {
        if (!out.empty()) {
            out.push_back(',');
        }
        out.append(tag);
    }
    return out;
}

core::JournalEvent reject(std::uint64_t seq, const core::JournalEvent& event,
                          std::string_view code, std::string_view category,
                          const std::string& reason) {
    std::map<std::string, std::string> fields;
    fields.emplace("category", std::string(category));
    fields.emplace("code", std::string(code));
    fields.emplace("reason", reason);
    fields.emplace("sessionId", field(event, "sessionId"));
    return core::JournalEvent{seq, std::string(kTypeOrderRejected), std::move(fields)};
}

core::JournalEvent on_session_logon(const core::JournalEvent& event, std::uint64_t seq,
                                    aaa::AaaService& service) {
    const auto session_id = parse_session_id(event);
    aaa::Identity identity{field(event, "firm"), field(event, "desk"), field(event, "user"),
                           split_tags(field(event, "tags"))};

    std::map<std::string, std::string> fields;
    fields.emplace("sessionId", session_id_text(session_id));
    fields.emplace("user", identity.user);
    // The granted tags are echoed so a corpus case can show *why* a later
    // rejection happened without the reader having to re-read the input.
    fields.emplace("tags", join_tags(identity.tags));

    service.register_session(
        aaa::Session{session_id.value_or(UINT64_MAX), std::move(identity)});

    return core::JournalEvent{seq, std::string(kTypeSessionAccepted), std::move(fields)};
}

core::JournalEvent on_order_new(const core::JournalEvent& event, std::uint64_t seq,
                                const aaa::AaaService& service, core::DeterministicIds& ids) {
    const auto session_id = parse_session_id(event);
    const aaa::Session* session =
        session_id.has_value() ? service.session(*session_id) : nullptr;

    if (session == nullptr) {
        return reject(seq, event, aaa::kCodeSessionNotFound, "SES",
                      "Session " + session_id_text(session_id) + " not found or has expired.");
    }

    const std::string tag = field(event, "tag");
    if (const auto decision = aaa::AaaService::authorize(*session, tag); !decision.allowed) {
        return reject(seq, event, decision.code, decision.category, decision.reason);
    }

    // Only an accepted order consumes an identifier. If a rejected one did, the
    // ids in a journal would depend on how many orders failed — and every corpus
    // case downstream of a rejection would shift.
    std::map<std::string, std::string> fields;
    fields.emplace("orderId", ids.next_order_id());
    for (const auto& key : kEchoedFields) {
        const auto it = event.fields.find(std::string(key));
        if (it != event.fields.end()) {
            fields.emplace(it->first, it->second);
        }
    }
    return core::JournalEvent{seq, std::string(kTypeOrderAccepted), std::move(fields)};
}

}  // namespace

std::vector<core::JournalEvent> run_slice(const std::vector<core::JournalEvent>& input,
                                          core::DeterministicIds& ids) {
    std::vector<core::JournalEvent> output;
    output.reserve(input.size() + 1U);
    aaa::AaaService service;
    std::uint64_t seq = 0;

    for (const auto& event : input) {
        ++seq;
        if (event.type == kTypeSessionLogon) {
            output.push_back(on_session_logon(event, seq, service));
        } else if (event.type == kTypeOrderNew) {
            output.push_back(on_order_new(event, seq, service, ids));
        } else {
            core::JournalEvent copy = event;
            copy.seq = seq;
            output.push_back(std::move(copy));
        }
    }

    std::map<std::string, std::string> summary;
    summary.emplace("events", std::to_string(input.size()));
    summary.emplace("seed", std::to_string(ids.seed()));
    output.push_back(core::JournalEvent{++seq, std::string(kTypeRunSummary), std::move(summary)});

    return output;
}

}  // namespace ems::it
