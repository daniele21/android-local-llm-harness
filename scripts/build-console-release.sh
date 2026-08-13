#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYCHAIN_SERVICE="io.github.daniele21.localllm.phonetest.android-upload"
KEYCHAIN_ACCOUNT="local-llm-phone-test-upload"
DEFAULT_STORE_FILE="${HOME}/.keystore/local-llm-phone-test-upload.jks"
DEFAULT_KEY_ALIAS="local-llm-phone-test-upload"
DEFAULT_UNSIGNED_AAB="${ROOT_DIR}/local-llm-console-release-unsigned.aab"
DEFAULT_SIGNED_AAB="${ROOT_DIR}/local-llm-console-release-signed.aab"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/build-console-release.sh setup
  bash scripts/build-console-release.sh build
  bash scripts/build-console-release.sh sign-ci-aab [INPUT_AAB] [OUTPUT_AAB]

The Console intentionally defaults to the same upload keystore and macOS
Keychain entry used by the Phone Test app. This keeps local release signing
consistent across the two applications. Google Play App Signing must still be
configured to use the same app-signing key for both apps when signature-level
Binder authorization is required on installed Play builds.

The setup command stores the existing PKCS12 upload-keystore password in the
user's default macOS Keychain. It does not create the upload keystore.

The build command retrieves the password without printing it, increments the
Console versionCode, and creates a signed release bundle through Gradle.
The sign-ci-aab command signs an existing unsigned CI bundle and verifies the
resulting JAR signature.

Optional non-secret overrides:
  LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE  Upload keystore path
  LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_ALIAS   Upload key alias
  ANDROID_HOME                                 Android SDK path
EOF
}

require_macos_keychain() {
    if [[ "$(uname -s)" != "Darwin" ]] || ! command -v security >/dev/null 2>&1; then
        echo "This helper requires the macOS Keychain 'security' command." >&2
        exit 1
    fi
}

setup_keychain_password() {
    require_macos_keychain
    echo "Store the Local LLM Console upload-keystore password in macOS Keychain."
    echo "By default this is the same keystore/password used by Phone Test."
    echo "The keystore and key must use the same password."
    echo "Input is handled by Keychain and is not shown or added to shell history."
    security add-generic-password \
        -U \
        -a "${KEYCHAIN_ACCOUNT}" \
        -s "${KEYCHAIN_SERVICE}" \
        -l "Local LLM Phone Test Android upload keystore" \
        -j "Password for the shared Play upload keystore; store and key password are identical." \
        -w
    echo "Console Android signing password saved in macOS Keychain."
}

read_keychain_password() {
    require_macos_keychain
    security find-generic-password \
        -a "${KEYCHAIN_ACCOUNT}" \
        -s "${KEYCHAIN_SERVICE}" \
        -w 2>/dev/null
}

is_android_sdk() {
    local sdk_path="$1"
    [[ -n "${sdk_path}" && -d "${sdk_path}/platforms" && -d "${sdk_path}/build-tools" ]]
}

configure_android_sdk() {
    local properties_sdk=""
    local sdk_candidate=""

    if [[ -f "${ROOT_DIR}/local.properties" ]]; then
        properties_sdk="$(sed -n 's/^sdk\.dir=//p' "${ROOT_DIR}/local.properties" | tail -n 1)"
    fi

    for sdk_candidate in \
        "${ANDROID_HOME:-}" \
        "${ANDROID_SDK_ROOT:-}" \
        "${properties_sdk}" \
        "${HOME}/Library/Android/sdk" \
        "/opt/homebrew/share/android-commandlinetools"; do
        if is_android_sdk "${sdk_candidate}"; then
            export ANDROID_HOME="${sdk_candidate}"
            export ANDROID_SDK_ROOT="${sdk_candidate}"
            return
        fi
    done

    echo "Android SDK not found." >&2
    echo "Set ANDROID_HOME to an SDK containing platforms/ and build-tools/," >&2
    echo "or set sdk.dir in ${ROOT_DIR}/local.properties." >&2
    exit 1
}

