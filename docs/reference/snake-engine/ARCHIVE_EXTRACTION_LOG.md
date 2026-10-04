# Static extraction log: Snake payload folders

This log records a static inspection of the uploaded `docs/snake.zip`. That package ZIP was used as an extraction input and is **not retained** in the final tree; only its `snake/assets/` and `snake/res/` folders were selected. No DEX, `resources.arsc`, or `.so` file from Snake was copied into Aether. The separately retained `docs/com.snake.zip` is a runtime-data snapshot, not this package archive and not an extraction source; see [`RUNTIME_SNAPSHOT_REVIEW.md`](RUNTIME_SNAPSHOT_REVIEW.md).

## Source identity and size

| Property | Value |
| --- | --- |
| Input archive | `docs/snake.zip` (removed after the requested folders were extracted) |
| Original ZIP size | 17,247,537 bytes (16.45 MiB) |
| Original ZIP SHA-256 | `4d36f591f0fd4b521f1dd53621d0b28ba8c70398b8fd0b8b9324cd151ab2c0fb` |
| Archive members | 951 files; 31,675,417 uncompressed bytes |
| Extracted into Aether | `android-host/src/main/assets/snake/{assets,res}/` |
| Imported payload total | 1,507,495 bytes (1,141,120 assets + 366,375 res) |

Before removal, the archived Android manifest was parsed and identified package `com.snake`, manifest `versionName=2.2.6`, `minSdk=28`, `targetSdk=35`, and `compileSdk=35`. The archive contained native files for `arm64-v8a`; none were copied. It was a Snake package snapshot, not an 8 Ball Pool APK.

Before removing the ZIP, the following metadata was recorded for the excluded package binaries (they are not retained): `classes.dex` — 3,881,048 bytes, MD5 `34bf488b6ef3f3bd54ce805153d464a7`, SHA-256 `e2b1fb586a9a85b4f094340458ea350eb1faafd602b8a4a287d4b3b97297af11`; `AndroidManifest.xml` — 55,176 bytes, MD5 `0f25dd8113eb921a5a027b21ef3f69f7`, SHA-256 `3471243c394371496cfdab37eb7a2ccd33389612b221a39c51148267ef9440a0`.

## What Snake is, and what the extraction does not do

The accompanying analyses characterize Snake Engine as an **Android guest-app virtualization/container host**: Flutter/Dart is its UI/control plane, while a separate native engine and proxy/process components are described as the guest runtime. It is therefore more than a generic Flutter screen or a game APK. The pre-removal static inspection recorded the package identity, manifest declarations, and bundled payload; those static facts do **not by themselves** prove successful guest installation/launch or every runtime claim in the separate analysis notes.

Copying `assets/` and `res/` does not port that virtualization capability. Those folders contain UI/package resources, not the native guest-process implementation. Aether remains the selected 8 Ball Pool 56.30.0 host; Snake binaries and runtime behavior are not used to run that target.

## Extracted folders and integrity

| Folder | Files | Bytes | Tree SHA-256 |
| --- | ---: | ---: | --- |
| `android-host/src/main/assets/snake/assets/` | 16 | 1,141,120 | `6c8151a2e8734b0ad995adcc76aaf06b070fba935449cd4a09afb7e6927fc9d5` |
| `android-host/src/main/assets/snake/res/` | 840 | 366,375 | `2d8e87edd1172f25456a2baf6a38e986f7a9b94d97299b13d753380d85b068d7` |

`assets/` contains the package's Flutter-asset manifests, notices, shader, fonts, SVG icons, and baseline profile. These are preserved under `assets/snake/` to avoid colliding with Aether's generated `assets/flutter_assets/`.

`res/` contains 602 XML files (601 begin with the compiled binary AXML header; one is text XML), 223 PNG files, and 15 WebP files. A simple recorded lowercase-alphanumeric/underscore filename-shape check (including the extension) does not match 463 filenames; this is an inventory observation, not a claim that every mismatch alone makes a resource unusable. Because this is a compiled package tree with obfuscated names and no accompanying `resources.arsc`, the files are kept as **opaque Android assets** under `assets/snake/res/`, not under `android-host/src/main/res/`; they cannot be accessed through Aether's generated `R` IDs or merged into Aether's resource table.

The payload verifier checks file counts, byte totals, and deterministic tree hashes: `python3 scripts/verify_snake_payload.py`.

## Snapshot mismatches and external claims

- `หลักฐาน-snake-dex-manifest.txt` describes an older pair: DEX 3,868,092 bytes / MD5 `006e396872a920cb16a71cf12a7327a3` and manifest 54,228 bytes / MD5 `379ad4c8599ef1c889e079e4b4e4b11c`; it labels that manifest `versionName=2.1.3`. Those hashes do not match the inspected archive, whose manifest says `2.2.6`.
- `EVIDENCE_CHAIN.txt` and `NATIVE_CALLSITE_MAP.txt` cite additional inputs, including external `comsnake-live` and `com.ninja.engine.zip`. These were not members of `docs/snake.zip`; the later-added `docs/com.snake.zip` is a third, separate runtime-data snapshot reviewed in [`RUNTIME_SNAPSHOT_REVIEW.md`](RUNTIME_SNAPSHOT_REVIEW.md). Its limited inventory does not establish a specific game APK, successful runtime launch, or Aether capability. Keep all such analyses separate from the curated Snake assets/resources and do not treat them as Aether implementation instructions.
- The Snake `.so` files were not ported. Aether continues to build its own `libaether.so` and obtains its own `libapp.so`/`libflutter.so` from the Flutter toolchain. Snake binaries are not signer pins or evidence of compatibility with 8 Ball Pool 56.30.0.
