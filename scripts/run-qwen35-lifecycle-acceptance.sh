#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
EXPECTED_08B_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
EXPECTED_2B_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
MODEL_08B=""
MODEL_2B=""
DEVICE=""
OUTPUT_DIR="$ROOT_DIR/build/q35-lifecycle-acceptance"
THERMAL_START_MAX=1
TIMEOUT_SECONDS=900
MEMORY_REPEAT_COUNT=3
MAX_PSS_GROWTH_KB=131072
ADB_BIN="${ADB:-adb}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-qwen35-lifecycle-acceptance.sh \
    --model-08b /path/Qwen3.5-0.8B-Q4_K_M.gguf \
    --model-2b /path/Qwen3.5-2B-Q4_K_M.gguf \
    [--device SERIAL] [options]

Runs the Q35-6 lifecycle/memory acceptance wave on one physical arm64 Android device.
The runner is provenance-gated, never promotes a runtime profile automatically, and keeps
same-device execution serialized.

Options:
  --device SERIAL                ADB device serial.
  --output-dir PATH              Evidence root (default: build/q35-lifecycle-acceptance).
  --thermal-start-max N          Maximum thermal status before each suite, 0..6 (default: 1).
  --timeout-seconds N            Async operation timeout (default: 900).
  --memory-repeat N              Load/generate/unload repetitions, >=3 (default: 3).
  --max-pss-growth-kb N          Maximum repeated-cycle PSS growth (default: 131072).
  --help                         Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model-08b) MODEL_08B="${2:-}"; shift 2 ;;
        --model-2b) MODEL_2B="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
        --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
        --memory-repeat) MEMORY_REPEAT_COUNT="${2:-}"; shift 2 ;;
        --max-pss-growth-kb) MAX_PSS_GROWTH_KB="${2:-}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

for model in "$MODEL_08B" "$MODEL_2B"; do
    if [[ -z "$model" || ! -f "$model" || ! -r "$model" ]]; then
        echo "Both --model-08b and --model-2b must point to readable GGUF files" >&2
        exit 2
    fi
done
for pair in \
    "thermal:$THERMAL_START_MAX" \
    "timeout:$TIMEOUT_SECONDS" \
    "memory-repeat:$MEMORY_REPEAT_COUNT" \
    "max-pss-growth-kb:$MAX_PSS_GROWTH_KB"; do
    name="${pair%%:*}"
    value="${pair#*:}"
    if [[ ! "$value" =~ ^[0-9]+$ ]]; then
        echo "$name must be a non-negative integer" >&2
        exit 2
    fi
done
if (( THERMAL_START_MAX > 6 )); then
    echo "--thermal-start-max must be 0..6" >&2
    exit 2
fi
if (( TIMEOUT_SECONDS < 1 || MEMORY_REPEAT_COUNT < 3 )); then
    echo "--timeout-seconds must be positive and --memory-repeat must be >=3" >&2
    exit 2
fi
if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "adb is required" >&2
    exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required" >&2
    exit 2
fi

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

require_clean_tracked_worktree() {
    stage="$1"
    status="$(git status --short --untracked-files=no --ignore-submodules=dirty)"
    if [[ -n "$status" ]]; then
        echo "Tracked Harness worktree must be clean $stage:" >&2
        printf '%s\n' "$status" >&2
        exit 1
    fi
}

require_pinned_backend() {
    stage="$1"
    if [[ ! -e third_party/llama.cpp/.git ]]; then
        echo "llama.cpp submodule is not initialized $stage" >&2
        exit 1
    fi
    backend_status="$(git -C third_party/llama.cpp status --short)"
    if [[ -n "$backend_status" ]]; then
        echo "llama.cpp submodule must be clean $stage" >&2
        printf '%s\n' "$backend_status" >&2
        exit 1
    fi
    backend_head="$(git -C third_party/llama.cpp rev-parse HEAD)"
    if [[ "$backend_head" != "$BACKEND_REVISION" ]]; then
        echo "llama.cpp revision mismatch $stage: $backend_head" >&2
        exit 1
    fi
}

cd "$ROOT_DIR"
require_clean_tracked_worktree "before lifecycle evidence"
require_pinned_backend "before lifecycle evidence"
HARNESS_COMMIT="$(git rev-parse HEAD)"

