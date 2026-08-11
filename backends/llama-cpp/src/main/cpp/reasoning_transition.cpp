#include "reasoning_transition.h"

#include <algorithm>
#include <stdexcept>
#include <utility>

ReasoningTransitionTracker::ReasoningTransitionTracker(std::string close_marker)
    : close_marker_(std::move(close_marker)) {
    if (close_marker_.empty()) {
        throw std::invalid_argument("Reasoning close marker must not be empty");
    }
}

void ReasoningTransitionTracker::observe(const std::string& text) {
    if (closed_ || text.empty()) {
        return;
    }

    const std::string candidate = suffix_ + text;
    if (candidate.find(close_marker_) != std::string::npos) {
        closed_ = true;
        suffix_.clear();
        return;
    }

    const std::size_t retained = std::min(
        candidate.size(),
        close_marker_.size() > 1 ? close_marker_.size() - 1 : 0U
    );
    suffix_ = retained == 0 ? std::string{} : candidate.substr(candidate.size() - retained);
}

bool ReasoningTransitionTracker::closed() const {
    return closed_;
}

const std::string& ReasoningTransitionTracker::close_marker() const {
    return close_marker_;
}
