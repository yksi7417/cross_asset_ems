//! Session lookup and the three-layer tag-permission AND-gate.
//!
//! A port of `io.crossasset.ems.aaa` as the slice uses it: `InMemoryAaaService`
//! for sessions and `TagPermissionEvaluator` for the firm → desk → user
//! AND-gate. The Java side is the reference; this must match its reject codes
//! and its message wording character for character, because both reach the
//! output journal the conformance gate compares byte-for-byte.
//!
//! Sessions arrive on the journal as `SessionLogon` events rather than from
//! configuration, which keeps the entitlement state derivable from the input.
#![forbid(unsafe_code)]

use std::collections::{BTreeMap, BTreeSet};

/// Catalog code: session ID unknown or already logged out.
pub const CODE_SESSION_NOT_FOUND: &str = "EMS-SES-1002";

/// Catalog code: the user does not hold the tag (innermost layer).
pub const CODE_USER_MISSING_TAG: &str = "EMS-PRM-1001";

/// Catalog code: the user holds the tag but the desk is not granted it.
pub const CODE_DESK_NOT_GRANTED: &str = "EMS-PRM-1002";

/// Catalog code: the firm is not granted the tag (outermost layer).
pub const CODE_FIRM_NOT_GRANTED: &str = "EMS-PRM-1003";

/// Which layer of the AND-gate refused.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DenialLevel {
    /// Firm-level grant missing — the outermost layer.
    Firm,
    /// Desk-level grant missing.
    Desk,
    /// User-level grant missing — the innermost layer.
    User,
}

/// Who is acting, and what they are entitled to.
///
/// `tags` is a [`BTreeSet`] because its iteration order can reach the output
/// journal, and the gate compares that journal byte-for-byte.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Identity {
    /// Firm identifier.
    pub firm: String,
    /// Desk identifier.
    pub desk: String,
    /// User identifier.
    pub user: String,
    /// Tags granted to the user, iterating in lexicographic order.
    pub tags: BTreeSet<String>,
}

/// An established session.
///
/// No `established_at`: the Java record carries one, and the slice pins it
/// rather than reading a clock. It does not reach the journal today.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Session {
    /// Identifier the order references.
    pub session_id: u64,
    /// Who the session belongs to.
    pub identity: Identity,
}

/// The outcome of the AND-gate.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AuthorizationResult {
    /// All three layers grant the tag.
    Allow,
    /// A layer refused. `code` is a catalog entry; `message` reaches the journal.
    Deny {
        /// Catalog code naming the outermost missing layer.
        code: String,
        /// Human-readable explanation. Wording is a cross-language contract.
        message: String,
        /// Which layer refused.
        level: DenialLevel,
        /// Who can grant the missing permission. Reaches the journal via the
        /// validator, which wraps it as ``"Talk to {admin_hint}."``
        admin_hint: String,
    },
}

/// Firm- and desk-level tag grants. User-level grants live on [`Identity::tags`].
#[derive(Debug, Default)]
pub struct TagPermissionStore {
    firm_grants: BTreeMap<String, BTreeSet<String>>,
    desk_grants: BTreeMap<(String, String), BTreeSet<String>>,
}

impl TagPermissionStore {
    /// Grants `tag` at firm level, subject to the inner layers.
    pub fn grant_firm_tag(&mut self, firm: &str, tag: &str) {
        self.firm_grants
            .entry(firm.to_owned())
            .or_default()
            .insert(tag.to_owned());
    }

    /// Grants `tag` at desk level, subject to the firm and user layers.
    pub fn grant_desk_tag(&mut self, firm: &str, desk: &str, tag: &str) {
        self.desk_grants
            .entry((firm.to_owned(), desk.to_owned()))
            .or_default()
            .insert(tag.to_owned());
    }

    /// True when the firm holds the tag.
    #[must_use]
    pub fn firm_granted(&self, firm: &str, tag: &str) -> bool {
        self.firm_grants
            .get(firm)
            .is_some_and(|tags| tags.contains(tag))
    }

