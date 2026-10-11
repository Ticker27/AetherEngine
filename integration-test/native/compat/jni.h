#pragma once

/*
 * Minimal host-build shim for <jni.h>.
 *
 * The Android build uses the NDK's real jni.h. Host-side native tests compile
 * aether-native sources (which include <jni.h> through bridge/engine_bridge.h)
 * without a JDK or NDK, so this file declares only the small subset of the JNI
 * surface those translation units reference. It is never packaged into the APK
 * and must not be used to build code that actually calls into a JVM.
 *
 * The subset covers the JNI registry as well as the runtime/bridge units, so the
 * `native_registry.cpp` translation unit is compile-checked on the host. That is
 * the point: the Kotlin facade and the C++ registration table can drift, and the
 * only symptom of that drift is a LinkageError on a device. Compiling the table
 * here catches signature-shape mistakes before a device run.
 */

#include <cstdint>

#define JNI_VERSION_1_6 0x00010006
#define JNI_OK 0
#define JNI_ERR (-1)
#define JNI_EDETACHED (-2)
#define JNI_FALSE 0
#define JNI_TRUE 1
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL

typedef int32_t jint;
typedef uint8_t jboolean;
typedef int32_t jsize;

struct _jobject {};
struct _jclass : _jobject {};
struct _jstring : _jobject {};
struct _jthrowable : _jobject {};

typedef _jobject* jobject;
typedef _jclass* jclass;
typedef _jstring* jstring;
typedef _jthrowable* jthrowable;

/** Mirrors the NDK's JNINativeMethod layout closely enough for a compile check. */
struct JNINativeMethod {
    const char* name;
    const char* signature;
    void* fnPtr;
};

struct JNIEnv;

struct JavaVM {
    jint GetEnv(void** env, jint version);
    jint AttachCurrentThread(JNIEnv** env, void* args);
};

/**
 * Inline no-op stubs. Host tests never attach to a JVM, but linking the
 * translation units that contain these calls still requires the symbols.
 * Every method returns a benign default rather than aborting, so a host-side
 * test that accidentally reaches one fails its own assertion instead of crashing.
 */
struct JNIEnv {
    jclass FindClass(const char* name);
    jint RegisterNatives(jclass clazz, const JNINativeMethod* methods, jint count);
    void DeleteLocalRef(jobject ref);
    jboolean ExceptionCheck();
    void ExceptionDescribe();
    void ExceptionClear();
    const char* GetStringUTFChars(jstring string, jboolean* isCopy);
    void ReleaseStringUTFChars(jstring string, const char* utf);
    jstring NewStringUTF(const char* utf);
};

inline jclass JNIEnv::FindClass(const char*) { return nullptr; }
inline jint JNIEnv::RegisterNatives(jclass, const JNINativeMethod*, jint) { return JNI_ERR; }
inline void JNIEnv::DeleteLocalRef(jobject) {}
inline jboolean JNIEnv::ExceptionCheck() { return JNI_FALSE; }
inline void JNIEnv::ExceptionDescribe() {}
inline void JNIEnv::ExceptionClear() {}
inline const char* JNIEnv::GetStringUTFChars(jstring, jboolean*) { return ""; }
inline void JNIEnv::ReleaseStringUTFChars(jstring, const char*) {}
inline jstring JNIEnv::NewStringUTF(const char*) { return nullptr; }

inline jint JavaVM::GetEnv(void**, jint) { return JNI_EDETACHED; }
inline jint JavaVM::AttachCurrentThread(JNIEnv**, void*) { return JNI_EDETACHED; }
