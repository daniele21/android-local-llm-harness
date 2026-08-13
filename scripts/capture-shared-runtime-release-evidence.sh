#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_SERIAL=""
ADB_PATH="${ADB:-}"
EVIDENCE_DIR=""
ALLOW_EMULATOR="false"
HOST_ONLY="false"
SKIP_NEGATIVE="false"
CLEANUP="false"

HOST_PACKAGE="io.github.daniele21.localllm.phonetest"
CLIENT_PACKAGE="io.github.daniele21.localllm.consumerfixture"
POSITIVE_TEST_CLASS="io.github.daniele21.localllm.consumerfixture.SharedRuntimeReleaseEvidenceTest"
NEGATIVE_TEST_CLASS="io.github.daniele21.localllm.consumerfixture.SharedRuntimeInvalidSignerTest"
HOST_APK="$ROOT_DIR/apps/local-llm-phone-test/build/outputs/apk/release/local-llm-phone-test-release.apk"
CLIENT_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/release/shared-runtime-client-consumer-fixture-release.apk"
CLIENT_TEST_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/androidTest/release/shared-runtime-client-consumer-fixture-release-androidTest.apk"
KEYCHAIN_SERVICE="io.github.daniele21.localllm.phonetest.android-upload"
KEYCHAIN_ACCOUNT="local-llm-phone-test-upload"
DEFAULT_STORE_FILE="${HOME}/.keystore/local-llm-phone-test-upload.jks"
DEFAULT_KEY_ALIAS="local-llm-phone-test-upload"
TEMP_DIR=""

usage() {
    cat <<'EOF'
Usage:
  bash scripts/capture-shared-runtime-release-evidence.sh [options]

Options:
  --device SERIAL        Exact ADB device/emulator serial. Required when more than one device is online.
  --adb PATH             Explicit adb binary.
  --evidence-dir PATH    Explicit evidence directory. Defaults to build/shared-runtime-evidence/<UTC timestamp>.
  --allow-emulator       Allow emulator execution as PRE-FLIGHT ONLY. Never closes SR-6.
  --host-only            Build/install/launch the release host, then stop so its curated model can be installed/selected.
  --skip-negative        Skip the independently signed denial fixture. Evidence remains incomplete for SR-6.
  --cleanup              Remove consumer fixture/test package after the run. Host/model state is retained.
  --help, -h             Show this help.

Positive signing:
  The release-like host and packaged consumer must use the same external signing identity.
  Configure the existing LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* environment variables, or on macOS
  keep the existing phone-test upload key/password in the repository's documented Keychain location.

Model precondition:
  The release host must already contain an explicitly installed and selected curated Qwen3.5 model.
  The runner never copies, downloads, or discovers a host-private GGUF path.
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
        --evidence-dir)
            EVIDENCE_DIR="${2:-}"
            shift 2
            ;;
        --allow-emulator)
            ALLOW_EMULATOR="true"
            shift
            ;;
        --host-only)
            HOST_ONLY="true"
            shift
            ;;
        --skip-negative)
            SKIP_NEGATIVE="true"
            shift
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

cleanup_temp() {
    if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
    fi
}
trap cleanup_temp EXIT

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

resolve_apksigner() {
    local sdk_root=""
    local candidate=""
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        sdk_root="$ANDROID_HOME"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        sdk_root="$ANDROID_SDK_ROOT"
    elif [[ -d "$HOME/Library/Android/sdk" ]]; then
        sdk_root="$HOME/Library/Android/sdk"
    fi
    if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
        candidate="$(find "$sdk_root/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n 1)"
    fi
    if [[ -n "$candidate" && -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
    fi
    candidate="$(command -v apksigner 2>/dev/null || true)"
    [[ -n "$candidate" ]] && printf '%s\n' "$candidate"
}

load_positive_signing() {
    local configured=0
    local value
    for value in \
        "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE:-}" \
        "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD:-}" \
        "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS:-}" \
        "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD:-}"; do
        [[ -n "$value" ]] && configured=$((configured + 1))
    done
    if [[ $configured -ne 0 && $configured -ne 4 ]]; then
        echo "Error: positive signing configuration is partial; set all LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* variables." >&2
        exit 1
    fi
    if [[ $configured -eq 4 ]]; then
        return 0
    fi

    if [[ "$(uname -s)" == "Darwin" && -f "$DEFAULT_STORE_FILE" && -x "$(command -v security 2>/dev/null || true)" ]]; then
        local password
        password="$(security find-generic-password -a "$KEYCHAIN_ACCOUNT" -s "$KEYCHAIN_SERVICE" -w 2>/dev/null || true)"
        if [[ -n "$password" ]]; then
            export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE="$DEFAULT_STORE_FILE"
            export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD="$password"
            export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS="$DEFAULT_KEY_ALIAS"
            export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD="$password"
            return 0
        fi
    fi

    echo "Error: same-signer release evidence requires the external positive signing identity." >&2
    echo "Configure LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* or the documented macOS Keychain upload-key entry." >&2
    exit 1
}

apk_cert_digest() {
    local apk="$1"
    "$APKSIGNER_BIN" verify --print-certs "$apk" 2>/dev/null |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
        head -n 1
}

sha256_value() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    else
        shasum -a 256 "$file" | awk '{print $1}'
    fi
}

