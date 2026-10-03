# Lifecycle

## Native Runtime State Machine

```
NEW -> INITIALIZED -> RUNNING -> STOPPING -> STOPPED
```

- `NEW`: Initial state after lib load, before any init
- `INITIALIZED`: `initialize()` succeeded, resources allocated, thread dispatcher started
- `RUNNING`: (Future) runtime loop active — used when game loop attached
- `STOPPING`: Transitional — cleaning up resources
- `STOPPED`: Terminal — no further transitions except ForceResetForTesting

### Guards

- `initialize()` allowed only from `NEW` -> `INITIALIZED`
- If already `INITIALIZED` or `RUNNING`, returns true (idempotent for caller)
- `StartRunning()` allowed from `INITIALIZED` -> `RUNNING`
- `Shutdown()` allowed from `INITIALIZED` or `RUNNING` -> `STOPPING` -> `STOPPED`
- Idempotent shutdown — calling twice returns true
- `NEW` -> `STOPPED` allowed for cleanup path

### Thread Safety

- `std::mutex` protects transitions
- `std::atomic<RuntimeState>` for lock-free reads
- `GetStateString()` never throws, returns lowercase string

## Android Host Lifecycle

```
AetherApplication.onCreate()
  │
  ├── (Phase 1) tryInitializeNative() optional
  └── (Phase 3) AetherFlutterHost.warmup() — FlutterEngine + Dart entrypoint

MainActivity.onCreate()
  │
  ├── Show bootstrap debug info (Phase 1)
  └── AetherFlutterHost.attach() (Phase 3)

MainActivity.onResume/onPause
  └── Forward to FlutterHost

MainActivity.onDestroy()
  └── FlutterHost.detach() — keep engine warm

AetherApplication.onTerminate()
  └── Native.shutdown()
```

## Flutter Lifecycle

```
Dart main() / mainAether()
  │
  ├── WidgetsFlutterBinding.ensureInitialized()
  └── runApp(AetherApp)

AetherApp -> HomePage
  │
  ├── initState() -> NativeService.getStatus()
  └── MethodChannel("aether/runtime")
        ├── version -> Native.getVersion()
        ├── state -> Native.runtimeState()
        ├── initialize -> Native.initialize()
        ├── shutdown -> Native.shutdown()
        └── ping -> runtimeState + echo
```

## Error Mapping

- `UnsatisfiedLinkError` -> `MethodChannel` error code `UNSATISFIED_LINK`
- Native exception -> `NATIVE_ERROR` with stacktrace
- Null returns prevented at native layer — always return valid String
- Dart side throws `StateError` if result is null (defensive)

## Testing Lifecycle

- Unit: RuntimeStateManager transitions, ForceResetForTesting
- JNI: Registration failure test (wrong class path, wrong signature)
- Flutter: MethodChannel mock
- Instrumentation: Engine attach/detach, Activity lifecycle
- ABI: arm64-v8a only
