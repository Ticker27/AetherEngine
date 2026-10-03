# AetherEngine Runtime Architecture

## Purpose

AetherEngine is designed as a Flutter application with a custom native subsystem alongside the Flutter engine. The custom JNI library and Flutter runtime are separate components: `libaether.so` is application-owned native code, `libflutter.so` is the Flutter engine, and `libapp.so` is the Dart application compiled for AOT.

This document separates the **intended runtime architecture** from what this repository currently builds. The Android host now has a structured container foundation (bootstrap, trusted DEX loader, lifecycle proxy shells), but does not yet implement full stock-APK component virtualization or package the Flutter runtime binaries described below.

## Target runtime architecture

```text
                           Android APK
          AndroidManifest.xml + classes.dex (Kotlin/Java)
                              │
                  Application / Activity bootstrap
                    ┌─────────┴─────────┐
                    │                   │
            Custom JNI lane        Flutter lane
                    │                   │
       Kotlin Native facade       FlutterJNI / FlutterEngine
       loadLibrary("aether")              │
                    │             loads libflutter.so
                    ▼                   │
              libaether.so              ▼
          JNI_OnLoad / RegisterNatives  Flutter runtime / Dart VM
                    │                   │
                    ▼             loads/executes app snapshot
          custom native subsystem      libapp.so + flutter_assets
                                        │
                                        ▼
                                  Dart Application
                                ┌───────┼────────┐
                                ▼       ▼        ▼
                                UI     Logic   Services
```

The two native loading paths are parallel, not interchangeable:

```text
Android host ── Native facade / JNI ──> libaether.so ──> custom native subsystem
Android host ── FlutterJNI ───────────> libflutter.so ──> Dart VM ──> libapp.so
```

The Flutter engine loads/runs the Dart application. The custom library does not load `libapp.so`; the two paths communicate only where the application explicitly bridges a platform message through Kotlin.

`libaether.so` should not be described as `libengine.so` unless the CMake target and every `System.loadLibrary` call are deliberately renamed together. In this repository the canonical name is **`aether`**: `System.loadLibrary("aether")` maps to `libaether.so`.

## Four runtime layers

| Layer | Main responsibility | Runtime pieces |
| --- | --- | --- |
| Android host | Starts the process, owns the Activity/Application lifecycle, and connects platform code to native code and Flutter | `AndroidManifest.xml`, compiled Kotlin/Java in `classes.dex`, Android embedding |
| Custom native subsystem | Implements Aether-owned native functions and JNI registration | `libaether.so`, `JNI_OnLoad`, `RegisterNatives`, native runtime/bridges |
| Flutter engine | Runs Dart, renders frames, schedules work, handles semantics, textures, and platform messages | `FlutterJNI`, `FlutterEngine`, `libflutter.so` |
| Dart application | Application UI, logic, services, and Dart-side channel wrappers | Dart source in `flutter-app/`; in an AOT Android build, compiled application code in `libapp.so` plus `flutter_assets/` |

`libapp.so` and `libflutter.so` are different artifacts: one contains the compiled Dart application and the other provides the Flutter runtime. Neither belongs to the Aether C++ target.

## Communication paths

### Direct Kotlin-to-custom-native path

```text
Kotlin Native facade
  → System.loadLibrary("aether")
  → libaether.so / JNI_OnLoad()
  → RegisterNatives()
  → C++ implementation
```

This is the custom JNI path. It does not require Dart and is separate from FlutterJNI.

### Dart-to-Android path

```text
Dart
  ↔ MethodChannel / Flutter platform-message system
  ↔ Flutter engine
  ↔ Kotlin channel handler
  → custom Kotlin JNI facade
  → libaether.so
```

Dart code does not call a JNI function directly. Flutter transports platform messages; Android/Kotlin code handles the channel and may then call the custom JNI facade. The return path follows the same bridge in reverse. Some Dart APIs (for example rendering and frame scheduling) are engine APIs and do not pass through the custom Aether JNI layer.

## Repository-to-runtime mapping

