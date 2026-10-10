# AetherEngine

AetherEngine is a regular, installable Android host that embeds a Flutter UI beside a custom
native runtime and a deliberately limited in-process guest-code loader.

## Canonical structure

```text
android-host/       # the only host application and proxy component pool
fixture-guest/      # first-party Android guest used by S1 CI/instrumentation tests
aether-native/      # native runtime used by android-host
flutter-app/        # Flutter add-to-app module
integration-test/   # host-side native tests
scripts/            # repository and artifact verifiers
```

The root Gradle project is the only Android build. The fixture is never bundled into the host
release APK and is not an external target.

## Current status

- Host Flutter/native channel: implemented and covered by existing checks.
- Guest APK verification, private cache, guest-first DEX loader, and per-digest VFS: implemented.
- **S1 first-party fixture guest: implementation in progress on `main`; CI/device gates pending.**
- Guest Application/Activity attachment, resources, package model, and component dispatch: not
  implemented yet; they belong to S2–S5.

S1 deliberately loads code and creates private storage only. It does not instantiate or launch
the fixture's Activity, Service, or Receiver.

## S1 fixture

`fixture-guest` builds `com.aether.fixture` version `1.0.0` with:

- one exported launcher Activity;
- one private Service;
- one private BroadcastReceiver;
- one string resource and one deterministic asset;
- the build's debug signer is checked by the instrumentation run.

The host's generic `GuestApkTrustPolicy` accepts an explicit `GuestApkTrustProfile`. The external
8 Ball Pool contract remains separate and unchanged. The Android instrumentation test stages the
CI-built fixture APK, reads its signer metadata into a fixture-only profile, verifies its
package/version/signer, loads its Activity/Service/Receiver classes through `DynamicApkLoader`,
checks the private digest cache, and checks the VFS root.

The fixture uses the build's debug signer. Its certificate is read into a fixture-only trust
profile during the instrumentation run; no private key or release credential is committed. This
trust profile is test plumbing, not a production trust anchor or isolation boundary.

## Local checks

The lightweight check does not need Android SDK, JDK, Flutter, or a device:

```sh
TMPDIR=/tmp sh scripts/check_local.sh
```

The environment used by Minis may expose a non-existent `TMPDIR`; setting it to `/tmp` is
required there. The check runs the source-structure verifier and host-side native tests.

## Release-only CI

The `main` CI build and tag release workflow expose only one APK artifact:

```text
aether-engine-release.apk
```

The pipeline assembles the host release variant, materializes the signing keystore from the
protected `production` GitHub Actions environment, applies `zipalign`, signs with Android APK
Signature Scheme **v1 and v2**, verifies both schemes with `apksigner`, runs host/native/Flutter
checks, and uploads only the signed APK. The unsigned intermediate APK is never uploaded or
published. There is no debug, fixture, instrumentation, or unsigned release artifact in the
release output.

The signing secrets are `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. They are not stored in Git. Losing or replacing
the production signing key breaks update compatibility for already-installed APKs.

The S1 fixture and instrumentation test remain source-level development assets for the next
milestone; they are not release artifacts and are not bundled into the host APK.

## Security boundary

The loader is not a sandbox. Guest code shares the host process, UID, and permissions. No hidden
API bypass, signature spoofing, package-manager spoofing, anti-cheat bypass, licensing bypass,
external endpoint, or imported target APK is part of this repository.

The external target policy remains fixed to 8 Ball Pool `com.miniclip.eightballpool`, version
`56.30.0` / code `4028`, with explicit signer pins supplied out-of-band. The target APK is not
committed and is not a CI dependency.
