# Decision F-3: keep AGP 8.5.0 (no bump this round)

- Order: BLOCK B / D-B.1, Phase-A1 closure
- Status: **KEPT** — AGP is not bumped in this round
- Date: 2026-10-04

## Context

- `aether-engine/build.gradle.kts` pins `com.android.application` version `8.5.0`.
- `aether-engine/app/build.gradle.kts` sets `compileSdk = 35`, `minSdk = 28`, `targetSdk = 35`.
- AGP 8.5.0 was validated up to compileSdk 34, so builds emit the "tested up to" warning for SDK 35. It is a **warning, not an error**.

## Decision

Keep AGP 8.5.0 with compileSdk 35. Rationale:

1. compileSdk 35 / minSdk 28 / targetSdk 35 are locked parameters (spec §1); changing them is not in scope for a closure round.
2. CI is green with AGP 8.5.0 — runs `37217095131` (AetherEngine CI) and `37217095084` (aether-engine), commit `d28c3ff12f22fc6df1446756dd798cdf4bf405a4`.
3. The warning has no effect on produced binaries.

## Evidence

- `phase-a1/evidence/artifacts.txt` — APKs produced by the locked configuration
- `phase-a1/evidence/needed.txt` — `libaether.so` dynamic dependencies (4 entries: liblog, libm, libdl, libc)

## Revisit condition

A separate order must request the bump. See `docs/decisions/agp-sdk-watch.md` for the required two-file co-change.