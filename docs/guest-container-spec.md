# Guest Container Specification (Phase 2 / M0.1)

Status: **draft contract — no code ships with this document** · Date: 2026-10-04 ·
Milestone: Phase 2 M0.1 · Base: `main` @ `630c474`

This document fixes the contracts that Phase 2 milestones M1–M5 implement: which objects
exist, who owns them, which thread each operation runs on, how guest lifecycle events flow,
which error codes are stable, and what payloads the Flutter control plane exchanges.

Everything in §2–§8 is a **proposed contract, not current behavior**. Statements about what
the host does today are marked *Exists today* and point at real source; nothing in this file
should be read as a claim that a guest Activity can already run. The single-process MVP
decision (2026-10-04) and the milestone scope live in [phase2-plan.md](phase2-plan.md); this
file is M0.1's deliverable inside that plan.

## 0. Related documents

| Document | Relationship |
| --- | --- |
| [phase2-plan.md](phase2-plan.md) | Milestones M0–M6, gap matrix, decision log, required inputs. This spec implements M0.1. |
| [host-container.md](host-container.md) | Current capability inventory, proxy inventory, trust/security constraints. Source of §9 non-goals. |
| [architecture.md](architecture.md) | Four ownership layers, separate guest APK lane, two JNI paths. |
| [lifecycle.md](lifecycle.md) | Existing native/Flutter/proxy lifecycles and the relayed event list. Extended by §6/§7. |
| [jni-contract.md](jni-contract.md) | Verified 4-method native contract; no guest JNI growth without evidence. |
| [target-apk.md](target-apk.md) | Pinned guest identity (`com.miniclip.eightballpool` 56.30.0 / 4028) and signer pin. |
| [reference/snake-engine/EVIDENCE_CHAIN.txt](reference/snake-engine/EVIDENCE_CHAIN.txt) | Architectural reference for install/launch chains. Nothing is ported from it. |

## 1. Layering and ownership rules

```text
Flutter/Dart  ── MethodChannel("aether/runtime") ──►  AetherRuntimeChannel  (M5 surface)
                                                        │
Control plane (M5)                                     │  reads/requests only
                                                        ▼
HostInitializer  ──owns──►  guest session (one at a time)
    ├── GuestSlotScheduler   ──books──►  proxy slot (VirtualActivitySlotRegistry snapshots)
    ├── GuestRuntimeAdapter  ──binds──►  guest Application + Activity inside one booked slot
    ├── GuestPackage         ──model──►  parsed/persisted manifest of one verified APK
    └── GuestContext         ──facade─►  GuestPackage + GuestVirtualFileSystem + GuestClassLoaderProxy + resources
```

**Ownership invariants (all milestones must preserve these)**

1. `HostInitializer` is the single owner of the *guest session*. One installed guest, one
   guest context, one adapter owner at a time. Installing a second guest replaces the session
   only after the previous one is closed (M5 `guestInstall`/`guestClose`).
2. `VirtualActivitySlotRegistry` keeps its current invariant: **snapshots hold no Activity or
   Service reference**. Booking state therefore lives in `GuestSlotScheduler`, never inside the
   registry. The registry stays a passive lifecycle index.
3. The adapter owns guest framework objects only for the lifetime of a booking, and releases
   them on the slot's `destroyed` event. It must not outlive the proxy component it is bound to.
4. Every object crossing a thread boundary is immutable or explicitly synchronized; see §5.
5. No guest object is stored in the `MethodChannel` payload, in `flagger`, or in any static map
   keyed by guest class names. Guest identity is data (`GuestPackage`), not live references.

## 2. `GuestPackage` — parsed package model (M1)

Immutable value model of one verified APK. No `Context`, no `ClassLoader`, no file handles.

