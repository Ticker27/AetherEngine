# JNI Contract

This page records the JNI contract that is implemented in the repository and the safe way to extend it toward the API described in the target architecture. It intentionally does not invent names or signatures for methods that are not in source.

## Current Kotlin declaration

Source: `android-host/src/main/kotlin/com/aether/host/bridge/Native.kt`

```kotlin
package com.aether.host.bridge

object Native {
    init { System.loadLibrary("aether") }

    @JvmStatic external fun initialize(): Boolean
    @JvmStatic external fun shutdown()
    @JvmStatic external fun getVersion(): String
    @JvmStatic external fun runtimeState(): String
}
```

This loads `libaether.so` and registers the class path **`com/aether/host/bridge/Native`**. Do not use the reported APK-analysis package `com/aether/helper/Native` in C++ unless that exact Kotlin declaration is added and verified.

## Current registered methods

Source: `aether-native/src/main/cpp/jni/native_registry.cpp`

| Kotlin method | JNI descriptor | Native result |
| --- | --- | --- |
| `initialize()` | `()Z` | `jboolean` |
| `shutdown()` | `()V` | `void` |
| `getVersion()` | `()Ljava/lang/String;` | non-null `String` |
| `runtimeState()` | `()Ljava/lang/String;` | non-null `String` |

The current implementation has one `JNINativeMethod` table with four entries and one `RegisterNatives()` call. It does not export per-method `Java_com_...` functions.

## Current load and registration sequence

```text
System.loadLibrary("aether")
  → Android loads libaether.so
  → JNI_OnLoad(JavaVM*, ...)
  → GetEnv(JNI_VERSION_1_6)
  → save JavaVM for bridge callbacks
  → RegisterNativeMethods(env)
  → FindClass("com/aether/host/bridge/Native")
  → RegisterNatives(class, kMethods, methodCount)
```

`methodCount` is derived from the table with `sizeof(kMethods) / sizeof(kMethods[0])`. Registration failure makes `JNI_OnLoad()` return `JNI_ERR`, causing library loading to fail. This repository's `JNI_OnLoad()` does **not** decode method names or build a runtime table dynamically; the four current entries are a static C++ table.

## Target API from the supplied architecture notes

The notes describe a different/expanded APK contract:

| Target class path | Reported native methods | Status in this repository |
| --- | ---: | --- |
| `com/aether/helper/Native` | 11 | Not present; current JNI facade is `com/aether/host/bridge/Native` with 4 methods |
| `com/aether/helper/flagger` | 2 | Not present as a JNI class; `com/aether/host/virtualization/flags/flagger` is a separate pure-Kotlin feature-flag utility |
| **Total reported** | **13** | Target binary contract only; not implemented here |

The notes also sketch registration groups of 1, 2, and 10 methods. The current source does not contain those groups. The group sizes and their class ownership must be verified against the real DEX/APK; they cannot be inferred from the current Git source. Do not add placeholder methods simply to make the count appear to match.

A separate [Snake Engine reference bundle](reference/snake-engine/README.md) contains reports about `com.snake.helper.Native` and `com.snake.helper.flagger`. The [static extraction log](reference/snake-engine/ARCHIVE_EXTRACTION_LOG.md) records the pre-removal inspection of the `com.snake` 2.2.6 package snapshot and the hash mismatch with older notes. These Snake-specific class paths are not declarations for `com.aether.helper.*`, Aether's host bridge, or 8 Ball Pool. Call-site/runtime reports cite additional inputs not in the archive; do not copy their JNI names or descriptors into this project without direct, target-specific evidence.

## Extension design for multiple classes/tables

Once the exact Kotlin/DEX declarations and descriptors are known, keep one registration table per class or deliberate API group and make each registration failure explicit. A suitable shape is:

```cpp
bool RegisterNativeMethods(JNIEnv* env) {
    return RegisterClassMethods(env, kNativeClass, kNativeMethods,
                                CountOf(kNativeMethods)) &&
           RegisterClassMethods(env, kFlaggerClass, kFlaggerMethods,
                                CountOf(kFlaggerMethods));
}
```

`RegisterClassMethods` should validate the environment, call `FindClass()` with the verified slash-separated binary name, check pending exceptions, call `RegisterNatives()`, release the local class reference, and report failure. If analysis confirms multiple independent registration groups for one class, represent those groups explicitly and ensure they do not conflict; otherwise prefer one table per class for simpler validation.

Before implementing the target API:

1. Record every exact class binary name, method name, static/instance form, and JNI descriptor from the DEX/API contract.
2. Add matching Kotlin declarations and C++ implementations in the same change.
3. Update `JNI_OnLoad()` registration and native contract tests together.
4. Verify the final APK's DEX and `libaether.so` exports/registration behavior on each supported ABI.

## JNI descriptor reminders

- `()Z` → Boolean
- `()V` → Unit/void
- `()Ljava/lang/String;` → String
- `(Ljava/lang/String;)V` → String argument, void result
- `(Ljava/lang/String;)Ljava/lang/String;` → String argument and result

For `@JvmStatic` declarations, verify the generated JVM method form as well as the Kotlin source signature. JNI descriptors are based on the compiled class, not just the visual Kotlin declaration.

## Safety and diagnostics

- Use slash-separated class paths in `FindClass()`, never dotted package names.
- Match method names, descriptors, and static/instance calling conventions exactly.
- Compute table sizes; do not hardcode counts.
- Never return `nullptr` for non-null Kotlin `String` results; the current methods have fallbacks.
- Check `ExceptionCheck()` after class lookup and registration. Preserve enough diagnostic information to identify the failed class/table.
- Delete local references after successful class lookup.
- Keep JNI callbacks and runtime-state access thread-safe.
