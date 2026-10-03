# AetherEngine

PHASE 0 — CI Skeleton monorepo: Android host + Native C++ (CMake) + Rust runtime + Flutter console.

CI: GitHub Actions (`.github/workflows/ci.yml`) gates every push/PR to `main`:

- `structure` — repository layout check
- `android` — Gradle build + unit tests (JDK 17)
- `native` — CMake/Ninja build + ctest
- `rust` — cargo check/test/build
- `flutter` — flutter pub get/analyze/test
- `ci-gate` — final pass/fail gate

See CI contract and Definition of Done in the project docs. Release/signing/deployment are out of scope for PHASE 0.
