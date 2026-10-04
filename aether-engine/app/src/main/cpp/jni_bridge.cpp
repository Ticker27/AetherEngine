#include <jni.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AetherJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void stub_void() { LOGI("stub_void called"); }

// signature ตรงกับ Native.kt
static void n_ac(JNIEnv*, jobject, jobject, jobject) { stub_void(); }
static void n_aior(JNIEnv*, jobject, jstring, jstring) { stub_void(); }
static void n_awl(JNIEnv*, jobject, jstring) { stub_void(); }
static jboolean n_chl(JNIEnv*, jobject, jbyteArray) { return JNI_FALSE; }
static jbyteArray n_djp(JNIEnv* env, jobject, jint) { return env->NewByteArray(0); }
static void n_eio(JNIEnv*, jobject) { stub_void(); }
static void n_i(JNIEnv*, jobject, jint) { stub_void(); }
static void n_ic(JNIEnv*, jobject, jobject) { stub_void(); }
static jstring n_ilil(JNIEnv* env, jobject, jint) { return env->NewStringUTF(""); }
static void n_jpo(JNIEnv*, jobject, jobject, jobject, jobject) { stub_void(); }
static void n_update(JNIEnv*, jobject, jobject, jobject) { stub_void(); }

static const JNINativeMethod kMethods[] = {
    {(char*)"ac",     (char*)"(Ljava/lang/Object;Ljava/lang/Object;)V",                          (void*)n_ac},
    {(char*)"aior",   (char*)"(Ljava/lang/String;Ljava/lang/String;)V",                          (void*)n_aior},
    {(char*)"awl",    (char*)"(Ljava/lang/String;)V",                                             (void*)n_awl},
    {(char*)"chl",    (char*)"([B)Z",                                                             (void*)n_chl},
    {(char*)"djp",    (char*)"(I)[B",                                                             (void*)n_djp},
    {(char*)"eio",    (char*)"()V",                                                               (void*)n_eio},
    {(char*)"i",      (char*)"(I)V",                                                              (void*)n_i},
    {(char*)"ic",     (char*)"(Landroid/content/Context;)V",                                      (void*)n_ic},
    {(char*)"ilil",   (char*)"(I)Ljava/lang/String;",                                             (void*)n_ilil},
    {(char*)"jpo",    (char*)"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",         (void*)n_jpo},
    {(char*)"update", (char*)"(Ljava/lang/Object;Ljava/lang/reflect/Method;)V",                   (void*)n_update},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass clazz = env->FindClass("com/aether/helper/Native");
    if (!clazz) { LOGI("FindClass failed"); return JNI_ERR; }

    jint rc = env->RegisterNatives(clazz, kMethods, 11);
    LOGI("RegisterNatives rc=%d entries=11", rc);

    // log fnPtr + section (hex address เท่านั้น)
    for (int idx = 0; idx < 11; ++idx) {
        LOGI("native[%02d] name=%s fnPtr=%p", idx, kMethods[idx].name, kMethods[idx].fnPtr);
    }

    // PHASE-2 hook slot (ยังไม่ implement)
    // runtime_patch_apply(env, clazz);

    return JNI_VERSION_1_6;
}
