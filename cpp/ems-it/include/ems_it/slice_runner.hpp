#pragma once

// The slice, as far as it has been built.

#include <vector>

#include "ems_core/ids.hpp"
#include "ems_core/journal.hpp"

namespace ems::it {

/// Runs the slice over `input`, returning the output journal.
///
/// **Today this covers component 1 only**: the journal codec and deterministic
/// identifiers. An `OrderNew` becomes an `OrderAccepted` carrying a generated
/// order id; everything else passes through with its sequence renumbered; a
/// `RunSummary` closes the journal. There is no validation, no FSM, no routing
/// and no venue — those are later components, and pretending otherwise in the
/// output would make the conformance corpus lie about what is implemented.
///
/// Kept in lockstep with `java/ems-it/.../SliceRunner.java` and
/// `rust/ems-slice/src/runner.rs`.
[[nodiscard]] std::vector<core::JournalEvent> run_slice(
    const std::vector<core::JournalEvent>& input, core::DeterministicIds& ids);

}  // namespace ems::it
