# S1 evidence protocol (not an S1 completion certificate)

## Isolation and invocation

`.github/workflows/s1.yml` is manual `workflow_dispatch` only. It neither changes nor
calls `ci.yml`/`release.yml`, has `contents: read`, no production environment, no
signing secrets, and never downloads or publishes an external game APK. Checkout
credentials are not persisted. APK signing uses only AGP's disposable debug key.
The workflow does not upload keystores, `local.properties`, Gradle caches, private
app storage, credentials, or full-device logcat.

GitHub generally discovers `workflow_dispatch` workflows from the default branch.
A new `s1.yml` present only on this feature branch may therefore be unavailable
for dispatch. Branch-only routing through an existing workflow ID, if separately
approved and performed by the integration owner, is outside these owned edits;
this implementation does not overwrite either release baseline workflow.

Inputs:

| Input | Default | Meaning |
| --- | --- | --- |
| `run_emulator` | `true` | Execute scoped instrumentation on x86_64 API34 only. |
| `test_abi` | `x86_64` | Pass `-PaetherTestAbi` to all Gradle commands. Choices are `x86_64` and `arm64-v8a`. The host Gradle guard must reject release tasks using the override. |
| `upload_debug_apks` | `false` | Optionally preserve inspected first-party debug host/test/fixture APKs for authorized physical-device tests. |

For ARM64 artifacts select `test_abi=arm64-v8a`, `run_emulator=false`, and
`upload_debug_apks=true`. An ARM64 APK build **is not an ARM64 test pass**.
`run_emulator=true` with an ARM64 ABI is rejected before toolchain/build work.

## Toolchain and build identity

GitHub Actions uses an x86 runner (`ubuntu-24.04`, assert `RUNNER_ARCH=X64`),
Temurin JDK17, Flutter3.47.0, Android platform36/build tools36.0.0,
NDK26.3.11579264, and CMake3.22.1, matching the release baseline versions.
`flutter pub get` creates the Flutter module before Gradle configuration.

One assembly invocation runs:

```sh
bash ./gradlew -PaetherTestAbi="$TEST_ABI" \
  :fixture-guest:assembleDebug :android-host:assembleDebug \
  :android-host:assembleDebugAndroidTest :android-host:testDebugUnitTest \
  --stacktrace --console=plain
```

The standard-library inspector recursively enumerates actual `*.apk` candidates
under each module's `build/outputs/apk`, rather than assuming a filename or the
incorrect `debugAndroidTest` directory. It accepts exactly one host debug APK,
one test APK (AGP normally uses `androidTest/debug`), and one fixture debug APK.
Extra output/split/release APKs fail closed except the explicitly generated
first-party config fixture at
`android-host/build/generated/fixtureSplit/fixture-config-arm64.apk`.

ZIP checks require `AndroidManifest.xml` and `classes.dex` in host/test/base
fixture, reject duplicate and encrypted members, and require the test APK's only
embedded APKs to be `assets/fixture-guest.apk` and
`assets/fixture-config-arm64.apk`. Host debug must contain neither. The base
fixture must contain nonempty `assets/fixture.txt` and `classes.dex`. Both embedded
fixtures must match their independently built SHA256, and both nested ZIPs are
inspected. The signed config fixture is code-free: nonempty compiled manifest,
no DEX, no native libraries, no nested APK. Its `config.arm64_v8a` split metadata
is loader input even on x86; it is not installed as an ARM64 runtime or used to
launch an arbitrary Activity. AAPT2 output records the actual split manifest.
Test assets must additionally include nonempty `assets/fixture-signer.sha256`,
exactly 64 lowercase hexadecimal characters (optional trailing newline). This is
the public debug certificate digest, generated from `keytool -exportcert`; it is
not a keystore or private key. Inspector saves the public value/member SHA256 and
rejects the pin in host assets. Loader assertions must check real signer binding;
format validation alone is not signature verification.
The host must contain `lib/<test_abi>/libaether.so` and no other native ABI.
JSON contains actual APK SHA256, members/sizes/CRC32, required-member SHA256,
observed native ABIs, selected source paths, and all inspection issues.
AAPT2 badging and compiled manifest dumps are retained separately.

Instrumentation uses the same packaged outputs via
`:android-host:connectedDebugAndroidTest`, excluding `packageDebug`,
`packageDebugAndroidTest`, `stageFixtureApk`, and `createFixtureConfig` to prevent
assembly dependencies and signed split regeneration.
A post-test SHA256 check rejects any changed APK. AGP task wiring is intentionally
fail-closed: if a future AGP needs other packaging dependencies, adjust exclusions
only after verifying the selected bytes remain unchanged. Do not silently rebuild
and substitute a new APK after inspection.

## Executed test scope and required gates

