#include "native_cancellation_registry.h"

#include <atomic>
#include <iostream>
#include <thread>

namespace {

bool require(bool condition, const char* expression, int line) {
    if (!condition) {
        std::cerr << "Assertion failed at line " << line << ": " << expression << '\n';
    }
    return condition;
}

#define REQUIRE(expression) \
    do { \
        if (!require((expression), #expression, __LINE__)) { \
            return false; \
        } \
    } while (false)

bool test_lifecycle_and_duplicate_detection() {
    NativeCancellationRegistry registry;
    REQUIRE(registry.empty());
    REQUIRE(!registry.begin(""));

    const auto signal = registry.begin("request-a");
    REQUIRE(signal);
    REQUIRE(!signal->load(std::memory_order_acquire));
    REQUIRE(!registry.begin("request-a"));
    REQUIRE(registry.size() == 1);

    REQUIRE(registry.cancel("request-a"));
    REQUIRE(signal->load(std::memory_order_acquire));
    REQUIRE(!registry.cancel("missing"));
    REQUIRE(registry.finish("request-a"));
    REQUIRE(!registry.finish("request-a"));
    REQUIRE(registry.empty());
    return true;
}

bool test_cross_thread_cancellation() {
    NativeCancellationRegistry registry;
    const auto signal = registry.begin("request-b");
    REQUIRE(signal);

    std::thread cancellation([&registry]() {
        registry.cancel("request-b");
    });
    cancellation.join();

    REQUIRE(signal->load(std::memory_order_acquire));
    REQUIRE(registry.finish("request-b"));
    REQUIRE(registry.empty());
    return true;
}

}  // namespace

int main() {
    if (!test_lifecycle_and_duplicate_detection()) {
        return 1;
    }
    if (!test_cross_thread_cancellation()) {
        return 1;
    }
    std::cout << "All native cancellation registry tests passed\n";
    return 0;
}