| Field | Type | Source | Notes |
| --- | --- | --- | --- |
| `schemaVersion` | `Int` | host | Persisted-model version; bumped on any field change. |
| `packageName` | `String` | manifest | Must equal `TargetApkContract.PACKAGE_NAME`. |
| `versionName` / `versionCode` | `String` / `Long` | manifest | Verified by `GuestApkTrustPolicy` before parsing. |
| `minSdkVersion` / `targetSdkVersion` | `Int` | manifest uses-sdk | Recorded; not enforced (no SDK downgrades in Phase 2). |
| `applicationClass` | `String?` | manifest `application@android:name` | Null when the guest has a default Application. |
| `permissions` | `List<String>` | manifest `uses-permission` | Declared by the guest; the host maps them to the policy in §9. |
| `usesFeatures` | `List<String>` | manifest `uses-feature` | Recorded for diagnostics and M3's runtime-requirement note. |
| `activities` / `services` / `receivers` / `providers` | `List<GuestComponent>` | manifest components | `GuestComponent(name, exported, process, intentFilters, authorities, permission, launchMode)`. |
| `launcherActivity` | `String?` | component resolution | Result of `resolveLauncher()`; see below. |
| `apkSha256` / `signerSha256` | `String` / `List<String>` | host computation | Binds the model to the exact verified binary. |
| `installedAt` | `Long` | host | Epoch millis, diagnostics only. |

**Launcher resolution** (pure function of the parsed components, no Android APIs):

1. An activity whose filters include `android.intent.action.MAIN` **and**
   `android.intent.category.LAUNCHER`.
2. If several match, the first in manifest declaration order (deterministic).
3. If none match, the first `MAIN` + `android.intent.category.INFO`.
4. Otherwise the session is invalid → error `GUEST_LAUNCHER_NOT_FOUND` (§7).

**Persistence**

- Path: `<noBackupFilesDir>/aether-guest-data/<packageName>/<versionCode>/<apkSha256>/package.json`,
  i.e. inside the existing `GuestVirtualFileSystem.forGuest` root. Written with that facade, so
  the same traversal/symlink rules apply.
- Format: UTF-8 JSON, deterministic key order (the field order of the table above),
  two-space indent. Determinism is required so parse → persist → reload is byte-stable and
  testable.
- Reload: a model is reloaded only when `apkSha256` matches a currently verified, cached APK.
  A digest with no matching verified APK is deleted, never trusted.

## 3. `GuestContext` — resources, class loading, data dirs (M2)

A `ContextWrapper` over the host `Application` context, with every guest-visible accessor
overridden. Chosen over a hand-written `Context` subclass because guest code casts `Context` to
framework types (`ContextThemeWrapper`, `Resources.Theme`, `InputMethodManager` lookups);
a partial `Context` would fail at runtime in ways a `ContextWrapper` cannot.

| Member | Backed by | Rule |
| --- | --- | --- |
| `getPackageName()` | `GuestPackage.packageName` | Returns the guest name; the host package name is never returned to guest code. This is a data substitution, not package-manager spoofing (§9). |
| `getApplicationInfo()` / `getApplicationInfo()` | `GuestPackage` + `ApplicationInfo` built from it | `sourceDir`/`publicSourceDir` point at the verified read-only cached APK; `dataDir`/`deviceProtectedDataDir` at the guest VFS roots; the host UID/permissions are inherited and **not** rewritten. |
| `getClassLoader()` | `GuestClassLoaderProxy` | Parent-first prefixes unchanged. |
| `getAssets()`, `getResources()` | Spike A decision (§10) | Either the guest's own resource table, or the documented fallback: host resources with guest assets served through `GuestVirtualFileSystem`. Whichever is chosen is recorded here and in [architecture.md](architecture.md) before M3 starts. |
| `getFilesDir()`, `getCacheDir()`, `getCodeCacheDir()`, `getNoBackupFilesDir()`, `getDatabasePath()`, `getSharedPreferencesPath()` | `GuestVirtualFileSystem` | Every returned path lies below the guest root; `..`, absolute paths, and symlinks are rejected by the facade, never re-implemented. |
| `getPackageManager()`, `getPackageInfo()` | **not virtualized** | Return host-package data. Recorded limitation; guests that depend on real package visibility are unsupported in Phase 2 (§9). |
| `getSystemService()` | host | Real host services. Documented consequence: a guest sees the host's services, which is part of the "not a sandbox" statement, not a feature. |

`GuestContext` is created once per session, on a worker thread, and is then read-only.
It is never re-parented into a different session, and never shared between slots.

## 4. Slot scheduling — `GuestSlotScheduler` over `VirtualActivitySlotRegistry` (M3)

Bookable Phase 2 slots are the manifest-declared `ProxyActivityP0..P3` and their landscape
variants `ProxyActivityP0_L..P3_L`. Transparent, pending, service, receiver, and provider
slots are **not** bookable by the Activity scheduler (M4 uses those pools through its own
dispatch path).

**Booking state machine**

