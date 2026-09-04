#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
CONTEXT=2048
THREADS=4
BATCH_THREADS=4
BATCH=128
UBATCH=64
REPETITIONS=3
MAX_OUTPUT_TOKENS=64
GENERATION_SEED=42
THINKING_MODE="DISABLED"
TIMEOUT_SECONDS=600
CASE_SCOPE="all"
THERMAL_START_MAX="1"
COOLDOWN_TIMEOUT_SECONDS=1800
COOLDOWN_POLL_SECONDS=30
OUTPUT_DIR="$ROOT_DIR/build/llrt6"
RESET_OUTPUT=false
PROMPT_FILE=""
DEFAULT_PROMPT="How much is the Earth radius?"

usage() {
    cat <<'EOF'
Usage: bash scripts/run-llama-cpp-kv-cache-evidence.sh --model /path/model.gguf --tier 0.8b|2b [options]

Runs bounded LLRT-6 physical-device evidence for explicit llama.cpp K/V cache data types.
It reuses the Qwen3.5 physical tuning evidence contract and never promotes a cache policy automatically.

Options:
  --device SERIAL                 ADB serial. Optional when exactly one device is online.
  --context N                     Approved Qwen3.5 context tier (default: 2048).
  --threads N                     Generation threads (default: 4).
  --batch-threads N               Batch/prefill threads (default: 4).
  --batch N                       Batch size (default: 128).
  --ubatch N                      Micro-batch size (default: 64).
  --repetitions N                 Warm samples per case, >= 3 (default: 3).
  --max-output-tokens N           Output budget (default: 64).
  --seed N                        Fixed non-negative generation seed (default: 42).
  --thinking-mode MODE            DISABLED or ENABLED (default: DISABLED).
  --timeout-seconds N             Per-generation timeout (default: 600).
  --prompt-file PATH              UTF-8 prompt file; only its SHA-256 enters evidence.
  --case NAME[,NAME...]           all or a comma-separated subset of the bounded cases below.
  --thermal-start-max N|off       Require Android thermal status <= N before each case (default: 1).
  --cooldown-timeout-seconds N    Maximum thermal-gate wait (default: 1800).
  --cooldown-poll-seconds N       Thermal-gate polling interval (default: 30).
  --output-dir PATH               Evidence root (default: build/llrt6).
  --reset-output                  Discard evidence for this exact run identity.
  --help                          Show this help.

Bounded cases:
  release-default                 DEFAULT K / DEFAULT V / Flash Attention off
  k-q8-fa-off                     q8_0 K / DEFAULT V / Flash Attention off
  k-q4-fa-off                     q4_0 K / DEFAULT V / Flash Attention off
  f16-f16-fa-on                   f16 K / f16 V / Flash Attention on
  q8-q8-fa-on                     q8_0 K / q8_0 V / Flash Attention on
  q4-q4-fa-on                     q4_0 K / q4_0 V / Flash Attention on

The K-only lane isolates key-cache type changes without enabling Flash Attention. The K+V lane
uses an explicit f16/f16 + FA-on control because the pinned llama.cpp revision requires Flash
Attention for quantized V cache. Do not compare either FA-on candidate directly with the release
baseline as a single-factor cache result. Each sample records memory, latency, thermal state and a
privacy-safe output SHA-256 under a fixed seed. Digest differences are evidence for review, not an
automatic quality verdict.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model) MODEL="${2:-}"; shift 2 ;;
        --tier) TIER="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --context) CONTEXT="${2:-}"; shift 2 ;;
        --threads) THREADS="${2:-}"; shift 2 ;;
        --batch-threads) BATCH_THREADS="${2:-}"; shift 2 ;;
        --batch) BATCH="${2:-}"; shift 2 ;;
        --ubatch) UBATCH="${2:-}"; shift 2 ;;
        --repetitions) REPETITIONS="${2:-}"; shift 2 ;;
        --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
        --seed) GENERATION_SEED="${2:-}"; shift 2 ;;
        --thinking-mode) THINKING_MODE="${2:-}"; shift 2 ;;
        --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
        --prompt-file) PROMPT_FILE="${2:-}"; shift 2 ;;
        --case) CASE_SCOPE="${2:-}"; shift 2 ;;
        --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
        --cooldown-timeout-seconds) COOLDOWN_TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
        --cooldown-poll-seconds) COOLDOWN_POLL_SECONDS="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --reset-output) RESET_OUTPUT=true; shift ;;
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
if [[ -n "$PROMPT_FILE" && (! -f "$PROMPT_FILE" || ! -r "$PROMPT_FILE") ]]; then
    echo "--prompt-file must point to a readable UTF-8 file" >&2
    exit 2
