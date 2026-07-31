// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesessionfsm.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py --rust-only
//
// The match in `apply` is exhaustive over states with no catch-all. Adding a
// state to the schema makes this fail to COMPILE until its arm is generated —
// where Java compiles and takes a default branch at run time. That difference
// is the point of the Rust port; see
// 70_concepts/idioms/fsm-state-exhaustiveness.md.
// Not formatted by hand, so not formatted by rustfmt either.
//
// `cargo fmt` would reformat this file and the fsm-sync gate step would then
// see a diff against what the generator emits — the two checks would deadlock.
// Emitting rustfmt-formatted output instead would make the byte comparison
// depend on the rustfmt version, which is not pinned. The same reasoning
// Spotless already applies on the Java side via targetExclude("**/generated/**").
#![cfg_attr(rustfmt, rustfmt::skip)]
#![allow(
    clippy::doc_markdown,
    clippy::match_like_matches_macro,
    clippy::match_same_arms,
    clippy::too_many_lines
)]

/// States of the `VenueSession` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum VenueSessionFsmState {
    /// No live TCP connection; session ready to reconnect on request.
    Disconnected,
    /// TCP connection attempt in progress; awaiting connect or failure.
    Connecting,
    /// TCP established; outbound 35=A Logon sent; awaiting venue response.
    LogonSent,
    /// Session established; exchanging heartbeats and application messages.
    Active,
    /// Heartbeat overdue; 35=1 TestRequest sent; awaiting any inbound message.
    TestRequestSent,
    /// Gap detected; outbound 35=2 ResendRequest sent; awaiting gap fill.
    ResendInProgress,
    /// Inbound 35=4 SequenceReset being processed (GapFill or Reset mode).
    SequenceResetting,
    /// Graceful logout (35=5) initiated; awaiting venue 35=5 echo.
    LogoutInProgress,
}

impl VenueSessionFsmState {
    /// The initial state, per the schema.
    #[must_use]
    pub const fn initial() -> Self {
        Self::Disconnected
    }

    /// The state name as the schema spells it.
    ///
    /// This reaches the output journal, so it must match Java's enum constant
    /// character for character — the conformance gate compares bytes.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::Disconnected => "DISCONNECTED",
            Self::Connecting => "CONNECTING",
            Self::LogonSent => "LOGON_SENT",
            Self::Active => "ACTIVE",
            Self::TestRequestSent => "TEST_REQUEST_SENT",
            Self::ResendInProgress => "RESEND_IN_PROGRESS",
            Self::SequenceResetting => "SEQUENCE_RESETTING",
            Self::LogoutInProgress => "LOGOUT_IN_PROGRESS",
        }
    }

    /// Whether this state accepts no further transitions.
    #[must_use]
    pub const fn is_terminal(self) -> bool {
        false
    }
}

/// Events the `VenueSession` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum VenueSessionFsmEvent {
    /// Operator or reconnect-scheduler requests a new connection attempt.
    ConnectRequested,
    /// TCP handshake completed successfully.
    TcpConnected,
    /// TCP connection attempt failed or timed out.
    TcpFailed,
    /// Venue responded with 35=A Logon; session is now active.
    LogonAcknowledged,
    /// Venue responded with 35=5 Logout or 35=3 Reject during logon.
    LogonRejected,
    /// Inbound 35=0 Heartbeat or any application message — resets timer.
    HeartbeatReceived,
    /// Heartbeat timer expired without receiving any inbound message.
    HeartbeatOverdue,
    /// Any inbound message received after sending a 35=1 TestRequest.
    TestRequestResponse,
    /// No response to 35=1 TestRequest within 2× HeartBtInt; session stale.
    TestRequestTimeout,
    /// Inbound sequence gap detected; EMS must send 35=2 ResendRequest.
    GapDetected,
    /// All requested messages received (or gap-filled via 35=4 GapFill=Y).
    ResendComplete,
    /// Venue sent 35=2 ResendRequest; EMS must replay the requested range.
    InboundResendRequest,
    /// Venue sent 35=4 SequenceReset (NewSeqNo in GapFill or Reset mode).
    SequenceResetReceived,
    /// Operator or session manager initiated graceful logout.
    LogoutRequested,
    /// Venue sent 35=5 Logout; EMS must echo back and then disconnect.
    LogoutReceived,
    /// EMS echoed the venue-initiated 35=5; session can now close.
    LogoutEchoed,
    /// TCP connection dropped unexpectedly from any live state.
    UnexpectedDisconnect,
}

