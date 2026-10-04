# AetherEngine Runtime Architecture

## Goal

The Android host is a regular, installable Android application. Its runtime keeps four ownership boundaries distinct:

1. Android bootstrap and host components in `classes.dex` plus `AndroidManifest.xml`.
2. Aether-owned custom native code in `libaether.so`.
3. Flutter's engine/runtime in `libflutter.so`.
4. The Dart application in the Flutter module; in release builds its AOT binary is `libapp.so` and its resources are under `assets/flutter_assets/`.

The host APK is installed and launched through Android's normal package manager; it does not require root. This statement does **not** mean that a `DexClassLoader` can run an arbitrary third-party APK as an installed app. Guest components still need Android package/resource/context/task integration, and loaded guest code shares the host UID and process.

## Runtime architecture

```text
                              ANDROID
                                 │
                                 ▼
                       AndroidManifest.xml
                           + classes.dex
                                 │
                  ┌──────────────┴──────────────┐
                  │                             │
                  ▼                             ▼
          Custom Native lane               FlutterJNI lane
       com.aether.host.bridge.Native     FlutterActivity/Engine
          loadLibrary("aether")                │
                  │                       loads "flutter"
                  ▼                             ▼
          ┌───────────────┐              ┌────────────────┐
          │ libaether.so  │─────────────▶│ libflutter.so  │
          └───────────────┘              └────────────────┘
                  │                             │
          custom C++ runtime              Flutter runtime/Dart VM
                                                │
                                                ▼
                                           libapp.so (AOT)
                                                │
                                                ▼
                                         Dart Application
                                       ┌────────┼────────┐
                                       ▼        ▼        ▼
                                      UI      Logic   Services
```

`libaether.so` and `libflutter.so` are separate shared libraries and separate JNI systems. Flutter's runtime loads/runs the Dart application; the custom library does not load `libapp.so`. The two lanes meet only through explicit application code such as a Flutter platform message handled by Kotlin.

The diagram's arrow between native lanes is conceptual coordination, not a direct `dlopen` dependency: the Android host starts both lanes. Flutter's supported embedding owns `FlutterJNI`; Aether's `Native` facade owns calls into `libaether.so`.

## Four layers and packaged files

| Layer | Main responsibility | Files/artifacts |
| --- | --- | --- |
| Android | Bootstrap the host process, declare components, forward framework lifecycle, and connect platform APIs | `AndroidManifest.xml`, Kotlin/Java in `classes.dex`, `AetherApplication`, `MainActivity`, `HostInitializer` |
| Custom Native | Aether-owned native implementation and its JNI registration | `libaether.so`, `JNI_OnLoad`, `RegisterNatives`, C++ runtime/bridges |
| Flutter Engine | Run Dart, render frames, schedule work, handle semantics/textures/platform messages | `FlutterJNI`, `FlutterEngine`, `libflutter.so` |
| Dart Application | UI, application logic, services, assets, and Dart-side channel wrappers | `flutter-app/lib/`; release `libapp.so` and `assets/flutter_assets/` |

The debug APK contains the Flutter debug runtime and Dart debug payload. The release APK is additionally required by CI to contain `libapp.so` (Dart AOT). The custom CMake target only builds `libaether.so`; the Flutter tooling builds and packages its own engine, Dart code, and assets.

### Separate guest APK lane

```text
External 8 Ball Pool APK (not in this repository or host APK)
        │
        ▼
TargetApkContract: com.miniclip.eightballpool / 56.30.0 / 4028
        │
        ▼
GuestApkTrustPolicy: exact identity + configured signer pins
        │
        ▼
DynamicApkLoader → GuestClassLoaderProxy + app-private GuestVirtualFileSystem facade

Guest DEX class loading and explicit guest-relative file I/O are provided. No guest Activity launch, native-library loading, package/resource virtualization, or sandbox is provided.
```

This guest lane is separate from Aether's Dart app, Flutter Engine, and `libaether.so`. The external APK is not a Flutter module and its runtime has not been established from a verified binary. See [the target APK record](target-apk.md) and the distinct [Snake Engine reference bundle](reference/snake-engine/README.md).

## Two JNI paths and the message path

### Aether custom JNI

```text
HostInitializer / AetherRuntimeChannel
        │
        ▼
com.aether.host.bridge.Native
        │ System.loadLibrary("aether")
        ▼
libaether.so → JNI_OnLoad() → RegisterNatives() → C++ implementation
```

The current source declares four known host methods: `initialize()`, `shutdown()`, `getVersion()`, and `runtimeState()`. The native registry uses a `JNINativeMethod` table; the current library has no per-method `Java_*` exports.

### Flutter JNI

```text
MainActivity (FlutterActivity)
        │
        ▼
FlutterEngine / FlutterJNI
        │
        ▼
libflutter.so → Flutter runtime / Dart VM → libapp.so (release)
```

The Flutter embedding owns this lane. Aether Kotlin code does not replace or re-register Flutter's JNI methods.

### Dart ↔ Android platform messages

```text
Dart AetherChannel
   ↕ MethodChannel("aether/runtime")
Flutter engine platform-message transport
   ↕
AetherRuntimeChannel (Kotlin)
   ↕
Native facade → libaether.so
```

Dart does not call Aether's JNI functions directly. The Flutter engine transports a `MethodChannel` message; Kotlin handles it and may call the separate Aether JNI facade. Flutter rendering/frame APIs stay within Flutter's own runtime path. Platform messages can travel in both directions.

