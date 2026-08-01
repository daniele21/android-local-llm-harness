#pragma once

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

class NativeCancellationRegistry {
public:
    std::shared_ptr<std::atomic_bool> begin(const std::string& request_id) {
        if (request_id.empty()) {
            return {};
        }
        std::lock_guard<std::mutex> lock(mutex_);
        if (requests_.find(request_id) != requests_.end()) {
            return {};
        }
        auto signal = std::make_shared<std::atomic_bool>(false);
        requests_.emplace(request_id, signal);
        return signal;
    }

    bool cancel(const std::string& request_id) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto iterator = requests_.find(request_id);
        if (iterator == requests_.end()) {
            return false;
        }
        iterator->second->store(true, std::memory_order_release);
        return true;
    }

    bool finish(const std::string& request_id) {
        std::lock_guard<std::mutex> lock(mutex_);
        return requests_.erase(request_id) == 1;
    }

    std::size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return requests_.size();
    }

    bool empty() const {
        return size() == 0;
    }

private:
    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::shared_ptr<std::atomic_bool>> requests_;
};