```text
FREE ──book──► RESERVED ──attach──► BOUND ──onResumed──► ACTIVE
  ▲                │                    │                  │
  │                └──cancel──► FREE    └──detach─────────┤
  └──────────────────────────────────────────────────────┘
                        release (on slot `destroyed`)
```

| Rule | Definition |
| --- | --- |
| Exclusive booking | One booking per slot. A slot already in `RESERVED`/`BOUND`/`ACTIVE` cannot be booked again. |
| One active guest slot | At most one slot in `ACTIVE` at a time. A second launch while a guest Activity is `RESUMED` is refused with `GUEST_SLOT_UNAVAILABLE` rather than stealing focus. Phase 2 has no real task stack (§9). |
| Orientation preference | `book(orientation)` prefers the `_L` variant when the host configuration is landscape, otherwise the portrait variant. Fallback to the other variant is allowed and recorded in the booking result. |
| Selection order | Free slots are chosen lowest index first (`P0` → `P3`), then the orientation variant of the same index. Deterministic, so tests and diagnostics are reproducible. |
| Generation check | The adapter re-reads `VirtualActivitySlotSnapshot.generation` before each guest lifecycle forward. A mismatch (the slot was recreated) aborts with `GUEST_LIFECYCLE_REJECTED` instead of calling guest code on a stale Activity. |
| Release | Release happens on the slot's `destroyed` event, on the main thread, and is idempotent. Bookings do not survive `HostInitializer.shutdown()`, which clears the registry. |
| No retention | The scheduler stores slot ids, generations, and guest component names — never Activity, Service, or guest framework references. Same rule as the registry. |

`VirtualActivitySlotRegistry` is used read-only for this purpose (`snapshot()`); Phase 2 adds no
new state to it. The M5 diagnostics screen reads the same snapshots (§8.4).

## 5. Thread rules

| Operation | Required thread | Rationale / enforcement |
| --- | --- | --- |
| `HostInitializer.loadTargetApk` (copy → verify → hash → DEX optimize) | **Worker** | Already documented on the method; these are blocking I/O operations. |
| Manifest/AXML parse, `GuestPackage` persistence | **Worker** | Same reason; `guestInstall` returns its result asynchronously. |
| `GuestContext` / `GuestClassLoaderProxy` creation | **Worker** | DEX loading and VFS root creation touch disk. |
| Guest `Application` construction, `attachBaseContext`, `onCreate` | **Main** | Guest code assumes a main looper and may touch framework state. |
| Guest `Activity` creation, view attach, all guest lifecycle callbacks | **Main** | Same reason; the proxy component's own callbacks are already main-thread. |
| Proxy component lifecycle relay (`HostInitializer.dispatch`) | **Caller thread = main** | `VirtualActivity`/`ProxyService`/etc. dispatch from their framework callbacks. |
| `HostComponentListener` callbacks (incl. `GuestRuntimeAdapter`) | **Main (synchronously)** | `dispatch` invokes listeners inline. The adapter must therefore do no I/O and no guest work synchronously inside a listener other than enqueueing. |
| Guest work that must not block the main thread (asset I/O, large reads) | **Guest worker executor owned by the adapter** | Results are delivered back to the main thread before touching guest or framework state. |
| `GuestSlotScheduler.book/release` | **Any, internally synchronized** | Single mutex protecting the booking table; the public API is thread-safe. |
| `VirtualActivitySlotRegistry.record/snapshot/clear` | **Any (`@Synchronized`, exists today)** | Unchanged. |
| `flagger.setEnabled` | **Main** | Process-local switch; not a security boundary. |
| `guestInstall` / `guestLaunch` channel methods | **Called on the platform (main) thread, work offloaded** | The `MethodChannel.Result` is answered exactly once, from any thread, after the worker step finishes. The main thread is never blocked on guest work. |
| Guest code itself | **Whichever thread the guest chooses** | Aether does not sandbox thread usage; the rules above constrain host-driven calls only. |

Exception: `HostInitializer.dispatch` is synchronous today and stays synchronous. If M3/M4 ever
need asynchronous dispatch, that is a separate documented change to `lifecycle.md`, not an
implicit behavior shift.

## 6. Event flow

### 6.1 Install (M1 + M5)

