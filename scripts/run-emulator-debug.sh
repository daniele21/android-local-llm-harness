#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_TARGET="console"
DEVICE_SERIAL=""
SHOW_LOGS="false"
ADB_PATH="${ADB:-}"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/run-emulator-debug.sh [options]

Options:
  --app TARGET       Target app to build and launch: console | phone-test | device-test (default: console)
  --device SERIAL    Specific ADB device or emulator serial (default: auto-detect first connected device)
  --adb PATH         Custom absolute path to the adb binary
  --logs             Follow adb logcat output after launching
  --help, -h         Show this help message

Examples:
  # Build, install, and launch local-llm-console on the running emulator
  bash scripts/run-emulator-debug.sh

  # Build, install, and launch Harnex (local-llm-phone-test)
  bash scripts/run-emulator-debug.sh --app phone-test

  # Specify custom ADB binary path
  bash scripts/run-emulator-debug.sh --adb /opt/homebrew/share/android-commandlinetools/platform-tools/adb
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --app)
            APP_TARGET="${2:-}"
            shift 2
            ;;
        --device)
            DEVICE_SERIAL="${2:-}"
            shift 2
            ;;
        --adb)
            ADB_PATH="${2:-}"
            shift 2
            ;;
        --logs|--logcat)
            SHOW_LOGS="true"
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Error: Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

case "$APP_TARGET" in
    console)
        GRADLE_TASK=":apps:local-llm-console:installDebug"
        APP_ID="io.github.daniele21.localllm.console.debug"
        APP_NAME="Local LLM Console (Debug)"
        ;;
    phone-test)
        GRADLE_TASK=":apps:local-llm-phone-test:installDebug"
        APP_ID="io.github.daniele21.localllm.phonetest.debug"
        APP_NAME="Harnex (Debug)"
        ;;
    device-test)
        GRADLE_TASK=":apps:device-test-runner:installDebug"
        APP_ID="io.github.daniele21.localllm.devicetest.debug"
        APP_NAME="Device Test Runner (Debug)"
        ;;
    *)
        echo "Error: Invalid target '$APP_TARGET'. Choose from: console, phone-test, device-test" >&2
        exit 2
        ;;
esac

echo "===================================================="
echo "  Harnex — Android Emulator Debug Runner"
echo "===================================================="
echo "Target App : $APP_NAME ($APP_ID)"

# 1. Resolve ANDROID_HOME if not defined
if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -d "/opt/homebrew/share/android-commandlinetools" ]]; then
        export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
    elif [[ -d "$HOME/Library/Android/sdk" ]]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    fi
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    echo "Android SDK: $ANDROID_HOME"
fi

# 2. Resolve ADB location
ADB=""
POSSIBLE_ADB_PATHS=(
    "$ADB_PATH"
    "$(command -v adb 2>/dev/null || true)"
    "${ANDROID_HOME:-}/platform-tools/adb"
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
    "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
    "$HOME/Library/Android/sdk/platform-tools/adb"
    "/opt/homebrew/bin/adb"
    "/usr/local/bin/adb"
)

for p in "${POSSIBLE_ADB_PATHS[@]}"; do
    if [[ -n "$p" && -x "$p" ]]; then
        ADB="$p"
        break
    fi
done

if [[ -z "$ADB" ]]; then
    echo "" >&2
    echo "Error: 'adb' command not found." >&2
    echo "Assicurati che Android SDK platform-tools sia installato e nel tuo PATH." >&2
    echo "Puoi specificare il percorso di adb in uno dei seguenti modi:" >&2
    echo "  1) export PATH=\"\$PATH:/opt/homebrew/share/android-commandlinetools/platform-tools\"" >&2
    echo "  2) ADB=/percorso/a/adb bash scripts/run-emulator-debug.sh" >&2
    echo "  3) bash scripts/run-emulator-debug.sh --adb /percorso/a/adb" >&2
    exit 1
fi

echo "ADB Binary : $ADB"

# 3. Check running devices/emulators
echo "Checking connected devices/emulators..."
DEVICES=($("$ADB" devices | grep -v "List of devices attached" | grep -v "^$" | awk '{print $1}'))

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "Error: No online ADB devices or emulators found." >&2
    echo "Assicurati che l'emulatore sia avviato e visibile in 'adb devices'." >&2
    exit 1
fi

if [[ -z "$DEVICE_SERIAL" ]]; then
    DEVICE_SERIAL="${DEVICES[0]}"
fi

echo "Using Device : $DEVICE_SERIAL"
ADB_CMD=("$ADB" -s "$DEVICE_SERIAL")

# 4. Build & Install Debug APK via Gradle
echo "Building and installing debug build via Gradle ($GRADLE_TASK)..."
cd "$ROOT_DIR"
./gradlew "$GRADLE_TASK"

# 5. Launch the app on the emulator
echo "Launching $APP_NAME on $DEVICE_SERIAL..."
"${ADB_CMD[@]}" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || {
    echo "Warning: monkey launch did not succeed. Attempting fallback launch..."
    "${ADB_CMD[@]}" shell am start -n "$APP_ID/$APP_ID.MainActivity"
}

echo "✅ App successfully launched on $DEVICE_SERIAL!"

# 6. Follow logs if requested
if [[ "$SHOW_LOGS" == "true" ]]; then
    echo "Streaming logcat output for package '$APP_ID' (Ctrl+C to stop)..."
    PID="$("${ADB_CMD[@]}" shell pidof -s "$APP_ID" || true)"
    if [[ -n "$PID" ]]; then
        "${ADB_CMD[@]}" logcat --pid="$PID"
    else
        "${ADB_CMD[@]}" logcat | grep "$APP_ID"
    fi
fi
