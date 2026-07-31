// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sorfsm.fsm.yaml
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

/// States of the `Sor` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum SorFsmState {
    /// SOR parent route created; outbound 35=D being assembled. Not yet sent.
    Pending,
    /// 35=D dispatched to SOR adapter; awaiting strategy selection and child dispatch.
    Sent,
    /// First child route returned Pending New; parent echoes before confirming Working.
    PendingNewAtVenue,
    /// At least one child route is working; parent aggregate is active.
    Working,
    /// Replace cascaded to all child routes; awaiting child replace confirmations.
    PendingReplaceAtVenue,
    /// Cancel cascaded to all child routes; awaiting child cancel confirmations.
    PendingCancelAtVenue,
    /// Child fills received; parent aggregate partially filled.
    PartiallyFilled,
    /// All child routes filled; parent route fully filled.
    Filled,
    /// All child routes canceled; parent route canceled.
    Canceled,
    /// SOR strategy or venue rejected the route.
    Rejected,
    /// TIF reached; child routes expired.
    Expired,
    /// Prior SOR parent route closed due to cancel-and-resubmit on a venue that does not support in-place replace.

    Superseded,
    /// State inconsistency between EMS and SOR child routes requiring ops triage.

    Anomaly,
}

impl SorFsmState {
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

/// Events the `Sor` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum SorFsmEvent {
    /// SOR strategy engine selected a plan (wheel bucket, slicer schedule, etc.) and is dispatching child routes. Self-loop on SENT; parent stays SENT until first child acknowledges. Compliance payload: strategy_id, bucket, alternatives, seed, wheel_def_hash — logged via SorStrategySelected.

    SorStrategyDecided,
    /// Reactive replan triggered by on_child_event(). Strategy adjusted the cascade plan mid-flight (e.g. cancelled a stale child, added a new slice). Self-loop on WORKING.

    SorPlanAdjusted,
    /// EMS outbound 35=D dispatched to SOR virtual venue adapter.
    RouteSent,
    /// First child returned ExecType=A; parent echoes Pending New.
    RoutePendingNewAtVenue,
    /// First child confirmed working; parent SOR route is now WORKING.
    RouteAcknowledged,
    /// Strategy selection or first child submission rejected.
    RouteRejected,
    /// EMS dispatched 35=G; cascades to all active child routes.
    RouteReplaceRequested,
    /// Child routes acknowledged replace with ExecType=E.
    RouteReplacePendingAtVenue,
    /// All child routes confirmed replace.
    RouteReplaced,
    /// Child route replace rejected; parent returns to WORKING.
    RouteReplaceRejected,
    /// EMS dispatched 35=F; cascades to all active child routes.
    RouteCancelRequested,
    /// All child routes confirmed canceled.
    RouteCanceled,
    /// Child route cancel rejected; parent returns to prior state.
    RouteCancelRejected,
    /// Child route partial fill aggregated into parent.
    RoutePartiallyFilled,
    /// All child routes filled; parent route is fully filled.
    RouteFilled,
    /// Child routes expired at TIF boundary.
    RouteExpired,
    /// Prior SOR parent closed due to cancel-and-resubmit supersession.
    RouteSuperseded,
    /// State inconsistency detected; ops triage required.
    RouteAnomaly,
}

impl SorFsmEvent {
    /// The event name as the schema spells it.
    ///
    /// Event names reach the output journal for the same reason state names do —
    /// the `FsmTransition` events the conformance gate compares carry both — so
    /// this must match Java's enum constant character for character.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::SorStrategyDecided => "SorStrategyDecided",
            Self::SorPlanAdjusted => "SorPlanAdjusted",
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
            "SorStrategyDecided" => Some(Self::SorStrategyDecided),
            "SorPlanAdjusted" => Some(Self::SorPlanAdjusted),
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
pub struct SorFsmContext {
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
    /// `sor_strategy_id` from the schema.
    pub sor_strategy_id: String,
}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SorFsmTransitionResult {
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: SorFsmState,
    /// The context after the event.
    pub new_context: SorFsmContext,
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}

impl SorFsmTransitionResult {
    /// No rule matched: state and context are unchanged.
    #[must_use]
    pub fn no_transition(state: SorFsmState, ctx: &SorFsmContext) -> Self {
        Self { new_state: state, new_context: ctx.clone(), is_no_transition: true }
    }
}

/// Payload carried by [`SorFsmEvent::RouteReplaceRequested`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplaceRequestedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
    /// `new_route_qty` from the schema.
    pub new_route_qty: u64,
    /// `new_price` from the schema.
    pub new_price: Option<i64>,
}

/// Payload carried by [`SorFsmEvent::RouteReplaced`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplacedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
}

