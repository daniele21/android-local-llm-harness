#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE="$ROOT_DIR/docs/shared-runtime/consumer-sdk-public-abi.txt"
ACTUAL="${CONSUMER_SDK_ABI_OUTPUT:-$ROOT_DIR/build/consumer-sdk-repository/public-abi.txt}"

expected="$(sed -n 's/^sha256=//p' "$BASELINE")"
if [[ ! "$expected" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Consumer SDK ABI baseline is missing or invalid" >&2
  exit 2
fi

if command -v sha256sum >/dev/null 2>&1; then
  actual="$(sha256sum "$ACTUAL" | awk '{print $1}')"
else
  actual="$(shasum -a 256 "$ACTUAL" | awk '{print $1}')"
fi

if [[ "$actual" != "$expected" ]]; then
  echo "Consumer SDK supported ABI changed." >&2
  echo "expected_sha256=$expected" >&2
  echo "actual_sha256=$actual" >&2
  echo "Review compatibility and update the baseline only with an intentional SDK version decision." >&2
  exit 1
fi

printf 'Consumer SDK supported ABI matches baseline: %s\n' "$actual"
