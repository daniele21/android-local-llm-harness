#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
CONTEXT=2048
BASE_THREADS=4
BASE_BATCH_THREADS=4
BASE_BATCH=128
BASE_UBATCH=64
REPETITIONS=8
MAX_OUTPUT_TOKENS=128
THINKING_MODE="DISABLED"
OUTPUT_DIR="$ROOT_DIR/build/llama-cpp-cpu-deltas"

usage() {
    cat <<'EOF'
Usage: bash scripts/run-llama-cpp-cpu-deltas.sh --model /path/model.gguf --tier 0.8b|2b [options]

Runs a bounded one-factor CPU delta set against one explicit baseline. It does not
replace the full Q35-6 tuning matrix and never promotes a runtime profile automatically.

Options:
  --device SERIAL              ADB serial. Optional when exactly one device is online.
  --context N                  Approved Qwen3.5 context tier (default: 2048).
  --threads N                  Baseline generation threads (default: 4).
  --batch-threads N            Baseline batch/prefill threads (default: 4).
  --batch N                    Baseline batch size (default: 128).
  --ubatch N                   Baseline micro-batch size (default: 64).
  --repetitions N              Warm samples per case, >= 5 (default: 8).
  --max-output-tokens N        Output budget used for sustained samples (default: 128).
  --thinking-mode MODE         DISABLED or ENABLED (default: DISABLED).
  --output-dir PATH            Privacy-safe local evidence directory.
  --help                       Show this help.

Default bounded cases:
  baseline                     exact supplied baseline
  generation-threads-2         only generation threads changed to 2
  batch-threads-2              only batch/prefill threads changed to 2
  batch64-ubatch32             only batch/ubatch changed to 64/32

Cases identical to the supplied baseline are de-duplicated. Each case emits one cold
sample plus N warm samples. The summary reports first-to-last warm drift but applies no
new pass/fail threshold; policy remains owned by Q35-6/Q35-7 evidence review.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model) MODEL="${2:-}"; shift 2 ;;
        --tier) TIER="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --context) CONTEXT="${2:-}"; shift 2 ;;
        --threads) BASE_THREADS="${2:-}"; shift 2 ;;
        --batch-threads) BASE_BATCH_THREADS="${2:-}"; shift 2 ;;
        --batch) BASE_BATCH="${2:-}"; shift 2 ;;
        --ubatch) BASE_UBATCH="${2:-}"; shift 2 ;;
        --repetitions) REPETITIONS="${2:-}"; shift 2 ;;
        --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
        --thinking-mode) THINKING_MODE="${2:-}"; shift 2 ;;
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
if [[ "$THINKING_MODE" != "DISABLED" && "$THINKING_MODE" != "ENABLED" ]]; then
    echo "--thinking-mode must be DISABLED or ENABLED" >&2
    exit 2
fi
if [[ "$CONTEXT" != "1024" && "$CONTEXT" != "2048" && "$CONTEXT" != "4096" && "$CONTEXT" != "8192" ]]; then
    echo "--context must be one of 1024, 2048, 4096, 8192" >&2
    exit 2
fi
for value_name in BASE_THREADS BASE_BATCH_THREADS BASE_BATCH BASE_UBATCH MAX_OUTPUT_TOKENS; do
    value="${!value_name}"
    if [[ ! "$value" =~ ^[0-9]+$ ]] || ((value < 1)); then
        echo "$value_name must be a positive integer" >&2
        exit 2
    fi
done
if [[ ! "$REPETITIONS" =~ ^[0-9]+$ ]] || ((REPETITIONS < 5)); then
    echo "--repetitions must be an integer >= 5 for sustained delta evidence" >&2
    exit 2
fi
if ((BASE_UBATCH > BASE_BATCH)); then
    echo "--ubatch must be <= --batch" >&2
    exit 2
fi
if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "adb is required" >&2
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

case "$TIER" in
    0.8b) EXPECTED_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517" ;;
    2b) EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223" ;;
esac
ACTUAL_SHA="$(sha256_file "$MODEL" | tr '[:upper:]' '[:lower:]')"
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "$TIER model does not match the curated Q4_K_M reference" >&2
    exit 2