```text
Flutter  guestInstall(request)                        [main]
  → AetherRuntimeChannel                               [main]
    → flagger(DYNAMIC_APK_LOADING) must be enabled      [worker]
    → DynamicApkLoader.load(apk)  copy → verify → read-only cache → re-verify   [worker]
      → HostInitializer.dispatch(HOST, "guest_loaded") [worker]
    → GuestPackage.parse + persist                     [worker]
    → HostInitializer.dispatch(HOST, "guest_installed")[worker]
  ← identity result (Map)                              [any thread, once]
```

### 6.2 Launch (M3 + M5)

```text
Flutter  guestLaunch(request)                          [main]
  → AetherRuntimeChannel                               [main]
    → GuestSlotScheduler.book(orientation)             [any, synchronized]
    → startActivity(ProxyActivityP<n>)                 [main]
        VirtualActivity.onCreate → dispatch(ACTIVITY, "created")  [main]
          → VirtualActivitySlotRegistry.record → listeners
            GuestRuntimeAdapter.attach(GuestContext)    [main]
              guest Application.attachBaseContext/onCreate        [main]
              guest Activity new → view attach                    [main]
        onStart/onResume → dispatch                      [main]
          GuestRuntimeAdapter forwards guest lifecycle   [main]
    → HostInitializer.dispatch(HOST, "guest_launched") [worker→main? main]
  ← {slot, generation, activityClass}                   [any, once]
```

### 6.3 Close and shutdown

```text
guestClose → finish() on the booked proxy Activity → destroyed → adapter.release()
           → scheduler.release(slot) → HostInitializer.dispatch(HOST, "guest_closed")
HostInitializer.shutdown() → all bookings released → guest_closed for each → registry.clear()
```

### 6.4 Event vocabulary

Existing lifecycle strings relayed by the proxies (unchanged): `created`, `started`, `resumed`,
`paused`, `stopped`, `save_instance_state`, `destroyed`, `new_intent` (Activity); `created`,
`started`, `bound`, `unbound`, `destroyed` (Service); `started`, `stopped` (JobService);
`received` (receiver); `queried`, `type`, `inserted`, `deleted`, `updated` (provider).

New host-level strings, all with `kind = HOST`, `slot = null` unless noted:

| `lifecycle` | Emitted when | `slot` | Notes |
| --- | --- | --- | --- |
| `guest_installed` | `GuestPackage` parsed and persisted | `null` | Carries package identity in `guestPackageName`. |
| `guest_launch_failed` | Booking, resolution, or bind failed | attempted slot or `null` | Always accompanied by `GUEST_*` error code in the payload. |
| `guest_launched` | Guest Activity resumed in a slot | booked slot | `intent` carries the host Intent, never the guest's own Intent object. |
| `guest_closed` | Booking released or `shutdown()` | released slot | |
| `guest_error` | Any guest-scoped failure surfaced to listeners | `null` | Payload uses §7 codes. |

Listener contract is unchanged: listeners are called synchronously on the main thread and must
not retain `owner` past its destroy/stop event.

## 7. Stable error codes

Codes are stable strings. Dart may match on them; messages may change. No exception class name,
stack trace, file path outside the app sandbox, or guest-supplied string may appear in
`message`/`details` (the existing host ops pass `error.javaClass.simpleName` as `details`;
guest ops use the fixed `detail` strings in the last column instead).

| Code | Meaning | Retryable | `detail` |
| --- | --- | --- | --- |
| `GUEST_NOT_INSTALLED` | No verified guest session exists | after `guestInstall` | `no_session` |
| `GUEST_ALREADY_INSTALLED` | Install requested while a session is open | after `guestClose` | `close_current_session` |
| `GUEST_TRUST_REJECTED` | Package/version/signer pin mismatch | no — identity input must change | `trust_policy_rejected` |
| `GUEST_APK_UNREADABLE` | Source file missing/unreadable/invalid archive | yes, with a new source | `apk_unreadable` |
| `GUEST_MANIFEST_INVALID` | AXML parse failure or missing required field | no | `manifest_invalid` |
| `GUEST_LAUNCHER_NOT_FOUND` | No `MAIN`+`LAUNCHER`/`INFO` activity | no | `launcher_not_found` |
| `GUEST_SLOT_UNAVAILABLE` | No free slot, or one slot already active | yes, after close | `no_free_slot` / `slot_busy` |
| `GUEST_CLASS_NOT_FOUND` | Guest class absent from the verified DEX | no | `class_not_found` |
| `GUEST_RESOURCES_UNSUPPORTED` | Resource path unavailable per Spike A decision | no | `resources_unavailable` |
| `GUEST_BIND_FAILED` | Guest Application/Activity construction or attach threw | no, until guest closes | `bind_failed` |
| `GUEST_LIFECYCLE_REJECTED` | Slot generation mismatch or out-of-order callback | yes, with a fresh booking | `generation_mismatch` |
| `GUEST_PERMISSION_DENIED` | Guest permission denied by host policy (§9) | no | `permission_denied` |
| `GUEST_NOT_IMPLEMENTED` | Op exists on the channel but its milestone has not landed | no | `not_implemented` |
| `GUEST_INTERNAL` | Unexpected host failure in the guest path | yes | `internal_error` |

