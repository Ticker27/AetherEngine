# AetherEngine

A regular, installable Android host application that embeds a Flutter/Dart UI beside a custom
native runtime (`libaether.so`) and an in-process virtualization-container foundation.

**What works today**

- The host APK builds, installs, and runs through Android's normal app model; root is not required.
- The Dart app talks to the custom native runtime over `MethodChannel("aether/runtime")`
  (`version`, `state`, `initialize`, `shutdown`, `ping`), and the native lane (runtime state
  machine, task dispatcher, JSON message bridge) passes host-side tests on any machine.
- The guest loader verifies a pinned APK identity plus explicit signer pins, copies the APK to
  app-private read-only storage, re-verifies it, then creates a guest-first `DexClassLoader`
  and a per-digest `GuestVirtualFileSystem` root.

**What does not work yet**

- Loading is not a sandbox and does not install or launch another app's Android components.
  Guest code shares the host UID and permissions; guest `Application`/`Activity`, resources,
  native libraries, and package-manager behavior are not virtualized. The milestone plan is
  [PLAN.md](PLAN.md).

## Verify it locally

```sh
sh scripts/check_local.sh
```

Runs the repository structure verifier and the host-side native suite (runtime-state machine,
thread dispatcher, and message-bridge smoke demo). No Android SDK, JDK, or Flutter needed.

The Android and Flutter layers are checked by CI (`.github/workflows/ci.yml`): Gradle unit
tests, `flutter analyze` / `flutter test`, debug/release APK assembly, and binary verification
of the packaged artifacts (`scripts/verify_apk_architecture.py`).

## Repository layout

```text
android-host/                  # Kotlin host module (manifest, sources, resources)
aether-native/src/main/cpp/    # C++ sources for libaether.so
flutter-app/                   # Flutter add-to-app module (Dart UI, channels, services)
integration-test/              # host-side native tests + Android/Flutter contract tests
scripts/                       # verifiers + local check runner
PLAN.md                        # teardown record + milestones
.github/workflows/             # ci.yml (build/test), release.yml (v* tag releases)
```

Host source map (`android-host/src/main/kotlin/com/aether/host/`):

```text
AetherApplication.kt / MainActivity.kt   # Android process + FlutterActivity entry
bootstrap/                               # HostInitializer, slot registry, lifecycle relay
bridge/                                  # Native JNI facade + MethodChannel handler
runtime/                                 # AetherRuntime bootstrap + HostLifecycle
target/TargetApkContract.kt              # pinned external guest identity
virtualization/                          # loader, filesystem, activity, components, flags, util, web
```

## Target guest policy

The trust policy accepts only 8 Ball Pool `com.miniclip.eightballpool` version `56.30.0`
(version code `4028`) with explicitly configured SHA-256 signer pins; a package/version match
alone is insufficient. The target APK is never committed to Git (`local/` is ignored), and the
loader does not launch it. Hidden-API bypass, signature/permission spoofing, package-manager
spoofing, and anti-cheat or licensing bypass are intentionally not implemented. Loaded guest
code runs with the host UID and permissions: this is not isolation.

## Build and test (full toolchain)

Requirements: JDK 17+, Flutter 3.47.0, Android SDK API 36, Android NDK 26.3.11579264, CMake 3.22.1.

```sh
cd flutter-app && flutter pub get && cd ..
./gradlew :android-host:assembleDebug :android-host:assembleRelease :android-host:test
python3 scripts/verify_apk_architecture.py android-host/build/outputs/apk/debug/android-host-debug.apk debug
python3 scripts/verify_apk_architecture.py android-host/build/outputs/apk/release/android-host-release-unsigned.apk release
```

Flutter module checks:

```sh
cd flutter-app && flutter analyze && flutter test
```

Supported ABI: `arm64-v8a`; minimum Android API 24 (Android 7.0).

## Release

Pushing a `v*` tag runs `.github/workflows/release.yml`: it re-runs the checks above and
publishes the debug and release APKs plus a `SHA256SUMS.txt` checksum file to a GitHub Release.
The release APK from this build is unsigned and must be signed before distribution.
