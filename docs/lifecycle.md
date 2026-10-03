# Runtime and Host Lifecycle

This document describes the lifecycle currently wired into the Android host, the custom native runtime, the Flutter engine/Dart app, and the separate guest proxy shells.

## Custom native runtime state machine

```text
NEW → INITIALIZED → RUNNING → STOPPING → STOPPED
```

- `NEW`: initial state after `libaether.so` is loaded.
- `INITIALIZED`: `Native.initialize()` succeeded.
- `RUNNING`: runtime work has been started through the C++ state manager/thread dispatcher.
- `STOPPING`: shutdown is in progress.
- `STOPPED`: terminal state except the native test-only reset path.

JNI initialization is idempotent for an already initialized/running runtime. Shutdown is idempotent. The state manager synchronizes transitions and supports state queries.

## Android process and host Activity

```text
Android starts AetherApplication
  └── onCreate()
      └── HostInitializer.initialize()
          └── custom Native facade → System.loadLibrary("aether")
              └── libaether.so / JNI_OnLoad / RegisterNatives

Android launches MainActivity (FlutterActivity)
  └── Flutter embedding creates/attaches FlutterEngine
      ├── FlutterJNI loads/initializes libflutter.so
      ├── engine runs the default Dart entrypoint
      ├── release build executes Dart AOT from libapp.so
      └── configureFlutterEngine() attaches AetherRuntimeChannel
```

The Android app's process is a normal app process. Installing/running the host does not require root. `Application.onTerminate()` is not guaranteed for production process kills, so process correctness must not depend on that callback.

## Two independent native loading paths

```text
HostInitializer / AetherRuntimeChannel
  → com.aether.host.bridge.Native
  → System.loadLibrary("aether")
  → libaether.so → JNI_OnLoad() → RegisterNatives()

FlutterActivity / FlutterEngine
  → FlutterJNI
  → libflutter.so → Flutter runtime / Dart VM
  → libapp.so (release AOT)
```

`FlutterJNI` is owned by the Flutter embedding; it does not pass through Aether's custom JNI registry. The Aether facade owns only the custom `libaether.so` path.

## Flutter platform-message lifecycle

```text
AetherApp / NativeService
  ↔ Dart AetherChannel
  ↔ MethodChannel("aether/runtime")
  ↔ Flutter engine platform-message transport
  ↔ AetherRuntimeChannel (Kotlin)
  ↔ HostInitializer / com.aether.host.bridge.Native
  ↔ libaether.so
```

`MainActivity.configureFlutterEngine()` registers the handler before the first Dart frame uses the channel. `cleanUpFlutterEngine()` clears the handler when the Activity detaches. Current methods are `version`, `state`, `initialize`, `shutdown`, and `ping`; link failures and operation errors are returned using stable platform error codes.

Rendering, frame scheduling, semantics, textures, and Flutter runtime services stay in the engine lane unless a feature explicitly sends a platform message.

## Guest DEX loading and proxy lifecycle routing

```text
HostInitializer.loadTargetApk(file, trustPolicy)  [worker thread]
  ├── DYNAMIC_APK_LOADING switch must be enabled
  ├── DynamicApkLoader copies APK into private storage
  ├── exact package + signer pins are verified
  └── read-only APK → DexClassLoader → LoadedGuestApk metadata

Android invokes a declared, internal proxy component
  └── proxy reports its own Android lifecycle event
      └── HostInitializer.dispatch(HostComponentEvent)
          └── registered HostComponentListener(s)
```

The guest loader and proxy lifecycle are separate from the embedded Flutter application lifecycle. Loading DEX does not make Android instantiate arbitrary guest components. A compatible guest component/resource/context/task model is still required before a normal third-party APK can run as a virtual app. Loaded guest code has the host UID and permissions; the loader is not a sandbox.

## Proxy lifecycle events

| Host component | Lifecycle relayed |
| --- | --- |
| `VirtualActivity` pool | created, started, resumed, paused, stopped, save-instance-state, destroyed, new intent |
| `ProxyServiceP0..P3`, daemon services | created, started, bound/unbound where supported, destroyed |
| `ProxyJobServiceP0..P3` | started, stopped; returns complete immediately until a job adapter exists |
| `ProxyBroadcastReceiver` | received for explicit in-package broadcasts |
| `ProxyContentProviderP0..P3`, `SystemCallProvider` | query/type/insert/update/delete; current results are empty/no-op |
| `ProxyVpnService` | started/revoked/destroyed; does not establish a tunnel |

Listeners are invoked synchronously. They must avoid long-running work on the main thread and must not retain an Activity/Service instance after its destroy/stop event.

## Error handling and tests

- `UnsatisfiedLinkError` → channel error `UNSATISFIED_LINK`; implementation details stay out of production messages.
- Native operation failure → channel error `NATIVE_ERROR`.
- Unknown MethodChannel method → `notImplemented`.
- Kotlin `String` JNI results declared non-null → always return a valid string.
- JVM tests cover signer-pin validation, feature switches, reflection visibility, and native state transitions.
- CI builds debug/release APKs, checks that both native lanes and Flutter assets are packaged, runs Android/native/Flutter tests, and uploads the APKs.
- Instrumentation tests for real guest Activity attachment remain future work because the guest component model is not implemented.
