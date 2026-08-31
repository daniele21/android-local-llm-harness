#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYCHAIN_SERVICE="io.github.daniele21.localllm.phonetest.android-upload"
KEYCHAIN_ACCOUNT="local-llm-phone-test-upload"
DEFAULT_STORE_FILE="${HOME}/.keystore/local-llm-phone-test-upload.jks"
DEFAULT_KEY_ALIAS="local-llm-phone-test-upload"
DEFAULT_UNSIGNED_AAB="${ROOT_DIR}/local-llm-phone-test-release-unsigned.aab"
DEFAULT_SIGNED_AAB="${ROOT_DIR}/local-llm-phone-test-release-signed.aab"
RELEASE_APK="${ROOT_DIR}/apps/local-llm-phone-test/build/outputs/apk/release/local-llm-phone-test-release.apk"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/build-phone-test-release.sh setup
  bash scripts/build-phone-test-release.sh build
  bash scripts/build-phone-test-release.sh build-apk
  bash scripts/build-phone-test-release.sh sign-ci-aab [INPUT_AAB] [OUTPUT_AAB]

The setup command stores the existing PKCS12 upload-keystore password in the
user's default macOS Keychain. It does not create the upload keystore.

The build command creates a signed release bundle. Locally it reads the password
from macOS Keychain and increments version.properties. In CI, explicit secure
environment variables are accepted and PLAY_VERSION_CODE can provide the exact
Play-resolved versionCode without modifying version.properties.

The build-apk command is for exact-candidate physical E2E. It requires a clean
Git checkout, keeps the current version.properties identity unchanged, builds a
signed release APK with the shared upload key and verifies its APK signature.

The sign-ci-aab command signs an existing unsigned CI bundle and verifies the
resulting JAR signature.

Optional non-secret overrides:
  LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE  Upload keystore path
  LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS   Upload key alias
  PLAY_VERSION_CODE                               Positive CI release versionCode
  ANDROID_HOME                                    Android SDK path

Optional secret overrides for non-Keychain environments:
  LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD
  LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD
EOF
}

require_macos_keychain() {
    if [[ "$(uname -s)" != "Darwin" ]] || ! command -v security >/dev/null 2>&1; then
        echo "macOS Keychain is unavailable." >&2
        echo "Set both LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_*_PASSWORD variables in a secure environment." >&2
        return 1
    fi
}

setup_keychain_password() {
    require_macos_keychain
    echo "Store the Local LLM Phone Test upload-keystore password in macOS Keychain."
    echo "The keystore and key normally use the same password."
    echo "Input is handled by Keychain and is not shown or added to shell history."
    security add-generic-password \
        -U \
        -a "${KEYCHAIN_ACCOUNT}" \
        -s "${KEYCHAIN_SERVICE}" \
        -l "Local LLM Phone Test Android upload keystore" \
        -j "Password for the Play upload keystore." \
        -w
    echo "Phone-test Android signing password saved in macOS Keychain."
}

