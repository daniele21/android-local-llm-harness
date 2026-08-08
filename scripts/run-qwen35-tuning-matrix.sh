#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB="${ADB:-adb}"
MODEL_PATH=""
TIER=""
REPEATS=3
THINKING_MODE="DISABLED"
OUTPUT_DIR="$ROOT_DIR/build/qwen35-tuning"

usage() {
    cat <<'EOF'
Usage: bash scripts/run-qwen35-tuning-matrix.sh --model /absolute/model.gguf --tier 0.8b|2b [options]

Options:
  --repeats N                 Samples per tuning case (default: 3).
  --thinking-mode MODE        DISABLED or ENABLED (default: DISABLED).
  --output-dir PATH           Local privacy-safe evidence output directory.
  --help                      Show this help.

Only the exact curated Qwen3.5 Q4_K_M certification candidates are accepted.
This harness records evidence; it never promotes CANDIDATE runtime profiles automatically.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model)
            MODEL_PATH="${2:-}"
            shift 2
            ;;
        --tier)
            TIER="${2:-}"
            shift 2
            ;;
        --repeats)
            REPEATS="${2:-}"
            shift 2
            ;;
        --thinking-mode)
            THINKING_MODE="${2:-}"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="${2:-}"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! -f "$MODEL_PATH" || ! -r "$MODEL_PATH" ]]; then
    echo "--model must be a readable file" >&2
    exit 2
fi
if [[ ! "$REPEATS" =~ ^[1-9][0-9]*$ ]]; then
    echo "--repeats must be positive" >&2
    exit 2
fi
if [[ "$THINKING_MODE" != "DISABLED" && "$THINKING_MODE" != "ENABLED" ]]; then
    echo "--thinking-mode must be DISABLED or ENABLED" >&2
    exit 2
fi
if ! command -v "$ADB" >/dev/null 2>&1; then
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
    0.8b)
        EXPECTED_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
        ;;
    2b)
        EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
        ;;
    *)
        echo "--tier must be 0.8b or 2b" >&2
        exit 2
        ;;
esac

ACTUAL_SHA="$(sha256_file "$MODEL_PATH" | tr '[:upper:]' '[:lower:]')"
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "Model SHA does not match the curated $TIER Q4_K_M reference" >&2
    exit 2
fi

"$ADB" get-state >/dev/null
DEVICE_ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$DEVICE_ABI" != arm64-v8a* ]]; then
    echo "Qwen3.5 tuning requires arm64-v8a; device reports $DEVICE_ABI" >&2
    exit 2
fi

cd "$ROOT_DIR"
./gradlew \
    :apps:device-test-runner:assembleDebug \
    :apps:device-test-runner:assembleDebugAndroidTest

APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
    echo "Unable to locate device-test APKs" >&2
    exit 1
fi

"$ADB" install -r -t "$APP_APK"
"$ADB" install -r -t "$TEST_APK"
"$ADB" shell run-as "$APP_ID" mkdir -p files/e2e
"$ADB" shell -T run-as "$APP_ID" dd of=files/e2e/model.gguf bs=1048576 < "$MODEL_PATH" >/dev/null

RUNNER="$(
    "$ADB" shell pm list instrumentation \
        | tr -d '\r' \
        | grep -F "(target=$APP_ID)" \
        | head -n 1 \
        | sed -E 's/^instrumentation:([^ ]+).*/\1/' \
        || true
)"
if [[ -z "$RUNNER" ]]; then
    echo "Unable to discover AndroidJUnitRunner" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
JSONL="$OUTPUT_DIR/qwen35-${TIER}-${THINKING_MODE,,}.jsonl"
CSV="$OUTPUT_DIR/qwen35-${TIER}-${THINKING_MODE,,}-summary.csv"
: > "$JSONL"

for context in 1024 2048 4096; do
    for threads in 2 4; do
        for pair in "64 32" "128 64"; do
            read -r batch ubatch <<< "$pair"
            case_id="${TIER}-ctx${context}-t${threads}-bt${threads}-b${batch}-ub${ubatch}-${THINKING_MODE,,}"
            for ((sample = 1; sample <= REPEATS; sample++)); do
                echo "Running $case_id sample $sample/$REPEATS"
                set +e
                output="$(
                    "$ADB" shell am instrument -w -r \
                        -e class io.github.daniele21.localllm.devicetest.LocalLlmDeviceE2eTest#qwen35TuningSampleRecordsColdAndWarmEvidence \
                        -e modelRelativePath files/e2e/model.gguf \
                        -e modelSha256 "$EXPECTED_SHA" \
                        -e modelArchitecture qwen35 \
                        -e modelQuantization Q4_K_M \
                        -e modelTier "$TIER" \
                        -e contextSize "$context" \
                        -e batchSize "$batch" \
                        -e microBatchSize "$ubatch" \
                        -e cpuThreads "$threads" \
                        -e batchThreads "$threads" \
                        -e maxOutputTokens 64 \
                        -e thinkingMode "$THINKING_MODE" \
                        -e tuningEnabled true \
                        -e tuningCaseId "$case_id" \
                        -e cancellationEnabled false \
                        -e memoryRepeatCount 0 \
                        "$RUNNER" 2>&1
                )"
                status=$?
                set -e
                output="${output//$'\r'/}"
                printf '%s\n' "$output"
                if ((status != 0)); then
                    echo "Tuning case failed: $case_id" >&2
                    exit 1
                fi
                mapfile -t evidence_lines < <(printf '%s\n' "$output" | sed -n 's/^.*LOCAL_LLM_TUNING_JSON //p')
                if [[ ${#evidence_lines[@]} -ne 2 ]]; then
                    echo "Expected cold and warm evidence for $case_id" >&2
                    exit 1
                fi
                printf '%s\n' "${evidence_lines[@]}" >> "$JSONL"
            done
        done
    done
done

python3 scripts/summarize-qwen35-tuning.py "$JSONL" "$CSV"
echo "Qwen3.5 tuning evidence written to:"
echo "  $JSONL"
echo "  $CSV"
