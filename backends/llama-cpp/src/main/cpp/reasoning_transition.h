#pragma once

#include <string>

class ReasoningTransitionTracker final {
public:
    explicit ReasoningTransitionTracker(std::string close_marker);

    void observe(const std::string& text);
    bool closed() const;
    const std::string& close_marker() const;

private:
    std::string close_marker_;
    std::string suffix_;
    bool closed_ = false;
};
