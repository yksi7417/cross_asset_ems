#include "ems_it/slice_runner.hpp"

#include <array>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>
#include <string_view>

#include "ems_aaa/aaa.hpp"
#include "ems_validator/validator.hpp"
#include "order_fsm.hpp"

namespace ems::it {
namespace {

/// Input event adding an instrument to the security master.
constexpr std::string_view kTypeInstrumentCreated = "InstrumentCreated";
/// Output event acknowledging one.
constexpr std::string_view kTypeInstrumentAccepted = "InstrumentAccepted";
/// Input event carrying a venue or client action against a live order.
///
/// One input type rather than one per FSM event: the journal names the FSM
/// event in a field, so adding a transition to the schema needs no new type
/// here. An unrecognised name is ignored, not fatal.
constexpr std::string_view kTypeOrderEvent = "OrderEvent";
/// Output event recording every FSM transition taken. Read by fsm_coverage.py.
constexpr std::string_view kTypeFsmTransition = "FsmTransition";
/// Output event for an order action that changed nothing.
constexpr std::string_view kTypeOrderEventIgnored = "OrderEventIgnored";
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

/// An order and its FSM state, keyed on the CLIENT's identifier.
///
/// ClOrdID exists before the order reaches us; OrderID is ours and is assigned
/// on acceptance. That is the FIX convention, and it is what lets a rejected
/// order take the real PENDING_NEW -> REJECTED transition without consuming an
/// order id.
using OrderBook = std::map<std::string, std::pair<crossasset::ems::fsm::OrderFsmState,
                                                  crossasset::ems::fsm::OrderFsmContext>>;

/// The FSM context an order starts with, from the fields the journal carries.
crossasset::ems::fsm::OrderFsmContext context_for(const std::string& cl_ord_id,
                                                  const core::JournalEvent& event) {
    crossasset::ems::fsm::OrderFsmContext ctx;
    const std::string raw_qty = field(event, "qty");
    std::uint64_t qty = 0;
    for (const char c : raw_qty) {
        if (c >= '0' && c <= '9') {
            qty = (qty * 10U) + static_cast<std::uint64_t>(c - '0');
        }
    }
    ctx.orderId = cl_ord_id;
    ctx.clOrdId = cl_ord_id;
    ctx.instrumentId = field(event, "figi");
    ctx.side = (field(event, "side") == "SELL") ? 2 : 1;
    ctx.orderQty = qty;
    ctx.cumQty = 0;
    ctx.leavesQty = qty;
    ctx.account = field(event, "account");
    ctx.tif = 0;
    ctx.initialClOrdId = cl_ord_id;
    ctx.chainId = cl_ord_id;
    ctx.orderVersion = 1;
    return ctx;
}

std::int64_t parse_i64(const std::string& raw) {
    std::int64_t value = 0;
    for (const char c : raw) {
        if (c >= '0' && c <= '9') {
            value = (value * 10) + (c - '0');
        }
    }
    return value;
}

/// Applies a transition and records it.
///
/// A no-transition is recorded too, with applied=false. Dropping it would make a
/// case that fed the FSM an event it ignored look identical to one that never
/// sent the event.
std::uint64_t emit_transition(const std::string& cl_ord_id,
                              crossasset::ems::fsm::OrderFsmEvent fsm_event,
                              const void* payload,
                              const crossasset::ems::fsm::OrderFsmContext& seed,
                              std::uint64_t seq, OrderBook& orders,
                              std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;

    const auto it = orders.find(cl_ord_id);
    const f::OrderFsmState before =
        it == orders.end() ? f::OrderFsmState::PENDING_NEW : it->second.first;
    const f::OrderFsmContext ctx = it == orders.end() ? seed : it->second.second;

    const auto result = f::transition(before, fsm_event, ctx, payload);
    if (!result.isNoTransition) {
        orders.insert_or_assign(cl_ord_id, std::make_pair(result.newState, result.newContext));
    } else if (it == orders.end()) {
        orders.insert_or_assign(cl_ord_id, std::make_pair(before, ctx));
    }

    std::map<std::string, std::string> fields;
    fields.emplace("applied", result.isNoTransition ? "false" : "true");
    fields.emplace("clOrdId", cl_ord_id);
    fields.emplace("event", f::name(fsm_event));
    fields.emplace("from", f::name(before));
    fields.emplace("fsm", "order");
    fields.emplace("to", f::name(result.isNoTransition ? before : result.newState));
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeFsmTransition), std::move(fields)});
    return seq + 1U;
}

