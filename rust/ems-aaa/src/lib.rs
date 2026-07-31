//! Session lookup and the entitlement decision, sized for the deterministic slice.
//!
//! This is the authorization decision only — no logon credentials, no SSO, no
//! SCIM, no session sequence recovery. ADR 0002 scopes the component that way,
//! and the Java side carries the same narrowing in
//! `io.crossasset.ems.aaa.slice`.
//!
//! Sessions arrive on the journal as `SessionLogon` events rather than from
//! configuration, which keeps the whole entitlement state derivable from the
//! input. That is what lets a corpus case describe an authorization failure
//! without any external fixture.
#![forbid(unsafe_code)]

use std::collections::{BTreeMap, BTreeSet};

/// Catalog code: session ID unknown or already logged out.
pub const CODE_SESSION_NOT_FOUND: &str = "EMS-SES-1002";

/// Catalog code: user does not hold the required permission tag.
pub const CODE_MISSING_TAG: &str = "EMS-PRM-1001";

/// Who is acting, and what they are entitled to.
///
/// `tags` is a [`BTreeSet`] for the same reason journal fields are a
/// [`BTreeMap`]: its iteration order can reach the output journal, and the
/// conformance gate compares that journal byte-for-byte across three languages.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Identity {
    /// Firm identifier.
    pub firm: String,
    /// Desk identifier.
    pub desk: String,
    /// User identifier.
    pub user: String,
    /// Granted permission tags, iterating in lexicographic order.
    pub tags: BTreeSet<String>,
}

impl Identity {
    /// True when this identity holds `tag`.
    #[must_use]
    pub fn holds(&self, tag: &str) -> bool {
        self.tags.contains(tag)
    }
}

/// An established session.
///
/// No `established_at`: the production session record carries one read from a
/// clock, and the slice cannot read a clock without giving up byte-identical
/// replay. Logical ordering comes from the journal's sequence numbers.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Session {
    /// Identifier the order references.
    pub session_id: u64,
    /// Who the session belongs to.
    pub identity: Identity,
}

/// The outcome of an entitlement check.
///
/// The reject `code` is a real entry in `schemas/reject-codes/catalog.yaml` — it
/// reaches the output journal, so an invented code would diverge from the
/// catalog silently and the conformance gate would not notice.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AuthorizationDecision {
    /// The action is permitted.
    Allow,
    /// The action is refused.
    Deny {
        /// Catalog code, e.g. `EMS-PRM-1001`.
        code: String,
        /// Catalog category, e.g. `PRM`.
        category: String,
        /// Human-readable explanation; reaches the journal, so the wording is a contract.
        reason: String,
    },
}

/// Sessions registered from the journal, held for the length of one run.
///
/// Not thread-safe by construction — the slice binary is single-threaded.
#[derive(Debug, Default)]
pub struct AaaService {
    sessions: BTreeMap<u64, Session>,
}

impl AaaService {
    /// Creates an empty service.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Registers a session, replacing any existing one with the same identifier.
    ///
    /// Replacement rather than rejection: a second logon on the same identifier
    /// is a re-logon, and the FIX session layer that would police it is out of
    /// scope for this slice (ADR 0002).
    pub fn register(&mut self, session: Session) {
        self.sessions.insert(session.session_id, session);
    }

    /// Returns the session, or `None` when the identifier is unknown.
    #[must_use]
    pub fn session(&self, session_id: u64) -> Option<&Session> {
        self.sessions.get(&session_id)
    }

    /// Decides whether `session` may act with `tag`.
    ///
    /// An empty tag means the action requires no entitlement.
    #[must_use]
    pub fn authorize(session: &Session, tag: &str) -> AuthorizationDecision {
        if tag.is_empty() || session.identity.holds(tag) {
            return AuthorizationDecision::Allow;
        }
        AuthorizationDecision::Deny {
            code: CODE_MISSING_TAG.to_owned(),
            category: "PRM".to_owned(),
            reason: format!(
                "User {} does not have permission tag #{tag}.",
                session.identity.user
            ),
        }
    }
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::{AaaService, AuthorizationDecision, Identity, Session};
    use std::collections::BTreeSet;

    fn session(id: u64, user: &str, tags: &[&str]) -> Session {
        Session {
            session_id: id,
            identity: Identity {
                firm: "FIRM1".to_owned(),
                desk: "DESK1".to_owned(),
                user: user.to_owned(),
                tags: tags
                    .iter()
                    .map(|t| (*t).to_owned())
                    .collect::<BTreeSet<_>>(),
            },
        }
    }

    #[test]
    fn unknown_session_is_none() {
        assert!(AaaService::new().session(42).is_none());
    }

    #[test]
    fn registered_session_is_found() {
        let mut service = AaaService::new();
        service.register(session(42, "trader1", &["order-entry"]));

        assert_eq!(
            service.session(42).expect("registered").identity.user,
            "trader1"
        );
    }

    #[test]
    fn re_logon_replaces_the_session() {
        let mut service = AaaService::new();
        service.register(session(42, "trader1", &["order-entry"]));
        service.register(session(42, "trader2", &[]));

        // Policing duplicate logons belongs to the FIX session layer, which
        // ADR 0002 puts out of scope for this slice.
        assert_eq!(
            service.session(42).expect("registered").identity.user,
            "trader2"
        );
    }

    #[test]
    fn held_tag_is_allowed() {
        let s = session(1, "trader1", &["order-entry"]);
        assert_eq!(
            AaaService::authorize(&s, "order-entry"),
            AuthorizationDecision::Allow
        );
    }

    #[test]
    fn missing_tag_is_denied_with_the_catalog_code() {
        let s = session(1, "trader1", &["market-data"]);

        match AaaService::authorize(&s, "order-entry") {
            AuthorizationDecision::Deny {
                code,
                category,
                reason,
            } => {
                // EMS-PRM-1001 is a real entry in
                // schemas/reject-codes/catalog.yaml. It reaches the output
                // journal, so an invented code would diverge silently.
                assert_eq!(code, "EMS-PRM-1001");
                assert_eq!(category, "PRM");
                assert_eq!(
                    reason,
                    "User trader1 does not have permission tag #order-entry."
                );
            }
            other @ AuthorizationDecision::Allow => {
                panic!("expected a denial, got {other:?}")
            }
        }
    }

    #[test]
    fn empty_tag_requires_no_entitlement() {
        let s = session(1, "trader1", &[]);
        assert_eq!(AaaService::authorize(&s, ""), AuthorizationDecision::Allow);
    }

    #[test]
    fn tags_iterate_in_lexicographic_order_regardless_of_insertion_order() {
        let s = session(1, "trader1", &["zeta", "alpha", "mid"]);

        // The tag set can reach the output journal, so its order is a contract.
        assert_eq!(
            s.identity.tags.iter().collect::<Vec<_>>(),
            vec!["alpha", "mid", "zeta"]
        );
    }
}
