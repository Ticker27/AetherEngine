# Snake Engine reference bundle

This directory contains the Snake Engine analysis artifacts brought in from the repository's `main/docs` directory. Treat them as a **reference snapshot**, not as source code for Aether and not as evidence about the exact 8 Ball Pool 56.30.0 binary.

## Inventory

| File | Purpose |
| --- | --- |
| `AndroidManifest.xml`, `resources.arsc`, `classes.dex` | Raw Android package artifacts supplied for the Snake analysis |
| `libapp.so`, `libapp_analysis.txt`, `libapp_rodata_strings.txt` | Flutter/Dart AOT artifact and its analysis |
| `libengine_analysis.txt`, `libengine_dump.txt`, `libengine_F3_strings.txt`, `libengine_antitamper_strings.txt` | Textual analysis and string extracts for Snake's native engine |
| `EVIDENCE_CHAIN.txt`, `NATIVE_CALLSITE_MAP.txt`, `หลักฐาน-snake-dex-manifest.txt` | Architecture narrative, call-site table, and DEX/manifest notes |

The native `libengine.so` binary itself is **not** in this bundle; the native-engine entries are analysis text and extracts.

## Architecture reported by the evidence

The notes describe Snake as a Flutter/Dart front end (`libapp.so` and Flutter embedding) alongside a distinct native engine (`libengine.so` loaded as `engine`). They further describe a multi-process guest container, with a server process, child process slots, proxy Android components, and package/activity mediation. These ideas are useful for comparing system boundaries: Flutter Engine, Dart code, the host's custom JNI, and a guest APK are separate concerns.

This is not a drop-in implementation plan. Aether's current `DynamicApkLoader` creates a DEX class loader; it does not reproduce Snake's package manager, activity manager, guest context, component dispatch, or process model. Do not describe the current host as running a normal guest APK.

## Integrity and version caveats

The files and reports do not all identify one consistent build. Checks performed on the files currently in this directory found:

| Artifact | File in this bundle | Value reported by a related note | Result |
| --- | --- | --- | --- |
| `classes.dex` | 3,881,048 bytes; MD5 `34bf488b6ef3f3bd54ce805153d464a7`; SHA-256 `e2b1fb586a9a85b4f094340458ea350eb1faafd602b8a4a287d4b3b97297af11` | `หลักฐาน-snake-dex-manifest.txt` reports 3,868,092 bytes and MD5 `006e396872a920cb16a71cf12a7327a3` | Does not match the described snapshot |
| `AndroidManifest.xml` | 55,176 bytes; MD5 `0f25dd8113eb921a5a027b21ef3f69f7`; SHA-256 `3471243c394371496cfdab37eb7a2ccd33389612b221a39c51148267ef9440a0` | The same note reports 54,228 bytes and MD5 `379ad4c8599ef1c889e079e4b4e4b11c` | Does not match the described snapshot |
| `libapp.so` | 5,637,024 bytes; SHA-256 `2d3577fbaaacc7cb63e5b04a5a21572eeee1e0d55b223941d6a5496a91a427c8` | `libapp_analysis.txt` reports the same size and SHA-256 | Matches that analysis record |
| `libengine.so` | Not included | `libengine_analysis.txt` reports 8,544,568 bytes and MD5 `2ae5d2b628942e13fce7a48728ebee25` | Cannot independently verify without the binary |

The notes also differ on Snake Engine's version: the DEX/manifest note names `2.1.3`, while `EVIDENCE_CHAIN.txt` names `2.2.6`. Game-version references in the bundle include 8 Ball Pool `56.23.2` and `56.29.1`; they do not establish compatibility with `56.30.0`.

## Contract boundary

Native method names and descriptors reported for `com.snake.helper.Native` or `com.snake.helper.flagger` are Snake-specific. They are not the declarations for Aether's `com.aether.host.bridge.Native`, the previously described `com.aether.helper.*` contract, or the 8 Ball Pool APK. Do not copy JNI signatures across those identities without matching binary evidence.

The records discuss licensing, anti-tamper, root/instrumentation detection, hidden framework behavior, and runtime hooks. This bundle is kept for architectural comparison and evidence tracking; it is not an instruction to bypass a game's license, anti-cheat, anti-tamper, or platform security controls.

## Exact target still requiring its own evidence

The selected target remains 8 Ball Pool `com.miniclip.eightballpool` version `56.30.0` / version code `4028`. To claim target-specific support, inspect that exact APK (or an exact split package) for its manifest, DEX, signing certificate, ABI libraries, and runtime. Snake artifacts are supporting reference only. See [`docs/target-apk.md`](../../target-apk.md).