load_signing_configuration() {
    STORE_FILE="${LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE:-${DEFAULT_STORE_FILE}}"
    KEY_ALIAS="${LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_ALIAS:-${DEFAULT_KEY_ALIAS}}"

    if [[ ! -f "${STORE_FILE}" ]]; then
        echo "Upload keystore not found at ${STORE_FILE}." >&2
        echo "Create it manually outside the repository or set LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE." >&2
        exit 1
    fi

    if ! SIGNING_PASSWORD="$(read_keychain_password)" || [[ -z "${SIGNING_PASSWORD}" ]]; then
        echo "Console signing password is not available in macOS Keychain." >&2
        echo "Run: bash scripts/build-console-release.sh setup" >&2
        exit 1
    fi

    export LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE="${STORE_FILE}"
    export LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_PASSWORD="${SIGNING_PASSWORD}"
    export LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_ALIAS="${KEY_ALIAS}"
    export LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_PASSWORD="${SIGNING_PASSWORD}"
    trap clear_signing_configuration EXIT
}

clear_signing_configuration() {
    unset SIGNING_PASSWORD
    unset LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE
    unset LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_PASSWORD
    unset LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_ALIAS
    unset LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_PASSWORD
    unset CONSOLE_JARSIGNER_PASSWORD
}

increment_version_code() {
    local prop_file="${ROOT_DIR}/apps/local-llm-console/version.properties"
    if [[ -f "${prop_file}" ]]; then
        local current_code
        current_code="$(sed -n 's/^versionCode=//p' "${prop_file}")"
        if [[ -n "${current_code}" ]]; then
            local next_code=$((current_code + 1))
            sed -i '' "s/^versionCode=.*/versionCode=${next_code}/" "${prop_file}"
        fi
    fi
}

build_release() {
    load_signing_configuration
    configure_android_sdk
    increment_version_code
    cd "${ROOT_DIR}"
    ./gradlew :apps:local-llm-console:bundleRelease

    local prop_file="${ROOT_DIR}/apps/local-llm-console/version.properties"
    local v_code=""
    local v_name=""
    if [[ -f "${prop_file}" ]]; then
        v_code="$(sed -n 's/^versionCode=//p' "${prop_file}")"
        v_name="$(sed -n 's/^versionName=//p' "${prop_file}")"
    fi

    echo
    echo "Signed Android App Bundle created:"
    echo "${ROOT_DIR}/apps/local-llm-console/build/outputs/bundle/release/local-llm-console-release.aab"
    echo "Bundle Version: versionCode: ${v_code:-N/A} versionName: ${v_name:-N/A}"
}

sign_ci_aab() {
    local input_aab="${1:-${DEFAULT_UNSIGNED_AAB}}"
    local output_aab="${2:-${DEFAULT_SIGNED_AAB}}"

    if [[ ! -f "${input_aab}" ]]; then
        echo "Unsigned CI bundle not found at ${input_aab}." >&2
        exit 1
    fi
    if [[ "${input_aab}" == "${output_aab}" ]]; then
        echo "Input and output AAB paths must differ; the unsigned artifact is preserved." >&2
        exit 2
    fi
    if ! command -v jarsigner >/dev/null 2>&1; then
        echo "jarsigner is required and was not found on PATH." >&2
        exit 1
    fi

    load_signing_configuration
    export CONSOLE_JARSIGNER_PASSWORD="${SIGNING_PASSWORD}"
    jarsigner \
        -keystore "${STORE_FILE}" \
        -storetype PKCS12 \
        -storepass:env CONSOLE_JARSIGNER_PASSWORD \
        -keypass:env CONSOLE_JARSIGNER_PASSWORD \
        -signedjar "${output_aab}" \
        "${input_aab}" \
        "${KEY_ALIAS}"
    jarsigner -verify -verbose -certs "${output_aab}"

    echo
    echo "Signed and verified Android App Bundle created:"
    echo "${output_aab}"
}

case "${1:-help}" in
    setup)
        setup_keychain_password
        ;;
    build)
        build_release
        ;;
    sign-ci-aab)
        sign_ci_aab "${2:-}" "${3:-}"
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
