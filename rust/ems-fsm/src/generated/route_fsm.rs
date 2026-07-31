// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/routefsm.fsm.yaml
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

/// States of the `Route` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum RouteFsmState {
    /// Route created; outbound 35=D being assembled. Not yet sent.
    Pending,
    /// 35=D dispatched to venue adapter; awaiting venue acknowledgment.
    Sent,
    /// Venue acknowledged with Pending New (ExecType=A) before confirming New.
    PendingNewAtVenue,
    /// Venue confirmed route as working (ExecType=0, OrdStatus=0).
    Working,
    /// 35=G dispatched to venue; awaiting venue replace confirmation.
    PendingReplaceAtVenue,
    /// 35=F dispatched to venue; awaiting cancel confirmation.
    PendingCancelAtVenue,
    /// One or more partial fills received; leaves_qty > 0.
    PartiallyFilled,
    /// Route fully filled.
    Filled,
    /// Venue confirmed cancel.
    Canceled,
    /// Venue rejected the route on submission.
    Rejected,
    /// TIF reached; venue expired the route.
    Expired,
    /// Prior route closed because the venue does not support in-place replace; a new route was issued (RouteSuperseded event). Common in FX/OTC adapters.

    Superseded,
    /// State inconsistency detected between EMS and venue requiring ops triage (RouteAnomaly event). Human intervention required before continuation.

    Anomaly,
}

impl RouteFsmState {
    /// The initial state, per the schema.
    #[must_use]
    pub const fn initial() -> Self {
        Self::Pending
    }

    /// The state name as the schema spells it.
    ///
    /// This reaches the output journal, so it must match Java's enum constant
    /// character for character — the conformance gate compares bytes.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::Sent => "SENT",
            Self::PendingNewAtVenue => "PENDING_NEW_AT_VENUE",
            Self::Working => "WORKING",
            Self::PendingReplaceAtVenue => "PENDING_REPLACE_AT_VENUE",
            Self::PendingCancelAtVenue => "PENDING_CANCEL_AT_VENUE",
            Self::PartiallyFilled => "PARTIALLY_FILLED",
            Self::Filled => "FILLED",
            Self::Canceled => "CANCELED",
            Self::Rejected => "REJECTED",
            Self::Expired => "EXPIRED",
            Self::Superseded => "SUPERSEDED",
            Self::Anomaly => "ANOMALY",
        }
    }

    /// Whether this state accepts no further transitions.
    #[must_use]
    pub const fn is_terminal(self) -> bool {
        match self {
            Self::Filled => true,
            Self::Canceled => true,
            Self::Rejected => true,
            Self::Expired => true,
            Self::Superseded => true,
            Self::Anomaly => true,
            _ => false,
        }
    }
}

/// Events the `Route` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum RouteFsmEvent {
    /// EMS outbound 35=D dispatched to venue adapter.
    RouteSent,
    /// Venue sent 35=8 ExecType=A (Pending New) before confirming New.
    RoutePendingNewAtVenue,
    /// Venue confirmed route as working (35=8 ExecType=0 OrdStatus=0).
    RouteAcknowledged,
    /// Venue rejected route on submission (35=8 ExecType=8).
    RouteRejected,
    /// EMS dispatched 35=G to venue for this route.
    RouteReplaceRequested,
    /// Venue acknowledged replace with ExecType=E.
    RouteReplacePendingAtVenue,
    /// Venue confirmed replace (35=8 ExecType=5).
    RouteReplaced,
    /// Venue rejected replace via 35=9; route returns to prior working state.
    RouteReplaceRejected,
    /// EMS dispatched 35=F to venue for this route.
    RouteCancelRequested,
    /// Venue confirmed cancel (35=8 ExecType=4).
    RouteCanceled,
    /// Venue rejected cancel via 35=9; route returns to prior state.
    RouteCancelRejected,
    /// Partial fill from venue (35=8 ExecType=F OrdStatus=1).
    RoutePartiallyFilled,
    /// Final fill from venue (35=8 ExecType=F OrdStatus=2).
    RouteFilled,
    /// Venue expired the route at TIF boundary (35=8 ExecType=C).
    RouteExpired,
    /// Venue does not support in-place replace; prior route closed and new route issued. Emitted by the venue adapter after cancel+resubmit completes.

    RouteSuperseded,
    /// EMS-side state inconsistency detected (e.g. venue reports unknown ClOrdID, or state cannot be reconciled with internal records). Requires ops triage.

    RouteAnomaly,
}

