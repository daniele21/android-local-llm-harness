#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-adb}"

MODEL_08=""
MODEL_2=""
DEVICE=""
CONTEXT=1024
MAX_OUTPUT_TOKENS=8
KV_CASES="release-default,k-q8-fa-off"
THERMAL_START_MAX=1
COOLDOWN_TIMEOUT_SECONDS=600
COOLDOWN_POLL_SECONDS=20
OPENCL_INCLUDE_DIR=""
OPENCL_LIBRARY=""
GPU_LAYERS="1"
OPENCL_TIER="auto"
OUTPUT_ROOT="$ROOT_DIR/build/llrt-suite"
RUN_ID=""

usage() {
  cat <<'USAGE'
Usage:
  bash scripts/run-llrt-device-suite.sh \
    --model-0.8b /path/Qwen3.5-0.8B-Q4_K_M.gguf \
    [--model-2b /path/Qwen3.5-2B-Q4_K_M.gguf] [options]

Runs the bounded LLRT physical-device screening suite from one command and renders
both Markdown and standalone HTML reports.

At least one curated model is required. When both model paths are supplied, batch
and KV screening run for both tiers. OpenCL is optional and defaults to the 0.8B
tier (or the only supplied tier) to keep the quick suite bounded.

This suite is diagnostic screening only. It DOES NOT close LLRT-6C, LLRT-7C or
LLRT-9C and MUST NOT promote runtime defaults. Full qualification still uses the
canonical 2048-context / 64-output evidence matrices.

Options:
  --model-0.8b PATH               Curated Qwen3.5 0.8B Q4_K_M GGUF.
  --model-2b PATH                 Curated Qwen3.5 2B Q4_K_M GGUF.
  --device SERIAL                 Exact ADB serial; auto-resolved when one device is online.
  --context N                     Quick context (default: 1024).
  --max-output-tokens N           Quick output budget (default: 8).
  --kv-cases CSV                  LLRT-6 cases (default: release-default,k-q8-fa-off).
  --opencl-include-dir PATH       OpenCL headers. If omitted, OpenCL is reported SKIPPED.
  --opencl-library PATH           Optional AArch64 libOpenCL.so for LLRT-7.
  --gpu-layers CSV                Requested OpenCL offload values (default: 1).
  --opencl-tier auto|0.8b|2b|both|none
                                  Tier(s) used for OpenCL screening (default: auto).
  --thermal-start-max N|off       Thermal gate before samples (default: 1).
  --cooldown-timeout-seconds N    Maximum thermal wait (default: 600).
  --cooldown-poll-seconds N       Thermal polling interval (default: 20).
  --output-dir PATH               Suite output root (default: build/llrt-suite).
  --run-id ID                     Reuse/resume a named run directory.
  --help                          Show this help.

Quick matrix:
  LLRT-9: width=2, repetitions=4, fixed seed, context=1024, output=8 by default.
  LLRT-6: repetitions=3, release default vs K q8_0/FA-off by default.
  LLRT-7: repetitions=3, CPU control + requested gpuLayers=1 by default.

Reports:
  <output-dir>/<run-id>/report.md
  <output-dir>/<run-id>/report.html
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-0.8b) MODEL_08="${2:-}"; shift 2 ;;
    --model-2b) MODEL_2="${2:-}"; shift 2 ;;
    --device) DEVICE="${2:-}"; shift 2 ;;
    --context) CONTEXT="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --kv-cases) KV_CASES="${2:-}"; shift 2 ;;
    --opencl-include-dir) OPENCL_INCLUDE_DIR="${2:-}"; shift 2 ;;
    --opencl-library) OPENCL_LIBRARY="${2:-}"; shift 2 ;;
    --gpu-layers) GPU_LAYERS="${2:-}"; shift 2 ;;
    --opencl-tier) OPENCL_TIER="${2:-}"; shift 2 ;;
    --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
    --cooldown-timeout-seconds) COOLDOWN_TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --cooldown-poll-seconds) COOLDOWN_POLL_SECONDS="${2:-}"; shift 2 ;;
    --output-dir) OUTPUT_ROOT="${2:-}"; shift 2 ;;
    --run-id) RUN_ID="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$MODEL_08" && -z "$MODEL_2" ]]; then
  echo "At least one of --model-0.8b or --model-2b is required" >&2
  exit 2
