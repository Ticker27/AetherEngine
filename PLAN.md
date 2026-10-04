# AetherEngine Plan

Status: **active reset** · 2026-10-04 · supersedes the removed `docs/` specification set.

## Why the reset

The project had stalled behind documents and imported payloads: every meaningful test path
depended on the external 8 Ball Pool APK plus a device, signer authorization, and an
out-of-band download, while ~890 files of opaque reference data and tens of megabytes of
binary analysis dumps sat in the repository without a single code path using them.

Two rules apply from now on:

1. **No milestone lands without a check that runs.** Every step below ends with a command
   that passes locally (`sh scripts/check_local.sh`) or in CI.
2. **No binary or imported payload is committed** unless a test in this repository loads it.

## Teardown record (landed with this plan)

| Removed | Why |
| --- | --- |
| `android-host/src/main/assets/snake/` (856 files) | Opaque imported data; no Kotlin or Dart code loaded it. |
| `scripts/verify_snake_payload.py` | Existed only to police those files. |
| `docs/` reference dumps and specs (32 files, ~43 MB) | Removed with this reset; contracts folded into this plan and the README. |
| Snake references in host source comments | The reference bundle is no longer in the repository. |

Kept: the host container code base, the native lane, the pinned target policy and its security
stance, and the CI build/test/release workflows.

## What exists today (verified by `sh scripts/check_local.sh`)

- Kotlin host: `HostInitializer`, `VirtualActivity` P0..P3 proxy pools + slot registry,
  `DynamicApkLoader` (copy → pinned identity + signer verification → read-only private cache →
  re-verify → `DexClassLoader`), `GuestClassLoaderProxy`, `GuestVirtualFileSystem`.
- Native lane: runtime state machine, thread dispatcher, JSON message bridge, 4-method JNI
  contract in `libaether.so`; host-side tests pass without an Android toolchain.
- Flutter add-to-app module wired over `MethodChannel("aether/runtime")`.
- CI: build + unit tests + APK binary verification + Flutter analyze/test; `v*` tags publish
  APKs to a GitHub Release.

## Not implemented (unchanged, deliberate)

- Guest `Application`/`Activity` attachment, guest resources, package-manager behavior.
- Guest native-library loading, multi-process isolation, hidden-API bypass, signature or
  permission spoofing, package-manager spoofing, anti-cheat or licensing bypass.
- Loaded guest code shares the host UID and permissions; none of this is a sandbox.

## Milestones

Each milestone must land with its check green.

### S1 — First-party fixture guest (next)

The container cannot be developed against an APK that CI must not hold. Add a first-party
fixture: a tiny Gradle Android module (one launcher Activity, one string, one asset, one
service, one receiver) built by CI, plus a fixture trust profile in `GuestApkTrustPolicy`
(test-only signer pin) so tests exercise the real loader path instead of mocking it.

**Check:** fixture APK assembles in CI, and JVM tests install it through `DynamicApkLoader`
(copy, verify, DEX class loading, VFS root creation).

### S2 — Guest package model (parse, persist, resolve)

Manifest parsing into a `GuestPackage` model, deterministic JSON persistence under the guest
VFS root, launcher resolution (`MAIN`+`LAUNCHER`, `INFO` fallback), and component lookup.

**Check:** parse → persist → reload determinism tests in `:android-host:test` against the
fixture.

### S3 — Install and launch the fixture in a proxy slot

`GuestRuntimeAdapter`: construct the fixture `Application` through the guest class loader,
attach its launcher Activity into a booked `ProxyActivityP0..P3` slot, forward lifecycle
events through the existing relay, release on destroy. Gated behind `flagger`
(`GUEST_CONTAINER`, default off).

**Check:** on-device run showing the fixture Activity inside the proxy slot with lifecycle
events relayed; a scripted manual protocol covers environments without instrumentation CI.

### S4 — Guest context and resources for the fixture

`GuestContext`/`GuestResources` (assets, strings, theme) using public APIs only; if only a
degraded path is possible, document the fallback instead of reaching for hidden APIs.

**Check:** fixture string, asset, and theme attribute resolve through the guest context.

### S5 — Component dispatch (service, receiver, provider)

Manifest-driven dispatch into the existing proxy pools, exercised by the fixture.

**Check:** one round-trip per component family in the instrumented suite.

### Later — the external target

Only after S3–S5: a manual, on-device validation protocol for the pinned 8 Ball Pool release
(out-of-band APK, signer pins, and authorization required). Never a CI dependency.
