# AetherEngine

In-Process Virtualization Engine — Paradigm shift from "observe from outside" to "control from inside".

Current stage: **Phase 1 Bootstrap — Android → System.loadLibrary("aether") → JNI_OnLoad → RegisterNatives**

## Structure (Phase 1)

```
aether-engine/
├── android-host/          # Android host (Kotlin)
│   ├── AetherApplication.kt
│   ├── MainActivity.kt
│   ├── AetherFlutterHost.kt (Flutter add-to-app, MethodChannel "aether/runtime")
│   └── Native.kt (System.loadLibrary + 4 externals)
│
├── aether-native/         # Native subsystem (libaether.so)
│   ├── CMakeLists.txt
│   └── src/main/cpp/
│       ├── jni/ (jni_onload.cpp, native_registry.cpp)
│       ├── bridge/ (engine_bridge, message_bridge)
│       ├── runtime/ (runtime_state: NEW->INITIALIZED->RUNNING->STOPPING->STOPPED)
│       ├── platform/ (thread_dispatcher)
│       └── common/ (logging.h, result.h)
│
├── flutter-app/           # Flutter add-to-app module
│   ├── lib/channels/aether_channel.dart (MethodChannel)
│   ├── lib/services/native_service.dart
│   └── lib/features/home/home_page.dart
│
├── integration-test/
│   ├── android/ (NativeContractTest)
│   ├── flutter/ (aether_channel_test)
│   └── native/ (runtime_state_test)
│
└── docs/
    ├── architecture.md
    ├── jni-contract.md
    └── lifecycle.md
```

## Build

```bash
./gradlew :android-host:assembleDebug
./gradlew :android-host:assembleRelease
./gradlew :android-host:test
```

ABI: `arm64-v8a` only

## JNI Contract

- `Native.kt` declares 4 methods: `initialize()`, `shutdown()`, `getVersion()`, `runtimeState()`
- `native_registry.cpp` registers via `RegisterNatives()` in `JNI_OnLoad()`
- Never return null for String methods
- Method count via `sizeof(kMethods)/sizeof(kMethods[0])`
- Class path uses `/`: `com/aether/host/Native`

## Flutter Channel

```
Dart → MethodChannel("aether/runtime") → Kotlin (AetherFlutterHost) → JNI → libaether.so
```

Methods:
- `version` -> `Native.getVersion()`
- `state` -> `Native.runtimeState()` (code challenge implemented)
- `initialize` -> `Native.initialize()`
- `shutdown` -> `Native.shutdown()`
- `ping` -> echo + state

## Roadmap

- [x] Phase 0: CI skeleton
- [x] Phase 1: Bootstrap (Native.kt + JNI_OnLoad + RegisterNatives + getVersion + runtimeState)
- [ ] Phase 2: Native runtime state machine guards + thread_dispatcher
- [ ] Phase 3: Flutter host (FlutterEngine lifecycle)
- [ ] Phase 4: Message bridge protocol {requestId, method, payload}
- [ ] Phase 5: Assets (flutter_assets, libapp.so, libflutter.so)
- [ ] Phase 6: Testing & diagnostics (ABI arm64-v8a)
