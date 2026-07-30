//! Journal codec tests.
//!
//! Every expectation here is deliberately the same assertion as
//! `java/ems-core/src/test/java/io/crossasset/ems/core/journal/JournalCodecTest.java`.
//! Two implementations agreeing on their own unit tests is what makes the
//! byte-exact conformance run a formality rather than a surprise.

// A failing test SHOULD panic — that is how it reports. The workspace denies
// expect/unwrap/panic because the slice binary must turn errors into an exit
// code or a journal event, and that rule has no business applying to tests.
#![allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]

use std::fs;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};

use ems_core::journal::{decode, read_journal, write_journal, JournalError, JournalEvent};

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

fn event(seq: u64, event_type: &str, fields: &[(&str, &str)]) -> JournalEvent {
    JournalEvent {
        seq,
        event_type: event_type.to_owned(),
        fields: fields
            .iter()
            .map(|(k, v)| ((*k).to_owned(), (*v).to_owned()))
            .collect(),
    }
}

#[test]
fn writes_top_level_and_field_keys_in_lexicographic_order() {
    let dir = tmp_dir();
    let path = dir.join("out.jsonl");
    write_journal(
        &path,
        &[event(1, "OrderNew", &[("qty", "100"), ("account", "ACC1")])],
    )
    .expect("write");

    assert_eq!(
        fs::read_to_string(&path).expect("read"),
        "{\"fields\":{\"account\":\"ACC1\",\"qty\":\"100\"},\"seq\":1,\"type\":\"OrderNew\"}\n"
    );
}

#[test]
fn every_line_ends_with_newline_including_the_last() {
    let dir = tmp_dir();
    let path = dir.join("out.jsonl");
    write_journal(&path, &[event(1, "A", &[]), event(2, "B", &[])]).expect("write");

    assert_eq!(
        fs::read_to_string(&path).expect("read"),
        "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n{\"fields\":{},\"seq\":2,\"type\":\"B\"}\n"
    );
}

#[test]
fn round_trips_without_loss() {
    let dir = tmp_dir();
    let path = dir.join("rt.jsonl");
    let events = vec![
        event(
            1,
            "OrderNew",
            &[("figi", "BBG000B9XRY4"), ("price", "1250000")],
        ),
        event(2, "OrderAccepted", &[("orderId", "ORD-0000000001")]),
    ];
    write_journal(&path, &events).expect("write");

    assert_eq!(read_journal(&path).expect("read"), events);
}

#[test]
fn escapes_quotes_backslashes_and_control_characters() {
    let dir = tmp_dir();
    let path = dir.join("esc.jsonl");
    let raw = "a\"b\\c\nd\te\u{1}f";
    write_journal(&path, &[event(1, "Note", &[("text", raw)])]).expect("write");

    assert_eq!(
        fs::read_to_string(&path).expect("read"),
        "{\"fields\":{\"text\":\"a\\\"b\\\\c\\nd\\te\\u0001f\"},\"seq\":1,\"type\":\"Note\"}\n"
    );
    assert_eq!(read_journal(&path).expect("read")[0].fields["text"], raw);
}

#[test]
fn non_ascii_is_written_as_utf8_not_escaped() {
    let dir = tmp_dir();
    let path = dir.join("utf8.jsonl");
    write_journal(&path, &[event(1, "Note", &[("text", "café — ☕")])]).expect("write");

    assert_eq!(
        fs::read_to_string(&path).expect("read"),
        "{\"fields\":{\"text\":\"café — ☕\"},\"seq\":1,\"type\":\"Note\"}\n"
    );
    assert_eq!(
        read_journal(&path).expect("read")[0].fields["text"],
        "café — ☕"
    );
}

#[test]
fn empty_journal_round_trips() {
    let dir = tmp_dir();
    let path = dir.join("empty.jsonl");
    write_journal(&path, &[]).expect("write");

    assert_eq!(fs::read_to_string(&path).expect("read"), "");
    assert!(read_journal(&path).expect("read").is_empty());
}

#[test]
fn blank_lines_are_ignored_on_read() {
    let dir = tmp_dir();
    let path = dir.join("blank.jsonl");
    fs::write(&path, "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n\n").expect("write");

    assert_eq!(read_journal(&path).expect("read").len(), 1);
}

