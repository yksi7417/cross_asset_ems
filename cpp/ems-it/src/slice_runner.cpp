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
#include "route_fsm.hpp"
#include "venuesession_fsm.hpp"

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
/// Input event projecting an accepted order onto one venue.
constexpr std::string_view kTypeRouteNew = "RouteNew";
/// Output event acknowledging a route.
constexpr std::string_view kTypeRouteAccepted = "RouteAccepted";
/// Output event refusing one.
constexpr std::string_view kTypeRouteRejected = "RouteRejected";
/// Input event carrying a venue action against a live route.
///
/// The route counterpart of OrderEvent, named the same way for the same reason:
/// the journal carries the FSM event name in a field, so a transition added to
/// the schema needs no new event type here.
constexpr std::string_view kTypeRouteEvent = "RouteEvent";
/// Output event for a route action that reached no route.
constexpr std::string_view kTypeRouteEventIgnored = "RouteEventIgnored";
/// Input event driving one venue's FIX session.
constexpr std::string_view kTypeVenueSession = "VenueSession";
/// Output event for a session action the schema does not define.
constexpr std::string_view kTypeVenueSessionIgnored = "VenueSessionEventIgnored";
/// Output event: the outbound FIX message a dispatched route produces.
///
/// The journal records that a message was produced and its identifying tags, not
/// a wire-format string. A byte-exact FIX encoder is a component of its own.
constexpr std::string_view kTypeFixOut = "FixOut";
/// Input event: an inbound FIX ExecutionReport from a venue.
constexpr std::string_view kTypeExecutionReport = "ExecutionReport";
/// Output event for an ExecutionReport that reached no route.
constexpr std::string_view kTypeExecutionReportIgnored = "ExecutionReportIgnored";
/// Catalog code: the venue's FIX session cannot currently take an order.
constexpr std::string_view kCodeVenueSessionNotActive = "EMS-VEN-5001";
/// Final output event: makes the seed and the input size visible in the journal.
constexpr std::string_view kTypeRunSummary = "RunSummary";

/// Catalog code: no such order to route.
constexpr std::string_view kCodeRouteUnknownOrder = "EMS-RTE-4001";
/// Catalog code: the order is not in a state that can be routed.
constexpr std::string_view kCodeRouteOrderNotRoutable = "EMS-RTE-4002";
/// Catalog code: the requested quantity is not routable against what is left.
constexpr std::string_view kCodeRouteQtyInvalid = "EMS-RTE-4003";
/// Catalog code: the route's ClOrdID is already in use.
constexpr std::string_view kCodeRouteClOrdIdCollision = "EMS-RTE-2005";

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

/// One FIX session per venue, keyed on the MIC.
///
/// **A venue with no session is not a venue with a broken session.** The routing
/// gate cannot tell them apart and should not have to; the journal can, because
/// "we never connected" and "we connected and got logged out" are different
/// things to tell an operator.
using SessionBook = std::map<std::string, std::pair<crossasset::ems::fsm::VenueSessionFsmState,
                                                    crossasset::ems::fsm::VenueSessionFsmContext>>;

crossasset::ems::fsm::VenueSessionFsmContext session_context(const std::string& venue_mic) {
    crossasset::ems::fsm::VenueSessionFsmContext ctx;
    ctx.sessionId = "SES-" + venue_mic;
    ctx.nextExpectedSeqIn = 1;
    ctx.nextSendSeqOut = 1;
    ctx.heartbeatIntervalSecs = 30;
    ctx.testRequestOutstanding = false;
    ctx.resendWindowLow = 0;
    ctx.resendWindowHigh = 0;
    ctx.venueMic = venue_mic;
    return ctx;
}

/// Whether `venue_mic` can currently take an order.
///
/// ACTIVE only. A session in LOGON_SENT has a TCP connection and no agreed
/// sequence numbers; one in RESEND_IN_PROGRESS is mid-gap-fill. Sending a new
/// order into either is how you end up with an order the venue has and the EMS
/// cannot account for.
bool session_is_active(const SessionBook& sessions, const std::string& venue_mic) {
    const auto it = sessions.find(venue_mic);
    return it != sessions.end() &&
           it->second.first == crossasset::ems::fsm::VenueSessionFsmState::ACTIVE;
}

