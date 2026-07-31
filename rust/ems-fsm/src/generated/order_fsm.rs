// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/orderfsm.fsm.yaml
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

/// States of the `Order` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum OrderFsmState {
    /// Order received; validation in progress.
    PendingNew,
    /// Validation passed; order is open and working.
    New,
    /// OrderCancelReplaceRequest (35=G) dispatched; awaiting venue response.
    PendingReplace,
    /// Most-recent replace confirmed by venue. Order is still working.
    Replaced,
    /// OrderCancelRequest (35=F) dispatched; awaiting venue response.
    PendingCancel,
    /// One or more partial fills received; leaves_qty > 0.
    PartiallyFilled,
    /// Order fully filled (cum_qty == order_qty). Terminal for clean path; can still receive trade busts/corrections.
    Filled,
    /// Cancel confirmed by venue.
    Canceled,
    /// Order rejected by validator or venue pre-fill.
    Rejected,
    /// TIF reached; order expired by venue.
    Expired,
    /// Day order done at day end.
    DoneForDay,
    /// Post-fill price/qty correction applied (ExecType=G).
    TradeCorrected,
    /// Post-fill bust — fill treated as never occurred (ExecType=H).
    TradeCanceled,
}

impl OrderFsmState {
    /// The initial state, per the schema.
    #[must_use]
    pub const fn initial() -> Self {
        Self::PendingNew
    }

    /// The state name as the schema spells it.
    ///
    /// This reaches the output journal, so it must match Java's enum constant
    /// character for character — the conformance gate compares bytes.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::PendingNew => "PENDING_NEW",
            Self::New => "NEW",
            Self::PendingReplace => "PENDING_REPLACE",
            Self::Replaced => "REPLACED",
            Self::PendingCancel => "PENDING_CANCEL",
            Self::PartiallyFilled => "PARTIALLY_FILLED",
            Self::Filled => "FILLED",
            Self::Canceled => "CANCELED",
            Self::Rejected => "REJECTED",
            Self::Expired => "EXPIRED",
            Self::DoneForDay => "DONE_FOR_DAY",
            Self::TradeCorrected => "TRADE_CORRECTED",
            Self::TradeCanceled => "TRADE_CANCELED",
        }
    }

    /// Whether this state accepts no further transitions.
    #[must_use]
    pub const fn is_terminal(self) -> bool {
        match self {
            Self::Canceled => true,
            Self::Rejected => true,
            Self::Expired => true,
            Self::DoneForDay => true,
            Self::TradeCorrected => true,
            Self::TradeCanceled => true,
            _ => false,
        }
    }
}

/// Events the `Order` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum OrderFsmEvent {
    /// Internal validator accepted the order; move to NEW.
    ValidationPassed,
    /// Internal validator rejected the order.
    ValidationFailed,
    /// OrderCancelReplaceRequest received from client.
    ReplaceRequested,
    /// Venue confirmed replace (35=8 ExecType=5).
    ReplaceAccepted,
    /// Venue rejected replace via 35=9 OrderCancelReject; order stays in prior state.
    ReplaceRejected,
    /// OrderCancelRequest received from client or automation.
    CancelRequested,
    /// Venue confirmed cancel (35=8 ExecType=4).
    CancelAccepted,
    /// Venue rejected cancel via 35=9; order stays in prior state.
    CancelRejected,
    /// Partial execution report from venue (OrdStatus=1, ExecType=F).
    PartialFill,
    /// Final fill — order fully executed (OrdStatus=2, ExecType=F).
    FullFill,
    /// Post-fill price/qty correction received (ExecType=G).
    TradeCorrect,
    /// Post-fill bust received (ExecType=H); fill treated as never occurred.
    TradeCancelBust,
    /// Venue expired the order at TIF boundary (ExecType=C).
    OrderExpired,
    /// Day order closed at end of day (ExecType=3).
    DoneForDay,
}

/// Context carried alongside the state.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct OrderFsmContext {
    /// `order_id` from the schema.
    pub order_id: String,
    /// `cl_ord_id` from the schema.
    pub cl_ord_id: String,
    /// `orig_cl_ord_id` from the schema.
    pub orig_cl_ord_id: Option<String>,
    /// `instrument_id` from the schema.
    pub instrument_id: String,
    /// `side` from the schema.
    pub side: u8,
    /// `order_qty` from the schema.
    pub order_qty: u64,
    /// `price` from the schema.
    pub price: Option<i64>,
    /// `cum_qty` from the schema.
    pub cum_qty: u64,
    /// `leaves_qty` from the schema.
    pub leaves_qty: u64,
    /// `account` from the schema.
    pub account: String,
    /// `tif` from the schema.
    pub tif: u8,
    /// `initial_cl_ord_id` from the schema.
    pub initial_cl_ord_id: String,
    /// `chain_id` from the schema.
    pub chain_id: String,
    /// `order_version` from the schema.
    pub order_version: u32,
    /// `pre_cancel_status` from the schema.
    pub pre_cancel_status: Option<String>,
    /// `pre_replace_status` from the schema.
    pub pre_replace_status: Option<String>,
}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OrderFsmTransitionResult {
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: OrderFsmState,
    /// The context after the event.
    pub new_context: OrderFsmContext,
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}

