//! State machines generated from `schemas/fsm/*.fsm.yaml`.
//!
//! Nothing here is hand-written except this file and the tests.
//! `tools/codegen/fsm_codegen.py` emits `src/generated/` from the same YAML
//! that produces the Java and C++ machines, and the `fsm-sync` gate step
//! regenerates all three and fails on any diff — so a hand-edit does not
//! survive a push.
//!
//! The generated `apply` matches exhaustively over states with no catch-all.
//! Adding a state to the schema makes this crate fail to *compile* until the
//! generator emits its arm, where Java compiles and takes a default branch at
//! run time. That difference is the point of having the Rust port at all; see
//! `70_concepts/idioms/fsm-state-exhaustiveness.md`.
//!
//! All five machines are re-exported, not only the two the cash-equity slice
//! uses. The generator emits every schema it finds, and a module that compiles
//! but is unreachable is a module nothing checks.
#![forbid(unsafe_code)]

/// The generated machines, one module per schema.
pub mod generated;

pub use generated::multi_leg_fsm::{
    MultiLegFsmContext, MultiLegFsmEvent, MultiLegFsmPayload, MultiLegFsmState,
    MultiLegFsmTransitionResult,
};
pub use generated::order_fsm::{
    OrderFsmContext, OrderFsmEvent, OrderFsmPayload, OrderFsmState, OrderFsmTransitionResult,
};
pub use generated::route_fsm::{
    RouteFsmContext, RouteFsmEvent, RouteFsmPayload, RouteFsmState, RouteFsmTransitionResult,
};
pub use generated::sor_fsm::{
    SorFsmContext, SorFsmEvent, SorFsmPayload, SorFsmState, SorFsmTransitionResult,
};
pub use generated::venue_session_fsm::{
    VenueSessionFsmContext, VenueSessionFsmEvent, VenueSessionFsmPayload, VenueSessionFsmState,
    VenueSessionFsmTransitionResult,
};