```text
AetherEngine/
├── android-host/                  # Android application / Kotlin host source
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/aether/host/
│           ├── AetherApplication.kt
│           ├── MainActivity.kt
│           ├── AetherFlutterHost.kt
│           ├── bridge/Native.kt    # Current JNI facade: 4 methods
│           ├── bootstrap/HostInitializer.kt
│           └── virtualization/    # Loader, proxy pools, providers, services, browser
├── aether-native/                 # C++ source that builds libaether.so
│   └── src/main/cpp/
│       ├── jni/                   # JNI_OnLoad and native registration
│       ├── bridge/                # Engine/message bridge abstractions
│       ├── runtime/               # Runtime state machine
│       ├── platform/              # Thread dispatcher
│       └── common/                # Logging/result helpers
├── flutter-app/                   # Dart source; source of the future libapp.so
│   ├── lib/                       # main, app, channels, services, features
│   ├── assets/
│   └── pubspec.yaml
├── integration-test/
└── docs/
```

### What the current repository actually produces

| Runtime name | Source/build location | Current status in this repository |
| --- | --- | --- |
| `AndroidManifest.xml` | `android-host/src/main/AndroidManifest.xml` | Present as source |
| `classes.dex` | Android Gradle build of `android-host` | Generated; not checked in |
| `libaether.so` | CMake target `aether` in `aether-native/src/main/cpp/CMakeLists.txt` | Built by the Android host native build |
| `libflutter.so` | Flutter Android embedding/toolchain | Not currently wired into `android-host` Gradle dependencies |
| `libapp.so` | Flutter AOT/release build | Not currently generated or packaged by this repository's Android build |
| `flutter_assets/` | Flutter build output | Source assets are under `flutter-app/assets/`; packaged output is not currently wired into the Android host |

The repository currently contains Flutter/Dart source, but `android-host/build.gradle.kts` intentionally has no Flutter embedding dependency and `AetherFlutterHost` uses reflection with a Phase 1 fallback. Therefore, the runtime picture above is the **target design**, not a claim that the current APK already contains `libflutter.so` and `libapp.so`.

## JNI API: reported target vs current source

The supplied system description identifies classes named `com.aether.helper.Native` and `com.aether.helper.flagger`, with 11 and 2 native methods respectively. Those exact declarations and descriptors are not present in the current Git source.

At the current repository revision:

- The JNI facade is `com.aether.host.bridge.Native`.
- It declares and registers four methods: `initialize`, `shutdown`, `getVersion`, and `runtimeState`.
- `JNI_OnLoad()` calls one registry function, which makes one `RegisterNatives()` call for that class.
- The host now has a pure-Kotlin feature switch object at `com.aether.host.virtualization.flags.flagger`; it is not the reported JNI `com.aether.helper.flagger` class.
- The host-container components are private lifecycle shells; they do not yet launch arbitrary Android components from the guest APK.

Treat the reported 11+2 native API as an APK-analysis target until the actual DEX declarations/signatures are added. See [JNI Contract](jni-contract.md) and [The Host container](host-container.md) for the implementation boundary. This avoids documenting guessed native method names or signatures as implemented facts.

## Recommended implementation sequence

1. Keep the Android host, custom native library, Flutter engine, and Dart app as separate ownership boundaries.
2. Validate the exact custom class names, method names, and JNI descriptors against the DEX/APK before changing the registration tables.
3. Add the custom facade(s) under the verified package paths and register each class/table explicitly from `JNI_OnLoad()`; do not rely on `Java_*` exports.
4. Integrate Flutter using the supported Android add-to-app build path. Let the Flutter embedding package `libflutter.so`, and let the selected Flutter build mode generate/package `libapp.so` and `flutter_assets/`.
5. Add CI checks against the built APK contents and channel contracts so the source layout and packaged runtime stay in sync.

## Naming and packaging rules

- Android's `System.loadLibrary` argument omits both `lib` and `.so`: `"aether"` loads `libaether.so`.
- Keep `libaether.so` (custom native code) separate from Flutter's `libflutter.so` (engine) and `libapp.so` (AOT application).
- Do not commit generated APKs, `.so` files, DEX files, or Flutter build output as source. Build and inspect them as artifacts instead.
- Keep native registration metadata next to the JNI registration code and test every name/signature against its Kotlin declaration.
