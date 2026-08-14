#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_PATH="${ADB:-}"
DEVICE_SERIAL=""
ALLOW_EMULATOR="false"
EVIDENCE_DIR=""
BASE_HOST_APK=""
REPLACEMENT_HOST_APK=""
BASE_COMMIT=""
REPLACEMENT_COMMIT=""
CLIENT_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/release/shared-runtime-client-consumer-fixture-release.apk"
CLIENT_TEST_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/androidTest/release/shared-runtime-client-consumer-fixture-release-androidTest.apk"

HOST_PACKAGE="io.github.daniele21.localllm.phonetest"
CLIENT_PACKAGE="io.github.daniele21.localllm.consumerfixture"
POSITIVE_TEST_CLASS="io.github.daniele21.localllm.consumerfixture.SharedRuntimeReleaseEvidenceTest"
HOST_ACTIVITY="$HOST_PACKAGE/io.github.daniele21.localllm.phonetest.MainActivity"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/capture-shared-runtime-package-upgrade-evidence.sh \
    --base-host-apk PATH --base-commit SHA \
    --replacement-host-apk PATH --replacement-commit SHA [options]

Required:
  --base-host-apk PATH          Release host APK used before replacement.
  --base-commit SHA             Exact source commit for the base host APK.
  --replacement-host-apk PATH   Compatible release host APK installed with adb install -r.
  --replacement-commit SHA      Exact source commit for the replacement host APK.

Options:
  --client-apk PATH             Packaged release consumer APK.
  --client-test-apk PATH        Packaged release consumer instrumentation APK.
  --device SERIAL               Exact ADB device serial when more than one is online.
  --adb PATH                    Explicit adb binary.
  --evidence-dir PATH           Output directory; defaults under build/shared-runtime-upgrade-evidence/.
  --allow-emulator              Permit PRE-FLIGHT ONLY execution on an emulator.
  --help, -h                    Show help.

Precondition:
  The host package must already have a curated Qwen3.5 model installed/selected in app data.
  The base host, replacement host, consumer and consumer test APKs must share the accepted
  signing certificate. The runner preserves host data with `adb install -r` and proves new
  Binder traffic immediately before and after package replacement. It does not claim that an
  existing live session survives package/process replacement.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --base-host-apk) BASE_HOST_APK="${2:-}"; shift 2 ;;
        --replacement-host-apk) REPLACEMENT_HOST_APK="${2:-}"; shift 2 ;;
        --base-commit) BASE_COMMIT="${2:-}"; shift 2 ;;
        --replacement-commit) REPLACEMENT_COMMIT="${2:-}"; shift 2 ;;
        --client-apk) CLIENT_APK="${2:-}"; shift 2 ;;
        --client-test-apk) CLIENT_TEST_APK="${2:-}"; shift 2 ;;
        --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
        --adb) ADB_PATH="${2:-}"; shift 2 ;;
        --evidence-dir) EVIDENCE_DIR="${2:-}"; shift 2 ;;
        --allow-emulator) ALLOW_EMULATOR="true"; shift ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Error: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done

for required in BASE_HOST_APK REPLACEMENT_HOST_APK BASE_COMMIT REPLACEMENT_COMMIT; do
    if [[ -z "${!required}" ]]; then
        echo "Error: --$(echo "$required" | tr '[:upper:]_' '[:lower:]-') is required." >&2
        exit 2
    fi
done
for artifact in "$BASE_HOST_APK" "$REPLACEMENT_HOST_APK" "$CLIENT_APK" "$CLIENT_TEST_APK"; do
    if [[ ! -f "$artifact" ]]; then
        echo "Error: APK does not exist: $artifact" >&2
        exit 1
    fi
done
if [[ ! "$BASE_COMMIT" =~ ^[0-9a-fA-F]{40}$ || ! "$REPLACEMENT_COMMIT" =~ ^[0-9a-fA-F]{40}$ ]]; then
    echo "Error: base/replacement commits must be full 40-character git SHAs." >&2
    exit 2
fi

