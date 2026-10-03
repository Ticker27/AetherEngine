#include "native_registry.h"
#include "../common/logging.h"
#include "../runtime/runtime_state.h"
#include "../bridge/engine_bridge.h"
#include "../platform/thread_dispatcher.h"
#include <string>

namespace {

using namespace aether;

// Lifecycle bindings — 3 methods group (initialize, shutdown included, future: start)
jboolean nativeInitialize(JNIEnv* env, jobject /*thiz*/) {
    AETHER_LOGI("nativeInitialize() called");
    try {
        bool ok = runtime::RuntimeStateManager::Instance().Initialize();
        if (ok) {
            platform::ThreadDispatcher::Instance().Start();
            AETHER_LOGI("Runtime initialized successfully");
        } else {
            AETHER_LOGW("Runtime initialize rejected — already initialized");
            // Return true if already initialized (idempotent success for callers that check state)
            auto state = runtime::RuntimeStateManager::Instance().GetState();
            if (state == runtime::RuntimeState::Initialized ||
                state == runtime::RuntimeState::Running) {
                return JNI_TRUE;
            }
        }
        return ok ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        AETHER_LOGE("nativeInitialize exception");
        return JNI_FALSE;
    }
}

void nativeShutdown(JNIEnv* env, jobject /*thiz*/) {
    AETHER_LOGI("nativeShutdown() called");
    try {
        runtime::RuntimeStateManager::Instance().Shutdown();
        platform::ThreadDispatcher::Instance().Stop();
        AETHER_LOGI("Runtime shutdown completed");
    } catch (...) {
        AETHER_LOGE("nativeShutdown exception");
    }
}

// Runtime bindings — 3 methods
jstring nativeGetVersion(JNIEnv* env, jobject /*thiz*/) {
    try {
        std::string version = bridge::EngineBridge::Instance().GetVersion();
        if (version.empty()) version = "aether-dev";
        return env->NewStringUTF(version.c_str());
    } catch (...) {
        // Never return nullptr — contract requires non-null
        return env->NewStringUTF("aether-dev-unknown");
    }
}

jstring nativeRuntimeState(JNIEnv* env, jobject /*thiz*/) {
    try {
        std::string state = runtime::RuntimeStateManager::Instance().GetStateString();
        if (state.empty()) state = "new";
        return env->NewStringUTF(state.c_str());
    } catch (...) {
        // Never return nullptr
        return env->NewStringUTF("new");
    }
}

// Current registration table for com.aether.host.bridge.Native.
// Keep this table in sync with the compiled Kotlin declaration and JNI descriptors.
JNINativeMethod kMethods[] = {
    // Lifecycle bindings
    {"initialize", "()Z", reinterpret_cast<void*>(nativeInitialize)},
    {"shutdown", "()V", reinterpret_cast<void*>(nativeShutdown)},

    // Runtime bindings
    {"getVersion", "()Ljava/lang/String;", reinterpret_cast<void*>(nativeGetVersion)},
    {"runtimeState", "()Ljava/lang/String;", reinterpret_cast<void*>(nativeRuntimeState)},
};

constexpr char kNativeClass[] = "com/aether/host/bridge/Native";

} // namespace

bool RegisterNativeMethods(JNIEnv* env) {
    if (!env) {
        AETHER_LOGE("RegisterNativeMethods: env is null");
        return false;
    }

    jclass clazz = env->FindClass(kNativeClass);
    if (clazz == nullptr) {
        AETHER_LOGE("FindClass failed for com/aether/host/bridge/Native — check class path uses '/'");
        // Exception already pending — clear for logging
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    const jint methodCount = static_cast<jint>(sizeof(kMethods) / sizeof(kMethods[0]));
    AETHER_LOGI("Registering native methods");

    const jint result = env->RegisterNatives(clazz, kMethods, methodCount);

    env->DeleteLocalRef(clazz);

    if (result != JNI_OK) {
        AETHER_LOGE("RegisterNatives failed — check method names and JNI signatures match Kotlin");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    AETHER_LOGI("RegisterNatives success");
    return true;
}