package_version_name() {
    local package_name="$1"
    "${ADB_CMD[@]}" shell dumpsys package "$package_name" 2>/dev/null |
        tr -d '\r' |
        sed -n 's/^[[:space:]]*versionName=//p' |
        head -n 1
}

package_version_code() {
    local package_name="$1"
    "${ADB_CMD[@]}" shell dumpsys package "$package_name" 2>/dev/null |
        tr -d '\r' |
        sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*/\1/p' |
        head -n 1
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

run_instrumentation() {
    local test_class="$1"
    local log_file="$2"
    local runner
    runner="$(find_instrumentation)"
    if [[ -z "$runner" ]]; then
        echo "Error: no instrumentation registered for $CLIENT_PACKAGE." >&2
        return 1
    fi
    set +e
    "${ADB_CMD[@]}" shell am instrument -w -r -e class "$test_class" "$runner" | tee "$log_file"
    local status=${PIPESTATUS[0]}
    set -e
    if [[ $status -ne 0 ]]; then
        return 1
    fi
    if ! grep -Eq 'OK \([0-9]+ tests?\)' "$log_file"; then
        return 1
    fi
    if ! grep -Fq 'INSTRUMENTATION_CODE: -1' "$log_file"; then
        return 1
    fi
}

write_device_identity() {
    local file="$1"
    {
        echo "device_manufacturer=$("${ADB_CMD[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
        echo "device_model=$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
        echo "device_codename=$("${ADB_CMD[@]}" shell getprop ro.product.device | tr -d '\r')"
        echo "android_release=$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
        echo "android_sdk=$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
        echo "abi=$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
    } > "$file"
}

ADB_BIN="$(resolve_adb || true)"
if [[ -z "$ADB_BIN" ]]; then
    echo "Error: adb was not found. Install Android platform-tools or pass --adb PATH." >&2
    exit 1
fi
APKSIGNER_BIN="$(resolve_apksigner || true)"
if [[ -z "$APKSIGNER_BIN" ]]; then
    echo "Error: apksigner was not found. Install Android build-tools." >&2
    exit 1
fi
if ! command -v keytool >/dev/null 2>&1; then
    echo "Error: keytool is required for the ephemeral independently signed negative fixture." >&2
    exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
    echo "Error: python3 is required to generate the ephemeral signing password." >&2
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
export ANDROID_SERIAL="$DEVICE_SERIAL"

DEVICE_KIND="physical"
if [[ "$("${ADB_CMD[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
    DEVICE_KIND="emulator"
fi
if [[ "$DEVICE_KIND" == "emulator" && "$ALLOW_EMULATOR" != "true" ]]; then
    echo "Error: SR-6 requires physical-device evidence. Use --allow-emulator only for labeled preflight." >&2
    exit 1
fi

ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$ABI" != arm64-v8a* ]]; then
    echo "Error: shared-runtime physical evidence currently requires arm64-v8a; device reports '$ABI'." >&2
    exit 1
fi

cd "$ROOT_DIR"
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Error: official SR-6 evidence requires a clean checkout so the recorded commit is exact." >&2
    exit 1
fi
load_positive_signing

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -z "$EVIDENCE_DIR" ]]; then
    EVIDENCE_DIR="$ROOT_DIR/build/shared-runtime-evidence/$TIMESTAMP"
fi
mkdir -p "$EVIDENCE_DIR"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/local-llm-sr6.XXXXXX")"

EVIDENCE_SCOPE="PHYSICAL_RELEASE_EVIDENCE"
if [[ "$DEVICE_KIND" == "emulator" ]]; then
    EVIDENCE_SCOPE="EMULATOR_PREFLIGHT_ONLY"
fi

write_device_identity "$EVIDENCE_DIR/device.txt"
"${ADB_CMD[@]}" shell dumpsys meminfo "$HOST_PACKAGE" > "$EVIDENCE_DIR/host-meminfo-before.txt" 2>/dev/null || true
"${ADB_CMD[@]}" shell dumpsys thermalservice > "$EVIDENCE_DIR/thermal-before.txt" 2>/dev/null || true

printf 'Building same-signer release-like host and packaged client...\n'
./gradlew --stop >/dev/null 2>&1 || true
./gradlew --no-daemon \
    :apps:local-llm-phone-test:assembleRelease \
    :apps:shared-runtime-client-consumer-fixture:assembleRelease \
    :apps:shared-runtime-client-consumer-fixture:assembleReleaseAndroidTest

for artifact in "$HOST_APK" "$CLIENT_APK" "$CLIENT_TEST_APK"; do
    if [[ ! -f "$artifact" ]]; then
        echo "Error: expected release artifact is missing: $artifact" >&2
        exit 1
    fi
done

HOST_CERT="$(apk_cert_digest "$HOST_APK")"
CLIENT_CERT="$(apk_cert_digest "$CLIENT_APK")"
CLIENT_TEST_CERT="$(apk_cert_digest "$CLIENT_TEST_APK")"
if [[ -z "$HOST_CERT" || -z "$CLIENT_CERT" || -z "$CLIENT_TEST_CERT" ]]; then
    echo "Error: unable to extract release APK certificate digests." >&2
    exit 1
fi
if [[ "$HOST_CERT" != "$CLIENT_CERT" || "$CLIENT_CERT" != "$CLIENT_TEST_CERT" ]]; then
    echo "Error: positive host/client/test APKs are not signed by the same certificate." >&2
    exit 1
fi

HOST_APK_SHA256="$(sha256_value "$HOST_APK")"
POSITIVE_CLIENT_APK_SHA256="$(sha256_value "$CLIENT_APK")"
POSITIVE_CLIENT_TEST_APK_SHA256="$(sha256_value "$CLIENT_TEST_APK")"

"${ADB_CMD[@]}" install -r "$HOST_APK" >/dev/null
"${ADB_CMD[@]}" shell am start -W -n "$HOST_PACKAGE/io.github.daniele21.localllm.phonetest.MainActivity" >/dev/null
HOST_VERSION_NAME="$(package_version_name "$HOST_PACKAGE")"
HOST_VERSION_CODE="$(package_version_code "$HOST_PACKAGE")"
if [[ "$HOST_ONLY" == "true" ]]; then
    {
        echo "scope=$EVIDENCE_SCOPE"
        echo "result=HOST_READY_FOR_MODEL_SETUP"
        echo "git_commit=$(git rev-parse HEAD)"
        echo "repository_dirty=false"
        echo "host_package=$HOST_PACKAGE"
        echo "host_version_name=${HOST_VERSION_NAME:-unknown}"
        echo "host_version_code=${HOST_VERSION_CODE:-unknown}"
        echo "host_certificate_sha256=$HOST_CERT"
        echo "host_apk_sha256=$HOST_APK_SHA256"
    } > "$EVIDENCE_DIR/manifest.txt"
    echo "Release host installed and launched. Install/select the curated Qwen3.5 model in the host, then rerun without --host-only."
    exit 0
fi

"${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true
"${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE.test" >/dev/null 2>&1 || true
"${ADB_CMD[@]}" install "$CLIENT_APK" >/dev/null
"${ADB_CMD[@]}" install "$CLIENT_TEST_APK" >/dev/null
POSITIVE_CLIENT_VERSION_NAME="$(package_version_name "$CLIENT_PACKAGE")"
POSITIVE_CLIENT_VERSION_CODE="$(package_version_code "$CLIENT_PACKAGE")"

"${ADB_CMD[@]}" logcat -c >/dev/null 2>&1 || true
POSITIVE_RESULT="PASS"
if ! run_instrumentation "$POSITIVE_TEST_CLASS" "$EVIDENCE_DIR/positive-instrumentation.log"; then
    POSITIVE_RESULT="FAIL"
fi
"${ADB_CMD[@]}" logcat -d -v threadtime 2>/dev/null |
    grep -E 'SR6_SHARED_RUNTIME|AndroidRuntime.*(phonetest|consumerfixture)' > "$EVIDENCE_DIR/filtered-logcat.txt" || true
grep 'SR6_SHARED_RUNTIME' "$EVIDENCE_DIR/positive-instrumentation.log" > "$EVIDENCE_DIR/metrics.txt" || true

MODEL_DIGEST_SHA256="$(sed -n 's/.*SR6_SHARED_RUNTIME identity modelDigestSha256=\([^ ]*\).*/\1/p' "$EVIDENCE_DIR/positive-instrumentation.log" | tail -n 1)"
NEGOTIATED_MINOR="$(sed -n 's/.*SR6_SHARED_RUNTIME identity .*negotiatedMinor=\([^ ]*\).*/\1/p' "$EVIDENCE_DIR/positive-instrumentation.log" | tail -n 1)"
ENABLED_FEATURES="$(sed -n 's/.*SR6_SHARED_RUNTIME identity .*enabledFeatures=\([^[:space:]]*\).*/\1/p' "$EVIDENCE_DIR/positive-instrumentation.log" | tail -n 1)"
if [[ "$POSITIVE_RESULT" == "PASS" && -z "$MODEL_DIGEST_SHA256" ]]; then
    echo "Error: positive instrumentation passed without recording the selected model digest." >&2
    POSITIVE_RESULT="FAIL"
fi

NEGATIVE_RESULT="SKIPPED"
NEGATIVE_CERT=""
NEGATIVE_CLIENT_APK_SHA256=""
NEGATIVE_CLIENT_TEST_APK_SHA256=""
if [[ "$SKIP_NEGATIVE" != "true" ]]; then
    NEGATIVE_RESULT="PASS"
    NEGATIVE_KEYSTORE="$TEMP_DIR/sr6-negative-client.p12"
    NEGATIVE_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
    keytool -genkeypair \
        -keystore "$NEGATIVE_KEYSTORE" \
        -storetype PKCS12 \
        -storepass "$NEGATIVE_PASSWORD" \
        -keypass "$NEGATIVE_PASSWORD" \
        -alias sr6-negative-client \
        -keyalg RSA \
        -keysize 2048 \
        -validity 2 \
        -dname "CN=SR6 Ephemeral Denied Client,O=Local LLM Harness,C=IT" \
        -noprompt >/dev/null 2>&1

    "${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true
    "${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE.test" >/dev/null 2>&1 || true

    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE="$NEGATIVE_KEYSTORE"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD="$NEGATIVE_PASSWORD"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS="sr6-negative-client"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD="$NEGATIVE_PASSWORD"
    ./gradlew --stop >/dev/null 2>&1 || true
    ./gradlew --no-daemon \
        :apps:shared-runtime-client-consumer-fixture:assembleRelease \
        :apps:shared-runtime-client-consumer-fixture:assembleReleaseAndroidTest

    NEGATIVE_CERT="$(apk_cert_digest "$CLIENT_APK")"
    NEGATIVE_TEST_CERT="$(apk_cert_digest "$CLIENT_TEST_APK")"
    if [[ -z "$NEGATIVE_CERT" || "$NEGATIVE_CERT" == "$HOST_CERT" || "$NEGATIVE_TEST_CERT" != "$NEGATIVE_CERT" ]]; then
        echo "Error: negative fixture signing identity is not independent or test signing does not match target signing." >&2
        exit 1
    fi
    NEGATIVE_CLIENT_APK_SHA256="$(sha256_value "$CLIENT_APK")"
    NEGATIVE_CLIENT_TEST_APK_SHA256="$(sha256_value "$CLIENT_TEST_APK")"
    "${ADB_CMD[@]}" install "$CLIENT_APK" >/dev/null
    "${ADB_CMD[@]}" install "$CLIENT_TEST_APK" >/dev/null
    if ! run_instrumentation "$NEGATIVE_TEST_CLASS" "$EVIDENCE_DIR/negative-instrumentation.log"; then
        NEGATIVE_RESULT="FAIL"
    fi
    grep 'SR6_SHARED_RUNTIME' "$EVIDENCE_DIR/negative-instrumentation.log" >> "$EVIDENCE_DIR/metrics.txt" || true
fi

"${ADB_CMD[@]}" shell dumpsys meminfo "$HOST_PACKAGE" > "$EVIDENCE_DIR/host-meminfo-after.txt" 2>/dev/null || true
"${ADB_CMD[@]}" shell dumpsys thermalservice > "$EVIDENCE_DIR/thermal-after.txt" 2>/dev/null || true

CLIENT_SDK_VERSION="$(sed -n 's/^version=//p' transports/android-binder-client/version.properties | head -n 1)"
PROTOCOL_MAJOR="$(sed -n 's/^[[:space:]]*const val MAJOR = //p' transports/android-binder-contract/src/main/kotlin/io/github/daniele21/localllm/transport/binder/contract/ProtocolModels.kt | head -n 1)"
PROTOCOL_MINOR="$(sed -n 's/^[[:space:]]*const val MINOR = //p' transports/android-binder-contract/src/main/kotlin/io/github/daniele21/localllm/transport/binder/contract/ProtocolModels.kt | head -n 1)"
LLAMA_REVISION="$(git -C third_party/llama.cpp rev-parse HEAD 2>/dev/null || echo unavailable)"

{
    echo "scope=$EVIDENCE_SCOPE"
    echo "result_positive=$POSITIVE_RESULT"
    echo "result_invalid_signer=$NEGATIVE_RESULT"
    echo "git_commit=$(git rev-parse HEAD)"
    echo "repository_dirty=false"
    echo "device_kind=$DEVICE_KIND"
    echo "host_package=$HOST_PACKAGE"
    echo "host_version_name=${HOST_VERSION_NAME:-unknown}"
    echo "host_version_code=${HOST_VERSION_CODE:-unknown}"
    echo "host_certificate_sha256=$HOST_CERT"
    echo "client_package=$CLIENT_PACKAGE"
    echo "positive_client_version_name=${POSITIVE_CLIENT_VERSION_NAME:-unknown}"
    echo "positive_client_version_code=${POSITIVE_CLIENT_VERSION_CODE:-unknown}"
    echo "positive_client_certificate_sha256=$CLIENT_CERT"
    echo "negative_client_certificate_sha256=${NEGATIVE_CERT:-not-run}"
    echo "client_sdk_version=${CLIENT_SDK_VERSION:-unknown}"
    echo "binder_protocol_major=${PROTOCOL_MAJOR:-unknown}"
    echo "binder_protocol_minor=${PROTOCOL_MINOR:-unknown}"
    echo "negotiated_protocol_minor=${NEGOTIATED_MINOR:-unknown}"
    echo "negotiated_features=${ENABLED_FEATURES:-unknown}"
    echo "model_digest_sha256=${MODEL_DIGEST_SHA256:-unknown}"
    echo "llama_cpp_revision=$LLAMA_REVISION"
    echo "negative_signing_material_committed=false"
    echo "prompt_output_persisted=false"
} > "$EVIDENCE_DIR/manifest.txt"

{
    echo "host_release_apk_sha256=$HOST_APK_SHA256"
    echo "positive_client_release_apk_sha256=$POSITIVE_CLIENT_APK_SHA256"
    echo "positive_client_test_apk_sha256=$POSITIVE_CLIENT_TEST_APK_SHA256"
    echo "negative_client_release_apk_sha256=${NEGATIVE_CLIENT_APK_SHA256:-not-run}"
    echo "negative_client_test_apk_sha256=${NEGATIVE_CLIENT_TEST_APK_SHA256:-not-run}"
} > "$EVIDENCE_DIR/apk-sha256.txt"

cat > "$EVIDENCE_DIR/README.txt" <<EOF
Shared Runtime SR-6 evidence bundle

Scope: $EVIDENCE_SCOPE
Positive same-signer result: $POSITIVE_RESULT
Independent-signer denial result: $NEGATIVE_RESULT

The bundle intentionally omits prompts, generated output, Binder tokens, signing keys/passwords, GGUF bytes and host-private model paths.
The negative signing key was generated in a temporary directory and deleted after the run.
The recorded model digest identifies the selected curated artifact without exposing its private filesystem path.
An emulator run is preflight only and cannot close SR-6.
EOF

ARCHIVE="$EVIDENCE_DIR.tar.gz"
tar -czf "$ARCHIVE" -C "$(dirname "$EVIDENCE_DIR")" "$(basename "$EVIDENCE_DIR")"

if [[ "$CLEANUP" == "true" ]]; then
    "${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true
    "${ADB_CMD[@]}" uninstall "$CLIENT_PACKAGE.test" >/dev/null 2>&1 || true
fi

if [[ "$POSITIVE_RESULT" != "PASS" || "$NEGATIVE_RESULT" == "FAIL" ]]; then
    echo "SR-6 evidence failed. Evidence retained at $EVIDENCE_DIR" >&2
    exit 1
fi
if [[ "$SKIP_NEGATIVE" == "true" ]]; then
    echo "SR-6 positive evidence passed, but invalid-signer evidence was skipped. Evidence is incomplete."
else
    echo "SR-6 release evidence passed for scope $EVIDENCE_SCOPE."
fi
echo "Evidence directory: $EVIDENCE_DIR"
echo "Archive: $ARCHIVE"
