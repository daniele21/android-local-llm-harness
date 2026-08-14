#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_SERIAL=""
ADB_PATH="${ADB:-}"
CLEANUP="false"

APP_PACKAGE="io.github.daniele21.localllm.console.debug"
TEST_CLASS="io.github.daniele21.localllm.console.document.OmbraPdfBoxFallbackInstrumentedTest"
REPORT_DIR="$ROOT_DIR/build/reports/ombra"
RUNTIME_LOG="$REPORT_DIR/omb0-pdf-runtime-gradle.log"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-ombra-pdf-spike-device-e2e.sh [options]

Options:
  --device SERIAL    Exact ADB device/emulator serial. Required when more than one device is online.
  --adb PATH         Explicit adb binary.
  --cleanup          Uninstall the OMBRA/Console debug APK after the run.
  --help, -h         Show this help.

The test is self-contained and does not require a Harness host or model. It generates synthetic
PDFs in app-private cache, exercises the active OMB-0 parser fidelity spike, and records only
privacy-safe device/build metadata.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)
            DEVICE_SERIAL="${2:-}"
            shift 2
            ;;
        --adb)
            ADB_PATH="${2:-}"
            shift 2
            ;;
        --cleanup)
            CLEANUP="true"
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown argument '$1'" >&2
            usage >&2
            exit 2
            ;;
    esac
done

resolve_adb() {
    local candidates=(
        "$ADB_PATH"
        "$(command -v adb 2>/dev/null || true)"
        "${ANDROID_HOME:-}/platform-tools/adb"
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
        "$HOME/Library/Android/sdk/platform-tools/adb"
        "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
    )
    local candidate
    for candidate in "${candidates[@]}"; do
        if [[ -n "$candidate" && -x "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

ADB_BIN="$(resolve_adb || true)"
if [[ -z "$ADB_BIN" ]]; then
    echo "Error: adb was not found. Install Android platform-tools or pass --adb PATH." >&2
    exit 1
fi

DEVICES=()
while IFS= read -r device; do
    [[ -n "$device" ]] && DEVICES+=("$device")
done < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "Error: no online ADB device or emulator is available." >&2
    exit 1
fi
if [[ -z "$DEVICE_SERIAL" ]]; then
    if [[ ${#DEVICES[@]} -ne 1 ]]; then
        echo "Error: ${#DEVICES[@]} devices are online; select one with --device SERIAL." >&2
        exit 1
    fi
    DEVICE_SERIAL="${DEVICES[0]}"
fi
if ! printf '%s\n' "${DEVICES[@]}" | grep -Fxq "$DEVICE_SERIAL"; then
    echo "Error: requested device '$DEVICE_SERIAL' is not online." >&2
    exit 1
fi

ADB_CMD=("$ADB_BIN" -s "$DEVICE_SERIAL")
DEVICE_KIND="physical"
if [[ "$("${ADB_CMD[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
    DEVICE_KIND="emulator"
fi
SDK_INT="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ -z "$SDK_INT" || "$SDK_INT" -lt 28 ]]; then
    echo "Error: OMBRA PDF spike requires Android API 28 or newer; device reports '$SDK_INT'." >&2
    exit 1
fi

export ANDROID_SERIAL="$DEVICE_SERIAL"
cd "$ROOT_DIR"
mkdir -p "$REPORT_DIR"
: > "$RUNTIME_LOG"

echo "OMB-0 PDF runtime spike"
echo "Device: $DEVICE_SERIAL ($DEVICE_KIND, API $SDK_INT)"
echo "App:    $APP_PACKAGE"

echo "Building OMBRA/Console and instrumentation APKs..."
./gradlew \
    :apps:local-llm-console:assembleDebug \
    :apps:local-llm-console:assembleDebugAndroidTest \
    2>&1 | tee -a "$RUNTIME_LOG"

echo "Running active parser fidelity fixture evidence..."
./gradlew \
    :apps:local-llm-console:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
    2>&1 | tee -a "$RUNTIME_LOG"

REPORT="$REPORT_DIR/omb0-pdf-runtime-spike.txt"
{
    echo "result=PASS"
    echo "device_kind=$DEVICE_KIND"
    echo "device_serial=$DEVICE_SERIAL"
    echo "sdk_int=$SDK_INT"
    echo "app_package=$APP_PACKAGE"
    echo "test_class=$TEST_CLASS"
    echo "git_commit=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
} > "$REPORT"

echo "OMB-0 PDF runtime spike passed. Privacy-safe summary: $REPORT"

if [[ "$CLEANUP" == "true" ]]; then
    echo "Cleaning OMBRA/Console debug APK..."
    "${ADB_CMD[@]}" uninstall "$APP_PACKAGE" >/dev/null 2>&1 || true
fi
