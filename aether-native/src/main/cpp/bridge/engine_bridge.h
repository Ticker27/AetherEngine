#pragma once

#include <jni.h>
#include <string>
#include <mutex>

namespace aether {
namespace bridge {

/**
 * EngineBridge — holds JavaVM and provides helpers for JNI callbacks.
 * Keeps global reference to Native class for future callbacks.
 */
class EngineBridge {
public:
    static EngineBridge& Instance();

    EngineBridge(const EngineBridge&) = delete;
    EngineBridge& operator=(const EngineBridge&) = delete;

    void SetJavaVM(JavaVM* vm);
    JavaVM* GetJavaVM() const;

    JNIEnv* GetEnv() const;
    bool IsAttached() const;

    std::string GetVersion() const;

private:
    EngineBridge();
    ~EngineBridge() = default;

    mutable std::mutex mutex_;
    JavaVM* javaVm_{nullptr};
    static constexpr const char* kVersion = "aether-dev-0.2.0";
};

} // namespace bridge
} // namespace aether
