# Phase 2 Plan — Guest Container ("work like Snake")

Status: **proposed** · Base: `main` @ `91b0539` (CI green) · Date: 2026-10-04

Phase 2 turns today's host-container foundation into a working **guest container**: model a
verified guest APK as a package, resolve its launcher, and run its `Application` / `Activity`
inside Aether's existing proxy slots with guest resources and files — the capability class
that the Snake reference demonstrates. Milestones below are ordered by dependency, each with
acceptance criteria, tests, and documentation gates. Scope boundaries are inherited from the
existing documents; this plan does not widen them.

---

## 0. What was checked (existing content)

### Evidence base — Snake reference (`reference/snake-engine/`)

- [EVIDENCE_CHAIN.txt](reference/snake-engine/EVIDENCE_CHAIN.txt) — the full four-process
  model (`Main` Flutter UI · `:engine` server with fake AMS/PMS · `:p0..:p3` stub processes ·
  guest), the install chain (virtual `PackageParser` → `BPackage` → `package.conf` → component
  resolvers) and the launch chain (Flutter channel `"G"` → binder → stub scheduler →
  `ProxyActivity$P0..P3` → **ContentProvider handshake** spawns `:pN` → virtual ActivityThread
  binds guest `Application`/`Activity`).
- [NATIVE_CALLSITE_MAP.txt](reference/snake-engine/NATIVE_CALLSITE_MAP.txt) — native methods
  and call sites; the essential natives for a run chain (`ic`, `i`, `ac`) versus
  license/protection natives; the H-callback re-queue and `AppBindData` identity writes; the
  `p3` identity bundle; `Native.gcuid` UID mapping.
- [README.md](reference/snake-engine/README.md) and
  [RUNTIME_SNAPSHOT_REVIEW.md](reference/snake-engine/RUNTIME_SNAPSHOT_REVIEW.md) — evidence
  boundaries: Snake remains an architectural reference; nothing has been ported.

### Canonical Aether docs

- [host-container.md](host-container.md) — capability inventory + "Full virtualization work
  still required" (the canonical gap list) + trust/security constraints.
- [architecture.md](architecture.md) — four ownership layers, separate guest lane, two JNI paths.
- [lifecycle.md](lifecycle.md) — native state machine, Flutter channel lifecycle, proxy
  lifecycle relay table.
- [jni-contract.md](jni-contract.md) — implemented 4-method contract; reported 11+2 target
  contract is **not** implemented and must not be invented.
- [target-apk.md](target-apk.md) — pinned target identity and the signer-pin prerequisite.

### What the code actually contains today (verified)

| Area | State |
| --- | --- |
| Trust chain | `TargetApkContract` + `GuestApkTrustPolicy` + `DynamicApkLoader` (copy → verify → read-only cache → re-verify) + `GuestClassLoaderProxy` (guest-first, parent-first prefixes) |
| Files | `GuestVirtualFileSystem` — per-APK roots below `noBackupFilesDir`, traversal/symlink rejected |
| Proxy pools | Manifest-declared `ProxyActivityP0..P3` (+ landscape), transparent/pending slots, `ProxyServiceP0..P3`, `ProxyJobServiceP0..P3`, `ProxyBroadcastReceiver`, `ProxyContentProviderP0..P3`, `SystemCallProvider`, daemon services, `ProxyVpnService` (off) — **all single-process** (no `android:process`), all `exported=false` except launcher/VPN |
| Lifecycle | `VirtualActivity` → `HostInitializer.dispatch` → listeners + `VirtualActivitySlotRegistry` (P0..P3 snapshots, no Activity retention) |
| Runtime | `AetherRuntime` (LOADER/NATIVE/HOST module bootstrap) + `HostLifecycle` + `flagger` (all features off by default) |
| Control plane | `MethodChannel("aether/runtime")` both sides: `version`, `state`, `initialize`, `shutdown`, `ping` |
| Native | `libaether.so`: 4-method registered contract, `runtime_state` machine, `thread_dispatcher`, `message_bridge` already present |
| Tests/CI | 26 JVM tests + native runtime-state test + Flutter analyze/test; structure, Snake-payload and APK-architecture verifiers; CI green on `main` |
| Test fixture | `docs/AndroidManifest.xml` is the target's compiled manifest — already parsed in-repo; confirms `com.miniclip.eightballpool` / `56.30.0` / versionCode `4028` |

### Call-chain verification (2026-10-04)