fi
if [[ -n "$MODEL_08" && (! -f "$MODEL_08" || ! -r "$MODEL_08") ]]; then
  echo "--model-0.8b must point to a readable GGUF file" >&2
  exit 2
fi
if [[ -n "$MODEL_2" && (! -f "$MODEL_2" || ! -r "$MODEL_2") ]]; then
  echo "--model-2b must point to a readable GGUF file" >&2
  exit 2
fi
if [[ "$CONTEXT" != "1024" && "$CONTEXT" != "2048" && "$CONTEXT" != "4096" && "$CONTEXT" != "8192" ]]; then
  echo "--context must be one of 1024, 2048, 4096, 8192" >&2
  exit 2
fi
if [[ ! "$MAX_OUTPUT_TOKENS" =~ ^[0-9]+$ ]] || (( MAX_OUTPUT_TOKENS < 1 )); then
  echo "--max-output-tokens must be a positive integer" >&2
  exit 2
fi
if [[ "$OPENCL_TIER" != "auto" && "$OPENCL_TIER" != "0.8b" && "$OPENCL_TIER" != "2b" && "$OPENCL_TIER" != "both" && "$OPENCL_TIER" != "none" ]]; then
  echo "--opencl-tier must be auto, 0.8b, 2b, both or none" >&2
  exit 2
fi
if [[ "$THERMAL_START_MAX" != "off" && ! "$THERMAL_START_MAX" =~ ^[0-6]$ ]]; then
  echo "--thermal-start-max must be 0..6 or off" >&2
  exit 2
fi
for value_name in COOLDOWN_TIMEOUT_SECONDS COOLDOWN_POLL_SECONDS; do
  value="${!value_name}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < 1 )); then
    echo "$value_name must be a positive integer" >&2
    exit 2
  fi
done
if [[ -n "$RUN_ID" && ! "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "--run-id may contain only letters, digits, dot, underscore and dash" >&2
  exit 2
fi

command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }
command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 2; }

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

echo "== Physical-device preflight =="
if ! bash "$ROOT_DIR/scripts/llrt-device-preflight.sh" --device "$DEVICE"; then
  echo "Device preflight failed; no LLRT suite was executed." >&2
  exit 1
fi

cd "$ROOT_DIR"
HARNESS_COMMIT="$(git rev-parse HEAD)"
HARNESS_SHORT="$(git rev-parse --short=12 HEAD)"
if [[ -z "$RUN_ID" ]]; then
  RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$HARNESS_SHORT"
fi
OUTPUT_ROOT="$(python3 - "$OUTPUT_ROOT" <<'PY'
from pathlib import Path
import sys
print(Path(sys.argv[1]).expanduser().resolve())
PY
)"
RUN_DIR="$OUTPUT_ROOT/$RUN_ID"
EVIDENCE_DIR="$RUN_DIR/evidence"
STATUS_TSV="$RUN_DIR/lane-status.tsv"
MANIFEST_JSON="$RUN_DIR/run.json"
REPORT_MD="$RUN_DIR/report.md"
REPORT_HTML="$RUN_DIR/report.html"
mkdir -p "$EVIDENCE_DIR"
printf 'tier\tlane\tstatus\texitCode\tevidenceRoot\tnote\n' > "$STATUS_TSV"

