#include "prepared_prompt_cache.h"

#include <cassert>
#include <cstdint>
#include <vector>

int main() {
    local_llm::PreparedPromptCache cache;

    assert(!cache.has_value());
    assert(!cache.take("missing").has_value());

    cache.store("prompt-a", {1, 2, 3});
    assert(cache.has_value());
    const auto first = cache.take("prompt-a");
    assert(first.has_value());
    assert((first.value() == std::vector<std::int32_t>{1, 2, 3}));
    assert(!cache.has_value());
    assert(!cache.take("prompt-a").has_value());

    cache.store("prompt-b", {4, 5});
    assert(!cache.take("different-prompt").has_value());
    assert(!cache.has_value());

    cache.store("old", {6});
    cache.store("new", {7, 8});
    assert(!cache.take("old").has_value());
    assert(!cache.has_value());

    cache.store("new", {7, 8});
    const auto replacement = cache.take("new");
    assert(replacement.has_value());
    assert((replacement.value() == std::vector<std::int32_t>{7, 8}));

    cache.store("", {9});
    assert(!cache.has_value());
    cache.store("prompt-c", {});
    assert(!cache.has_value());

    cache.store("prompt-d", {10});
    cache.clear();
    assert(!cache.has_value());

    return 0;
}
