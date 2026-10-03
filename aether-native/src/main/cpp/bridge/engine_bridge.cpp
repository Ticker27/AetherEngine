#include "engine_bridge.h"
#include "../common/logging.h"

namespace aether {
namespace bridge {

EngineBridge::EngineBridge() = default;

EngineBridge& EngineBridge::Instance() {
    static EngineBridge instance;
    return instance;
}

void EngineBridge::SetJavaVM(JavaVM* vm) {
    std::lock_guard<std::mutex> lock(mutex_);
    javaVm_ = vm;
    AETHER_LOGI("EngineBridge JavaVM set");
}

JavaVM* EngineBridge::GetJavaVM() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return javaVm_;
}

JNIEnv* EngineBridge::GetEnv() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!javaVm_) return nullptr;
    JNIEnv* env = nullptr;
    jint result = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_OK) {
        return env;
    }
    // Try attach current thread
    if (result == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            return env;
        }
    }
    return nullptr;
}

bool EngineBridge::IsAttached() const {
    return GetEnv() != nullptr;
}

std::string EngineBridge::GetVersion() const {
    return std::string(kVersion);
}

} // namespace bridge
} // namespace aether
