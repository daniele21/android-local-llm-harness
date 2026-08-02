#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB="${ADB:-adb}"
EVIDENCE_DIR=""
MODEL_PATH=""
MODEL_ARCHITECTURE="unknown"
MODEL_QUANTIZATION="unknown"
MEMORY_REPEAT_COUNT="0"
MAX_PSS_GROWTH_KB="131072"
TIMEOUT_SECONDS="120"
CPU_THREADS="default"
CANCELLATION_ENABLED="true"
CUSTOM_PROMPT="false"
CUSTOM_CANCELLATION_PROMPT="false"
FORWARD_ARGS=()

usage() {
    cat <<'EOF'
Usage:
  bash scripts/capture-device-e2e-evidence.sh \
    --model /absolute/path/model.gguf \
    [run-device-e2e options] \
    [--evidence-dir PATH]

This wrapper executes scripts/run-device-e2e.sh and writes a privacy-safe evidence
bundle containing the instrumentation log, selected device/build metadata, metrics,
APK hashes, packaged native-library inventory and optional thermal/memory snapshots.

The GGUF is never copied into the evidence bundle. Prompt text and device serial
numbers are not recorded.
EOF
}

require_value() {
    local option="$1"
    local value="${2:-}"
    if [[ -z "$value" ]]; then
        echo "$option requires a value" >&2
        exit 2
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --evidence-dir)
            require_value "$1" "${2:-}"
            EVIDENCE_DIR="$2"
            shift 2
            ;;
        --model)
            require_value "$1" "${2:-}"
            MODEL_PATH="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --architecture)
            require_value "$1" "${2:-}"
            MODEL_ARCHITECTURE="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --quantization)
            require_value "$1" "${2:-}"
            MODEL_QUANTIZATION="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --memory-repeat)
            require_value "$1" "${2:-}"
            MEMORY_REPEAT_COUNT="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --max-pss-growth-kb)
            require_value "$1" "${2:-}"
            MAX_PSS_GROWTH_KB="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --timeout-seconds)
            require_value "$1" "${2:-}"
            TIMEOUT_SECONDS="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --cpu-threads)
            require_value "$1" "${2:-}"
            CPU_THREADS="$2"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --prompt)
            require_value "$1" "${2:-}"
            CUSTOM_PROMPT="true"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --cancellation-prompt)
            require_value "$1" "${2:-}"
            CUSTOM_CANCELLATION_PROMPT="true"
            FORWARD_ARGS+=( "$1" "$2" )
            shift 2
            ;;
        --skip-cancellation)
            CANCELLATION_ENABLED="false"
            FORWARD_ARGS+=( "$1" )
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            FORWARD_ARGS+=( "$1" )
            shift
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

if [[ -z "$EVIDENCE_DIR" ]]; then
    EVIDENCE_DIR="$ROOT_DIR/build/device-e2e-evidence/$(date -u +%Y%m%dT%H%M%SZ)"
