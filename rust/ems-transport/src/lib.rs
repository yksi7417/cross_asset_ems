//! How the slice receives and emits events, without knowing what carries them.
//!
//! Two implementations are intended: [`JournalTransport`], which reads and
//! writes files and is what the conformance gate runs, and an Aeron-backed one
//! for the live path. The slice is written against the [`Transport`] trait so
//! the gate can be deterministic while production is not.
//!
//! That split is the answer to the one genuine technical objection in the
//! 2026-06-07 decision to drop Rust: it takes the unproven `aeron-rs` binding
//! off the critical path of everything. See
//! `docs/decisions/0006-abstract-transport-journal-first.md`.
#![forbid(unsafe_code)]

use std::path::{Path, PathBuf};

use ems_core::{read_journal, write_journal, JournalError, JournalEvent};

/// The transport seam.
///
/// Single-threaded by design — see
/// `docs/decisions/0003-shared-schemas-corpus-harness.md`. A concurrent
/// transport is a later, separately-gated concern.
pub trait Transport {
    /// Returns everything available to consume, and consumes it.
    ///
    /// A second call returns an empty vector rather than replaying: draining
    /// twice is a programming error the trait makes visible instead of quietly
    /// duplicating an order.
    ///
    /// # Errors
    /// Returns [`JournalError`] if the source cannot be read or does not parse.
    fn drain(&mut self) -> Result<Vec<JournalEvent>, JournalError>;

    /// Queues an event for emission.
    ///
    /// Nothing is visible to a reader until [`Transport::flush`]. A half-written
    /// journal from a crashed run would look exactly like a legitimately short
    /// one, and the conformance gate cannot tell the difference.
    fn publish(&mut self, event: JournalEvent);

    /// Makes every published event visible, as one unit.
    ///
    /// # Errors
    /// Returns [`JournalError`] if the destination cannot be written.
    fn flush(&mut self) -> Result<(), JournalError>;
}

/// A [`Transport`] backed by two journal files.
///
/// Deterministic by construction: no media driver, no network, no clock. This
/// is what makes byte-identical replay across three languages testable at all.
///
/// Published events are buffered and written on [`Transport::flush`].
/// Streaming them as they arrive would be cheaper, and would mean a run that
/// died halfway left a file indistinguishable from a correct short one — which
/// the conformance differ would report as a byte mismatch on a later line,
/// sending the reader looking for a logic bug that is not there.
#[derive(Debug)]
pub struct JournalTransport {
    input: PathBuf,
    output: PathBuf,
    pending: Vec<JournalEvent>,
    drained: bool,
}

impl JournalTransport {
    /// Creates a transport reading `input` and writing `output`.
    #[must_use]
    pub fn new(input: &Path, output: &Path) -> Self {
        Self {
            input: input.to_path_buf(),
            output: output.to_path_buf(),
            pending: Vec::new(),
            drained: false,
        }
    }
}

impl Transport for JournalTransport {
    fn drain(&mut self) -> Result<Vec<JournalEvent>, JournalError> {
        if self.drained {
            return Ok(Vec::new());
        }
        self.drained = true;
        read_journal(&self.input)
    }

    fn publish(&mut self, event: JournalEvent) {
        self.pending.push(event);
    }

    fn flush(&mut self) -> Result<(), JournalError> {
        write_journal(&self.output, &self.pending)
    }
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::{JournalTransport, Transport};
    use ems_core::{write_journal, JournalError, JournalEvent};
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    fn tmp_dir() -> PathBuf {
        static COUNTER: AtomicU64 = AtomicU64::new(0);
        // Process id as well as a counter, and the directory is cleared: reusing a
        // fixed name across runs meant a previous run's out.jsonl was still there,
        // and `publish_before_flush_writes_nothing` failed on a file it did not
        // create. Wall-clock is not an option — the port bans it.
        let dir = std::env::temp_dir().join(format!(
            "ems-{}-{}-{}",
            env!("CARGO_PKG_NAME"),
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::Relaxed)
        ));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).expect("create temp dir");
        dir
    }

    fn event(seq: u64, event_type: &str) -> JournalEvent {
        JournalEvent::of(seq, event_type)
    }

    #[test]
    fn drain_returns_the_input_journal_once_and_then_empty() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        write_journal(&input, &[event(1, "OrderNew"), event(2, "Heartbeat")]).expect("write");
        let mut transport = JournalTransport::new(&input, &dir.join("out.jsonl"));

        assert_eq!(transport.drain().expect("drain").len(), 2);
        // Draining twice is a programming error; replaying would silently
        // duplicate an order rather than surfacing it.
        assert!(transport.drain().expect("drain").is_empty());
    }

    #[test]
    fn published_events_appear_in_order_after_flush() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        let output = dir.join("out.jsonl");
        write_journal(&input, &[]).expect("write");
        let mut transport = JournalTransport::new(&input, &output);
        transport.publish(event(1, "A"));
        transport.publish(event(2, "B"));
        transport.flush().expect("flush");

        let written = ems_core::read_journal(&output).expect("read");
        assert_eq!(
            written
                .iter()
                .map(|e| e.event_type.as_str())
                .collect::<Vec<_>>(),
            vec!["A", "B"]
        );
    }

    #[test]
    fn publish_before_flush_writes_nothing() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        let output = dir.join("out.jsonl");
        write_journal(&input, &[]).expect("write");
        let mut transport = JournalTransport::new(&input, &output);
        transport.publish(event(1, "A"));

        // A half-written journal is indistinguishable from a legitimately short
        // one, which would send a reader hunting for a logic bug that is not
        // there.
        assert!(!output.exists());
    }

    #[test]
    fn flush_with_nothing_published_writes_an_empty_file() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        let output = dir.join("out.jsonl");
        write_journal(&input, &[]).expect("write");
        let mut transport = JournalTransport::new(&input, &output);
        transport.flush().expect("flush");

        assert_eq!(fs::read_to_string(&output).expect("read"), "");
    }

    #[test]
    fn flush_is_idempotent_rather_than_appending() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        let output = dir.join("out.jsonl");
        write_journal(&input, &[]).expect("write");
        let mut transport = JournalTransport::new(&input, &output);
        transport.publish(event(1, "A"));
        transport.flush().expect("flush");
        transport.flush().expect("flush");

        // Appending on the second flush would duplicate the whole journal — and
        // the conformance differ would report it as an "extra line" ten lines
        // later.
        assert_eq!(ems_core::read_journal(&output).expect("read").len(), 1);
    }

    #[test]
    fn malformed_input_surfaces_as_a_malformed_journal_error() {
        let dir = tmp_dir();
        let input = dir.join("in.jsonl");
        fs::write(&input, "not json\n").expect("write");
        let mut transport = JournalTransport::new(&input, &dir.join("out.jsonl"));

        match transport.drain() {
            Err(JournalError::Malformed(e)) => assert_eq!(e.line, 1),
            other => panic!("expected a malformed-journal error, got {other:?}"),
        }
    }

    #[test]
    fn missing_input_surfaces_as_an_io_error() {
        let dir = tmp_dir();
        let mut transport = JournalTransport::new(&dir.join("nope.jsonl"), &dir.join("out.jsonl"));

        assert!(matches!(transport.drain(), Err(JournalError::Io(_))));
    }
}
