# Audit corrections

## MM-01 / MM-12 — module name (reclassified, D-A15.5)

Original commands and raw output: `phase-a1/evidence/module_name.txt`.

Findings (unchanged since the A1 round):

- `settings.gradle.kts` line 20: `rootProject.name = "AetherEngine"`; line 22: `include(":android-host")` → the Gradle module is **android-host**.
- The CI artifact name was a hardcoded upload-artifact label in `.github/workflows/ci.yml` line 125, unrelated to the module name.

**Reclassification (D-A15.5): MM-01 and MM-12 are *cosmetic*, not a true mismatch.** The module has always been `android-host`; only the artifact label used a different word. **The audit v5 module-name statement was correct** and needs no correction.

**Status: label fixed (NEW-7).** The label was changed to `android-host-apks-${{ github.sha }}` — label only, no logic change (D-A15.1). A guard in `.github/workflows/aether-engine.yml` now asserts the artifact label matches the module name from `settings.gradle.kts`, so the two cannot drift again.

Historical note: `phase-a1/evidence/artifacts.txt` records the pre-fix artifact name `aether-host-apks-d28c3ff…` as raw, point-in-time evidence and is intentionally left unchanged.

**No rename of the module was performed** (spec intent for the legacy host remains `android-host`); renaming would change the applicationId / Flutter coupling and invalidate previously collected CI evidence.

## Related observation (not a correction)

The ordered static commands for the fnPtr table target `app-debug.apk` / `n_*` symbols, which exist only in the **aether-engine** artifact (`aether-engine-debug-d28c3ff…`), while the artifact named in D-A1.1 is the **legacy** host artifact. Both were analysed; the legacy `.so` carries a different (4-method) JNI contract and 6 `NEEDED` entries (`phase-a1/evidence/needed.txt`, `symbols.txt`). Phase 1 DoD evidence is reported against the aether-engine artifact.