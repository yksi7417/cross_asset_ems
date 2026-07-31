// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multilegfsm.fsm.yaml
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

/// States of the `MultiLeg` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum MultiLegFsmState {
    /// Package received and staged; legs defined, awaiting validation.
    Staged,
    /// All legs validated; package ready for routing.
    Ready,
    /// One or more legs are being routed or awaiting fills.
    LegsWorking,
    /// All legs fully filled.
    Filled,
    /// Some legs filled; package complete under LEGS_INDEPENDENT semantics where the failed or canceled leg does not void the filled legs.

    PartiallyFilled,
    /// Package or all remaining legs canceled.
    Canceled,
    /// Package rejected — validator failure, venue rejection, or leg failure under ALL_OR_NONE / SEQUENCED mode (any failure voids the package).

    Rejected,
}

impl MultiLegFsmState {
    /// The initial state, per the schema.
    #[must_use]
    pub const fn initial() -> Self {
        Self::Staged
    }

    /// The state name as the schema spells it.
    ///
    /// This reaches the output journal, so it must match Java's enum constant
    /// character for character — the conformance gate compares bytes.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::Staged => "STAGED",
            Self::Ready => "READY",
            Self::LegsWorking => "LEGS_WORKING",
            Self::Filled => "FILLED",
            Self::PartiallyFilled => "PARTIALLY_FILLED",
            Self::Canceled => "CANCELED",
            Self::Rejected => "REJECTED",
        }
    }

    /// Whether this state accepts no further transitions.
    #[must_use]
    pub const fn is_terminal(self) -> bool {
        match self {
            Self::Filled => true,
            Self::PartiallyFilled => true,
            Self::Canceled => true,
            Self::Rejected => true,
            _ => false,
        }
    }
}

/// Events the `MultiLeg` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum MultiLegFsmEvent {
    /// All legs pass the EMS validator; package promoted to READY.
    LegsValidated,
    /// One or more legs fail validation (EMS-ORD-4401 / EMS-ORD-4402 / EMS-ORD-4403 / EMS-ORD-4404).
    LegsValidationFailed,
    /// First leg has been sent to routing; execution is now in progress.
    FirstLegDispatched,
    /// One leg has been fully filled by its venue.
    LegFilled,
    /// One leg received a partial fill; parent aggregate remains LEGS_WORKING.
    LegPartiallyFilled,
    /// One leg was rejected by its venue.
    LegRejected,
    /// One leg was confirmed canceled by its venue.
    LegCanceled,
    /// Operator or automation requested cancellation of the package.
    CancelRequested,
}

impl MultiLegFsmEvent {
    /// The event name as the schema spells it.
    ///
    /// Event names reach the output journal for the same reason state names do —
    /// the `FsmTransition` events the conformance gate compares carry both — so
    /// this must match Java's enum constant character for character.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::LegsValidated => "LegsValidated",
            Self::LegsValidationFailed => "LegsValidationFailed",
            Self::FirstLegDispatched => "FirstLegDispatched",
            Self::LegFilled => "LegFilled",
            Self::LegPartiallyFilled => "LegPartiallyFilled",
            Self::LegRejected => "LegRejected",
            Self::LegCanceled => "LegCanceled",
            Self::CancelRequested => "CancelRequested",
        }
    }

    /// Parses a schema event name. `None` for anything the schema does not define.
    ///
    /// A journal can carry any string; an unrecognised one is data, not a defect,
    /// so the caller decides what to do rather than being handed a panic.
    #[must_use]
    pub fn from_name(name: &str) -> Option<Self> {
        match name {
            "LegsValidated" => Some(Self::LegsValidated),
            "LegsValidationFailed" => Some(Self::LegsValidationFailed),
            "FirstLegDispatched" => Some(Self::FirstLegDispatched),
            "LegFilled" => Some(Self::LegFilled),
            "LegPartiallyFilled" => Some(Self::LegPartiallyFilled),
            "LegRejected" => Some(Self::LegRejected),
            "LegCanceled" => Some(Self::LegCanceled),
            "CancelRequested" => Some(Self::CancelRequested),
            _ => None,
        }
    }
}

/// Context carried alongside the state.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct MultiLegFsmContext {
    /// `order_id` from the schema.
    pub order_id: String,
    /// `multileg_kind` from the schema.
    pub multileg_kind: String,
    /// `execution_mode` from the schema.
    pub execution_mode: String,
    /// `total_legs` from the schema.
    pub total_legs: u32,
    /// `legs_filled` from the schema.
    pub legs_filled: u32,
    /// `legs_rejected` from the schema.
    pub legs_rejected: u32,
    /// `legs_canceled` from the schema.
    pub legs_canceled: u32,
    /// `package_id` from the schema.
    pub package_id: Option<String>,
}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MultiLegFsmTransitionResult {
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: MultiLegFsmState,
    /// The context after the event.
    pub new_context: MultiLegFsmContext,
    /// What the schema asks the caller to do, in the order it declares them.
    ///
    /// Static data, not an owned list: the values are all literals from the
    /// YAML, so there is nothing to allocate and nothing to free. Empty when no
    /// transition matched.
    pub effects: &'static [MultiLegFsmEffect],
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}