Existing host codes are untouched: `UNSATISFIED_LINK`, `NATIVE_ERROR`, and `notImplemented`
for unknown methods. Trust failures always map to `GUEST_TRUST_REJECTED`; the underlying
`UntrustedGuestApkException` message stays in the log only.

## 8. M5 payload contract

Request/response ops stay on the existing channel `aether/runtime`
(`MethodChannel('aether/runtime')`, unchanged in both Dart and Kotlin). The event stream uses a
new `EventChannel("aether/guest-events")`. All maps use `String` keys and JSON-compatible
values; `null` means "absent", never "unknown".

### 8.1 `guestInstall`

Request: `{"source": "<absolute path>", "signerPins": ["<sha256 hex>"]}`
(`signerPins` optional only if the host build supplies pins from configuration; an empty list
is rejected with `GUEST_TRUST_REJECTED`, never silently accepted.)

Response `GuestIdentity`:

```json
{
  "packageName": "com.miniclip.eightballpool",
  "versionName": "56.30.0",
  "versionCode": 4028,
  "apkSha256": "2710e403…251b6",
  "signerSha256": ["5bee86ed…2405"],
  "applicationClass": "com.miniclip.eightballpool.EightBallPoolApplication",
  "launcherActivity": "com.miniclip.eightballpool.EightBallPoolActivity",
  "permissions": ["android.permission.INTERNET"],
  "installedAt": 1760000000000,
  "schemaVersion": 1
}
```

### 8.2 `guestLaunch`

Request: `{"slot": 0}` where `slot` is optional (`null` = automatic selection per §4).
Response `GuestLaunchResult`: `{"slot": 0, "proxyType": "standard-p0", "generation": 1,
"landscape": false, "activityClass": "…"}`.

### 8.3 `guestClose`

Request: `{"slot": 0}` (optional; `null` closes the active booking). Response: `null`.

### 8.4 `guestState`

Response `GuestSessionState`:

```json
{
  "hostState": "READY",
  "nativeRuntimeState": "running",
  "guest": { "…GuestIdentity or null…" },
  "active": { "slot": 0, "proxyType": "standard-p0", "generation": 1,
              "phase": "RESUMED", "landscape": false },
  "slots": [
    { "proxyType": "standard-p0", "slot": 0, "phase": "RESUMED", "generation": 1,
      "guestPackageName": "com.miniclip.eightballpool" },
    { "proxyType": "standard-p1", "slot": 1, "phase": "DESTROYED", "generation": 1,
      "guestPackageName": null }
  ],
  "flags": { "DYNAMIC_APK_LOADING": false, "GUEST_CONTAINER": false },
  "lastError": { "code": "GUEST_SLOT_UNAVAILABLE", "detail": "slot_busy",
                 "at": 1760000000000 }
}
```

`slots[]` is exactly the `VirtualActivitySlotSnapshot` projection — the registry stays the
single source of truth for slot phases. `lastError` is `null` when nothing failed.

### 8.5 `guestEvents` stream

Each event: `{"seq": 12, "at": 1760000000000, "kind": "ACTIVITY", "lifecycle": "resumed",
"proxyType": "standard-p0", "slot": 0, "generation": 1, "guestPackageName": "…",
"errorCode": null}`.

- `seq` is monotonic per attachment; Dart may use it to detect gaps after a UI pause.
- `kind` ∈ `HOST`, `ACTIVITY`, `SERVICE`, `JOB_SERVICE`, `BROADCAST_RECEIVER`,
  `CONTENT_PROVIDER`, `VPN_SERVICE` (existing `HostComponentKind`).
