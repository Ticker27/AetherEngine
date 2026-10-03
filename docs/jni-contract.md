# JNI Contract

## Kotlin Declaration (Native.kt)

```kotlin
object Native {
    init { System.loadLibrary("aether") }
    external fun initialize(): Boolean
    external fun shutdown()
    external fun getVersion(): String
    external fun runtimeState(): String
}
```

Class path: `com/aether/host/Native` — must use `/`, not `.`

## C++ Registration (native_registry.cpp)

Grouped 13 bindings design (currently 4 implemented):

- Lifecycle: initialize, shutdown, (future: start)
- Runtime: getVersion, runtimeState, (future: isRunning)
- Message: 4 methods (future)
- Diagnostic: 3 methods (future)

```cpp
JNINativeMethod kMethods[] = {
    {"initialize", "()Z", reinterpret_cast<void*>(nativeInitialize)},
    {"shutdown", "()V", reinterpret_cast<void*>(nativeShutdown)},
    {"getVersion", "()Ljava/lang/String;", reinterpret_cast<void*>(nativeGetVersion)},
    {"runtimeState", "()Ljava/lang/String;", reinterpret_cast<void*>(nativeRuntimeState)},
};
```

## JNI Signatures

- `()Z` -> boolean
- `()V` -> void
- `()Ljava/lang/String;` -> String (never null)
- `(Ljava/lang/String;)V` -> void with String param
- Future: `(Ljava/lang/String;)Ljava/lang/String;`

## JNI_OnLoad

```cpp
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (!RegisterNativeMethods(env)) return JNI_ERR;
    return JNI_VERSION_1_6;
}
```

## Debugging Tips

If `RegisterNatives()` fails, check 3 points first:
1. Class path must use `/`: `com/aether/host/Native`
2. JNI signature must match Kotlin declaration exactly
3. Method count must be calculated via `sizeof(kMethods)/sizeof(kMethods[0])`, not hardcoded

Common failures:
- `NoSuchMethodError` -> signature mismatch
- `UnsatisfiedLinkError` at load -> `JNI_OnLoad` returned `JNI_ERR`
- `ClassNotFoundException` -> wrong class path

## Safety

- Never return nullptr for String methods — return "unknown" or "new" as fallback
- Guard state machine with mutex — prevent double initialize
- Use `DeleteLocalRef` after `FindClass`
- Check `ExceptionCheck()` after `FindClass` and `RegisterNatives`
