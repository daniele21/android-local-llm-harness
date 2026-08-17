#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_VERSION="${CONSUMER_SDK_VERSION:-0.1.0-SNAPSHOT}"
REPO_DIR="${CONSUMER_SDK_REPOSITORY:-$ROOT_DIR/build/consumer-sdk-repository}"
OUTPUT="${CONSUMER_SDK_ABI_OUTPUT:-$REPO_DIR/public-abi.txt}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

: > "$OUTPUT"

is_supported_contract() {
  local simple_name="${1%%\$*}"
  case "$simple_name" in
    UseCaseId|SessionId|RequestId|InferencePresetId|InferencePresetRef|SessionKind|ConversationRole|ConversationMessage|UseCaseReadiness|UseCaseCapabilities|EffectiveConsumerReasoningMode|Consumer*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_supported_android_client() {
  local simple_name="${1%%\$*}"
  case "$simple_name" in
    BinderConsumerLocalLlmClient|SharedRuntimeHostConfig|SharedRuntimeConnectionState|SharedRuntimeConnectionSnapshot|SharedRuntimeConnectionObserver)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

emit_public_class() {
  local classes_jar="$1"
  local class_name="$2"

  javap -classpath "$classes_jar" -public -constants "$class_name" \
    | sed -E \
        -e 's/[[:space:]]+$//' \
        -e '/kotlin\.jvm\.internal\.DefaultConstructorMarker/d' \
        -e '/\$default\(/d' \
        -e '/[-]impl[0-9-]*\(/d'
  echo
}

for artifact in core-contracts consumer-android; do
  aar="$REPO_DIR/io/github/daniele21/localllm/$artifact/$SDK_VERSION/$artifact-$SDK_VERSION.aar"
  if [[ ! -f "$aar" ]]; then
    echo "Missing published AAR: $aar" >&2
    exit 1
  fi

  work="$TMP_DIR/$artifact"
  mkdir -p "$work"
  unzip -q "$aar" classes.jar -d "$work"

  echo "# artifact=$artifact" >> "$OUTPUT"
  while IFS= read -r entry; do
    class_name="${entry%.class}"
    class_name="${class_name//\//.}"
    simple_name="${class_name##*.}"

    if [[ "$artifact" == "core-contracts" ]]; then
      is_supported_contract "$simple_name" || continue
    else
      is_supported_android_client "$simple_name" || continue
    fi

    emit_public_class "$work/classes.jar" "$class_name" >> "$OUTPUT"
  done < <(
    jar tf "$work/classes.jar" \
      | grep '^io/github/daniele21/localllm/' \
      | grep '\.class$' \
      | grep -v '/R\$\|/R\.class$\|/BuildConfig\.class$' \
      | sort
  )
done

if grep -Eq 'BinderConsumer(Lifecycle|Generation)Adapter|SharedRuntimeConnection[,)]' "$OUTPUT"; then
  echo "Supported Consumer SDK ABI leaked an implementation-only transport type" >&2
  exit 1
fi

printf 'Consumer SDK supported ABI dump: %s\n' "$OUTPUT"
