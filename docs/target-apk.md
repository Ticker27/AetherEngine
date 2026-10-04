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

The values above were **independently verified against the APK binary** on 2026-10-04 (see the verification record below). The page's 40-character fingerprint `87615343657c0a98155d71ec5a3158218b9a9f62` equals the **SHA-1** of the APK's actual signer certificate — now confirmed — while the certificate's **SHA-256**, the pin format `GuestApkTrustPolicy` requires, is recorded below. The APK's manifest, DEX, and native libraries have been inspected from the binary; the game's **runtime has not been executed** yet.

## Enforced by the host

`TargetApkContract` and `GuestApkTrustPolicy` pin the package name, version name, and version code to the values above. The loader checks these values both before caching and after copying the APK into private storage. A different release (including 56.30.1), a mismatched version code, an unexpected package, or an unpinned signer is rejected.

The SHA-256 signer pin remains mandatory and must come from a verified APK certificate; the webpage's 40-character fingerprint is a SHA-1 value and is never substituted for it. With the exact APK now inspected and its signer verified (record below), the pin is known and `GuestApkTrustPolicy` can be configured with it as a Phase 2 M3 step — production loading still requires the device validation protocol in the [Phase 2 plan](phase2-plan.md).

## Snake Engine is a separate reference

Snake Engine is a separate Android guest-app virtualization/container host, not the selected guest game. The [static extraction log](reference/snake-engine/ARCHIVE_EXTRACTION_LOG.md) records that the inspected package manifest identified `com.snake` versionName `2.2.6`; it also documents the extracted `assets/` and compiled `res/` folders. A separate [runtime-data snapshot](reference/snake-engine/RUNTIME_SNAPSHOT_REVIEW.md), `docs/com.snake.zip`, contains older `56.23.2` metadata strings but no game APK or native library; it is not the static package archive and does not establish the exact APK version or successful launch. Other historical analysis notes mention `56.23.2` or `56.29.1` from separate inputs. None establishes support for target `56.30.0`. Snake remains an architectural reference and its guest-container capability is not implemented by Aether's current DEX loader.

## Compatibility and implementation limits

- The host currently builds with `minSdk = 24` and `arm64-v8a` only. Although the target listing says API 23 and includes `armeabi-v7a`, this host build is only compatible with API 24+ arm64 devices.
- APK selection and loading do not require root. That does not make the guest isolated or make a normal APK runnable inside the host.
- `DynamicApkLoader` only creates a `DexClassLoader`. It does not install or launch the game, load guest native libraries, attach the game Activity, provide guest resources, or emulate package-manager behavior. No claim is made that 8 Ball Pool currently runs in Aether.
- Do not infer Flutter, Unity, Cocos, or another runtime from the version listing. Inspect the exact APK's manifest, DEX, and ABI-specific native libraries before selecting an integration strategy. The host's Flutter Engine/Dart app and custom JNI bridge remain separate from any guest runtime.

The exact 8 Ball Pool 56.30.0 APK (or its complete split set) is **not committed to this repository**: a verified local copy is kept outside Git at `local/target-apk/` (ignored via `.gitignore`), and only hashes and extracted metadata are recorded here. The Snake reference artifacts are a separate package/version and are not substitutes. Keep downloaded target APKs out of Git unless explicitly required; verify provenance, package/version metadata, and signing certificate before testing.

## Verification record — 2026-10-04

All values below were read from the APK binary itself, not from the listing page.

| Field | Value |
| --- | --- |
| Source | APKPure listing above, served via `d.apkpure.net` redirect |
| Local file | `local/target-apk/8bp-56.30.0-4028.apk` (workspace only, gitignored) |
| Size | 145,423,612 bytes |
| File SHA-256 | `2710e403fe3101812de7122d3a8353fd4201c4857233c9b1643759a593a251b6` |
| Package | `com.miniclip.eightballpool` — matches `TargetApkContract` |
| versionName / versionCode | `56.30.0` / `4028` (`0xfbc`) — matches `TargetApkContract` |
| minSdk / targetSdk (from binary) | `23` / `36` |
| Application entry | `com.miniclip.eightballpool.EightBallPoolApplication` |
| Launcher activity | `com.miniclip.eightballpool.EightBallPoolActivity` (`MAIN` + `LAUNCHER`) |
| Activities / permissions | 106 activities · 15 `uses-permission` |
| ABIs | universal APK: `arm64-v8a` + `armeabi-v7a` (19 `.so` files each) |
| DEX | 22 files: `classes.dex` … `classes22.dex` |
| Signature schemes | v1 (`META-INF/BNDLTOOL.RSA`) + v2/v3 signing block |
| Signer subject / serial | `C=PT, O=miniclip, OU=miniclip portugal, CN=edward barber` · serial `4E64CC14` |
| Signer SHA-1 | `87615343657c0a98155d71ec5a3158218b9a9f62` — **identical** to the listing fingerprint cited above |
| **Signer SHA-256 (trust pin)** | `5bee86eda78132ef1f5610e153ca3f20ba71fb8ea2c3de147a5ee65bc0d22405` — confirmed byte-identical in the v1 and v2/v3 blocks, i.e. exactly what `signingInfo.apkContentsSigners` reports |

**Native libraries (`lib/arm64-v8a/`, 19 files):** `libgame-BPM-GooglePlay-Gold-Release-Module-4028.so` (85.5 MB game module; `4028` in the name matches the version code), `libloader.so` (7.6 MB), `libadsurgeflex.so`, `libadsurgeqjs.so`, `libanybrainSDK.so`, `libapminsighta.so`, `libapminsightb.so`, `libapplovin-native-crash-reporter.so`, `libbuffer_pgl.so`, `libc++_shared.so`, `libcrashlytics-common.so`, `libcrashlytics-handler.so`, `libcrashlytics-trampoline.so`, `libcrashlytics.so`, `libdatastore_shared_counter.so`, `libfile_lock_pgl.so`, `libnms.so`, `libpglarmor.so`, `libtt_ugen_layout.so` — the `armeabi-v7a` set mirrors the same 19 names.

**Cross-check with this repository:** `docs/AndroidManifest.xml` and **all twelve** `docs/classes*.dex` files are byte-identical (SHA-256 equal) to the corresponding entries in this APK — the earlier upload came from exactly this release. The dump stays partial by design: `classes2`–`classes9` and `classes20`–`classes22` exist only in the local APK; further static analysis reads them from `local/target-apk/` instead of adding more binaries to Git.

**Still not established:** the game has not been run on any device — there is no runtime evidence. Per the [Phase 2 plan](phase2-plan.md), the M3 device-validation protocol remains the gate before any claim that the target executes inside Aether.
