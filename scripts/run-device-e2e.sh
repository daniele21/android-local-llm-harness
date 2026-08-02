#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
MODEL_PATH=""
MODEL_ARCHITECTURE="unknown"
MODEL_QUANTIZATION="unknown"
PROMPT="Reply with the single word READY."
CANCELLATION_ENABLED="true"
CANCELLATION_PROMPT="Write a numbered list from 1 to 1000. Continue until every number is written."
MEMORY_REPEAT_COUNT="0"
MAX_PSS_GROWTH_KB="131072"
TIMEOUT_SECONDS="120"
CPU_THREADS=""
ADB="${ADB:-adb}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-device-e2e.sh --model /absolute/path/model.gguf [options]

Options:
  --architecture VALUE       GGUF architecture label used by the test profile.
  --quantization VALUE       Quantization label used by the test profile.
  --prompt VALUE             Prompt used by the generation lifecycle test.
  --skip-cancellation        Skip active-generation cancellation validation.
  --cancellation-prompt VAL  Long prompt used before cooperative cancellation.
  --memory-repeat COUNT      Run repeated load/generate/unload cycles (0 disables).
  --max-pss-growth-kb VALUE  Allowed PSS growth between first and final cycle.
  --timeout-seconds VALUE    Timeout for each asynchronous operation.
  --cpu-threads VALUE        Explicit llama.cpp CPU thread count.
  --help                     Show this help.

The model is streamed into the debuggable test application's private data directory.
It is never copied into the repository or packaged inside an APK.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model)
            MODEL_PATH="${2:-}"
            shift 2
            ;;
        --architecture)
            MODEL_ARCHITECTURE="${2:-}"
            shift 2
            ;;
        --quantization)
            MODEL_QUANTIZATION="${2:-}"
            shift 2
            ;;
        --prompt)
            PROMPT="${2:-}"
            shift 2
            ;;
        --skip-cancellation)
            CANCELLATION_ENABLED="false"
            shift
            ;;
        --cancellation-prompt)
            CANCELLATION_PROMPT="${2:-}"
            shift 2
            ;;
        --memory-repeat)
            MEMORY_REPEAT_COUNT="${2:-}"
            shift 2
            ;;
        --max-pss-growth-kb)
            MAX_PSS_GROWTH_KB="${2:-}"
            shift 2
            ;;
        --timeout-seconds)
            TIMEOUT_SECONDS="${2:-}"
            shift 2
            ;;
        --cpu-threads)
            CPU_THREADS="${2:-}"
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

if [[ -z "$MODEL_PATH" ]]; then
    echo "--model is required" >&2
    usage >&2
    exit 2
fi

if [[ ! -f "$MODEL_PATH" || ! -r "$MODEL_PATH" ]]; then
    echo "Model must be a readable regular file: $MODEL_PATH" >&2
    exit 2
fi

if ! command -v "$ADB" >/dev/null 2>&1; then
    echo "adb is unavailable. Install Android platform-tools or set ADB." >&2
    exit 2
fi

if ! command -v base64 >/dev/null 2>&1; then
    echo "base64 is required to pass prompts safely to instrumentation" >&2
    exit 2
fi

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
    echo "Gradle wrapper is missing or not executable: $ROOT_DIR/gradlew" >&2
    exit 2
fi

require_non_negative_integer() {
    local name="$1"
    local value="$2"
    if [[ ! "$value" =~ ^[0-9]+$ ]]; then
        echo "$name must be a non-negative integer: $value" >&2
        exit 2
    fi
}

require_positive_integer() {
    local name="$1"
    local value="$2"
    require_non_negative_integer "$name" "$value"
    if (( value == 0 )); then
        echo "$name must be positive" >&2
        exit 2
    fi
}

require_non_negative_integer "--memory-repeat" "$MEMORY_REPEAT_COUNT"
require_non_negative_integer "--max-pss-growth-kb" "$MAX_PSS_GROWTH_KB"
require_positive_integer "--timeout-seconds" "$TIMEOUT_SECONDS"
if (( MEMORY_REPEAT_COUNT == 1 )); then
    echo "--memory-repeat must be 0 or at least 2" >&2
    exit 2
fi
if [[ -n "$CPU_THREADS" ]]; then
    require_positive_integer "--cpu-threads" "$CPU_THREADS"
fi

