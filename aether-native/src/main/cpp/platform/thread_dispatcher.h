#pragma once

#include <functional>
#include <mutex>
#include <queue>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <vector>

namespace aether {
namespace platform {

using Task = std::function<void()>;

/**
 * Simple thread dispatcher — single background thread with task queue.
 * Production-ready: graceful shutdown, exception safety.
 */
class ThreadDispatcher {
public:
    static ThreadDispatcher& Instance();

    ThreadDispatcher(const ThreadDispatcher&) = delete;
    ThreadDispatcher& operator=(const ThreadDispatcher&) = delete;

    bool Start();
    void Stop();
    bool IsRunning() const;

    void Post(Task task);
    void PostDelayed(Task task, int delayMs);

private:
    ThreadDispatcher();
    ~ThreadDispatcher();

    void Loop();

    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::queue<Task> tasks_;
    std::thread worker_;
    std::atomic<bool> running_{false};
    std::atomic<bool> stopRequested_{false};
};

} // namespace platform
} // namespace aether