fi
for value_name in THREADS BATCH_THREADS BATCH UBATCH MAX_OUTPUT_TOKENS TIMEOUT_SECONDS COOLDOWN_TIMEOUT_SECONDS COOLDOWN_POLL_SECONDS; do
    value="${!value_name}"
    if [[ ! "$value" =~ ^[0-9]+$ ]] || ((value < 1)); then
        echo "$value_name must be a positive integer" >&2
        exit 2
    fi
done
if [[ ! "$REPETITIONS" =~ ^[0-9]+$ ]] || ((REPETITIONS < 3)); then
    echo "--repetitions must be an integer >= 3" >&2
    exit 2
fi
if [[ ! "$GENERATION_SEED" =~ ^[0-9]+$ ]]; then
    echo "--seed must be a non-negative integer" >&2
    exit 2
fi
if ((UBATCH > BATCH)); then
    echo "--ubatch must be <= --batch" >&2
    exit 2
fi
if [[ "$THERMAL_START_MAX" != "off" && ! "$THERMAL_START_MAX" =~ ^[0-6]$ ]]; then
    echo "--thermal-start-max must be 0..6 or off" >&2
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

validate_case_scope() {
    if [[ "$CASE_SCOPE" == "all" ]]; then
        return 0
    fi
    old_ifs="$IFS"
    IFS=','
    set -- $CASE_SCOPE
    IFS="$old_ifs"
    if [[ $# -eq 0 ]]; then
        echo "--case must not be empty" >&2
        exit 2
    fi
    for selected_case in "$@"; do
        case "$selected_case" in
            release-default|k-q8-fa-off|k-q4-fa-off|f16-f16-fa-on|q8-q8-fa-on|q4-q4-fa-on) ;;
            *) echo "--case contains unsupported value: $selected_case" >&2; exit 2 ;;
        esac
    done
}
validate_case_scope

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

PROMPT_PAYLOAD="$(python3 - "$PROMPT_FILE" "$DEFAULT_PROMPT" <<'PY'
import base64
import hashlib
import sys
from pathlib import Path

prompt_file = sys.argv[1]
default_prompt = sys.argv[2]
prompt = Path(prompt_file).read_text(encoding="utf-8") if prompt_file else default_prompt
if not prompt.strip():
    raise SystemExit("prompt must not be blank")
prompt_bytes = prompt.encode("utf-8")
print(hashlib.sha256(prompt_bytes).hexdigest())
print(base64.b64encode(prompt_bytes).decode("ascii"))
PY
)"
PROMPT_DIGEST="$(printf '%s\n' "$PROMPT_PAYLOAD" | sed -n '1p')"
PROMPT_BASE64="$(printf '%s\n' "$PROMPT_PAYLOAD" | sed -n '2p')"
PROMPT_KEY="$(printf '%s' "$PROMPT_DIGEST" | cut -c1-12)"
if [[ ! "$PROMPT_DIGEST" =~ ^[0-9a-f]{64}$ || -z "$PROMPT_BASE64" ]]; then
    echo "Unable to build prompt evidence identity" >&2
    exit 2
fi

case "$TIER" in
    0.8b)
        EXPECTED_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
        EXPECTED_MODEL_TIER="B0_8"
        ;;
    2b)
        EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
        EXPECTED_MODEL_TIER="B2"
        ;;
