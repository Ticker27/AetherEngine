// Aether native lane — host-side smoke demo.
//
// Exercises the production C++ sources that do not need a JVM:
//   runtime/runtime_state     — NEW -> INITIALIZED -> RUNNING -> STOPPED state machine
//   platform/thread_dispatcher — background worker queue used by the runtime
//   bridge/message_bridge     — JSON request/response protocol behind the Dart channel
//   bridge/engine_bridge      — version holder / JavaVM facade (compiled with the
//                               host-test jni shim; no JVM calls are made here)
//
// Built and run by scripts/native_tests.sh on any machine with a C++17 compiler,
// and by CI. It proves the native lane's logic and concurrency work on the host;
// it does not prove on-device guest execution.

#include "runtime/runtime_state.h"
#include "platform/thread_dispatcher.h"
#include "bridge/message_bridge.h"
#include "bridge/engine_bridge.h"

#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <thread>

namespace {

int g_failures = 0;

void Check(bool condition, const char* what) {
    if (condition) {
        std::printf("[PASS] %s\n", what);
    } else {
        std::printf("[FAIL] %s\n", what);
        ++g_failures;
    }
}

bool Contains(const std::string& haystack, const std::string& needle) {
    return haystack.find(needle) != std::string::npos;
}

bool WaitFor(const std::atomic<int>& counter, int target, std::chrono::milliseconds timeout) {
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    while (counter.load(std::memory_order_relaxed) < target &&
           std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    return counter.load(std::memory_order_relaxed) >= target;
}

}  // namespace

int main() {
    using aether::bridge::MessageBridge;
    std::printf("=== Aether native lane smoke demo (host build) ===\n\n");

    // 1. Runtime state machine -------------------------------------------------
    std::printf("-- runtime state machine --\n");
    aether::runtime::RuntimeStateManager& state = aether::runtime::RuntimeStateManager::Instance();
    state.ForceResetForTesting();
    Check(state.Initialize(), "initialize from NEW");
    Check(state.StartRunning(), "start RUNNING from INITIALIZED");
    Check(state.GetStateString() == "running", "state string is 'running'");
    Check(!state.Initialize(), "double initialize rejected");
    std::printf("\n");

    // 2. Thread dispatcher -----------------------------------------------------
    std::printf("-- thread dispatcher --\n");
    aether::platform::ThreadDispatcher& dispatcher = aether::platform::ThreadDispatcher::Instance();
    Check(dispatcher.Start(), "dispatcher starts");
    Check(dispatcher.Start(), "dispatcher start is idempotent");

    std::atomic<int> processed{0};
    constexpr int kTasks = 128;
    for (int i = 0; i < kTasks; ++i) {
        dispatcher.Post([&processed] { processed.fetch_add(1, std::memory_order_relaxed); });
    }
    Check(WaitFor(processed, kTasks, std::chrono::milliseconds(5000)),
          "128 queued tasks all processed on the worker");
    std::printf("\n");

    // 3. Message bridge --------------------------------------------------------
    std::printf("-- message bridge --\n");
    MessageBridge& bridge = MessageBridge::Instance();

    const auto status = bridge.Handle({"smoke-1", "runtime.status", "{}"});
    std::printf("       runtime.status  -> %s\n", status.ToJson().c_str());
    Check(status.ok && Contains(status.ToJson(), "\"state\":\"running\""),
          "runtime.status reports running");

    const auto ping = bridge.Handle({"smoke-2", "runtime.ping", "{}"});
    std::printf("       runtime.ping    -> %s\n", ping.ToJson().c_str());
    Check(ping.ok && Contains(ping.ToJson(), "\"pong\":true"), "runtime.ping returns pong");

    const auto version = bridge.Handle({"smoke-3", "runtime.version", "{}"});
    std::printf("       runtime.version -> %s\n", version.ToJson().c_str());
    Check(version.ok && Contains(version.ToJson(), "aether-dev-0.2.0"),
          "runtime.version returns the native version");

    const auto unknown = bridge.Handle({"smoke-4", "guest.launch", "{}"});
    std::printf("       guest.launch    -> %s\n", unknown.ToJson().c_str());
    Check(!unknown.ok && Contains(unknown.ToJson(), "method not found"),
          "unknown method is rejected");
    std::printf("\n");

    // 4. Boot sequence: state + queue + bridge together ------------------------
    std::printf("-- boot sequence (dispatcher + bridge) --\n");
    std::atomic<int> okResponses{0};
    constexpr int kMessages = 100;
    for (int i = 0; i < kMessages; ++i) {
        dispatcher.Post([&bridge, &okResponses, i] {
            const auto response = bridge.Handle({"boot-" + std::to_string(i), "runtime.ping", "{}"});
            if (response.ok) okResponses.fetch_add(1, std::memory_order_relaxed);
        });
    }
    Check(WaitFor(okResponses, kMessages, std::chrono::milliseconds(5000)),
          "100 pipelined bridge calls over the worker queue all succeeded");
    std::printf("\n");

    // 5. Shutdown --------------------------------------------------------------
    std::printf("-- shutdown --\n");
    dispatcher.Stop();
    Check(!dispatcher.IsRunning(), "dispatcher stops");
    Check(state.Shutdown(), "runtime shutdown from RUNNING");
    Check(state.GetStateString() == "stopped", "final state is 'stopped'");
    Check(state.Shutdown(), "shutdown is idempotent");
    std::printf("\n");

    std::printf("=== RESULT: %s (%d failure(s)) ===\n",
                g_failures == 0 ? "PASS" : "FAIL", g_failures);
    return g_failures == 0 ? 0 : 1;
}
