# AetherEngine

Android host process for a Flutter/Dart application with a structured, in-process virtualization container.

> **Status:** the repository now has a host/bootstrap layer, a signer-pinned DEX loader, and private proxy-component slots. This is a secure **foundation**, not a full Android app sandbox: loaded APK code runs with the host UID/permissions, and arbitrary guest `Activity`/`Service`/`Provider` components are not instantiated automatically. See [The Host container design](docs/host-container.md).

## Runtime architecture

```text
AndroidManifest.xml + classes.dex
              │
      Android host bootstrap
        ┌─────┴────────┐
        │              │
 HostInitializer    FlutterJNI (target integration)
        │              │
 Native facade     libflutter.so
        │              │
 libaether.so     Flutter runtime / Dart VM
                       │
                libapp.so (AOT)
                       │
                Dart application

Proxy Android components ── lifecycle events ──> HostInitializer
HostInitializer ── trusted, opt-in DEX loading ──> DynamicApkLoader
```

The binaries have different roles: `libaether.so` is Aether's custom JNI layer, `libflutter.so` is Flutter's runtime, and `libapp.so` is the compiled Dart application. The current Android Gradle host builds `libaether.so`; Flutter embedding and its generated binaries remain a separate integration step.

## Repository layout

```text
android-host/src/main/kotlin/com/aether/host/
├── AetherApplication.kt                 # process owner
├── MainActivity.kt                      # host status screen
├── AetherFlutterHost.kt                 # optional Flutter reflection adapter
├── bridge/Native.kt                     # JNI facade (4 registered methods)
├── bootstrap/HostInitializer.kt         # process + component lifecycle coordinator
└── virtualization/
    ├── loader/                          # DynamicApkLoader + signer trust policy
    ├── activity/                        # VirtualActivity and proxy Activity pools
    ├── components/
    │   ├── service/                     # daemon, service, job, and VPN proxies
    │   ├── provider/                    # FileProvider aliases and provider slots
    │   └── receiver/                    # broadcast proxy
    ├── flags/flagger.kt                 # process-local capability switches
    ├── util/MethodUtils.kt              # visibility-respecting reflection helpers
    └── web/InternalWebBrowser.kt        # internal HTTPS-only browser

aether-native/                           # C++ source for libaether.so
flutter-app/                              # Dart source, channels, services, assets
scripts/verify_host_structure.py         # component/manifest consistency check
docs/                                     # architecture and implementation contracts
```

## Current JNI contract

- Kotlin facade: `com.aether.host.bridge.Native`
- Registered methods: `initialize()`, `shutdown()`, `getVersion()`, `runtimeState()`
- Registration: `JNI_OnLoad()` → `RegisterNatives()` (no `Java_*` method exports)
- Library name: `System.loadLibrary("aether")` → `libaether.so`

The APK-analysis target previously described `com.aether.helper.Native` (11 native methods) and `com.aether.helper.flagger` (2 native methods). Those exact JNI declarations are not present here. The new host `flagger` is a **pure Kotlin feature-flag utility** with two methods; it is not a substitute for the reported native API. Exact method names/signatures must be verified before implementing that separate binary contract.

## Build and test

Android/native build (requires JDK 17, Android SDK, NDK, and CMake):

```bash
./gradlew :android-host:assembleDebug
./gradlew :android-host:assembleRelease
./gradlew :android-host:test
```

Validate source-to-manifest component registrations:

```bash
python3 scripts/verify_host_structure.py
```

Run the native runtime-state test (requires `g++`):

```bash
g++ -std=c++17 -I aether-native/src/main/cpp \
  aether-native/src/main/cpp/runtime/runtime_state.cpp \
  integration-test/native/runtime_state_test.cpp \
  -o /tmp/runtime_state_test -pthread
/tmp/runtime_state_test
```

Flutter source checks (requires Flutter SDK; they do not embed Flutter into the Android host):

```bash
cd flutter-app
flutter pub get
flutter analyze
flutter test
```

The GitHub Actions workflow runs structure checks, Android debug/release assembly, JVM/native tests, Flutter analysis/tests, and uploads both APK artifacts. Android ABI target: `arm64-v8a`.

## Documentation

- [Host container structure, trust boundaries, and proxy inventory](docs/host-container.md)
- [Runtime architecture and repository-to-binary mapping](docs/architecture.md)
- [JNI contract](docs/jni-contract.md)
- [Android/native/Flutter lifecycle](docs/lifecycle.md)
