#!/usr/bin/env bash
set -euo pipefail

expected_commit="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
submodule_path="${LLAMA_CPP_SUBMODULE_PATH:-third_party/llama.cpp}"

print_initialization_help() {
  echo "llama.cpp submodule is not initialized at $submodule_path" >&2
  echo "Run: git submodule update --init --recursive $submodule_path" >&2
}

if [[ ! -d "$submodule_path" || ! -e "$submodule_path/.git" ]]; then
  print_initialization_help
  exit 1
fi

expected_root="$(cd "$submodule_path" && pwd -P)"
actual_root="$(git -C "$submodule_path" rev-parse --show-toplevel 2>/dev/null || true)"

if [[ -z "$actual_root" || "$actual_root" != "$expected_root" ]]; then
  print_initialization_help
  exit 1
fi

actual_commit="$(git -C "$submodule_path" rev-parse HEAD)"

if [[ "$actual_commit" != "$expected_commit" ]]; then
  echo "llama.cpp pin mismatch: expected $expected_commit, found $actual_commit" >&2
  exit 1
fi

if [[ -n "$(git -C "$submodule_path" status --porcelain)" ]]; then
  echo "llama.cpp submodule contains uncommitted changes" >&2
  exit 1
fi

echo "llama.cpp pin verified: $actual_commit"
