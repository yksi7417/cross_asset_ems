#pragma once

// A Transport backed by two journal files.

#include <filesystem>
#include <vector>

#include "ems_transport/transport.hpp"

namespace ems::transport {

/// Deterministic by construction: no media driver, no network, no clock. This
/// is what makes byte-identical replay across three languages testable at all.
///
/// Published events are buffered and written on flush(). Streaming them as they
/// arrive would be cheaper, and would mean a run that died halfway left a file
/// indistinguishable from a correct short one — which the conformance differ
/// would report as a byte mismatch on a later line, sending the reader looking
/// for a logic bug that is not there.
///
/// Rule of five is spelled out rather than defaulted: the base class deletes
/// copy and move, and inheriting that silently would leave a reader guessing.
class JournalTransport final : public Transport {
public:
    JournalTransport(std::filesystem::path input, std::filesystem::path output);

    [[nodiscard]] core::Result<std::vector<core::JournalEvent>, core::MalformedJournal> drain()
        override;

    void publish(core::JournalEvent event) override;

    [[nodiscard]] core::Status<core::MalformedJournal> flush() override;

private:
    std::filesystem::path input_;
    std::filesystem::path output_;
    std::vector<core::JournalEvent> pending_;
    bool drained_{false};
};

}  // namespace ems::transport