ADB_CMD=("$ADB_BIN" "-s" "$DEVICE")
DEVICE_MANUFACTURER="$("${ADB_CMD[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_SOC="$("${ADB_CMD[@]}" shell getprop ro.soc.model | tr -d '\r')"
[[ -n "$DEVICE_SOC" ]] || DEVICE_SOC="$("${ADB_CMD[@]}" shell getprop ro.hardware | tr -d '\r')"
DEVICE_ANDROID="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"

MODEL_08_NAME=""
MODEL_2_NAME=""
[[ -n "$MODEL_08" ]] && MODEL_08_NAME="$(basename "$MODEL_08")"
[[ -n "$MODEL_2" ]] && MODEL_2_NAME="$(basename "$MODEL_2")"

python3 - "$MANIFEST_JSON" "$RUN_ID" "$HARNESS_COMMIT" "$DEVICE" "$DEVICE_MANUFACTURER" "$DEVICE_MODEL" \
  "$DEVICE_SOC" "$DEVICE_ANDROID" "$DEVICE_SDK" "$DEVICE_ABI" "$CONTEXT" "$MAX_OUTPUT_TOKENS" \
  "$KV_CASES" "$GPU_LAYERS" "$OPENCL_TIER" "$MODEL_08_NAME" "$MODEL_2_NAME" <<'PY'
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

(
    output, run_id, commit, serial, manufacturer, model, soc, android, sdk, abi,
    context, max_output, kv_cases, gpu_layers, opencl_tier, model_08, model_2
) = sys.argv[1:]
record = {
    "schemaVersion": 1,
    "suite": "LLRT_QUICK_PHYSICAL_SCREENING",
    "runId": run_id,
    "startedAtUtc": datetime.now(timezone.utc).isoformat(),
    "harnessCommit": commit,
    "device": {
        "serial": serial,
        "manufacturer": manufacturer or "unknown",
        "model": model or "unknown",
        "soc": soc or "unknown",
        "androidRelease": android or "unknown",
        "sdkInt": int(sdk) if sdk.isdigit() else sdk,
        "abi": abi or "unknown",
    },
    "profile": {
        "contextTokens": int(context),
        "maxOutputTokens": int(max_output),
        "batchWidth": 2,
        "batchRepetitions": 4,
        "kvRepetitions": 3,
        "kvCases": kv_cases.split(","),
        "openClRepetitions": 3,
        "requestedGpuLayers": gpu_layers,
        "openClTier": opencl_tier,
        "thinkingMode": "DISABLED",
        "generationSeed": 42,
    },
    "models": {
        "0.8b": model_08 or None,
        "2b": model_2 or None,
    },
    "evidenceSemantics": "diagnostic-screening-only",
}
Path(output).write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
PY

sanitize_note() {
  printf '%s' "$1" | tr '\t\r\n' '   '
}

record_status() {
  local tier="$1"
  local lane="$2"
  local status="$3"
  local exit_code="$4"
  local evidence_root="$5"
  local note="$6"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$tier" "$lane" "$status" "$exit_code" "$evidence_root" "$(sanitize_note "$note")" >> "$STATUS_TSV"
}

run_lane() {
  local tier="$1"
  local lane="$2"
  local model_path="$3"
  local lane_root="$EVIDENCE_DIR/$tier/$lane"
  local rc
  mkdir -p "$lane_root"

  echo
  echo "================================================================"
  echo "LLRT quick suite: tier=$tier lane=$lane"
  echo "================================================================"

  args=(
    bash "$ROOT_DIR/scripts/run-llrt-quick-physical-evidence.sh"
    --model "$model_path"
    --tier "$tier"
    --device "$DEVICE"
    --lane "$lane"
    --context "$CONTEXT"
    --max-output-tokens "$MAX_OUTPUT_TOKENS"
    --kv-cases "$KV_CASES"
    --thermal-start-max "$THERMAL_START_MAX"
    --cooldown-timeout-seconds "$COOLDOWN_TIMEOUT_SECONDS"
    --cooldown-poll-seconds "$COOLDOWN_POLL_SECONDS"
    --output-dir "$lane_root"
  )

  if [[ "$lane" == "opencl" ]]; then
    args+=(--opencl-include-dir "$OPENCL_INCLUDE_DIR" --gpu-layers "$GPU_LAYERS")
    [[ -n "$OPENCL_LIBRARY" ]] && args+=(--opencl-library "$OPENCL_LIBRARY")
  fi

  "${args[@]}"
  rc=$?
  if (( rc == 0 )); then
    record_status "$tier" "$lane" "PASS" "$rc" "$lane_root" "canonical quick runner completed"
  else
    record_status "$tier" "$lane" "FAIL" "$rc" "$lane_root" "canonical quick runner failed; inspect terminal output and partial evidence"
  fi
  return 0
}

run_required_tier() {
  local tier="$1"
  local model_path="$2"
  run_lane "$tier" "batch" "$model_path"
  run_lane "$tier" "kv" "$model_path"
}

if [[ -n "$MODEL_08" ]]; then
  run_required_tier "0.8b" "$MODEL_08"
fi
if [[ -n "$MODEL_2" ]]; then
  run_required_tier "2b" "$MODEL_2"
fi

OPENCL_SELECTED=()
case "$OPENCL_TIER" in
  none)
    ;;
  auto)
    if [[ -n "$MODEL_08" ]]; then
      OPENCL_SELECTED=("0.8b")
    elif [[ -n "$MODEL_2" ]]; then
      OPENCL_SELECTED=("2b")
    fi
    ;;
  0.8b)
    OPENCL_SELECTED=("0.8b")
    ;;
  2b)
    OPENCL_SELECTED=("2b")
    ;;
  both)
    [[ -n "$MODEL_08" ]] && OPENCL_SELECTED+=("0.8b")
    [[ -n "$MODEL_2" ]] && OPENCL_SELECTED+=("2b")
    ;;