ACTUAL_08B_SHA="$(sha256_file "$MODEL_08B" | tr '[:upper:]' '[:lower:]')"
ACTUAL_2B_SHA="$(sha256_file "$MODEL_2B" | tr '[:upper:]' '[:lower:]')"
[[ "$ACTUAL_08B_SHA" == "$EXPECTED_08B_SHA" ]] || { echo "0.8B model does not match curated Q4_K_M identity" >&2; exit 2; }
[[ "$ACTUAL_2B_SHA" == "$EXPECTED_2B_SHA" ]] || { echo "2B model does not match curated Q4_K_M identity" >&2; exit 2; }

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then ADB_CMD+=("-s" "$DEVICE"); fi
"${ADB_CMD[@]}" get-state >/dev/null
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$DEVICE_ABI" == arm64-v8a* ]] || { echo "Q35 lifecycle acceptance requires arm64-v8a" >&2; exit 2; }

RUN_DIR="$OUTPUT_DIR/$DEVICE_MODEL/$HARNESS_COMMIT"
mkdir -p "$RUN_DIR"

cleanup() {
    "${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f \
        files/e2e/qwen35-08b.gguf files/e2e/qwen35-2b.gguf >/dev/null 2>&1 || true
    require_clean_tracked_worktree "after lifecycle evidence"
    require_pinned_backend "after lifecycle evidence"
}
trap cleanup EXIT

./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || { echo "Unable to locate device-test APKs" >&2; exit 1; }
"${ADB_CMD[@]}" install -r -t "$APP_APK"
"${ADB_CMD[@]}" install -r -t "$TEST_APK"
"${ADB_CMD[@]}" shell run-as "$APP_ID" mkdir -p files/e2e
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/qwen35-08b.gguf bs=1048576 < "$MODEL_08B" >/dev/null
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/qwen35-2b.gguf bs=1048576 < "$MODEL_2B" >/dev/null

RUNNER="$(
    "${ADB_CMD[@]}" shell pm list instrumentation \
        | tr -d '\r' \
        | grep -F "(target=$APP_ID)" \
        | head -n 1 \
        | sed -E 's/^instrumentation:([^ ]+).*/\1/' \
        || true
)"
[[ -n "$RUNNER" ]] || { echo "Unable to discover AndroidJUnitRunner" >&2; exit 1; }

read_thermal_status() {
    set +e
    output="$("${ADB_CMD[@]}" shell am instrument -w -r \
        -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#reportsThermalStatus \
        "$RUNNER" 2>&1)"
    status=$?
    set -e
    output="${output//$'\r'/}"
    (( status == 0 )) || { printf '%s\n' "$output" >&2; return 1; }
    value="$(printf '%s\n' "$output" | sed -n 's/^.*LOCAL_LLM_THERMAL_STATUS //p' | tail -n 1)"
    [[ "$value" =~ ^[0-9]+$ ]] || { echo "Unable to parse thermal status" >&2; return 1; }
    printf '%s\n' "$value"
}

wait_for_thermal_gate() {
    while true; do
        thermal="$(read_thermal_status)"
        if (( thermal <= THERMAL_START_MAX )); then
            echo "Thermal gate satisfied: status=$thermal <= $THERMAL_START_MAX"
            return 0
        fi
        echo "Thermal status=$thermal; cooling before the next serialized suite"
        sleep 30
    done
}

run_instrumentation() {
    label="$1"
    shift
    log="$RUN_DIR/$label.log"
    wait_for_thermal_gate
    set +e
    output="$("${ADB_CMD[@]}" shell am instrument -w -r "$@" "$RUNNER" 2>&1)"
    status=$?
    set -e
    output="${output//$'\r'/}"
    printf '%s\n' "$output" | tee "$log"
    if (( status != 0 )) || printf '%s\n' "$output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        echo "$label failed" >&2
        exit 1
    fi
    printf '%s\n' "$output" | grep -Eq '^OK \(' || { echo "$label missing JUnit success marker" >&2; exit 1; }
    printf '%s\n' "$output" | grep -Eq '^INSTRUMENTATION_CODE: -1$' || { echo "$label missing instrumentation success marker" >&2; exit 1; }
}

