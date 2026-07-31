#pragma once

// The JSONL event journal — the wire format the conformance gate compares
// byte-for-byte across Java, Rust and C++.

#include <cstdint>
#include <filesystem>
#include <map>
#include <string>
#include <vector>

#include "ems_core/result.hpp"

namespace ems::core {

/// One line of an event journal.
///
/// `fields` is a std::map rather than an unordered_map on purpose: its
/// iteration order reaches the output journal, and the conformance gate
/// compares that journal byte-for-byte across three languages.
struct JournalEvent {
    std::uint64_t seq{};
    std::string type;
    std::map<std::string, std::string> fields;

    friend bool operator==(const JournalEvent&, const JournalEvent&) = default;
};

/// A journal line could not be parsed.
///
/// Always carries the 1-based line number. The journal parser is one of the
/// three fuzz targets in the polyglot gate, so every malformed input must
/// produce this — never a crash, never a silently skipped line.
struct MalformedJournal {
    std::size_t line{};
    std::string message;

    [[nodiscard]] std::string to_string() const {
        return "line " + std::to_string(line) + ": " + message;
    }
};

/// Encodes one event, without the trailing newline.
[[nodiscard]] std::string encode(const JournalEvent& event);

/// Decodes one line. `line_number` is 1-based and appears in any error message.
[[nodiscard]] Result<JournalEvent, MalformedJournal> decode(std::string_view line,
                                                            std::size_t line_number);

/// Reads every non-blank line, failing on the first line that does not parse.
[[nodiscard]] Result<std::vector<JournalEvent>, MalformedJournal> read_journal(
    const std::filesystem::path& path);

/// Writes every event, one per line, replacing any existing file.
[[nodiscard]] Status<MalformedJournal> write_journal(const std::filesystem::path& path,
                                                     const std::vector<JournalEvent>& events);

}  // namespace ems::core