impl VenueSessionFsmEvent {
    /// The event name as the schema spells it.
    ///
    /// Event names reach the output journal for the same reason state names do —
    /// the `FsmTransition` events the conformance gate compares carry both — so
    /// this must match Java's enum constant character for character.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::ConnectRequested => "ConnectRequested",
            Self::TcpConnected => "TcpConnected",
            Self::TcpFailed => "TcpFailed",
            Self::LogonAcknowledged => "LogonAcknowledged",
            Self::LogonRejected => "LogonRejected",
            Self::HeartbeatReceived => "HeartbeatReceived",
            Self::HeartbeatOverdue => "HeartbeatOverdue",
            Self::TestRequestResponse => "TestRequestResponse",
            Self::TestRequestTimeout => "TestRequestTimeout",
            Self::GapDetected => "GapDetected",
            Self::ResendComplete => "ResendComplete",
            Self::InboundResendRequest => "InboundResendRequest",
            Self::SequenceResetReceived => "SequenceResetReceived",
            Self::LogoutRequested => "LogoutRequested",
            Self::LogoutReceived => "LogoutReceived",
            Self::LogoutEchoed => "LogoutEchoed",
            Self::UnexpectedDisconnect => "UnexpectedDisconnect",
        }
    }

    /// Parses a schema event name. `None` for anything the schema does not define.
    ///
    /// A journal can carry any string; an unrecognised one is data, not a defect,
    /// so the caller decides what to do rather than being handed a panic.
    #[must_use]
    pub fn from_name(name: &str) -> Option<Self> {
        match name {
            "ConnectRequested" => Some(Self::ConnectRequested),
            "TcpConnected" => Some(Self::TcpConnected),
            "TcpFailed" => Some(Self::TcpFailed),
            "LogonAcknowledged" => Some(Self::LogonAcknowledged),
            "LogonRejected" => Some(Self::LogonRejected),
            "HeartbeatReceived" => Some(Self::HeartbeatReceived),
            "HeartbeatOverdue" => Some(Self::HeartbeatOverdue),
            "TestRequestResponse" => Some(Self::TestRequestResponse),
            "TestRequestTimeout" => Some(Self::TestRequestTimeout),
            "GapDetected" => Some(Self::GapDetected),
            "ResendComplete" => Some(Self::ResendComplete),
            "InboundResendRequest" => Some(Self::InboundResendRequest),
            "SequenceResetReceived" => Some(Self::SequenceResetReceived),
            "LogoutRequested" => Some(Self::LogoutRequested),
            "LogoutReceived" => Some(Self::LogoutReceived),
            "LogoutEchoed" => Some(Self::LogoutEchoed),
            "UnexpectedDisconnect" => Some(Self::UnexpectedDisconnect),
            _ => None,
        }
    }
}

/// Context carried alongside the state.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct VenueSessionFsmContext {
    /// `session_id` from the schema.
    pub session_id: String,
    /// `next_expected_seq_in` from the schema.
    pub next_expected_seq_in: u64,
    /// `next_send_seq_out` from the schema.
    pub next_send_seq_out: u64,
    /// `heartbeat_interval_secs` from the schema.
    pub heartbeat_interval_secs: u32,
    /// `test_request_outstanding` from the schema.
    pub test_request_outstanding: bool,
    /// `resend_window_low` from the schema.
    pub resend_window_low: u64,
    /// `resend_window_high` from the schema.
    pub resend_window_high: u64,
    /// `venue_mic` from the schema.
    pub venue_mic: String,
}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VenueSessionFsmTransitionResult {
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: VenueSessionFsmState,
    /// The context after the event.
    pub new_context: VenueSessionFsmContext,
    /// What the schema asks the caller to do, in the order it declares them.
    ///
    /// Static data, not an owned list: the values are all literals from the
    /// YAML, so there is nothing to allocate and nothing to free. Empty when no
    /// transition matched.
    pub effects: &'static [VenueSessionFsmEffect],
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}

