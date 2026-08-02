#!/usr/bin/env bash
set -euo pipefail

expected_commit="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
actual_commit="$(git -C third_party/llama.cpp rev-parse HEAD)"

if [[ "$actual_commit" != "$expected_commit" ]]; then
  echo "llama.cpp pin mismatch: expected $expected_commit, found $actual_commit" >&2
  exit 1
fi

if [[ -n "$(git -C third_party/llama.cpp status --porcelain)" ]]; then
  echo "llama.cpp submodule contains uncommitted changes" >&2
  exit 1
fi

echo "llama.cpp pin verified: $actual_commit"
