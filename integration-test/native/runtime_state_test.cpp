#include "../../aether-native/src/main/cpp/runtime/runtime_state.h"
#include <cassert>
#include <iostream>

using namespace aether::runtime;

void test_initial_state() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    assert(RuntimeStateManager::Instance().GetState() == RuntimeState::New);
    assert(RuntimeStateManager::Instance().GetStateString() == "new");
    std::cout << "PASS: initial state\n";
}

void test_initialize() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    bool ok = RuntimeStateManager::Instance().Initialize();
    assert(ok);
    assert(RuntimeStateManager::Instance().GetState() == RuntimeState::Initialized);
    assert(RuntimeStateManager::Instance().GetStateString() == "initialized");
    std::cout << "PASS: initialize\n";
}

void test_double_initialize_rejected() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    RuntimeStateManager::Instance().Initialize();
    bool second = RuntimeStateManager::Instance().Initialize();
    assert(!second); // should reject
    std::cout << "PASS: double initialize rejected\n";
}

void test_shutdown() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    RuntimeStateManager::Instance().Initialize();
    bool ok = RuntimeStateManager::Instance().Shutdown();
    assert(ok);
    assert(RuntimeStateManager::Instance().GetState() == RuntimeState::Stopped);
    std::cout << "PASS: shutdown\n";
}

void test_shutdown_idempotent() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    RuntimeStateManager::Instance().Initialize();
    RuntimeStateManager::Instance().Shutdown();
    bool second = RuntimeStateManager::Instance().Shutdown();
    assert(second);
    std::cout << "PASS: shutdown idempotent\n";
}

void test_running_transition() {
    RuntimeStateManager::Instance().ForceResetForTesting();
    RuntimeStateManager::Instance().Initialize();
    bool ok = RuntimeStateManager::Instance().StartRunning();
    assert(ok);
    assert(RuntimeStateManager::Instance().GetStateString() == "running");
    RuntimeStateManager::Instance().Shutdown();
    std::cout << "PASS: running transition\n";
}

int main() {
    test_initial_state();
    test_initialize();
    test_double_initialize_rejected();
    test_shutdown();
    test_shutdown_idempotent();
    test_running_transition();
    std::cout << "All runtime_state tests passed\n";
    return 0;
}
