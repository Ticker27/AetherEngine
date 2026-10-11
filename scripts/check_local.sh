#!/usr/bin/env sh
# One-command local verification for AetherEngine.
#
# Runs everything that works without an Android toolchain:
#   1. repository structure verifier
#   2. Kotlin static consistency (unresolved types, bad labels, JNI parity)
#   3. dead code and dangling references
#   4. host-side native test suite (runtime state + smoke demo + JNI compile check)
#
# Gradle unit tests, Flutter tests, and APK verification need the full
# toolchain and are run by CI (.github/workflows/ci.yml).
set -eu

cd "$(dirname "$0")/.."

echo "=============================================="
echo " AetherEngine local checks"
echo "=============================================="
echo ""
echo "-- repository structure --"
python3 scripts/verify_host_structure.py
echo ""
echo "-- kotlin static consistency --"
python3 scripts/check_kotlin_static.py
echo ""
echo "-- dead code and dangling references --"
# Reported, not fatal: a human decides what is genuinely removable. Anything *dangling*
# (referenced but missing) still fails this script.
python3 scripts/find_dead_code.py
echo ""
sh scripts/native_tests.sh
echo ""
echo "=============================================="
echo " ALL LOCAL CHECKS PASSED"
echo " (Gradle/Flutter/APK checks run in CI)"
echo "=============================================="
