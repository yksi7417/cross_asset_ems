//! The layered validation pipeline.
//!
//! A port of `io.crossasset.ems.validator.LayeredValidatorPipeline`. Layers run
//! in a fixed order and the first failure short-circuits, so the reject a
//! caller sees names the *outermost* thing that was wrong — which is the one
//! worth telling a trader about. An order against an unknown session and an
//! unknown instrument is a session problem; saying "unknown instrument" would
//! send them to symbology onboarding for a login failure.
//!
//! Layers 5–8 of the Java pipeline (`ASSET_CLASS`, `LIMITS`, `MARKET`, `ROUTE`) are
//! stubs there and absent here. When they land they land in both.
#![forbid(unsafe_code)]

use std::collections::BTreeMap;

use ems_aaa::{AaaService, AuthorizationResult};

/// Which layer produced a decision.
///
/// Two rejections can share a code and differ in the layer that produced them
/// once later layers land, so the layer is carried explicitly rather than
/// inferred from the code.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ValidationLayer {
    /// Layer 1: the session must exist.
    Session,
    /// Layer 2: identity resolution. A pass-through today, as in Java.
    Identity,
    /// Layer 3: the instrument must be known and active.
    Reference,
    /// Layer 4: the three-layer tag AND-gate.
    Permission,
}

impl ValidationLayer {
    /// The name as it reaches the journal. Matches the Java enum constant.
    #[must_use]
    pub const fn name(self) -> &'static str {
        match self {
            Self::Session => "SESSION",
            Self::Identity => "IDENTITY",
            Self::Reference => "REFERENCE",
            Self::Permission => "PERMISSION",
        }
    }
}

/// Catalog code: FIGI not present in the security master.
pub const CODE_UNKNOWN_FIGI: &str = "EMS-REF-2001";

/// Catalog code: instrument present but not active.
pub const CODE_INACTIVE_INSTRUMENT: &str = "EMS-REF-2002";

/// What the pipeline is asked to validate.
///
/// `tag` and `figi` are `Option` because "not supplied" is a real state the
/// journal expresses by omitting the field — and it means "skip that layer",
/// not "validate against nothing".
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ValidationRequest {
    /// Caller-supplied identifier, echoed on the result.
    pub request_id: String,
    /// The session the order claims.
    pub session_id: Option<u64>,
    /// Permission tag the action requires. `None` skips the PERMISSION layer.
    pub tag: Option<String>,
    /// Instrument the order references. `None` skips the REFERENCE layer.
    pub figi: Option<String>,
}

/// The outcome of a validation pass.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ValidationResult {
    /// Every layer passed.
    Pass {
        /// Echo of the request id.
        request_id: String,
    },
    /// A layer failed. Everything here reaches the journal.
    Reject {
        /// Echo of the request id.
        request_id: String,
        /// Catalog code.
        code: String,
        /// Catalog category: `SES`, `REF` or `PRM`.
        category: String,
        /// The layer that refused.
        layer: ValidationLayer,
        /// Human-readable explanation.
        message: String,
        /// Who can resolve it.
        admin_hint: Option<String>,
    },
}

/// Whether an instrument is known, and whether it is tradable.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InstrumentStatus {
    /// Tradable.
    Active,
    /// Known but not tradable; the string is the lifecycle status name.
    Inactive(&'static str),
}

/// The security master, as the REFERENCE layer consumes it.
///
/// Carries only what that layer consults: the FIGI and whether it is active.
/// The Java `InstrumentCore` has twenty-two fields; modelling all of them here
/// would imply the slice validates against them, and it does not.
#[derive(Debug, Default)]
pub struct SecurityMaster {
    instruments: BTreeMap<String, InstrumentStatus>,
}

impl SecurityMaster {
    /// Creates an empty security master.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Adds or replaces an instrument.
    pub fn add(&mut self, figi: &str, status: InstrumentStatus) {
        self.instruments.insert(figi.to_owned(), status);
    }

    /// Looks an instrument up.
    #[must_use]
    pub fn lookup(&self, figi: &str) -> Option<InstrumentStatus> {
        self.instruments.get(figi).copied()
    }
}

