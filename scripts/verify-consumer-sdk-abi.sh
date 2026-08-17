#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE="$ROOT_DIR/docs/shared-runtime/consumer-sdk-public-abi.txt"
ACTUAL="${CONSUMER_SDK_ABI_OUTPUT:-$ROOT_DIR/build/consumer-sdk-repository/public-abi.txt}"

if grep -q '^# Bootstrap placeholder' "$BASELINE"; then
  echo "Consumer SDK ABI baseline is not frozen yet" >&2
  exit 2
fi

if ! diff -u "$BASELINE" "$ACTUAL"; then
  echo "Consumer SDK public ABI changed. Review compatibility and update the baseline only with an intentional SDK version decision." >&2
  exit 1
fi

printf 'Consumer SDK public ABI matches baseline\n'