impl RouteFsmEvent {
    /// The event name as the schema spells it.
    ///
    /// Event names reach the output journal for the same reason state names do —
    /// the `FsmTransition` events the conformance gate compares carry both — so
    /// this must match Java's enum constant character for character.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::RouteSent => "RouteSent",
            Self::RoutePendingNewAtVenue => "RoutePendingNewAtVenue",
            Self::RouteAcknowledged => "RouteAcknowledged",
            Self::RouteRejected => "RouteRejected",
            Self::RouteReplaceRequested => "RouteReplaceRequested",
            Self::RouteReplacePendingAtVenue => "RouteReplacePendingAtVenue",
            Self::RouteReplaced => "RouteReplaced",
            Self::RouteReplaceRejected => "RouteReplaceRejected",
            Self::RouteCancelRequested => "RouteCancelRequested",
            Self::RouteCanceled => "RouteCanceled",
            Self::RouteCancelRejected => "RouteCancelRejected",
            Self::RoutePartiallyFilled => "RoutePartiallyFilled",
            Self::RouteFilled => "RouteFilled",
            Self::RouteExpired => "RouteExpired",
            Self::RouteSuperseded => "RouteSuperseded",
            Self::RouteAnomaly => "RouteAnomaly",
        }
    }

    /// Parses a schema event name. `None` for anything the schema does not define.
    ///
    /// A journal can carry any string; an unrecognised one is data, not a defect,
    /// so the caller decides what to do rather than being handed a panic.
    #[must_use]
    pub fn from_name(name: &str) -> Option<Self> {
        match name {
            "RouteSent" => Some(Self::RouteSent),
            "RoutePendingNewAtVenue" => Some(Self::RoutePendingNewAtVenue),
            "RouteAcknowledged" => Some(Self::RouteAcknowledged),
            "RouteRejected" => Some(Self::RouteRejected),
            "RouteReplaceRequested" => Some(Self::RouteReplaceRequested),
            "RouteReplacePendingAtVenue" => Some(Self::RouteReplacePendingAtVenue),
            "RouteReplaced" => Some(Self::RouteReplaced),
            "RouteReplaceRejected" => Some(Self::RouteReplaceRejected),
            "RouteCancelRequested" => Some(Self::RouteCancelRequested),
            "RouteCanceled" => Some(Self::RouteCanceled),
            "RouteCancelRejected" => Some(Self::RouteCancelRejected),
            "RoutePartiallyFilled" => Some(Self::RoutePartiallyFilled),
            "RouteFilled" => Some(Self::RouteFilled),
            "RouteExpired" => Some(Self::RouteExpired),
            "RouteSuperseded" => Some(Self::RouteSuperseded),
            "RouteAnomaly" => Some(Self::RouteAnomaly),
            _ => None,
        }
    }
}

/// Context carried alongside the state.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteFsmContext {
    /// `route_id` from the schema.
    pub route_id: String,
    /// `order_id` from the schema.
    pub order_id: String,
    /// `cl_ord_id` from the schema.
    pub cl_ord_id: String,
    /// `orig_cl_ord_id` from the schema.
    pub orig_cl_ord_id: Option<String>,
    /// `venue_mic` from the schema.
    pub venue_mic: String,
    /// `instrument_id` from the schema.
    pub instrument_id: String,
    /// `side` from the schema.
    pub side: u8,
    /// `route_qty` from the schema.
    pub route_qty: u64,
    /// `price` from the schema.
    pub price: Option<i64>,
    /// `cum_qty` from the schema.
    pub cum_qty: u64,
    /// `leaves_qty` from the schema.
    pub leaves_qty: u64,
    /// `trace_id` from the schema.
    pub trace_id: u64,
    /// `initial_order_id` from the schema.
    pub initial_order_id: String,
    /// `pre_cancel_status` from the schema.
    pub pre_cancel_status: Option<String>,
}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RouteFsmTransitionResult {
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: RouteFsmState,
    /// The context after the event.
    pub new_context: RouteFsmContext,
    /// What the schema asks the caller to do, in the order it declares them.
    ///
    /// Static data, not an owned list: the values are all literals from the
    /// YAML, so there is nothing to allocate and nothing to free. Empty when no
    /// transition matched.
    pub effects: &'static [RouteFsmEffect],
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}

