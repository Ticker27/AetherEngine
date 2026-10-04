# aether-engine — Phase 1

Phase 1 = visible-mechanism mirror of the reference architecture; hidden payload intentionally omitted.

## Scope

- Visible-mechanism mirror only: component families, JNI bridge, RegisterNatives contract.
- Hidden payload, C2 and seller layer intentionally omitted; Phase 2 hook slot is a comment only.
- No networking call is made by any component in this tree (acceptance step 10 must print `NO-NET-OK`).

## Architecture (visible layer; C2 / seller layer cut)

```
app (com.aether · arm64-v8a · debug only)
│
├─ App : Application ── onCreate() ──► Native() ──► System.loadLibrary("aether")
│                                                        │
│        ┌───────────────────────────────────────────────┘
│        ▼
│  JNI_OnLoad (jni_bridge.cpp) ── FindClass com/aether/helper/Native
│        └── RegisterNatives × 11 ── fnPtr log: hex address + section name
│
├─ Entry : Activity (LAUNCHER) — lifecycle logcat only
│
├─ helper/ component families (P0..P3 are real nested classes, not strings)
│   ├─ ProxyActivity / TransparentProxyActivity / ProxyPendingActivity
│   ├─ ProxyService / ProxyJobService / ProxyVpnService
│   ├─ DaemonService (+ DaemonInnerService)   — START_STICKY, no restart job
│   ├─ ProxyContentProvider P0..P3            — call() echo "aether-echo"
│   ├─ SystemCallProvider / FileProvider
│   └─ ProxyBroadcastReceiver
│
└─ native (libaether.so)
    ├─ jni_bridge.cpp    — kMethods[11] stubs + JNI_OnLoad registration
    └─ runtime_init.cpp  — Phase 2 placeholder (intentionally empty)
```

## Build & verify

```bash
cd aether-engine
./gradlew :app:assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libaether.so   # lib/arm64-v8a/libaether.so
```

On-device acceptance steps (launch, provider echo, daemon, netstat before/after, logcat fnPtr table)
are listed in the Phase 1 build order; run them in order on an arm64-v8a device/emulator.
