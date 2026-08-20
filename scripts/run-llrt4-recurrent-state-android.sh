#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
OUTPUT_DIR="$ROOT_DIR/build/llrt4"
NDK_VERSION="28.2.13676358"
ANDROID_PLATFORM="android-26"

usage() {
    cat <<'EOF'
Usage: bash scripts/run-llrt4-recurrent-state-android.sh --model /path/model.gguf --tier 0.8b|2b [options]

Cross-compiles the LLRT-4 recurrent-state correctness probe for arm64-v8a, runs it
on one physical Android device against the exact curated Qwen3.5 artifact, and writes
privacy-safe machine-readable evidence. A KEEP_DISABLED verdict is valid evidence;
only probe/build/device failures make this script fail.

Options:
  --device SERIAL       ADB serial. Optional when exactly one device is online.
  --output-dir PATH     Evidence/build root (default: build/llrt4).
  --help                Show this help.

Environment:
  ANDROID_NDK_HOME      Preferred Android NDK root.
  ANDROID_NDK_ROOT      Fallback Android NDK root.
  ANDROID_SDK_ROOT      Used to resolve ndk/28.2.13676358 when NDK vars are absent.
  ANDROID_HOME          Same fallback as ANDROID_SDK_ROOT.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model) MODEL="${2:-}"; shift 2 ;;
        --tier) TIER="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [[ ! -f "$MODEL" || ! -r "$MODEL" ]]; then
    echo "--model must point to a readable GGUF file" >&2
    exit 2
fi
if [[ "$TIER" != "0.8b" && "$TIER" != "2b" ]]; then
    echo "--tier must be 0.8b or 2b" >&2
    exit 2
fi
for command_name in "$ADB_BIN" cmake python3; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "$command_name is required" >&2
        exit 2
    fi
done

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    else
        echo "A SHA-256 utility is required" >&2
        exit 2
    fi
}

case "$TIER" in
    0.8b) EXPECTED_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517" ;;
    2b) EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223" ;;
esac
ACTUAL_SHA="$(sha256_file "$MODEL" | tr '[:upper:]' '[:lower:]')"
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "$TIER model does not match the curated Qwen3.5 Q4_K_M artifact" >&2
    exit 2
fi

