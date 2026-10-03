#include "runtime_state.h"
#include "../common/logging.h"

namespace aether {
namespace runtime {

RuntimeStateManager::RuntimeStateManager() : state_(RuntimeState::New) {
    AETHER_LOGI("RuntimeStateManager created — state=new");
}

RuntimeStateManager& RuntimeStateManager::Instance() {
    static RuntimeStateManager instance;
    return instance;
}

RuntimeState RuntimeStateManager::GetState() const {
    return state_.load(std::memory_order_acquire);
}

std::string RuntimeStateManager::GetStateString() const {
    return std::string(ToString(GetState()));
}

bool RuntimeStateManager::Initialize() {
    std::lock_guard<std::mutex> lock(mutex_);
    RuntimeState current = state_.load(std::memory_order_acquire);

    if (current != RuntimeState::New) {
        AETHER_LOGW("Initialize() rejected — current state is not NEW");
        return false;
    }

    AETHER_LOGI("Runtime transition: new -> initialized");
    state_.store(RuntimeState::Initialized, std::memory_order_release);
    return true;
}

bool RuntimeStateManager::StartRunning() {
    std::lock_guard<std::mutex> lock(mutex_);
    RuntimeState current = state_.load(std::memory_order_acquire);

    if (current != RuntimeState::Initialized) {
        AETHER_LOGW("StartRunning() rejected — must be INITIALIZED");
        return false;
    }

    AETHER_LOGI("Runtime transition: initialized -> running");
    state_.store(RuntimeState::Running, std::memory_order_release);
    return true;
}

bool RuntimeStateManager::Shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    RuntimeState current = state_.load(std::memory_order_acquire);

    if (current == RuntimeState::Stopped || current == RuntimeState::Stopping) {
        AETHER_LOGI("Shutdown() idempotent — already stopping/stopped");
        return true;
    }

    if (current != RuntimeState::Initialized && current != RuntimeState::Running) {
        AETHER_LOGW("Shutdown() rejected — invalid state for shutdown");
        // Allow shutdown from New to Stopped for cleanup path
        if (current == RuntimeState::New) {
            state_.store(RuntimeState::Stopped, std::memory_order_release);
            return true;
        }
        return false;
    }

    AETHER_LOGI("Runtime transition: -> stopping -> stopped");
    state_.store(RuntimeState::Stopping, std::memory_order_release);
    // Simulate cleanup — in real impl, stop threads, release resources here
    state_.store(RuntimeState::Stopped, std::memory_order_release);
    AETHER_LOGI("Runtime stopped");
    return true;
}

void RuntimeStateManager::ForceResetForTesting() {
    std::lock_guard<std::mutex> lock(mutex_);
    AETHER_LOGW("ForceResetForTesting() — state -> new");
    state_.store(RuntimeState::New, std::memory_order_release);
}

} // namespace runtime
} // namespace aether
