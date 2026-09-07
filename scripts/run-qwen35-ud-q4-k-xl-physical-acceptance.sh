#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
EXPECTED_2B_SHA="0af96165ea615bea39a04118d63f0b6d35908aea850ee4a51aa6151d851b8b35"
EXPECTED_4B_SHA="b252c5610a42ca82d20fe2a12813e9d069eed89292907e26c783eeb0bc961bc7"
QUANTIZATION="UD-Q4_K_XL"

MODEL_2B=""
MODEL_4B=""
DEVICE=""
OUTPUT_DIR="$ROOT_DIR/build/q35-ud-q4-k-xl-physical-acceptance"
THERMAL_START_MAX=1
TIMEOUT_SECONDS=1200
MEMORY_REPEAT_COUNT=3
MAX_PSS_GROWTH_KB=131072
CONTEXT_TOKENS=2048
BATCH_SIZE=128
MICRO_BATCH_SIZE=64
ADB_BIN="${ADB:-adb}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-qwen35-ud-q4-k-xl-physical-acceptance.sh \
    --model-2b /path/Qwen3.5-2B-UD-Q4_K_XL.gguf \
    --model-4b /path/Qwen3.5-4B-UD-Q4_K_XL.gguf \
    [--device SERIAL] [options]

Runs a provenance-gated physical Android acceptance lane for the exact Unsloth
Qwen3.5 2B and 4B UD-Q4_K_XL artifacts. Each model runs the real JNI/llama.cpp
path through generation, active cancellation and repeated load/generate/unload
memory checks. Evidence is device-bound and never promotes a runtime profile
automatically.

Options:
  --device SERIAL                ADB device serial.
  --output-dir PATH              Evidence root.
  --thermal-start-max N          Maximum thermal status before each model, 0..6 (default: 1).
  --timeout-seconds N            Instrumentation timeout (default: 1200).
  --memory-repeat N              Load/generate/unload repetitions, >=2 (default: 3).
  --max-pss-growth-kb N          Maximum repeated-cycle PSS growth (default: 131072).
  --context-tokens N             Context size (default: 2048).
  --batch-size N                 Batch size (default: 128).
  --micro-batch-size N           Micro-batch size (default: 64).
  --help                         Show this help.

This lane does not replace the separate LOW_MEMORY/cross-model-switch gate.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model-2b) MODEL_2B="${2:-}"; shift 2 ;;
        --model-4b) MODEL_4B="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
        --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
        --memory-repeat) MEMORY_REPEAT_COUNT="${2:-}"; shift 2 ;;
        --max-pss-growth-kb) MAX_PSS_GROWTH_KB="${2:-}"; shift 2 ;;
        --context-tokens) CONTEXT_TOKENS="${2:-}"; shift 2 ;;
        --batch-size) BATCH_SIZE="${2:-}"; shift 2 ;;
        --micro-batch-size) MICRO_BATCH_SIZE="${2:-}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

for pair in "2B:$MODEL_2B" "4B:$MODEL_4B"; do
    tier="${pair%%:*}"
    model="${pair#*:}"
    if [[ -z "$model" || ! -f "$model" || ! -r "$model" ]]; then
        echo "--model-${tier,,} must point to a readable GGUF file" >&2
        exit 2
    fi
done

for pair in \
    "thermal:$THERMAL_START_MAX" \
    "timeout:$TIMEOUT_SECONDS" \
    "memory-repeat:$MEMORY_REPEAT_COUNT" \
    "max-pss-growth-kb:$MAX_PSS_GROWTH_KB" \
    "context:$CONTEXT_TOKENS" \
    "batch:$BATCH_SIZE" \
    "micro-batch:$MICRO_BATCH_SIZE"; do
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
if (( TIMEOUT_SECONDS < 1 || MEMORY_REPEAT_COUNT < 2 || CONTEXT_TOKENS < 1 || BATCH_SIZE < 1 || MICRO_BATCH_SIZE < 1 )); then
    echo "timeout/context/batch sizes must be positive and --memory-repeat must be >=2" >&2
    exit 2
fi
if (( MICRO_BATCH_SIZE > BATCH_SIZE )); then
    echo "--micro-batch-size cannot exceed --batch-size" >&2
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
        echo "Tracked Harnex worktree must be clean $stage:" >&2
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
require_clean_tracked_worktree "before physical acceptance"
require_pinned_backend "before physical acceptance"
HARNEX_COMMIT="$(git rev-parse HEAD)"