resolve_ndk() {
    for candidate in \
        "${ANDROID_NDK_HOME:-}" \
        "${ANDROID_NDK_ROOT:-}" \
        "${ANDROID_SDK_ROOT:-}/ndk/$NDK_VERSION" \
        "${ANDROID_HOME:-}/ndk/$NDK_VERSION" \
        "$HOME/Library/Android/sdk/ndk/$NDK_VERSION"; do
        if [[ -n "$candidate" && -f "$candidate/build/cmake/android.toolchain.cmake" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

NDK_ROOT="$(resolve_ndk || true)"
if [[ -z "$NDK_ROOT" ]]; then
    echo "Unable to locate Android NDK $NDK_VERSION; set ANDROID_NDK_HOME" >&2
    exit 2
fi

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then
    ADB_CMD+=("-s" "$DEVICE")
fi
"${ADB_CMD[@]}" get-state >/dev/null
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$DEVICE_ABI" != arm64-v8a* ]]; then
    echo "LLRT-4 physical evidence requires arm64-v8a; device reports $DEVICE_ABI" >&2
    exit 2
fi
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"

cd "$ROOT_DIR"
./scripts/verify-llama-cpp-pin.sh
HARNESS_COMMIT="$(git rev-parse HEAD)"
BACKEND_REVISION="$(git -C third_party/llama.cpp rev-parse HEAD)"
BUILD_DIR="$OUTPUT_DIR/build-$TIER"
mkdir -p "$BUILD_DIR" "$OUTPUT_DIR/$TIER"

cmake \
    -S backends/llama-cpp/src/test-native \
    -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DANDROID_STL=c++_static
cmake --build "$BUILD_DIR" --target recurrent_state_reuse_probe --parallel

PROBE_BIN="$(find "$BUILD_DIR" -type f -name recurrent_state_reuse_probe -perm -u+x | head -n 1)"
if [[ -z "$PROBE_BIN" ]]; then
    PROBE_BIN="$(find "$BUILD_DIR" -type f -name recurrent_state_reuse_probe | head -n 1)"
fi
[[ -n "$PROBE_BIN" && -f "$PROBE_BIN" ]] || { echo "Unable to locate Android LLRT-4 probe executable" >&2; exit 1; }

REMOTE_DIR="/data/local/tmp/local-llm-llrt4-${TIER}-$$"
REMOTE_MODEL="$REMOTE_DIR/model.gguf"
REMOTE_PROBE="$REMOTE_DIR/recurrent_state_reuse_probe"
cleanup_remote() {
    "${ADB_CMD[@]}" shell rm -rf "$REMOTE_DIR" >/dev/null 2>&1 || true
}
trap cleanup_remote EXIT INT TERM

"${ADB_CMD[@]}" shell mkdir -p "$REMOTE_DIR"
"${ADB_CMD[@]}" push "$PROBE_BIN" "$REMOTE_PROBE" >/dev/null
"${ADB_CMD[@]}" push "$MODEL" "$REMOTE_MODEL" >/dev/null
"${ADB_CMD[@]}" shell chmod 0755 "$REMOTE_PROBE"

set +e
PROBE_OUTPUT="$("${ADB_CMD[@]}" shell "LOCAL_LLM_LLRT4_MODEL=$REMOTE_MODEL $REMOTE_PROBE" 2>&1)"
PROBE_STATUS=$?
set -e
PROBE_OUTPUT="$(printf '%s' "$PROBE_OUTPUT" | tr -d '\r')"
printf '%s\n' "$PROBE_OUTPUT"
if ((PROBE_STATUS != 0)); then
    echo "LLRT-4 Android probe execution failed with status $PROBE_STATUS" >&2
    exit 1
fi

VERDICT="$(printf '%s\n' "$PROBE_OUTPUT" | sed -n 's/^LLRT4_NATIVE_VERDICT //p' | tail -n 1)"
if [[ "$VERDICT" != "NATIVE_STATE_COMPATIBLE" && "$VERDICT" != "KEEP_DISABLED" ]]; then
    echo "LLRT-4 probe did not emit a recognized native verdict" >&2
    exit 1
fi

RAW_OUTPUT="$OUTPUT_DIR/$TIER/llrt4-native-probe.txt"
EVIDENCE_JSON="$OUTPUT_DIR/$TIER/llrt4-native-evidence.json"
printf '%s\n' "$PROBE_OUTPUT" > "$RAW_OUTPUT"
python3 - "$EVIDENCE_JSON" "$RAW_OUTPUT" "$TIER" "$EXPECTED_SHA" "$BACKEND_REVISION" "$HARNESS_COMMIT" \
    "$DEVICE_MODEL" "$DEVICE_RELEASE" "$DEVICE_SDK" "$DEVICE_ABI" "$VERDICT" <<'PY'
import json
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
raw_path = Path(sys.argv[2])
checks = [line for line in raw_path.read_text().splitlines() if line.startswith("LLRT4 ")]
evidence = {
    "schemaVersion": 1,
    "scope": "llrt4-native-recurrent-state",
    "modelTier": sys.argv[3],
    "modelDigest": sys.argv[4],
    "architecture": "qwen35",
    "quantization": "Q4_K_M",
    "backendRevision": sys.argv[5],
    "harnessCommit": sys.argv[6],
    "deviceModel": sys.argv[7],
    "androidRelease": sys.argv[8],
    "sdkInt": int(sys.argv[9]),
    "abi": sys.argv[10],
    "nativeVerdict": sys.argv[11],
    "checks": checks,
    "productionReuseEnabled": False,
}
output_path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
PY

cleanup_remote
trap - EXIT INT TERM

echo "LLRT-4 physical native evidence written to:"
echo "  $RAW_OUTPUT"
echo "  $EVIDENCE_JSON"
echo "Native verdict: $VERDICT"
echo "Production recurrent/prefix reuse remains disabled until runtime-level lifecycle evidence is reviewed."