run_e2e_tier() {
    label="$1"
    relative_path="$2"
    sha="$3"
    threads="$4"
    run_instrumentation "$label" \
        -e class io.github.daniele21.localllm.devicetest.LocalLlmDeviceE2eTest \
        -e modelRelativePath "$relative_path" \
        -e modelSha256 "$sha" \
        -e modelArchitecture qwen35 \
        -e modelQuantization Q4_K_M \
        -e contextSize 2048 \
        -e batchSize 128 \
        -e microBatchSize 64 \
        -e cpuThreads "$threads" \
        -e cancellationEnabled true \
        -e memoryRepeatCount "$MEMORY_REPEAT_COUNT" \
        -e maxPssGrowthKb "$MAX_PSS_GROWTH_KB" \
        -e timeoutSeconds "$TIMEOUT_SECONDS"
    grep -Fq 'LOCAL_LLM_E2E generation ' "$RUN_DIR/$label.log" || { echo "$label missing generation evidence" >&2; exit 1; }
    grep -Fq 'LOCAL_LLM_E2E cancellation terminal=cancelled' "$RUN_DIR/$label.log" || { echo "$label missing cancellation evidence" >&2; exit 1; }
    grep -Fq 'LOCAL_LLM_E2E memory pssSamplesKb=' "$RUN_DIR/$label.log" || { echo "$label missing repeated-memory evidence" >&2; exit 1; }
}

run_e2e_tier "08b-e2e" files/e2e/qwen35-08b.gguf "$ACTUAL_08B_SHA" 2
run_e2e_tier "2b-e2e" files/e2e/qwen35-2b.gguf "$ACTUAL_2B_SHA" 4

run_instrumentation "cross-tier-lifecycle" \
    -e class io.github.daniele21.localllm.devicetest.Qwen35LifecycleAcceptanceInstrumentedTest \
    -e primaryModelRelativePath files/e2e/qwen35-08b.gguf \
    -e primaryModelSha256 "$ACTUAL_08B_SHA" \
    -e primaryCpuThreads 2 \
    -e secondaryModelRelativePath files/e2e/qwen35-2b.gguf \
    -e secondaryModelSha256 "$ACTUAL_2B_SHA" \
    -e secondaryCpuThreads 4 \
    -e contextTokens 2048 \
    -e batchSize 128 \
    -e microBatchSize 64 \
    -e batchThreads 4 \
    -e switchOutputTokens 8 \
    -e lowMemoryOutputTokens 256 \
    -e timeoutSeconds "$TIMEOUT_SECONDS" \
    -e harnessCommit "$HARNESS_COMMIT" \
    -e backendRevision "$BACKEND_REVISION"

LIFECYCLE_JSONL="$RUN_DIR/lifecycle-evidence.jsonl"
sed -n 's/^.*LOCAL_LLM_Q35_LIFECYCLE_JSON //p' "$RUN_DIR/cross-tier-lifecycle.log" > "$LIFECYCLE_JSONL"
[[ "$(grep -c . "$LIFECYCLE_JSONL")" -eq 2 ]] || { echo "Expected exactly two structured lifecycle scenarios" >&2; exit 1; }

MANIFEST="$RUN_DIR/manifest.json"
python3 - "$MANIFEST" "$HARNESS_COMMIT" "$BACKEND_REVISION" "$DEVICE_MODEL" "$DEVICE_RELEASE" "$DEVICE_SDK" "$DEVICE_ABI" \
    "$ACTUAL_08B_SHA" "$ACTUAL_2B_SHA" "$THERMAL_START_MAX" "$RUN_DIR" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
run_dir = Path(sys.argv[11])

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

payload = {
    "schemaVersion": 1,
    "evidenceType": "Q35_LIFECYCLE_MEMORY_ACCEPTANCE",
    "harnessCommit": sys.argv[2],
    "backendRevision": sys.argv[3],
    "deviceModel": sys.argv[4],
    "androidRelease": sys.argv[5],
    "sdkInt": int(sys.argv[6]),
    "abi": sys.argv[7],
    "model08bDigest": sys.argv[8],
    "model2bDigest": sys.argv[9],
    "thermalStartMax": int(sys.argv[10]),
    "contextTokens": 2048,
    "batchSize": 128,
    "microBatchSize": 64,
    "switchOutputTokens": 8,
    "lowMemoryOutputTokens": 256,
    "cases": {
        "08bE2e": digest(run_dir / "08b-e2e.log"),
        "2bE2e": digest(run_dir / "2b-e2e.log"),
        "crossTierLifecycle": digest(run_dir / "cross-tier-lifecycle.log"),
        "structuredLifecycle": digest(run_dir / "lifecycle-evidence.jsonl"),
    },
    "automaticProfilePromotion": False,
}
manifest.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
print(f"Lifecycle acceptance manifest: {manifest}")
print(f"Manifest SHA-256: {digest(manifest)}")
PY

require_clean_tracked_worktree "after evidence capture"
require_pinned_backend "after evidence capture"
echo "Q35 lifecycle/memory acceptance completed; no runtime profile was promoted automatically."