/// Runs the layers in order, short-circuiting on the first failure.
///
/// `aaa` supplies the SESSION and PERMISSION layers; `securities` supplies
/// REFERENCE. IDENTITY is a pass-through, as in Java — session logon already
/// resolved the identity, and there is no separate catalog code for
/// identity-not-found.
#[must_use]
pub fn validate(
    request: &ValidationRequest,
    aaa: &AaaService,
    securities: &SecurityMaster,
) -> ValidationResult {
    // Layer 1 — SESSION
    let Some(session) = request.session_id.and_then(|id| aaa.session(id)) else {
        let shown = request
            .session_id
            .map_or_else(|| "-1".to_owned(), |id| id.to_string());
        return ValidationResult::Reject {
            request_id: request.request_id.clone(),
            code: ems_aaa::CODE_SESSION_NOT_FOUND.to_owned(),
            category: "SES".to_owned(),
            layer: ValidationLayer::Session,
            message: format!("Session {shown} not found or has expired."),
            admin_hint: Some("Talk to session admin.".to_owned()),
        };
    };

    // Layer 2 — IDENTITY: pass-through placeholder, matching Java.

    // Layer 3 — REFERENCE
    if let Some(figi) = &request.figi {
        match securities.lookup(figi) {
            None => {
                return ValidationResult::Reject {
                    request_id: request.request_id.clone(),
                    code: CODE_UNKNOWN_FIGI.to_owned(),
                    category: "REF".to_owned(),
                    layer: ValidationLayer::Reference,
                    message: format!("FIGI {figi} not present in security master."),
                    admin_hint: Some("Verify symbology onboarding for this instrument.".to_owned()),
                };
            }
            Some(InstrumentStatus::Inactive(status)) => {
                return ValidationResult::Reject {
                    request_id: request.request_id.clone(),
                    code: CODE_INACTIVE_INSTRUMENT.to_owned(),
                    category: "REF".to_owned(),
                    layer: ValidationLayer::Reference,
                    message: format!("Instrument {figi} is not active (status: {status})."),
                    admin_hint: Some("Verify symbology onboarding for this instrument.".to_owned()),
                };
            }
            Some(InstrumentStatus::Active) => {}
        }
    }

    // Layer 4 — PERMISSION
    if let Some(tag) = &request.tag {
        if let AuthorizationResult::Deny {
            code,
            message,
            admin_hint,
            ..
        } = aaa.authorize(session, tag)
        {
            return ValidationResult::Reject {
                request_id: request.request_id.clone(),
                code,
                category: "PRM".to_owned(),
                layer: ValidationLayer::Permission,
                message,
                admin_hint: Some(format!("Talk to {admin_hint}.")),
            };
        }
    }

    ValidationResult::Pass {
        request_id: request.request_id.clone(),
    }
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::{
        validate, InstrumentStatus, SecurityMaster, ValidationLayer, ValidationRequest,
        ValidationResult,
    };
    use ems_aaa::{AaaService, Identity};
    use std::collections::BTreeSet;

    fn tags(values: &[&str]) -> BTreeSet<String> {
        values.iter().map(|t| (*t).to_owned()).collect()
    }

    fn aaa_with(user_tags: &[&str]) -> AaaService {
        let mut service = AaaService::new();
        let granted = tags(user_tags);
        service.register_session(
            7,
            Identity {
                firm: "FIRM1".to_owned(),
                desk: "DESK1".to_owned(),
                user: "trader1".to_owned(),
                tags: granted.clone(),
            },
            &granted,
            &granted,
        );
        service
    }

    fn securities() -> SecurityMaster {
        let mut master = SecurityMaster::new();
        master.add("BBG000B9XRY4", InstrumentStatus::Active);
        master.add("BBG000SUSPEND", InstrumentStatus::Inactive("SUSPENDED"));
        master
    }

    fn request(
        session_id: Option<u64>,
        tag: Option<&str>,
        figi: Option<&str>,
    ) -> ValidationRequest {
        ValidationRequest {
            request_id: "C-1".to_owned(),
            session_id,
            tag: tag.map(ToOwned::to_owned),
            figi: figi.map(ToOwned::to_owned),
        }
    }

    #[test]
    fn everything_valid_passes() {
        let result = validate(
            &request(Some(7), Some("order-entry"), Some("BBG000B9XRY4")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        assert!(
            matches!(result, ValidationResult::Pass { .. }),
            "{result:?}"
        );
    }

    #[test]
    fn unknown_session_fails_at_the_session_layer() {
        let result = validate(
            &request(Some(99), Some("order-entry"), Some("BBG000B9XRY4")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject {
                code,
                layer,
                message,
                ..
            } => {
                assert_eq!(code, "EMS-SES-1002");
                assert_eq!(layer, ValidationLayer::Session);
                assert_eq!(message, "Session 99 not found or has expired.");
            }
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn session_is_checked_before_reference() {
        // Both the session and the instrument are bad. SESSION is layer 1, so it
        // wins — telling the trader "unknown instrument" would send them to
        // symbology onboarding for a login failure.
        let result = validate(
            &request(Some(99), Some("order-entry"), Some("BBG000NOTREAL")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject { layer, .. } => assert_eq!(layer, ValidationLayer::Session),
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn unknown_figi_fails_at_the_reference_layer() {
        let result = validate(
            &request(Some(7), Some("order-entry"), Some("BBG000NOTREAL")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject {
                code,
                layer,
                message,
                admin_hint,
                ..
            } => {
                assert_eq!(code, "EMS-REF-2001");
                assert_eq!(layer, ValidationLayer::Reference);
                assert_eq!(
                    message,
                    "FIGI BBG000NOTREAL not present in security master."
                );
                assert_eq!(
                    admin_hint.as_deref(),
                    Some("Verify symbology onboarding for this instrument.")
                );
            }
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn inactive_instrument_fails_at_the_reference_layer() {
        let result = validate(
            &request(Some(7), Some("order-entry"), Some("BBG000SUSPEND")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject { code, message, .. } => {
                assert_eq!(code, "EMS-REF-2002");
                assert_eq!(
                    message,
                    "Instrument BBG000SUSPEND is not active (status: SUSPENDED)."
                );
            }
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn reference_is_checked_before_permission() {
        // The instrument is unknown AND the tag is not granted. REFERENCE is
        // layer 3, PERMISSION layer 4.
        let result = validate(
            &request(Some(7), Some("algo-trading"), Some("BBG000NOTREAL")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject { layer, .. } => assert_eq!(layer, ValidationLayer::Reference),
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn missing_tag_fails_at_the_permission_layer() {
        let result = validate(
            &request(Some(7), Some("algo-trading"), Some("BBG000B9XRY4")),
            &aaa_with(&["order-entry"]),
            &securities(),
        );

        match result {
            ValidationResult::Reject {
                code,
                layer,
                admin_hint,
                ..
            } => {
                assert_eq!(code, "EMS-PRM-1003");
                assert_eq!(layer, ValidationLayer::Permission);
                assert_eq!(admin_hint.as_deref(), Some("Talk to FIRM1 admin."));
            }
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }

    #[test]
    fn absent_figi_skips_the_reference_layer() {
        let result = validate(
            &request(Some(7), Some("order-entry"), None),
            &aaa_with(&["order-entry"]),
            &SecurityMaster::new(),
        );

        assert!(
            matches!(result, ValidationResult::Pass { .. }),
            "{result:?}"
        );
    }

    #[test]
    fn absent_tag_skips_the_permission_layer() {
        let result = validate(
            &request(Some(7), None, Some("BBG000B9XRY4")),
            &aaa_with(&[]),
            &securities(),
        );

        assert!(
            matches!(result, ValidationResult::Pass { .. }),
            "{result:?}"
        );
    }

    #[test]
    fn absent_session_id_fails_at_the_session_layer() {
        let result = validate(
            &request(None, None, None),
            &aaa_with(&[]),
            &SecurityMaster::new(),
        );

        match result {
            ValidationResult::Reject { layer, message, .. } => {
                assert_eq!(layer, ValidationLayer::Session);
                assert_eq!(message, "Session -1 not found or has expired.");
            }
            other @ ValidationResult::Pass { .. } => {
                panic!("expected a rejection, got {other:?}")
            }
        }
    }
}
