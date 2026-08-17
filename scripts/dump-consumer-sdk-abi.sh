#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_VERSION="${CONSUMER_SDK_VERSION:-0.1.0-SNAPSHOT}"
REPO_DIR="${CONSUMER_SDK_REPOSITORY:-$ROOT_DIR/build/consumer-sdk-repository}"
OUTPUT="${CONSUMER_SDK_ABI_OUTPUT:-$REPO_DIR/public-abi.txt}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

: > "$OUTPUT"

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
    javap -classpath "$work/classes.jar" -public -constants "$class_name" \
      | sed -E 's/[[:space:]]+$//' \
      >> "$OUTPUT"
    echo >> "$OUTPUT"
  done < <(
    jar tf "$work/classes.jar" \
      | grep '^io/github/daniele21/localllm/' \
      | grep '\.class$' \
      | grep -v '/R\$\|/R\.class$\|/BuildConfig\.class$' \
      | sort
  )
done

printf 'Consumer SDK public ABI dump: %s\n' "$OUTPUT"
