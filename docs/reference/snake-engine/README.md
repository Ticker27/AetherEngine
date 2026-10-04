# Snake Engine reference bundle

Snake Engine is best described as an **Android guest-app virtualization/container host**, not as the guest game itself and not merely as a generic Flutter app. The inspected package is `com.snake` (`versionName=2.2.6`); the accompanying reports describe a Flutter/Dart UI/control plane and a separate native guest-container engine.

Only the package's `assets/` and `res/` folders were extracted from `docs/snake.zip`. They are preserved as opaque data under [`android-host/src/main/assets/snake/`](../../../android-host/src/main/assets/snake/). The ZIP and its duplicate loose binaries are not retained. See the [static extraction log](ARCHIVE_EXTRACTION_LOG.md).

## What is in the extracted folders

- `assets/` contains Flutter package assets, including fonts, SVG icons, generated asset manifests/notices, a shader, and a baseline profile.
- `res/` contains package-compiled Android resources (binary AXML, PNG, and WebP files) with obfuscated resource names. These are **not** source files suitable for direct placement in `src/main/res` and do not provide Snake resource IDs through Aether's `R` class. They are packaged below `assets/snake/res/` to avoid resource-table collisions.
- The native engine, Dart AOT, Flutter Engine, DEX, manifest, and `resources.arsc` were intentionally not imported. Aether continues to build `libaether.so` and use its own Flutter-generated `libapp.so` and `libflutter.so`.

## Capability and evidence boundary

The accompanying static analyses characterize Snake as a guest APK container, with Flutter as the UI/control plane and native/process/proxy components for guest handling. That is materially different from Aether's current `DynamicApkLoader`, which creates a DEX class loader and does not implement guest package-manager virtualization, process slots, resource resolution, or arbitrary Activity launch.

The extraction log records the original ZIP's package metadata and static inspection before that ZIP was removed; the retained verifier reproduces only the copied `assets/` and `res/` inventories and hashes. Neither that static inspection nor the retained payload proves runtime guest installation or launch. Textual analyses of `libapp.so`/`libengine.so` and reports citing external files such as `comsnake-live` or `com.ninja.engine.zip` are analyst records whose source binaries/runtime inputs are not retained here; their claims are not independently reproducible from this compact payload and do not describe Aether capabilities.

## Snapshot caveats

- The inspected manifest reports `com.snake` versionName `2.2.6`, min API 28 and target API 35.
- `หลักฐาน-snake-dex-manifest.txt` reports an older DEX/manifest pair (`2.1.3`) with different hashes and sizes; it is a historical snapshot, not metadata for the inspected package.
- References to 8 Ball Pool `56.23.2` or `56.29.1` are external/historical analysis notes. The extracted folders are not an 8 Ball Pool APK and do not support Aether's selected 56.30.0 target.
- The extraction does not port Snake's guest runtime or authorize bypassing licensing, anti-cheat, anti-tamper, hidden-API, or platform protections.

The selected Aether target remains 8 Ball Pool `com.miniclip.eightballpool` version `56.30.0` / listing version code `4028`; the exact game APK still requires independent inspection. See [`docs/target-apk.md`](../../target-apk.md).