/// Drives one venue's FIX session.
std::uint64_t on_venue_session(const core::JournalEvent& event, std::uint64_t seq,
                               SessionBook& sessions,
                               std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;
    const std::string venue_mic = field(event, "venueMic");
    const std::string raw = field(event, "event");

    const auto fsm_event = f::VenueSessionFsmEventFromName(raw);
    if (!fsm_event.has_value()) {
        std::map<std::string, std::string> fields;
        fields.emplace("event", raw);
        fields.emplace("reason", "unknown FSM event");
        fields.emplace("venueMic", venue_mic);
        output.push_back(
            core::JournalEvent{seq + 1U, std::string(kTypeVenueSessionIgnored), std::move(fields)});
        return seq + 1U;
    }

    // A venue we have never heard of starts in the schema's initial state rather
    // than being refused, so ConnectRequested is reachable by a corpus case.
    auto found = sessions.find(venue_mic);
    if (found == sessions.end()) {
        found = sessions
                    .insert_or_assign(venue_mic,
                                      std::make_pair(f::VenueSessionFsmState::DISCONNECTED,
                                                     session_context(venue_mic)))
                    .first;
    }

    const f::VenueSessionFsmState before = found->second.first;
    const auto result = f::transition(before, *fsm_event, found->second.second, nullptr);
    if (!result.isNoTransition) {
        sessions.insert_or_assign(venue_mic, std::make_pair(result.newState, result.newContext));
    }

    std::map<std::string, std::string> fields;
    fields.emplace("applied", result.isNoTransition ? "false" : "true");
    fields.emplace("event", f::name(*fsm_event));
    fields.emplace("from", f::name(before));
    fields.emplace("fsm", "venue_session");
    fields.emplace("to", f::name(result.isNoTransition ? before : result.newState));
    fields.emplace("venueMic", venue_mic);
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeFsmTransition), std::move(fields)});
    return seq + 1U;
}

/// FIX ExecType (tag 150) to a route FSM event.
///
/// An explicit table, not a name-matching convention. The FIX values are one
/// character and the schema's event names are not, so there is no derivation to
/// be had — and a wrong guess here is a venue message silently applied to the
/// wrong transition.
std::optional<crossasset::ems::fsm::RouteFsmEvent> from_exec_type(const std::string& exec_type,
                                                                  const std::string& ord_status) {
    namespace f = crossasset::ems::fsm;
    if (exec_type == "0") return f::RouteFsmEvent::RouteAcknowledged;
    if (exec_type == "4") return f::RouteFsmEvent::RouteCanceled;
    if (exec_type == "5") return f::RouteFsmEvent::RouteReplaced;
    if (exec_type == "8") return f::RouteFsmEvent::RouteRejected;
    if (exec_type == "A") return f::RouteFsmEvent::RoutePendingNewAtVenue;
    if (exec_type == "C") return f::RouteFsmEvent::RouteExpired;
    if (exec_type == "E") return f::RouteFsmEvent::RouteReplacePendingAtVenue;
    // ExecType=F is a trade. OrdStatus=2 means nothing is left, so it is the
    // final fill; anything else leaves the route open.
    if (exec_type == "F") {
        return ord_status == "2" ? f::RouteFsmEvent::RouteFilled
                                 : f::RouteFsmEvent::RoutePartiallyFilled;
    }
    return std::nullopt;
}

/// Every route the run has created, keyed on route id.
///
/// **One map, no derived indexes.** "How much have we routed for this order" and
/// "is this route ClOrdID taken" are both answered by scanning, not by side
/// tables kept in step with this one. Scanning is O(n) in a book that holds tens
/// of routes; the alternative — two maps that can disagree after a partial
/// failure — is the bug that would actually cost something. The scan order is
/// the map's, so the answer is deterministic.
using RouteBook = std::map<std::string, std::pair<crossasset::ems::fsm::RouteFsmState,
                                                  crossasset::ems::fsm::RouteFsmContext>>;

