# Selected Guest APK: 8 Ball Pool 56.30.0

Aether's selected target is **only** the 8 Ball Pool release linked below. The host's guest APK policy rejects any other package or version before creating a DEX class loader.

## Release identity

| Field | Required value |
| --- | --- |
| Package | `com.miniclip.eightballpool` |
| Version name | `56.30.0` |
| Version code | `4028` |
| Listing minimum Android | Android 6.0 (API 23) |
| Listing ABIs | `arm64-v8a`, `armeabi-v7a` |

Source metadata: [APKPure — 8 Ball Pool 56.30.0](https://apkpure.com/th/8-ball-pool-android-1/com.miniclip.eightballpool/download/56.30.0).

The values above identify the release advertised by that listing; they have **not** yet been independently checked against the APK binary. The page displays fingerprint `87615343657c0a98155d71ec5a3158218b9a9f62` (40 hexadecimal characters), but it has not been verified against the actual signing certificate and is not used as the loader's SHA-256 pin. The APK's manifest, DEX, native libraries, and runtime have not been inspected.

## Enforced by the host

`TargetApkContract` and `GuestApkTrustPolicy` pin the package name, version name, and version code to the values above. The loader checks these values both before caching and after copying the APK into private storage. A different release (including 56.30.1), a mismatched version code, an unexpected package, or an unpinned signer is rejected.

The SHA-256 signer pin is still mandatory and must be calculated from a verified APK certificate. The webpage's 40-character fingerprint is not substituted for a SHA-256 certificate pin. Until the exact APK is available for inspection and its signer is verified, the trust policy cannot be configured for production loading.

## Snake Engine is a separate reference

Snake Engine is a separate Android guest-app virtualization/container host, not the selected guest game. The [static extraction log](reference/snake-engine/ARCHIVE_EXTRACTION_LOG.md) records that the inspected package manifest identified `com.snake` versionName `2.2.6`; it also documents the extracted `assets/` and compiled `res/` folders. The package does not contain an 8 Ball Pool `base.apk` or split APK. Some historical analysis notes mention 8 Ball Pool `56.23.2` or `56.29.1`, but they rely on other inputs and do not establish support for target `56.30.0`. Snake remains an architectural reference and its guest-container capability is not implemented by Aether's current DEX loader.

## Compatibility and implementation limits

- The host currently builds with `minSdk = 24` and `arm64-v8a` only. Although the target listing says API 23 and includes `armeabi-v7a`, this host build is only compatible with API 24+ arm64 devices.
- APK selection and loading do not require root. That does not make the guest isolated or make a normal APK runnable inside the host.
- `DynamicApkLoader` only creates a `DexClassLoader`. It does not install or launch the game, load guest native libraries, attach the game Activity, provide guest resources, or emulate package-manager behavior. No claim is made that 8 Ball Pool currently runs in Aether.
- Do not infer Flutter, Unity, Cocos, or another runtime from the version listing. Inspect the exact APK's manifest, DEX, and ABI-specific native libraries before selecting an integration strategy. The host's Flutter Engine/Dart app and custom JNI bridge remain separate from any guest runtime.

The exact 8 Ball Pool 56.30.0 APK (or its complete split set) is not in this repository. The Snake reference artifacts are a separate package/version and are not substitutes. Keep downloaded target APKs out of Git unless explicitly required; verify provenance, package/version metadata, and signing certificate before testing.
