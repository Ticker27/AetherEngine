#!/usr/bin/env bash
# Align and sign one APK with Android APK Signature Scheme v1 and v2 only.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: sign_release_apk.sh <unsigned-apk> <signed-apk> <keystore>" >&2
  exit 2
fi
: "${RELEASE_STORE_PASSWORD:?RELEASE_STORE_PASSWORD is required}"
: "${RELEASE_KEY_ALIAS:?RELEASE_KEY_ALIAS is required}"
: "${RELEASE_KEY_PASSWORD:?RELEASE_KEY_PASSWORD is required}"
: "${ANDROID_BUILD_TOOLS_VERSION:?ANDROID_BUILD_TOOLS_VERSION is required}"

unsigned=$1
signed=$2
keystore=$3
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
tools="$sdk_root/build-tools/$ANDROID_BUILD_TOOLS_VERSION"
zipalign="$tools/zipalign"
apksigner="$tools/apksigner"
if [[ ! -x "$zipalign" ]]; then
  zipalign="$(command -v zipalign || true)"
fi
if [[ ! -x "$apksigner" ]]; then
  apksigner="$(command -v apksigner || true)"
fi
[[ -x "$zipalign" ]] || { echo "zipalign not found for build-tools $ANDROID_BUILD_TOOLS_VERSION" >&2; exit 1; }
[[ -x "$apksigner" ]] || { echo "apksigner not found for build-tools $ANDROID_BUILD_TOOLS_VERSION" >&2; exit 1; }
jarsigner_bin="$(command -v jarsigner || true)"
[[ -x "$jarsigner_bin" ]] || { echo "jarsigner is required for the v1 JAR signature" >&2; exit 1; }
[[ -f "$unsigned" ]] || { echo "unsigned APK not found: $unsigned" >&2; exit 1; }
[[ -f "$keystore" ]] || { echo "keystore not found: $keystore" >&2; exit 1; }

aligned="${signed%.apk}.aligned.apk"
v1_signed="${signed%.apk}.v1.apk"
mkdir -p "$(dirname "$signed")"
rm -f "$aligned" "$v1_signed" "$signed"
"$zipalign" -f -p 4 "$unsigned" "$aligned"
"$jarsigner_bin" \
  -keystore "$keystore" \
  -storetype PKCS12 \
  -storepass:env RELEASE_STORE_PASSWORD \
  -keypass:env RELEASE_KEY_PASSWORD \
  -signedjar "$v1_signed" \
  "$aligned" "$RELEASE_KEY_ALIAS" >/tmp/aether-jarsigner.log
"$apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$RELEASE_KEY_ALIAS" \
  --ks-pass "env:RELEASE_STORE_PASSWORD" \
  --key-pass "env:RELEASE_KEY_PASSWORD" \
  --min-sdk-version 1 \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --v4-signing-enabled false \
  --append-signature true \
  --out "$signed" \
  "$v1_signed"
rm -f "$aligned" "$v1_signed" /tmp/aether-jarsigner.log
"$apksigner" verify --verbose "$signed"