impl MultiLegFsmTransitionResult {
    /// No rule matched: state and context are unchanged, and nothing is asked for.
    #[must_use]
    pub fn no_transition(state: MultiLegFsmState, ctx: &MultiLegFsmContext) -> Self {
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
            MultiLegFsmEffect::EmitEvent { target_fsm, event } => Some((*target_fsm, *event)),
            _ => None,
        })
    }
}

/// A side effect a transition asks for, as the schema declares it.
///
/// Every field is `&'static str`: the values come from the YAML, so a
/// transition's effects are compile-time data. `apply` returns
/// `&'static [MultiLegFsmEffect]` — no allocation, and nothing to keep alive.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MultiLegFsmEffect {
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


/// Payload carried by [`MultiLegFsmEvent::LegFilled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct LegFilledPayload {
    /// `leg_id` from the schema.
    pub leg_id: String,
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
}

/// Payload carried by [`MultiLegFsmEvent::LegPartiallyFilled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct LegPartiallyFilledPayload {
    /// `leg_id` from the schema.
    pub leg_id: String,
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
}

/// Payload carried by [`MultiLegFsmEvent::LegRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct LegRejectedPayload {
    /// `leg_id` from the schema.
    pub leg_id: String,
}

/// Payload carried by [`MultiLegFsmEvent::LegCanceled`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct LegCanceledPayload {
    /// `leg_id` from the schema.
    pub leg_id: String,
}

/// Any event payload this machine accepts.
///
/// A sum type rather than the `const void*` the C++ header takes: `apply`
/// cannot be handed the payload of a different event, and the compiler
/// checks it rather than the programmer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MultiLegFsmPayload {
    /// Payload for [`MultiLegFsmEvent::LegFilled`].
    LegFilled(LegFilledPayload),
    /// Payload for [`MultiLegFsmEvent::LegPartiallyFilled`].
    LegPartiallyFilled(LegPartiallyFilledPayload),
    /// Payload for [`MultiLegFsmEvent::LegRejected`].
    LegRejected(LegRejectedPayload),
    /// Payload for [`MultiLegFsmEvent::LegCanceled`].
    LegCanceled(LegCanceledPayload),
}