fi

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then
    ADB_CMD+=("-s" "$DEVICE")
fi
"${ADB_CMD[@]}" get-state >/dev/null
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$DEVICE_ABI" != arm64-v8a* ]]; then
    echo "CPU delta evidence requires arm64-v8a; device reports $DEVICE_ABI" >&2
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
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/model.gguf bs=1048576 < "$MODEL" >/dev/null
trap '"${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f files/e2e/model.gguf >/dev/null 2>&1 || true' EXIT

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
JSONL="$OUTPUT_DIR/llama-cpp-cpu-deltas-evidence.jsonl"
CSV="$OUTPUT_DIR/llama-cpp-cpu-deltas-summary.csv"
: > "$JSONL"

# Keep this runner compatible with the system Bash 3.2 shipped on macOS.
# Do not use associative arrays or Bash 4+ case-modifying parameter expansion.
SEEN_SIGNATURES=()
SEEN_LABELS=()
THINKING_MODE_SLUG="$(printf '%s' "$THINKING_MODE" | tr '[:upper:]' '[:lower:]')"

seen_case_index() {
    wanted_signature="$1"
    index=0
    while ((index < ${#SEEN_SIGNATURES[@]})); do
        if [[ "${SEEN_SIGNATURES[$index]}" == "$wanted_signature" ]]; then
            printf '%s\n' "$index"
            return 0
        fi
        index=$((index + 1))
    done
    return 1
}

run_case() {
    label="$1"
    threads="$2"
    batch_threads="$3"
    batch="$4"
    ubatch="$5"
    signature="$threads:$batch_threads:$batch:$ubatch"
    duplicate_index=""
    if duplicate_index="$(seen_case_index "$signature")"; then
        echo "Skipping duplicate $label; identical to ${SEEN_LABELS[$duplicate_index]}"
        return
    fi
    SEEN_SIGNATURES+=("$signature")
    SEEN_LABELS+=("$label")

    case_id="llrt3-${TIER}-ctx${CONTEXT}-${label}-t${threads}-bt${batch_threads}-b${batch}-ub${ubatch}-${THINKING_MODE_SLUG}"
    echo "Running $case_id: 1 cold + $REPETITIONS warm"
    set +e
    output="$(
        "${ADB_CMD[@]}" shell am instrument -w -r \
            -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#recordsColdAndWarmEvidence \
            -e modelRelativePath files/e2e/model.gguf \
            -e modelSha256 "$EXPECTED_SHA" \
            -e modelTier "$TIER" \
            -e contextSize "$CONTEXT" \
            -e batchSize "$batch" \
            -e microBatchSize "$ubatch" \
            -e cpuThreads "$threads" \
            -e batchThreads "$batch_threads" \
            -e maxOutputTokens "$MAX_OUTPUT_TOKENS" \
            -e warmRepetitions "$REPETITIONS" \
            -e thinkingMode "$THINKING_MODE" \
            -e tuningCaseId "$case_id" \
            -e harnessCommit "$HARNESS_COMMIT" \
            "$RUNNER" 2>&1
    )"
    status=$?
    set -e
    output="$(printf '%s' "$output" | tr -d '\r')"
    printf '%s\n' "$output"
    if ((status != 0)) || printf '%s\n' "$output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        echo "CPU delta case failed: $case_id" >&2
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

run_case baseline "$BASE_THREADS" "$BASE_BATCH_THREADS" "$BASE_BATCH" "$BASE_UBATCH"
run_case generation-threads-2 2 "$BASE_BATCH_THREADS" "$BASE_BATCH" "$BASE_UBATCH"
run_case batch-threads-2 "$BASE_THREADS" 2 "$BASE_BATCH" "$BASE_UBATCH"
run_case batch64-ubatch32 "$BASE_THREADS" "$BASE_BATCH_THREADS" 64 32

python3 scripts/summarize-qwen35-tuning.py "$JSONL" "$CSV"
echo "Bounded llama.cpp CPU delta evidence written to:"
echo "  $JSONL"
echo "  $CSV"
echo "Review first-to-last warm drift together with median/p95, PSS, memory and thermal status; no threshold was auto-promoted."
