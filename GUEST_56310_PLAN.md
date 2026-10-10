# Guest 56.31.0: evidence and implementation gates

Status: identity aligned on a local branch; external guest launch is NOT implemented or verified.

## Initial Analysis: actual trigger and call path

The framework creates `AetherApplication`, whose `onCreate()` initializes `HostInitializer`.
`HostLifecycle.attach()` exists only in `AetherRuntime.bootstrap()`, which currently has no
production caller. `MainActivity.configureFlutterEngine()` registers
`AetherRuntimeChannel`. The channel handles version/state/initialize/shutdown/ping; it does NOT
call `loadTargetApk()` or start a guest. `AetherRuntime.bootstrap()` records the LOADER module but
does not invoke `DynamicApkLoader`. Search of the custom source finds no caller of `loadTargetApk`.
`VirtualActivity` sends host Activity events and renders a placeholder, not guest callbacks.

Proposed cooperative fixture flow, not current runtime behavior:

```text
explicit host request
  -> inspect immutable guest identity / split set / trusted signer
  -> reject unsupported capabilities before guest code execution
  -> verified private APK cache + guest package model
  -> guest class policy + storage/resources contract
  -> cooperative fixture entry point in a host-owned proxy slot
  -> lifecycle state machine + observable cleanup
```

`System.loadLibrary("aether")` initializes Aether's own native runtime. Snake's
`System.loadLibrary("engine")` initializes Snake's native runtime. Neither call, by itself,
launches an APK. A PackageManager launch Intent would open the separately installed guest under
Android's own app environment; that is NOT an in-container launch and must not be labelled one.

## Target identity evidence (2026-10-11)

Read-only device PackageManager dump:

- package: `com.miniclip.eightballpool`
- versionName: `56.31.0`; versionCode: `4035`
- minSdk: 23; targetSdk: 36; primaryCpuAbi: `arm64-v8a`
- package paths: `base.apk` plus `split_config.arm64_v8a.apk`

Supplied standalone APK manifest:

- the same package/version/code, with ARM64 and ARMv7 native entries
- Application: `com.miniclip.eightballpool.EightBallPoolApplication`
- launcher: `com.miniclip.eightballpool.EightBallPoolActivity`
- APK SHA-256: `78b7a68abfa581ad02a8eb7e0de24938c9ead3dde5ea83e02e6fdccfe63e6472`

Matching metadata does not establish that the standalone archive and installed APK set are the
same bytes or have the same signer. No target signer pin was established in this change. Explicit
trusted pins remain mandatory. Target APKs, saves, auth tokens and accounts stay out of Git/CI.

## What Snake does and does not establish

The supplied Snake artifacts show an Application bootstrap, JNI bridge, proxy component pools
and guest paths under its own data tree. Snake manifest slots use `:p0` through `:p3`, so its
process layout is not evidence that Aether's in-process layout is equivalent. Reported/obfuscated
native behavior is not a specification for Aether. No protection/licensing bypass, signature or
PackageManager spoofing, hidden-API attachment, native hooks or arbitrary target injection is part
of this plan.

## Blocking gaps in Aether

1. `DynamicApkLoader.load(File)` accepts one APK, not the installed base/split set. Native library
   search path is null, and guest resources are unsupported.
2. `GuestClassLoaderProxy` falls back to the host for any missing guest class despite comments
   suggesting explicit sharing. Shared ABI/class identity needs a deliberate policy and tests.
3. `GuestVirtualFileSystem` applies only to calls through its facade. Java/native direct IO can
   bypass it; same-process same-UID code is not a security sandbox. Canonical checks do not prove
   resistance to concurrent path replacement between validation and file open.
4. `HostInitializer.loadTargetApk()` creates a fresh loader per call, so its per-loader cache does
   not provide a stable process-owned session. Replacing a guest while slots are active needs a
   state contract. Releasing references does not guarantee DEX/native code unloading.
5. `VirtualActivity` does not construct/attach guest Activity objects. Public Android APIs alone
   do not promise arbitrary third-party Activity attachment. Feasibility must be demonstrated on
   a cooperative fixture before this can be offered as a capability.
6. S1's generated fixture is an androidTest APK asset. `ApplicationProvider` supplies the target
   app context, not the instrumentation APK asset context. Earlier missing-asset stack traces do
   not, by themselves, prove broken Gradle packaging. Inspect the test APK and use
   `InstrumentationRegistry.getInstrumentation().context` for test assets, while retaining the
   target app context for `DynamicApkLoader` and private storage.
7. The existing probe run `38070705218` stopped with `test_apk=` empty and unzip exit code 9.
   Its filename glob did not locate the test APK; it never established fixture-asset presence or
   absence. Replace it with explicit ZIP-member inspection of the build's actual APK candidates,
   and avoid `unzip | grep -q` under pipefail because a producer SIGPIPE can misreport a match.

## Next gates, in order

### G0: preserve the release baseline
Keep signed release v1/v2 CI and main unchanged until a tested branch is reviewed. Match target
identity exactly at 56.31.0 / 4035, continue rejecting 56.30.0 / 4028, wrong package and wrong signer.

### G1: close first-party S1 evidence
Correct test asset context, inspect `assets/fixture-guest.apk` in the CI-built test APK, and execute
both fixture tests. An x86_64 emulator result must be labelled x86_64, not ARM64. A physical ARM64
result must record device/API/ABI, host and test APK hashes, XML test results and scoped logcat.
No repeated CI builds based on unverified assumptions; fix the demonstrated failing layer first.

### G2: inspect guest package sets without executing code
Design an immutable `GuestPackageSet` and `GuestPackage` with base/split identities, individual
hashes and consistent signer/version metadata, component resolution and explicit unsupported
capabilities. Unit/fixture tests cover missing splits, duplicate split names, inconsistent signer,
corrupt input and stable persistence. Reject incomplete sets before any class/native loading.

### G3: make the cooperative loader/storage boundary explicit
Process-owned loader/session state; finite class sharing contract; private cache identity and
failure rollback; guest-relative storage API, concurrent access, streams/handles and cleanup tests.
Do not claim arbitrary java.io/libc calls are redirected or a malicious guest is contained.

### G4: prove resources and lifecycle on the fixture
Resolve fixture string/asset/theme with documented supported Android APIs BEFORE offering a launch
button. Use a cooperative guest entry-point interface hosted by a normal proxy Activity. Define
create/start/resume/pause/stop/destroy, saved state, new Intent, configuration change, background /
foreground and process-recreation behavior. Test slot leases/generations and listener cleanup.
This does not prove compatibility with the unmodified external game's Activity.

### G5: capability decision before external launch
Assess native ABI/dependency support, split resources, component routing and unsupported APIs with
first-party samples. A missing requirement produces a structured unsupported result; it cannot be
made supported merely by renaming a class or enabling a flag. Do not attempt external guest boot
until the evidence supports a legitimate, non-bypass integration path. Stop if hidden-API or
protection bypass becomes necessary rather than silently introducing it.

## What counts as evidence
Every test record identifies trigger, session, artifact hash, device/API/ABI, expected and observed
result. Metadata match, DEX lookup, Application construction, Activity rendering, storage behavior
and lifecycle correctness are separate milestones. Build/signing success is not launch success.
No claim of "perfect" or "zero leaks" without a stated boundary and concrete tests.