`tools/verify_s1_artifacts.py scope` discovers the declared package of exactly one
`FixtureGuestLoaderTest`, plus `FixtureGuestLaunchTest` when present (either package
path is supported). Source paths and hashes are saved in `test-scope.json`.
The comma-separated fully qualified classes are passed to
`android.testInstrumentationRunnerArguments.class`. No broad connected suite is
used. Host JVM unit tests run independently as `testDebugUnitTest`.

Both real unit XML and requested instrumentation XML are mandatory evidence:

- Parseable JUnit `testsuite`/`testsuites` documents and at least one unit case;
  instrumentation additionally requires `--min-tests 4` (three Loader tests,
  including the split case, plus one cooperative Launch test). If Launch is not
  present, scope discovery still works, but the four-case gate remains fail-closed.
- Declared counters must match actual cases/failure/error/skipped elements.
- Total failures and errors must be zero; an all-skipped run cannot pass.
- Every selected instrumentation class must have a passing case; unscoped cases
  and any skipped scoped instrumentation test fail the gate.
- Missing, empty, malformed, or inconsistent XML returns a nonzero exit code.
- Gradle/emulator command failure remains a workflow failure even if XML exists.

The x86_64 API34 emulator records SDK/ABI/native bridge properties before tests.
Scoped logcat includes app/bootstrap, `AetherS1` (the Launch test's nativeReady
statement), fixture/test-runner tags and AndroidRuntime errors only. After tests,
package `primaryCpuAbi`/`secondaryCpuAbi` and native library location are saved;
`nativeReady`/ABI log lines are extracted. Missing READY evidence is explicitly
marked missing, never inferred from a successful APK build. Device supported ABI
lists are not proof of the running process ABI; package ABI is labelled as such.
While instrumentation is alive, a host-only sampler records its PID and attempts
`run-as com.aether.host readlink /proc/<pid>/exe`; `app_process64` is executable
bitness evidence, not an ARM64 ISA result on x86_64. SELinux denial or a missed
short-lived process is retained as missing evidence, never treated as a pass.
The Launch assertion supplies native initialization evidence only for that run's
selected ABI, never for production ARM64 when running on x86_64.

## Retained artifacts (14 days, upload even on failure)

Required `s1-evidence-<abi>-<run_id>-<attempt>` includes:

- `run-context.json` (commit/run/ABI/scope, `arm64_result=NOT_RUN`, `s1_complete=false`).
- APK/member/SHA256 JSON, scope JSON and test class list.
- AAPT2 badging and compiled manifest evidence for host/test/base/config fixture.
- Toolchain, Flutter resolution, SDK setup, build/unit-test, scoped connected-test
  and evidence-gate logs (pipelines preserve command exit status).
- Unit XML + HTML reports and connected instrumentation XML + HTML reports.
- Scoped logcat, device properties, package ABI/nativeReady extracts, emulator
  command exit status, and post-test APK identity check.

Optional `s1-debug-apks-<abi>-<run_id>-<attempt>` includes only verified
`host-debug.apk`, `test-debug.apk`, `fixture-debug.apk`, `fixture-config-arm64.apk`,
and source SHA256 records. These are staged under `evidence/debug-apks` for the
authorized installer. The required evidence upload explicitly excludes that
subdirectory; APK upload occurs only when `upload_debug_apks=true`.
The upload excludes all production signing material and external game APKs.
When build/setup fails, some evidence can be absent; the run must remain failed.
A missing instrumentation XML is a failure when an emulator was requested, not a
reason to downgrade the run to artifact-only success.

## Real ARM64 acceptance remains separate

A green workflow is an evidence-generation result, **not S1 completion**.
Physical-device validation must use an authorized ARM64 device and the preserved
ARM64 debug bytes, record their SHA256 + commit, device API/model and ABI, actual
process ABI/nativeReady, scoped command exit status, test XML with the same gates,
and lifecycle/loader evidence. If generating XML on a device, use an authentic
instrumentation/AGP reporter; do not manufacture passing XML from console text.
Do not upload private device state, unrelated logs, credentials, or external APKs.
Only independently collected and reviewed ARM64 results can close S1. Physical
host/test installation is a separate authorized procedure: stop on signer
mismatch; do not uninstall, wipe app data, or replace signing identity to bypass
it. This CI workflow itself neither accesses nor installs on a physical device.

## Lightweight local verification (no Android build)

```sh
python3 -B -m unittest discover -s tools -p test_verify_s1_artifacts.py -v
```

The tests synthesize ZIP/XML bytes, exercise stale/duplicate/missing/bad candidate
cases, both ABIs, scope discovery, and false-green XML prevention. No Android SDK,
network, APK execution, Gradle build, signing credentials or external APK is needed.
