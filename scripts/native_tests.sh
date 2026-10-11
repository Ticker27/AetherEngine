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

TEMP_ROOT="${TMPDIR:-/tmp}"
[ -d "$TEMP_ROOT" ] || TEMP_ROOT=/tmp
OUT="$(mktemp -d "$TEMP_ROOT/aether-native.XXXXXX")"
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
echo "== JNI registry compile check (no NDK: compat jni.h) =="
# native_registry.cpp is the only translation unit that the Android build compiles but the
# host-side suite does not, so a signature drift between the Kotlin facade and the C++ table
# would otherwise only surface as a LinkageError on a device. Compile it here instead.
# shellcheck disable=SC2086
"$CXX" -std=c++17 -fsyntax-only $INCLUDES \
    aether-native/src/main/cpp/jni/native_registry.cpp \
    aether-native/src/main/cpp/jni/jni_onload.cpp
echo "JNI registry compiles clean."

echo ""
echo "Native test suite passed."
