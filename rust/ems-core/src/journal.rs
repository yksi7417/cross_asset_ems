//! The JSONL event journal — the wire format the conformance gate compares byte-for-byte.

use std::collections::BTreeMap;
use std::fmt;
use std::fmt::Write as _;
use std::fs;
use std::io;
use std::path::Path;

const KEY_FIELDS: &str = "fields";
const KEY_SEQ: &str = "seq";
const KEY_TYPE: &str = "type";

/// One line of an event journal.
///
/// `fields` is a [`BTreeMap`] rather than a `HashMap` on purpose: its iteration
/// order reaches the output journal, and the conformance gate compares that
/// journal byte-for-byte across three languages. `clippy.toml` bans `HashMap`
/// workspace-wide for the same reason.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JournalEvent {
    /// Monotonically increasing sequence number, starting at 1.
    pub seq: u64,
    /// Event type name, e.g. `OrderNew`.
    pub event_type: String,
    /// Event payload; keys iterate in lexicographic order.
    pub fields: BTreeMap<String, String>,
}

impl JournalEvent {
    /// Creates an event with no payload.
    #[must_use]
    pub fn of(seq: u64, event_type: &str) -> Self {
        Self {
            seq,
            event_type: event_type.to_owned(),
            fields: BTreeMap::new(),
        }
    }

    /// Returns a copy of this event carrying a different sequence number.
    #[must_use]
    pub fn with_seq(&self, seq: u64) -> Self {
        Self {
            seq,
            ..self.clone()
        }
    }
}

/// A journal line could not be parsed.
///
/// Always carries the 1-based line number. The journal parser is one of the
/// three fuzz targets in the polyglot gate, so every malformed input must
/// produce this — never a panic, never a silently skipped line.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MalformedJournal {
    /// 1-based line number the failure was found on.
    pub line: usize,
    /// What was wrong.
    pub message: String,
}

impl fmt::Display for MalformedJournal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "line {}: {}", self.line, self.message)
    }
}

impl std::error::Error for MalformedJournal {}

/// Anything that can go wrong reading or writing a journal.
#[derive(Debug)]
pub enum JournalError {
    /// The file could not be read or written.
    Io(io::Error),
    /// A line could not be parsed.
    Malformed(MalformedJournal),
}

impl fmt::Display for JournalError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(e) => write!(f, "{e}"),
            Self::Malformed(e) => write!(f, "{e}"),
        }
    }
}

impl std::error::Error for JournalError {}

impl From<io::Error> for JournalError {
    fn from(e: io::Error) -> Self {
        Self::Io(e)
    }
}

impl From<MalformedJournal> for JournalError {
    fn from(e: MalformedJournal) -> Self {
        Self::Malformed(e)
    }
}

// ── writing ──────────────────────────────────────────────────────────────────

/// Writes every event, one per line, replacing any existing file.
///
/// # Errors
/// Returns [`JournalError::Io`] if the file cannot be written.
pub fn write_journal(path: &Path, events: &[JournalEvent]) -> Result<(), JournalError> {
    let mut out = String::new();
    for event in events {
        encode_into(&mut out, event);
        out.push('\n');
    }
    fs::write(path, out.as_bytes())?;
    Ok(())
}

/// Encodes one event, without the trailing newline.
#[must_use]
pub fn encode(event: &JournalEvent) -> String {
    let mut out = String::new();
    encode_into(&mut out, event);
    out
}

fn encode_into(out: &mut String, event: &JournalEvent) {
    out.push_str("{\"");
    out.push_str(KEY_FIELDS);
    out.push_str("\":{");
    for (i, (key, value)) in event.fields.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        append_string(out, key);
        out.push(':');
        append_string(out, value);
    }
    out.push_str("},\"");
    out.push_str(KEY_SEQ);
    out.push_str("\":");
    out.push_str(&event.seq.to_string());
    out.push_str(",\"");
    out.push_str(KEY_TYPE);
    out.push_str("\":");
    append_string(out, &event.event_type);
    out.push('}');
}

