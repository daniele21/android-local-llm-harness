#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <vector>

std::size_t utf8_complete_prefix_size(const std::string& value, std::size_t limit);

std::optional<std::size_t> earliest_stop_position(
    const std::string& value,
    const std::vector<std::string>& stop_sequences
);
