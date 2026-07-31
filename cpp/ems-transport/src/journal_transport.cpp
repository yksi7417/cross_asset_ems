#include "ems_transport/journal_transport.hpp"

#include <utility>

namespace ems::transport {

JournalTransport::JournalTransport(std::filesystem::path input, std::filesystem::path output)
    : input_(std::move(input)), output_(std::move(output)) {}

core::Result<std::vector<core::JournalEvent>, core::MalformedJournal> JournalTransport::drain() {
    if (drained_) {
        return std::vector<core::JournalEvent>{};
    }
    drained_ = true;
    return core::read_journal(input_);
}

void JournalTransport::publish(core::JournalEvent event) { pending_.push_back(std::move(event)); }

core::Status<core::MalformedJournal> JournalTransport::flush() {
    return core::write_journal(output_, pending_);
}

}  // namespace ems::transport
