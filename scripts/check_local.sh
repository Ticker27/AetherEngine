#!/usr/bin/env sh
# One-command local verification for AetherEngine.
#
# Runs everything that works without an Android toolchain:
#   1. repository structure verifier
#   2. host-side native test suite (runtime state + smoke demo)
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
sh scripts/native_tests.sh
echo ""
echo "=============================================="
echo " ALL LOCAL CHECKS PASSED"
echo " (Gradle/Flutter/APK checks run in CI)"
echo "=============================================="