    /// True when the desk holds the tag.
    #[must_use]
    pub fn desk_granted(&self, firm: &str, desk: &str, tag: &str) -> bool {
        self.desk_grants
            .get(&(firm.to_owned(), desk.to_owned()))
            .is_some_and(|tags| tags.contains(tag))
    }
}

/// Sessions plus the AND-gate, as the slice assembles them.
#[derive(Debug, Default)]
pub struct AaaService {
    sessions: BTreeMap<u64, Session>,
    grants: TagPermissionStore,
}

impl AaaService {
    /// Creates an empty service.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Establishes a session, replacing any existing one with the same id.
    ///
    /// A second logon on one id is a re-logon; policing it belongs to the FIX
    /// session layer, which is out of scope for this slice (ADR 0002).
    pub fn register_session(
        &mut self,
        session_id: u64,
        identity: Identity,
        firm_tags: &BTreeSet<String>,
        desk_tags: &BTreeSet<String>,
    ) {
        for tag in firm_tags {
            self.grants.grant_firm_tag(&identity.firm, tag);
        }
        for tag in desk_tags {
            self.grants
                .grant_desk_tag(&identity.firm, &identity.desk, tag);
        }
        self.sessions.insert(
            session_id,
            Session {
                session_id,
                identity,
            },
        );
    }

    /// Returns the session, or `None` when the identifier is unknown.
    #[must_use]
    pub fn session(&self, session_id: u64) -> Option<&Session> {
        self.sessions.get(&session_id)
    }

    /// Runs the three-layer AND-gate, outermost first.
    ///
    /// Order matters and is part of the contract: a firm denial is reported
    /// before the desk is consulted, because a missing firm grant makes the
    /// inner resolution irrelevant — and reporting "you lack the tag" would
    /// send the user to the wrong administrator. An empty tag requires no
    /// entitlement.
    #[must_use]
    pub fn authorize(&self, session: &Session, tag: &str) -> AuthorizationResult {
        if tag.is_empty() {
            return AuthorizationResult::Allow;
        }
        let identity = &session.identity;

        if !self.grants.firm_granted(&identity.firm, tag) {
            return AuthorizationResult::Deny {
                code: CODE_FIRM_NOT_GRANTED.to_owned(),
                message: format!("Firm `{}` is not granted tag `#{tag}`.", identity.firm),
                level: DenialLevel::Firm,
                admin_hint: format!("{} admin", identity.firm),
            };
        }

        if !self
            .grants
            .desk_granted(&identity.firm, &identity.desk, tag)
        {
            return AuthorizationResult::Deny {
                code: CODE_DESK_NOT_GRANTED.to_owned(),
                message: format!(
                    "User `{}` has tag `#{tag}` but desk `{}` is not granted.",
                    identity.user, identity.desk
                ),
                level: DenialLevel::Desk,
                admin_hint: format!("{} admin", identity.desk),
            };
        }

        if !identity.tags.contains(tag) {
            return AuthorizationResult::Deny {
                code: CODE_USER_MISSING_TAG.to_owned(),
                message: format!(
                    "User `{}` does not have permission tag `#{tag}`.",
                    identity.user
                ),
                level: DenialLevel::User,
                admin_hint: format!("tag admin for #{tag}"),
            };
        }

        AuthorizationResult::Allow
    }
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::{AaaService, AuthorizationResult, DenialLevel, Identity};
    use std::collections::BTreeSet;

    fn tags(values: &[&str]) -> BTreeSet<String> {
        values.iter().map(|t| (*t).to_owned()).collect()
    }

    fn identity(user: &str, user_tags: &[&str]) -> Identity {
        Identity {
            firm: "FIRM1".to_owned(),
            desk: "DESK1".to_owned(),
            user: user.to_owned(),
            tags: tags(user_tags),
        }
    }

    /// Registers a session granting the same tags at all three layers.
    fn service_with(user: &str, all_tags: &[&str]) -> AaaService {
        let mut service = AaaService::new();
        let granted = tags(all_tags);
        service.register_session(7, identity(user, all_tags), &granted, &granted);
        service
    }

    #[test]
    fn unknown_session_is_none() {
        assert!(AaaService::new().session(42).is_none());
    }

