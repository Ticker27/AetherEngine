#pragma once

#include <atomic>
#include <mutex>
#include <string>

namespace aether {
namespace runtime {

enum class RuntimeState {
    New = 0,
    Initialized = 1,
    Running = 2,
    Stopping = 3,
    Stopped = 4
};

inline const char* ToString(RuntimeState state) {
    switch (state) {
        case RuntimeState::New: return "new";
        case RuntimeState::Initialized: return "initialized";
        case RuntimeState::Running: return "running";
        case RuntimeState::Stopping: return "stopping";
        case RuntimeState::Stopped: return "stopped";
        default: return "unknown";
    }
}

inline RuntimeState FromString(const char* str) {
    if (!str) return RuntimeState::New;
    std::string s(str);
    if (s == "new") return RuntimeState::New;
    if (s == "initialized") return RuntimeState::Initialized;
    if (s == "running") return RuntimeState::Running;
    if (s == "stopping") return RuntimeState::Stopping;
    if (s == "stopped") return RuntimeState::Stopped;
    return RuntimeState::New;
}

/**
 * Thread-safe singleton state machine:
 * NEW -> INITIALIZED -> RUNNING -> STOPPING -> STOPPED
 *
 * Guards:
 * - initialize() allowed only from NEW
 * - startRunning() allowed from INITIALIZED
 * - shutdown() allowed from INITIALIZED or RUNNING, transitions to STOPPING then STOPPED
 * - Idempotent shutdown
 */
class RuntimeStateManager {
public:
    static RuntimeStateManager& Instance();

    RuntimeStateManager(const RuntimeStateManager&) = delete;
    RuntimeStateManager& operator=(const RuntimeStateManager&) = delete;

    RuntimeState GetState() const;
    std::string GetStateString() const;

    bool Initialize();
    bool StartRunning();
    bool Shutdown();

    void ForceResetForTesting();

private:
    RuntimeStateManager();
    ~RuntimeStateManager() = default;

    mutable std::mutex mutex_;
    std::atomic<RuntimeState> state_{RuntimeState::New};
};

} // namespace runtime
} // namespace aether
