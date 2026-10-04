# Audit corrections

## MM-01 / MM-12 — module name (re-verified, D-A1.8)

Commands and raw output: `phase-a1/evidence/module_name.txt`.

Findings:

- `settings.gradle.kts` line 20: `rootProject.name = "AetherEngine"`; line 22: `include(":android-host")` → the Gradle module is **android-host**.
- `.github/workflows/ci.yml` line 125: `name: aether-host-apks-${{ github.sha }}` → the CI artifact label is a **hardcoded upload-artifact name**, not a module rename.
- Artifact inventory (`phase-a1/evidence/artifacts.txt`): `aether-host-apks-d28c3ff12f22fc6df1446756dd798cdf4bf405a4`, archive sha256 `b0597a9008b77b8711df6ba13a2d9d3cc8296be5ecaf2e7bba3d7b95a20f67e4` (53,117,486 bytes).

**Verdict: the audit v5 statement was correct** — the module is `android-host`. The apparent MM-01 / MM-12 mismatch is a cosmetic artifact label only.

**Action**: no rename. Recorded as an exemption: the spec intent for the legacy host is `android-host`, and renaming would change the applicationId / Flutter coupling and invalidate previously collected CI evidence.

## Related observation (not a correction)

The ordered static commands for the fnPtr table target `app-debug.apk` / `n_*` symbols, which exist only in the **aether-engine** artifact (`aether-engine-debug-d28c3ff…`), while the artifact named in D-A1.1 is the **legacy** `aether-host-apks-d28c3ff…`. Both were analysed; the legacy `.so` carries a different (4-method) JNI contract and 6 `NEEDED` entries (`phase-a1/evidence/needed.txt`, `symbols.txt`). Phase 1 DoD evidence is reported against the aether-engine artifact.