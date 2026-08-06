#include "generation_output_buffer.h"

#include <algorithm>

namespace {

std::size_t utf8_sequence_size(unsigned char lead) {
    if ((lead & 0x80U) == 0U) return 1;
    if ((lead & 0xE0U) == 0xC0U) return 2;
    if ((lead & 0xF0U) == 0xE0U) return 3;
    if ((lead & 0xF8U) == 0xF0U) return 4;
    return 0;
}

bool is_continuation(unsigned char value) {
    return (value & 0xC0U) == 0x80U;
}

}  // namespace

std::size_t utf8_complete_prefix_size(const std::string& value, std::size_t limit) {
    const std::size_t bounded_limit = std::min(limit, value.size());
    std::size_t offset = 0;
    while (offset < bounded_limit) {
        const std::size_t sequence_size = utf8_sequence_size(static_cast<unsigned char>(value[offset]));
        if (sequence_size == 0 || offset + sequence_size > bounded_limit) {
            break;
        }
        bool valid = true;
        for (std::size_t index = 1; index < sequence_size; ++index) {
            if (!is_continuation(static_cast<unsigned char>(value[offset + index]))) {
                valid = false;
                break;
            }
        }
        if (!valid) {
            break;
        }
        offset += sequence_size;
    }
    return offset;
}

std::optional<std::size_t> earliest_stop_position(
    const std::string& value,
    const std::vector<std::string>& stop_sequences
) {
    std::optional<std::size_t> earliest;
    for (const std::string& sequence : stop_sequences) {
        const std::size_t position = value.find(sequence);
        if (position != std::string::npos && (!earliest.has_value() || position < earliest.value())) {
            earliest = position;
        }
    }
    return earliest;
}
