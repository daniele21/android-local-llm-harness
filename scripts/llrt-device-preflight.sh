#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-adb}"
DEVICE=""
REQUIRE_OPENCL=false

usage() {
  cat <<'USAGE'
Usage: bash scripts/llrt-device-preflight.sh [--device SERIAL] [--require-opencl]

Checks the Android phone connected through ADB before LLRT physical evidence.
The script rejects emulators and non-arm64 devices. OpenCL support is reported
as a capability and only becomes mandatory with --require-opencl.

Options:
  --device SERIAL       Exact ADB serial. Optional when exactly one device is online.
  --require-opencl      Fail when LLRT-7 OpenCL representative preflight is not eligible.
  --help                Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device) DEVICE="${2:-}"; shift 2 ;;
    --require-opencl) REQUIRE_OPENCL=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
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

ADB_CMD=("$ADB_BIN" "-s" "$DEVICE")
STATE="$("${ADB_CMD[@]}" get-state | tr -d '\r')"
[[ "$STATE" == "device" ]] || { echo "ADB device is not ready: $STATE" >&2; exit 2; }

KERNEL_QEMU="$("${ADB_CMD[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
if [[ "$DEVICE" == emulator-* || "$KERNEL_QEMU" == "1" ]]; then
  echo "Physical evidence rejects emulators: $DEVICE" >&2
  exit 2
fi

ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$ABI" == arm64-v8a* ]] || { echo "LLRT physical evidence requires arm64-v8a; got $ABI" >&2; exit 2; }

MANUFACTURER="$("${ADB_CMD[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
SOC="$("${ADB_CMD[@]}" shell getprop ro.soc.model | tr -d '\r')"
[[ -n "$SOC" ]] || SOC="$("${ADB_CMD[@]}" shell getprop ro.hardware | tr -d '\r')"

BATTERY_RAW="$("${ADB_CMD[@]}" shell dumpsys battery 2>/dev/null | tr -d '\r' || true)"
BATTERY_LEVEL="$(printf '%s\n' "$BATTERY_RAW" | awk -F: '/^[[:space:]]*level:/ {gsub(/[[:space:]]/,"",$2); print $2; exit}')"
BATTERY_TEMP_TENTHS="$(printf '%s\n' "$BATTERY_RAW" | awk -F: '/^[[:space:]]*temperature:/ {gsub(/[[:space:]]/,"",$2); print $2; exit}')"
BATTERY_STATUS="$(printf '%s\n' "$BATTERY_RAW" | awk -F: '/^[[:space:]]*status:/ {gsub(/[[:space:]]/,"",$2); print $2; exit}')"

if [[ "$BATTERY_TEMP_TENTHS" =~ ^[0-9]+$ ]]; then
  BATTERY_TEMP_C="$(python3 - "$BATTERY_TEMP_TENTHS" <<'PY'
import sys
print(f"{int(sys.argv[1]) / 10:.1f}")
PY
)"
else
  BATTERY_TEMP_C="unavailable"
fi

echo "LLRT physical-device preflight"
echo "  serial:       $DEVICE"
echo "  manufacturer: ${MANUFACTURER:-unknown}"
echo "  model:        ${MODEL:-unknown}"
echo "  SoC:          ${SOC:-unknown}"
echo "  Android:      ${RELEASE:-unknown} (SDK ${SDK:-unknown})"
echo "  ABI:          $ABI"
echo "  battery:      ${BATTERY_LEVEL:-unknown}%"
echo "  battery temp: ${BATTERY_TEMP_C} C"
echo "  battery stat: ${BATTERY_STATUS:-unknown}"

TMP_OPENCL="${TMPDIR:-/tmp}/llrt-opencl-preflight.$$"
trap 'rm -f "$TMP_OPENCL"' EXIT
OPENCL_ELIGIBLE=false
if python3 "$ROOT_DIR/scripts/llrt7_opencl_device_preflight.py" --device "$DEVICE" >"$TMP_OPENCL" 2>&1; then
  OPENCL_ELIGIBLE=true
  echo "  LLRT-7 OpenCL: eligible for representative qualification"
else
  echo "  LLRT-7 OpenCL: not eligible / not proven on this device"
fi
if [[ -s "$TMP_OPENCL" ]]; then
  sed 's/^/    /' "$TMP_OPENCL"
fi

if [[ "$REQUIRE_OPENCL" == true && "$OPENCL_ELIGIBLE" != true ]]; then
  exit 1
fi

echo
echo "Preflight OK for CPU/KV/batching physical evidence."
echo "Use --device $DEVICE in subsequent evidence commands."
