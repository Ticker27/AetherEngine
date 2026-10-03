# AetherEngine

A regular Android host application that embeds the Aether Flutter/Dart application beside a separate custom native subsystem and an in-process virtualization-container foundation.

> **What works now:** `MainActivity` is a real `FlutterActivity`; the Android host builds and packages Aether's `libaether.so`, Flutter's `libflutter.so`, Flutter assets, and (in release) Dart AOT `libapp.so`. The APK installs and runs through Android's normal app model; root is not required.
>
> **Important limit:** `DynamicApkLoader` only verifies and loads DEX code. It is not a sandbox and does not run an arbitrary APK's Android components automatically. Guest code shares the host UID and permissions. See [The Host container design](docs/host-container.md).

## Runtime architecture

```text
                          Android
                             │
              AndroidManifest.xml + classes.dex
                 ┌───────────┴────────────┐
                 │                        │
                 ▼                        ▼
       Custom Native JNI lane        FlutterJNI lane
 com.aether.host.bridge.Native       FlutterActivity/Engine
   System.loadLibrary("aether")             │
                 │                   libflutter.so
                 ▼                        │
          libaether.so                     ▼
       JNI_OnLoad/RegisterNatives     Flutter runtime/Dart VM
                                           │
                                      libapp.so (release)
                                           │
                                           ▼
                                     Dart application

Dart ↔ MethodChannel("aether/runtime") ↔ AetherRuntimeChannel (Kotlin)
                                          ↔ custom Native facade/libaether.so
```

The custom Aether JNI bridge and Flutter's `FlutterJNI` are separate paths. Dart platform messages pass through Flutter's engine to Kotlin; Dart does not call Aether's JNI methods directly. See [runtime architecture](docs/architecture.md).

## Main source areas

```text
android-host/src/main/kotlin/com/aether/host/
├── AetherApplication.kt                 # Android process owner
├── MainActivity.kt                      # FlutterActivity entry point
├── bridge/
│   ├── Native.kt                         # custom libaether.so facade (4 known methods)
│   └── AetherRuntimeChannel.kt            # Flutter platform-message handler
├── bootstrap/HostInitializer.kt          # process/native and proxy lifecycle coordinator
└── virtualization/
    ├── loader/                            # DynamicApkLoader + signer trust policy
    ├── activity/                          # VirtualActivity and proxy Activity pools
    ├── components/                        # service/provider/receiver proxies
    ├── flags/flagger.kt                   # process-local host feature switches
    ├── util/MethodUtils.kt                # visibility-respecting reflection helpers
    └── web/InternalWebBrowser.kt          # internal HTTPS-only browser

aether-native/                             # C++ source for libaether.so
flutter-app/                               # Flutter module: Dart UI, logic, channels, assets
scripts/verify_host_structure.py          # source/manifest registration check
scripts/verify_apk_architecture.py        # packaged APK binary/asset check
docs/                                      # architecture, lifecycle, JNI, host boundaries
```

The generated `flutter-app/.android/` directory is created by `flutter pub get` and is intentionally not checked in.

## Current JNI contract vs target APK contract

The source currently implements `com.aether.host.bridge.Native` with `initialize()`, `shutdown()`, `getVersion()`, and `runtimeState()`, registered from `JNI_OnLoad()` using `RegisterNatives()`.

The supplied target architecture names `com.aether.helper.Native` (11 native methods) and `com.aether.helper.flagger` (2 native methods). Their exact declarations/descriptors are not present in this repository. The host's `com.aether.host.virtualization.flags.flagger` is a separate pure-Kotlin utility; it is not that target JNI class. Do not treat the 11+2 contract as implemented or invent signatures. See [JNI contract](docs/jni-contract.md).

## Build and test

Requirements: JDK 17+, Flutter 3.47.0, Android SDK API 36, Android NDK 26.3.11579264, and CMake 3.22.1. Configure the local Android SDK and Flutter SDK paths in root `local.properties`, then:

```bash
cd flutter-app
flutter pub get
cd ..
./gradlew :android-host:assembleDebug :android-host:assembleRelease :android-host:test
python3 scripts/verify_host_structure.py
python3 scripts/verify_apk_architecture.py android-host/build/outputs/apk/debug/android-host-debug.apk debug
python3 scripts/verify_apk_architecture.py android-host/build/outputs/apk/release/android-host-release-unsigned.apk release
```

Flutter tests:

```bash
cd flutter-app
flutter analyze
flutter test
```

The release APK is unsigned by this CI build and must be signed for distribution. GitHub Actions uploads debug and release APKs as a run artifact. The supported ABI is currently `arm64-v8a`.

## Documentation

- [Host container structure, trust boundaries, and proxy inventory](docs/host-container.md)
- [Four-layer architecture and binary mapping](docs/architecture.md)
- [JNI contract and unknown target signatures](docs/jni-contract.md)
- [Android/native/Flutter lifecycle](docs/lifecycle.md)
