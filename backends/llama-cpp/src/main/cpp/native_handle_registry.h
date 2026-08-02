#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>

template <typename T>
class NativeHandleRegistry {
public:
    std::int64_t add(std::shared_ptr<T> value) {
        if (!value) {
            return 0;
        }
        const std::int64_t handle = next_handle_.fetch_add(1, std::memory_order_relaxed);
        std::lock_guard<std::mutex> lock(mutex_);
        values_.emplace(handle, std::move(value));
        return handle;
    }

    std::shared_ptr<T> get(std::int64_t handle) const {
        if (handle <= 0) {
            return {};
        }
        std::lock_guard<std::mutex> lock(mutex_);
        const auto iterator = values_.find(handle);
        return iterator == values_.end() ? std::shared_ptr<T>{} : iterator->second;
    }

    bool remove(std::int64_t handle) {
        if (handle <= 0) {
            return false;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        return values_.erase(handle) == 1;
    }

    std::size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return values_.size();
    }

    bool empty() const {
        return size() == 0;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        values_.clear();
    }

private:
    mutable std::mutex mutex_;
    std::unordered_map<std::int64_t, std::shared_ptr<T>> values_;
    std::atomic<std::int64_t> next_handle_{1};
};
