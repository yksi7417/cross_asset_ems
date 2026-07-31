#pragma once

// STUDY: expected-without-exceptions
//
// A minimal stand-in for std::expected, which is C++23 and this tree targets
// C++20 (cpp/CMakeLists.txt). See 70_concepts/idioms/expected-without-exceptions.md
// for why the alternatives — exceptions, std::optional plus an out-parameter,
// an error-code return — were all rejected for the journal parser.

#include <cassert>
#include <string>
#include <utility>
#include <variant>

namespace ems::core {

/// Marker for the error alternative, so Result<T, E> can hold T == E.
template <typename E>
struct Failure {
    E error;
};

template <typename E>
Failure(E) -> Failure<E>;

/// Either a value or an error. Never both, never neither.
///
/// [[nodiscard]] is load-bearing rather than decorative: with -Werror, a
/// dropped Result is a build failure. That is the whole reason this type beats
/// an error-code return, where ignoring the code compiles silently.
template <typename T, typename E>
class [[nodiscard]] Result {
public:
    // NOLINTNEXTLINE(google-explicit-constructor,hicpp-explicit-conversions)
    // Implicit on purpose: `return value;` at a call site reads better than
    // `return Result<T, E>{value};`, and the type is unambiguous.
    Result(T value) : storage_(std::in_place_index<0>, std::move(value)) {}

    // NOLINTNEXTLINE(google-explicit-constructor,hicpp-explicit-conversions)
    Result(Failure<E> failure) : storage_(std::in_place_index<1>, std::move(failure.error)) {}

    [[nodiscard]] bool has_value() const noexcept { return storage_.index() == 0; }
    explicit operator bool() const noexcept { return has_value(); }

    [[nodiscard]] const T& value() const {
        assert(has_value() && "Result::value() on an error");
        return std::get<0>(storage_);
    }

    [[nodiscard]] T& value() {
        assert(has_value() && "Result::value() on an error");
        return std::get<0>(storage_);
    }

    [[nodiscard]] const E& error() const {
        assert(!has_value() && "Result::error() on a value");
        return std::get<1>(storage_);
    }

private:
    std::variant<T, E> storage_;
};

/// Result specialisation for operations that produce no value.
template <typename E>
class [[nodiscard]] Status {
public:
    Status() = default;

    // NOLINTNEXTLINE(google-explicit-constructor,hicpp-explicit-conversions)
    Status(Failure<E> failure) : error_(std::move(failure.error)), ok_(false) {}

    [[nodiscard]] bool ok() const noexcept { return ok_; }
    explicit operator bool() const noexcept { return ok_; }

    [[nodiscard]] const E& error() const {
        assert(!ok_ && "Status::error() on success");
        return error_;
    }

private:
    E error_{};
    bool ok_{true};
};

}  // namespace ems::core
