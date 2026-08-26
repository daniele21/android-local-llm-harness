#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE="${ANDROID_SERIAL:-}"
OUTPUT_DIR=""
HOST_PACKAGE="io.github.daniele21.localllm.phonetest"
CLIENT_PACKAGE="io.github.daniele21.localllm.consumerfixture"
CLIENT_TEST_PACKAGE="io.github.daniele21.localllm.consumerfixture.test"
TEST_CLASS="io.github.daniele21.localllm.consumerfixture.SharedRuntimeControlPlaneReleaseEvidenceTest"
CLIENT_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/release/shared-runtime-client-consumer-fixture-release.apk"
CLIENT_TEST_APK="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/apk/androidTest/release/shared-runtime-client-consumer-fixture-release-androidTest.apk"
MAPPING="$ROOT_DIR/apps/shared-runtime-client-consumer-fixture/build/outputs/mapping/release/mapping.txt"
KEYCHAIN_SERVICE="io.github.daniele21.localllm.phonetest.android-upload"
KEYCHAIN_ACCOUNT="local-llm-phone-test-upload"
DEFAULT_STORE_FILE="${HOME}/.keystore/local-llm-phone-test-upload.jks"
DEFAULT_KEY_ALIAS="local-llm-phone-test-upload"

usage() {
  cat <<'EOF'
Usage:
  bash scripts/reproduce-shared-runtime-binder-r8.sh --device SERIAL [--output-dir PATH]

Builds only the minified release consumer fixture + instrumentation APK, verifies
that they use the same signer as the already-installed release Harness, then runs
the real Consumer Control Plane discovery/activation test against that host.

The installed Harness is never replaced, downgraded, force-stopped, cleared or
uninstalled. A fixture/test package installed by this run is removed on exit when
it was not already present.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device) DEVICE="${2:-}"; shift 2 ;;
    --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$DEVICE" ]] || { echo "--device is required (or set ANDROID_SERIAL)." >&2; exit 2; }
command -v adb >/dev/null || { echo "adb is required on PATH." >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 is required on PATH." >&2; exit 2; }
adb -s "$DEVICE" get-state >/dev/null

resolve_apksigner() {
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  local candidate=""
  if [[ -d "$sdk_root/build-tools" ]]; then
    candidate="$(find "$sdk_root/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n 1)"
  fi
  if [[ -z "$candidate" ]]; then candidate="$(command -v apksigner 2>/dev/null || true)"; fi
  [[ -n "$candidate" && -x "$candidate" ]] || return 1
  printf '%s\n' "$candidate"
}

load_signing() {
  local configured=0 value
  for value in \
    "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE:-}" \
    "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD:-}" \
    "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS:-}" \
    "${LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD:-}"; do
    [[ -n "$value" ]] && configured=$((configured + 1))
  done
  if [[ $configured -ne 0 && $configured -ne 4 ]]; then
    echo "Signing configuration is partial; set all LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* variables." >&2
    return 1
  fi
  if [[ $configured -eq 4 ]]; then return 0; fi

  if [[ "$(uname -s)" == "Darwin" && -f "$DEFAULT_STORE_FILE" ]] && command -v security >/dev/null; then
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
  echo "Same-signer release fixture requires the documented Harness upload signing identity." >&2
  return 1
}

package_path() {
  adb -s "$DEVICE" shell pm path "$1" 2>/dev/null | head -n 1 | sed 's/^package://' | tr -d '\r'
}

package_installed() {
  [[ -n "$(package_path "$1")" ]]
}

apk_cert_digest() {
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n 1
}

sha256_value() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

APKSIGNER="$(resolve_apksigner || true)"
[[ -n "$APKSIGNER" ]] || { echo "apksigner is required." >&2; exit 2; }
package_installed "$HOST_PACKAGE" || { echo "Release Harness is not installed on the selected device." >&2; exit 2; }

cd "$ROOT_DIR"
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "A clean checkout is required so the consumer fixture source identity is exact." >&2
  exit 2
fi
load_signing

