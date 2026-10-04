# The Host — App Virtualization Container

## Responsibility boundaries

```text
AetherApplication
  └── HostInitializer                 # process/runtime and component lifecycle owner
       ├── Native JNI facade          # host's own libaether.so API
       ├── DynamicApkLoader           # verified DEX code loader
       └── HostComponentListener(s)   # proxy-component lifecycle relay

Target Android components
  ├── VirtualActivity + Activity slots
  ├── Service / JobService / VPN slots
  ├── BroadcastReceiver slot
  └── ContentProvider slots
```

The package layout keeps Android component types separate from bootstrap, DEX loading, native bindings, and utilities:

```text
com.aether.host/
├── AetherApplication.kt
├── MainActivity.kt                         # FlutterActivity entry point
├── bridge/
│   ├── Native.kt                            # custom libaether.so facade
│   └── AetherRuntimeChannel.kt              # Flutter MethodChannel handler
├── bootstrap/
│   ├── HostInitializer.kt
│   └── VirtualActivitySlotRegistry.kt          # process-local P0..P3 lifecycle snapshots
├── target/TargetApkContract.kt                # fixed external guest release identity
└── virtualization/
    ├── filesystem/GuestVirtualFileSystem.kt    # scoped app-private file facade
    ├── activity/
    │   ├── VirtualActivity.kt
    │   ├── ProxyActivity.kt
    │   ├── ProxyPendingActivity.kt
    │   └── TransparentProxyActivity.kt
    ├── components/
    │   ├── receiver/ProxyBroadcastReceiver.kt
    │   ├── provider/{FileProvider, ProxyContentProvider, SystemCallProvider}.kt
    │   └── service/{DaemonService, ProxyService, ProxyJobService, ProxyVpnService}.kt
    ├── flags/flagger.kt
    ├── loader/{DynamicApkLoader, GuestApkTrustPolicy, GuestClassLoaderProxy}.kt
    ├── util/MethodUtils.kt
    └── web/InternalWebBrowser.kt
```

## Component inventory

| Component family | Implemented host classes | Purpose and current behavior |
| --- | --- | --- |
| Android/Flutter bootstrap | `AetherApplication`, `MainActivity` (`FlutterActivity`), `HostInitializer` | Initializes the custom native runtime, starts the embedded Flutter/Dart app through the Android embedding, owns guest metadata, and relays proxy lifecycle events |
| Flutter platform messages | `AetherRuntimeChannel` | Registers `MethodChannel("aether/runtime")` on the Flutter engine and routes supported calls to the host's separate JNI facade |
| DEX loader | `DynamicApkLoader`, `GuestApkTrustPolicy`, `GuestClassLoaderProxy` | Copies an APK into private storage, requires 8 Ball Pool 56.30.0 (4028) and configured signer pins, then applies guest-first class lookup while keeping Android/framework and Aether API classes parent-first |
| Guest file facade | `GuestVirtualFileSystem` | Provides relative-file I/O under a per-APK `noBackupFilesDir` root; rejects traversal and symbolic links. This is not syscall interception or a security sandbox |
| Activity | `VirtualActivity`, `ProxyActivityP0..P3`, `ProxyActivityP0_L..P3_L` | Four standard slots and four landscape slots; forwards Activity lifecycle events to listeners and the host's P0..P3 lifecycle registry |
| Transparent Activity | `TransparentProxyActivityP0..P3` | Four translucent, private slots; no fallback UI is drawn when unattached |
| Pending Activity | `ProxyPendingActivityP0..P3` | Four private slots reserved for host-created PendingIntent flows |
| Services | `DaemonService`, `DaemonInnerService`, `ProxyServiceP0..P3` | Explicit, non-sticky service endpoints; no automatic restart or hidden persistence |
| Job services | `ProxyJobServiceP0..P3` | Four `BIND_JOB_SERVICE`-protected slots; they complete immediately until an adapter is registered |
| VPN service | `ProxyVpnService` | System-bindable entry point; feature-off by default and does not establish a VPN tunnel |
| Broadcast | `ProxyBroadcastReceiver` | Non-exported receiver that relays explicit in-package broadcasts |
| File providers | `FileProvider`, nested aliases `FileProvider$a` and `FileProvider$b` | AndroidX providers restricted to host-owned `files/shared/` and `cache/shared/` directories |
| Content providers | `ProxyContentProviderP0..P3`, `SystemCallProvider` | Non-exported, inert endpoints; CRUD calls return empty/no-op results and expose no call-log data |
| Web | `InternalWebBrowser` | Internal HTTPS-only WebView, with JavaScript, file access, and content access disabled |
| Utilities | `MethodUtils`, `flagger` | Public-method reflection only; process-local capability switches, all disabled by default |
| Native bridge | `bridge.Native` | Four existing host JNI declarations; registered with `RegisterNatives()` from `JNI_OnLoad()` |

