#include "ems_it/slice_runner.hpp"

#include <array>
#include <optional>
#include <set>
#include <string>
#include <string_view>

#include "ems_aaa/aaa.hpp"
#include "ems_validator/validator.hpp"

namespace ems::it {
namespace {

/// Input event adding an instrument to the security master.
constexpr std::string_view kTypeInstrumentCreated = "InstrumentCreated";
/// Output event acknowledging one.
constexpr std::string_view kTypeInstrumentAccepted = "InstrumentAccepted";
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

/// Turns a pipeline rejection into a journal event.
///
/// The layer is carried explicitly: two rejections can share a code and differ
/// in which layer produced them once later layers land, and a journal that
/// omitted it would make those indistinguishable on replay.
core::JournalEvent reject_from(std::uint64_t seq, const core::JournalEvent& event,
                               const validator::ValidationResult& result) {
    std::map<std::string, std::string> fields;
    if (result.admin_hint.has_value()) {
        fields.emplace("adminHint", *result.admin_hint);
    }
    fields.emplace("category", result.category);
    fields.emplace("code", result.code);
    fields.emplace("layer", std::string(validator::layer_name(result.layer)));
    fields.emplace("reason", result.message);
    fields.emplace("sessionId", field(event, "sessionId"));
    return core::JournalEvent{seq, std::string(kTypeOrderRejected), std::move(fields)};
}

/// The field's value, or nullopt when absent or empty — the pipeline reads that
/// as "skip the layer this feeds".
std::optional<std::string> non_empty(const core::JournalEvent& event, std::string_view key) {
    const std::string value = field(event, key);
    return value.empty() ? std::nullopt : std::optional<std::string>{value};
}

/// Adds an instrument to the security master.
///
/// An unrecognised status is treated as inactive, so a malformed instrument
/// makes orders on it fail the REFERENCE layer rather than failing the run.
core::JournalEvent on_instrument_created(const core::JournalEvent& event, std::uint64_t seq,
                                         validator::SecurityMaster& securities) {
    const std::string figi = field(event, "figi");
    const std::string raw = field(event, "status");
    const bool active = raw == "ACTIVE";
    const std::string status =
        (active || raw == "SUSPENDED" || raw == "EXPIRED" || raw == "MATURED" ||
         raw == "DEFAULTED")
            ? raw
            : std::string("UNKNOWN");
    securities.add(figi, validator::Instrument{active, status});

    std::map<std::string, std::string> fields;
    fields.emplace("figi", figi);
    fields.emplace("status", status);
    return core::JournalEvent{seq, std::string(kTypeInstrumentAccepted), std::move(fields)};
}

/// Tags from `key`, or `fallback` when the field is absent.
std::set<std::string> tags_or_default(const core::JournalEvent& event, std::string_view key,
                                      const std::set<std::string>& fallback) {
    const auto it = event.fields.find(std::string(key));
    return it == event.fields.end() ? fallback : split_tags(it->second);
}

core::JournalEvent on_session_logon(const core::JournalEvent& event, std::uint64_t seq,
                                    aaa::AaaService& service) {
    const auto session_id = parse_session_id(event);
    const std::set<std::string> user_tags = split_tags(field(event, "tags"));

    // firmTags / deskTags default to the user's tags, so the common case reads
    // as "this user may do these things" and the AND-gate passes. A case that
    // wants a firm- or desk-level denial states them explicitly.
    const std::set<std::string> firm_tags = tags_or_default(event, "firmTags", user_tags);
    const std::set<std::string> desk_tags = tags_or_default(event, "deskTags", user_tags);

    aaa::Identity identity{field(event, "firm"), field(event, "desk"), field(event, "user"),
                           user_tags};

    std::map<std::string, std::string> fields;
    fields.emplace("deskTags", join_tags(desk_tags));
    fields.emplace("firmTags", join_tags(firm_tags));
    fields.emplace("sessionId", session_id_text(session_id));
    // The granted tags are echoed so a corpus case can show *why* a later
    // rejection happened without the reader having to re-read the input.
    fields.emplace("tags", join_tags(user_tags));
    fields.emplace("user", identity.user);

    service.register_session(session_id.value_or(UINT64_MAX), std::move(identity), firm_tags,
                             desk_tags);

    return core::JournalEvent{seq, std::string(kTypeSessionAccepted), std::move(fields)};
}

core::JournalEvent on_order_new(const core::JournalEvent& event, std::uint64_t seq,
                                const aaa::AaaService& service,
                                const validator::SecurityMaster& securities,
                                core::DeterministicIds& ids) {
    // The whole decision is the pipeline's. SESSION, IDENTITY, REFERENCE and
    // PERMISSION run in that fixed order and the first failure short-circuits,
    // so the reject the journal carries names the outermost thing that was
    // wrong — which is the one worth telling a trader about.
    const validator::ValidationRequest request{field(event, "clOrdId"), parse_session_id(event),
                                               non_empty(event, "tag"),
                                               non_empty(event, "figi")};

    if (const auto result = validator::validate(request, service, securities); !result.passed) {
        return reject_from(seq, event, result);
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
    validator::SecurityMaster securities;
    std::uint64_t seq = 0;

    for (const auto& event : input) {
        ++seq;
        if (event.type == kTypeInstrumentCreated) {
            output.push_back(on_instrument_created(event, seq, securities));
        } else if (event.type == kTypeSessionLogon) {
            output.push_back(on_session_logon(event, seq, service));
        } else if (event.type == kTypeOrderNew) {
            output.push_back(on_order_new(event, seq, service, securities, ids));
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
