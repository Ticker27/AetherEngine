# Review of `docs/com.snake.zip` runtime-data snapshot

Reviewed on 2026-10-04. This is a separate evidence set from `docs/snake.zip`, the historical Snake package archive documented in [`ARCHIVE_EXTRACTION_LOG.md`](ARCHIVE_EXTRACTION_LOG.md).

## Scope and handling

The archive was inspected with Python's ZIP reader. Member contents were read only in memory for an allow-listed set of metadata checks; no files were extracted to the workspace, copied into Aether, or added to the Android assets/payload. Sensitive database, preference, WebView, crash-report, and SDK-state contents were not transcribed here.

The archive contains app-private persisted state and third-party SDK data. Treat it as sensitive; avoid raw dumps or broad redistribution. This note intentionally omits identifiers and credential-like values.

## Archive identity and integrity

| Property | Observed value |
| --- | --- |
| Archive | `docs/com.snake.zip` |
| ZIP size | 3,108,570 bytes |
| SHA-256 | `e079c6a972a93def6f73d59e958ea6d619e9beea0810d36aab3fadf923ed3f3c` |
| ZIP entries | 338 total: 230 files and 108 directories |
| Uncompressed file bytes | 7,809,849 |
| Member path layout | All entries are below `com.snake/`; no absolute or `..` member paths |
| ZIP CRC check | Passed |

## Selected observations

- `com.snake/root/proc/0/cmdline` is a 26-byte snapshot member whose recorded process name is `com.miniclip.eightballpool`. This is evidence about the captured filesystem snapshot, not independently reproducible proof of a successful guest launch.
- `com.snake/root/data/app/com.miniclip.eightballpool/package.conf` is a 173,904-byte binary metadata file. Allow-listed strings identify the package and the `EightBallPoolActivity` / `EightBallPoolApplication` names, include `56.23.2`, and reference an installed `base.apk` path (the path itself is omitted). The referenced APK is not a ZIP member. This review does **not** establish a versionCode. A separately named `56.23.2.plist` preference file is also present; a preference filename is not, by itself, authoritative installed-package version metadata.
- The ZIP has no `.apk`, `.dex`, or `.so` members. It does include five `com.snake/oat/arm64/Anonymous-DexFile@*.vdex` files (108, 8,748, 156, 300, and 156 bytes). These are VDEX cache records, not an APK, source DEX, or native guest library.
- The archive contains app-private data and caches, including database, shared-preference, WebView, crash-report, and SDK-state files. Their values were not copied into this report.

The `package.conf` SHA-256 in this archive is `34afdbbe314935324e5cb5aa535e5ea8ef8fae042c82038b0e5279c160d60b6b`. It does not match the `8ce5af64…` prefix cited for the separate `comsnake-live`/`com.snake_1.zip` material in older analyst notes. Do not treat those snapshots as byte-identical or attribute external library inventories to this ZIP.

## Relationship to the Snake payload and Aether target

- `docs/snake.zip` was a separate static package archive. Its extraction log records that only its `assets/` and `res/` folders were copied, as opaque data, to `android-host/src/main/assets/snake/{assets,res}/`.
- `docs/com.snake.zip` is a runtime-data snapshot, not that package archive and not an APK source. It was not unpacked into Aether and must not be used as a payload source.
- The snapshot's `56.23.2` metadata is older than the selected 8 Ball Pool `56.30.0` target. It contains no game APK or `.so` library and does not establish support for that target or successful game execution.

See [`EVIDENCE_CHAIN.txt`](EVIDENCE_CHAIN.txt) for the corresponding corrections to older runtime-analysis wording.