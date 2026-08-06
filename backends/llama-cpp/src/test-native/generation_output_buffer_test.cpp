#include "generation_output_buffer.h"

#include <cassert>
#include <string>
#include <vector>

int main() {
    const std::string text = "A\xC3\xA8\xF0\x9F\x98\x80Z";
    assert(utf8_complete_prefix_size(text, 1) == 1);
    assert(utf8_complete_prefix_size(text, 2) == 1);
    assert(utf8_complete_prefix_size(text, 3) == 3);
    assert(utf8_complete_prefix_size(text, 6) == 3);
    assert(utf8_complete_prefix_size(text, 7) == 7);
    assert(utf8_complete_prefix_size(text, text.size()) == text.size());

    const std::vector<std::string> stops = {"later", "stop"};
    assert(earliest_stop_position("a stop before later", stops).value() == 2);
    assert(!earliest_stop_position("no terminator", stops).has_value());
    return 0;
}
