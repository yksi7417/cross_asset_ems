//! Identifier generation for the slice: same seed, same identifiers, in every language.

/// Deterministic identifier generator.
///
/// A fixed-width counter per prefix, not a seeded PRNG. Identifiers reach the
/// output journal, which the conformance gate compares byte-for-byte across
/// Java, Rust and C++ — and getting three PRNG implementations to agree is a
/// far stronger thing to ask for than getting three counters to agree.
///
/// Format is `<PREFIX>-%010d`. Past ten digits the value widens rather than
/// wrapping, because a silently reused identifier is worse than a wider string.
#[derive(Debug, Clone)]
pub struct DeterministicIds {
    seed: u64,
    order: u64,
    route: u64,
    exec: u64,
}

impl DeterministicIds {
    /// Creates a generator whose first identifier of each kind is `seed + 1`.
    #[must_use]
    pub const fn new(seed: u64) -> Self {
        Self {
            seed,
            order: seed,
            route: seed,
            exec: seed,
        }
    }

    /// The seed this generator was created with.
    #[must_use]
    pub const fn seed(&self) -> u64 {
        self.seed
    }

    /// Next order identifier, e.g. `ORD-0000000001`.
    pub fn next_order_id(&mut self) -> String {
        self.order += 1;
        format_id("ORD", self.order)
    }

    /// Next route identifier, e.g. `RTE-0000000001`.
    pub fn next_route_id(&mut self) -> String {
        self.route += 1;
        format_id("RTE", self.route)
    }

    /// Next execution identifier, e.g. `EXE-0000000001`.
    pub fn next_exec_id(&mut self) -> String {
        self.exec += 1;
        format_id("EXE", self.exec)
    }
}

fn format_id(prefix: &str, value: u64) -> String {
    format!("{prefix}-{value:010}")
}

#[cfg(test)]
mod tests {
    use super::DeterministicIds;

    #[test]
    fn produces_the_agreed_string_form() {
        let mut ids = DeterministicIds::new(0);
        assert_eq!(ids.next_order_id(), "ORD-0000000001");
        assert_eq!(ids.next_route_id(), "RTE-0000000001");
        assert_eq!(ids.next_exec_id(), "EXE-0000000001");
    }

    #[test]
    fn counters_are_independent_per_prefix() {
        let mut ids = DeterministicIds::new(0);
        assert_eq!(ids.next_order_id(), "ORD-0000000001");
        assert_eq!(ids.next_order_id(), "ORD-0000000002");
        assert_eq!(ids.next_route_id(), "RTE-0000000001");
        assert_eq!(ids.next_order_id(), "ORD-0000000003");
    }

    #[test]
    fn same_seed_produces_the_same_sequence() {
        let mut a = DeterministicIds::new(7);
        let mut b = DeterministicIds::new(7);
        for _ in 0..100 {
            assert_eq!(a.next_order_id(), b.next_order_id());
        }
    }

    #[test]
    fn seed_offsets_the_counter() {
        assert_eq!(DeterministicIds::new(41).next_order_id(), "ORD-0000000042");
    }

    #[test]
    fn width_holds_until_the_counter_outgrows_it() {
        let mut ids = DeterministicIds::new(9_999_999_998);
        assert_eq!(ids.next_order_id(), "ORD-9999999999");
        // Past ten digits the value widens rather than wrapping or truncating.
        assert_eq!(ids.next_order_id(), "ORD-10000000000");
    }
}