- `errorCode` is a §7 code or `null`; it is the only error field in the stream.
- Stream events are emitted on the main thread; the Dart side must not block in the handler.

### 8.6 Dart surface

`AetherChannel` gains `guestInstall`/`guestLaunch`/`guestClose`/`guestState` and an
`EventChannel('aether/guest-events')` subscription, all wrapped by `NativeService` so
`MethodChannel` usage stays inside `channels/aether_channel.dart`. Each wrapper throws
`StateError` with `code`/`detail` on `PlatformException`, matching the existing wrappers.
Unknown methods keep returning `notImplemented` on the Kotlin side.

## 9. Non-goals (Phase 2 — inherited from `phase2-plan.md` §1)

These must not regress; each is a deliberate limit, not a backlog item hidden in the spec.

1. **No sandbox claims.** Guest code shares the host UID, permissions, process, and I/O. The
   class loader and file facade are delegation policies, not isolation.
2. **No hidden-API bypass, signature or permission spoofing, package-manager spoofing.** Guest
   identity substitution is limited to the `GuestPackage`/`GuestContext` data surface (§3); no
   framework identity is rewritten.
3. **No anti-cheat, anti-tamper, or licensing bypass.** Snake's identity-bundle and `gcuid`
   mechanisms are documented as evidence, never copied.
4. **No target APK, DEX, or native libraries committed to Git.** Signer pins come from a
   verified certificate supplied out-of-band and are configuration only.
5. **No multi-process slots, binder servers, or native hooking in Phase 2.** `:p0..:p3`, the
   ContentProvider handshake spawn, real task/back-stack mapping, and native lane growth are
   Phase 3 (§5 of the plan).
6. **No permission auto-grant.** Guest-declared permissions map to a host allow-list; runtime
   permissions are requested through Flutter UI (M4).
7. **No unverified native contract growth.** `libaether.so` stays at the verified 4-method
   contract; `message_bridge` gains callers only with a documented payload.
8. **No new exported components.** Every proxy stays `exported=false` except the launcher
   `MainActivity` and the system-bindable `ProxyVpnService` (still feature-off).
9. **No flagger-gated capability enabled by default.** `DYNAMIC_APK_LOADING` and the new
   `GUEST_CONTAINER` both default to disabled, and flags are not a security boundary — the
   trust policy is.

## 10. Open decisions carried by M0

| # | Decision | Blocking | Recorded in |
| --- | --- | --- | --- |
| M0.2 | Resource/asset access path for a private APK (public path · `addAssetPath` · documented fallback) | M2, then M3 | §3 table row `getResources()` + [architecture.md](architecture.md) |
| M0.3 | Manifest parsing path (`getPackageArchiveInfo` vs in-house AXML reader) | M1 | §2 field set + `verify_host_structure.py` fixture test |
| M0.4 | Fixture guest APK (prebuilt vs Gradle-built, license/notice) | all CI tests | M0.4 record |
| M0.5 | Production wiring for `AetherRuntime.bootstrap` (candidate `AetherApplication.onCreate`), the install-op surface that calls `HostInitializer.loadTargetApk`, the `flagger` toggle path, and the `message_bridge` payload contract | M3, M5 | §6.1, §8.6 |

Open inputs that only the user can supply (plan §6): test device/emulator details (#4),
confirmation of authorization to run/inspect the target APK (#5), optional logcat (#6). None of
them blocks M0–M2.

## 11. Traceability

| Spec section | Milestone | Verified by |
| --- | --- | --- |
| §2 `GuestPackage` + persistence | M1 | JVM tests against the in-repo manifest fixture; parse → persist → reload determinism test |
| §3 `GuestContext` | M2 | Instrumented test loading a guest string, asset, and theme attribute |
| §4 scheduler | M3 | JVM tests for booking, contention, orientation preference, release idempotency |
| §5 thread rules | M1–M3 | Code review gate + instrumentation assertions that guest lifecycle runs on the main looper |
| §6 event flow | M1, M3, M4 | Instrumentation observing `guest_installed`/`guest_launched`/`guest_closed` through the relay |
| §7 error codes | M1, M3, M5 | One JVM/channel test per code; Dart wrappers matched against the table |
| §8 M5 payloads | M5 | Dart channel-wrapper tests + recorded manual run-through |
| §9 non-goals | all | `verify_host_structure.py` manifest/exported rules; review checklist in [host-container.md](host-container.md) |