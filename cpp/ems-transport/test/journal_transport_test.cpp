// Mirrored by java/ems-transport/.../JournalTransportTest.java and the inline
// tests in rust/ems-transport/src/lib.rs.

#include "ems_transport/journal_transport.hpp"

#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>

// POSIX getpid(). The CI runners and the devcontainer are both Linux; if this
// tree ever targets Windows, swap it for std::this_thread::get_id().
#include <unistd.h>

namespace {

using ems::core::JournalEvent;
using ems::core::read_journal;
using ems::core::write_journal;
using ems::transport::JournalTransport;

std::filesystem::path temp_dir() {
    static int counter = 0;
    const auto dir = std::filesystem::temp_directory_path() /
                     ("ems-transport-test-" + std::to_string(::getpid()) + "-" +
                      std::to_string(++counter));
    // Process id as well as a counter: a fixed name is reused across runs, so
    // a previous run's output file was still present and a test asserting
    // "this file must not exist yet" failed on a file it did not create.
    std::filesystem::remove_all(dir);
    std::filesystem::create_directories(dir);
    return dir;
}

JournalEvent event(std::uint64_t seq, const std::string& type) {
    return JournalEvent{seq, type, {}};
}

TEST(JournalTransport, DrainReturnsTheInputJournalOnceAndThenEmpty) {
    const auto dir = temp_dir();
    ASSERT_TRUE(write_journal(dir / "in.jsonl", {event(1, "OrderNew"), event(2, "Heartbeat")}));
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");

    const auto first = transport.drain();
    ASSERT_TRUE(first.has_value()) << first.error().to_string();
    EXPECT_EQ(first.value().size(), 2U);

    // Draining twice is a programming error; replaying would silently duplicate
    // an order rather than surfacing it.
    const auto second = transport.drain();
    ASSERT_TRUE(second.has_value());
    EXPECT_TRUE(second.value().empty());
}

TEST(JournalTransport, PublishedEventsAppearInOrderAfterFlush) {
    const auto dir = temp_dir();
    ASSERT_TRUE(write_journal(dir / "in.jsonl", {}));
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");
    transport.publish(event(1, "A"));
    transport.publish(event(2, "B"));
    ASSERT_TRUE(transport.flush());

    const auto written = read_journal(dir / "out.jsonl");
    ASSERT_TRUE(written.has_value()) << written.error().to_string();
    ASSERT_EQ(written.value().size(), 2U);
    EXPECT_EQ(written.value().at(0).type, "A");
    EXPECT_EQ(written.value().at(1).type, "B");
}

TEST(JournalTransport, PublishBeforeFlushWritesNothing) {
    const auto dir = temp_dir();
    ASSERT_TRUE(write_journal(dir / "in.jsonl", {}));
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");
    transport.publish(event(1, "A"));

    // A half-written journal is indistinguishable from a legitimately short
    // one, which would send a reader hunting for a logic bug that is not there.
    EXPECT_FALSE(std::filesystem::exists(dir / "out.jsonl"));
}

TEST(JournalTransport, FlushWithNothingPublishedWritesAnEmptyFile) {
    const auto dir = temp_dir();
    ASSERT_TRUE(write_journal(dir / "in.jsonl", {}));
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");
    ASSERT_TRUE(transport.flush());

    std::ifstream in(dir / "out.jsonl", std::ios::binary);
    std::ostringstream buffer;
    buffer << in.rdbuf();
    EXPECT_EQ(buffer.str(), "");
}

TEST(JournalTransport, FlushIsIdempotentRatherThanAppending) {
    const auto dir = temp_dir();
    ASSERT_TRUE(write_journal(dir / "in.jsonl", {}));
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");
    transport.publish(event(1, "A"));
    ASSERT_TRUE(transport.flush());
    ASSERT_TRUE(transport.flush());

    // Appending on the second flush would duplicate the whole journal — and the
    // conformance differ would report it as an "extra line" ten lines later.
    const auto written = read_journal(dir / "out.jsonl");
    ASSERT_TRUE(written.has_value());
    EXPECT_EQ(written.value().size(), 1U);
}

TEST(JournalTransport, MalformedInputSurfacesAsAMalformedJournalError) {
    const auto dir = temp_dir();
    {
        std::ofstream out(dir / "in.jsonl", std::ios::binary);
        out << "not json\n";
    }
    JournalTransport transport(dir / "in.jsonl", dir / "out.jsonl");

    const auto drained = transport.drain();
    ASSERT_FALSE(drained.has_value());
    EXPECT_EQ(drained.error().line, 1U);
}

TEST(JournalTransport, MissingInputSurfacesAsAnError) {
    const auto dir = temp_dir();
    JournalTransport transport(dir / "nope.jsonl", dir / "out.jsonl");

    EXPECT_FALSE(transport.drain().has_value());
}

}  // namespace
