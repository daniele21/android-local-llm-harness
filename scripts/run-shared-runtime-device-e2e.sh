#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_SERIAL=""
ADB_PATH="${ADB:-}"
CLEANUP="false"

HOST_PACKAGE="io.github.daniele21.localllm.phonetest.debug"
CLIENT_PACKAGE="io.github.daniele21.localllm.console.debug"
TEST_CLASS="io.github.daniele21.localllm.console.SharedRuntimeTwoApkE2eTest"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-shared-runtime-device-e2e.sh [options]

Options:
  --device SERIAL    Exact ADB device/emulator serial. Required when more than one device is online.
  --adb PATH         Explicit adb binary.
  --cleanup          Uninstall Console/test APK after the run. The host and its model state are retained.
  --help, -h         Show this help.

Precondition:
  The phone-test host must already contain an explicitly installed and selected curated Qwen3.5 model.
  The runner never copies, downloads, or discovers host-private GGUF paths.

The script builds the debug host and Console from the same checkout/signing configuration, installs the host,
and runs Console instrumentation from a separately installed APK across the real Binder process boundary.
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

export ANDROID_SERIAL="$DEVICE_SERIAL"
cd "$ROOT_DIR"

echo "Shared runtime SR-4 two-APK preflight"
echo "Device: $DEVICE_SERIAL ($DEVICE_KIND)"
echo "Host:   $HOST_PACKAGE"
echo "Client: $CLIENT_PACKAGE"
echo ""
echo "Precondition: a curated Qwen3.5 model must already be installed and selected in the host."

echo "Building and installing the proof host..."
./gradlew :apps:local-llm-phone-test:installDebug

if ! "${ADB_CMD[@]}" shell pm path "$HOST_PACKAGE" >/dev/null 2>&1; then
    echo "Error: proof host installation was not visible to package manager." >&2
    exit 1
fi

echo "Building Console and instrumentation APK..."
./gradlew \
    :apps:local-llm-console:assembleDebug \
    :apps:local-llm-console:assembleDebugAndroidTest

echo "Running the SR-4 functional slice through the separately installed Console APK..."
./gradlew \
    :apps:local-llm-console:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS"

REPORT_DIR="$ROOT_DIR/build/reports/shared-runtime"
mkdir -p "$REPORT_DIR"
REPORT="$REPORT_DIR/sr4-two-apk-preflight.txt"
{
    echo "result=PASS"
    echo "device_kind=$DEVICE_KIND"
    echo "device_serial=$DEVICE_SERIAL"
    echo "host_package=$HOST_PACKAGE"
    echo "client_package=$CLIENT_PACKAGE"
    echo "test_class=$TEST_CLASS"
    echo "git_commit=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
} > "$REPORT"

echo "SR-4 preflight passed. Privacy-safe summary: $REPORT"

if [[ "$CLEANUP" == "true" ]]; then
    echo "Cleaning Console APK while retaining host/model state..."
    "${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true
fi