esac
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
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
    echo "LLRT-6 evidence requires arm64-v8a; device reports $DEVICE_ABI" >&2
    exit 2
fi
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"

cd "$ROOT_DIR"
HARNESS_COMMIT="$(git rev-parse HEAD)"
THINKING_MODE_SLUG="$(printf '%s' "$THINKING_MODE" | tr '[:upper:]' '[:lower:]')"
RUN_KEY="ctx${CONTEXT}-out${MAX_OUTPUT_TOKENS}-w${REPETITIONS}-seed${GENERATION_SEED}-${THINKING_MODE_SLUG}-p${PROMPT_KEY}"
TIER_OUTPUT_DIR="$OUTPUT_DIR/$TIER"
mkdir -p "$TIER_OUTPUT_DIR"
JSONL="$TIER_OUTPUT_DIR/llama-cpp-kv-cache-${RUN_KEY}-evidence.jsonl"
CSV="$TIER_OUTPUT_DIR/llama-cpp-kv-cache-${RUN_KEY}-summary.csv"

if [[ "$RESET_OUTPUT" == true ]]; then
    echo "Resetting evidence for exact run identity: $RUN_KEY"
    : > "$JSONL"
    rm -f "$CSV"
elif [[ ! -f "$JSONL" ]]; then
    : > "$JSONL"
fi

validate_existing_evidence() {
    [[ -s "$JSONL" ]] || return 0
    python3 - "$JSONL" "$EXPECTED_SHA" "$EXPECTED_MODEL_TIER" "$BACKEND_REVISION" "$HARNESS_COMMIT" \
        "$CONTEXT" "$MAX_OUTPUT_TOKENS" "$REPETITIONS" "$THINKING_MODE" "$DEVICE_MODEL" "$DEVICE_RELEASE" \
        "$DEVICE_SDK" "$DEVICE_ABI" "$PROMPT_DIGEST" "$GENERATION_SEED" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
expected = {
    "schemaVersion": 5,
    "modelDigest": sys.argv[2],
    "modelTier": sys.argv[3],
    "architecture": "qwen35",
    "quantization": "Q4_K_M",
    "backendRevision": sys.argv[4],
    "harnessCommit": sys.argv[5],
    "contextTokens": int(sys.argv[6]),
    "maxOutputTokens": int(sys.argv[7]),
    "warmRepetitionsRequested": int(sys.argv[8]),
    "thinkingMode": sys.argv[9],
    "deviceModel": sys.argv[10],
    "androidRelease": sys.argv[11],
    "sdkInt": int(sys.argv[12]),
    "abi": sys.argv[13],
    "promptDigest": sys.argv[14],
    "seedPolicy": "FIXED",
    "generationSeed": int(sys.argv[15]),
}
for line_number, line in enumerate(path.read_text().splitlines(), start=1):
    if not line.strip():
        continue
    record = json.loads(line)
    for field, value in expected.items():
        if record.get(field) != value:
            raise SystemExit(
                f"existing evidence {path} is incompatible at line {line_number}: "
                f"{field}={record.get(field)!r}, expected {value!r}. "
                "Use --reset-output or a different output directory."
            )
PY
}
validate_existing_evidence

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

read_thermal_status() {
    set +e
    thermal_output="$(
        "${ADB_CMD[@]}" shell am instrument -w -r \
            -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#reportsThermalStatus \
            "$RUNNER" 2>&1
    )"
    thermal_command_status=$?
    set -e
    thermal_output="$(printf '%s' "$thermal_output" | tr -d '\r')"
    if ((thermal_command_status != 0)) || printf '%s\n' "$thermal_output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        printf '%s\n' "$thermal_output" >&2
        return 1
    fi
    thermal_status="$(printf '%s\n' "$thermal_output" | sed -n 's/^.*LOCAL_LLM_THERMAL_STATUS //p' | tail -n 1)"
    if [[ ! "$thermal_status" =~ ^-?[0-9]+$ ]]; then
        echo "Unable to parse Android thermal status" >&2
        return 1
    fi
    printf '%s\n' "$thermal_status"
}

