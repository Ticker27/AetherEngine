#pragma once

/*
 * Minimal host-build shim for <jni.h>.
 *
 * The Android build uses the NDK's real jni.h. Host-side native tests compile
 * aether-native sources (which include <jni.h> through bridge/engine_bridge.h)
 * without a JDK or NDK, so this file declares only the small subset of the JNI
 * surface those translation units reference. It is never packaged into the APK
 * and must not be used to build code that actually calls into a JVM.
 */

#include <cstdint>

#define JNI_VERSION_1_6 0x00010006
#define JNI_OK 0
#define JNI_EDETACHED (-2)

typedef int32_t jint;

struct JNIEnv;

struct JavaVM {
    jint GetEnv(void** env, jint version);
    jint AttachCurrentThread(JNIEnv** env, void* args);
};

// Inline no-op definitions: host tests never attach to a JVM, but linking
// engine_bridge.cpp requires symbols for the calls it contains.
inline jint JavaVM::GetEnv(void**, jint) { return JNI_EDETACHED; }
inline jint JavaVM::AttachCurrentThread(JNIEnv**, void*) { return JNI_EDETACHED; }
