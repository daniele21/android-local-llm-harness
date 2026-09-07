#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.phonetest"
MAIN_ACTIVITY="$APP_ID/.MainActivity"
MODEL_LABEL="Qwen 3.5 2B Q4_K_M"
EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
QUANTIZATION="Q4_K_M"
DEVICE=""
OUTPUT_DIR="$ROOT_DIR/build/q35-2b-q4-k-m-physical-acceptance"
ADB_BIN="${ADB:-adb}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-qwen35-2b-q4-k-m-physical-acceptance.sh [options]

Coordinates physical acceptance for the exact curated Qwen3.5 2B Q4_K_M
artifact already installed in Harnex on the connected Android device.

No GGUF path is required on the Mac. Harnex performs its existing host-owned
Physical-device validation against the selected ModelStore artifact. The runner
captures the privacy-safe report and screenshot through ADB, checks exact model
identity and required runtime evidence, and writes a bounded manifest locally.

Options:
  --device SERIAL       ADB device serial (alias: --serial).
  --output-dir PATH     Evidence root.
  --help                Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device|--serial)
            DEVICE="${2:-}"
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

command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }

ADB_CMD=("$ADB_BIN")
if [[ -n "$DEVICE" ]]; then
    ADB_CMD+=("-s" "$DEVICE")
fi
adb_shell() { "${ADB_CMD[@]}" shell "$@"; }

"${ADB_CMD[@]}" get-state >/dev/null
if ! adb_shell pm path "$APP_ID" 2>/dev/null | grep -q '^package:'; then
    echo "Harnex ($APP_ID) is not installed on the selected device." >&2
    exit 2
fi

DEVICE_MANUFACTURER="$(adb_shell getprop ro.product.manufacturer | tr -d '\r')"
DEVICE_MODEL="$(adb_shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$(adb_shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$(adb_shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$(adb_shell getprop ro.product.cpu.abi | tr -d '\r')"
DEVICE_SERIAL="${DEVICE:-$("${ADB_CMD[@]}" get-serialno | tr -d '\r')}"
DEVICE_RAM_KB="$(adb_shell cat /proc/meminfo | tr -d '\r' | awk '/^MemTotal:/ {print $2; exit}')"
[[ "$DEVICE_ABI" == arm64-v8a* ]] || { echo "Physical acceptance requires arm64-v8a; found $DEVICE_ABI" >&2; exit 2; }

PACKAGE_DUMP="$(adb_shell dumpsys package "$APP_ID" | tr -d '\r')"
APP_VERSION_NAME="$(printf '%s\n' "$PACKAGE_DUMP" | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1)"
APP_VERSION_CODE="$(printf '%s\n' "$PACKAGE_DUMP" | sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n 1)"

cd "$ROOT_DIR"
RUNNER_COMMIT="$(git rev-parse HEAD 2>/dev/null || printf 'unknown')"
RUN_STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
SAFE_MODEL="$(printf '%s' "$DEVICE_MODEL" | tr ' /:' '___')"
RUN_DIR="$OUTPUT_DIR/$SAFE_MODEL/$RUN_STAMP"
mkdir -p "$RUN_DIR"

{
    echo "deviceSerial=$DEVICE_SERIAL"
    echo "manufacturer=$DEVICE_MANUFACTURER"
    echo "model=$DEVICE_MODEL"
    echo "android=$DEVICE_RELEASE"
    echo "api=$DEVICE_SDK"
    echo "abi=$DEVICE_ABI"
    echo "ramKb=$DEVICE_RAM_KB"
    echo "harnexVersionName=${APP_VERSION_NAME:-unknown}"
    echo "harnexVersionCode=${APP_VERSION_CODE:-unknown}"
    echo "runnerCommit=$RUNNER_COMMIT"
} > "$RUN_DIR/device-and-runner.txt"

extract_visible_report() {
    python3 - "$1" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
root = ET.parse(sys.argv[1]).getroot()
texts = [n.attrib.get("text", "") for n in root.iter() if n.attrib.get("text", "")]
prefix = "LOCAL_LLM_PHONE_TEST result="
candidates = [t for t in texts if prefix in t]
if candidates:
    report = max(candidates, key=len)
else:
    combined = "\n".join(texts)
    start = combined.find(prefix)
    if start < 0:
        raise SystemExit("Could not find LOCAL_LLM_PHONE_TEST in visible UI")
    report = combined[start:]
Path(sys.argv[2]).write_text(report.replace("\r", "\n").strip() + "\n", encoding="utf-8")
PY
}

