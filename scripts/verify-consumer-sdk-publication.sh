#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SDK_VERSION="${CONSUMER_SDK_VERSION:-0.1.0-SNAPSHOT}"
REPO_DIR="$ROOT_DIR/build/consumer-sdk-repository"

rm -rf "$REPO_DIR"

./gradlew -PconsumerSdkVersion="$SDK_VERSION" \
  :core:contracts:publishReleasePublicationToConsumerSdkRepository \
  :transports:android-binder-contract:publishReleasePublicationToConsumerSdkRepository \
  :transports:android-binder-client:publishReleasePublicationToConsumerSdkRepository

./gradlew -p samples/external-consumer-android \
  -PconsumerSdkVersion="$SDK_VERSION" \
  :consumer:assembleDebug

if grep -R --line-number -E 'project\(|includeBuild\(|android-local-llm-harness' samples/external-consumer-android --include='*.gradle.kts'; then
  echo "External consumer fixture contains a forbidden source/build coupling" >&2
  exit 1
fi

manifest="$REPO_DIR/build-manifest.txt"
{
  echo "consumer_sdk_version=$SDK_VERSION"
  echo "source_revision=$(git rev-parse HEAD 2>/dev/null || echo unavailable)"
  echo "artifacts:"
  find "$REPO_DIR/io/github/daniele21/localllm" -type f \( -name '*.aar' -o -name '*.pom' -o -name '*.module' \) -print | sort
} > "$manifest"

checksum="$REPO_DIR/SHA256SUMS"
: > "$checksum"
while IFS= read -r artifact; do
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$artifact" >> "$checksum"
  else
    shasum -a 256 "$artifact" >> "$checksum"
  fi
done < <(find "$REPO_DIR/io/github/daniele21/localllm" -type f \( -name '*.aar' -o -name '*.pom' -o -name '*.module' \) -print | sort)

printf 'Consumer SDK publication verified: %s\n' "$SDK_VERSION"