resolve_adb() {
    local candidates=(
        "$ADB_PATH"
        "$(command -v adb 2>/dev/null || true)"
        "${ANDROID_HOME:-}/platform-tools/adb"
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
        "$HOME/Library/Android/sdk/platform-tools/adb"
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

resolve_apksigner() {
    local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    local candidate=""
    if [[ -z "$sdk_root" && -d "$HOME/Library/Android/sdk" ]]; then
        sdk_root="$HOME/Library/Android/sdk"
    fi
    if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
        candidate="$(find "$sdk_root/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n 1)"
    fi
    if [[ -n "$candidate" && -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
    fi
    command -v apksigner 2>/dev/null || true
}

apk_cert_digest() {
    "$APKSIGNER_BIN" verify --print-certs "$1" 2>/dev/null |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
        head -n 1
}

sha256_value() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

package_version_name() {
    "${ADB_CMD[@]}" shell dumpsys package "$1" 2>/dev/null |
        tr -d '\r' | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1
}

package_version_code() {
    "${ADB_CMD[@]}" shell dumpsys package "$1" 2>/dev/null |
        tr -d '\r' | sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n 1
}

find_instrumentation() {
    "${ADB_CMD[@]}" shell pm list instrumentation 2>/dev/null |
        tr -d '\r' |
        awk -v target="$CLIENT_PACKAGE" '
            index($0, "(target=" target ")") > 0 {
                sub(/^instrumentation:/, "")
                sub(/ \(target=.*/, "")
                print
                exit
            }
        '
}

run_positive_traffic() {
    local log_file="$1"
    local runner
    runner="$(find_instrumentation)"
    if [[ -z "$runner" ]]; then
        echo "Error: no instrumentation registered for $CLIENT_PACKAGE." >&2
        return 1
    fi
    set +e
    "${ADB_CMD[@]}" shell am instrument -w -r -e class "$POSITIVE_TEST_CLASS" "$runner" | tee "$log_file"
    local status=${PIPESTATUS[0]}
    set -e
    [[ $status -eq 0 ]] || return 1
    grep -Eq 'OK \([0-9]+ tests?\)' "$log_file" || return 1
    grep -Fq 'INSTRUMENTATION_CODE: -1' "$log_file" || return 1
    grep -Fq 'SR6_SHARED_RUNTIME identity ' "$log_file" || return 1
    grep -Fq 'SR6_SHARED_RUNTIME generation ' "$log_file" || return 1
}

extract_model_digest() {
    sed -n 's/.*SR6_SHARED_RUNTIME identity modelDigestSha256=\([^ ]*\).*/\1/p' "$1" | tail -n 1
}

write_device_identity() {
    {
        echo "device_manufacturer=$("${ADB_CMD[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
        echo "device_model=$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
        echo "android_release=$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
        echo "android_sdk=$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
        echo "abi=$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
    } > "$1"
}

ADB_BIN="$(resolve_adb || true)"
APKSIGNER_BIN="$(resolve_apksigner || true)"
[[ -n "$ADB_BIN" ]] || { echo "Error: adb not found." >&2; exit 1; }
[[ -n "$APKSIGNER_BIN" ]] || { echo "Error: apksigner not found." >&2; exit 1; }

DEVICES=()
while IFS= read -r device; do
    [[ -n "$device" ]] && DEVICES+=("$device")
done < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')
[[ ${#DEVICES[@]} -gt 0 ]] || { echo "Error: no online device." >&2; exit 1; }
if [[ -z "$DEVICE_SERIAL" ]]; then
    [[ ${#DEVICES[@]} -eq 1 ]] || { echo "Error: select one device with --device SERIAL." >&2; exit 1; }
    DEVICE_SERIAL="${DEVICES[0]}"
fi
printf '%s\n' "${DEVICES[@]}" | grep -Fxq "$DEVICE_SERIAL" || { echo "Error: device is not online." >&2; exit 1; }
ADB_CMD=("$ADB_BIN" -s "$DEVICE_SERIAL")

DEVICE_KIND="physical"
if [[ "$("${ADB_CMD[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
    DEVICE_KIND="emulator"
fi
if [[ "$DEVICE_KIND" == "emulator" && "$ALLOW_EMULATOR" != "true" ]]; then
    echo "Error: package-upgrade release evidence requires a physical device." >&2
    exit 1
fi
ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$ABI" == arm64-v8a* ]] || { echo "Error: arm64-v8a device required; got $ABI." >&2; exit 1; }

BASE_CERT="$(apk_cert_digest "$BASE_HOST_APK")"
REPLACEMENT_CERT="$(apk_cert_digest "$REPLACEMENT_HOST_APK")"
CLIENT_CERT="$(apk_cert_digest "$CLIENT_APK")"
CLIENT_TEST_CERT="$(apk_cert_digest "$CLIENT_TEST_APK")"
if [[ -z "$BASE_CERT" || "$BASE_CERT" != "$REPLACEMENT_CERT" || "$BASE_CERT" != "$CLIENT_CERT" || "$CLIENT_CERT" != "$CLIENT_TEST_CERT" ]]; then
    echo "Error: all package-upgrade evidence APKs must share the accepted signing certificate." >&2
    exit 1
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -z "$EVIDENCE_DIR" ]]; then
    EVIDENCE_DIR="$ROOT_DIR/build/shared-runtime-upgrade-evidence/$TIMESTAMP"
fi
mkdir -p "$EVIDENCE_DIR"
write_device_identity "$EVIDENCE_DIR/device.txt"

"${ADB_CMD[@]}" install -r "$BASE_HOST_APK" >/dev/null
"${ADB_CMD[@]}" shell am start -W -n "$HOST_ACTIVITY" >/dev/null
"${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true
"${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE.test" >/dev/null 2>&1 || true
"${ADB_CMD[@]}" install "$CLIENT_APK" >/dev/null
"${ADB_CMD[@]}" install "$CLIENT_TEST_APK" >/dev/null

BASE_VERSION_NAME="$(package_version_name "$HOST_PACKAGE")"
BASE_VERSION_CODE="$(package_version_code "$HOST_PACKAGE")"
if ! run_positive_traffic "$EVIDENCE_DIR/pre-upgrade-instrumentation.log"; then
    echo "Error: pre-upgrade Binder traffic failed." >&2
    exit 1
fi
PRE_MODEL_DIGEST="$(extract_model_digest "$EVIDENCE_DIR/pre-upgrade-instrumentation.log")"
[[ -n "$PRE_MODEL_DIGEST" ]] || { echo "Error: pre-upgrade model identity missing." >&2; exit 1; }

echo "PACKAGE_UPGRADE_PRE_TRAFFIC=pass" > "$EVIDENCE_DIR/markers.txt"
"${ADB_CMD[@]}" install -r "$REPLACEMENT_HOST_APK" >/dev/null
"${ADB_CMD[@]}" shell am start -W -n "$HOST_ACTIVITY" >/dev/null
REPLACEMENT_VERSION_NAME="$(package_version_name "$HOST_PACKAGE")"
REPLACEMENT_VERSION_CODE="$(package_version_code "$HOST_PACKAGE")"

if ! run_positive_traffic "$EVIDENCE_DIR/post-upgrade-instrumentation.log"; then
    echo "Error: post-upgrade Binder traffic failed." >&2
    exit 1
fi
POST_MODEL_DIGEST="$(extract_model_digest "$EVIDENCE_DIR/post-upgrade-instrumentation.log")"
[[ -n "$POST_MODEL_DIGEST" ]] || { echo "Error: post-upgrade model identity missing." >&2; exit 1; }
if [[ "$PRE_MODEL_DIGEST" != "$POST_MODEL_DIGEST" ]]; then
    echo "Error: selected model identity changed across host package replacement." >&2
    exit 1
fi
{
    echo "PACKAGE_UPGRADE_POST_READY=ready"
    echo "PACKAGE_UPGRADE_POST_TRAFFIC=pass"
    echo "PACKAGE_UPGRADE_MODEL_IDENTITY=preserved"
} >> "$EVIDENCE_DIR/markers.txt"

SCOPE="PHYSICAL_RELEASE_EVIDENCE"
[[ "$DEVICE_KIND" == "emulator" ]] && SCOPE="EMULATOR_PREFLIGHT_ONLY"
{
    echo "scope=$SCOPE"
    echo "result=PASS"
    echo "base_commit=$BASE_COMMIT"
    echo "replacement_commit=$REPLACEMENT_COMMIT"
    echo "base_host_version_name=${BASE_VERSION_NAME:-unknown}"
    echo "base_host_version_code=${BASE_VERSION_CODE:-unknown}"
    echo "replacement_host_version_name=${REPLACEMENT_VERSION_NAME:-unknown}"
    echo "replacement_host_version_code=${REPLACEMENT_VERSION_CODE:-unknown}"
    echo "signing_certificate_sha256=$BASE_CERT"
    echo "base_host_apk_sha256=$(sha256_value "$BASE_HOST_APK")"
    echo "replacement_host_apk_sha256=$(sha256_value "$REPLACEMENT_HOST_APK")"
    echo "client_apk_sha256=$(sha256_value "$CLIENT_APK")"
    echo "client_test_apk_sha256=$(sha256_value "$CLIENT_TEST_APK")"
    echo "model_digest_sha256=$PRE_MODEL_DIGEST"
    echo "pre_upgrade_traffic=PASS"
    echo "post_upgrade_traffic=PASS"
    echo "live_session_survival_claimed=false"
    echo "prompt_output_persisted=false"
} > "$EVIDENCE_DIR/manifest.txt"

grep 'SR6_SHARED_RUNTIME' "$EVIDENCE_DIR/pre-upgrade-instrumentation.log" > "$EVIDENCE_DIR/pre-upgrade-metrics.txt" || true
grep 'SR6_SHARED_RUNTIME' "$EVIDENCE_DIR/post-upgrade-instrumentation.log" > "$EVIDENCE_DIR/post-upgrade-metrics.txt" || true

cat > "$EVIDENCE_DIR/README.txt" <<EOF
Shared Runtime SR-6 package-upgrade evidence

Scope: $SCOPE
Result: PASS

This scenario proves that a compatible, same-signer host package can service new packaged-client
Binder traffic immediately before and after `adb install -r` replacement while preserving the
selected curated-model identity. It deliberately does not claim that a live session survives
package/process replacement.

The evidence omits prompts, generated output, Binder tokens, GGUF bytes, adb serials and private
host model paths.
EOF

ARCHIVE="$EVIDENCE_DIR.tar.gz"
tar -czf "$ARCHIVE" -C "$(dirname "$EVIDENCE_DIR")" "$(basename "$EVIDENCE_DIR")"
echo "SR-6 package-upgrade evidence passed for scope $SCOPE."
echo "Evidence directory: $EVIDENCE_DIR"
echo "Archive: $ARCHIVE"
