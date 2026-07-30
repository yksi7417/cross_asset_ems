// ems-slice --input <journal> --output <journal> [--seed <n>]
//
// A pure function from an input journal to an output journal. No network, no
// clock, no filesystem beyond those two paths. The Java and Rust binaries
// accept the same arguments and must produce byte-identical output — see
// conformance/README.md.
//
// Exit codes: 0 success, 1 the input journal could not be read, 2 a usage
// error. These are part of the contract: the harness uses them to tell "this
// implementation rejected the input" apart from "this implementation was
// invoked wrongly".

#include <charconv>
#include <cstdint>
#include <filesystem>
#include <iostream>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <system_error>
#include <vector>

#include "ems_core/ids.hpp"
#include "ems_core/journal.hpp"
#include "ems_it/slice_runner.hpp"

namespace {

constexpr int kExitOk = 0;
constexpr int kExitInputError = 1;
constexpr int kExitUsage = 2;

constexpr std::string_view kUsage =
    "usage: ems-slice --input <journal> --output <journal> [--seed <n>]\n"
    "\n"
    "  --input   input event journal (JSONL)\n"
    "  --output  output event journal (JSONL), overwritten\n"
    "  --seed    identifier generator seed, default 0\n"
    "\n"
    "A pure function from input journal to output journal. See conformance/README.md.\n";

struct Args {
    std::filesystem::path input;
    std::filesystem::path output;
    std::uint64_t seed{0};
};

int usage(std::string_view message) {
    if (!message.empty()) {
        std::cerr << "ems-slice: " << message << '\n';
    }
    std::cerr << kUsage;
    return kExitUsage;
}

/// STUDY: span-at-boundaries
///
/// std::span<const char* const> rather than (int argc, char** argv): the
/// boundary carries its own length, so no loop in this function can walk off
/// the end of the array by reading one argument too many.
std::optional<Args> parse_args(std::span<const char* const> argv, std::string& error) {
    Args args;
    bool have_input = false;
    bool have_output = false;

    for (std::size_t i = 0; i < argv.size(); ++i) {
        const std::string_view arg(argv[i]);
        auto next = [&](std::string_view what) -> std::optional<std::string_view> {
            if (i + 1U >= argv.size()) {
                error = std::string(what);
                return std::nullopt;
            }
            return std::string_view(argv[++i]);
        };

        if (arg == "--input") {
            const auto value = next("--input requires a path");
            if (!value) {
                return std::nullopt;
            }
            args.input = std::filesystem::path(*value);
            have_input = true;
        } else if (arg == "--output") {
            const auto value = next("--output requires a path");
            if (!value) {
                return std::nullopt;
            }
            args.output = std::filesystem::path(*value);
            have_output = true;
        } else if (arg == "--seed") {
            const auto value = next("--seed requires a number");
            if (!value) {
                return std::nullopt;
            }
            if (!value->empty() && value->front() == '-') {
                error = "--seed must not be negative: " + std::string(*value);
                return std::nullopt;
            }
            std::uint64_t seed = 0;
            const auto* begin = value->data();
            const auto* end = begin + value->size();
            const auto [ptr, ec] = std::from_chars(begin, end, seed);
            if (ec != std::errc{} || ptr != end) {
                error = "--seed is not a number: " + std::string(*value);
                return std::nullopt;
            }
            args.seed = seed;
        } else if (arg == "--help" || arg == "-h") {
            error.clear();
            return std::nullopt;
        } else {
            error = "unknown argument: " + std::string(arg);
            return std::nullopt;
        }
    }

    if (!have_input) {
        error = "--input is required";
        return std::nullopt;
    }
    if (!have_output) {
        error = "--output is required";
        return std::nullopt;
    }
    return args;
}

}  // namespace

int main(int argc, char** argv) {
    const std::span<const char* const> arguments(const_cast<const char* const*>(argv) + 1,
                                                 static_cast<std::size_t>(argc > 0 ? argc - 1 : 0));

    std::string error;
    const auto args = parse_args(arguments, error);
    if (!args) {
        return usage(error);
    }

    auto events = ems::core::read_journal(args->input);
    if (!events) {
        // No stack unwind noise: a malformed input journal is a data problem,
        // and the line number in the message is what actually helps.
        std::cerr << "ems-slice: " << events.error().to_string() << '\n';
        return kExitInputError;
    }

    ems::core::DeterministicIds ids{args->seed};
    const auto output = ems::it::run_slice(events.value(), ids);

    if (auto status = ems::core::write_journal(args->output, output); !status) {
        std::cerr << "ems-slice: " << status.error().to_string() << '\n';
        return kExitInputError;
    }
    return kExitOk;
}
