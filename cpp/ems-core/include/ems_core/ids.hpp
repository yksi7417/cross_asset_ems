#pragma once

// Identifier generation for the slice: same seed, same identifiers, in every
// language. The exact string form is a cross-language contract — see
// java/ems-core .../DeterministicIds.java and rust/ems-core/src/ids.rs.

#include <cstdint>
#include <string>

namespace ems::core {

/// Deterministic identifier generator.
///
/// A fixed-width counter per prefix, not a seeded PRNG. Identifiers reach the
/// output journal, which the conformance gate compares byte-for-byte across
/// Java, Rust and C++ — and getting three PRNG implementations to agree is a
/// far stronger thing to ask for than getting three counters to agree.
///
/// Format is `<PREFIX>-%010d`. Past ten digits the value widens rather than
/// wrapping, because a silently reused identifier is worse than a wider string.
///
/// Not thread-safe. The slice binary is single-threaded by design.
class DeterministicIds {
public:
    /// The first identifier of each kind is `seed + 1`.
    ///
    /// The parameter is unsigned, so unlike the Java constructor there is no
    /// negative-seed check to write: the invalid state is unrepresentable.
    /// STUDY: unrepresentable-invalid-state
    explicit DeterministicIds(std::uint64_t seed) noexcept
        : seed_(seed), order_(seed), route_(seed), exec_(seed) {}

    [[nodiscard]] std::uint64_t seed() const noexcept { return seed_; }

    [[nodiscard]] std::string next_order_id() { return format("ORD", ++order_); }
    [[nodiscard]] std::string next_route_id() { return format("RTE", ++route_); }
    [[nodiscard]] std::string next_exec_id() { return format("EXE", ++exec_); }

private:
    static std::string format(const char* prefix, std::uint64_t value);

    std::uint64_t seed_;
    std::uint64_t order_;
    std::uint64_t route_;
    std::uint64_t exec_;
};

}  // namespace ems::core
