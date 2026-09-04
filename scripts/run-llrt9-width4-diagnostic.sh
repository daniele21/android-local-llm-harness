#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
CONTEXT=1024
THREADS=""
BATCH_THREADS=4
BATCH=128
UBATCH=64
MAX_OUTPUT_TOKENS=8
GENERATION_SEED=42
TIMEOUT_SECONDS=900
THERMAL_START_MAX=1
COOLDOWN_TIMEOUT_SECONDS=1800
COOLDOWN_POLL_SECONDS=30
CASE_SCOPE="all"
OUTPUT_DIR="$ROOT_DIR/build/llrt9-width4-diagnostic"
RESET_OUTPUT=false
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
MODEL_RELATIVE_PATH="e2e/model.gguf"
MODEL_APP_DATA_PATH="files/$MODEL_RELATIVE_PATH"

usage() {
  cat <<'EOF'
Usage: bash scripts/run-llrt9-width4-diagnostic.sh --model /path/model.gguf --tier 0.8b|2b [options]

Runs non-qualifying LLRT-9 width=4 diagnostics to distinguish sequence-slot attribution
bugs from sampling/numerical divergence. Diagnostic output is intentionally separate from
canonical LLRT-9C evidence and MUST NOT be used to promote runtime policy.

Diagnostic cases:
  baseline-quality   quality sampling, prompt order 0,1,2,3
  swap02-quality     quality sampling, prompt order 2,1,0,3
  baseline-greedy    greedy control, prompt order 0,1,2,3

The swap case exchanges the previously divergent source prompt (index 2) with slot 0.
If divergence follows slot 2, attribution/KV handling is suspect. If it follows prompt 2,
sampling sensitivity is more likely. Greedy control removes stochastic sampling while
keeping the same model, context, sequence width and batch path.

Options:
  --device SERIAL                 Exact ADB serial; auto-resolved when one device is online.
  --context N                     Per-sequence context tokens (default: 1024).
  --threads N                     Generation threads (default: 2 for 0.8b, 4 for 2b).
  --batch-threads N               Prefill/batch threads (default: 4).
  --batch N                       llama.cpp batch size (default: 128).
  --ubatch N                      llama.cpp micro-batch size (default: 64).
  --max-output-tokens N           Output budget per case (default: 8).
  --seed N                        Fixed non-negative seed (default: 42).
  --timeout-seconds N             Per diagnostic pair timeout (default: 900).
  --case NAME|all                 Run one diagnostic case or all three (default: all).
  --thermal-start-max N|off       Require Android thermal status <= N before each case (default: 1).
  --cooldown-timeout-seconds N    Maximum thermal-gate wait (default: 1800).
  --cooldown-poll-seconds N       Thermal polling interval (default: 30).
  --output-dir PATH               Diagnostic root (default: build/llrt9-width4-diagnostic).
  --reset-output                  Discard diagnostics for this exact run identity.
  --help                          Show this help.
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
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --seed) GENERATION_SEED="${2:-}"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
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

[[ -f "$MODEL" && -r "$MODEL" ]] || { echo "--model must point to a readable GGUF file" >&2; exit 2; }
[[ "$TIER" == "0.8b" || "$TIER" == "2b" ]] || { echo "--tier must be 0.8b or 2b" >&2; exit 2; }
[[ "$CASE_SCOPE" == "all" || "$CASE_SCOPE" == "baseline-quality" || "$CASE_SCOPE" == "swap02-quality" || "$CASE_SCOPE" == "baseline-greedy" ]] || {
  echo "--case must be all, baseline-quality, swap02-quality or baseline-greedy" >&2
  exit 2
}
[[ "$THERMAL_START_MAX" == "off" || "$THERMAL_START_MAX" =~ ^[0-6]$ ]] || {
  echo "--thermal-start-max must be 0..6 or off" >&2
  exit 2
}
if [[ -z "$THREADS" ]]; then
  [[ "$TIER" == "0.8b" ]] && THREADS=2 || THREADS=4
fi
for value_name in CONTEXT THREADS BATCH_THREADS BATCH UBATCH MAX_OUTPUT_TOKENS TIMEOUT_SECONDS COOLDOWN_TIMEOUT_SECONDS COOLDOWN_POLL_SECONDS; do
  value="${!value_name}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 )) || { echo "$value_name must be a positive integer" >&2; exit 2; }
done
[[ "$GENERATION_SEED" =~ ^[0-9]+$ ]] || { echo "--seed must be non-negative" >&2; exit 2; }
(( UBATCH <= BATCH )) || { echo "--ubatch must be <= --batch" >&2; exit 2; }
(( CONTEXT % 256 == 0 )) || { echo "--context must be a multiple of 256" >&2; exit 2; }
command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }
command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 2; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then openssl dgst -sha256 "$1" | awk '{print $NF}'
  else echo "A SHA-256 implementation is required" >&2; exit 2
  fi
}

