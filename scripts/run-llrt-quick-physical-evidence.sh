#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
LANE="batch"
CONTEXT=1024
MAX_OUTPUT_TOKENS=8
THERMAL_START_MAX=1
COOLDOWN_TIMEOUT_SECONDS=600
COOLDOWN_POLL_SECONDS=20
OPENCL_INCLUDE_DIR=""
OPENCL_LIBRARY=""
GPU_LAYERS="1"
KV_CASES="release-default,k-q8-fa-off"
OUTPUT_DIR="$ROOT_DIR/build/llrt-quick"
RESET_OUTPUT=false

usage() {
  cat <<'USAGE'
Usage: bash scripts/run-llrt-quick-physical-evidence.sh \
  --model /path/model.gguf --tier 0.8b|2b [options]

Runs a deliberately short physical-device screening profile over the integrated LLRT
physical-evidence runners. It is intended to catch correctness, integration and obvious
performance signals before the full curated 2048-context / 64-output qualification matrix.

This quick profile DOES NOT close LLRT-6C, LLRT-7C or LLRT-9C and MUST NOT promote a
runtime policy. Full qualification still uses the canonical runners directly.

Options:
  --device SERIAL                 Exact ADB serial; auto-resolved when one device is online.
  --lane all|batch|kv|opencl      Quick lane to run (default: batch).
  --context N                     Quick context (default: 1024).
  --max-output-tokens N           Short output budget (default: 8).
  --kv-cases CSV                  LLRT-6 cases (default: release-default,k-q8-fa-off).
  --opencl-include-dir PATH       OpenCL headers. With --lane all, OpenCL skips if absent.
  --opencl-library PATH           Optional AArch64 libOpenCL.so for LLRT-7.
  --gpu-layers CSV                LLRT-7 requested offload values (default: 1).
  --thermal-start-max N|off       Thermal gate before samples (default: 1).
  --cooldown-timeout-seconds N    Maximum thermal wait (default: 600).
  --cooldown-poll-seconds N       Thermal poll interval (default: 20).
  --output-dir PATH               Evidence root (default: build/llrt-quick).
  --reset-output                  Reset exact quick-run evidence before execution.
  --help                          Show this help.

Quick matrix:
  LLRT-9: width=2, repetitions=4 (minimum balanced gate), context=1024, output=8.
  LLRT-6: repetitions=3 (minimum gate), release default vs K q8_0/FA-off by default.
  LLRT-7: repetitions=3, CPU control + gpuLayers=1 by default, only when preflight eligible.

For a broader K/V smoke without running all six LLRT-6 cases:
  --kv-cases release-default,k-q8-fa-off,f16-f16-fa-on,q8-q8-fa-on
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL="${2:-}"; shift 2 ;;
    --tier) TIER="${2:-}"; shift 2 ;;
    --device) DEVICE="${2:-}"; shift 2 ;;
    --lane) LANE="${2:-}"; shift 2 ;;
    --context) CONTEXT="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --kv-cases) KV_CASES="${2:-}"; shift 2 ;;
    --opencl-include-dir) OPENCL_INCLUDE_DIR="${2:-}"; shift 2 ;;
    --opencl-library) OPENCL_LIBRARY="${2:-}"; shift 2 ;;
    --gpu-layers) GPU_LAYERS="${2:-}"; shift 2 ;;
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
[[ "$LANE" == "all" || "$LANE" == "batch" || "$LANE" == "kv" || "$LANE" == "opencl" ]] || {
  echo "--lane must be all, batch, kv or opencl" >&2
  exit 2
}
[[ "$CONTEXT" == "1024" || "$CONTEXT" == "2048" || "$CONTEXT" == "4096" || "$CONTEXT" == "8192" ]] || {
  echo "--context must be one of 1024, 2048, 4096, 8192" >&2
  exit 2
}
[[ "$MAX_OUTPUT_TOKENS" =~ ^[0-9]+$ ]] && (( MAX_OUTPUT_TOKENS > 0 )) || {
  echo "--max-output-tokens must be a positive integer" >&2
  exit 2
}
[[ "$THERMAL_START_MAX" == "off" || "$THERMAL_START_MAX" =~ ^[0-6]$ ]] || {
  echo "--thermal-start-max must be 0..6 or off" >&2
  exit 2
}
for value_name in COOLDOWN_TIMEOUT_SECONDS COOLDOWN_POLL_SECONDS; do
  value="${!value_name}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 )) || { echo "$value_name must be positive" >&2; exit 2; }
done

command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }

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