/// States in which a route holds no quantity.
///
/// The venue killed the route without filling it, so nothing is committed
/// anywhere and the quantity is routable again. FILLED is deliberately absent —
/// a filled route consumed its quantity, and forgetting that would let an order
/// be over-filled.
bool releases_quantity(crossasset::ems::fsm::RouteFsmState state) {
    namespace f = crossasset::ems::fsm;
    return state == f::RouteFsmState::REJECTED || state == f::RouteFsmState::CANCELED ||
           state == f::RouteFsmState::EXPIRED || state == f::RouteFsmState::SUPERSEDED;
}

/// Quantity currently committed to venues for `order_id`.
///
/// Counts live and filled routes; a route the venue rejected, cancelled, expired
/// or superseded releases its quantity back. Until component 6b those states
/// were unreachable, so this counted every route and an order whose only route
/// was refused could never be re-routed.
std::uint64_t routed_qty(const RouteBook& routes, const std::string& order_id) {
    std::uint64_t total = 0;
    for (const auto& [id, entry] : routes) {
        if (entry.second.orderId == order_id && !releases_quantity(entry.first)) {
            total += entry.second.routeQty;
        }
    }
    return total;
}

/// How many routes exist for `order_id`. Used to name the next one.
std::size_t count_for_order(const RouteBook& routes, const std::string& order_id) {
    std::size_t count = 0;
    for (const auto& [id, entry] : routes) {
        if (entry.second.orderId == order_id) {
            ++count;
        }
    }
    return count;
}

/// Whether any route already carries `cl_ord_id` — FIX requires them unique.
bool has_route_cl_ord_id(const RouteBook& routes, const std::string& cl_ord_id) {
    for (const auto& [id, entry] : routes) {
        if (entry.second.clOrdId == cl_ord_id) {
            return true;
        }
    }
    return false;
}

/// Refuses a route. No route is created, and no identifier is consumed.
std::uint64_t reject_route(const core::JournalEvent& event, std::uint64_t seq,
                           std::string_view code, const std::string& reason,
                           std::vector<core::JournalEvent>& output) {
    std::map<std::string, std::string> fields;
    fields.emplace("clOrdId", field(event, "clOrdId"));
    fields.emplace("code", std::string(code));
    fields.emplace("qty", field(event, "qty"));
    fields.emplace("reason", reason);
    fields.emplace("venueMic", field(event, "venueMic"));
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeRouteRejected), std::move(fields)});
    return seq + 1U;
}

/// States an order can be routed from.
///
/// An allowlist, not a denylist of terminal states. A state added to the schema
/// is then un-routable until someone decides it should be — the safe direction
/// for a list that decides whether quantity goes to a venue.
bool is_routable(crossasset::ems::fsm::OrderFsmState state) {
    namespace f = crossasset::ems::fsm;
    return state == f::OrderFsmState::NEW || state == f::OrderFsmState::REPLACED ||
           state == f::OrderFsmState::PARTIALLY_FILLED;
}