wait_for_thermal_gate() {
    if [[ "$THERMAL_START_MAX" == "off" ]]; then
        echo "Thermal start gate disabled by explicit request"
        return 0
    fi
    thermal_deadline=$(( $(date +%s) + COOLDOWN_TIMEOUT_SECONDS ))
    while true; do
        current_thermal="$(read_thermal_status)" || exit 1
        if ((current_thermal < 0)); then
            echo "Android thermal status is unavailable; use --thermal-start-max off only if intentional" >&2
            exit 1
        fi
        if ((current_thermal <= THERMAL_START_MAX)); then
            echo "Thermal gate satisfied: status=$current_thermal <= $THERMAL_START_MAX"
            return 0
        fi
        now_epoch="$(date +%s)"
        if ((now_epoch >= thermal_deadline)); then
            echo "Thermal gate timed out: status=$current_thermal > $THERMAL_START_MAX" >&2
            exit 1
        fi
        remaining=$((thermal_deadline - now_epoch))
        echo "Thermal status=$current_thermal > $THERMAL_START_MAX; cooling for ${COOLDOWN_POLL_SECONDS}s (${remaining}s remaining)"
        sleep "$COOLDOWN_POLL_SECONDS"
    done
}

case_selected() {
    label="$1"
    if [[ "$CASE_SCOPE" == "all" ]]; then
        return 0
    fi
    printf '%s\n' "$CASE_SCOPE" | tr ',' '\n' | grep -Fqx "$label"
}

case_state() {
    case_id="$1"
    python3 - "$JSONL" "$case_id" "$REPETITIONS" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
case_id = sys.argv[2]
repetitions = int(sys.argv[3])
records = []
if path.exists():
    for line in path.read_text().splitlines():
        if line.strip():
            record = json.loads(line)
            if record.get("tuningCaseId") == case_id:
                records.append(record)
if not records:
    print("missing")
    raise SystemExit(0)
indexes = sorted(int(record.get("sampleIndex", -1)) for record in records)
loads = [record.get("modelLoadKind") for record in records]
expected = list(range(repetitions + 1))
print("complete" if len(records) == repetitions + 1 and indexes == expected and loads.count("COLD") == 1 and loads.count("WARM") == repetitions else "partial")
PY
}

remove_case_records() {
    case_id="$1"
    python3 - "$JSONL" "$case_id" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
case_id = sys.argv[2]
kept = []
removed = 0
for line in path.read_text().splitlines():
    if not line.strip():
        continue
    record = json.loads(line)
    if record.get("tuningCaseId") == case_id:
        removed += 1
    else:
        kept.append(line)
path.write_text("\n".join(kept) + ("\n" if kept else ""))
print(removed)
PY
}

