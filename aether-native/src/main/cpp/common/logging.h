#pragma once

#ifdef __ANDROID__
#include <android/log.h>
#define AETHER_LOG_TAG "AetherNative"
#define AETHER_LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, AETHER_LOG_TAG, __VA_ARGS__)
#define AETHER_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, AETHER_LOG_TAG, __VA_ARGS__)
#define AETHER_LOGI(...) __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG, __VA_ARGS__)
#define AETHER_LOGW(...) __android_log_print(ANDROID_LOG_WARN, AETHER_LOG_TAG, __VA_ARGS__)
#define AETHER_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, AETHER_LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define AETHER_LOGV(...) do { std::printf("[V][AetherNative] "); std::printf(__VA_ARGS__); std::printf("\n"); } while(0)
#define AETHER_LOGD(...) do { std::printf("[D][AetherNative] "); std::printf(__VA_ARGS__); std::printf("\n"); } while(0)
#define AETHER_LOGI(...) do { std::printf("[I][AetherNative] "); std::printf(__VA_ARGS__); std::printf("\n"); } while(0)
#define AETHER_LOGW(...) do { std::printf("[W][AetherNative] "); std::printf(__VA_ARGS__); std::printf("\n"); } while(0)
#define AETHER_LOGE(...) do { std::fprintf(stderr, "[E][AetherNative] "); std::fprintf(stderr, __VA_ARGS__); std::fprintf(stderr, "\n"); } while(0)
#endif

namespace aether {
namespace common {

inline void LogVerbose(const char* msg) { AETHER_LOGV("%s", msg); }
inline void LogDebug(const char* msg) { AETHER_LOGD("%s", msg); }
inline void LogInfo(const char* msg) { AETHER_LOGI("%s", msg); }
inline void LogWarn(const char* msg) { AETHER_LOGW("%s", msg); }
inline void LogError(const char* msg) { AETHER_LOGE("%s", msg); }

} // namespace common
} // namespace aether
