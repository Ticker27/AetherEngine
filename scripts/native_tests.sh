#!/usr/bin/env sh
# Build and run the host-side native test suite for the Aether native lane.
#
# Needs only a C++17 compiler (g++ or clang++). No JDK, NDK, or Android SDK.
# Used by scripts/check_local.sh and by GitHub Actions.
set -eu

cd "$(dirname "$0")/.."

CXX="${CXX:-g++}"
command -v "$CXX" >/dev/null 2>&1 || {
    echo "error: no C++ compiler found; install g++/clang++ or set CXX" >&2
    exit 1
}

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT INT TERM

INCLUDES="-I aether-native/src/main/cpp -I integration-test/native/compat"

echo "== native runtime-state tests =="
# shellcheck disable=SC2086
"$CXX" -std=c++17 $INCLUDES \
    aether-native/src/main/cpp/runtime/runtime_state.cpp \
    integration-test/native/runtime_state_test.cpp \
    -o "$OUT/runtime_state_test" -pthread
"$OUT/runtime_state_test"

echo ""
echo "== native smoke demo (state machine + dispatcher + message bridge) =="
# shellcheck disable=SC2086
"$CXX" -std=c++17 $INCLUDES \
    aether-native/src/main/cpp/runtime/runtime_state.cpp \
    aether-native/src/main/cpp/platform/thread_dispatcher.cpp \
    aether-native/src/main/cpp/bridge/message_bridge.cpp \
    aether-native/src/main/cpp/bridge/engine_bridge.cpp \
    integration-test/native/native_smoke_demo.cpp \
    -o "$OUT/native_smoke_demo" -pthread
"$OUT/native_smoke_demo"

echo ""
echo "Native test suite passed."