RUN_ID="binder-r8-$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -z "$OUTPUT_DIR" ]]; then OUTPUT_DIR="$ROOT_DIR/build/shared-runtime-binder-r8/$RUN_ID"; fi
mkdir -p "$OUTPUT_DIR"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/binder-r8.XXXXXX")"
CLIENT_PREEXISTED=false
TEST_PREEXISTED=false
package_installed "$CLIENT_PACKAGE" && CLIENT_PREEXISTED=true
package_installed "$CLIENT_TEST_PACKAGE" && TEST_PREEXISTED=true
cleanup() {
  if [[ "$TEST_PREEXISTED" == false ]]; then adb -s "$DEVICE" uninstall "$CLIENT_TEST_PACKAGE" >/dev/null 2>&1 || true; fi
  if [[ "$CLIENT_PREEXISTED" == false ]]; then adb -s "$DEVICE" uninstall "$CLIENT_PACKAGE" >/dev/null 2>&1 || true; fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

./gradlew --no-daemon \
  :apps:shared-runtime-client-consumer-fixture:assembleRelease \
  :apps:shared-runtime-client-consumer-fixture:assembleReleaseAndroidTest

for artifact in "$CLIENT_APK" "$CLIENT_TEST_APK" "$MAPPING"; do
  [[ -f "$artifact" ]] || { echo "Expected build output is missing: $artifact" >&2; exit 1; }
done

grep -E '^io\.github\.daniele21\.localllm\.transport\.binder\.contract\.(ClientTokenParcel|ConsumerControlPlaneRequestParcel|ConsumerControlPlaneResultParcel|ConsumerPresetParcel|IConsumerControlPlaneResultCallback|IConsumerLocalLlmService) -> ' \
  "$MAPPING" > "$OUTPUT_DIR/r8-wire-mapping.txt" || true

HOST_REMOTE_APK="$(package_path "$HOST_PACKAGE")"
HOST_LOCAL_APK="$TMP_DIR/host.apk"
adb -s "$DEVICE" pull "$HOST_REMOTE_APK" "$HOST_LOCAL_APK" >/dev/null
HOST_CERT="$(apk_cert_digest "$HOST_LOCAL_APK")"
CLIENT_CERT="$(apk_cert_digest "$CLIENT_APK")"
TEST_CERT="$(apk_cert_digest "$CLIENT_TEST_APK")"
[[ -n "$HOST_CERT" && "$HOST_CERT" == "$CLIENT_CERT" && "$CLIENT_CERT" == "$TEST_CERT" ]] || {
  echo "Host, consumer fixture and instrumentation APK do not share the same signer." >&2
  exit 1
}

adb -s "$DEVICE" install -r "$CLIENT_APK" >/dev/null
adb -s "$DEVICE" install -r -t "$CLIENT_TEST_APK" >/dev/null
RUNNER="$(adb -s "$DEVICE" shell pm list instrumentation 2>/dev/null \
  | tr -d '\r' \
  | awk -v target="$CLIENT_PACKAGE" 'index($0, "(target=" target ")") { sub(/^instrumentation:/, ""); sub(/ \(target=.*/, ""); print; exit }')"
[[ -n "$RUNNER" ]] || { echo "Instrumentation runner for $CLIENT_PACKAGE was not registered." >&2; exit 1; }

adb -s "$DEVICE" logcat -c >/dev/null 2>&1 || true
set +e
adb -s "$DEVICE" shell am instrument -w -r -e class "$TEST_CLASS" "$RUNNER" \
  | tee "$OUTPUT_DIR/instrumentation.txt"
INSTRUMENTATION_STATUS=${PIPESTATUS[0]}
set -e
adb -s "$DEVICE" logcat -d -v epoch 2>/dev/null \
  | grep -Ei 'libbinder\.Parcel|protected data in Parcel|HCP21_CONTROL_PLANE|AndroidRuntime|ConsumerControlPlane|discoverUseCases' \
  > "$OUTPUT_DIR/technical-log.txt" || true

INSTRUMENTATION_OK=false
if [[ $INSTRUMENTATION_STATUS -eq 0 ]] \
  && grep -Eq 'OK \([0-9]+ tests?\)' "$OUTPUT_DIR/instrumentation.txt" \
  && grep -Fq 'INSTRUMENTATION_CODE: -1' "$OUTPUT_DIR/instrumentation.txt"; then
  INSTRUMENTATION_OK=true
fi
PROTECTED_PARCEL=false
if grep -Fq 'protected data in Parcel' "$OUTPUT_DIR/technical-log.txt"; then PROTECTED_PARCEL=true; fi

RESULT="INCONCLUSIVE"
if [[ "$INSTRUMENTATION_OK" == false && "$PROTECTED_PARCEL" == true ]]; then
  RESULT="REPRODUCED"
elif [[ "$INSTRUMENTATION_OK" == true && "$PROTECTED_PARCEL" == false ]]; then
  RESULT="NOT_REPRODUCED"
fi

{
  echo "result=$RESULT"
  echo "consumer_source_revision=$(git rev-parse HEAD)"
  echo "consumer_repository_dirty=false"
  echo "host_package=$HOST_PACKAGE"
  echo "host_version=$(adb -s "$DEVICE" shell dumpsys package "$HOST_PACKAGE" | tr -d '\r' | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1)"
  echo "host_apk_sha256=$(sha256_value "$HOST_LOCAL_APK")"
  echo "host_signer=$HOST_CERT"
  echo "consumer_apk_sha256=$(sha256_value "$CLIENT_APK")"
  echo "consumer_signer=$CLIENT_CERT"
  echo "instrumentation_ok=$INSTRUMENTATION_OK"
  echo "protected_parcel=$PROTECTED_PARCEL"
} | tee "$OUTPUT_DIR/result.txt"

printf '\nEvidence: %s\n' "$OUTPUT_DIR"
case "$RESULT" in
  REPRODUCED)
    echo "Classification: minified packaged consumer reproduced the protected-Parcel failure."
    ;;
  NOT_REPRODUCED)
    echo "Classification: minified packaged consumer passed; R8 alone is insufficient to explain the RedactGuard failure."
    ;;
  *)
    echo "Classification: inconclusive; inspect instrumentation.txt and technical-log.txt." >&2
    exit 1
    ;;
esac