    #[test]
    fn registered_session_is_found() {
        let service = service_with("trader1", &["order-entry"]);
        assert_eq!(
            service.session(7).expect("registered").identity.user,
            "trader1"
        );
    }

    #[test]
    fn re_logon_replaces_the_session() {
        let mut service = service_with("trader1", &["order-entry"]);
        let granted = tags(&["order-entry"]);
        service.register_session(7, identity("trader2", &["order-entry"]), &granted, &granted);

        assert_eq!(
            service.session(7).expect("registered").identity.user,
            "trader2"
        );
    }

    #[test]
    fn held_tag_is_allowed_at_all_three_layers() {
        let service = service_with("trader1", &["order-entry"]);
        let session = service.session(7).expect("registered");

        assert_eq!(
            service.authorize(session, "order-entry"),
            AuthorizationResult::Allow
        );
    }

    #[test]
    fn empty_tag_requires_no_entitlement() {
        let service = service_with("trader1", &[]);
        let session = service.session(7).expect("registered");

        assert_eq!(service.authorize(session, ""), AuthorizationResult::Allow);
    }

    #[test]
    fn firm_denial_is_reported_before_the_desk_is_consulted() {
        // Outermost-first is part of the contract: a missing firm grant makes
        // the inner resolution irrelevant, so reporting "you lack the tag"
        // would send the user to the wrong administrator.
        let mut service = AaaService::new();
        service.register_session(
            7,
            identity("trader1", &["order-entry"]),
            &tags(&[]),
            &tags(&[]),
        );
        let session = service.session(7).expect("registered");

        match service.authorize(session, "order-entry") {
            AuthorizationResult::Deny {
                code,
                message,
                level,
                ..
            } => {
                assert_eq!(code, "EMS-PRM-1003");
                assert_eq!(level, DenialLevel::Firm);
                assert_eq!(message, "Firm `FIRM1` is not granted tag `#order-entry`.");
            }
            other @ AuthorizationResult::Allow => {
                panic!("expected a firm denial, got {other:?}")
            }
        }
    }

    #[test]
    fn desk_denial_when_the_firm_grants_but_the_desk_does_not() {
        let mut service = AaaService::new();
        service.register_session(
            7,
            identity("trader2", &["order-entry"]),
            &tags(&["order-entry"]),
            &tags(&[]),
        );
        let session = service.session(7).expect("registered");

        match service.authorize(session, "order-entry") {
            AuthorizationResult::Deny {
                code,
                message,
                level,
                ..
            } => {
                assert_eq!(code, "EMS-PRM-1002");
                assert_eq!(level, DenialLevel::Desk);
                assert_eq!(
                    message,
                    "User `trader2` has tag `#order-entry` but desk `DESK1` is not granted."
                );
            }
            other @ AuthorizationResult::Allow => {
                panic!("expected a desk denial, got {other:?}")
            }
        }
    }

    #[test]
    fn user_denial_when_both_outer_layers_grant() {
        let mut service = AaaService::new();
        service.register_session(
            7,
            identity("trader3", &["market-data"]),
            &tags(&["order-entry"]),
            &tags(&["order-entry"]),
        );
        let session = service.session(7).expect("registered");

        match service.authorize(session, "order-entry") {
            AuthorizationResult::Deny {
                code,
                message,
                level,
                ..
            } => {
                assert_eq!(code, "EMS-PRM-1001");
                assert_eq!(level, DenialLevel::User);
                assert_eq!(
                    message,
                    "User `trader3` does not have permission tag `#order-entry`."
                );
            }
            other @ AuthorizationResult::Allow => {
                panic!("expected a user denial, got {other:?}")
            }
        }
    }

    #[test]
    fn tags_iterate_in_lexicographic_order_regardless_of_insertion_order() {
        let id = identity("trader1", &["zeta", "alpha", "mid"]);

        // The tag set reaches the output journal, so its order is a contract.
        assert_eq!(
            id.tags.iter().collect::<Vec<_>>(),
            vec!["alpha", "mid", "zeta"]
        );
    }
}
