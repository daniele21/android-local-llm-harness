#include "prepared_prompt_cache.h"

#include <utility>

namespace local_llm {

void PreparedPromptCache::store(std::string prompt, std::vector<std::int32_t> tokens) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (prompt.empty() || tokens.empty()) {
        entry_.reset();
        return;
    }
    entry_ = Entry{std::move(prompt), std::move(tokens)};
}

std::optional<std::vector<std::int32_t>> PreparedPromptCache::take(const std::string& prompt) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!entry_.has_value()) {
        return std::nullopt;
    }

    Entry entry = std::move(entry_.value());
    entry_.reset();
    if (entry.prompt != prompt) {
        return std::nullopt;
    }
    return std::move(entry.tokens);
}

void PreparedPromptCache::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    entry_.reset();
}

bool PreparedPromptCache::has_value() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return entry_.has_value();
}

}  // namespace local_llm
