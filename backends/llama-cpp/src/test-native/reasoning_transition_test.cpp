#include "reasoning_transition.h"

#include <cassert>
#include <string>

int main() {
    const std::string marker = "</think>";

    {
        ReasoningTransitionTracker tracker(marker);
        tracker.observe("analysis");
        assert(!tracker.closed());
        tracker.observe("</think>answer");
        assert(tracker.closed());
    }

    for (std::size_t boundary = 1; boundary < marker.size(); ++boundary) {
        ReasoningTransitionTracker tracker(marker);
        tracker.observe("analysis" + marker.substr(0, boundary));
        assert(!tracker.closed());
        tracker.observe(marker.substr(boundary) + "answer");
        assert(tracker.closed());
    }

    {
        ReasoningTransitionTracker tracker(marker);
        tracker.observe("analysis without marker");
        tracker.observe(" and more analysis");
        assert(!tracker.closed());
    }

    {
        ReasoningTransitionTracker tracker(marker);
        tracker.observe("prefix </think> suffix </think>");
        assert(tracker.closed());
        tracker.observe("ignored after close");
        assert(tracker.closed());
    }

    return 0;
}