REPORT_FILE="$RUN_DIR/report.txt"
SCREENSHOT_FILE="$RUN_DIR/screenshot.png"
XML_REMOTE="/sdcard/harnex-q35-2b-q4-k-m-validation.xml"
XML_LOCAL="$RUN_DIR/ui.xml"

adb_shell am start -n "$MAIN_ACTIVITY" >/dev/null
cat <<EOF
Harnex physical acceptance — $MODEL_LABEL
Device: $DEVICE_MANUFACTURER $DEVICE_MODEL · Android $DEVICE_RELEASE · $DEVICE_ABI
Installed Harnex: ${APP_VERSION_NAME:-unknown} (${APP_VERSION_CODE:-unknown})
Evidence directory: $RUN_DIR

Sul telefono:
  1. Harnex > Models: seleziona $MODEL_LABEL già installato.
  2. Harnex > Diagnostics > Validation.
  3. Tocca 'Run full validation'.
  4. Attendi 'Validation completed' e lascia visibile il report.
EOF
printf "\nQuando il report è visibile, premi INVIO qui... "
IFS= read -r _

"${ADB_CMD[@]}" exec-out screencap -p > "$SCREENSHOT_FILE"
adb_shell uiautomator dump "$XML_REMOTE" >/dev/null
"${ADB_CMD[@]}" pull "$XML_REMOTE" "$XML_LOCAL" >/dev/null
adb_shell rm -f "$XML_REMOTE" >/dev/null 2>&1 || true
extract_visible_report "$XML_LOCAL" "$REPORT_FILE"

grep -Fq 'LOCAL_LLM_PHONE_TEST result=PASS' "$REPORT_FILE" || { cat "$REPORT_FILE" >&2; exit 1; }
grep -Fq "modelDigest=$EXPECTED_SHA" "$REPORT_FILE" || { echo "Wrong model digest; expected $EXPECTED_SHA" >&2; cat "$REPORT_FILE" >&2; exit 1; }
grep -Fq 'architecture=qwen35' "$REPORT_FILE" || { echo "Missing qwen35 architecture evidence" >&2; exit 1; }
grep -Fq "quantization=$QUANTIZATION" "$REPORT_FILE" || { echo "Wrong quantization; expected $QUANTIZATION" >&2; exit 1; }
for marker in 'ttftMs=' 'totalMs=' 'decodeTokensPerSecond=' 'cancellation=cancelled' 'pssSamplesKb=' 'pssGrowthKb=' 'thermalStart=' 'thermalEnd='; do
    grep -Fq "$marker" "$REPORT_FILE" || { echo "Missing evidence marker: $marker" >&2; exit 1; }
done

python3 - "$RUN_DIR/manifest.json" "$REPORT_FILE" "$SCREENSHOT_FILE" "$RUN_DIR/device-and-runner.txt" "$RUN_STAMP" <<'PY'
import hashlib, json, sys
from pathlib import Path
manifest, report, screenshot, provenance = map(Path, sys.argv[1:5])
stamp = sys.argv[5]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def parse(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("LOCAL_LLM_PHONE_TEST "):
            for part in line.split()[1:]:
                if "=" in part:
                    k, v = part.split("=", 1); values[k] = v
        elif "=" in line:
            k, v = line.split("=", 1); values[k] = v
    return values
payload = {
    "schemaVersion": 1,
    "lane": "qwen35-2b-q4-k-m-host-owned-physical-acceptance",
    "capturedAtUtc": stamp,
    "modelSource": "existing Harnex private ModelStore; no Mac-side GGUF transfer",
    "validationOwner": "Harnex PhoneTestController.runFullValidation",
    "report": parse(report),
    "artifacts": {"reportSha256": sha(report), "screenshotSha256": sha(screenshot), "deviceAndRunnerSha256": sha(provenance)},
}
manifest.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"Manifest: {manifest}")
print(f"Manifest SHA-256: {sha(manifest)}")
PY

echo
echo "Physical acceptance PASS for exact $MODEL_LABEL."
echo "Report: $REPORT_FILE"
echo "Evidence: $RUN_DIR"
