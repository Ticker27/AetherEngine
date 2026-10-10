# AetherEngine delivery plan

Status: **S1 evidence pending; target identity 56.31.0 / 4035 aligned on a local branch** · 2026-10-11

The installed target is a base APK plus an ARM64 configuration split. See
[GUEST_56310_PLAN.md](GUEST_56310_PLAN.md) for current call-path evidence and implementation gates.
Identity alignment is not guest-launch support.

## Canonical source of truth

The root Gradle project is the only active Android project:

- `android-host` is the host application and owns the proxy component pools.
- `fixture-guest` is the first-party guest APK used by S1 tests.
- `aether-native` is the host native runtime.
- `flutter-app` is the Flutter add-to-app module.
- `scripts/check_local.sh` and GitHub Actions are the verification entry points.

The former nested Android project and its separate CI contract are not part of the active
architecture. Historical phase notes are not acceptance criteria for the current plan.

## Invariants

1. Every milestone ends with a check that runs.
2. No external target APK or opaque imported payload is committed.
3. The external target trust contract stays separate from first-party test fixtures.
4. No milestone claims sandboxing: guest code shares the host process, UID, and permissions.
5. S1 does not instantiate or launch guest Android components.

## Baseline already present

- Host Flutter UI communicates over `MethodChannel("aether/runtime")`.
- Native runtime state machine, dispatcher, JSON bridge, and JNI lane have host-side checks.
- `DynamicApkLoader` copies a selected APK into private storage, verifies identity and signer,
  re-verifies the immutable digest cache, creates a guest-first `DexClassLoader`, and creates a
  digest-specific `GuestVirtualFileSystem` root.
- Proxy Activity, Service, Receiver, Provider, lifecycle, flag, and filesystem foundations exist.

## S1 — First-party fixture guest

**Goal:** make the real loader path testable without an external APK or device-only payload.

### Delivered design

`fixture-guest` is a small Android application with deterministic identity:

- package `com.aether.fixture`;
- version `1.0.0`, code `1`;
- launcher `FixtureActivity`;
- private `FixtureService`;
- private `FixtureReceiver`;
- one string resource and `assets/fixture.txt`;
- the build's debug signing certificate is read into a fixture-only trust profile during the
  instrumentation run; no signing credential is committed.

`GuestApkTrustPolicy` now accepts a generic `GuestApkTrustProfile`; its default constructor still
represents only the pinned external target. The fixture does not alter `TargetApkContract`.

### S1 acceptance criteria

1. `:fixture-guest:assembleDebug` produces the fixture APK.
2. The source verifier checks the fixture identity, manifest components, deterministic asset,
   signing boundary, and host/fixture separation.
3. The fixture APK is staged as a generated Android-test asset; no APK is committed.
4. JVM tests continue to cover policy, class delegation, filesystem, and host logic.
5. The Android instrumentation test loads the generated fixture with `DynamicApkLoader`.
6. The test verifies package, version, observed signer through an explicit fixture profile,
   digest-named private cache, and re-load
   cache reuse.
7. The test loads the fixture Application and Activity/Service/Receiver DEX classes.
8. The test verifies a per-digest guest VFS root exists.
9. The test rejects the same APK when the signer pin is wrong.
10. The fixture is not included in the host release APK.

### Current gate state

- Local structure/native gate: green when run with `TMPDIR=/tmp`.
- Main/tag CI now produces one signed release APK only; fixture and instrumentation remain
  source-level S1 assets and are not release artifacts.
- ARM64 connected instrumentation run: **pending device/runner**.
- S1 is not marked complete until the instrumentation result is recorded on an ARM64 target.

## S2 — Guest package model

After S1 is green, parse the verified fixture manifest into an immutable `GuestPackage`, persist it
as deterministic JSON below the guest VFS root, reload it, resolve `MAIN` + `LAUNCHER` (with
`INFO` fallback), and provide component lookup. No external APK is required.

## S3 — Install and launch fixture in a proxy slot

First prove public-API resource and lifecycle feasibility with a cooperative first-party fixture.
A feature-flagged `GuestRuntimeAdapter` may host a fixture entry-point contract inside an existing
host proxy Activity and forward lifecycle callbacks with explicit cleanup. Do not claim that this
attaches an arbitrary third-party Android Activity: DEX class loading is not framework attachment.
Default remains off; external guest launch stays unsupported until the required capabilities are
verified.

## S4 — Guest context and resources

Implement public-API `GuestContext`/`GuestResources` for the fixture's string, asset, and theme.
Document any degraded path; do not use hidden APIs.

## S5 — Component dispatch

Dispatch the fixture Service, Receiver, and later Provider through the existing proxy pools using
the parsed manifest model.

## External target later

Only after S3–S5: perform a manual, on-device validation protocol for the separately authorized
8 Ball Pool release. It remains out-of-band and never becomes a CI dependency.
