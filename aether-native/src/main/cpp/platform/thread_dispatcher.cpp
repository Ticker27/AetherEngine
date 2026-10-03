#include "thread_dispatcher.h"
#include "../common/logging.h"
#include <chrono>

namespace aether {
namespace platform {

ThreadDispatcher::ThreadDispatcher() = default;

ThreadDispatcher::~ThreadDispatcher() {
    Stop();
}

ThreadDispatcher& ThreadDispatcher::Instance() {
    static ThreadDispatcher instance;
    return instance;
}

bool ThreadDispatcher::Start() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_.load()) {
        return true;
    }
    stopRequested_.store(false);
    try {
        worker_ = std::thread(&ThreadDispatcher::Loop, this);
        running_.store(true);
        AETHER_LOGI("ThreadDispatcher started");
        return true;
    } catch (const std::exception& e) {
        AETHER_LOGE("ThreadDispatcher start failed");
        running_.store(false);
        return false;
    }
}

void ThreadDispatcher::Stop() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!running_.load()) {
            return;
        }
        stopRequested_.store(true);
    }
    cv_.notify_all();
    if (worker_.joinable()) {
        worker_.join();
    }
    running_.store(false);
    AETHER_LOGI("ThreadDispatcher stopped");
}

bool ThreadDispatcher::IsRunning() const {
    return running_.load();
}

void ThreadDispatcher::Post(Task task) {
    if (!task) return;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_.load()) {
            AETHER_LOGW("Post rejected — stopping");
            return;
        }
        tasks_.push(std::move(task));
    }
    cv_.notify_one();
}

void ThreadDispatcher::PostDelayed(Task task, int delayMs) {
    if (!task) return;
    // For simplicity, spawn detached thread for delay — production would use timer queue
    std::thread([this, t = std::move(task), delayMs]() mutable {
        std::this_thread::sleep_for(std::chrono::milliseconds(delayMs));
        Post(std::move(t));
    }).detach();
}

void ThreadDispatcher::Loop() {
    while (true) {
        Task task;
        {
            std::unique_lock<std::mutex> lock(mutex_);
            cv_.wait(lock, [this] { return stopRequested_.load() || !tasks_.empty(); });

            if (stopRequested_.load() && tasks_.empty()) {
                break;
            }

            if (!tasks_.empty()) {
                task = std::move(tasks_.front());
                tasks_.pop();
            }
        }

        if (task) {
            try {
                task();
            } catch (const std::exception& e) {
                AETHER_LOGE("Task threw exception");
            } catch (...) {
                AETHER_LOGE("Task threw unknown exception");
            }
        }
    }
}

} // namespace platform
} // namespace aether
