#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB_BIN="${ADB:-adb}"
MODEL_08B=""
MODEL_2B=""
DEVICE=""
REPETITIONS=3
THINKING_SCOPE="BOTH"
OUTPUT_DIR="$ROOT_DIR/build/qwen35-tuning"

usage() {
    cat <<'EOF'
Usage: bash scripts/run-qwen35-tuning-matrix.sh --model-08b /path/model.gguf --model-2b /path/model.gguf [options]

Options:
  --device SERIAL             ADB serial. Optional when exactly one device is online.
  --repetitions N             Warm samples per tuning case (minimum/default: 3).
  --thinking-mode MODE        BOTH, DISABLED, or ENABLED (default: BOTH).
  --output-dir PATH           Privacy-safe local evidence directory.
  --help                      Show this help.

The full default matrix measures both curated Qwen3.5 Q4_K_M reference artifacts,
all approved 1K/2K/4K/8K contexts, 2/4 threads, 64/32 and 128/64 batch/ubatch,
and thinking disabled/enabled. Each case emits one cold sample followed by N warm
samples inside the same runtime. Evidence is never promoted to MEASURED automatically.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model-08b) MODEL_08B="${2:-}"; shift 2 ;;
        --model-2b) MODEL_2B="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --repetitions) REPETITIONS="${2:-}"; shift 2 ;;
        --thinking-mode) THINKING_SCOPE="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

for model in "$MODEL_08B" "$MODEL_2B"; do
    if [[ ! -f "$model" || ! -r "$model" ]]; then
        echo "Both --model-08b and --model-2b must point to readable files" >&2
        exit 2
    fi
done
if [[ ! "$REPETITIONS" =~ ^[0-9]+$ ]] || ((REPETITIONS < 3)); then
    echo "--repetitions must be an integer >= 3" >&2
    exit 2
fi
if [[ "$THINKING_SCOPE" != "BOTH" && "$THINKING_SCOPE" != "DISABLED" && "$THINKING_SCOPE" != "ENABLED" ]]; then
    echo "--thinking-mode must be BOTH, DISABLED, or ENABLED" >&2
    exit 2
fi
if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "adb is required" >&2
    exit 2
fi

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then
    ADB_CMD+=("-s" "$DEVICE")
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

SHA_08B="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
SHA_2B="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
if [[ "$(sha256_file "$MODEL_08B" | tr '[:upper:]' '[:lower:]')" != "$SHA_08B" ]]; then
    echo "0.8B model does not match the curated Q4_K_M reference" >&2
    exit 2
fi
if [[ "$(sha256_file "$MODEL_2B" | tr '[:upper:]' '[:lower:]')" != "$SHA_2B" ]]; then
    echo "2B model does not match the curated Q4_K_M reference" >&2
    exit 2
fi

"${ADB_CMD[@]}" get-state >/dev/null
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$DEVICE_ABI" != arm64-v8a* ]]; then
    echo "Qwen3.5 tuning requires arm64-v8a; device reports $DEVICE_ABI" >&2
    exit 2
fi

cd "$ROOT_DIR"
HARNESS_COMMIT="$(git rev-parse HEAD)"
./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest

APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || { echo "Unable to locate device-test APKs" >&2; exit 1; }
"${ADB_CMD[@]}" install -r -t "$APP_APK"
"${ADB_CMD[@]}" install -r -t "$TEST_APK"
"${ADB_CMD[@]}" shell run-as "$APP_ID" mkdir -p files/e2e

RUNNER="$(
    "${ADB_CMD[@]}" shell pm list instrumentation \
        | tr -d '\r' \
        | grep -F "(target=$APP_ID)" \
        | head -n 1 \
        | sed -E 's/^instrumentation:([^ ]+).*/\1/' \
        || true
)"
[[ -n "$RUNNER" ]] || { echo "Unable to discover AndroidJUnitRunner" >&2; exit 1; }

mkdir -p "$OUTPUT_DIR"
JSONL="$OUTPUT_DIR/qwen35-tuning-evidence.jsonl"
CSV="$OUTPUT_DIR/qwen35-tuning-summary.csv"
: > "$JSONL"

run_case() {
    tier="$1"
    expected_sha="$2"
    context="$3"
    threads="$4"
    batch="$5"
    ubatch="$6"
    thinking="$7"
    thinking_slug="$(printf '%s' "$thinking" | tr '[:upper:]' '[:lower:]')"
    case_id="${tier}-ctx${context}-t${threads}-bt${threads}-b${batch}-ub${ubatch}-${thinking_slug}"
    echo "Running $case_id: 1 cold + $REPETITIONS warm"
    set +e
    output="$(
        "${ADB_CMD[@]}" shell am instrument -w -r \
            -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#recordsColdAndWarmEvidence \
            -e modelRelativePath files/e2e/model.gguf \
            -e modelSha256 "$expected_sha" \
            -e modelTier "$tier" \
            -e contextSize "$context" \
            -e batchSize "$batch" \
            -e microBatchSize "$ubatch" \
            -e cpuThreads "$threads" \
            -e batchThreads "$threads" \
            -e maxOutputTokens 64 \
            -e warmRepetitions "$REPETITIONS" \
            -e thinkingMode "$thinking" \
            -e tuningCaseId "$case_id" \
            -e harnessCommit "$HARNESS_COMMIT" \
            "$RUNNER" 2>&1
    )"
    status=$?
    set -e
    output="$(printf '%s' "$output" | tr -d '\r')"
    printf '%s\n' "$output"
    if ((status != 0)) || printf '%s\n' "$output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        echo "Tuning case failed: $case_id" >&2
        exit 1
    fi
    evidence_lines="$(printf '%s\n' "$output" | sed -n 's/^.*LOCAL_LLM_TUNING_JSON //p')"
    evidence_count="$(printf '%s\n' "$evidence_lines" | sed '/^$/d' | wc -l | tr -d ' ')"
    expected_count=$((REPETITIONS + 1))
    if [[ "$evidence_count" -ne "$expected_count" ]]; then
        echo "Expected $expected_count evidence records for $case_id, got $evidence_count" >&2
        exit 1
    fi
    printf '%s\n' "$evidence_lines" >> "$JSONL"
}

run_tier() {
    tier="$1"
    model_path="$2"
    expected_sha="$3"
    echo "Pushing curated Qwen3.5 $tier artifact once"
    "${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/model.gguf bs=1048576 < "$model_path" >/dev/null
    for context in 1024 2048 4096 8192; do
        for threads in 2 4; do
            for pair in "64 32" "128 64"; do
                set -- $pair
                batch="$1"
                ubatch="$2"
                if [[ "$THINKING_SCOPE" == "BOTH" || "$THINKING_SCOPE" == "DISABLED" ]]; then
                    run_case "$tier" "$expected_sha" "$context" "$threads" "$batch" "$ubatch" DISABLED
                fi
                if [[ "$THINKING_SCOPE" == "BOTH" || "$THINKING_SCOPE" == "ENABLED" ]]; then
                    run_case "$tier" "$expected_sha" "$context" "$threads" "$batch" "$ubatch" ENABLED
                fi
            done
        done
    done
}

run_tier 0.8b "$MODEL_08B" "$SHA_08B"
run_tier 2b "$MODEL_2B" "$SHA_2B"
"${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f files/e2e/model.gguf || true

python3 scripts/summarize-qwen35-tuning.py "$JSONL" "$CSV"
echo "Qwen3.5 tuning evidence written to:"
echo "  $JSONL"
echo "  $CSV"
