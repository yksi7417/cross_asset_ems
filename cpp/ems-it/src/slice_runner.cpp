#include "ems_it/slice_runner.hpp"

#include <array>
#include <string>
#include <string_view>

namespace ems::it {
namespace {

/// Input event that opens an order.
constexpr std::string_view kTypeOrderNew = "OrderNew";
/// Output event acknowledging one.
constexpr std::string_view kTypeOrderAccepted = "OrderAccepted";
/// Final output event: makes the seed and the input size visible in the journal.
constexpr std::string_view kTypeRunSummary = "RunSummary";

/// Fields copied from `OrderNew` onto `OrderAccepted`.
///
/// An explicit list rather than "copy everything": an unknown field silently
/// reaching the output would be a divergence that only shows up once some other
/// language's map happens to order it differently.
constexpr std::array<std::string_view, 5> kEchoedFields = {"account", "figi", "price", "qty",
                                                           "side"};

}  // namespace

std::vector<core::JournalEvent> run_slice(const std::vector<core::JournalEvent>& input,
                                          core::DeterministicIds& ids) {
    std::vector<core::JournalEvent> output;
    output.reserve(input.size() + 1U);
    std::uint64_t seq = 0;

    for (const auto& event : input) {
        if (event.type == kTypeOrderNew) {
            std::map<std::string, std::string> fields;
            fields.emplace("orderId", ids.next_order_id());
            for (const auto& key : kEchoedFields) {
                const auto it = event.fields.find(std::string(key));
                if (it != event.fields.end()) {
                    fields.emplace(it->first, it->second);
                }
            }
            output.push_back(
                core::JournalEvent{++seq, std::string(kTypeOrderAccepted), std::move(fields)});
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