/// Projects an accepted order onto one venue.
///
/// Four refusals, checked in a fixed order with the first one winning, exactly as
/// the validation pipeline short-circuits: unknown order, order not routable,
/// quantity not routable, ClOrdID already taken. Fixed order matters because a
/// request can fail two of them at once and the journal must say the same thing
/// in all three languages.
///
/// **A refused route creates nothing.** There is no FsmTransition on this path,
/// and that asymmetry with OrderNew is the schema's, not a shortcut:
/// order.fsm.yaml models validation failure as a real PENDING_NEW -> REJECTED
/// transition, while route.fsm.yaml has no transition out of PENDING except
/// RouteSent. Emitting one would mean inventing a transition the schema does not
/// define — and fsm-coverage would then count a transition that does not exist.
std::uint64_t on_route_new(const core::JournalEvent& event, std::uint64_t seq,
                           core::DeterministicIds& ids, const OrderBook& orders,
                           const std::map<std::string, std::string>& order_ids, RouteBook& routes,
                           const SessionBook& sessions,
                           std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;
    const std::string cl_ord_id = field(event, "clOrdId");
    const std::string venue_mic = field(event, "venueMic");
    const auto qty = static_cast<std::uint64_t>(parse_i64(field(event, "qty")));

    // The venue gate runs first. A route to a venue that cannot take it is refused
    // before any of the order-side checks, because the answer does not depend on
    // them — and telling a trader "your order is fine, the venue is down" is more
    // useful than a quantity complaint.
    if (!session_is_active(sessions, venue_mic)) {
        const auto session = sessions.find(venue_mic);
        const std::string state = session == sessions.end()
                                      ? std::string("never connected")
                                      : std::string(f::name(session->second.first));
        return reject_route(event, seq, kCodeVenueSessionNotActive, "venue session is " + state,
                            output);
    }

    const auto parent = orders.find(cl_ord_id);
    if (parent == orders.end()) {
        return reject_route(event, seq, kCodeRouteUnknownOrder, "no such order", output);
    }
    // A REJECTED order is in the book and lands here, not on 4001 — it exists, it
    // just cannot take quantity. "your order was rejected" is a more useful thing
    // to tell a trader than "we have never heard of it".
    if (!is_routable(parent->second.first)) {
        return reject_route(event, seq, kCodeRouteOrderNotRoutable,
                            std::string("order is ") + f::name(parent->second.first), output);
    }
    // Unreachable: a routable state is only reached after acceptance, and
    // acceptance is what fills order_ids. Handled rather than asserted, because
    // this runs against a journal a venue wrote.
    const auto assigned = order_ids.find(cl_ord_id);
    if (assigned == order_ids.end()) {
        return reject_route(event, seq, kCodeRouteOrderNotRoutable, "order has no identifier",
                            output);
    }

    const std::string& order_id = assigned->second;
    const std::uint64_t already_routed = routed_qty(routes, order_id);
    const std::uint64_t order_qty = parent->second.second.orderQty;
    if (qty == 0U || already_routed + qty > order_qty) {
        return reject_route(event, seq, kCodeRouteQtyInvalid,
                            "qty " + std::to_string(qty) + " not routable against " +
                                std::to_string(order_qty - already_routed) + " remaining",
                            output);
    }

    // Absent, the route's ClOrdID is derived from the parent's and the count of
    // routes already hung off it: C-A-1, C-A-2. Derived rather than taken from
    // the route id so that the check below can run BEFORE an id is consumed —
    // the same "a refusal costs nothing" property the order path has.
    std::string route_cl_ord_id = field(event, "routeClOrdId");
    if (route_cl_ord_id.empty()) {
        route_cl_ord_id = cl_ord_id + "-" + std::to_string(count_for_order(routes, order_id) + 1U);
    }
    if (has_route_cl_ord_id(routes, route_cl_ord_id)) {
        return reject_route(event, seq, kCodeRouteClOrdIdCollision,
                            "ClOrdID " + route_cl_ord_id + " in use", output);
    }

    const std::string route_id = ids.next_route_id();
    const auto price = non_empty(event, "price");

    f::RouteFsmContext context;
    context.routeId = route_id;
    context.orderId = order_id;
    context.clOrdId = route_cl_ord_id;
    context.venueMic = venue_mic;
    context.instrumentId = parent->second.second.instrumentId;
    context.side = parent->second.second.side;
    context.routeQty = qty;
    if (price.has_value()) {
        context.price = parse_i64(*price);
    }
    context.cumQty = 0;
    context.leavesQty = qty;
    context.traceId = 1;
    context.initialOrderId = order_id;

    // Every route starts in the schema's initial state and is moved out of it by
    // RouteSent, exactly as an order is moved out of PENDING_NEW by a validation
    // outcome. Seeding a route straight into SENT would make the first transition
    // unreachable by any corpus case.
    const auto result =
        f::transition(f::RouteFsmState::PENDING, f::RouteFsmEvent::RouteSent, context, nullptr);
    routes.insert_or_assign(
        route_id, std::make_pair(result.isNoTransition ? f::RouteFsmState::PENDING
                                                       : result.newState,
                                 result.isNoTransition ? context : result.newContext));

    std::map<std::string, std::string> transition;
    transition.emplace("applied", result.isNoTransition ? "false" : "true");
    transition.emplace("event", f::name(f::RouteFsmEvent::RouteSent));
    transition.emplace("from", f::name(f::RouteFsmState::PENDING));
    transition.emplace("fsm", "route");
    transition.emplace("routeId", route_id);
    // Read back from the book rather than taken from the transition result: the
    // journal should report the state the slice is actually holding.
    const auto stored = routes.find(route_id);
    transition.emplace("to", f::name(stored == routes.end() ? f::RouteFsmState::PENDING
                                                            : stored->second.first));
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeFsmTransition), std::move(transition)});

    std::map<std::string, std::string> fields;
    fields.emplace("clOrdId", cl_ord_id);
    fields.emplace("orderId", order_id);
    if (price.has_value()) {
        fields.emplace("price", std::to_string(parse_i64(*price)));
    }
    fields.emplace("qty", std::to_string(qty));
    fields.emplace("routeClOrdId", route_cl_ord_id);
    fields.emplace("routeId", route_id);
    fields.emplace("venueMic", venue_mic);
    output.push_back(
        core::JournalEvent{seq + 2U, std::string(kTypeRouteAccepted), std::move(fields)});

    // 35=D goes out last: the journal reads in the order things happened, and the
    // message is a consequence of the route existing rather than the cause of it.
    std::map<std::string, std::string> fix;
    fix.emplace("clOrdId", route_cl_ord_id);
    fix.emplace("msgType", "D");
    fix.emplace("orderQty", std::to_string(qty));
    if (price.has_value()) {
        fix.emplace("price", std::to_string(parse_i64(*price)));
    }
    fix.emplace("side", std::to_string(static_cast<int>(parent->second.second.side)));
    fix.emplace("symbol", parent->second.second.instrumentId);
    fix.emplace("venueMic", venue_mic);
    output.push_back(core::JournalEvent{seq + 3U, std::string(kTypeFixOut), std::move(fix)});
    return seq + 3U;
}