impl VenueSessionFsmTransitionResult {
    /// No rule matched: state and context are unchanged, and nothing is asked for.
    #[must_use]
    pub fn no_transition(state: VenueSessionFsmState, ctx: &VenueSessionFsmContext) -> Self {
        Self {
            new_state: state,
            new_context: ctx.clone(),
            effects: &[],
            is_no_transition: true,
        }
    }

    /// The events this transition cascades to another machine, in schema order.
    ///
    /// The common reason to look at effects at all: a route reaching `WORKING`
    /// tells the order machine so. Returning `(target, event)` pairs rather than
    /// the effects themselves keeps the caller from matching on variants it does
    /// not handle.
    pub fn emitted_events(&self) -> impl Iterator<Item = (&'static str, &'static str)> + '_ {
        self.effects.iter().filter_map(|effect| match effect {
            VenueSessionFsmEffect::EmitEvent { target_fsm, event } => Some((*target_fsm, *event)),
            _ => None,
        })
    }
}

/// A side effect a transition asks for, as the schema declares it.
///
/// Every field is `&'static str`: the values come from the YAML, so a
/// transition's effects are compile-time data. `apply` returns
/// `&'static [VenueSessionFsmEffect]` — no allocation, and nothing to keep alive.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VenueSessionFsmEffect {
    /// Cascade an event to another FSM instance.
    EmitEvent {
        /// The machine the event is for, as the schema names it.
        target_fsm: &'static str,
        /// The event to apply there.
        event: &'static str,
    },
    /// Append an event-log audit record.
    PublishEventLog {
        /// The log event name.
        event: &'static str,
    },
    /// Emit an outbound FIX message.
    PublishFixMessage(&'static [(&'static str, &'static str)]),
    /// Schedule a timer.
    ScheduleTimer(&'static [(&'static str, &'static str)]),
    /// Cancel a pending timer.
    CancelTimer(&'static [(&'static str, &'static str)]),
    /// Notify subscribers.
    Notify(&'static [(&'static str, &'static str)]),
    /// Stamp identity chaining trace fields.
    ChainIdentityStamp(&'static [(&'static str, &'static str)]),
}


/// This machine has no event payloads. The type exists so `apply` has a
/// uniform signature across every generated machine.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum VenueSessionFsmPayload {}

impl VenueSessionFsmState {
    /// Applies `event`, returning the new state and context.
    ///
    /// The match over states is exhaustive with no catch-all: adding a state
    /// to the schema makes this fail to compile until its arm is generated.
    ///
    /// `payload` carries the event's fields where the schema declares any.
    /// A transition whose guard or update reads a payload field cannot fire
    /// without one, so a missing payload is a no-transition rather than a
    /// panic — malformed input is data, not a defect.
    #[must_use]
    #[allow(clippy::too_many_lines, clippy::match_same_arms)]
    // STUDY: fsm-state-exhaustiveness
    pub fn apply(
        self,
        event: VenueSessionFsmEvent,
        ctx: &VenueSessionFsmContext,
        _payload: Option<&VenueSessionFsmPayload>,
    ) -> VenueSessionFsmTransitionResult {
        match self {
            Self::Disconnected => match event {
                VenueSessionFsmEvent::ConnectRequested => {
                    VenueSessionFsmTransitionResult { new_state: Self::Connecting, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "initiate_tcp"), ("session_id", "{{ context.session_id }}")])], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Connecting => match event {
                VenueSessionFsmEvent::TcpConnected => {
                    VenueSessionFsmTransitionResult { new_state: Self::LogonSent, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_logon"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "logon_timer"), ("duration_secs", "30")])], is_no_transition: false }
                }
                VenueSessionFsmEvent::TcpFailed => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "TcpConnectionFailed" }, VenueSessionFsmEffect::ScheduleTimer(&[("name", "reconnect_backoff"), ("duration_secs", "5")])], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::LogonSent => match event {
                VenueSessionFsmEvent::LogonAcknowledged => {
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::CancelTimer(&[("name", "logon_timer")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "heartbeat_rx_timer"), ("duration_secs", "{{ context.heartbeat_interval_secs }}")]), VenueSessionFsmEffect::PublishEventLog { event: "SessionActive" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::LogonRejected => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::CancelTimer(&[("name", "logon_timer")]), VenueSessionFsmEffect::Notify(&[("signal", "close_socket"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::PublishEventLog { event: "LogonRejected" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Active => match event {
                VenueSessionFsmEvent::HeartbeatReceived => {
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::CancelTimer(&[("name", "heartbeat_rx_timer")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "heartbeat_rx_timer"), ("duration_secs", "{{ context.heartbeat_interval_secs }}")])], is_no_transition: false }
                }
                VenueSessionFsmEvent::HeartbeatOverdue => {
                    let mut next = ctx.clone();
                    next.test_request_outstanding = true;
                    VenueSessionFsmTransitionResult { new_state: Self::TestRequestSent, new_context: next, effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_test_request"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "test_request_timer"), ("duration_secs", "{{ context.heartbeat_interval_secs }}")])], is_no_transition: false }
                }
                VenueSessionFsmEvent::GapDetected => {
                    VenueSessionFsmTransitionResult { new_state: Self::ResendInProgress, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_resend_request"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::PublishEventLog { event: "GapDetected" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::InboundResendRequest => {
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_resend_messages"), ("session_id", "{{ context.session_id }}")])], is_no_transition: false }
                }
                VenueSessionFsmEvent::SequenceResetReceived => {
                    VenueSessionFsmTransitionResult { new_state: Self::SequenceResetting, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "SequenceResetReceived" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::LogoutRequested => {
                    VenueSessionFsmTransitionResult { new_state: Self::LogoutInProgress, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_logout"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "logout_timer"), ("duration_secs", "10")]), VenueSessionFsmEffect::PublishEventLog { event: "LogoutInitiated" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::LogoutReceived => {
                    VenueSessionFsmTransitionResult { new_state: Self::LogoutInProgress, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "send_logout"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "logout_timer"), ("duration_secs", "5")]), VenueSessionFsmEffect::PublishEventLog { event: "LogoutEchoSent" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::TestRequestSent => match event {
                VenueSessionFsmEvent::TestRequestResponse => {
                    let mut next = ctx.clone();
                    next.test_request_outstanding = false;
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: next, effects: &[VenueSessionFsmEffect::CancelTimer(&[("name", "test_request_timer")]), VenueSessionFsmEffect::ScheduleTimer(&[("name", "heartbeat_rx_timer"), ("duration_secs", "{{ context.heartbeat_interval_secs }}")])], is_no_transition: false }
                }
                VenueSessionFsmEvent::TestRequestTimeout => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::Notify(&[("signal", "close_socket"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::PublishEventLog { event: "SessionStale" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::ResendInProgress => match event {
                VenueSessionFsmEvent::ResendComplete => {
                    let mut next = ctx.clone();
                    next.resend_window_low = 0;
                    next.resend_window_high = 0;
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: next, effects: &[VenueSessionFsmEffect::PublishEventLog { event: "ResendComplete" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::SequenceResetReceived => {
                    VenueSessionFsmTransitionResult { new_state: Self::SequenceResetting, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "SequenceResetReceived" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::SequenceResetting => match event {
                VenueSessionFsmEvent::ResendComplete => {
                    let mut next = ctx.clone();
                    next.resend_window_low = 0;
                    next.resend_window_high = 0;
                    VenueSessionFsmTransitionResult { new_state: Self::Active, new_context: next, effects: &[VenueSessionFsmEffect::PublishEventLog { event: "SequenceResetApplied" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
            Self::LogoutInProgress => match event {
                VenueSessionFsmEvent::LogoutEchoed => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::CancelTimer(&[("name", "logout_timer")]), VenueSessionFsmEffect::Notify(&[("signal", "close_socket"), ("session_id", "{{ context.session_id }}")]), VenueSessionFsmEffect::PublishEventLog { event: "SessionClosed" }], is_no_transition: false }
                }
                VenueSessionFsmEvent::UnexpectedDisconnect => {
                    VenueSessionFsmTransitionResult { new_state: Self::Disconnected, new_context: ctx.clone(), effects: &[VenueSessionFsmEffect::PublishEventLog { event: "UnexpectedDisconnect" }], is_no_transition: false }
                }
                _ => VenueSessionFsmTransitionResult::no_transition(self, ctx),
            },
        }
    }
}