elif [[ "$EVIDENCE_DIR" != /* ]]; then
    EVIDENCE_DIR="$ROOT_DIR/$EVIDENCE_DIR"
fi

if [[ -e "$EVIDENCE_DIR" ]]; then
    echo "Evidence directory already exists: $EVIDENCE_DIR" >&2
    exit 2
fi
mkdir -p "$EVIDENCE_DIR"

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

read_prop() {
    local property="$1"
    "$ADB" shell getprop "$property" 2>/dev/null | tr -d '\r' || true
}

capture_optional_adb() {
    local output_file="$1"
    shift
    set +e
    "$ADB" "$@" > "$output_file" 2>&1
    local status=$?
    set -e
    if (( status != 0 )); then
        printf '\ncommand_exit_code=%s\n' "$status" >> "$output_file"
    fi
}

STARTED_AT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
MODEL_FILE_NAME="$(basename "$MODEL_PATH")"
MODEL_SIZE_BYTES="$(wc -c < "$MODEL_PATH" | tr -d ' ')"
MODEL_SHA256="$(sha256_file "$MODEL_PATH" | tr '[:upper:]' '[:lower:]')"
GIT_COMMIT="$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT_DIR" branch --show-current 2>/dev/null || echo unknown)"
if [[ -n "$(git -C "$ROOT_DIR" status --porcelain 2>/dev/null || true)" ]]; then
    GIT_DIRTY="true"
else
    GIT_DIRTY="false"
fi

"$ADB" get-state >/dev/null
DEVICE_MANUFACTURER="$(read_prop ro.product.manufacturer)"
DEVICE_MODEL="$(read_prop ro.product.model)"
DEVICE_DEVICE="$(read_prop ro.product.device)"
DEVICE_ABI="$(read_prop ro.product.cpu.abi)"
ANDROID_RELEASE="$(read_prop ro.build.version.release)"
ANDROID_SDK="$(read_prop ro.build.version.sdk)"
BUILD_FINGERPRINT="$(read_prop ro.build.fingerprint)"

capture_optional_adb "$EVIDENCE_DIR/thermal-before.txt" shell dumpsys thermalservice
capture_optional_adb "$EVIDENCE_DIR/meminfo-before.txt" shell dumpsys meminfo "$APP_ID"

RUN_LOG="$EVIDENCE_DIR/instrumentation.log"
set +e
bash "$ROOT_DIR/scripts/run-device-e2e.sh" "${FORWARD_ARGS[@]}" 2>&1 | tee "$RUN_LOG"
RUN_STATUS=${PIPESTATUS[0]}
set -e

capture_optional_adb "$EVIDENCE_DIR/thermal-after.txt" shell dumpsys thermalservice
capture_optional_adb "$EVIDENCE_DIR/meminfo-after.txt" shell dumpsys meminfo "$APP_ID"

grep -E '^LOCAL_LLM_E2E ' "$RUN_LOG" > "$EVIDENCE_DIR/metrics.txt" || true
grep -E '^(OK \(|INSTRUMENTATION_CODE:|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=)' \
    "$RUN_LOG" > "$EVIDENCE_DIR/test-markers.txt" || true

APK_INVENTORY="$EVIDENCE_DIR/apk-inventory.txt"
APK_HASHES="$EVIDENCE_DIR/apk-sha256.txt"
: > "$APK_INVENTORY"
: > "$APK_HASHES"
while IFS= read -r -d '' apk; do
    relative_apk="${apk#"$ROOT_DIR/"}"
    printf '%s  %s\n' "$(sha256_file "$apk")" "$relative_apk" >> "$APK_HASHES"
    printf '## %s\n' "$relative_apk" >> "$APK_INVENTORY"
    if command -v unzip >/dev/null 2>&1; then
        unzip -l "$apk" 2>/dev/null | awk '$4 ~ /^lib\/.*\.so$/ { print $4 }' >> "$APK_INVENTORY" || true
    else
        printf 'unzip unavailable; native-library inventory not collected\n' >> "$APK_INVENTORY"
    fi
    printf '\n' >> "$APK_INVENTORY"
done < <(
    find "$ROOT_DIR/apps/device-test-runner/build/outputs/apk" \
        -type f -name '*.apk' -print0 2>/dev/null | sort -z
)

FINISHED_AT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$EVIDENCE_DIR/manifest.txt" <<EOF
format_version=1
started_at_utc=$STARTED_AT_UTC
finished_at_utc=$FINISHED_AT_UTC
runner_exit_code=$RUN_STATUS
repository_commit=$GIT_COMMIT
repository_branch=$GIT_BRANCH
repository_dirty=$GIT_DIRTY
model_file_name=$MODEL_FILE_NAME
model_size_bytes=$MODEL_SIZE_BYTES
model_sha256=$MODEL_SHA256
model_architecture=$MODEL_ARCHITECTURE
model_quantization=$MODEL_QUANTIZATION
cancellation_enabled=$CANCELLATION_ENABLED
memory_repeat_count=$MEMORY_REPEAT_COUNT
max_pss_growth_kb=$MAX_PSS_GROWTH_KB
timeout_seconds=$TIMEOUT_SECONDS
cpu_threads=$CPU_THREADS
custom_prompt=$CUSTOM_PROMPT
custom_cancellation_prompt=$CUSTOM_CANCELLATION_PROMPT
device_manufacturer=$DEVICE_MANUFACTURER
device_model=$DEVICE_MODEL
device_codename=$DEVICE_DEVICE
device_abi=$DEVICE_ABI
android_release=$ANDROID_RELEASE
android_sdk=$ANDROID_SDK
build_fingerprint=$BUILD_FINGERPRINT
EOF

cat > "$EVIDENCE_DIR/README.txt" <<'EOF'
This directory is a privacy-safe Android local-LLM device validation record.

It intentionally excludes:
- the GGUF model bytes;
- prompt and generated-content text;
- adb device serial numbers.

Review manifest.txt, metrics.txt, test-markers.txt, instrumentation.log,
apk-inventory.txt, APK hashes and the before/after thermal and memory snapshots.
A successful script exit is necessary but does not by itself prove leak freedom or
representative performance across the supported device matrix.
EOF

ARCHIVE_PATH="${EVIDENCE_DIR}.tar.gz"
tar -czf "$ARCHIVE_PATH" -C "$(dirname "$EVIDENCE_DIR")" "$(basename "$EVIDENCE_DIR")"

echo "Evidence directory: $EVIDENCE_DIR"
echo "Evidence archive: $ARCHIVE_PATH"

if (( RUN_STATUS != 0 )); then
    echo "Device E2E validation failed; evidence was preserved" >&2
fi
exit "$RUN_STATUS"
