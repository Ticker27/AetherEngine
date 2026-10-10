#!/usr/bin/env bash
# Align and sign the host release APK with Android signature schemes v1 and v2.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: sign_release_apk.sh <unsigned-apk> <signed-apk> <keystore>" >&2
  exit 2
fi

unsigned_apk=$1
signed_apk=$2
keystore=$3
: "${ANDROID_HOME:?ANDROID_HOME is required}"
: "${ANDROID_BUILD_TOOLS_VERSION:?ANDROID_BUILD_TOOLS_VERSION is required}"
: "${RELEASE_STORE_PASSWORD:?RELEASE_STORE_PASSWORD is required}"
: "${RELEASE_KEY_ALIAS:?RELEASE_KEY_ALIAS is required}"
: "${RELEASE_KEY_PASSWORD:?RELEASE_KEY_PASSWORD is required}"

tools_dir="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS_VERSION"
zipalign="$tools_dir/zipalign"
apksigner="$tools_dir/apksigner"
[[ -x "$zipalign" ]] || { echo "zipalign not found: $zipalign" >&2; exit 1; }
[[ -x "$apksigner" ]] || { echo "apksigner not found: $apksigner" >&2; exit 1; }
[[ -f "$unsigned_apk" ]] || { echo "unsigned APK not found: $unsigned_apk" >&2; exit 1; }
[[ -f "$keystore" ]] || { echo "release keystore not found" >&2; exit 1; }

mkdir -p "$(dirname "$signed_apk")"
aligned_apk="${signed_apk}.aligned"
trap 'rm -f "$aligned_apk"' EXIT

"$zipalign" -f -p 4 "$unsigned_apk" "$aligned_apk"
"$apksigner" sign \
  --ks "$keystore" \
  --ks-type PKCS12 \
  --ks-key-alias "$RELEASE_KEY_ALIAS" \
  --ks-pass env:RELEASE_STORE_PASSWORD \
  --key-pass env:RELEASE_KEY_PASSWORD \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --v4-signing-enabled false \
  --out "$signed_apk" \
  "$aligned_apk"

"$apksigner" verify --verbose "$signed_apk"
echo "Signed release APK: $signed_apk"
