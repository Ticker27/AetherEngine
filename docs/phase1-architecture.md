# Phase 1 architecture (visible layer)

Scope: the **visible layer** mirrored by `aether-engine/` — the component families, the JNI bridge, and the provider dispatch path.

Excluded by decision: the C2 / seller layer, external endpoints, and the hidden payload. No network call exists anywhere in this tree (static proof: `phase-a1/evidence/none_ok.txt`).

```mermaid
graph TD
    App["App : Application<br/>onCreate"] --> Load["Native / System.loadLibrary(aether)"]
    Load --> OnLoad["JNI_OnLoad<br/>jni_bridge.cpp"]
    OnLoad --> Reg["RegisterNatives<br/>11 entries, rc logged"]
    Reg --> KMethods["kMethods[11]<br/>stubs, fnPtr logged"]

    App --> Entry["Entry : Activity<br/>LAUNCHER"]
    Entry --> PA["ProxyActivity<br/>P0-P3"]
    Entry --> TA["TransparentProxyActivity<br/>P0-P3"]
    Entry --> PP["ProxyPendingActivity<br/>P0-P3"]

    App --> Daemon["DaemonService<br/>+ DaemonInnerService<br/>START_STICKY"]
    App --> PS["ProxyService<br/>P0-P3"]
    App --> PJ["ProxyJobService<br/>P0-P3 (BIND_JOB_SERVICE)"]
    App --> PV["ProxyVpnService<br/>BIND_VPN_SERVICE"]
    App --> BR["ProxyBroadcastReceiver"]

    Disp["content://com.aether.pc.P0-P3"] --> PC["ProxyContentProvider P0-P3<br/>call dispatch"]
    PC --> Echo["Bundle reply<br/>aether-echo"]
    Sys["content://com.aether.syscall"] --> SP["SystemCallProvider<br/>a() / b(Bundle)"]
    Fp["content://com.aether.fp"] --> FP["FileProvider"]
```

## Component map

| family | classes (real nested classes) | manifest registration |
|---|---|---|
| Activities | `Entry`, `ProxyActivity` + P0–P3, `TransparentProxyActivity` + P0–P3, `ProxyPendingActivity` + P0–P3 | 14 activities, only `Entry` exported |
| Services | `DaemonService` + `DaemonInnerService`, `ProxyService` + P0–P3, `ProxyJobService` + P0–P3, `ProxyVpnService` | 11 services, none exported |
| Providers | `ProxyContentProvider` + P0–P3, `SystemCallProvider`, `FileProvider` | 6 providers, authorities `com.aether.pc.P0-P3`, `com.aether.syscall`, `com.aether.fp` |
| Receiver | `ProxyBroadcastReceiver` | 1 receiver, not exported |
| Native | `Native.kt` (11 `external fun`), `jni_bridge.cpp`, `runtime_init.cpp` (Phase 2 slot, intentionally empty) | `libaether.so`, arm64-v8a |

## Dispatch path

`App.onCreate` → `Native()` (companion `init` → `System.loadLibrary("aether")`) → `JNI_OnLoad` → `FindClass("com/aether/helper/Native")` → `RegisterNatives(kMethods, 11)` → fnPtr log lines → `Entry` lifecycle. Provider calls are answered locally by `ProxyContentProvider.call` with an `aether-echo` bundle.