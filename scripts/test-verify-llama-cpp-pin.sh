#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
verifier="$script_dir/verify-llama-cpp-pin.sh"
temporary_root="$(mktemp -d)"
trap 'rm -rf "$temporary_root"' EXIT

assert_uninitialized_failure() {
  local case_name="$1"
  local repository_path="$2"
  local stderr_file="$temporary_root/$case_name.stderr"

  if (cd "$repository_path" && bash "$verifier" > /dev/null 2>"$stderr_file"); then
    echo "$case_name unexpectedly passed" >&2
    exit 1
  fi

  if ! grep -Fq "llama.cpp submodule is not initialized" "$stderr_file"; then
    echo "$case_name did not report the missing submodule clearly" >&2
    cat "$stderr_file" >&2
    exit 1
  fi

  if grep -Fq "pin mismatch" "$stderr_file"; then
    echo "$case_name reported a false pin mismatch" >&2
    cat "$stderr_file" >&2
    exit 1
  fi
}

missing_path_repository="$temporary_root/missing-path"
mkdir -p "$missing_path_repository"
git -C "$missing_path_repository" init -q
assert_uninitialized_failure "missing-path" "$missing_path_repository"

empty_path_repository="$temporary_root/empty-path"
mkdir -p "$empty_path_repository/third_party/llama.cpp"
git -C "$empty_path_repository" init -q
assert_uninitialized_failure "empty-path" "$empty_path_repository"

echo "verify-llama-cpp-pin regression tests passed"
