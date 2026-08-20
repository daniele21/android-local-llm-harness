#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/qualify-llama-cpp-candidate.sh <40-char-commit-sha>

Qualifies one explicit llama.cpp candidate without changing the repository pin.
The pinned submodule checkout and any LLRT-5 compatibility source overlay are restored
on exit.

Environment:
  LLRT_RUN_NATIVE=1   run the host-native llama.cpp suite (default: 1)
  LLRT_RUN_ANDROID=1  build the Android llama.cpp backend and device runner (default: 0)

At least one lane must be enabled.
EOF
}

candidate_sha="${1:-}"
if [[ "$candidate_sha" == "--help" || "$candidate_sha" == "-h" ]]; then
  usage
  exit 0
fi
if [[ ! "$candidate_sha" =~ ^[0-9a-f]{40}$ ]]; then
  usage >&2
  echo "Candidate revision must be an exact lowercase 40-character commit SHA" >&2
  exit 2
fi

run_native="${LLRT_RUN_NATIVE:-1}"
run_android="${LLRT_RUN_ANDROID:-0}"
if [[ "$run_native" != "0" && "$run_native" != "1" ]] ||
   [[ "$run_android" != "0" && "$run_android" != "1" ]]; then
  echo "LLRT_RUN_NATIVE and LLRT_RUN_ANDROID must be 0 or 1" >&2
  exit 2
fi
if [[ "$run_native" == "0" && "$run_android" == "0" ]]; then
  echo "At least one qualification lane must be enabled" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repo_root"

./scripts/verify-llama-cpp-pin.sh

submodule_path="third_party/llama.cpp"
baseline_sha="$(git -C "$submodule_path" rev-parse HEAD)"
if [[ "$candidate_sha" == "$baseline_sha" ]]; then
  echo "Candidate equals the current production pin: $candidate_sha" >&2
  exit 2
fi

native_build_dir=""
if [[ "$run_native" == "1" ]]; then
  native_build_dir="$(mktemp -d "${TMPDIR:-/tmp}/llrt-0-native.XXXXXX")"
fi
jni_source="backends/llama-cpp/src/main/cpp/llama_jni.cpp"
jni_backup="$(mktemp "${TMPDIR:-/tmp}/llrt-5-jni.XXXXXX")"
cp "$jni_source" "$jni_backup"
restored=false
restore_baseline() {
  if [[ "$restored" == "false" ]]; then
    cp "$jni_backup" "$jni_source" || true
    git -C "$submodule_path" checkout --quiet --detach "$baseline_sha" || true
    restored=true
  fi
  rm -f "$jni_backup"
  if [[ -n "$native_build_dir" ]]; then
    rm -rf "$native_build_dir"
  fi
}
trap restore_baseline EXIT INT TERM

echo "LLRT-0 baseline:  $baseline_sha"
echo "LLRT-0 candidate: $candidate_sha"
echo "LLRT-0 lanes: native=$run_native android=$run_android"

git -C "$submodule_path" fetch --quiet --depth=1 origin "$candidate_sha"
git -C "$submodule_path" checkout --quiet --detach "$candidate_sha"
actual_sha="$(git -C "$submodule_path" rev-parse HEAD)"
if [[ "$actual_sha" != "$candidate_sha" ]]; then
  echo "Candidate checkout mismatch: expected $candidate_sha, found $actual_sha" >&2
  exit 1
fi
if [[ -n "$(git -C "$submodule_path" status --porcelain)" ]]; then
  echo "Candidate llama.cpp checkout is dirty" >&2
  exit 1
fi

candidate_header="$submodule_path/include/llama.h"
if grep -q 'enum llama_load_mode' "$candidate_header" && ! grep -q 'bool use_mmap' "$candidate_header"; then
  echo "LLRT-5: candidate exposes load_mode without legacy use_mmap/use_mlock; applying temporary compatibility overlay"
  python3 - "$jni_source" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
include_anchor = '#include "llama.h"\n'
include_replacement = '#include "llama.h"\n#include "model_load_params_compat.h"\n'
assignment_anchor = '''    params.n_gpu_layers = n_gpu_layers;\n    params.use_mmap = use_mmap == JNI_TRUE;\n    params.use_mlock = use_mlock == JNI_TRUE;\n'''
assignment_replacement = '''    params.n_gpu_layers = n_gpu_layers;\n    local_llm::apply_legacy_model_load_policy(\n        params,\n        use_mmap == JNI_TRUE,\n        use_mlock == JNI_TRUE\n    );\n'''
if source.count(include_anchor) != 1:
    raise SystemExit("LLRT-5 overlay expected exactly one llama.h include anchor")
if source.count(assignment_anchor) != 1:
    raise SystemExit("LLRT-5 overlay expected exactly one legacy model-load assignment block")
source = source.replace(include_anchor, include_replacement, 1)
source = source.replace(assignment_anchor, assignment_replacement, 1)
path.write_text(source)
PY
elif grep -q 'bool use_mmap' "$candidate_header"; then
  echo "LLRT-5: candidate still exposes legacy mmap/mlock fields; no source overlay required"
else
  echo "LLRT-5: candidate load API is unknown; refusing to infer model-load semantics" >&2
  exit 1
fi

if [[ "$run_native" == "1" ]]; then
  if command -v nproc >/dev/null 2>&1; then
    build_jobs="$(nproc)"
  elif command -v sysctl >/dev/null 2>&1; then
    build_jobs="$(sysctl -n hw.ncpu 2>/dev/null || printf '2')"
  else
    build_jobs=2
  fi
  if [[ ! "$build_jobs" =~ ^[0-9]+$ ]] || ((build_jobs < 1)); then
    build_jobs=2
  fi
  cmake \
    -S backends/llama-cpp/src/test-native \
    -B "$native_build_dir" \
    -DCMAKE_BUILD_TYPE=Release
  cmake --build "$native_build_dir" --parallel "$build_jobs"
  ctest --test-dir "$native_build_dir" --output-on-failure
fi

if [[ "$run_android" == "1" ]]; then
  chmod +x gradlew
  ./gradlew \
    --no-configuration-cache \
    --stacktrace \
    :backends:llama-cpp:assembleDebug \
    :backends:llama-cpp:testDebugUnitTest \
    :apps:device-test-runner:assembleDebug
fi

restore_baseline
./scripts/verify-llama-cpp-pin.sh
trap - EXIT INT TERM

echo "LLRT candidate qualification passed: $candidate_sha"
