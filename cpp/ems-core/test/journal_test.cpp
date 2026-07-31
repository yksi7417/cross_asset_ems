// Journal codec tests.
//
// Every expectation here is deliberately the same assertion as
// java/ems-core/.../JournalCodecTest.java and rust/ems-core/tests/journal_test.rs.
// Three implementations agreeing on their own unit tests is what makes the
// byte-exact conformance run a formality rather than a surprise.

#include "ems_core/journal.hpp"

#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

// POSIX getpid(). The CI runners and the devcontainer are both Linux; if this
// tree ever targets Windows, swap it for std::this_thread::get_id().
#include <unistd.h>

namespace {

using ems::core::decode;
using ems::core::encode;
using ems::core::JournalEvent;
using ems::core::read_journal;
using ems::core::write_journal;

std::filesystem::path temp_file(const std::string& name) {
    static int counter = 0;
    const auto dir = std::filesystem::temp_directory_path() /
                     ("ems-core-journal-test-" + std::to_string(::getpid()) + "-" +
                      std::to_string(++counter));
    // Process id as well as a counter: a fixed name is reused across runs, so
    // a previous run's output file was still present and a test asserting
    // "this file must not exist yet" failed on a file it did not create.
    std::filesystem::remove_all(dir);
    std::filesystem::create_directories(dir);
    return dir / name;
}

std::string read_file(const std::filesystem::path& path) {
    std::ifstream in(path, std::ios::binary);
    std::ostringstream buffer;
    buffer << in.rdbuf();
    return buffer.str();
}

JournalEvent event(std::uint64_t seq, const std::string& type,
                   std::map<std::string, std::string> fields = {}) {
    return JournalEvent{seq, type, std::move(fields)};
}

TEST(Journal, WritesTopLevelAndFieldKeysInLexicographicOrder) {
    const auto path = temp_file("out.jsonl");
    ASSERT_TRUE(write_journal(path, {event(1, "OrderNew", {{"qty", "100"}, {"account", "ACC1"}})}));

    EXPECT_EQ(read_file(path),
              "{\"fields\":{\"account\":\"ACC1\",\"qty\":\"100\"},\"seq\":1,\"type\":\"OrderNew\"}\n");
}

TEST(Journal, EveryLineEndsWithNewlineIncludingTheLast) {
    const auto path = temp_file("out.jsonl");
    ASSERT_TRUE(write_journal(path, {event(1, "A"), event(2, "B")}));

    EXPECT_EQ(read_file(path),
              "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n{\"fields\":{},\"seq\":2,\"type\":\"B\"}\n");
}

TEST(Journal, RoundTripsWithoutLoss) {
    const auto path = temp_file("rt.jsonl");
    const std::vector<JournalEvent> events = {
        event(1, "OrderNew", {{"figi", "BBG000B9XRY4"}, {"price", "1250000"}}),
        event(2, "OrderAccepted", {{"orderId", "ORD-0000000001"}})};
    ASSERT_TRUE(write_journal(path, events));

    const auto read = read_journal(path);
    ASSERT_TRUE(read.has_value()) << read.error().to_string();
    EXPECT_EQ(read.value(), events);
}

TEST(Journal, EscapesQuotesBackslashesAndControlCharacters) {
    const auto path = temp_file("esc.jsonl");
    const std::string raw = std::string("a\"b\\c\nd\te") + '\x01' + "f";
    ASSERT_TRUE(write_journal(path, {event(1, "Note", {{"text", raw}})}));

    EXPECT_EQ(read_file(path),
              "{\"fields\":{\"text\":\"a\\\"b\\\\c\\nd\\te\\u0001f\"},\"seq\":1,\"type\":\"Note\"}\n");

    const auto read = read_journal(path);
    ASSERT_TRUE(read.has_value()) << read.error().to_string();
    EXPECT_EQ(read.value().at(0).fields.at("text"), raw);
}

TEST(Journal, NonAsciiIsWrittenAsUtf8NotEscaped) {
    const auto path = temp_file("utf8.jsonl");
    ASSERT_TRUE(write_journal(path, {event(1, "Note", {{"text", "café — ☕"}})}));

    EXPECT_EQ(read_file(path), "{\"fields\":{\"text\":\"café — ☕\"},\"seq\":1,\"type\":\"Note\"}\n");

    const auto read = read_journal(path);
    ASSERT_TRUE(read.has_value()) << read.error().to_string();
    EXPECT_EQ(read.value().at(0).fields.at("text"), "café — ☕");
}

TEST(Journal, UnicodeEscapeDecodesToUtf8) {
    // U+2615 HOT BEVERAGE, written by another producer as an escape. Decoding
    // it must yield the same bytes we would have written directly.
    const auto parsed = decode("{\"fields\":{\"t\":\"\\u2615\"},\"seq\":1,\"type\":\"A\"}", 1);
    ASSERT_TRUE(parsed.has_value()) << parsed.error().to_string();
    EXPECT_EQ(parsed.value().fields.at("t"), "☕");
}

TEST(Journal, EmptyJournalRoundTrips) {
    const auto path = temp_file("empty.jsonl");
    ASSERT_TRUE(write_journal(path, {}));

    EXPECT_EQ(read_file(path), "");
    const auto read = read_journal(path);
    ASSERT_TRUE(read.has_value());
    EXPECT_TRUE(read.value().empty());
}

TEST(Journal, BlankLinesAreIgnoredOnRead) {
    const auto path = temp_file("blank.jsonl");
    {
        std::ofstream out(path, std::ios::binary);
        out << "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n\n";
    }

    const auto read = read_journal(path);
    ASSERT_TRUE(read.has_value()) << read.error().to_string();
    EXPECT_EQ(read.value().size(), 1U);
}

TEST(Journal, MalformedLineReportsItsLineNumber) {
    const auto path = temp_file("bad.jsonl");
    {
        std::ofstream out(path, std::ios::binary);
        out << "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\nnot json\n";
    }

    const auto read = read_journal(path);
    ASSERT_FALSE(read.has_value());
    EXPECT_EQ(read.error().line, 2U);
}

TEST(Journal, UnknownTopLevelKeyIsRejected) {
    const auto parsed = decode("{\"fields\":{},\"seq\":1,\"type\":\"A\",\"extra\":\"x\"}", 1);
    ASSERT_FALSE(parsed.has_value());
    EXPECT_NE(parsed.error().message.find("extra"), std::string::npos);
}

TEST(Journal, MissingRequiredKeyIsRejected) {
    const auto parsed = decode("{\"fields\":{},\"seq\":1}", 1);
    ASSERT_FALSE(parsed.has_value());
    EXPECT_NE(parsed.error().message.find("type"), std::string::npos);
}

TEST(Journal, DuplicateTopLevelKeyIsRejected) {
    const auto parsed = decode("{\"fields\":{},\"seq\":1,\"seq\":2,\"type\":\"A\"}", 1);
    ASSERT_FALSE(parsed.has_value());
    EXPECT_NE(parsed.error().message.find("duplicate"), std::string::npos);
}

TEST(Journal, NonStringFieldValueIsRejected) {
    EXPECT_FALSE(decode("{\"fields\":{\"qty\":100},\"seq\":1,\"type\":\"A\"}", 1).has_value());
}

TEST(Journal, TrailingContentAfterTheObjectIsRejected) {
    EXPECT_FALSE(decode("{\"fields\":{},\"seq\":1,\"type\":\"A\"} junk", 1).has_value());
}

TEST(Journal, NegativeSequenceIsRejected) {
    EXPECT_FALSE(decode("{\"fields\":{},\"seq\":-1,\"type\":\"A\"}", 1).has_value());
}

TEST(Journal, SequenceOverflowIsRejectedRatherThanWrapped) {
    // A wrapped sequence number is a journal that replays in a different order.
    EXPECT_FALSE(
        decode("{\"fields\":{},\"seq\":99999999999999999999999,\"type\":\"A\"}", 1).has_value());
}

TEST(Journal, LoneSurrogateEscapeIsRejected) {
    // Legal JSON, but not a Unicode scalar value — Rust's char cannot hold one,
    // so accepting it here would diverge from the Rust journal.
    const auto parsed = decode("{\"fields\":{},\"seq\":1,\"type\":\"\\ud800\"}", 1);
    ASSERT_FALSE(parsed.has_value());
    EXPECT_NE(parsed.error().message.find("scalar value"), std::string::npos);
}

TEST(Journal, HostileInputNeverCrashes) {
    // The parser is a fuzz target. Every one of these must be a clean rejection,
    // and under ASan/UBSan this test is also the out-of-bounds check.
    const std::vector<std::string> cases = {"",
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
                                            "{\"fields\":{},\"seq\":1,\"type\":\"\\u\"}",
                                            "{\"fields\":{},\"seq\":1,\"type\":\"\\uZZZZ\"}",
                                            "{\"fields\":{},\"seq\":1,\"type\":\"\\q\"}",
                                            "{\"fields\":{},\"seq\":1,\"type\":\"é",
                                            std::string(1, '\0'),
                                            "[]",
                                            "null"};
    for (const auto& input : cases) {
        EXPECT_FALSE(decode(input, 1).has_value()) << "expected rejection for: " << input;
    }
}

TEST(Journal, VeryLongInputIsHandledWithoutCrash) {
    const std::string long_value(100000, 'x');
    const auto parsed =
        decode("{\"fields\":{\"a\":\"" + long_value + "\"},\"seq\":1,\"type\":\"A\"}", 1);
    ASSERT_TRUE(parsed.has_value()) << parsed.error().to_string();
    EXPECT_EQ(parsed.value().fields.at("a").size(), 100000U);
}

TEST(Journal, EncodeMatchesTheAgreedByteLayout) {
    EXPECT_EQ(encode(event(7, "Fill", {{"qty", "50"}})),
              "{\"fields\":{\"qty\":\"50\"},\"seq\":7,\"type\":\"Fill\"}");
}

}  // namespace
