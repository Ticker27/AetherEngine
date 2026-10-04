# AGP / SDK watch

**Current**: AGP 8.5.0 with `compileSdk 35` / `minSdk 28` / `targetSdk 35`. Builds emit the "validated up to compileSdk 34" warning; no errors — CI green on commit `d28c3ff12f22fc6df1446756dd798cdf4bf405a4`.

**Future bump requires a co-change in two files** — editing one alone breaks the other:

1. `aether-engine/build.gradle.kts` — the AGP version string
2. `.github/workflows/aether-engine.yml` — the embedded contract test asserts `'version "8.5.0"'` (Test job)

Also re-check on any bump:

- `aether-engine/gradle/wrapper/gradle-wrapper.properties` — Gradle version must satisfy the AGP minimum (currently `gradle-8.7-bin.zip`)
- Build job SDK pins in `.github/workflows/aether-engine.yml`: `platforms;android-35`, `build-tools;35.0.0`, `ndk;26.3.11579264`, `cmake;3.22.1`

The decision to keep AGP 8.5.0 for this round is recorded in `docs/decisions/F-3-kept.md`.