tracked_worktree_status() {
  git status --short --untracked-files=no --ignore-submodules=dirty
}

require_clean_tracked_worktree() {
  stage="$1"
  if ! git diff --quiet --ignore-submodules=dirty -- || ! git diff --cached --quiet --ignore-submodules=dirty --; then
    echo "LLRT-9 diagnostic requires a clean tracked Harness worktree ($stage)" >&2
    dirty_status="$(tracked_worktree_status)"
    if [[ -n "$dirty_status" ]]; then
      echo "Tracked changes:" >&2
      printf '%s\n' "$dirty_status" >&2
    fi
    exit 2
  fi
}

resolve_device() {
  if [[ -n "$DEVICE" ]]; then
    "$ADB_BIN" -s "$DEVICE" get-state >/dev/null
    return
  fi
  online_devices=()
  while IFS= read -r serial; do
    [[ -n "$serial" ]] && online_devices+=("$serial")
  done < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if (( ${#online_devices[@]} != 1 )); then
    echo "Expected exactly one online ADB device; found ${#online_devices[@]}. Use --device SERIAL." >&2
    "$ADB_BIN" devices >&2
    exit 2
  fi
  DEVICE="${online_devices[0]}"
}
resolve_device

echo "== Device preflight =="
bash "$ROOT_DIR/scripts/llrt-device-preflight.sh" --device "$DEVICE"

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
ACTUAL_SHA="$(sha256_file "$MODEL" | tr '[:upper:]' '[:lower:]')"
[[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || { echo "$TIER model does not match the curated Q4_K_M reference" >&2; exit 2; }

cd "$ROOT_DIR"
require_clean_tracked_worktree "before build"
if [[ ! -e third_party/llama.cpp/.git ]]; then
  echo "third_party/llama.cpp is not initialized; initialize the pinned submodule before diagnostics" >&2
  exit 2
fi
if ! git -C third_party/llama.cpp diff --quiet -- || ! git -C third_party/llama.cpp diff --cached --quiet --; then
  echo "LLRT-9 diagnostic requires a clean llama.cpp submodule worktree" >&2
  exit 2
fi
ACTUAL_BACKEND_REVISION="$(git -C third_party/llama.cpp rev-parse HEAD)"
[[ "$ACTUAL_BACKEND_REVISION" == "$BACKEND_REVISION" ]] || {
  echo "Unexpected llama.cpp pin: expected $BACKEND_REVISION, got $ACTUAL_BACKEND_REVISION" >&2
  exit 2
}
HARNESS_COMMIT="$(git rev-parse HEAD)"
[[ "$HARNESS_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Unable to resolve exact Harness commit" >&2; exit 2; }

ADB_CMD=("$ADB_BIN" "-s" "$DEVICE")
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$DEVICE_ABI" == arm64-v8a* ]] || { echo "LLRT-9 diagnostic requires arm64-v8a; device reports $DEVICE_ABI" >&2; exit 2; }
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"

RUN_KEY="ctx${CONTEXT}-out${MAX_OUTPUT_TOKENS}-seed${GENERATION_SEED}-t${THREADS}-bt${BATCH_THREADS}-b${BATCH}-ub${UBATCH}"
TIER_OUTPUT_DIR="$OUTPUT_DIR/$TIER"
mkdir -p "$TIER_OUTPUT_DIR"
JSONL="$TIER_OUTPUT_DIR/llrt9-width4-${RUN_KEY}-diagnostic.jsonl"
CSV="$TIER_OUTPUT_DIR/llrt9-width4-${RUN_KEY}-summary.csv"
if [[ "$RESET_OUTPUT" == true ]]; then : > "$JSONL"; rm -f "$CSV"; elif [[ ! -f "$JSONL" ]]; then : > "$JSONL"; fi

python3 - "$JSONL" "$EXPECTED_SHA" "$EXPECTED_MODEL_TIER" "$BACKEND_REVISION" "$HARNESS_COMMIT" "$DEVICE_MODEL" "$DEVICE_RELEASE" "$DEVICE_SDK" "$DEVICE_ABI" "$CONTEXT" "$BATCH" "$UBATCH" "$THREADS" "$BATCH_THREADS" "$MAX_OUTPUT_TOKENS" "$GENERATION_SEED" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
expected = {
    "schemaVersion": 1,
    "evidenceType": "LLRT9_WIDTH4_DIAGNOSTIC",
    "modelDigest": sys.argv[2],
    "modelTier": sys.argv[3],
    "backendRevision": sys.argv[4],
    "harnessCommit": sys.argv[5],
    "deviceModel": sys.argv[6],
    "androidRelease": sys.argv[7],
    "sdkInt": int(sys.argv[8]),
    "abi": sys.argv[9],
    "contextTokensPerSequence": int(sys.argv[10]),
    "batchWidth": 4,
    "batchSize": int(sys.argv[11]),
    "microBatchSize": int(sys.argv[12]),
    "cpuThreads": int(sys.argv[13]),
    "batchThreads": int(sys.argv[14]),
    "maxOutputTokens": int(sys.argv[15]),
    "generationSeed": int(sys.argv[16]),
    "seedPolicy": "FIXED",
    "thinkingMode": "DISABLED",
}
for n, line in enumerate(path.read_text().splitlines(), 1):
    if not line.strip():
        continue
    record = json.loads(line)
    for key, value in expected.items():
        if record.get(key) != value:
            raise SystemExit(f"existing LLRT-9 diagnostic incompatible at line {n}: {key}")
    if record.get("diagnosticCase") not in {"baseline-quality", "swap02-quality", "baseline-greedy"}:
        raise SystemExit(f"existing LLRT-9 diagnostic has unknown case at line {n}")
    if record.get("aggregateContextTokens") != record["contextTokensPerSequence"] * 4:
        raise SystemExit(f"existing LLRT-9 diagnostic has invalid aggregate context at line {n}")
PY

./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
require_clean_tracked_worktree "after Gradle device-test build"
APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || { echo "Unable to locate device-test APKs" >&2; exit 1; }
"${ADB_CMD[@]}" install -r -t "$APP_APK"
"${ADB_CMD[@]}" install -r -t "$TEST_APK"
"${ADB_CMD[@]}" shell run-as "$APP_ID" mkdir -p files/e2e
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of="$MODEL_APP_DATA_PATH" bs=1048576 < "$MODEL" >/dev/null
trap '"${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f "$MODEL_APP_DATA_PATH" >/dev/null 2>&1 || true' EXIT
RUNNER="$("${ADB_CMD[@]}" shell pm list instrumentation | tr -d '\r' | grep -F "(target=$APP_ID)" | head -n 1 | sed -E 's/^instrumentation:([^ ]+).*/\1/' || true)"
[[ -n "$RUNNER" ]] || { echo "Unable to discover AndroidJUnitRunner" >&2; exit 1; }

read_thermal_status() {
  set +e
  out="$("${ADB_CMD[@]}" shell am instrument -w -r -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#reportsThermalStatus "$RUNNER" 2>&1)"
  rc=$?
  set -e
  out="$(printf '%s' "$out" | tr -d '\r')"
  (( rc == 0 )) || { printf '%s\n' "$out" >&2; return 1; }
  status="$(printf '%s\n' "$out" | sed -n 's/^.*LOCAL_LLM_THERMAL_STATUS //p' | tail -n 1)"
  [[ "$status" =~ ^-?[0-9]+$ ]] || return 1
  printf '%s\n' "$status"
}

wait_for_thermal_gate() {
  [[ "$THERMAL_START_MAX" == "off" ]] && return 0
  deadline=$(( $(date +%s) + COOLDOWN_TIMEOUT_SECONDS ))
  while true; do
    thermal="$(read_thermal_status)" || { echo "Unable to read thermal status" >&2; exit 1; }
    (( thermal >= 0 )) || { echo "Thermal status unavailable; use --thermal-start-max off only intentionally" >&2; exit 1; }
    if (( thermal <= THERMAL_START_MAX )); then return 0; fi
    (( $(date +%s) < deadline )) || { echo "Thermal gate timed out" >&2; exit 1; }
    sleep "$COOLDOWN_POLL_SECONDS"
  done
}

record_exists() {
  case_name="$1"
  python3 - "$JSONL" "$case_name" <<'PY'
import json, sys
from pathlib import Path
for line in Path(sys.argv[1]).read_text().splitlines():
    if line.strip() and json.loads(line).get("diagnosticCase") == sys.argv[2]:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

run_case() {
  case_name="$1"
  prompt_order="$2"
  sampling_mode="$3"
  if record_exists "$case_name"; then
    echo "Resume: diagnostic case=$case_name already recorded"
    return
  fi
  wait_for_thermal_gate
  echo "LLRT-9 width=4 diagnostic: case=$case_name promptOrder=$prompt_order sampling=$sampling_mode"
  set +e
  out="$("${ADB_CMD[@]}" shell am instrument -w -r \
    -e class io.github.daniele21.localllm.devicetest.Llrt9Width4DiagnosticInstrumentedTest#recordsWidth4Diagnostic \
    -e modelRelativePath "$MODEL_RELATIVE_PATH" \
    -e modelSha256 "$EXPECTED_SHA" -e modelTier "$TIER" \
    -e contextSize "$CONTEXT" -e batchSize "$BATCH" -e microBatchSize "$UBATCH" \
    -e cpuThreads "$THREADS" -e batchThreads "$BATCH_THREADS" -e maxOutputTokens "$MAX_OUTPUT_TOKENS" \
    -e generationSeed "$GENERATION_SEED" -e timeoutSeconds "$TIMEOUT_SECONDS" \
    -e harnessCommit "$HARNESS_COMMIT" -e diagnosticCase "$case_name" \
    -e promptOrder "$prompt_order" -e samplingMode "$sampling_mode" "$RUNNER" 2>&1)"
  rc=$?
  set -e
  out="$(printf '%s' "$out" | tr -d '\r')"
  printf '%s\n' "$out"
  if (( rc != 0 )) || printf '%s\n' "$out" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
    echo "LLRT-9 width=4 diagnostic execution failed for case=$case_name" >&2
    exit 1
  fi
  line="$(printf '%s\n' "$out" | sed -n 's/^.*LOCAL_LLM_LLRT9_DIAGNOSTIC_JSON //p' | tail -n 1)"
  [[ -n "$line" ]] || { echo "No LLRT-9 diagnostic record emitted" >&2; exit 1; }
  python3 - "$line" "$case_name" "$prompt_order" "$sampling_mode" <<'PY'
import json, sys
record = json.loads(sys.argv[1])
order = [int(value) for value in sys.argv[3].split(",")]
assert record["schemaVersion"] == 1
assert record["evidenceType"] == "LLRT9_WIDTH4_DIAGNOSTIC"
assert record["diagnosticCase"] == sys.argv[2]
assert record["promptSourceIndices"] == order
assert record["samplingMode"] == sys.argv[4]
assert record["batchWidth"] == 4
assert len(record["serialOutputDigests"]) == 4
assert len(record["batchOutputDigests"]) == 4
assert len(record["serialOutputTokensPerCase"]) == 4
assert len(record["batchOutputTokensPerCase"]) == 4
assert len(record["matchingSlots"]) == 4
assert record["aggregateContextTokens"] == record["contextTokensPerSequence"] * 4
assert record["serialElapsedMs"] > 0 and record["batchElapsedMs"] > 0
PY
  printf '%s\n' "$line" >> "$JSONL"
}

if [[ "$CASE_SCOPE" == "all" || "$CASE_SCOPE" == "baseline-quality" ]]; then
  run_case "baseline-quality" "0,1,2,3" "quality"
fi
if [[ "$CASE_SCOPE" == "all" || "$CASE_SCOPE" == "swap02-quality" ]]; then
  run_case "swap02-quality" "2,1,0,3" "quality"
fi
if [[ "$CASE_SCOPE" == "all" || "$CASE_SCOPE" == "baseline-greedy" ]]; then
  run_case "baseline-greedy" "0,1,2,3" "greedy"
fi

python3 - "$JSONL" "$CSV" <<'PY'
import csv, json, sys
from pathlib import Path
records = [json.loads(line) for line in Path(sys.argv[1]).read_text().splitlines() if line.strip()]
with Path(sys.argv[2]).open("w", newline="") as handle:
    writer = csv.writer(handle)
    writer.writerow([
        "diagnosticCase",
        "samplingMode",
        "promptSourceIndices",
        "outputsMatch",
        "outputTokensMatch",
        "mismatchSlots",
        "serialElapsedMs",
        "batchElapsedMs",
        "speedup",
        "maxObservedPssKb",
        "maxThermalStatus",
    ])
    for record in records:
        mismatch_slots = [index for index, matches in enumerate(record["matchingSlots"]) if not matches]
        writer.writerow([
            record["diagnosticCase"],
            record["samplingMode"],
            ",".join(str(value) for value in record["promptSourceIndices"]),
            str(record["outputsMatch"]).lower(),
            str(record["outputTokensMatch"]).lower(),
            ",".join(str(value) for value in mismatch_slots),
            record["serialElapsedMs"],
            record["batchElapsedMs"],
            f'{record["speedup"]:.6f}',
            max(record["processPssKbBefore"], record["processPssKbAfterSerial"], record["processPssKbAfterBatch"]),
            max(record["thermalStatusBefore"], record["thermalStatusAfterSerial"], record["thermalStatusAfterBatch"]),
        ])
PY

echo "LLRT-9 width=4 diagnostic: $JSONL"
echo "LLRT-9 width=4 summary:    $CSV"
echo "Diagnostic only: do not mark LLRT-9C DONE or promote runtime defaults from these records."
