# Guest Container Specification (Phase 2 / M0.1)

Status: **draft contract — no code ships with this document** · Date: 2026-10-04 ·
Milestone: Phase 2 M0.1 (+ M0.2 Spike A §3.1, M0.3 Spike B §2.1 resolved) · Base: `main` @ `630c474`

This document fixes the contracts that Phase 2 milestones M1–M5 implement: which objects
exist, who owns them, which thread each operation runs on, how guest lifecycle events flow,
which error codes are stable, and what payloads the Flutter control plane exchanges.

Everything in §2–§9 is a **proposed contract, not current behavior**. Statements about what
the host does today are marked *Exists today* and point at real source; nothing in this file
should be read as a claim that a guest Activity can already run. The single-process MVP
decision (2026-10-04) and the milestone scope live in [phase2-plan.md](phase2-plan.md); this
file is M0.1's deliverable inside that plan.

## 0. Related documents

| Document | Relationship |
| --- | --- |
| [phase2-plan.md](phase2-plan.md) | Milestones M0–M6, gap matrix, decision log, required inputs. This spec implements M0.1. |
| [host-container.md](host-container.md) | Current capability inventory, proxy inventory, trust/security constraints. Source of §10 non-goals. |
| [architecture.md](architecture.md) | Four ownership layers, separate guest APK lane, two JNI paths. |
| [lifecycle.md](lifecycle.md) | Existing native/Flutter/proxy lifecycles and the relayed event list. Extended by §7/§8. |
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
    ├── GuestManifestParser ──parses──►  AndroidManifest.xml of the verified APK → GuestPackage (§2.1)
    │   └── ApkEntrySource   ──reads───►  allow-listed entries of the verified APK: manifest + assets/** (§2.1, §3.1)
    ├── GuestSlotScheduler   ──books──►  proxy slot (VirtualActivitySlotRegistry snapshots)
    ├── GuestRuntimeAdapter  ──binds──►  guest Application + Activity inside one booked slot
    ├── GuestPackage         ──model──►  parsed/persisted manifest of one verified APK
    ├── GuestContext         ──facade─►  GuestPackage + GuestVirtualFileSystem + GuestClassLoaderProxy + resources
    └── GuestAssetArchive    ──reads───►  assets/** inside the verified read-only cached APK (§3.1)
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
4. Every object crossing a thread boundary is immutable or explicitly synchronized; see §6.
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
| `permissions` | `List<String>` | manifest `uses-permission` | Declared by the guest; the host maps them to the policy in §10. |
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
4. Otherwise the session is invalid → error `GUEST_LAUNCHER_NOT_FOUND` (§8).

**Persistence**

- Path: `<noBackupFilesDir>/aether-guest-data/<packageName>/<versionCode>/<apkSha256>/package.json`,
  i.e. inside the existing `GuestVirtualFileSystem.forGuest` root. Written with that facade, so
  the same traversal/symlink rules apply.
- Format: UTF-8 JSON, deterministic key order (the field order of the table above),
  two-space indent. Determinism is required so parse → persist → reload is byte-stable and
  testable.
- Reload: a model is reloaded only when `apkSha256` matches a currently verified, cached APK.
  A digest with no matching verified APK is deleted, never trusted.

### 2.1 Spike B decision record — guest manifest parsing (M0.3, resolved 2026-10-04)

**Question.** How does the host build the §2 model — components, intent filters, permissions,
SDK levels, application class, launcher — from the compiled `AndroidManifest.xml` inside a
verified but uninstalled APK?

**Method and its limits.** No Android runtime was available (the sandbox has no JDK/Android
SDK and plan §6 input #4 is still missing), so option (b) was settled from the framework's own
API surface rather than by executing it, while option (a) was validated by a **throwaway
prototype reader written from the documented AOSP chunk format** and run against (i) the real
`AndroidManifest.xml` entry of the verified APK and (ii) `docs/AndroidManifest.xml`, the copy
already committed to this repository. The prototype is a spike instrument only and is not
committed; the production parser is M1 Kotlin.

**Evidence**

| # | Finding | Source |
| --- | --- | --- |
| B1 | `PackageInfo` exposes `packageName`, `versionCode`/`versionName`, `applicationInfo`, `activities`/`services`/`receivers`/`providers`, `requestedPermissions` — and **no intent-filter data anywhere** in `PackageInfo` or `ComponentInfo`. Launcher resolution (§2) and M4's dispatch both need filters, so the framework result alone cannot produce this model. | AOSP `android/content/pm/PackageInfo.java` field list |
| B2 | `getPackageArchiveInfo` needs an Android runtime. This repository's CI runs JVM unit tests only (no emulator job) and sets `unitTests.isReturnDefaultValues = true`, so framework calls return defaults — option (b) could not be covered by the existing CI gate without adding instrumentation infrastructure. | `.github/workflows/ci.yml`, `android-host/build.gradle.kts` |
| B3 | The platform exposes no public binary-XML reader: `android.content.res` chunk APIs are internal, and `XmlResourceParser` instances come from resolved resources, not from an arbitrary archive path. | AOSP package structure |
| B4 | Compiled `AndroidManifest.xml` is a small, stable chunk format: `RES_XML` (0x0003) → string pool (0x0001) → optional resource map (0x0180) → nodes 0x0100–0x0104, with fixed 20-byte attribute records and both UTF-8 and UTF-16 pools decoded from one offsets array. | AOSP `ResourceTypes.h` / `AssetManager` AXML layout |
| B5 | Measured on the real entry: 84 492 bytes, 457 string-pool entries, 437 elements, 953 attributes; parse time **1.78 / 1.91 / 2.32 ms** (min / median / max) over 20 runs. | prototype run against `local/target-apk/8bp-56.30.0-4028.apk` |
| B6 | Values match the independently recorded binary facts: package `com.miniclip.eightballpool`; versionCode `4028` / versionName `56.30.0`; minSdk `23` / targetSdk `36`; application `…EightBallPoolApplication`; 106 activities; 15 `uses-permission`; launcher resolved to `…EightBallPoolActivity` via `MAIN`+`LAUNCHER`. Newly measured: 20 services, 22 receivers, 15 providers (all carrying `authorities`), 6 activity intent-filters, 45 activities with explicit `exported`, 21 with `launchMode`, 1 `uses-feature`, `compileSdkVersion 36`. | prototype vs [target-apk.md](target-apk.md) |
| B7 | The same prototype parsed the committed `docs/AndroidManifest.xml` fixture to identical results, so CI can cover this parser **without the target APK and without a device**. | prototype run against `docs/AndroidManifest.xml` |
| B8 | Negative tests: child chunk size `0`, child chunk size huge, mid-node truncation, plain-text (uncompiled) XML, `stringCount = 4 294 967 280`, and an attribute block with `attributeSize < 20` must all fail closed; budget knobs for string count, node count, depth, and per-element attribute count are enforceable. **Prototype finding:** three of those inputs surfaced *untyped* host errors instead of a domain error — so the production parser must bounds-check every read and map every failure to `GUEST_MANIFEST_INVALID`; no exception type may escape (§8). | prototype negative tests |
| B9 | *(assessment)* Third-party readers (`apk-parser`-style libraries, apktool's `AndrolibResources`) would add a runtime Maven dependency, its supply-chain and method-count surface, and apktool additionally expects an extracted project on disk and decodes `resources.arsc` — a scope Spike A already excluded — for what this model needs from one 82 KB entry. | spike assessment |

**Options evaluated**

| Option | Verdict | Why |
| --- | --- | --- |
| (a) AXML reader | **chosen — an in-house minimal reader (`GuestManifestParser`), not a third-party library** | It is the only way to obtain intent filters, filters, and component detail (B1, B3); it is pure Java/Kotlin, so the existing JVM test gate can cover it against the committed fixture (B2, B7); it needs no new dependency (B9); and the format is small and stable, measured at ~1.9 ms for this APK (B4, B5). |
| (b) `getPackageArchiveInfo` | **kept for trust verification only, rejected as the model source** | It is authoritative for package/version/signer identity and is already the basis of `GuestApkTrustPolicy` inside `DynamicApkLoader` — that stays. But it cannot return intent filters (B1), needs an Android runtime the CI gate does not have (B2), and the platform offers no public parser to fill the gap (B3). |

**Chosen approach — hybrid, each tool used where it is authoritative**

1. `DynamicApkLoader` (existing) verifies package/version/signer pins with `getPackageArchiveInfo`
   against a read-only private copy **before** any manifest parsing. Unchanged.
2. `GuestManifestParser` (new) parses the compiled manifest of that same verified file and
   produces the §2 model.
3. The parser's identity fields are cross-checked against the values the trust policy accepted;
   a disagreement fails the install with `GUEST_MANIFEST_INVALID` (a platform-rejected APK is
   `GUEST_TRUST_REJECTED` instead).

**New components (M1)**

- **`GuestManifestParser`** — `com.aether.host.virtualization.package`, pure Kotlin with **no
  Android imports** (so it runs in JVM unit tests): `parse(bytes: ByteArray): GuestManifest`.
  Namespace-aware for `android:` only; unknown prefixes such as `tools:` are ignored, not
  merged. Manifest declaration order is preserved. Values decode as: string-pool reference →
  string, `TYPE_INT_DEC`/`TYPE_INT_HEX` → decimal text, `TYPE_INT_BOOLEAN` → `true`/`false`,
  reference types → `@0x%08x` (compiled manifests carry no resource *names*, consistent with
  §3.1). Hard budgets: input ≤ 8 MiB, string count ≤ 65 536, nodes ≤ 200 000, depth ≤ 64,
  attributes per element ≤ 1 024. Any violation → `GUEST_MANIFEST_INVALID`.
- **`ApkEntrySource`** — allow-listed, read-only ZIP access to the verified cached APK
  (`AndroidManifest.xml` plus `assets/**`), built from the digest `GuestApkTrustPolicy` accepted.
  It owns the single `ZipFile` handle and is shared by `GuestManifestParser` (M1) and
  `GuestAssetArchive` (M2). This **amends §3.1**: `GuestAssetArchive` is the assets-scoped view
  over `ApkEntrySource` rather than owning its own handle.
- **`GuestManifest`** — the immutable parse result, immediately mapped to `GuestPackage` (§2);
  kept separate so parsing can be unit-tested without persistence.

**Exact field set exposed to `GuestPackage`**

| `GuestPackage` field | AXML source | `getPackageArchiveInfo` has it? |
| --- | --- | --- |
| `packageName`, `versionName`, `versionCode` | `manifest@package` / `@android:versionName` / `@android:versionCode` (+ `versionCodeMajor`) | yes (authoritative, trust-verified) |
| `minSdkVersion`, `targetSdkVersion` | `uses-sdk@android:minSdkVersion` / `@android:targetSdkVersion` | indirectly |
| `applicationClass` | `application@android:name` | yes |
| `permissions` | `uses-permission@android:name` (declaration order) | yes |
| `usesFeatures` | `uses-feature@android:name` + `@android:required` | partially |
| `activities` / `services` / `receivers` / `providers` | `@android:name`, `@android:exported`, `@android:process`, `@android:launchMode`, `@android:permission`, `@android:authorities`, `@android:enabled`, nested `intent-filter` → `action`/`category`/`data` | components yes, **intent filters no** |
| `launcherActivity` | resolved from the filters above (§2) | **no** |
| `apkSha256`, `signerSha256`, `installedAt`, `schemaVersion` | host-computed / host-written, never from the manifest | `signerSha256` yes |

**Behaviour implications accepted by this decision**

1. Parse order is trust-first: no manifest byte is read before the trust policy accepts the file.
2. The model is order-preserving and free of clock/locale input, so parse → persist → reload
   stays byte-stable (§2 persistence, §12).
3. Parser failures never leak implementation detail to Dart: one code, `GUEST_MANIFEST_INVALID`
   with detail `manifest_invalid` (§8), regardless of which guard tripped.
4. `DynamicApkLoader`'s `getPackageArchiveInfo` call is **not** removed; the trust path is
   unchanged, and the platform stays the authority on identity and signing.
5. `resources.arsc` remains out of scope (Spike A), so no resource ids exist in the model even
   though `theme`/`icon` attributes parse as `@0x%08x`.
6. The prototype is disposable: M1's Kotlin parser must reproduce B5–B8 measurements, and the
   committed `docs/AndroidManifest.xml` fixture is the regression input.

## 3. `GuestContext` — resources, class loading, data dirs (M2)

A `ContextWrapper` over the host `Application` context, with every guest-visible accessor
overridden. Chosen over a hand-written `Context` subclass because guest code casts `Context` to
framework types (`ContextThemeWrapper`, `Resources.Theme`, `InputMethodManager` lookups);
a partial `Context` would fail at runtime in ways a `ContextWrapper` cannot.

| Member | Backed by | Rule |
| --- | --- | --- |
| `getPackageName()` | `GuestPackage.packageName` | Returns the guest name; the host package name is never returned to guest code. This is a data substitution, not package-manager spoofing (§10). |
| `getApplicationInfo()` / `getApplicationInfo()` | `GuestPackage` + `ApplicationInfo` built from it | `sourceDir`/`publicSourceDir` point at the verified read-only cached APK; `dataDir`/`deviceProtectedDataDir` at the guest VFS roots; the host UID/permissions are inherited and **not** rewritten. |
| `getClassLoader()` | `GuestClassLoaderProxy` | Parent-first prefixes unchanged. |
| `getAssets()`, `getResources()` | host `Resources` + `GuestAssetArchive` (§3.1) | **Decided by Spike A.** Guest `resources.arsc` is not loaded and guest resource ids are not mapped, so `getResources()` returns host resources. Guest **assets** are read from the verified cached APK by path through `GuestAssetArchive`; guest code calling `getAssets()` itself still reaches host assets (see §3.1 for why that cannot be repointed publicly). |
| `getFilesDir()`, `getCacheDir()`, `getCodeCacheDir()`, `getNoBackupFilesDir()`, `getDatabasePath()`, `getSharedPreferencesPath()` | `GuestVirtualFileSystem` | Every returned path lies below the guest root; `..`, absolute paths, and symlinks are rejected by the facade, never re-implemented. |
| `getPackageManager()`, `getPackageInfo()` | **not virtualized** | Return host-package data. Recorded limitation; guests that depend on real package visibility are unsupported in Phase 2 (§10). |
| `getSystemService()` | host | Real host services. Documented consequence: a guest sees the host's services, which is part of the "not a sandbox" statement, not a feature. |

`GuestContext` is created once per session, on a worker thread, and is then read-only.
It is never re-parented into a different session, and never shared between slots.

### 3.1 Spike A decision record — guest resource & asset loading (M0.2, resolved 2026-10-04)

**Question.** How does the host give guest code the resources and assets of a verified APK that
is *not* installed, without opening the APK for installation and without violating the
no-hidden-API stance in [host-container.md](host-container.md)?

**Method and its limits.** This spike had no device or emulator available (the sandbox has no
JDK/Android SDK, and plan §6 input #4 — test device details — is still missing), so no runtime
measurement was taken. Instead the three options were settled on API-surface evidence (AOSP
source + Google's published non-SDK policy) plus measurements of the verified target APK
container and a sample of its DEX. Because options (a) and (b) are eliminated on API-surface
grounds rather than on runtime behaviour, the recommendation does not depend on the deferred
on-device run; that run stays recorded as a follow-up in §11.

**Evidence**

| # | Finding | Source |
| --- | --- | --- |
| E1 | `AssetManager` is a **`public final class`** — it cannot be subclassed, so a "custom asset manager" cannot be written. | AOSP `frameworks/base/core/java/android/content/res/AssetManager.java` |
| E2 | Its public constructor is `@hide` + `@UnsupportedAppUsage`, documented "Not for use by applications". | same, `public AssetManager()` |
| E3 | `addAssetPath(String)` is `@Deprecated @UnsupportedAppUsage` and `@hide`-documented; its replacement `setApkAssets(ApkAssets[], boolean)` is also `@hide`, and `ApkAssets` is a hidden type. | same, both methods |
| E4 | The resource-lookup helpers on `AssetManager` (`getResourceValue`, `getResourceName`, `getResourceIdentifier`, …) are package-private / `@hide` — the host cannot even query guest resource ids through public API. | same, method declarations |
| E5 | `Resources(AssetManager, DisplayMetrics, Configuration)` is public but useless without a guest-bound `AssetManager`; the supported way to build guest `Resources` (`ResourcesManager` + idmap) is hidden on every supported release. | AOSP `Resources`/`ResourcesManager` |
| E6 | `Context.createPackageContext(String, int)` is public but resolves an **installed** package (`NameNotFoundException` otherwise), so it cannot serve an uninstalled APK. Excluded by design: the host does not install the guest and does not spoof the package manager (§10). | Android `Context` API reference |
| E7 | `PackageManager.getPackageArchiveInfo` — already used by `DynamicApkLoader` — returns metadata (`PackageInfo`/`ApplicationInfo`) only; it never yields `Resources` or `AssetManager`. | existing host usage + API reference |
| E8 | Non-SDK policy: `unsupported` members are callable today but may move into `max-target-x` lists and then the blocklist, where access throws (`NoSuchMethodError`/…). The host is `targetSdk 34` / `compileSdk 36`, so non-SDK use is a live breakage risk, not a contract. | Android "Restrictions on non-SDK interfaces" |
| E9 | Target container: `resources.arsc` 2.64 MB; `res/` 2 457 entries (1 426 compiled AXML, 1 010 PNG); `assets/` 3 018 entries / 61.6 MB excluding `.so`; `lib/` 38 entries / 176 MB. | measured on `local/target-apk/8bp-56.30.0-4028.apk` (gitignored) |
| E10 | In the 11 committed DEX files (40.6 MB of 22), `getResources` appears 14×, `AssetManager` 12×, `getAssets` 7×, and no `assets/unpack` path or `System.loadLibrary` string appears — Java-level asset use is small next to a native engine that consumes content by file path. | measured on `docs/classes*.dex` (partial sample by design) |

**Options evaluated**

| Option | Verdict | Why |
| --- | --- | --- |
| (a) Public resource path for an uninstalled APK | **Rejected — does not exist** | E1–E7: the only public routes all require the package to be installed, and the one object that could carry a path (`AssetManager`) can neither be constructed nor subclassed through public API. |
| (b) `AssetManager.addAssetPath` (plus the hidden constructor) | **Rejected for Phase 2** | Requires three hidden surfaces (constructor, `addAssetPath`/`setApkAssets`, `ApkAssets`) and, for guest *resources*, hidden `ResourcesManager`/idmap machinery (E2–E5). It directly contradicts non-goal 2, is unstable at `targetSdk 34`+ (E8), and still does not give guest code a repointable `getAssets()` because the class is `final` (E1). |
| (c) Fallback: host resources + guest assets read from the verified cached APK | **Chosen** | Uses only public API (`java.util.zip.ZipFile` over the APK that `DynamicApkLoader` already copied, verified and made read-only). Keeps the no-hidden-API stance, is testable off-device with JVM tests, and covers the asset half of the problem for host-mediated reads. |

**Chosen approach and new components (M2)**

- **`GuestAssetArchive`** — assets-scoped view over `ApkEntrySource` (§2.1), which owns the
  read-only `ZipFile` handle on the verified cached APK and exposes only `assets/**`. Rejects
  absolute paths, `..` components, and any entry outside `assets/`. API:
  `openAsset(path): InputStream`, `listAssets(dir): List<String>`, `assetSize(path): Long`,
  `hasAsset(path): Boolean`. Lives in `com.aether.host.virtualization.assets`; created once per
  session on a worker thread, closed by `HostInitializer.shutdown()`.
- **Asset facade** — `GuestAssetArchive` plus the existing `GuestVirtualFileSystem` form the
  asset surface: reads come from the archive, writes go to the guest root. This is the *host*
  surface (adapter code, diagnostics, M5 payload previews), **not** a replacement for guest
  `Context.getAssets()`, which cannot be repointed publicly (E1).
- **`GuestResources`** — thin wrapper over host `Resources` that resolves an explicit
  allow-list of guest lookups *by archive path* and otherwise behaves as host resources. A guest
  resource id that is not in the allow-list yields `GUEST_RESOURCES_UNSUPPORTED`; no id mapping,
  no idmap, no overlay table is built.

**Behaviour implications accepted by this decision**

1. Guest code calling `getResources().getString(R.x)`, theme attributes, or `LayoutInflater`
   on guest layouts cannot work — guest resource ids are not mapped. Guest UI must be
   self-contained.
2. Guest code calling `getAssets()` receives **host** assets. Only host-mediated asset reads go
   to the archive.
3. Content the guest's native engine reads by file path is not intercepted; that is already true
   for `GuestVirtualFileSystem` and stays true (not a sandbox).
4. For the pinned target specifically (E9/E10: a native engine with 61.6 MB of assets and
   176 MB of native libraries), the realistic Phase 2 outcome is *guest code executes inside a
   proxy slot with guest data dirs and archive-backed asset reads* — **not** a guest-rendered
   game UI. M3's acceptance text and [target-apk.md](target-apk.md) must state this plainly
   instead of implying a playable guest.
5. Any path to guest-rendered UI (isolated non-SDK lane, idmap-based resource loading, or a
   runtime resource-plugin framework) is a **stance decision for the user**, tracked in §11 — it
   is not an M2 implementation detail and must not be introduced silently.

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
| One active guest slot | At most one slot in `ACTIVE` at a time. A second launch while a guest Activity is `RESUMED` is refused with `GUEST_SLOT_UNAVAILABLE` rather than stealing focus. Phase 2 has no real task stack (§10). |
| Orientation preference | `book(orientation)` prefers the `_L` variant when the host configuration is landscape, otherwise the portrait variant. Fallback to the other variant is allowed and recorded in the booking result. |
| Selection order | Free slots are chosen lowest index first (`P0` → `P3`), then the orientation variant of the same index. Deterministic, so tests and diagnostics are reproducible. |
| Generation check | The adapter re-reads `VirtualActivitySlotSnapshot.generation` before each guest lifecycle forward. A mismatch (the slot was recreated) aborts with `GUEST_LIFECYCLE_REJECTED` instead of calling guest code on a stale Activity. |
| Release | Release happens on the slot's `destroyed` event, on the main thread, and is idempotent. Bookings do not survive `HostInitializer.shutdown()`, which clears the registry. |
| No retention | The scheduler stores slot ids, generations, and guest component names — never Activity, Service, or guest framework references. Same rule as the registry. |

`VirtualActivitySlotRegistry` is used read-only for this purpose (`snapshot()`); Phase 2 adds no
new state to it. The M5 diagnostics screen reads the same snapshots (§9.4).

## 5. `GuestRuntimeAdapter` — binding guest components into a booked slot (M3)

One adapter instance per booked slot. It is the only object that touches guest framework
objects, and it exists exactly as long as its booking.

| State | Type | Notes |
| --- | --- | --- |
| `guestPackage` | `GuestPackage` | Immutable; never re-read during a binding. |
| `context` | `GuestContext` | Session-wide instance (§3), built at install time on a worker thread and shared read-only by every slot. |
| `booking` | `GuestBooking` | `slot`, `proxyType`, `generation`, `landscape` — copied from `GuestSlotScheduler` at booking time. |
| `guestApplication` | `Any?` | The guest's `android.app.Application` instance while bound; typed loosely because the class comes from guest DEX. |
| `boundComponent` | `String?` | Guest Activity class name currently attached to the slot. |
| `attached` | `Boolean` | Whether the guest view hierarchy is attached to the proxy Activity's content view. |

**Responsibilities** (all on the main thread, per §6)

1. `attach()` on the slot's `created` event, in this order: require `flagger(GUEST_CONTAINER)` and a
   live booking → re-check the slot generation against §4 → construct the guest `Application`
   through `GuestClassLoaderProxy` → `attachBaseContext(GuestContext)` → `onCreate()` → construct
   `GuestPackage.launcherActivity` and attach its guest view to the slot. Any failure unwinds what
   it created and reports the §8 code; a partially bound slot is never left behind.
2. `forwardLifecycle(event)` for every later slot event, mapping host → guest callbacks:
   `started→onStart`, `resumed→onResume`, `paused→onPause`, `stopped→onStop`,
   `destroyed→onDestroy`. `save_instance_state` and `new_intent` are recorded and surfaced in the
   event stream but not forwarded to guest code in Phase 2 — no guest task state exists (§10);
   this is a documented limit, not a silent drop.
3. `release()` on the slot's `destroyed` event: detach the guest view, call `onDestroy`, clear
   `guestApplication` and `boundComponent`, return the booking to the scheduler. Idempotent.

**Invariants**

- One adapter per slot; a second `attach()` on the same slot is refused with
  `GUEST_LIFECYCLE_REJECTED`.
- The adapter holds no Activity/Service reference beyond its own binding lifetime and never
  places one in the registry, in `flagger`, or in a channel payload (§1 rule 5).
- Guest callbacks receive exactly the context surface defined in §3; the adapter never patches
  framework identity or permissions.
- No blocking I/O runs inline in a listener callback: asset/resource reads go to the guest
  worker executor and their results are applied back on the main thread (§6).

## 6. Thread rules

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

## 7. Event flow

### 7.1 Install (M1 + M5)

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

### 7.2 Launch (M3 + M5)

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

### 7.3 Close and shutdown

```text
guestClose → finish() on the booked proxy Activity → destroyed → adapter.release()
           → scheduler.release(slot) → HostInitializer.dispatch(HOST, "guest_closed")
HostInitializer.shutdown() → all bookings released → guest_closed for each → registry.clear()
```

### 7.4 Event vocabulary

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
| `guest_error` | Any guest-scoped failure surfaced to listeners | `null` | Payload uses §8 codes. |

Listener contract is unchanged: listeners are called synchronously on the main thread and must
not retain `owner` past its destroy/stop event.

## 8. Stable error codes

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
| `GUEST_PERMISSION_DENIED` | Guest permission denied by host policy (§10) | no | `permission_denied` |
| `GUEST_NOT_IMPLEMENTED` | Op exists on the channel but its milestone has not landed | no | `not_implemented` |
| `GUEST_INTERNAL` | Unexpected host failure in the guest path | yes | `internal_error` |

Existing host codes are untouched: `UNSATISFIED_LINK`, `NATIVE_ERROR`, and `notImplemented`
for unknown methods. Trust failures always map to `GUEST_TRUST_REJECTED`; the underlying
`UntrustedGuestApkException` message stays in the log only.

## 9. M5 payload contract

Request/response ops stay on the existing channel `aether/runtime`
(`MethodChannel('aether/runtime')`, unchanged in both Dart and Kotlin). The event stream uses a
new `EventChannel("aether/guest-events")`. All maps use `String` keys and JSON-compatible
values; `null` means "absent", never "unknown".

### 9.1 `guestInstall`

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

### 9.2 `guestLaunch`

Request: `{"slot": 0}` where `slot` is optional (`null` = automatic selection per §4).
Response `GuestLaunchResult`: `{"slot": 0, "proxyType": "standard-p0", "generation": 1,
"landscape": false, "activityClass": "…"}`.

### 9.3 `guestClose`

Request: `{"slot": 0}` (optional; `null` closes the active booking). Response: `null`.

### 9.4 `guestState`

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

### 9.5 `guestEvents` stream

Each event: `{"seq": 12, "at": 1760000000000, "kind": "ACTIVITY", "lifecycle": "resumed",
"proxyType": "standard-p0", "slot": 0, "generation": 1, "guestPackageName": "…",
"errorCode": null}`.

- `seq` is monotonic per attachment; Dart may use it to detect gaps after a UI pause.
- `kind` ∈ `HOST`, `ACTIVITY`, `SERVICE`, `JOB_SERVICE`, `BROADCAST_RECEIVER`,
  `CONTENT_PROVIDER`, `VPN_SERVICE` (existing `HostComponentKind`).
- `errorCode` is a §8 code or `null`; it is the only error field in the stream.
- Stream events are emitted on the main thread; the Dart side must not block in the handler.

### 9.6 Dart surface

`AetherChannel` gains `guestInstall`/`guestLaunch`/`guestClose`/`guestState` and an
`EventChannel('aether/guest-events')` subscription, all wrapped by `NativeService` so
`MethodChannel` usage stays inside `channels/aether_channel.dart`. Each wrapper throws
`StateError` with `code`/`detail` on `PlatformException`, matching the existing wrappers.
Unknown methods keep returning `notImplemented` on the Kotlin side.

## 10. Non-goals (Phase 2 — inherited from `phase2-plan.md` §1)

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
10. **No guest resource-id mapping and no non-SDK resource lane.** Guest `resources.arsc` is not
    loaded, no idmap/overlay table is built, and `AssetManager`/`ResourcesManager` internals are
    never touched reflectively (§3.1).

## 11. Open decisions carried by M0

| # | Decision | Blocking | Recorded in |
| --- | --- | --- | --- |
| M0.2 | Resource/asset access path for a private APK | ✅ **decided: option (c)** — public-API fallback, `GuestAssetArchive` over the verified cached APK; (a) does not exist, (b) needs hidden APIs | §3.1 (full decision record) |
| M0.3 | Manifest parsing path (`getPackageArchiveInfo` vs in-house AXML reader) | ✅ **decided: hybrid** — platform API stays authoritative for identity/signer trust, an in-house `GuestManifestParser` builds the model (the framework cannot return intent filters) | §2.1 (full decision record) |
| M0.4 | Fixture guest APK (prebuilt vs Gradle-built, license/notice) | all CI tests | M0.4 record |
| M0.5 | Production wiring for `AetherRuntime.bootstrap` (candidate `AetherApplication.onCreate`), the install-op surface that calls `HostInitializer.loadTargetApk`, the `flagger` toggle path, and the `message_bridge` payload contract | M3, M5 | §7.1, §9.6 |

**Deferred follow-ups (do not block M1/M2)**

| # | Item | Blocks |
| --- | --- | --- |
| F1 | On-device confirmation of Spike A (§3.1) — run the option (c) path on real hardware and re-measure; no option was chosen *because* of a runtime result, so this validates rather than decides | M3 manual validation |
| F2 | Stance decision for the user: allow an isolated non-SDK resource lane, or accept a guest that executes without rendering its own UI (§3.1 consequence 5) | M3 target expectations |

Open inputs that only the user can supply (plan §6): test device/emulator details (#4),
confirmation of authorization to run/inspect the target APK (#5), optional logcat (#6). None of
them blocks M0–M2.

## 12. Traceability

| Spec section | Milestone | Verified by |
| --- | --- | --- |
| §2 `GuestPackage` + persistence | M1 | JVM tests against the in-repo manifest fixture; parse → persist → reload determinism test |
| §2.1 `GuestManifestParser` + Spike B record | M1 | JVM tests parsing `docs/AndroidManifest.xml` (package/version/application class/106 activities/15 permissions/launcher), budget-limit rejection tests, malformed-input tests asserting `GUEST_MANIFEST_INVALID`, and a parse-time budget check against the measured ~2 ms |
| §3 `GuestContext` | M2 | Instrumented test: host resources resolve, `GuestVirtualFileSystem` paths stay inside the guest root, a guest resource id yields `GUEST_RESOURCES_UNSUPPORTED` |
| §3.1 `GuestAssetArchive` + Spike A record | M2 | JVM tests over the fixture APK (and the local target APK) asserting `assets/**` reads, path rejection, and that no `AssetManager`/`ResourcesManager` reflection appears in host source |
| §4 scheduler | M3 | JVM tests for booking, contention, orientation preference, release idempotency |
| §5 `GuestRuntimeAdapter` | M3 | Instrumentation asserting guest `Application.onCreate` + `Activity.onCreate/onResume` through the relay, and that `release()` is idempotent |
| §6 thread rules | M1–M3 | Code review gate + instrumentation assertions that guest lifecycle runs on the main looper |
| §7 event flow | M1, M3, M4 | Instrumentation observing `guest_installed`/`guest_launched`/`guest_closed` through the relay |
| §8 error codes | M1, M3, M5 | One JVM/channel test per code; Dart wrappers matched against the table |
| §9 M5 payloads | M5 | Dart channel-wrapper tests + recorded manual run-through |
| §10 non-goals | all | `verify_host_structure.py` manifest/exported rules; review checklist in [host-container.md](host-container.md) |