Verified by tracing source, not by assumption:

| Chain | Result |
| --- | --- |
| Dart UI → `NativeService` → `AetherChannel` → `MethodChannel("aether/runtime")` | ✅ connected (`home_page.dart` instantiates `NativeService`) |
| Dart ↔ Kotlin method names and types (`version`/`state`/`initialize` bool/`shutdown` null/`ping`) + channel name | ✅ exact match on both sides |
| `MainActivity.configureFlutterEngine` → `AetherRuntimeChannel.attach`; `cleanUpFlutterEngine` → `detach` | ✅ wired |
| `AetherApplication.onCreate` → `hostInitializer.initialize()` → `Native.initialize()` | ✅ wired |
| Kotlin externals ↔ `kMethods` JNI table (4 names + descriptors, `com/aether/host/bridge/Native`) ↔ C++ impls | ✅ exact match; CMake compiles all 6 sources |
| `nativeInitialize`/`nativeShutdown` → `RuntimeStateManager` + `ThreadDispatcher.Start/Stop` | ✅ dispatcher has a real caller |
| 7 proxy components → `HostInitializer.dispatch` → slot registry | ✅ relay wired (`SystemCallProvider`/`FileProvider` intentionally inert) |
| `HostInitializer.loadTargetApk` | ❌ **no caller** in production or tests — surfaced by M5 install op |
| `AetherRuntime.bootstrap` → `HostLifecycle.attach` | ⚠️ **tests only** — production never attaches the lifecycle listener; wiring decision in M0.5 |
| `message_bridge` `{requestId, method, payload}` protocol | ⚠️ compiled into `libaether.so` but **zero callers** — contract lands with M5 |
| `flagger.DYNAMIC_APK_LOADING` | ⚠️ default off and **no production toggle** yet — added with the install op |

---

## 1. Goal and non-goals

**Phase 2 definition of done**

1. **Install-model**: parse, persist, and reload the pinned guest's package model
   (components, intent filters, application identity) from a verified APK.
2. **Launch**: resolve `MAIN`/`LAUNCHER` and run the guest `Application` + `Activity` inside
   an existing proxy slot, single host process, with guest ClassLoader + resources + VFS.