/// The route carrying `cl_ord_id`, or empty when none does.
///
/// An ExecutionReport names a ClOrdID, because that is what the venue was given.
/// It has never seen our route id.
std::string route_id_for_cl_ord_id(const RouteBook& routes, const std::string& cl_ord_id) {
    for (const auto& [route_id, entry] : routes) {
        if (entry.second.clOrdId == cl_ord_id) {
            return route_id;
        }
    }
    return {};
}

/// The client identifier for one of our order ids.
///
/// A scan rather than a second map. The order book is keyed on ClOrdID and
/// routes carry OrderID, so something has to bridge them; a reverse index would
/// be a second structure to keep in step, and this book holds tens of orders.
/// The map makes the scan order deterministic.
std::string cl_ord_id_for(const std::map<std::string, std::string>& order_ids,
                          const std::string& order_id) {
    for (const auto& [cl_ord_id, assigned] : order_ids) {
        if (assigned == order_id) {
            return cl_ord_id;
        }
    }
    return {};
}

/// Records a route action that reached no route. Never fatal.
std::uint64_t push_route_ignored(std::uint64_t seq, const std::string& route_id,
                                 const std::string& raw_event, const std::string& reason,
                                 std::vector<core::JournalEvent>& output) {
    std::map<std::string, std::string> fields;
    fields.emplace("event", raw_event);
    fields.emplace("reason", reason);
    fields.emplace("routeId", route_id);
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeRouteEventIgnored), std::move(fields)});
    return seq + 1U;
}

