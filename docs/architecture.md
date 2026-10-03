# AetherEngine Architecture

## Paradigm Shift

From "observe from outside" to "control from inside" — In-Process Virtualization.

Solves latency but increases complexity of Layer 1 (The Host) due to:
- Android ART Hidden API restrictions
- SELinux policies
- Namespace isolation

## Module Graph

```
app (android-host)
  │
  ├── aether-runtime (future: Bootstrap, Registry, Lifecycle)
  │     │
  │     ├── aether-host (HostInitializer, VirtualActivity, AetherFlutterHost)
  │     ├── aether-loader (DynamicApkLoader — Phase 1 of Virtualization)
  │     └── aether-bridge (Native.kt)
  │           │
  │           └── aether-native (libaether.so)
  │                 │
  │                 ├── jni/ (JNI_OnLoad, RegisterNatives)
  │                 ├── bridge/ (engine_bridge, message_bridge)
  │                 ├── runtime/ (runtime_state state machine)
  │                 ├── platform/ (thread_dispatcher)
  │                 └── common/ (logging, result)
  │
  ├── flutter-app (add-to-app, MethodChannel "aether/runtime")
  └── aether-common / aether-config / aether-testing
```

Dependency is one-way, no cycles.

## Build Pipeline

- `classes.dex` bootstraps and loads `libaether.so`
- `libaether.so` is native subsystem — no direct dependency on Flutter internals
- `libflutter.so` and `libapp.so` managed by Flutter embedding pipeline

## Flutter add-to-app

Use `FlutterJNI` lifecycle, not custom FlutterEngine creation.
`AetherFlutterHost` manages:
- FlutterEngine creation / warmup
- Dart entrypoint (main / mainAether)
- MethodChannel registration
- Lifecycle attach/detach

## JNI Bridge

Flow:
```
Dart -> MethodChannel -> Kotlin -> JNI -> C++
```

Never call JNI from Dart directly.

## Roadmap

1. Phase 0: CI/CD & Monorepo Foundation
2. Phase 1: Bootstrap (System.loadLibrary, JNI_OnLoad, RegisterNatives)
3. Phase 2: Native runtime state (NEW -> INITIALIZED -> RUNNING -> STOPPING -> STOPPED)
4. Phase 3: Flutter host (FlutterEngine, MethodChannel)
5. Phase 4: Message bridge protocol
6. Phase 5: Assets & application layer
7. Phase 6: Testing & diagnostics (arm64-v8a)