std::uint64_t push_ignored(std::uint64_t seq, const std::string& cl_ord_id,
                           const std::string& raw_event, const std::string& reason,
                           std::vector<core::JournalEvent>& output) {
    std::map<std::string, std::string> fields;
    fields.emplace("clOrdId", cl_ord_id);
    fields.emplace("event", raw_event);
    fields.emplace("reason", reason);
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeOrderEventIgnored), std::move(fields)});
    return seq + 1U;
}

/// Applies a venue or client action to a live order.
std::uint64_t on_order_event(const core::JournalEvent& event, std::uint64_t seq,
                             OrderBook& orders, std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;
    const std::string cl_ord_id = field(event, "clOrdId");
    const std::string raw = field(event, "event");

    const auto fsm_event = f::OrderFsmEventFromName(raw);
    if (!fsm_event.has_value()) {
        return push_ignored(seq, cl_ord_id, raw, "unknown FSM event", output);
    }

    const auto it = orders.find(cl_ord_id);
    if (it == orders.end()) {
        return push_ignored(seq, cl_ord_id, raw, "unknown order", output);
    }

    f::OrderFsmPayloads::PartialFillPayload partial{};
    f::OrderFsmPayloads::FullFillPayload full{};
    const void* payload = nullptr;
    if (*fsm_event == f::OrderFsmEvent::PartialFill) {
        partial.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
        partial.lastPx = parse_i64(field(event, "lastPx"));
        partial.execId = field(event, "execId");
        payload = &partial;
    } else if (*fsm_event == f::OrderFsmEvent::FullFill) {
        full.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
        full.lastPx = parse_i64(field(event, "lastPx"));
        full.execId = field(event, "execId");
        payload = &full;
    }

    return emit_transition(cl_ord_id, *fsm_event, payload, it->second.second, seq, orders, output);
}

std::uint64_t on_order_new(const core::JournalEvent& event, std::uint64_t seq,
                           const aaa::AaaService& service,
                           const validator::SecurityMaster& securities,
                           core::DeterministicIds& ids, OrderBook& orders,
                           std::vector<core::JournalEvent>& output) {
    // The whole decision is the pipeline's. SESSION, IDENTITY, REFERENCE and
    // PERMISSION run in that fixed order and the first failure short-circuits,
    // so the reject the journal carries names the outermost thing that was
    // wrong — which is the one worth telling a trader about.
    const validator::ValidationRequest request{field(event, "clOrdId"), parse_session_id(event),
                                               non_empty(event, "tag"),
                                               non_empty(event, "figi")};

    const std::string cl_ord_id = field(event, "clOrdId");
    const auto context = context_for(cl_ord_id, event);

    if (const auto result = validator::validate(request, service, securities); !result.passed) {
        seq = emit_transition(cl_ord_id, crossasset::ems::fsm::OrderFsmEvent::ValidationFailed,
                              nullptr, context, seq, orders, output);
        output.push_back(reject_from(seq + 1U, event, result));
        return seq + 1U;
    }

    seq = emit_transition(cl_ord_id, crossasset::ems::fsm::OrderFsmEvent::ValidationPassed, nullptr,
                          context, seq, orders, output);

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
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeOrderAccepted), std::move(fields)});
    return seq + 1U;
}

}  // namespace

std::vector<core::JournalEvent> run_slice(const std::vector<core::JournalEvent>& input,
                                          core::DeterministicIds& ids) {
    std::vector<core::JournalEvent> output;
    output.reserve(input.size() + 1U);
    aaa::AaaService service;
    validator::SecurityMaster securities;
    OrderBook orders;
    std::uint64_t seq = 0;

    for (const auto& event : input) {
        if (event.type == kTypeInstrumentCreated) {
            output.push_back(on_instrument_created(event, ++seq, securities));
        } else if (event.type == kTypeSessionLogon) {
            output.push_back(on_session_logon(event, ++seq, service));
        } else if (event.type == kTypeOrderNew) {
            seq = on_order_new(event, seq, service, securities, ids, orders, output);
        } else if (event.type == kTypeOrderEvent) {
            seq = on_order_event(event, seq, orders, output);
        } else {
            core::JournalEvent copy = event;
            copy.seq = ++seq;
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
