#include <jni.h>
#include "native_registry.h"
#include "../common/logging.h"
#include "../bridge/engine_bridge.h"

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    AETHER_LOGI("JNI_OnLoad — AetherEngine loading");

    if (!vm) {
        AETHER_LOGE("JNI_OnLoad: JavaVM is null");
        return JNI_ERR;
    }

    JNIEnv* env = nullptr;
    jint getEnvResult = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

    if (getEnvResult != JNI_OK) {
        AETHER_LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    if (!env) {
        AETHER_LOGE("JNI_OnLoad: JNIEnv is null");
        return JNI_ERR;
    }

    // Store JavaVM for later callbacks
    aether::bridge::EngineBridge::Instance().SetJavaVM(vm);

    if (!RegisterNativeMethods(env)) {
        AETHER_LOGE("JNI_OnLoad: RegisterNativeMethods failed — check 3 points: class path '/', signature match, method count via sizeof");
        return JNI_ERR;
    }

    AETHER_LOGI("JNI_OnLoad success — JNI_VERSION_1_6");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* /*reserved*/) {
    AETHER_LOGI("JNI_OnUnload — AetherEngine unloading");
    // Cleanup is handled by runtime shutdown — no forced detach here
    (void)vm;
}