/// Payload carried by [`SorFsmEvent::RouteReplaceRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteReplaceRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`SorFsmEvent::RouteCancelRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RouteCancelRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`SorFsmEvent::RoutePartiallyFilled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct RoutePartiallyFilledPayload {
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Payload carried by [`SorFsmEvent::RouteFilled`].
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
pub enum SorFsmPayload {
    /// Payload for [`SorFsmEvent::RouteReplaceRequested`].
    RouteReplaceRequested(RouteReplaceRequestedPayload),
    /// Payload for [`SorFsmEvent::RouteReplaced`].
    RouteReplaced(RouteReplacedPayload),
    /// Payload for [`SorFsmEvent::RouteReplaceRejected`].
    RouteReplaceRejected(RouteReplaceRejectedPayload),
    /// Payload for [`SorFsmEvent::RouteCancelRejected`].
    RouteCancelRejected(RouteCancelRejectedPayload),
    /// Payload for [`SorFsmEvent::RoutePartiallyFilled`].
    RoutePartiallyFilled(RoutePartiallyFilledPayload),
    /// Payload for [`SorFsmEvent::RouteFilled`].
    RouteFilled(RouteFilledPayload),
}

impl SorFsmState {
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
        event: SorFsmEvent,
        ctx: &SorFsmContext,
        payload: Option<&SorFsmPayload>,
    ) -> SorFsmTransitionResult {
        match self {
            Self::Pending => match event {
                SorFsmEvent::RouteSent => {
                    SorFsmTransitionResult { new_state: Self::Sent, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Sent => match event {
                SorFsmEvent::SorStrategyDecided => {
                    SorFsmTransitionResult { new_state: Self::Sent, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RoutePendingNewAtVenue => {
                    SorFsmTransitionResult { new_state: Self::PendingNewAtVenue, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteAcknowledged => {
                    SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteRejected => {
                    SorFsmTransitionResult { new_state: Self::Rejected, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingNewAtVenue => match event {
                SorFsmEvent::RouteAcknowledged => {
                    SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Working => match event {
                SorFsmEvent::SorPlanAdjusted => {
                    SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteReplaceRequested => {
                    SorFsmTransitionResult { new_state: Self::PendingReplaceAtVenue, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteCancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("0".to_owned());
                    SorFsmTransitionResult { new_state: Self::PendingCancelAtVenue, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RoutePartiallyFilled => {
                    let Some(SorFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return SorFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    SorFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RouteFilled => {
                    let Some(SorFsmPayload::RouteFilled(p)) = payload else {
                        return SorFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    SorFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RouteExpired => {
                    SorFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteSuperseded => {
                    SorFsmTransitionResult { new_state: Self::Superseded, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteAnomaly => {
                    SorFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingReplaceAtVenue => match event {
                SorFsmEvent::RouteReplacePendingAtVenue => {
                    SorFsmTransitionResult { new_state: Self::PendingReplaceAtVenue, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteReplaced => {
                    SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteReplaceRejected => {
                    SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RoutePartiallyFilled => {
                    let Some(SorFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return SorFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    SorFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RouteSuperseded => {
                    SorFsmTransitionResult { new_state: Self::Superseded, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteAnomaly => {
                    SorFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingCancelAtVenue => match event {
                SorFsmEvent::RouteCanceled => {
                    SorFsmTransitionResult { new_state: Self::Canceled, new_context: ctx.clone(), is_no_transition: false }
                }
                SorFsmEvent::RouteCancelRejected => {
                    if ctx.pre_cancel_status.as_deref() == Some("0") {
                        return SorFsmTransitionResult { new_state: Self::Working, new_context: ctx.clone(), is_no_transition: false };
                    }
                    if ctx.pre_cancel_status.as_deref() == Some("1") {
                        return SorFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: ctx.clone(), is_no_transition: false };
                    }
                    SorFsmTransitionResult::no_transition(self, ctx)
                }
                SorFsmEvent::RouteAnomaly => {
                    SorFsmTransitionResult { new_state: Self::Anomaly, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PartiallyFilled => match event {
                SorFsmEvent::RouteCancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("1".to_owned());
                    SorFsmTransitionResult { new_state: Self::PendingCancelAtVenue, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RoutePartiallyFilled => {
                    let Some(SorFsmPayload::RoutePartiallyFilled(p)) = payload else {
                        return SorFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    SorFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RouteFilled => {
                    let Some(SorFsmPayload::RouteFilled(p)) = payload else {
                        return SorFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    SorFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                SorFsmEvent::RouteExpired => {
                    SorFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => SorFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Filled => SorFsmTransitionResult::no_transition(self, ctx),
            Self::Canceled => SorFsmTransitionResult::no_transition(self, ctx),
            Self::Rejected => SorFsmTransitionResult::no_transition(self, ctx),
            Self::Expired => SorFsmTransitionResult::no_transition(self, ctx),
            Self::Superseded => SorFsmTransitionResult::no_transition(self, ctx),
            Self::Anomaly => SorFsmTransitionResult::no_transition(self, ctx),
        }
    }
}
