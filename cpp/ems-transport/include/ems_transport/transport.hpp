#pragma once

// How the slice receives and emits events, without knowing what carries them.
//
// Two implementations are intended: JournalTransport, which reads and writes
// files and is what the conformance gate runs, and an Aeron-backed one for the
// live path. See docs/decisions/0006-abstract-transport-journal-first.md.

#include <vector>

#include "ems_core/journal.hpp"
#include "ems_core/result.hpp"

namespace ems::transport {

/// The transport seam.
///
/// Single-threaded by design — see
/// docs/decisions/0003-shared-schemas-corpus-harness.md. A concurrent transport
/// is a later, separately-gated concern.
class Transport {
public:
    // STUDY: virtual-dtor-and-rule-of-zero
    Transport() = default;
    Transport(const Transport&) = delete;
    Transport& operator=(const Transport&) = delete;
    Transport(Transport&&) = delete;
    Transport& operator=(Transport&&) = delete;
    virtual ~Transport() = default;

    /// Returns everything available to consume, and consumes it.
    ///
    /// A second call returns an empty vector rather than replaying: draining
    /// twice is a programming error the interface makes visible instead of
    /// quietly duplicating an order.
    [[nodiscard]] virtual core::Result<std::vector<core::JournalEvent>, core::MalformedJournal>
    drain() = 0;

    /// Queues an event for emission.
    ///
    /// Nothing is visible to a reader until flush(). A half-written journal
    /// from a crashed run would look exactly like a legitimately short one, and
    /// the conformance gate cannot tell the difference.
    virtual void publish(core::JournalEvent event) = 0;

    /// Makes every published event visible, as one unit.
    [[nodiscard]] virtual core::Status<core::MalformedJournal> flush() = 0;
};

}  // namespace ems::transport