`FileProvider$a` and `$b` are nested binary class names registered explicitly in the manifest. They are kept only as compatibility aliases; new code should refer to the provider authorities rather than the alias names.

## Process and guest lifecycle

1. Android creates `AetherApplication`.
2. `HostInitializer.initialize()` initializes Aether's own native runtime and records `READY` or a degraded/failed status.
3. A caller may enable `HostFeature.DYNAMIC_APK_LOADING` and call `loadTargetApk(file, trustPolicy)` from a worker thread. Loading remains disabled by default.
4. `DynamicApkLoader` copies the selected file into the app's private `noBackupFilesDir`, parses its package/version/signers, requires exactly `com.miniclip.eightballpool` version `56.30.0` (version code `4028`) plus explicit signer pins, makes the cached APK read-only, then creates a digest-specific `GuestClassLoaderProxy` and a per-digest `GuestVirtualFileSystem` root.
5. `VirtualActivity` reports Android lifecycle events to `HostInitializer`; the process-local P0..P3 registry stores small state snapshots without retaining Activity instances, and registered `HostComponentListener`s can route events to a target-specific adapter. Listener callbacks remain synchronous.
6. `HostInitializer.shutdown()` drops loaded-guest references, clears P0..P3 snapshots, and shuts down Aether's native runtime when the process is explicitly terminated.

`GuestClassLoaderProxy` applies a Java class-delegation policy only; it does not attach a guest `Activity`, supply guest `Context`/resources, or virtualize package-manager calls. `GuestVirtualFileSystem` is an app-private I/O facade for code that explicitly uses it: it is not mounted over guest paths and cannot intercept arbitrary `File`, native `open(2)`, or other direct host-process I/O. Both components run in the host process and are **not a sandbox**. An unmodified APK still cannot use these foundations as if it were installed; `VirtualActivity` displays a placeholder until a compatible guest UI/component adapter exists.

## Trust and security constraints

- APK code loaded by `DexClassLoader` executes in Aether's process with Aether's UID, permissions, and access to in-process objects. This is **not isolation**; never load an untrusted APK.
- The target package/version is fixed to `com.miniclip.eightballpool` 56.30.0 (version code `4028`). The caller must supply SHA-256 signer certificate pins computed from the actual APK; every signer reported for the archive must be pinned. A package/version match alone is insufficient. See [the target APK record](target-apk.md).
- The APK is copied to app-private storage before validation/loading, and the code file is made read-only to reduce mutation/TOCTOU risk.
- The current loader supports guest DEX code plus the explicit file-facade API described above. It does not extract/load guest native libraries, merge guest resources/assets, or redirect arbitrary file I/O.
- Activity/process hooks, hidden API bypasses, signature spoofing, permission escalation, and package-manager spoofing are intentionally not implemented.
- All proxy components are `exported=false` except the Android VPN service entry, which is system-bindable only through `BIND_VPN_SERVICE`. The launcher `MainActivity` is the only normal exported app component.
- `FileProvider` exposes only two private app-owned `shared/` directories; it has no external-storage, root, or broad path mappings.
- VPN and internal-browser capabilities are disabled by the local `flagger` until the host explicitly enables them. These feature flags are not a security boundary.
- `SystemCallProvider` does not read or publish call history. A future data provider needs a separate permission and privacy review.

## Full virtualization work still required

To run a normal, unmodified target APK as an Android application—not merely load its DEX code—the host still needs a guest component model: package/resource/asset resolution, guest `Context` behavior, component manifest parsing and dispatch, task/back-stack mapping, permission policy, provider authority routing, service/job semantics, and lifecycle-aware UI attachment. Each capability needs explicit security review and integration tests. The proxy pools in this repository are private lifecycle endpoints, not a claim that those Android framework semantics are already virtualized.