COMMON=(
  --model "$MODEL"
  --tier "$TIER"
  --device "$DEVICE"
  --context "$CONTEXT"
  --max-output-tokens "$MAX_OUTPUT_TOKENS"
  --thermal-start-max "$THERMAL_START_MAX"
  --cooldown-timeout-seconds "$COOLDOWN_TIMEOUT_SECONDS"
  --cooldown-poll-seconds "$COOLDOWN_POLL_SECONDS"
)
if [[ "$RESET_OUTPUT" == true ]]; then
  RESET=(--reset-output)
else
  RESET=()
fi

if [[ "$TIER" == "0.8b" ]]; then
  THREADS=2
  TIMEOUT_SECONDS=300
else
  THREADS=4
  TIMEOUT_SECONDS=600
fi

run_batch() {
  echo
  echo "== LLRT-9 quick native-batch screen =="
  bash "$ROOT_DIR/scripts/run-llama-cpp-evaluation-batch-evidence.sh" \
    "${COMMON[@]}" \
    --threads "$THREADS" \
    --batch-threads 4 \
    --batch 128 \
    --ubatch 64 \
    --timeout-seconds "$TIMEOUT_SECONDS" \
    --repetitions 4 \
    --width 2 \
    --output-dir "$OUTPUT_DIR/llrt9" \
    "${RESET[@]}"
}

run_kv() {
  echo
  echo "== LLRT-6 quick KV-cache screen =="
  bash "$ROOT_DIR/scripts/run-llama-cpp-kv-cache-evidence.sh" \
    "${COMMON[@]}" \
    --threads "$THREADS" \
    --batch-threads 4 \
    --batch 128 \
    --ubatch 64 \
    --timeout-seconds "$TIMEOUT_SECONDS" \
    --repetitions 3 \
    --seed 42 \
    --thinking-mode DISABLED \
    --case "$KV_CASES" \
    --output-dir "$OUTPUT_DIR/llrt6" \
    "${RESET[@]}"
}

opencl_eligible() {
  python3 "$ROOT_DIR/scripts/llrt7_opencl_device_preflight.py" --device "$DEVICE" >/dev/null 2>&1
}

run_opencl() {
  if [[ -z "$OPENCL_INCLUDE_DIR" ]]; then
    if [[ "$LANE" == "opencl" ]]; then
      echo "--opencl-include-dir is required for --lane opencl" >&2
      exit 2
    fi
    echo
    echo "== LLRT-7 quick OpenCL screen: SKIPPED =="
    echo "No --opencl-include-dir supplied. CPU/KV/batch quick evidence remains valid."
    return
  fi

  if ! opencl_eligible; then
    if [[ "$LANE" == "opencl" ]]; then
      echo "LLRT-7 OpenCL preflight is not eligible on device $DEVICE" >&2
      exit 1
    fi
    echo
    echo "== LLRT-7 quick OpenCL screen: SKIPPED =="
    echo "Representative OpenCL preflight is not eligible/proven on device $DEVICE."
    return
  fi

  echo
  echo "== LLRT-7 quick OpenCL screen =="
  OPENCL_ARGS=(
    --opencl-include-dir "$OPENCL_INCLUDE_DIR"
    --gpu-layers "$GPU_LAYERS"
  )
  if [[ -n "$OPENCL_LIBRARY" ]]; then
    OPENCL_ARGS+=(--opencl-library "$OPENCL_LIBRARY")
  fi

  bash "$ROOT_DIR/scripts/run-llama-cpp-opencl-evidence.sh" \
    "${COMMON[@]}" \
    "${OPENCL_ARGS[@]}" \
    --threads "$THREADS" \
    --batch-threads 4 \
    --batch 128 \
    --ubatch 64 \
    --timeout-seconds "$TIMEOUT_SECONDS" \
    --repetitions 3 \
    --seed 42 \
    --thinking-mode DISABLED \
    --output-dir "$OUTPUT_DIR/llrt7" \
    "${RESET[@]}"
}

case "$LANE" in
  batch) run_batch ;;
  kv) run_kv ;;
  opencl) run_opencl ;;
  all)
    run_batch
    run_kv
    run_opencl
    ;;
esac

echo
echo "Quick physical screening completed."
echo "  device: $DEVICE"
echo "  tier:   $TIER"
echo "  output: $OUTPUT_DIR"
echo
echo "These quick runs are diagnostic only. Do not mark LLRT-6C/7C/9C DONE or promote"
echo "runtime defaults from this profile; use the full canonical evidence matrices next."