read_keychain_password() {
    require_macos_keychain >/dev/null
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

find_android_build_tool() {
    local tool="$1"
    find "${ANDROID_HOME}/build-tools" -type f -name "${tool}" 2>/dev/null | sort -V | tail -n 1
}

load_signing_configuration() {
    STORE_FILE="${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE:-${DEFAULT_STORE_FILE}}"
    KEY_ALIAS="${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS:-${DEFAULT_KEY_ALIAS}}"

    if [[ ! -f "${STORE_FILE}" ]]; then
        echo "Upload keystore not found at ${STORE_FILE}." >&2
        echo "Create it manually outside the repository or set LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE." >&2
        exit 1
    fi

    STORE_PASSWORD="${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD:-}"
    KEY_PASSWORD="${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD:-}"

    if [[ -z "${STORE_PASSWORD}" ]]; then
        if ! STORE_PASSWORD="$(read_keychain_password)" || [[ -z "${STORE_PASSWORD}" ]]; then
            echo "Phone-test signing password is not available in macOS Keychain." >&2
            echo "Run: bash scripts/build-phone-test-release.sh setup" >&2
            echo "or inject the signing password variables securely in CI." >&2
            exit 1
        fi
    fi
    if [[ -z "${KEY_PASSWORD}" ]]; then
        KEY_PASSWORD="${STORE_PASSWORD}"
    fi

    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE="${STORE_FILE}"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD="${STORE_PASSWORD}"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS="${KEY_ALIAS}"
    export LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD="${KEY_PASSWORD}"
    trap clear_signing_configuration EXIT
}

clear_signing_configuration() {
    unset STORE_FILE KEY_ALIAS STORE_PASSWORD KEY_PASSWORD
    unset LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE
    unset LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD
    unset LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS
    unset LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD
    unset PHONE_TEST_JARSIGNER_STORE_PASSWORD PHONE_TEST_JARSIGNER_KEY_PASSWORD
}

require_clean_source() {
    if ! command -v git >/dev/null 2>&1; then
        echo "git is required to create exact physical-E2E build identity." >&2
        exit 1
    fi
    SOURCE_REVISION="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
    if [[ ! "${SOURCE_REVISION}" =~ ^[0-9a-fA-F]{40,64}$ ]]; then
        echo "Unable to determine a full source revision." >&2
        exit 1
    fi
    if [[ -n "$(git -C "${ROOT_DIR}" status --porcelain --untracked-files=normal)" ]]; then
        echo "Refusing exact-candidate APK build from a dirty source checkout." >&2
        echo "Commit, stash or remove local changes before build-apk." >&2
        exit 1
    fi
}

increment_version_code() {
    if [[ -n "${PLAY_VERSION_CODE:-}" ]]; then
        if [[ ! "${PLAY_VERSION_CODE}" =~ ^[1-9][0-9]*$ ]]; then
            echo "PLAY_VERSION_CODE must be a positive integer." >&2
            exit 2
        fi
        return
    fi

    local prop_file="${ROOT_DIR}/apps/local-llm-phone-test/version.properties"
    if [[ -f "${prop_file}" ]]; then
        local current_code
        current_code="$(sed -n 's/^versionCode=//p' "${prop_file}")"
        if [[ -n "${current_code}" ]]; then
            local next_code=$((current_code + 1))
            if sed --version >/dev/null 2>&1; then
                sed -i "s/^versionCode=.*/versionCode=${next_code}/" "${prop_file}"
            else
                sed -i '' "s/^versionCode=.*/versionCode=${next_code}/" "${prop_file}"
            fi
        fi
    fi
}

build_release() {
    load_signing_configuration
    configure_android_sdk
    increment_version_code
    cd "${ROOT_DIR}"
    ./gradlew :apps:local-llm-phone-test:bundleRelease

    local prop_file="${ROOT_DIR}/apps/local-llm-phone-test/version.properties"
    local v_code="${PLAY_VERSION_CODE:-}"
    local v_name=""
    if [[ -f "${prop_file}" ]]; then
        if [[ -z "${v_code}" ]]; then
            v_code="$(sed -n 's/^versionCode=//p' "${prop_file}")"
        fi
        v_name="$(sed -n 's/^versionName=//p' "${prop_file}")"
    fi

    echo
    echo "Signed Android App Bundle created:"
    echo "${ROOT_DIR}/apps/local-llm-phone-test/build/outputs/bundle/release/local-llm-phone-test-release.aab"
    echo "Bundle Version: versionCode: ${v_code:-N/A} versionName: ${v_name:-N/A}"
}

build_release_apk() {
    load_signing_configuration
    configure_android_sdk
    require_clean_source
    cd "${ROOT_DIR}"
    ./gradlew :apps:local-llm-phone-test:assembleRelease

    [[ -f "${RELEASE_APK}" ]] || {
        echo "Expected signed release APK not found: ${RELEASE_APK}" >&2
        exit 1
    }

    local apksigner
    apksigner="$(find_android_build_tool apksigner)"
    [[ -n "${apksigner}" ]] || {
        echo "apksigner was not found below ${ANDROID_HOME}/build-tools." >&2
        exit 1
    }
    "${apksigner}" verify --print-certs "${RELEASE_APK}"

    local prop_file="${ROOT_DIR}/apps/local-llm-phone-test/version.properties"
    local v_code="$(sed -n 's/^versionCode=//p' "${prop_file}")"
    local v_name="$(sed -n 's/^versionName=//p' "${prop_file}")"

    echo
    echo "Signed physical-E2E release APK created:"
    echo "${RELEASE_APK}"
    echo "APK Version:     versionCode: ${v_code:-N/A} versionName: ${v_name:-N/A}"
    echo "Source revision: ${SOURCE_REVISION}"
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
    export PHONE_TEST_JARSIGNER_STORE_PASSWORD="${STORE_PASSWORD}"
    export PHONE_TEST_JARSIGNER_KEY_PASSWORD="${KEY_PASSWORD}"
    jarsigner \
        -keystore "${STORE_FILE}" \
        -storetype PKCS12 \
        -storepass:env PHONE_TEST_JARSIGNER_STORE_PASSWORD \
        -keypass:env PHONE_TEST_JARSIGNER_KEY_PASSWORD \
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
    build-apk)
        build_release_apk
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
