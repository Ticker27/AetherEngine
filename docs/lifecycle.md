# Runtime and Host Lifecycle

This document distinguishes the lifecycle implemented by the Android host/container from the future Flutter add-to-app lifecycle.

## Native runtime state machine

```text
NEW → INITIALIZED → RUNNING → STOPPING → STOPPED
```

- `NEW`: initial state after library load
- `INITIALIZED`: `bridge.Native.initialize()` succeeded
- `RUNNING`: runtime loop started through the C++ state manager
- `STOPPING`: shutdown is in progress
- `STOPPED`: terminal state except the native test-only reset path

The JNI wrapper treats initialization while already initialized/running as success. Shutdown is idempotent. The state manager synchronizes transitions and supports state queries.

## Current host process lifecycle

```text
Android creates AetherApplication
  └── onCreate()
      └── HostInitializer.initialize()
          └── Native.initialize() → libaether.so

MainActivity
  └── reads HostInitializer/native status and shows host diagnostics

AetherApplication.onTerminate() (best effort only)
  └── HostInitializer.shutdown()
      └── Native.shutdown() when the native runtime was ready
```

`Application.onTerminate()` is not guaranteed for a production process kill. The host therefore must not depend on it for correctness or persistent guest cleanup.

## Guest loading and lifecycle routing

```text
HostInitializer.loadTargetApk(file, trustPolicy)  [worker thread]
  ├── feature switch DYNAMIC_APK_LOADING must be enabled
  ├── DynamicApkLoader copies APK into private storage
  ├── package name + signer pins are verified
  └── read-only APK → DexClassLoader → LoadedGuestApk metadata

Android invokes an internal proxy component
  └── proxy reports created/started/resumed/paused/stopped/destroyed
      └── HostInitializer.dispatch(HostComponentEvent)
          └── registered HostComponentListener(s)
```

The guest loader and Android component lifecycle are deliberately separate. Loading DEX does not cause Android to instantiate an arbitrary guest `Activity` or give it Android framework lifecycle behavior. A compatible guest adapter/component model still needs to be implemented before a normal APK can run as a virtual app. See [The Host container design](host-container.md).

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

## Flutter lifecycle after add-to-app integration

```text
AetherApplication / host initializer
  └── create or retrieve a FlutterEngine
      ├── FlutterJNI and libflutter.so initialize the engine/runtime
      ├── register the aether/runtime MethodChannel handler
      └── execute the selected Dart entrypoint
          └── load Dart app code (libapp.so in an AOT/release package)
              └── runApp(AetherApp)

MainActivity
  ├── attach a Flutter UI / FlutterActivity to the host
  ├── forward lifecycle as required by the embedding
  └── detach the Activity view while optionally retaining a warm engine
```

`AetherFlutterHost` remains a reflection-based compatibility stub while the Flutter Android embedding is absent from Gradle. The final integration should use supported embedding APIs. `FlutterJNI` owns Flutter runtime operations; it is separate from Aether's `bridge.Native` and `libaether.so`.

## Channel lifecycle and message path

```text
Dart AetherChannel
  ↔ MethodChannel("aether/runtime")
  ↔ Flutter engine platform-message transport
  ↔ Kotlin channel handler (AetherFlutterHost)
  → com.aether.host.bridge.Native
  → JNI registration in libaether.so
```

Current Dart channel methods are `version`, `state`, `initialize`, `shutdown`, and `ping`. In the integrated build, channel handlers must be registered before Dart UI code depends on them. Map native/link errors to stable channel error codes and never pass null where the contract declares a non-null value.

## Error handling and tests

- `UnsatisfiedLinkError` → stable platform error such as `UNSATISFIED_LINK`
- Native operation failure → stable `NATIVE_ERROR`; keep internal stack traces out of production messages
- Unknown MethodChannel method → `notImplemented`
- Kotlin `String` results declared non-null → always return a valid string from JNI
- Unit tests cover signer pin validation, feature switches, reflection visibility, and native state transitions
- CI validates manifest/class consistency, builds debug and release APKs, runs Android/native/Flutter tests, and uploads the APKs
- Instrumentation tests for real guest Activity attachment are still required once the guest adapter and Android virtualization model exist
