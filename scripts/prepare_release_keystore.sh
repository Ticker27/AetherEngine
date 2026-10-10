#!/usr/bin/env bash
# Materialize the production release keystore from an Actions secret.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: prepare_release_keystore.sh <output-keystore>" >&2
  exit 2
fi
: "${RELEASE_KEYSTORE_BASE64:?RELEASE_KEYSTORE_BASE64 is required}"

output=$1
mkdir -p "$(dirname "$output")"
umask 077
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$output"
[[ -s "$output" ]] || { echo "release keystore is empty" >&2; exit 1; }
chmod 600 "$output"