esac

OPENCL_ELIGIBLE=false
if [[ ${#OPENCL_SELECTED[@]} -gt 0 && -n "$OPENCL_INCLUDE_DIR" ]]; then
  if python3 "$ROOT_DIR/scripts/llrt7_opencl_device_preflight.py" --device "$DEVICE" >/dev/null 2>&1; then
    OPENCL_ELIGIBLE=true
  fi
fi

for tier in "${OPENCL_SELECTED[@]}"; do
  if [[ "$tier" == "0.8b" ]]; then
    selected_model="$MODEL_08"
  else
    selected_model="$MODEL_2"
  fi
  lane_root="$EVIDENCE_DIR/$tier/opencl"
  mkdir -p "$lane_root"

  if [[ -z "$selected_model" ]]; then
    record_status "$tier" "opencl" "SKIP" "0" "$lane_root" "requested OpenCL tier has no model path"
  elif [[ -z "$OPENCL_INCLUDE_DIR" ]]; then
    record_status "$tier" "opencl" "SKIP" "0" "$lane_root" "OpenCL headers not supplied"
  elif [[ "$OPENCL_ELIGIBLE" != true ]]; then
    record_status "$tier" "opencl" "SKIP" "0" "$lane_root" "representative OpenCL device preflight not eligible/proven"
  else
    run_lane "$tier" "opencl" "$selected_model"
  fi
done

render_reports() {
  python3 "$ROOT_DIR/scripts/render-llrt-device-suite-report.py" \
    "$MANIFEST_JSON" "$STATUS_TSV" "$RUN_DIR" "$REPORT_MD" "$REPORT_HTML"
}

echo
echo "== Rendering suite report =="
render_reports

FAIL_COUNT="$(awk -F '\t' 'NR > 1 && $3 == "FAIL" {count++} END {print count+0}' "$STATUS_TSV")"
SKIP_COUNT="$(awk -F '\t' 'NR > 1 && $3 == "SKIP" {count++} END {print count+0}' "$STATUS_TSV")"

echo
echo "LLRT quick physical-device suite completed."
echo "  run:      $RUN_DIR"
echo "  report:   $REPORT_MD"
echo "  html:     $REPORT_HTML"
echo "  failures: $FAIL_COUNT"
echo "  skipped:  $SKIP_COUNT"
echo
echo "Quick screening is diagnostic only; full LLRT qualification remains evidence-gated."

if (( FAIL_COUNT > 0 )); then
  exit 1
fi
exit 0
