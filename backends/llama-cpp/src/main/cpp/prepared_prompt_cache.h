#pragma once

#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

namespace local_llm {

class PreparedPromptCache final {
public:
    void store(std::string prompt, std::vector<std::int32_t> tokens);
    std::optional<std::vector<std::int32_t>> take(const std::string& prompt);
    void clear();
    bool has_value() const;

private:
    struct Entry final {
        std::string prompt;
        std::vector<std::int32_t> tokens;
    };

    mutable std::mutex mutex_;
    std::optional<Entry> entry_;
};

}  // namespace local_llm
