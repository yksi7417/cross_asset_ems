//! One FIX session per venue, driven by the generated venue-session FSM.
//!
//! Keyed on the venue MIC. As with the order and route books the FSM is not
//! reimplemented here: `VenueSessionFsmState::apply` is generated from
//! `schemas/fsm/venue_session.fsm.yaml`.
//!
//! Kept in lockstep with `java/ems-it/.../SliceVenueSessions.java` and
//! `cpp/ems-it/src/slice_runner.cpp`.

use std::collections::BTreeMap;

use ems_fsm::{
    VenueSessionFsmContext, VenueSessionFsmEvent, VenueSessionFsmState,
    VenueSessionFsmTransitionResult,
};

/// A session, its FSM state, and the context the FSM threads through.
#[derive(Debug, Clone)]
struct Entry {
    state: VenueSessionFsmState,
    context: VenueSessionFsmContext,
}

/// Every venue session the run has touched.
///
/// **A venue with no session is not a venue with a broken session.** [`is_active`]
/// answers false for both, which is what the routing gate wants; [`state_of`]
/// keeps them apart, because "we never connected" and "we connected and got
/// logged out" are different things to tell an operator.
///
/// [`is_active`]: VenueSessions::is_active
/// [`state_of`]: VenueSessions::state_of
#[derive(Debug, Default)]
pub struct VenueSessions {
    sessions: BTreeMap<String, Entry>,
}

impl VenueSessions {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Applies `event` to the session for `venue_mic`, opening one if needed.
    ///
    /// A venue we have never heard of starts in the schema's initial state —
    /// `Disconnected` — rather than being rejected. That is what makes
    /// `ConnectRequested` the first transition a corpus case can exercise,
    /// exactly as `ValidationPassed` is for an order.
    pub fn apply(
        &mut self,
        venue_mic: &str,
        event: VenueSessionFsmEvent,
    ) -> VenueSessionFsmTransitionResult {
        let entry = self
            .sessions
            .entry(venue_mic.to_owned())
            .or_insert_with(|| Entry {
                state: VenueSessionFsmState::Disconnected,
                context: context_for(venue_mic),
            });

        let result = entry.state.apply(event, &entry.context, None);
        if !result.is_no_transition {
            entry.state = result.new_state;
            entry.context = result.new_context.clone();
        }
        result
    }

    /// The state of the session for `venue_mic`, or `None` when never seen.
    #[must_use]
    pub fn state_of(&self, venue_mic: &str) -> Option<VenueSessionFsmState> {
        self.sessions.get(venue_mic).map(|entry| entry.state)
    }

    /// Whether `venue_mic` can currently take an order.
    ///
    /// `Active` only. A session in `LogonSent` has a TCP connection and no agreed
    /// sequence numbers; one in `ResendInProgress` is mid-gap-fill. Sending a new
    /// order into either is how you end up with an order the venue has and the
    /// EMS cannot account for.
    #[must_use]
    pub fn is_active(&self, venue_mic: &str) -> bool {
        self.state_of(venue_mic) == Some(VenueSessionFsmState::Active)
    }
}

fn context_for(venue_mic: &str) -> VenueSessionFsmContext {
    VenueSessionFsmContext {
        session_id: format!("SES-{venue_mic}"),
        next_expected_seq_in: 1,
        next_send_seq_out: 1,
        heartbeat_interval_secs: 30,
        test_request_outstanding: false,
        resend_window_low: 0,
        resend_window_high: 0,
        venue_mic: venue_mic.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A venue nobody has connected to is not active, and does not panic.
    #[test]
    fn an_unknown_venue_is_not_active() {
        let sessions = VenueSessions::new();
        assert!(!sessions.is_active("XNAS"));
        assert_eq!(sessions.state_of("XNAS"), None);
    }

    /// Never-connected and connected-then-lost are different facts.
    #[test]
    fn never_connected_is_distinguishable_from_disconnected() {
        let mut sessions = VenueSessions::new();
        sessions.apply("XNAS", VenueSessionFsmEvent::ConnectRequested);
        sessions.apply("XNAS", VenueSessionFsmEvent::TcpFailed);

        assert_eq!(
            sessions.state_of("XNAS"),
            Some(VenueSessionFsmState::Disconnected)
        );
        assert_eq!(sessions.state_of("XLON"), None);
        // The routing gate cannot tell them apart, and should not have to.
        assert!(!sessions.is_active("XNAS"));
        assert!(!sessions.is_active("XLON"));
    }

    /// Only `Active` takes orders. A half-open session is the dangerous case:
    /// there is a socket, so it *looks* usable.
    #[test]
    fn only_an_active_session_takes_orders() {
        let mut sessions = VenueSessions::new();
        sessions.apply("XNAS", VenueSessionFsmEvent::ConnectRequested);
        sessions.apply("XNAS", VenueSessionFsmEvent::TcpConnected);
        assert_eq!(
            sessions.state_of("XNAS"),
            Some(VenueSessionFsmState::LogonSent)
        );
        assert!(!sessions.is_active("XNAS"));

        sessions.apply("XNAS", VenueSessionFsmEvent::LogonAcknowledged);
        assert!(sessions.is_active("XNAS"));

        // Mid-gap-fill is not order-ready either.
        sessions.apply("XNAS", VenueSessionFsmEvent::GapDetected);
        assert!(!sessions.is_active("XNAS"));
    }

    /// Two venues do not share a session.
    #[test]
    fn sessions_are_per_venue() {
        let mut sessions = VenueSessions::new();
        for venue in ["XNAS", "XLON"] {
            sessions.apply(venue, VenueSessionFsmEvent::ConnectRequested);
        }
        sessions.apply("XNAS", VenueSessionFsmEvent::TcpConnected);
        sessions.apply("XNAS", VenueSessionFsmEvent::LogonAcknowledged);

        assert!(sessions.is_active("XNAS"));
        assert!(!sessions.is_active("XLON"));
    }
}