impl MultiLegFsmState {
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
        event: MultiLegFsmEvent,
        ctx: &MultiLegFsmContext,
        _payload: Option<&MultiLegFsmPayload>,
    ) -> MultiLegFsmTransitionResult {
        match self {
            Self::Staged => match event {
                MultiLegFsmEvent::LegsValidated => {
                    MultiLegFsmTransitionResult { new_state: Self::Ready, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishEventLog { event: "MultiLegValidated" }], is_no_transition: false }
                }
                MultiLegFsmEvent::LegsValidationFailed => {
                    MultiLegFsmTransitionResult { new_state: Self::Rejected, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "j")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegRejected" }], is_no_transition: false }
                }
                _ => MultiLegFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Ready => match event {
                MultiLegFsmEvent::FirstLegDispatched => {
                    MultiLegFsmTransitionResult { new_state: Self::LegsWorking, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishEventLog { event: "MultiLegExecutionStarted" }], is_no_transition: false }
                }
                MultiLegFsmEvent::CancelRequested => {
                    MultiLegFsmTransitionResult { new_state: Self::Canceled, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "4"), ("ord_status", "4")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegCanceled" }], is_no_transition: false }
                }
                _ => MultiLegFsmTransitionResult::no_transition(self, ctx),
            },
            Self::LegsWorking => match event {
                MultiLegFsmEvent::LegPartiallyFilled => {
                    MultiLegFsmTransitionResult { new_state: Self::LegsWorking, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishEventLog { event: "LegPartiallyFilled" }], is_no_transition: false }
                }
                MultiLegFsmEvent::LegFilled => {
                    if ((ctx.legs_filled + 1) == ctx.total_legs) && (ctx.legs_rejected == 0) && (ctx.legs_canceled == 0) {
                        let mut next = ctx.clone();
                        next.legs_filled = ctx.legs_filled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::Filled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "F"), ("ord_status", "2")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegFilled" }], is_no_transition: false };
                    }
                    if (ctx.execution_mode.as_str() == "LEGS_INDEPENDENT") && ((((ctx.legs_filled + 1) + ctx.legs_rejected) + ctx.legs_canceled) == ctx.total_legs) && ((ctx.legs_rejected > 0) || (ctx.legs_canceled > 0)) {
                        let mut next = ctx.clone();
                        next.legs_filled = ctx.legs_filled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "F"), ("ord_status", "1")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegPartiallyFilled" }], is_no_transition: false };
                    }
                    if (((ctx.legs_filled + 1) + ctx.legs_rejected) + ctx.legs_canceled) < ctx.total_legs {
                        let mut next = ctx.clone();
                        next.legs_filled = ctx.legs_filled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::LegsWorking, new_context: next, effects: &[MultiLegFsmEffect::PublishEventLog { event: "LegFilled" }], is_no_transition: false };
                    }
                    MultiLegFsmTransitionResult::no_transition(self, ctx)
                }
                MultiLegFsmEvent::LegRejected => {
                    if ctx.execution_mode.as_str() == "ALL_OR_NONE" {
                        let mut next = ctx.clone();
                        next.legs_rejected = ctx.legs_rejected + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::Rejected, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "8"), ("ord_status", "8")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegRejected" }, MultiLegFsmEffect::EmitEvent { target_fsm: "RouteFsm", event: "RouteCancelRequested" }], is_no_transition: false };
                    }
                    if ctx.execution_mode.as_str() == "SEQUENCED" {
                        let mut next = ctx.clone();
                        next.legs_rejected = ctx.legs_rejected + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::Rejected, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "8"), ("ord_status", "8")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegRejected" }], is_no_transition: false };
                    }
                    if (ctx.execution_mode.as_str() == "LEGS_INDEPENDENT") && (ctx.legs_filled > 0) && ((((ctx.legs_filled + ctx.legs_rejected) + 1) + ctx.legs_canceled) == ctx.total_legs) {
                        let mut next = ctx.clone();
                        next.legs_rejected = ctx.legs_rejected + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "F"), ("ord_status", "1")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegPartiallyFilled" }], is_no_transition: false };
                    }
                    if (ctx.execution_mode.as_str() == "LEGS_INDEPENDENT") && (ctx.legs_filled == 0) && (((ctx.legs_rejected + 1) + ctx.legs_canceled) == ctx.total_legs) {
                        let mut next = ctx.clone();
                        next.legs_rejected = ctx.legs_rejected + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::Canceled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "4"), ("ord_status", "4")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegCanceled" }], is_no_transition: false };
                    }
                    if (ctx.execution_mode.as_str() == "LEGS_INDEPENDENT") && ((((ctx.legs_filled + ctx.legs_rejected) + 1) + ctx.legs_canceled) < ctx.total_legs) {
                        let mut next = ctx.clone();
                        next.legs_rejected = ctx.legs_rejected + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::LegsWorking, new_context: next, effects: &[MultiLegFsmEffect::PublishEventLog { event: "LegRejected" }], is_no_transition: false };
                    }
                    MultiLegFsmTransitionResult::no_transition(self, ctx)
                }
                MultiLegFsmEvent::LegCanceled => {
                    if (ctx.legs_filled > 0) && ((((ctx.legs_filled + ctx.legs_rejected) + ctx.legs_canceled) + 1) == ctx.total_legs) {
                        let mut next = ctx.clone();
                        next.legs_canceled = ctx.legs_canceled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "F"), ("ord_status", "1")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegPartiallyFilled" }], is_no_transition: false };
                    }
                    if (ctx.legs_filled == 0) && (((ctx.legs_rejected + ctx.legs_canceled) + 1) == ctx.total_legs) {
                        let mut next = ctx.clone();
                        next.legs_canceled = ctx.legs_canceled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::Canceled, new_context: next, effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "4"), ("ord_status", "4")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegCanceled" }], is_no_transition: false };
                    }
                    if (((ctx.legs_filled + ctx.legs_rejected) + ctx.legs_canceled) + 1) < ctx.total_legs {
                        let mut next = ctx.clone();
                        next.legs_canceled = ctx.legs_canceled + 1;
                        return MultiLegFsmTransitionResult { new_state: Self::LegsWorking, new_context: next, effects: &[MultiLegFsmEffect::PublishEventLog { event: "LegCanceled" }], is_no_transition: false };
                    }
                    MultiLegFsmTransitionResult::no_transition(self, ctx)
                }
                MultiLegFsmEvent::CancelRequested => {
                    MultiLegFsmTransitionResult { new_state: Self::Canceled, new_context: ctx.clone(), effects: &[MultiLegFsmEffect::PublishFixMessage(&[("msg_type", "8"), ("exec_type", "4"), ("ord_status", "4")]), MultiLegFsmEffect::PublishEventLog { event: "MultiLegCanceled" }, MultiLegFsmEffect::EmitEvent { target_fsm: "RouteFsm", event: "RouteCancelRequested" }], is_no_transition: false }
                }
                _ => MultiLegFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Filled => MultiLegFsmTransitionResult::no_transition(self, ctx),
            Self::PartiallyFilled => MultiLegFsmTransitionResult::no_transition(self, ctx),
            Self::Canceled => MultiLegFsmTransitionResult::no_transition(self, ctx),
            Self::Rejected => MultiLegFsmTransitionResult::no_transition(self, ctx),
        }
    }
}
