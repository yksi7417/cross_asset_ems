//! Live routes and their state, driven by the generated route FSM.
//!
//! A route is the EMS's outbound projection of one order to one venue. As with
//! the order book, the FSM is **not** reimplemented here: `RouteFsmState::apply`
//! is generated from `schemas/fsm/route.fsm.yaml` and this module only decides
//! which event to hand it.
//!
//! Kept in lockstep with `java/ems-it/.../SliceRouteBook.java` and
//! `cpp/ems-it/src/slice_runner.cpp`.

use std::collections::BTreeMap;

use ems_fsm::{RouteFsmContext, RouteFsmEvent, RouteFsmState, RouteFsmTransitionResult};

/// A route, its FSM state, and the context the FSM threads through transitions.
#[derive(Debug, Clone)]
pub struct Entry {
    pub state: RouteFsmState,
    pub context: RouteFsmContext,
}

/// Every route the run has created, keyed on route id.
///
/// **One map, no derived indexes.** "How much have we routed for this order" and
/// "is this route `ClOrdID` taken" are both answered by scanning, not by side
/// tables kept in step with this one. Scanning is O(n) in a book that holds tens
/// of routes; the alternative — two maps that can disagree after a partial
/// failure — is the bug that would actually cost something. The scan order is
/// the `BTreeMap`'s, so the answer is deterministic.
#[derive(Debug, Default)]
pub struct RouteBook {
    routes: BTreeMap<String, Entry>,
}

impl RouteBook {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Opens a route in `Pending` and immediately dispatches it.
    ///
    /// Every route starts in the schema's initial state and is moved out of it by
    /// `RouteSent`, exactly as an order is moved out of `PendingNew` by a
    /// validation outcome. Seeding a route straight into `Sent` would make the
    /// first transition unreachable by any corpus case.
    pub fn open(&mut self, route_id: &str, context: RouteFsmContext) -> RouteFsmTransitionResult {
        let result = RouteFsmState::Pending.apply(RouteFsmEvent::RouteSent, &context, None);
        let entry = if result.is_no_transition {
            Entry {
                state: RouteFsmState::Pending,
                context,
            }
        } else {
            Entry {
                state: result.new_state,
                context: result.new_context.clone(),
            }
        };
        self.routes.insert(route_id.to_owned(), entry);
        result
    }

    /// Quantity already routed for `order_id`.
    ///
    /// Counts every route, including terminal ones. That is right for a filled
    /// route and wrong for a rejected one — quantity on a route the venue refused
    /// is not committed anywhere and ought to be routable again. Nothing in the
    /// slice can reach those states yet, so encoding the distinction now would be
    /// a rule with no test behind it. DEFERRED: T-7
    #[must_use]
    pub fn routed_qty(&self, order_id: &str) -> u64 {
        self.routes
            .values()
            .filter(|entry| entry.context.order_id == order_id)
            .map(|entry| entry.context.route_qty)
            .sum()
    }

    /// How many routes exist for `order_id`. Used to name the next one.
    #[must_use]
    pub fn count_for_order(&self, order_id: &str) -> usize {
        self.routes
            .values()
            .filter(|entry| entry.context.order_id == order_id)
            .count()
    }

    /// Whether any route already carries `cl_ord_id` — FIX requires them unique.
    #[must_use]
    pub fn has_cl_ord_id(&self, cl_ord_id: &str) -> bool {
        self.routes
            .values()
            .any(|entry| entry.context.cl_ord_id == cl_ord_id)
    }

    /// The state of `route_id`, or `None` when no such route exists.
    ///
    /// The runner journals *this*, not the state the transition result predicted.
    /// The two agree today and the compiler is the reason the difference got
    /// thought about at all: `rustc` pointed out the book was storing a state
    /// nobody read, and the answer was not to delete the field but to stop
    /// reporting a state the book might not be holding. A journal that says
    /// `to=SENT` should mean the book has a route in `Sent`.
    #[must_use]
    pub fn state_of(&self, route_id: &str) -> Option<RouteFsmState> {
        self.routes.get(route_id).map(|entry| entry.state)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn context(route_id: &str, order_id: &str, cl_ord_id: &str, qty: u64) -> RouteFsmContext {
        RouteFsmContext {
            route_id: route_id.to_owned(),
            order_id: order_id.to_owned(),
            cl_ord_id: cl_ord_id.to_owned(),
            orig_cl_ord_id: None,
            venue_mic: "XNAS".to_owned(),
            instrument_id: "BBG000B9XRY4".to_owned(),
            side: 1,
            route_qty: qty,
            price: None,
            cum_qty: 0,
            leaves_qty: qty,
            trace_id: 1,
            initial_order_id: order_id.to_owned(),
            pre_cancel_status: None,
        }
    }

    /// A new route is dispatched, not merely created: `RouteSent` fires on open.
    #[test]
    fn opening_a_route_dispatches_it() {
        let mut book = RouteBook::new();
        let result = book.open("RTE-1", context("RTE-1", "ORD-1", "C-A-1", 100));

        assert!(!result.is_no_transition);
        assert_eq!(result.new_state, RouteFsmState::Sent);
        assert_eq!(book.state_of("RTE-1"), Some(RouteFsmState::Sent));
    }

    #[test]
    fn an_unknown_route_has_no_state() {
        assert_eq!(RouteBook::new().state_of("RTE-404"), None);
    }

    /// The quantity question is per order, and two orders do not pool.
    #[test]
    fn routed_quantity_is_summed_per_order() {
        let mut book = RouteBook::new();
        book.open("RTE-1", context("RTE-1", "ORD-1", "C-A-1", 400));
        book.open("RTE-2", context("RTE-2", "ORD-1", "C-A-2", 600));
        book.open("RTE-3", context("RTE-3", "ORD-2", "C-B-1", 200));

        assert_eq!(book.routed_qty("ORD-1"), 1000);
        assert_eq!(book.routed_qty("ORD-2"), 200);
        // An order with no routes is 0, not a missing-key panic — the runner asks
        // this before it knows whether any route exists.
        assert_eq!(book.routed_qty("ORD-404"), 0);
    }

    /// Numbering counts routes on *this* order, which is why the third route in
    /// the book is still the first on its own order.
    #[test]
    fn route_count_is_per_order() {
        let mut book = RouteBook::new();
        book.open("RTE-1", context("RTE-1", "ORD-1", "C-A-1", 400));
        book.open("RTE-2", context("RTE-2", "ORD-1", "C-A-2", 600));
        book.open("RTE-3", context("RTE-3", "ORD-2", "C-B-1", 200));

        assert_eq!(book.count_for_order("ORD-1"), 2);
        assert_eq!(book.count_for_order("ORD-2"), 1);
        assert_eq!(book.count_for_order("ORD-404"), 0);
    }

    /// Collision is checked across every route, not per order — a `ClOrdID` taken
    /// on one order blocks it on another, which is what FIX uniqueness means.
    #[test]
    fn cl_ord_id_collision_spans_orders() {
        let mut book = RouteBook::new();
        book.open("RTE-1", context("RTE-1", "ORD-1", "C-A-1", 400));

        assert!(book.has_cl_ord_id("C-A-1"));
        assert!(!book.has_cl_ord_id("C-A-2"));
        // The parent order's own ClOrdID is not a route ClOrdID and must not
        // collide with one.
        assert!(!book.has_cl_ord_id("C-A"));
    }
}
