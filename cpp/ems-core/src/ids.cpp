#include "ems_core/ids.hpp"

#include <cstddef>
#include <string>

namespace ems::core {

std::string DeterministicIds::format(const char* prefix, std::uint64_t value) {
    const std::string digits = std::to_string(value);
    std::string out;
    out.reserve(std::string::traits_type::length(prefix) + 1U + (digits.size() < 10U ? 10U : digits.size()));
    out.append(prefix);
    out.push_back('-');
    for (std::size_t i = digits.size(); i < 10U; ++i) {
        out.push_back('0');
    }
    out.append(digits);
    return out;
}

}  // namespace ems::core