fn append_string(out: &mut String, value: &str) {
    out.push('"');
    for c in value.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\u{8}' => out.push_str("\\b"),
            '\u{c}' => out.push_str("\\f"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                // No short escape exists; a raw control byte in the journal is a
                // byte-level divergence waiting to happen between three writers.
                // write! into the buffer rather than allocating a temporary String
                // per control character.
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            // Non-ASCII is written as UTF-8, not escaped. All three languages
            // agree on UTF-8; they would not agree on when to escape.
            c => out.push(c),
        }
    }
    out.push('"');
}

// ── reading ──────────────────────────────────────────────────────────────────

/// Reads every non-blank line.
///
/// # Errors
/// Returns [`JournalError::Io`] if the file cannot be read, or
/// [`JournalError::Malformed`] on the first line that does not parse.
pub fn read_journal(path: &Path) -> Result<Vec<JournalEvent>, JournalError> {
    let raw = fs::read_to_string(path)?;
    let mut events = Vec::new();
    for (index, line) in raw.lines().enumerate() {
        if line.trim().is_empty() {
            continue;
        }
        events.push(decode(line, index + 1)?);
    }
    Ok(events)
}

/// Decodes one line. `line_number` is 1-based and appears in any error message.
///
/// # Errors
/// Returns [`MalformedJournal`] describing what was wrong and where.
pub fn decode(line: &str, line_number: usize) -> Result<JournalEvent, MalformedJournal> {
    Parser::new(line, line_number).parse_event()
}

/// Recursive-descent parser for the restricted grammar the journal uses.
///
/// Accepting only what the format actually uses keeps the fuzz surface small
/// and makes every rejection specific. Operates on `char` positions rather than
/// bytes so a multi-byte UTF-8 value cannot be split mid-character.
struct Parser {
    chars: Vec<char>,
    line_number: usize,
    pos: usize,
}

impl Parser {
    fn new(src: &str, line_number: usize) -> Self {
        Self {
            chars: src.chars().collect(),
            line_number,
            pos: 0,
        }
    }

    fn parse_event(&mut self) -> Result<JournalEvent, MalformedJournal> {
        self.skip_whitespace();
        self.expect('{')?;

        let mut fields: Option<BTreeMap<String, String>> = None;
        let mut seq: Option<u64> = None;
        let mut event_type: Option<String> = None;

        self.skip_whitespace();
        if self.peek()? != '}' {
            loop {
                self.skip_whitespace();
                let key = self.parse_string()?;
                self.skip_whitespace();
                self.expect(':')?;
                self.skip_whitespace();
                match key.as_str() {
                    KEY_FIELDS => {
                        self.require_absent(fields.is_some(), KEY_FIELDS)?;
                        fields = Some(self.parse_fields()?);
                    }
                    KEY_SEQ => {
                        self.require_absent(seq.is_some(), KEY_SEQ)?;
                        seq = Some(self.parse_non_negative_u64()?);
                    }
                    KEY_TYPE => {
                        self.require_absent(event_type.is_some(), KEY_TYPE)?;
                        event_type = Some(self.parse_string()?);
                    }
                    other => return Err(self.fail(&format!("unknown key \"{other}\""))),
                }
                self.skip_whitespace();
                if self.peek()? == ',' {
                    self.pos += 1;
                    continue;
                }
                break;
            }
        }

        self.expect('}')?;
        self.skip_whitespace();
        if self.pos != self.chars.len() {
            return Err(self.fail("trailing content after the object"));
        }

        // STUDY: option-vs-nullable
        let fields = fields.ok_or_else(|| self.fail(&format!("missing key \"{KEY_FIELDS}\"")))?;
        let seq = seq.ok_or_else(|| self.fail(&format!("missing key \"{KEY_SEQ}\"")))?;
        let event_type =
            event_type.ok_or_else(|| self.fail(&format!("missing key \"{KEY_TYPE}\"")))?;

        Ok(JournalEvent {
            seq,
            event_type,
            fields,
        })
    }