3. **Control plane**: Flutter can install, launch, and observe the guest (the equivalents of
   Snake's channels `"G"`/`"H"`), with diagnostics.
4. Every milestone ships with tests, doc updates, and the security gates in
   [host-container.md](host-container.md).

**Non-goals — inherited from existing docs, must not regress**

- No sandbox claims: guest code shares the host UID and permissions.
- No hidden-API bypass, signature/permission spoofing, package-manager spoofing, or
  anti-cheat / anti-tamper / licensing bypass. Snake's identity-bundle and `gcuid` mechanisms
  are documented, not copied (see Phase 3 decisions).
- No target APK / DEX / native libraries committed to Git; signer pins come from a verified
  certificate supplied out-of-band.
- Multi-process slots, binder servers, and native hooking are **out of Phase 2** (§5).

---

## 2. Capability gap matrix (Snake → Aether today → Phase 2 action)

| # | Snake mechanism (evidence) | Aether today | Phase 2 action |
| --- | --- | --- | --- |
| 1 | Virtual install: `PackageParser` → `BPackage` → persist → resolvers (EVIDENCE_CHAIN §0-B) | DEX copy + verify only; no package model, no persistence | **M1** — `GuestPackage` model + persistence + component resolver |
| 2 | Launcher resolution: `MAIN`+`LAUNCHER`/`INFO` → real activity class (§0-C) | absent | **M1** — intent resolution |
| 3 | Stub scheduler + slot booking `P0..P3` (NATIVE_CALLSITE_MAP #9/#10) | pools exist; no scheduler/booking | **M3** — slot scheduler over existing registry |
| 4 | Guest bind: virtual ActivityThread, `newApplication`, `callActivityOnCreate` (§0-E) | absent | **M3** — `GuestRuntimeAdapter` in proxy Activity |
| 5 | Guest Context/ClassLoader redirect (`z2`, `a3`) | ClassLoader exists; no Context | **M2** — guest Context/resources facade |
| 6 | Guest resources/assets resolution | absent (Snake `assets/`+`res/` are opaque data) | **M2** — resource loading (spike-gated) |
| 7 | Guest data dirs install steps (bi/eh/ci) | `GuestVirtualFileSystem` per-APK roots ✅ | reuse; **M1** wires install step |
| 8 | Multi-process `:p0..:p3` spawned via ContentProvider handshake (#11/#12) | single-process | **Phase 3** (§5) — Phase 2 runs in-process |
| 9 | Fake AMS/PMS binder servers, identity bundle `p3`, `gcuid` | deliberately absent (docs forbid spoofing) | **Phase 3 decision**, not scheduled |
| 10 | Service/Receiver/Provider dispatch through stubs | inert pools + relay ✅ | **M4** — manifest-driven dispatch |
| 11 | Flutter control plane channels (`"G"` launch, `"H"` install) | 5 generic ops | **M5** — guest ops + event stream + diagnostics |
| 12 | Native essentials `ic`/`i`/`ac`, hooks | 4-method contract + state/dispatcher/bridge | keep contract rules; only extend with verified evidence ([jni-contract.md](jni-contract.md)) |

---

## 3. Milestones

Sizes are t-shirt estimates (S ≤ ~1 day-equivalent, M ~ 2–4, L ~ 1–2 weeks). No calendar dates.

### M0 — Contract & spikes (S)

**M0.1 Guest-container spec — `docs/guest-container-spec.md`**

- Objects and ownership: `GuestPackage` (parsed package model), `GuestContext` (resources +
  class loader + data dirs), `GuestRuntimeAdapter` (Application/Activity bind), slot
  scheduler over `VirtualActivitySlotRegistry`.
- Thread rules (main-thread vs worker for parse/load/bind), lifecycle event flow from proxy
  → `HostInitializer.dispatch` → listeners, stable error codes, event payloads for M5.
- Explicit statement of what Phase 2 does **not** do (from §1 non-goals).

**M0.2 Spike A (riskiest unknown): resource & asset access**

- Options to evaluate on-device: (a) public resource/asset paths for a private APK,
  (b) `addAssetPath`-style APIs (conflict with the no-hidden-API stance), (c) fallback —
  host resources with guest assets served through `GuestVirtualFileSystem`.
- Deliverable: a written decision record in the spec with measured results; the chosen
  path gates M2.

**M0.3 Spike B: manifest parsing path**

- Compare `getPackageArchiveInfo` (identity + component classes, **no intent filters**)
  versus an in-house minimal AXML reader (intent filters included; format proven parseable
  against `docs/AndroidManifest.xml` already in the repo).
- Deliverable: chosen parser + the exact fields `GuestPackage` will expose.

**M0.4 Fixture guest APK**

- Design a tiny first-party test app (launcher activity + string/asset + one service +
  one receiver) used by all Phase 2 tests, so CI never depends on the real target APK;
  decide prebuilt-in-repo vs built-by-Gradle and record its license/notice.

**M0.5 Call-chain gap list (from the 2026-10-04 verification above)**

- Decide production wiring for `AetherRuntime.bootstrap` (candidate: `AetherApplication.onCreate`)
  so `HostLifecycle` actually attaches; decide the install-op surface that will call
  `HostInitializer.loadTargetApk` (M5), the `flagger` toggle path, and the `message_bridge`
  contract — record each as a decision, implementation lands with its milestone.

**Acceptance:** spec merged with M0.2/M0.3 decision records; fixture design recorded;
M0.5 decisions logged; no production behavior change required in M0 itself.

### M1 — Guest package model (M)

- `virtualization/package/`: AXML/manifest parsing → `GuestPackage` (application class,
  components, intent filters, min/target SDK, permissions, label/icon refs).
- Persistence next to the existing guest roots
  (`noBackupFilesDir/aether-guest-data/<pkg>/<versionCode>/<sha256>/package.json`).
- Component resolver: `resolveLauncher()` (`MAIN`+`LAUNCHER`, `MAIN`+`INFO` fallback),
  `findActivity/findService/findReceiver/findProvider`.
- Wire into `HostInitializer.loadTargetApk` (parse after trust verification).

**Acceptance:** parse → persist → reload is deterministic; JVM unit tests run against the
in-repo target manifest fixture; `verify_host_structure.py` extended for the new files;
existing verifiers still pass.

### M2 — Guest resources & context (M, highest risk)

- `GuestResources` / `GuestContext`: assets, theme, string/drawable lookup, `classLoader`,
  `packageName`, data dirs backed by `GuestVirtualFileSystem`.
- Per Spike A decision; if only hidden APIs work, implement the documented fallback and say
  so plainly in the docs — do not silently regress the no-hidden-API stance.
- Security review gate before M3 (resource loading touches file access and class loading).

**Acceptance:** instrumented test loads a guest string + asset + theme attribute; docs
(`architecture.md`, `host-container.md`) updated with the verified resource path.

### M3 — Guest Application & Activity in proxy slots (L) — the "works like Snake" milestone

- `GuestRuntimeAdapter`: build the guest `ApplicationInfo`, create the guest `Application`
  via the guest ClassLoader + `GuestContext`, `attachBaseContext` → `onCreate`.
- Slot scheduler: book `ProxyActivityP0..P3` (+ `_L` landscape) from the existing registry;
  bind the resolved launcher Activity into the slot: guest view hierarchy attached, lifecycle
  forwarded through the existing `HostComponentEvent` relay and slot generations.
- Back/new-intent/task handling kept minimal and documented (single task; real back-stack is
  Phase 3).
- Everything gated behind `flagger` (`HostFeature.GUEST_CONTAINER`), default **off**.
- Manual validation protocol for the real target (needs out-of-band APK + signer pin);
  CI uses the fixture guest only.

**Acceptance:** instrumentation test with the fixture guest observes
`Application.onCreate` + `Activity.onCreate/onResume` through the relay; JVM tests cover
scheduler slot booking/overlap; `host-container.md` capability table and `lifecycle.md`
updated honestly (what now works, what still does not).

### M4 — Component dispatch through existing proxies (M)

- Manifest-driven dispatch into the already-declared pools:
  `ProxyServiceP0..P3`, `ProxyBroadcastReceiver`, `ProxyContentProviderP0..P3`,
  `ProxyJobServiceP0..P3` (job adapter replaces the "complete immediately" stub);
  `ProxyVpnService` stays off; `SystemCallProvider` stays inert.
- Authority mapping table for guest providers; explicit permission policy: guest-declared
  permissions map to an allow-list; runtime permissions are requested through Flutter UI —
  never auto-granted.

**Acceptance:** per-component JVM tests + one instrumentation round-trip per component
family; permission policy documented; no `exported=true` regressions (structure verifier
enforces).

### M5 — Control plane & diagnostics (S–M)

- Channel ops (Dart + Kotlin + tests): `guestInstall`, `guestLaunch`, `guestClose`,
  `guestState`, plus an event stream (`guestEvents`: slot lifecycle, guest lifecycle,
  trust failures) — the Aether equivalents of Snake's `"H"`/`"G"`.
- Flutter diagnostics screen: P0..P3 snapshots from `VirtualActivitySlotRegistry`, guest
  identity/version, flag states, last error (stable strings only, no exception internals).
- `AetherRuntime` LOADER/NATIVE/HOST wiring verified end-to-end with the new ops.

**Acceptance:** Dart tests for the channel wrappers; manual run-through script recorded;
`lifecycle.md` channel list updated.

### M6 — Validation, CI & docs hardening (S–M)

- Extend `verify_host_structure.py` (new sources, flags, manifest rules).
- Optional emulator instrumentation job (separate workflow, manual trigger first — note the
  repo's Actions dispatch permission gap: the integration lacks `Actions: write`).
- Update README ("what works now"), `architecture.md`, `lifecycle.md`, `host-container.md`,
  `target-apk.md`; evidence-boundary language preserved.
- Final security-review pass against the `host-container.md` trust checklist.

**Acceptance:** all CI jobs green on `main`; internal Markdown links valid; no verifier was
weakened to pass.

---

## 4. Sequencing

```text
M0 ──► M1 ──► M2 ──► M3          (critical path; M2 is the risk)
          │           │
          ├──► M4 ◄───┘           (needs M2 context; parallel with M3 after M2)
          └──► M5 (stubs) ──► M3+ integration
M6 continuous + final pass
```

- M5 channel stubs can start right after M1 (ops return `notImplemented` until M3).
- Nothing in Phase 2 requires multi-process work; every milestone is independently shippable
  behind `flagger`.

## 5. Phase 3 preview (explicitly out of Phase 2)

- Multi-process slots `:p0..:p3` and the ContentProvider handshake spawn pattern
  (NATIVE_CALLSITE_MAP chain #11–#13) with a linkToDeath supervision channel.
- Identity model decision: Snake's `p3` identity bundle / `gcuid` mapping versus Aether's
  documented no-spoofing stance — a product/legal decision, not an engineering default.
- Real task/back-stack mapping across guest activities and host tasks.
- Native lane growth: `thread_dispatcher` and `message_bridge` already exist; extend only
  against verified descriptors per [jni-contract.md](jni-contract.md) (never the unverified
  11+2 counts).

## 6. Prerequisites, decisions & required inputs

### Decision log

| Decision | Status |
| --- | --- |
| Single-process MVP for Phase 2 | ✅ **decided: yes** (2026-10-04) |
| In-house AXML reader vs `getPackageArchiveInfo` | pending Spike B (M0.3) |
| Resource/asset API path | pending Spike A (M0.2) |
| Fixture guest: prebuilt vs Gradle-built | pending M0.4 |

### Inputs still missing to make a game/app actually run inside AetherEngine

Ordered by when they block something. Items 1–3 were supplied and verified on 2026-10-04 (✅ rows below); items 4–6 remain open, and the rest is
built by Phase 2 milestones.

| # | Input | Why it is needed | Blocks | Where it goes |
| --- | --- | --- | --- | --- |
| 1 | ✅ **Received & verified 2026-10-04** — universal APK `56.30.0`/`4028`, file SHA-256 `2710e403…251b6`, kept at `local/target-apk/` (gitignored) | Trust policy, full manifest/permission/`uses-feature` inspection, runtime-requirement analysis; **never committed to Git** | M3 manual validation | workspace path above, referenced by tests only |
| 2 | ✅ **Received 2026-10-04** — signer SHA-256 `5bee86eda78132ef1f5610e153ca3f20ba71fb8ea2c3de147a5ee65bc0d22405` (full record in `target-apk.md`) | `GuestApkTrustPolicy` needs ≥1 pin; the page fingerprint is only SHA-1 | configure during M3 | `GuestApkTrustPolicy(...)` config only |
| 3 | ✅ **Resolved 2026-10-04** — full 22-DEX set lives in the verified local APK; the `docs/` dump stays partial on purpose (no further binaries committed) | Deep static analysis of the guest runtime reads the local APK directly | optional deep analysis | `local/target-apk/` (gitignored) |
| 4 | **Test device/emulator info** — Android version + ABI you will run on | host is `minSdk 24` / `arm64-v8a` only; instrumentation needs a real target | M3 manual validation, M6 optional CI | CI notes + test protocol |
| 5 | Confirmation you are authorized to run/inspect this target APK | licensing/anti-cheat boundary in `host-container.md` / `target-apk.md` | M3 manual validation | recorded in `target-apk.md` |
| 6 | Runtime logcat from a known-good run (optional, e.g., Snake/ninja reports already present) | cross-checks guest runtime requirements | optional | `docs/reference/` notes |

### Built by Phase 2 (not user-supplied)

Package model + persistence (M1) · resources/Context (M2) · Application/Activity attach +
scheduler (M3) · component dispatch (M4) · channel ops `guestInstall`/`guestLaunch`/events +
flagger toggle + `loadTargetApk` caller (M5) · docs/CI hardening (M6). The fixture guest
(M0.4) is first-party and needs no external input.

## 7. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Resource loading may require hidden APIs | M2 blocked or stance regression | Spike A decides before code; documented fallback keeps M1/M3/M5 movable |
| Guest SDKs assume package/identity visibility | Guest misbehaves in-process | Documented Phase 2 limits; no spoofing; fixture + manual target protocol |
| Licensing / anti-cheat boundary | Legal exposure | Non-goals; no target binaries in Git; fixture guest in CI; no hooking |
| Scope creep toward multi-process | Schedule | Phase 3 list; `flagger`-gated shippable milestones |
| Doc drift vs code | Trust | M6 gates: verifiers + link audit + capability-table updates per milestone |

## 8. Definition of Done (Phase 2)

- [ ] `docs/guest-container-spec.md` exists and matches the implementation
- [ ] Install-model parses/persists/resolves the pinned target (fixture-verified in CI)
- [ ] Guest `Application` + `Activity` observed running in a proxy slot (instrumented test)
- [ ] Guest resources/Context path implemented **or** explicitly documented as fallback
- [ ] Service/Receiver/Provider dispatch through existing pools with tests
- [ ] Flutter install/launch/observe ops + diagnostics screen
- [ ] `flagger` gates every new capability; defaults unchanged (off)
- [ ] All docs updated with no overclaim; all CI jobs green; verifiers extended, not weakened
