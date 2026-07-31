//! Event journal codec and deterministic identifiers.
//!
//! This crate is the Rust side of a three-language contract. The byte layout
//! [`journal::encode`] produces must match, byte for byte, what
//! `java/ems-core`'s `JournalCodec` and `cpp/ems-core`'s `journal.cpp` produce
//! — the conformance harness diffs their output on every corpus case.
//!
//! See `conformance/README.md` and
//! `docs/decisions/0003-shared-schemas-corpus-harness.md`.
#![forbid(unsafe_code)]

pub mod ids;
pub mod journal;

pub use ids::DeterministicIds;
pub use journal::{
    decode, encode, read_journal, write_journal, JournalError, JournalEvent, MalformedJournal,
};