/// Applies a venue action to a live route, and cascades what the schema says.
///
/// **The cascade is not written here.** route.fsm.yaml declares emit_event
/// effects — a route reaching WORKING emits ValidationPassed to the order
/// machine, a fill emits PartialFill — and this reads them off the transition
/// result. Hand-writing that mapping would mean three languages each holding an
/// opinion about what the YAML says, which is the failure the generator exists
/// to prevent.
///
/// Ordering is fixed and journalled: the route's own transition first, then each
/// cascaded order transition in the order the schema declares the effects.
std::uint64_t on_route_event(const core::JournalEvent& event, std::uint64_t seq, OrderBook& orders,
                             const std::map<std::string, std::string>& order_ids,
                             RouteBook& routes, std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;
    const std::string route_id = field(event, "routeId");
    const std::string raw = field(event, "event");

    const auto fsm_event = f::RouteFsmEventFromName(raw);
    if (!fsm_event.has_value()) {
        return push_route_ignored(seq, route_id, raw, "unknown FSM event", output);
    }
    const auto found = routes.find(route_id);
    if (found == routes.end()) {
        return push_route_ignored(seq, route_id, raw, "unknown route", output);
    }

    const f::RouteFsmState before = found->second.first;
    const std::string order_id = found->second.second.orderId;

    // The payload structs must outlive the transition call: `transition` takes a
    // `const void*` and reads through it, so a temporary would dangle.
    f::RouteFsmPayloads::RoutePartiallyFilledPayload partial{};
    f::RouteFsmPayloads::RouteFilledPayload filled{};
    f::RouteFsmPayloads::RouteCancelRejectedPayload cancel_rej{};
    f::RouteFsmPayloads::RouteReplaceRejectedPayload replace_rej{};
    f::RouteFsmPayloads::RouteReplacedPayload replaced{};
    f::RouteFsmPayloads::RouteReplaceRequestedPayload replace_req{};
    const void* payload = nullptr;
    switch (*fsm_event) {
    case f::RouteFsmEvent::RoutePartiallyFilled:
        partial.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
        partial.lastPx = parse_i64(field(event, "lastPx"));
        partial.execId = field(event, "execId");
        payload = &partial;
        break;
    case f::RouteFsmEvent::RouteFilled:
        filled.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
        filled.lastPx = parse_i64(field(event, "lastPx"));
        filled.execId = field(event, "execId");
        payload = &filled;
        break;
    case f::RouteFsmEvent::RouteCancelRejected:
        cancel_rej.cxlRejReason = static_cast<std::uint8_t>(parse_i64(field(event, "cxlRejReason")));
        payload = &cancel_rej;
        break;
    case f::RouteFsmEvent::RouteReplaceRejected:
        replace_rej.cxlRejReason =
            static_cast<std::uint8_t>(parse_i64(field(event, "cxlRejReason")));
        payload = &replace_rej;
        break;
    case f::RouteFsmEvent::RouteReplaced:
        replaced.newClOrdId = field(event, "newClOrdId");
        payload = &replaced;
        break;
    case f::RouteFsmEvent::RouteReplaceRequested:
        replace_req.newClOrdId = field(event, "newClOrdId");
        replace_req.newRouteQty =
            static_cast<std::uint64_t>(parse_i64(field(event, "newRouteQty")));
        payload = &replace_req;
        break;
    default:
        break;
    }

    const auto result = f::transition(before, *fsm_event, found->second.second, payload);
    if (!result.isNoTransition) {
        routes.insert_or_assign(route_id, std::make_pair(result.newState, result.newContext));
    }

    std::map<std::string, std::string> fields;
    fields.emplace("applied", result.isNoTransition ? "false" : "true");
    fields.emplace("event", f::name(*fsm_event));
    fields.emplace("from", f::name(before));
    fields.emplace("fsm", "route");
    fields.emplace("routeId", route_id);
    fields.emplace("to", f::name(result.isNoTransition ? before : result.newState));
    output.push_back(
        core::JournalEvent{seq + 1U, std::string(kTypeFsmTransition), std::move(fields)});
    seq += 1U;

    // A declined event cascades nothing. The generated effects are empty on a
    // no-transition precisely so this cannot be got wrong by forgetting to check.
    if (result.isNoTransition) {
        return seq;
    }

    const std::string cl_ord_id = cl_ord_id_for(order_ids, order_id);
    if (cl_ord_id.empty()) {
        return seq;
    }

    for (const auto& effect : result.effects) {
        if (effect.kind != f::RouteFsmEffectKind::EmitEvent) {
            continue;
        }
        const auto order_event = f::OrderFsmEventFromName(std::string(effect.event));
        const auto parent = orders.find(cl_ord_id);
        if (!order_event.has_value() || parent == orders.end()) {
            continue;
        }

        f::OrderFsmPayloads::PartialFillPayload order_partial{};
        f::OrderFsmPayloads::FullFillPayload order_full{};
        const void* order_payload = nullptr;
        if (*order_event == f::OrderFsmEvent::PartialFill) {
            order_partial.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
            order_partial.lastPx = parse_i64(field(event, "lastPx"));
            order_partial.execId = field(event, "execId");
            order_payload = &order_partial;
        } else if (*order_event == f::OrderFsmEvent::FullFill) {
            order_full.lastQty = static_cast<std::uint64_t>(parse_i64(field(event, "lastQty")));
            order_full.lastPx = parse_i64(field(event, "lastPx"));
            order_full.execId = field(event, "execId");
            order_payload = &order_full;
        }

        seq = emit_transition(cl_ord_id, *order_event, order_payload, parent->second.second, seq,
                              orders, output);
    }
    return seq;
}