impl RouteFsmTransitionResult {
    /// No rule matched: state and context are unchanged, and nothing is asked for.
    #[must_use]
    pub fn no_transition(state: RouteFsmState, ctx: &RouteFsmContext) -> Self {
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
            RouteFsmEffect::EmitEvent { target_fsm, event } => Some((*target_fsm, *event)),
            _ => None,
        })
    }
}

/// A side effect a transition asks for, as the schema declares it.
///
/// Every field is `&'static str`: the values come from the YAML, so a
/// transition's effects are compile-time data. `apply` returns
/// `&'static [RouteFsmEffect]` — no allocation, and nothing to keep alive.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RouteFsmEffect {
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


/// Payload carried by [`RouteFsmEvent::RouteReplaceRequested`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplaceRequestedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
    /// `new_route_qty` from the schema.
    pub new_route_qty: u64,
    /// `new_price` from the schema.
    pub new_price: Option<i64>,
}

/// Payload carried by [`RouteFsmEvent::RouteReplaced`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplacedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
}

/// Payload carried by [`RouteFsmEvent::RouteReplaceRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplaceRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`RouteFsmEvent::RouteCancelRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteCancelRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`RouteFsmEvent::RoutePartiallyFilled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RoutePartiallyFilledPayload {
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Payload carried by [`RouteFsmEvent::RouteFilled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteFilledPayload {
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Any event payload this machine accepts.
///
/// A sum type rather than the `const void*` the C++ header takes: `apply`
/// cannot be handed the payload of a different event, and the compiler
/// checks it rather than the programmer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RouteFsmPayload {
    /// Payload for [`RouteFsmEvent::RouteReplaceRequested`].
    RouteReplaceRequested(RouteReplaceRequestedPayload),
    /// Payload for [`RouteFsmEvent::RouteReplaced`].
    RouteReplaced(RouteReplacedPayload),
    /// Payload for [`RouteFsmEvent::RouteReplaceRejected`].
    RouteReplaceRejected(RouteReplaceRejectedPayload),
    /// Payload for [`RouteFsmEvent::RouteCancelRejected`].
    RouteCancelRejected(RouteCancelRejectedPayload),
    /// Payload for [`RouteFsmEvent::RoutePartiallyFilled`].
    RoutePartiallyFilled(RoutePartiallyFilledPayload),
    /// Payload for [`RouteFsmEvent::RouteFilled`].
    RouteFilled(RouteFilledPayload),
}

impl RouteFsmState {
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
        event: RouteFsmEvent,
        ctx: &RouteFsmContext,
        payload: Option<&RouteFsmPayload>,
    ) -> RouteFsmTransitionResult {
        match self {
            Self::Pending => match event {
                RouteFsmEvent::RouteSent => {
                    RouteFsmTransitionResult { new_state: Self::Sent, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteSent" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Sent => match event {
                RouteFsmEvent::RoutePendingNewAtVenue => {
                    RouteFsmTransitionResult { new_state: Self::PendingNewAtVenue, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RoutePendingNewAtVenue" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteAcknowledged => {
                    RouteFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteWorking" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "ValidationPassed" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteRejected => {
                    RouteFsmTransitionResult { new_state: Self::Rejected, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteRejected" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "ValidationFailed" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingNewAtVenue => match event {
                RouteFsmEvent::RouteAcknowledged => {
                    RouteFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteWorking" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "ValidationPassed" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Working => match event {
                RouteFsmEvent::RouteReplaceRequested => {
                    RouteFsmTransitionResult { new_state: Self::PendingReplaceAtVenue, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteReplaceRequested" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteCancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("0".to_owned());
                    RouteFsmTransitionResult { new_state: Self::PendingCancelAtVenue, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteCancelRequested" }], is_no_transition: false }
                }
                RouteFsmEvent::RoutePartiallyFilled => {
                    let Some(RouteFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    RouteFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RoutePartiallyFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "PartialFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteFilled => {
                    let Some(RouteFsmPayload::RouteFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    RouteFsmTransitionResult { new_state: Self::Filled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "FullFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteExpired => {
                    RouteFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteExpired" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "OrderExpired" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteSuperseded => {
                    RouteFsmTransitionResult { new_state: Self::Superseded, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteSuperseded" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteAnomaly => {
                    RouteFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), effects: &[RouteFsmEffect::Notify(&[("channel", "ops-alerts"), ("message", "Route anomaly detected — manual triage required")]), RouteFsmEffect::PublishEventLog { event: "RouteAnomaly" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingReplaceAtVenue => match event {
                RouteFsmEvent::RouteReplacePendingAtVenue => {
                    RouteFsmTransitionResult { new_state: Self::PendingReplaceAtVenue, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteReplacePendingAtVenue" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteReplaced => {
                    RouteFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteReplaced" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "ReplaceAccepted" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteReplaceRejected => {
                    RouteFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteReplaceRejected" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "ReplaceRejected" }], is_no_transition: false }
                }
                RouteFsmEvent::RoutePartiallyFilled => {
                    let Some(RouteFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    RouteFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RoutePartiallyFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "PartialFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteFilled => {
                    let Some(RouteFsmPayload::RouteFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    RouteFsmTransitionResult { new_state: Self::Filled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "FullFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteSuperseded => {
                    RouteFsmTransitionResult { new_state: Self::Superseded, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteSuperseded" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteAnomaly => {
                    RouteFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), effects: &[RouteFsmEffect::Notify(&[("channel", "ops-alerts"), ("message", "Route anomaly in PENDING_REPLACE — manual triage required")]), RouteFsmEffect::PublishEventLog { event: "RouteAnomaly" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingCancelAtVenue => match event {
                RouteFsmEvent::RouteCanceled => {
                    RouteFsmTransitionResult { new_state: Self::Canceled, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteCanceled" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteCancelRejected => {
                    if ctx.pre_cancel_status.as_deref() == Some("0") {
                        return RouteFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteCancelRejected" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "CancelRejected" }], is_no_transition: false };
                    }
                    if ctx.pre_cancel_status.as_deref() == Some("1") {
                        return RouteFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteCancelRejected" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "CancelRejected" }], is_no_transition: false };
                    }
                    RouteFsmTransitionResult::no_transition(self, ctx)
                }
                RouteFsmEvent::RoutePartiallyFilled => {
                    let Some(RouteFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    next.pre_cancel_status = Some("1".to_owned());
                    RouteFsmTransitionResult { new_state: Self::PendingCancelAtVenue, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RoutePartiallyFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "PartialFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteFilled => {
                    let Some(RouteFsmPayload::RouteFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    RouteFsmTransitionResult { new_state: Self::Filled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "FullFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteAnomaly => {
                    RouteFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), effects: &[RouteFsmEffect::Notify(&[("channel", "ops-alerts"), ("message", "Route anomaly in PENDING_CANCEL — manual triage required")]), RouteFsmEffect::PublishEventLog { event: "RouteAnomaly" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PartiallyFilled => match event {
                RouteFsmEvent::RouteCancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("1".to_owned());
                    RouteFsmTransitionResult { new_state: Self::PendingCancelAtVenue, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteCancelRequested" }], is_no_transition: false }
                }
                RouteFsmEvent::RoutePartiallyFilled => {
                    let Some(RouteFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    RouteFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RoutePartiallyFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "PartialFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteFilled => {
                    let Some(RouteFsmPayload::RouteFilled(p)) = payload else {
                        return RouteFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    RouteFsmTransitionResult { new_state: Self::Filled, new_context: next, effects: &[RouteFsmEffect::PublishEventLog { event: "RouteFilled" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "FullFill" }], is_no_transition: false }
                }
                RouteFsmEvent::RouteExpired => {
                    RouteFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), effects: &[RouteFsmEffect::PublishEventLog { event: "RouteExpired" }, RouteFsmEffect::EmitEvent { target_fsm: "OrderFsm", event: "OrderExpired" }], is_no_transition: false }
                }
                _ => RouteFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Filled => RouteFsmTransitionResult::no_transition(self, ctx),
            Self::Canceled => RouteFsmTransitionResult::no_transition(self, ctx),
            Self::Rejected => RouteFsmTransitionResult::no_transition(self, ctx),
            Self::Expired => RouteFsmTransitionResult::no_transition(self, ctx),
            Self::Superseded => RouteFsmTransitionResult::no_transition(self, ctx),
            Self::Anomaly => RouteFsmTransitionResult::no_transition(self, ctx),
        }
    }
}