impl OrderFsmTransitionResult {
    /// No rule matched: state and context are unchanged.
    #[must_use]
    pub fn no_transition(state: OrderFsmState, ctx: &OrderFsmContext) -> Self {
        Self { new_state: state, new_context: ctx.clone(), is_no_transition: true }
    }
}

/// Payload carried by [`OrderFsmEvent::ReplaceRequested`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ReplaceRequestedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
    /// `new_order_qty` from the schema.
    pub new_order_qty: u64,
    /// `new_price` from the schema.
    pub new_price: Option<i64>,
}

/// Payload carried by [`OrderFsmEvent::ReplaceAccepted`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ReplaceAcceptedPayload {
    /// `new_cl_ord_id` from the schema.
    pub new_cl_ord_id: String,
}

/// Payload carried by [`OrderFsmEvent::ReplaceRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ReplaceRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`OrderFsmEvent::CancelRejected`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct CancelRejectedPayload {
    /// `cxl_rej_reason` from the schema.
    pub cxl_rej_reason: u8,
}

/// Payload carried by [`OrderFsmEvent::PartialFill`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct PartialFillPayload {
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Payload carried by [`OrderFsmEvent::FullFill`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct FullFillPayload {
    /// `last_qty` from the schema.
    pub last_qty: u64,
    /// `last_px` from the schema.
    pub last_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Payload carried by [`OrderFsmEvent::TradeCorrect`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct TradeCorrectPayload {
    /// `corrected_qty` from the schema.
    pub corrected_qty: u64,
    /// `corrected_px` from the schema.
    pub corrected_px: i64,
    /// `exec_id` from the schema.
    pub exec_id: String,
}

/// Payload carried by [`OrderFsmEvent::TradeCancelBust`].
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct TradeCancelBustPayload {
    /// `busted_exec_id` from the schema.
    pub busted_exec_id: String,
}

/// Any event payload this machine accepts.
///
/// A sum type rather than the `const void*` the C++ header takes: `apply`
/// cannot be handed the payload of a different event, and the compiler
/// checks it rather than the programmer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OrderFsmPayload {
    /// Payload for [`OrderFsmEvent::ReplaceRequested`].
    ReplaceRequested(ReplaceRequestedPayload),
    /// Payload for [`OrderFsmEvent::ReplaceAccepted`].
    ReplaceAccepted(ReplaceAcceptedPayload),
    /// Payload for [`OrderFsmEvent::ReplaceRejected`].
    ReplaceRejected(ReplaceRejectedPayload),
    /// Payload for [`OrderFsmEvent::CancelRejected`].
    CancelRejected(CancelRejectedPayload),
    /// Payload for [`OrderFsmEvent::PartialFill`].
    PartialFill(PartialFillPayload),
    /// Payload for [`OrderFsmEvent::FullFill`].
    FullFill(FullFillPayload),
    /// Payload for [`OrderFsmEvent::TradeCorrect`].
    TradeCorrect(TradeCorrectPayload),
    /// Payload for [`OrderFsmEvent::TradeCancelBust`].
    TradeCancelBust(TradeCancelBustPayload),
}

impl OrderFsmState {
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
        event: OrderFsmEvent,
        ctx: &OrderFsmContext,
        payload: Option<&OrderFsmPayload>,
    ) -> OrderFsmTransitionResult {
        match self {
            Self::PendingNew => match event {
                OrderFsmEvent::ValidationPassed => {
                    OrderFsmTransitionResult { new_state: Self::New, new_context: ctx.clone(), is_no_transition: false }
                }
                OrderFsmEvent::ValidationFailed => {
                    OrderFsmTransitionResult { new_state: Self::Rejected, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::New => match event {
                OrderFsmEvent::ReplaceRequested => {
                    let mut next = ctx.clone();
                    next.pre_replace_status = Some("0".to_owned());
                    next.order_version = ctx.order_version + 1;
                    OrderFsmTransitionResult { new_state: Self::PendingReplace, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::CancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("0".to_owned());
                    OrderFsmTransitionResult { new_state: Self::PendingCancel, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::PartialFill => {
                    let Some(OrderFsmPayload::PartialFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::FullFill => {
                    let Some(OrderFsmPayload::FullFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    OrderFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::OrderExpired => {
                    OrderFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), is_no_transition: false }
                }
                OrderFsmEvent::DoneForDay => {
                    OrderFsmTransitionResult { new_state: Self::DoneForDay, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingReplace => match event {
                OrderFsmEvent::ReplaceAccepted => {
                    let mut next = ctx.clone();
                    next.pre_replace_status = None;
                    OrderFsmTransitionResult { new_state: Self::Replaced, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::ReplaceRejected => {
                    if ctx.pre_replace_status.as_deref() == Some("0") {
                        return OrderFsmTransitionResult { new_state: Self::New, new_context: ctx.clone(), is_no_transition: false };
                    }
                    if ctx.pre_replace_status.as_deref() == Some("5") {
                        return OrderFsmTransitionResult { new_state: Self::Replaced, new_context: ctx.clone(), is_no_transition: false };
                    }
                    OrderFsmTransitionResult::no_transition(self, ctx)
                }
                OrderFsmEvent::PartialFill => {
                    let Some(OrderFsmPayload::PartialFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::FullFill => {
                    let Some(OrderFsmPayload::FullFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    OrderFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Replaced => match event {
                OrderFsmEvent::ReplaceRequested => {
                    let mut next = ctx.clone();
                    next.pre_replace_status = Some("5".to_owned());
                    next.order_version = ctx.order_version + 1;
                    OrderFsmTransitionResult { new_state: Self::PendingReplace, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::CancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("5".to_owned());
                    OrderFsmTransitionResult { new_state: Self::PendingCancel, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::PartialFill => {
                    let Some(OrderFsmPayload::PartialFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::FullFill => {
                    let Some(OrderFsmPayload::FullFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    OrderFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::OrderExpired => {
                    OrderFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PendingCancel => match event {
                OrderFsmEvent::CancelAccepted => {
                    OrderFsmTransitionResult { new_state: Self::Canceled, new_context: ctx.clone(), is_no_transition: false }
                }
                OrderFsmEvent::CancelRejected => {
                    if ctx.pre_cancel_status.as_deref() == Some("0") {
                        return OrderFsmTransitionResult { new_state: Self::New, new_context: ctx.clone(), is_no_transition: false };
                    }
                    if ctx.pre_cancel_status.as_deref() == Some("5") {
                        return OrderFsmTransitionResult { new_state: Self::Replaced, new_context: ctx.clone(), is_no_transition: false };
                    }
                    if ctx.pre_cancel_status.as_deref() == Some("1") {
                        return OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: ctx.clone(), is_no_transition: false };
                    }
                    OrderFsmTransitionResult::no_transition(self, ctx)
                }
                OrderFsmEvent::PartialFill => {
                    let Some(OrderFsmPayload::PartialFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::FullFill => {
                    let Some(OrderFsmPayload::FullFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    OrderFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::PartiallyFilled => match event {
                OrderFsmEvent::CancelRequested => {
                    let mut next = ctx.clone();
                    next.pre_cancel_status = Some("1".to_owned());
                    OrderFsmTransitionResult { new_state: Self::PendingCancel, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::PartialFill => {
                    let Some(OrderFsmPayload::PartialFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = ctx.leaves_qty - p.last_qty;
                    OrderFsmTransitionResult { new_state: Self::PartiallyFilled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::FullFill => {
                    let Some(OrderFsmPayload::FullFill(p)) = payload else {
                        return OrderFsmTransitionResult::no_transition(self, ctx);
                    };
                    let mut next = ctx.clone();
                    next.cum_qty = ctx.cum_qty + p.last_qty;
                    next.leaves_qty = 0;
                    OrderFsmTransitionResult { new_state: Self::Filled, new_context: next, is_no_transition: false }
                }
                OrderFsmEvent::OrderExpired => {
                    OrderFsmTransitionResult { new_state: Self::Expired, new_context: ctx.clone(), is_no_transition: false }
                }
                OrderFsmEvent::DoneForDay => {
                    OrderFsmTransitionResult { new_state: Self::DoneForDay, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Filled => match event {
                OrderFsmEvent::TradeCorrect => {
                    OrderFsmTransitionResult { new_state: Self::TradeCorrected, new_context: ctx.clone(), is_no_transition: false }
                }
                OrderFsmEvent::TradeCancelBust => {
                    OrderFsmTransitionResult { new_state: Self::TradeCanceled, new_context: ctx.clone(), is_no_transition: false }
                }
                _ => OrderFsmTransitionResult::no_transition(self, ctx),
            },
            Self::Canceled => OrderFsmTransitionResult::no_transition(self, ctx),
            Self::Rejected => OrderFsmTransitionResult::no_transition(self, ctx),
            Self::Expired => OrderFsmTransitionResult::no_transition(self, ctx),
            Self::DoneForDay => OrderFsmTransitionResult::no_transition(self, ctx),
            Self::TradeCorrected => OrderFsmTransitionResult::no_transition(self, ctx),
            Self::TradeCanceled => OrderFsmTransitionResult::no_transition(self, ctx),
        }
    }
}