/// Translates an inbound FIX ExecutionReport into a route event and applies it.
///
/// The whole venue edge in one function: FIX vocabulary on the way in, the
/// slice's own vocabulary on the way out. The report names a route by ClOrdID,
/// because that is what a venue knows — it has never seen our route id.
std::uint64_t on_execution_report(const core::JournalEvent& event, std::uint64_t seq,
                                  OrderBook& orders,
                                  const std::map<std::string, std::string>& order_ids,
                                  RouteBook& routes,
                                  std::vector<core::JournalEvent>& output) {
    namespace f = crossasset::ems::fsm;
    const std::string cl_ord_id = field(event, "clOrdId");
    const std::string exec_type = field(event, "execType");

    const auto fsm_event = from_exec_type(exec_type, field(event, "ordStatus"));
    std::string reason;
    std::string route_id;
    if (!fsm_event.has_value()) {
        reason = "unmapped ExecType";
    } else {
        route_id = route_id_for_cl_ord_id(routes, cl_ord_id);
        if (route_id.empty()) {
            reason = "unknown route ClOrdID";
        }
    }
    if (!reason.empty()) {
        std::map<std::string, std::string> fields;
        fields.emplace("clOrdId", cl_ord_id);
        fields.emplace("execType", exec_type);
        fields.emplace("reason", reason);
        output.push_back(core::JournalEvent{seq + 1U, std::string(kTypeExecutionReportIgnored),
                                            std::move(fields)});
        return seq + 1U;
    }

    // Re-uses the route-event path, so an ExecutionReport and a hand-written
    // RouteEvent cannot drift apart — including the cascade to the parent order.
    core::JournalEvent translated = event;
    translated.type = std::string(kTypeRouteEvent);
    translated.fields.insert_or_assign("event", std::string(f::name(*fsm_event)));
    translated.fields.insert_or_assign("routeId", route_id);
    return on_route_event(translated, seq, orders, order_ids, routes, output);
}

std::uint64_t on_order_new(const core::JournalEvent& event, std::uint64_t seq,
                           const aaa::AaaService& service,
                           const validator::SecurityMaster& securities,
                           core::DeterministicIds& ids, OrderBook& orders,
                           std::map<std::string, std::string>& order_ids,
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
    std::string order_id = ids.next_order_id();
    order_ids.insert_or_assign(cl_ord_id, order_id);
    std::map<std::string, std::string> fields;
    fields.emplace("orderId", std::move(order_id));
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
    RouteBook routes;
    SessionBook sessions;
    // The order id we assigned to each accepted order, by the client's ClOrdID.
    // A runner-level index rather than a field on the order book: the FSM context
    // is the schema's shape and this is ours. A rejected order has no entry, so a
    // route can never be hung off an order id that was never issued.
    std::map<std::string, std::string> order_ids;
    std::uint64_t seq = 0;

    for (const auto& event : input) {
        if (event.type == kTypeInstrumentCreated) {
            output.push_back(on_instrument_created(event, ++seq, securities));
        } else if (event.type == kTypeSessionLogon) {
            output.push_back(on_session_logon(event, ++seq, service));
        } else if (event.type == kTypeOrderNew) {
            seq = on_order_new(event, seq, service, securities, ids, orders, order_ids, output);
        } else if (event.type == kTypeOrderEvent) {
            seq = on_order_event(event, seq, orders, output);
        } else if (event.type == kTypeRouteNew) {
            seq = on_route_new(event, seq, ids, orders, order_ids, routes, sessions, output);
        } else if (event.type == kTypeRouteEvent) {
            seq = on_route_event(event, seq, orders, order_ids, routes, output);
        } else if (event.type == kTypeVenueSession) {
            seq = on_venue_session(event, seq, sessions, output);
        } else if (event.type == kTypeExecutionReport) {
            seq = on_execution_report(event, seq, orders, order_ids, routes, output);
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