    fn parse_fields(&mut self) -> Result<BTreeMap<String, String>, MalformedJournal> {
        let mut fields = BTreeMap::new();
        self.expect('{')?;
        self.skip_whitespace();
        if self.peek()? == '}' {
            self.pos += 1;
            return Ok(fields);
        }
        loop {
            self.skip_whitespace();
            let key = self.parse_string()?;
            self.skip_whitespace();
            self.expect(':')?;
            self.skip_whitespace();
            if self.peek()? != '"' {
                return Err(self.fail(&format!("field \"{key}\" must be a string")));
            }
            let value = self.parse_string()?;
            if fields.insert(key.clone(), value).is_some() {
                return Err(self.fail(&format!("duplicate field \"{key}\"")));
            }
            self.skip_whitespace();
            if self.peek()? == ',' {
                self.pos += 1;
                continue;
            }
            break;
        }
        self.expect('}')?;
        Ok(fields)
    }

    fn parse_string(&mut self) -> Result<String, MalformedJournal> {
        self.expect('"')?;
        let mut out = String::new();
        loop {
            let Some(&c) = self.chars.get(self.pos) else {
                return Err(self.fail("unterminated string"));
            };
            self.pos += 1;
            if c == '"' {
                return Ok(out);
            }
            if c != '\\' {
                if (c as u32) < 0x20 {
                    return Err(self.fail("raw control character in string"));
                }
                out.push(c);
                continue;
            }
            let Some(&esc) = self.chars.get(self.pos) else {
                return Err(self.fail("unterminated escape"));
            };
            self.pos += 1;
            match esc {
                '"' => out.push('"'),
                '\\' => out.push('\\'),
                '/' => out.push('/'),
                'b' => out.push('\u{8}'),
                'f' => out.push('\u{c}'),
                'n' => out.push('\n'),
                'r' => out.push('\r'),
                't' => out.push('\t'),
                'u' => out.push(self.parse_unicode_escape()?),
                other => return Err(self.fail(&format!("invalid escape \"\\{other}\""))),
            }
        }
    }

    fn parse_unicode_escape(&mut self) -> Result<char, MalformedJournal> {
        if self.pos + 4 > self.chars.len() {
            return Err(self.fail("truncated unicode escape"));
        }
        let mut value: u32 = 0;
        for i in 0..4 {
            let digit = self.chars[self.pos + i]
                .to_digit(16)
                .ok_or_else(|| self.fail("invalid unicode escape"))?;
            value = value * 16 + digit;
        }
        self.pos += 4;
        char::from_u32(value).ok_or_else(|| self.fail("unicode escape is not a scalar value"))
    }

    fn parse_non_negative_u64(&mut self) -> Result<u64, MalformedJournal> {
        let start = self.pos;
        while self.chars.get(self.pos).is_some_and(char::is_ascii_digit) {
            self.pos += 1;
        }
        if self.pos == start {
            return Err(self.fail(&format!(
                "expected a non-negative integer for \"{KEY_SEQ}\""
            )));
        }
        let digits: String = self.chars[start..self.pos].iter().collect();
        digits
            .parse::<u64>()
            .map_err(|_| self.fail("sequence number out of range"))
    }

    fn require_absent(&self, already_seen: bool, key: &str) -> Result<(), MalformedJournal> {
        if already_seen {
            return Err(self.fail(&format!("duplicate key \"{key}\"")));
        }
        Ok(())
    }

    fn skip_whitespace(&mut self) {
        while matches!(self.chars.get(self.pos), Some(' ' | '\t' | '\r' | '\n')) {
            self.pos += 1;
        }
    }

    fn peek(&self) -> Result<char, MalformedJournal> {
        self.chars
            .get(self.pos)
            .copied()
            .ok_or_else(|| self.fail("unexpected end of line"))
    }

    fn expect(&mut self, expected: char) -> Result<(), MalformedJournal> {
        if self.chars.get(self.pos) != Some(&expected) {
            return Err(self.fail(&format!("expected '{expected}' at offset {}", self.pos)));
        }
        self.pos += 1;
        Ok(())
    }

    fn fail(&self, message: &str) -> MalformedJournal {
        MalformedJournal {
            line: self.line_number,
            message: message.to_owned(),
        }
    }
}
