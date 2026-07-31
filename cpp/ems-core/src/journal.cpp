#include "ems_core/journal.hpp"

#include <array>
#include <cstddef>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>

namespace ems::core {
namespace {

constexpr std::string_view kKeyFields = "fields";
constexpr std::string_view kKeySeq = "seq";
constexpr std::string_view kKeyType = "type";

void append_hex_escape(std::string& out, std::uint32_t code_point) {
    constexpr std::array<char, 16> kHexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                                                 '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    out.append("\\u");
    out.push_back(kHexDigits.at((code_point >> 12U) & 0xFU));
    out.push_back(kHexDigits.at((code_point >> 8U) & 0xFU));
    out.push_back(kHexDigits.at((code_point >> 4U) & 0xFU));
    out.push_back(kHexDigits.at(code_point & 0xFU));
}

void append_string(std::string& out, std::string_view value) {
    out.push_back('"');
    for (const char raw : value) {
        // Bytes >= 0x80 are UTF-8 continuation or lead bytes and are copied
        // through untouched — all three languages agree on UTF-8, and they
        // would not agree on when to escape. The cast keeps the comparisons
        // below from depending on whether char is signed on this platform.
        const auto byte = static_cast<unsigned char>(raw);
        switch (byte) {
            case '"':
                out.append("\\\"");
                break;
            case '\\':
                out.append("\\\\");
                break;
            case '\b':
                out.append("\\b");
                break;
            case '\f':
                out.append("\\f");
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\r':
                out.append("\\r");
                break;
            case '\t':
                out.append("\\t");
                break;
            default:
                if (byte < 0x20U) {
                    // No short escape exists; a raw control byte in the journal
                    // is a byte-level divergence waiting to happen between the
                    // three writers.
                    append_hex_escape(out, byte);
                } else {
                    out.push_back(raw);
                }
                break;
        }
    }
    out.push_back('"');
}

/// Appends `code_point` to `out` as UTF-8.
///
/// Hand-rolled rather than reached for from a library: the journal's only
/// non-ASCII path is \uXXXX unescaping, and pulling in a converter for four
/// branches would be a dependency the byte contract does not need.
void append_utf8(std::string& out, std::uint32_t code_point) {
    if (code_point < 0x80U) {
        out.push_back(static_cast<char>(code_point));
    } else if (code_point < 0x800U) {
        out.push_back(static_cast<char>(0xC0U | (code_point >> 6U)));
        out.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    } else if (code_point < 0x10000U) {
        out.push_back(static_cast<char>(0xE0U | (code_point >> 12U)));
        out.push_back(static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    } else {
        out.push_back(static_cast<char>(0xF0U | (code_point >> 18U)));
        out.push_back(static_cast<char>(0x80U | ((code_point >> 12U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    }
}

/// Recursive-descent parser for the restricted grammar the journal uses.
///
/// Accepting only what the format actually uses keeps the fuzz surface small
/// and makes every rejection specific. Every read goes through peek()/advance(),
/// which bounds-check — this parser is a fuzz target, so an out-of-range index
/// would be the exact defect the polyglot gate exists to catch.
class Parser {
public:
    Parser(std::string_view src, std::size_t line_number) : src_(src), line_number_(line_number) {}

    Result<JournalEvent, MalformedJournal> parse_event() {
        skip_whitespace();
        if (auto status = expect('{'); !status) {
            return Failure{status.error()};
        }

        std::optional<std::map<std::string, std::string>> fields;
        std::optional<std::uint64_t> seq;
        std::optional<std::string> type;

        skip_whitespace();
        if (pos_ >= src_.size()) {
            return Failure{fail("unexpected end of line")};
        }
        if (src_[pos_] != '}') {
            while (true) {
                skip_whitespace();
                auto key = parse_string();
                if (!key) {
                    return Failure{key.error()};
                }
                skip_whitespace();
                if (auto status = expect(':'); !status) {
                    return Failure{status.error()};
                }
                skip_whitespace();

                if (key.value() == kKeyFields) {
                    if (fields.has_value()) {
                        return Failure{duplicate(kKeyFields)};
                    }
                    auto parsed = parse_fields();
                    if (!parsed) {
                        return Failure{parsed.error()};
                    }
                    fields = parsed.value();
                } else if (key.value() == kKeySeq) {
                    if (seq.has_value()) {
                        return Failure{duplicate(kKeySeq)};
                    }
                    auto parsed = parse_non_negative_u64();
                    if (!parsed) {
                        return Failure{parsed.error()};
                    }
                    seq = parsed.value();
                } else if (key.value() == kKeyType) {
                    if (type.has_value()) {
                        return Failure{duplicate(kKeyType)};
                    }
                    auto parsed = parse_string();
                    if (!parsed) {
                        return Failure{parsed.error()};
                    }
                    type = parsed.value();
                } else {
                    return Failure{fail("unknown key \"" + key.value() + "\"")};
                }

                skip_whitespace();
                if (pos_ < src_.size() && src_[pos_] == ',') {
                    ++pos_;
                    continue;
                }
                break;
            }
        }

        if (auto status = expect('}'); !status) {
            return Failure{status.error()};
        }
        skip_whitespace();
        if (pos_ != src_.size()) {
            return Failure{fail("trailing content after the object")};
        }

        if (!fields.has_value()) {
            return Failure{missing(kKeyFields)};
        }
        if (!seq.has_value()) {
            return Failure{missing(kKeySeq)};
        }
        if (!type.has_value()) {
            return Failure{missing(kKeyType)};
        }

        return JournalEvent{*seq, std::move(*type), std::move(*fields)};
    }

private:
    Result<std::map<std::string, std::string>, MalformedJournal> parse_fields() {
        std::map<std::string, std::string> fields;
        if (auto status = expect('{'); !status) {
            return Failure{status.error()};
        }
        skip_whitespace();
        if (pos_ < src_.size() && src_[pos_] == '}') {
            ++pos_;
            return fields;
        }
        while (true) {
            skip_whitespace();
            auto key = parse_string();
            if (!key) {
                return Failure{key.error()};
            }
            skip_whitespace();
            if (auto status = expect(':'); !status) {
                return Failure{status.error()};
            }
            skip_whitespace();
            if (pos_ >= src_.size() || src_[pos_] != '"') {
                return Failure{fail("field \"" + key.value() + "\" must be a string")};
            }
            auto value = parse_string();
            if (!value) {
                return Failure{value.error()};
            }
            if (!fields.emplace(key.value(), value.value()).second) {
                return Failure{fail("duplicate field \"" + key.value() + "\"")};
            }
            skip_whitespace();
            if (pos_ < src_.size() && src_[pos_] == ',') {
                ++pos_;
                continue;
            }
            break;
        }
        if (auto status = expect('}'); !status) {
            return Failure{status.error()};
        }
        return fields;
    }

    Result<std::string, MalformedJournal> parse_string() {
        if (auto status = expect('"'); !status) {
            return Failure{status.error()};
        }
        std::string out;
        while (true) {
            if (pos_ >= src_.size()) {
                return Failure{fail("unterminated string")};
            }
            const char raw = src_[pos_++];
            const auto byte = static_cast<unsigned char>(raw);
            if (raw == '"') {
                return out;
            }
            if (raw != '\\') {
                if (byte < 0x20U) {
                    return Failure{fail("raw control character in string")};
                }
                out.push_back(raw);
                continue;
            }
            if (pos_ >= src_.size()) {
                return Failure{fail("unterminated escape")};
            }
            const char esc = src_[pos_++];
            switch (esc) {
                case '"':
                    out.push_back('"');
                    break;
                case '\\':
                    out.push_back('\\');
                    break;
                case '/':
                    out.push_back('/');
                    break;
                case 'b':
                    out.push_back('\b');
                    break;
                case 'f':
                    out.push_back('\f');
                    break;
                case 'n':
                    out.push_back('\n');
                    break;
                case 'r':
                    out.push_back('\r');
                    break;
                case 't':
                    out.push_back('\t');
                    break;
                case 'u': {
                    auto code_point = parse_unicode_escape();
                    if (!code_point) {
                        return Failure{code_point.error()};
                    }
                    append_utf8(out, code_point.value());
                    break;
                }
                default:
                    return Failure{fail(std::string("invalid escape \"\\") + esc + "\"")};
            }
        }
    }

    Result<std::uint32_t, MalformedJournal> parse_unicode_escape() {
        if (pos_ + 4U > src_.size()) {
            return Failure{fail("truncated unicode escape")};
        }
        std::uint32_t value = 0;
        for (std::size_t i = 0; i < 4U; ++i) {
            const char c = src_[pos_ + i];
            std::uint32_t digit = 0;
            if (c >= '0' && c <= '9') {
                digit = static_cast<std::uint32_t>(c - '0');
            } else if (c >= 'a' && c <= 'f') {
                digit = static_cast<std::uint32_t>(c - 'a') + 10U;
            } else if (c >= 'A' && c <= 'F') {
                digit = static_cast<std::uint32_t>(c - 'A') + 10U;
            } else {
                return Failure{fail("invalid unicode escape")};
            }
            value = (value * 16U) + digit;
        }
        pos_ += 4U;
        // Surrogate halves are legal JSON escapes but not Unicode scalar
        // values. Rust's char cannot hold one, so accepting it here would put a
        // different byte sequence in the C++ journal than in the Rust journal —
        // exactly the divergence the conformance gate exists to catch.
        if (value >= 0xD800U && value <= 0xDFFFU) {
            return Failure{fail("unicode escape is not a scalar value")};
        }
        return value;
    }

    Result<std::uint64_t, MalformedJournal> parse_non_negative_u64() {
        const std::size_t start = pos_;
        while (pos_ < src_.size() && src_[pos_] >= '0' && src_[pos_] <= '9') {
            ++pos_;
        }
        if (pos_ == start) {
            return Failure{fail("expected a non-negative integer for \"" + std::string(kKeySeq) + "\"")};
        }
        std::uint64_t value = 0;
        for (std::size_t i = start; i < pos_; ++i) {
            const auto digit = static_cast<std::uint64_t>(src_[i] - '0');
            // Overflow must be a rejection, not a wrap: a wrapped sequence
            // number is a journal that replays in a different order.
            if (value > ((UINT64_MAX - digit) / 10U)) {
                return Failure{fail("sequence number out of range")};
            }
            value = (value * 10U) + digit;
        }
        return value;
    }

    Status<MalformedJournal> expect(char expected) {
        if (pos_ >= src_.size() || src_[pos_] != expected) {
            return Failure{fail(std::string("expected '") + expected + "' at offset " +
                                std::to_string(pos_))};
        }
        ++pos_;
        return {};
    }

    void skip_whitespace() {
        while (pos_ < src_.size() &&
               (src_[pos_] == ' ' || src_[pos_] == '\t' || src_[pos_] == '\r' ||
                src_[pos_] == '\n')) {
            ++pos_;
        }
    }

    [[nodiscard]] MalformedJournal fail(std::string message) const {
        return MalformedJournal{line_number_, std::move(message)};
    }

    [[nodiscard]] MalformedJournal duplicate(std::string_view key) const {
        return fail("duplicate key \"" + std::string(key) + "\"");
    }

    [[nodiscard]] MalformedJournal missing(std::string_view key) const {
        return fail("missing key \"" + std::string(key) + "\"");
    }

    std::string_view src_;
    std::size_t line_number_;
    std::size_t pos_{0};
};

}  // namespace

std::string encode(const JournalEvent& event) {
    std::string out;
    out.append("{\"").append(kKeyFields).append("\":{");
    bool first = true;
    for (const auto& [key, value] : event.fields) {
        if (!first) {
            out.push_back(',');
        }
        first = false;
        append_string(out, key);
        out.push_back(':');
        append_string(out, value);
    }
    out.append("},\"").append(kKeySeq).append("\":").append(std::to_string(event.seq));
    out.append(",\"").append(kKeyType).append("\":");
    append_string(out, event.type);
    out.push_back('}');
    return out;
}

Result<JournalEvent, MalformedJournal> decode(std::string_view line, std::size_t line_number) {
    Parser parser(line, line_number);
    return parser.parse_event();
}

Result<std::vector<JournalEvent>, MalformedJournal> read_journal(
    const std::filesystem::path& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        return Failure{MalformedJournal{0, "cannot open " + path.string()}};
    }
    const std::string raw((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());

    std::vector<JournalEvent> events;
    std::size_t line_number = 0;
    std::size_t start = 0;
    while (start <= raw.size()) {
        const std::size_t end = raw.find('\n', start);
        const std::size_t stop = (end == std::string::npos) ? raw.size() : end;
        const std::string_view line(raw.data() + start, stop - start);
        ++line_number;

        const bool blank =
            line.find_first_not_of(" \t\r") == std::string_view::npos;
        if (!blank) {
            auto parsed = decode(line, line_number);
            if (!parsed) {
                return Failure{parsed.error()};
            }
            events.push_back(parsed.value());
        }

        if (end == std::string::npos) {
            break;
        }
        start = end + 1U;
    }
    return events;
}

Status<MalformedJournal> write_journal(const std::filesystem::path& path,
                                       const std::vector<JournalEvent>& events) {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) {
        return Failure{MalformedJournal{0, "cannot open " + path.string() + " for writing"}};
    }
    for (const auto& event : events) {
        out << encode(event) << '\n';
    }
    out.flush();
    if (!out) {
        return Failure{MalformedJournal{0, "write failed for " + path.string()}};
    }
    return {};
}

}  // namespace ems::core