#[test]
fn malformed_line_reports_its_line_number() {
    let dir = tmp_dir();
    let path = dir.join("bad.jsonl");
    fs::write(
        &path,
        "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\nnot json\n",
    )
    .expect("write");

    match read_journal(&path) {
        Err(JournalError::Malformed(e)) => assert_eq!(e.line, 2),
        other => panic!("expected a malformed-journal error, got {other:?}"),
    }
}

#[test]
fn unknown_top_level_key_is_rejected() {
    let err = decode(
        "{\"fields\":{},\"seq\":1,\"type\":\"A\",\"extra\":\"x\"}",
        1,
    )
    .expect_err("must reject");
    assert!(err.message.contains("extra"), "{err}");
}

#[test]
fn missing_required_key_is_rejected() {
    let err = decode("{\"fields\":{},\"seq\":1}", 1).expect_err("must reject");
    assert!(err.message.contains("type"), "{err}");
}

#[test]
fn duplicate_top_level_key_is_rejected() {
    let err =
        decode("{\"fields\":{},\"seq\":1,\"seq\":2,\"type\":\"A\"}", 1).expect_err("must reject");
    assert!(err.message.contains("duplicate"), "{err}");
}

#[test]
fn non_string_field_value_is_rejected() {
    decode("{\"fields\":{\"qty\":100},\"seq\":1,\"type\":\"A\"}", 1).expect_err("must reject");
}

#[test]
fn trailing_content_after_the_object_is_rejected() {
    decode("{\"fields\":{},\"seq\":1,\"type\":\"A\"} junk", 1).expect_err("must reject");
}

#[test]
fn negative_sequence_is_rejected() {
    decode("{\"fields\":{},\"seq\":-1,\"type\":\"A\"}", 1).expect_err("must reject");
}

#[test]
fn fields_are_ordered_regardless_of_insertion_order() {
    let a = event(1, "X", &[("b", "2"), ("a", "1")]);
    let b = event(1, "X", &[("a", "1"), ("b", "2")]);

    assert_eq!(a, b);
    assert_eq!(a.fields.keys().collect::<Vec<_>>(), vec!["a", "b"]);
}

/// Hostile input: the parser is a fuzz target, so none of these may panic.
#[test]
fn hostile_input_never_panics() {
    let cases = [
        "",
        "{",
        "}",
        "{\"",
        "{\"fields\"",
        "{\"fields\":",
        "{\"fields\":{",
        "{\"fields\":{\"a\"",
        "{\"fields\":{\"a\":",
        "{\"fields\":{\"a\":\"",
        "{\"fields\":{},\"seq\":",
        "{\"fields\":{},\"seq\":99999999999999999999999,\"type\":\"A\"}",
        "{\"fields\":{},\"seq\":1,\"type\":\"\\u\"}",
        "{\"fields\":{},\"seq\":1,\"type\":\"\\uZZZZ\"}",
        "{\"fields\":{},\"seq\":1,\"type\":\"\\q\"}",
        "{\"fields\":{},\"seq\":1,\"type\":\"é",
        "\u{0}",
        "[]",
        "null",
    ];
    for case in cases {
        let result = decode(case, 1);
        assert!(result.is_err(), "expected rejection for {case:?}");
    }
}

/// A lone surrogate is a valid `\uXXXX` escape in JSON but not a Rust `char`.
/// Rejecting it is the honest answer; silently substituting U+FFFD would put a
/// different byte sequence in the journal than Java produces.
#[test]
fn lone_surrogate_escape_is_rejected() {
    let err = decode("{\"fields\":{},\"seq\":1,\"type\":\"\\ud800\"}", 1).expect_err("must reject");
    assert!(err.message.contains("scalar value"), "{err}");
}

#[test]
fn very_long_input_is_handled_without_panic() {
    let long = "x".repeat(100_000);
    let line = format!("{{\"fields\":{{\"a\":\"{long}\"}},\"seq\":1,\"type\":\"A\"}}");
    let parsed = decode(&line, 1).expect("long values are legal");
    assert_eq!(parsed.fields["a"].len(), 100_000);
}