run_case() {
    label="$1"
    kv_k="$2"
    kv_v="$3"
    flash_attention="$4"

    if ! case_selected "$label"; then
        return
    fi

    case_id="llrt6-${TIER}-ctx${CONTEXT}-${label}-t${THREADS}-bt${BATCH_THREADS}-b${BATCH}-ub${UBATCH}-out${MAX_OUTPUT_TOKENS}-w${REPETITIONS}-seed${GENERATION_SEED}-${THINKING_MODE_SLUG}-p${PROMPT_KEY}"
    existing_state="$(case_state "$case_id")"
    if [[ "$existing_state" == "complete" ]]; then
        echo "Resume: $case_id already has complete evidence; skipping"
        return
    fi
    if [[ "$existing_state" == "partial" ]]; then
        removed_count="$(remove_case_records "$case_id")"
        echo "Removed $removed_count partial records for $case_id before rerun"
    fi

    wait_for_thermal_gate
    echo "Running $case_id: K=$kv_k V=$kv_v FA=$flash_attention; 1 cold + $REPETITIONS warm"

    instrument_args=(
        -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#recordsColdAndWarmEvidence
        -e modelRelativePath files/e2e/model.gguf
        -e modelSha256 "$EXPECTED_SHA"
        -e modelTier "$TIER"
        -e contextSize "$CONTEXT"
        -e batchSize "$BATCH"
        -e microBatchSize "$UBATCH"
        -e cpuThreads "$THREADS"
        -e batchThreads "$BATCH_THREADS"
        -e maxOutputTokens "$MAX_OUTPUT_TOKENS"
        -e warmRepetitions "$REPETITIONS"
        -e flashAttention "$flash_attention"
        -e generationSeed "$GENERATION_SEED"
        -e thinkingMode "$THINKING_MODE"
        -e tuningCaseId "$case_id"
        -e harnessCommit "$HARNESS_COMMIT"
        -e promptBase64 "$PROMPT_BASE64"
        -e promptSha256 "$PROMPT_DIGEST"
        -e timeoutSeconds "$TIMEOUT_SECONDS"
    )
    if [[ "$kv_k" != "DEFAULT" ]]; then
        instrument_args+=( -e kvCacheTypeK "$kv_k" )
    fi
    if [[ "$kv_v" != "DEFAULT" ]]; then
        instrument_args+=( -e kvCacheTypeV "$kv_v" )
    fi

    set +e
    output="$("${ADB_CMD[@]}" shell am instrument -w -r "${instrument_args[@]}" "$RUNNER" 2>&1)"
    status=$?
    set -e
    output="$(printf '%s' "$output" | tr -d '\r')"
    printf '%s\n' "$output"
    if ((status != 0)) || printf '%s\n' "$output" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
        echo "LLRT-6 KV-cache case failed: $case_id" >&2
        exit 1
    fi

    evidence_lines="$(printf '%s\n' "$output" | sed -n 's/^.*LOCAL_LLM_TUNING_JSON //p')"
    evidence_count="$(printf '%s\n' "$evidence_lines" | sed '/^$/d' | wc -l | tr -d ' ')"
    expected_count=$((REPETITIONS + 1))
    if [[ "$evidence_count" -ne "$expected_count" ]]; then
        echo "Expected $expected_count evidence records for $case_id, got $evidence_count" >&2
        exit 1
    fi

    case_jsonl="${JSONL}.case.$$"
    case_csv="${CSV}.case.$$"
    printf '%s\n' "$evidence_lines" > "$case_jsonl"
    if ! python3 scripts/summarize-qwen35-tuning.py "$case_jsonl" "$case_csv" >/dev/null; then
        rm -f "$case_jsonl" "$case_csv"
        echo "Evidence validation failed for $case_id" >&2
        exit 1
    fi
    cat "$case_jsonl" >> "$JSONL"
    rm -f "$case_jsonl" "$case_csv"
    echo "Recorded complete evidence for $case_id"
}

run_case release-default DEFAULT DEFAULT false
run_case k-q8-fa-off q8_0 DEFAULT false
run_case k-q4-fa-off q4_0 DEFAULT false
run_case f16-f16-fa-on f16 f16 true
run_case q8-q8-fa-on q8_0 q8_0 true
run_case q4-q4-fa-on q4_0 q4_0 true

if [[ ! -s "$JSONL" ]]; then
    echo "No completed LLRT-6 evidence cases are available for this run identity" >&2
    exit 1
fi
python3 scripts/summarize-qwen35-tuning.py "$JSONL" "$CSV"
echo "Bounded LLRT-6 KV-cache evidence written to:"
echo "  $JSONL"
echo "  $CSV"
echo "Prompt SHA-256: $PROMPT_DIGEST"
echo "Compare K-only candidates with release-default; compare quantized K+V candidates only with f16-f16-fa-on."
echo "Output digests are review signals only. No cache type, Flash Attention mode or runtime profile was promoted automatically."