"$ADB" get-state >/dev/null
DEVICE_ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$DEVICE_ABI" != arm64-v8a* ]]; then
    echo "The current Phase 1 backend requires arm64-v8a; device reports $DEVICE_ABI" >&2
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
        echo "A SHA-256 utility is required: sha256sum, shasum or openssl" >&2
        exit 2
    fi
}

encode_base64() {
    printf '%s' "$1" | base64 | tr -d '\r\n'
}

MODEL_SHA256="$(sha256_file "$MODEL_PATH" | tr '[:upper:]' '[:lower:]')"
PROMPT_BASE64="$(encode_base64 "$PROMPT")"
CANCELLATION_PROMPT_BASE64="$(encode_base64 "$CANCELLATION_PROMPT")"

cd "$ROOT_DIR"
./gradlew \
    :apps:device-test-runner:assembleDebug \
    :apps:device-test-runner:assembleDebugAndroidTest

APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"

if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
    echo "Unable to locate the device-test runner APKs" >&2
    exit 1
fi

"$ADB" install -r -t "$APP_APK"
"$ADB" install -r -t "$TEST_APK"

"$ADB" shell run-as "$APP_ID" sh -c 'mkdir -p files/e2e'
"$ADB" exec-out run-as "$APP_ID" sh -c 'cat > files/e2e/model.gguf' < "$MODEL_PATH"

REMOTE_SIZE="$("$ADB" shell run-as "$APP_ID" stat -c %s files/e2e/model.gguf | tr -d '\r')"
LOCAL_SIZE="$(wc -c < "$MODEL_PATH" | tr -d ' ')"
if [[ "$REMOTE_SIZE" != "$LOCAL_SIZE" ]]; then
    echo "Model transfer size mismatch: local=$LOCAL_SIZE remote=$REMOTE_SIZE" >&2
    exit 1
fi

RUNNER="$(
    "$ADB" shell pm list instrumentation \
        | tr -d '\r' \
        | grep -F "(target=$APP_ID)" \
        | head -n 1 \
        | sed -E 's/^instrumentation:([^ ]+).*/\1/' \
        || true
)"
if [[ -z "$RUNNER" ]]; then
    echo "Unable to discover the AndroidJUnitRunner targeting $APP_ID" >&2
    exit 1
fi

INSTRUMENTATION_ARGS=(
    -e modelRelativePath files/e2e/model.gguf
    -e modelSha256 "$MODEL_SHA256"
    -e modelArchitecture "$MODEL_ARCHITECTURE"
    -e modelQuantization "$MODEL_QUANTIZATION"
    -e promptBase64 "$PROMPT_BASE64"
    -e cancellationEnabled "$CANCELLATION_ENABLED"
    -e cancellationPromptBase64 "$CANCELLATION_PROMPT_BASE64"
    -e memoryRepeatCount "$MEMORY_REPEAT_COUNT"
    -e maxPssGrowthKb "$MAX_PSS_GROWTH_KB"
    -e timeoutSeconds "$TIMEOUT_SECONDS"
)
if [[ -n "$CPU_THREADS" ]]; then
    INSTRUMENTATION_ARGS+=( -e cpuThreads "$CPU_THREADS" )
fi

echo "Running local LLM device E2E tests"
echo "  device ABI: $DEVICE_ABI"
echo "  model bytes: $LOCAL_SIZE"
echo "  model SHA-256: $MODEL_SHA256"
echo "  cancellation: $CANCELLATION_ENABLED"
echo "  memory cycles: $MEMORY_REPEAT_COUNT"

set +e
TEST_OUTPUT="$(
    "$ADB" shell am instrument -w -r \
        "${INSTRUMENTATION_ARGS[@]}" \
        "$RUNNER" 2>&1
)"
TEST_STATUS=$?
set -e
printf '%s\n' "$TEST_OUTPUT"

if (( TEST_STATUS != 0 )) || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' <<< "$TEST_OUTPUT"; then
    echo "Device E2E validation failed" >&2
    exit 1
fi

if ! grep -Eq '^OK \(' <<< "$TEST_OUTPUT" || ! grep -Eq '^INSTRUMENTATION_CODE: -1$' <<< "$TEST_OUTPUT"; then
    echo "Instrumentation ended without the expected JUnit and Android success markers" >&2
    exit 1
fi

echo "Device E2E validation completed successfully"