ACTUAL_2B_SHA="$(sha256_file "$MODEL_2B" | tr '[:upper:]' '[:lower:]')"
ACTUAL_4B_SHA="$(sha256_file "$MODEL_4B" | tr '[:upper:]' '[:lower:]')"
[[ "$ACTUAL_2B_SHA" == "$EXPECTED_2B_SHA" ]] || {
    echo "2B model does not match the pinned Unsloth UD-Q4_K_XL identity" >&2
    exit 2
}
[[ "$ACTUAL_4B_SHA" == "$EXPECTED_4B_SHA" ]] || {
    echo "4B model does not match the pinned Unsloth UD-Q4_K_XL identity" >&2
    exit 2
}

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then
    ADB_CMD+=("-s" "$DEVICE")
fi
"${ADB_CMD[@]}" get-state >/dev/null

DEVICE_MANUFACTURER="$("${ADB_CMD[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
DEVICE_RAM_KB="$("${ADB_CMD[@]}" shell cat /proc/meminfo | tr -d '\r' | awk '/^MemTotal:/ {print $2; exit}')"
[[ "$DEVICE_ABI" == arm64-v8a* ]] || {
    echo "Physical acceptance requires arm64-v8a" >&2
    exit 2
}

RUN_DIR="$OUTPUT_DIR/$DEVICE_MODEL/$HARNEX_COMMIT"
mkdir -p "$RUN_DIR"

cleanup() {
    "${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f \
        files/e2e/qwen35-2b-ud-q4-k-xl.gguf \
        files/e2e/qwen35-4b-ud-q4-k-xl.gguf >/dev/null 2>&1 || true
    require_clean_tracked_worktree "after physical acceptance"
    require_pinned_backend "after physical acceptance"
}
trap cleanup EXIT

./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || {
    echo "Unable to locate device-test APKs" >&2
    exit 1
}

"${ADB_CMD[@]}" install -r -t "$APP_APK"
"${ADB_CMD[@]}" install -r -t "$TEST_APK"
"${ADB_CMD[@]}" shell run-as "$APP_ID" mkdir -p files/e2e
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/qwen35-2b-ud-q4-k-xl.gguf bs=1048576 < "$MODEL_2B" >/dev/null
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/qwen35-4b-ud-q4-k-xl.gguf bs=1048576 < "$MODEL_4B" >/dev/null

RUNNER="$(
    "${ADB_CMD[@]}" shell pm list instrumentation \
        | tr -d '\r' \
        | grep -F "(target=$APP_ID)" \
        | head -n 1 \
        | sed -E 's/^instrumentation:([^ ]+).*/\1/' \
        || true
)"
[[ -n "$RUNNER" ]] || {
    echo "Unable to discover AndroidJUnitRunner" >&2
    exit 1
}

read_thermal_status() {
    set +e
    output="$("${ADB_CMD[@]}" shell am instrument -w -r \
        -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#reportsThermalStatus \
        "$RUNNER" 2>&1)"
    status=$?
    set -e
    output="${output//$'\r'/}"
    (( status == 0 )) || {
        printf '%s\n' "$output" >&2
        return 1
    }
    value="$(printf '%s\n' "$output" | sed -n 's/^.*LOCAL_LLM_THERMAL_STATUS //p' | tail -n 1)"
    [[ "$value" =~ ^[0-9]+$ ]] || {
        echo "Unable to parse thermal status" >&2
        return 1
    }
    printf '%s\n' "$value"
}

wait_for_thermal_gate() {
    while true; do
        thermal="$(read_thermal_status)"
        if (( thermal <= THERMAL_START_MAX )); then
            printf '%s\n' "$thermal"
            return 0
        fi
        echo "Thermal status=$thermal; cooling before the next serialized suite" >&2
        sleep 30
    done
}

run_model_suite() {
    tier="$1"
    relative_path="$2"
    sha="$3"
    threads="$4"
    log="$RUN_DIR/${tier}-e2e.log"
    thermal_file="$RUN_DIR/${tier}-thermal.txt"

    thermal_before="$(wait_for_thermal_gate)"
    echo "Thermal gate satisfied for $tier: status=$thermal_before <= $THERMAL_START_MAX"

    set +e
    output="$("${ADB_CMD[@]}" shell am instrument -w -r \
        -e class io.github.daniele21.localllm.devicetest.LocalLlmDeviceE2eTest \
        -e modelRelativePath "$relative_path" \
        -e modelSha256 "$sha" \
        -e modelArchitecture qwen35 \
        -e modelQuantization "$QUANTIZATION" \
        -e contextSize "$CONTEXT_TOKENS" \
        -e batchSize "$BATCH_SIZE" \
        -e microBatchSize "$MICRO_BATCH_SIZE" \
        -e cpuThreads "$threads" \
        -e cancellationEnabled true \
        -e memoryRepeatCount "$MEMORY_REPEAT_COUNT" \
        -e maxPssGrowthKb "$MAX_PSS_GROWTH_KB" \
        -e timeoutSeconds "$TIMEOUT_SECONDS" \
        "$RUNNER" 2>&1)"
    status=$?
    set -e

    output="${output//$'\r'/}"
    printf '%s\n' "$output" | tee "$log"

    if (( status != 0 )) || printf '%s\n' "$output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        echo "$tier physical acceptance failed" >&2
        exit 1
    fi
    printf '%s\n' "$output" | grep -Eq '^OK \(' || {
        echo "$tier missing JUnit success marker" >&2
        exit 1
    }
    printf '%s\n' "$output" | grep -Eq '^INSTRUMENTATION_CODE: -1$' || {
        echo "$tier missing instrumentation success marker" >&2
        exit 1
    }
    grep -Fq 'LOCAL_LLM_E2E generation ' "$log" || {
        echo "$tier missing generation evidence" >&2
        exit 1
    }
    grep -Fq 'LOCAL_LLM_E2E cancellation terminal=cancelled' "$log" || {
        echo "$tier missing cancellation evidence" >&2
        exit 1
    }
    grep -Fq 'LOCAL_LLM_E2E memory pssSamplesKb=' "$log" || {
        echo "$tier missing repeated-memory evidence" >&2
        exit 1
    }

    thermal_after="$(read_thermal_status)"
    {
        echo "before=$thermal_before"
        echo "after=$thermal_after"
    } > "$thermal_file"
}

run_model_suite "2b" files/e2e/qwen35-2b-ud-q4-k-xl.gguf "$ACTUAL_2B_SHA" 4
run_model_suite "4b" files/e2e/qwen35-4b-ud-q4-k-xl.gguf "$ACTUAL_4B_SHA" 4

MANIFEST="$RUN_DIR/manifest.json"
python3 - \
    "$MANIFEST" \
    "$HARNEX_COMMIT" \
    "$BACKEND_REVISION" \
    "$DEVICE_MANUFACTURER" \
    "$DEVICE_MODEL" \
    "$DEVICE_RELEASE" \
    "$DEVICE_SDK" \
    "$DEVICE_ABI" \
    "$DEVICE_RAM_KB" \
    "$ACTUAL_2B_SHA" \
    "$ACTUAL_4B_SHA" \
    "$QUANTIZATION" \
    "$THERMAL_START_MAX" \
    "$MEMORY_REPEAT_COUNT" \
    "$MAX_PSS_GROWTH_KB" \
    "$TIMEOUT_SECONDS" \
    "$CONTEXT_TOKENS" \
    "$BATCH_SIZE" \
    "$MICRO_BATCH_SIZE" \
    "$RUN_DIR" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
run_dir = Path(sys.argv[20])

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def thermal(path: Path) -> dict[str, int]:
    values: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, value = line.split("=", 1)
        values[key] = int(value)
    return values

payload = {
    "schemaVersion": 1,
    "evidenceType": "Q35_UD_Q4_K_XL_PHYSICAL_ACCEPTANCE",
    "harnexCommit": sys.argv[2],
    "backendRevision": sys.argv[3],
    "deviceManufacturer": sys.argv[4],
    "deviceModel": sys.argv[5],
    "androidRelease": sys.argv[6],
    "sdkInt": int(sys.argv[7]),
    "abi": sys.argv[8],
    "deviceRamKb": int(sys.argv[9]),
    "models": {
        "2b": {
            "digest": sys.argv[10],
            "quantization": sys.argv[12],
            "threads": 4,
            "logSha256": digest(run_dir / "2b-e2e.log"),
            "thermal": thermal(run_dir / "2b-thermal.txt"),
        },
        "4b": {
            "digest": sys.argv[11],
            "quantization": sys.argv[12],
            "threads": 4,
            "logSha256": digest(run_dir / "4b-e2e.log"),
            "thermal": thermal(run_dir / "4b-thermal.txt"),
        },
    },
    "thermalStartMax": int(sys.argv[13]),
    "memoryRepeatCount": int(sys.argv[14]),
    "maxPssGrowthKb": int(sys.argv[15]),
    "timeoutSeconds": int(sys.argv[16]),
    "contextTokens": int(sys.argv[17]),
    "batchSize": int(sys.argv[18]),
    "microBatchSize": int(sys.argv[19]),
    "checks": [
        "real_jni_generation",
        "active_generation_cancellation",
        "repeated_load_generate_unload_pss",
        "thermal_before_after",
    ],
    "scopeBoundary": {
        "lowMemoryPressure": False,
        "crossModelSwitch": False,
        "automaticProfilePromotion": False,
    },
}

manifest.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"Physical acceptance manifest: {manifest}")
print(f"Manifest SHA-256: {digest(manifest)}")
PY

require_clean_tracked_worktree "after evidence capture"
require_pinned_backend "after evidence capture"
echo "Q35 UD-Q4_K_XL 2B/4B physical acceptance completed; no runtime profile was promoted automatically."