## Host APK architecture actually built by this repository

```text
AetherEngine/
├── android-host/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/aether/host/
│           ├── AetherApplication.kt
│           ├── MainActivity.kt               # FlutterActivity; owns Flutter lifecycle
│           ├── bootstrap/
│           │   ├── HostInitializer.kt         # process + host component lifecycle
│           │   └── VirtualActivitySlotRegistry.kt # P0..P3 snapshots; no Activity retention
│           ├── bridge/
│           │   ├── Native.kt                 # custom JNI facade; 4 known methods
│           │   └── AetherRuntimeChannel.kt    # Dart platform-message handler
│           ├── target/TargetApkContract.kt   # external guest identity; no APK binary
│           └── virtualization/
│               ├── filesystem/GuestVirtualFileSystem.kt # explicit app-private file facade
│               ├── loader/                   # trust policy + guest ClassLoader proxy
│               └── activity/                 # VirtualActivity + fixed proxy pools
├── aether-native/src/main/cpp/                 # C++ source for libaether.so
├── flutter-app/                                # Flutter add-to-app module / Dart app
│   ├── lib/                                    # UI, logic, channel, services
│   └── pubspec.yaml
└── scripts/verify_apk_architecture.py          # checks APK binary/asset contents
```

`flutter-app/.android/` is generated by `flutter pub get` for the Android add-to-app integration and is deliberately not checked into Git. The Android host consumes the generated `:flutter` project as a Gradle dependency. CI builds both APK variants and inspects their ZIP entries rather than assuming that source declarations imply packaged binaries.

| Artifact | Producer | CI expectation |
| --- | --- | --- |
| `AndroidManifest.xml`, `classes.dex` | Android Gradle Plugin | Present in debug and release APK |
| `lib/arm64-v8a/libaether.so` | Aether CMake target `aether` | Present in debug and release APK |
| `lib/arm64-v8a/libflutter.so` | Flutter Android embedding | Present in debug and release APK |
| `assets/flutter_assets/` | Flutter build tooling | Present in debug and release APK |
| `lib/arm64-v8a/libapp.so` | Flutter AOT compilation | Required in release APK |

The supported ABI is currently `arm64-v8a` only. The Flutter 3.47 engine requires minimum Android API 24 (Android 7.0), reflected in the host `minSdk`.

## Intended target JNI contract vs verified repository contract

The supplied target architecture describes `com/aether/helper/Native` with 11 native methods and `com/aether/helper/flagger` with 2 native methods. Those class declarations, method names, static/instance forms, and descriptors are not in the repository's source or supplied APK/DEX.

The current implementation instead contains:

- `com.aether.host.bridge.Native` with the four host methods documented above.
- `com.aether.host.virtualization.flags.flagger`, a pure-Kotlin process-local feature-switch object. It is **not** the target native `com/aether/helper/flagger` class.
- One current C++ registration table for the current host facade.

The target counts (11 + 2) must not be presented as already implemented. Before replacing/adding JNI declarations, obtain the target DEX/APK or an exact method/descriptor list and verify it against the compiled Kotlin/Java classes. Do not invent placeholder names or descriptors.

## The Host and arbitrary guest APKs

`DynamicApkLoader` copies a selected APK into private storage, verifies the fixed target identity (`com.miniclip.eightballpool`, version `56.30.0`, code `4028`) and configured signer pins, then creates a guest-first `GuestClassLoaderProxy` and per-APK `GuestVirtualFileSystem` facade. The facade only protects paths accessed through its API; it does not intercept arbitrary Java/native file I/O. `VirtualActivity` relays framework lifecycle events, and `HostInitializer` records P0..P3 slot snapshots without retaining Activity instances. These pieces are a host-container foundation, **not** a complete Android app virtualization implementation. The exact target APK has not yet been inspected; see [the target APK record](target-apk.md). A class loader is not a sandbox and does not make arbitrary guest Activity, Service, Provider, resources, permissions, or task/back-stack behavior work automatically.

Running this selected APK without root requires inspecting its actual runtime and then implementing a compatible guest component model or another supported integration path. The listing does not establish that 8 Ball Pool is a Flutter module; an arbitrary APK cannot be treated as one. If the exact APK is Flutter-based, Flutter Add-to-App still requires an appropriate module/build integration rather than `DexClassLoader` alone. Any in-process guest code also has the host UID/permissions, so an explicit security model is required. No root access, hidden-API bypass, signature spoofing, or permission escalation is used here.

## Build and verify

```bash
cd flutter-app
flutter pub get
cd ..
printf 'flutter.sdk=%s\n' "$(dirname "$(dirname "$(command -v flutter)")")" > local.properties
printf 'sdk.dir=%s\n' "$ANDROID_HOME" >> local.properties
./gradlew :android-host:assembleDebug :android-host:assembleRelease :android-host:test
python3 scripts/verify_host_structure.py
python3 scripts/verify_apk_architecture.py \
  android-host/build/outputs/apk/debug/android-host-debug.apk debug
python3 scripts/verify_apk_architecture.py \
  android-host/build/outputs/apk/release/android-host-release-unsigned.apk release
```

Use JDK 17+, Android SDK API 36, Android NDK 26.3.11579264, CMake 3.22.1, and the Flutter version pinned in `.github/workflows/ci.yml`. `local.properties`, generated `.android/`, APKs, DEX, and shared libraries remain build artifacts/local configuration, not source files.
