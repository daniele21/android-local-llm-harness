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

BINDER_AAR="$REPO_DIR/io/github/daniele21/localllm/android-binder-contract/$SDK_VERSION/android-binder-contract-$SDK_VERSION.aar"
BINDER_AAR="$BINDER_AAR" python3 <<'PY'
import os
import zipfile
from pathlib import Path

path = Path(os.environ["BINDER_AAR"])
if not path.is_file():
    raise SystemExit(f"Published Binder contract AAR is missing: {path}")

with zipfile.ZipFile(path) as archive:
    try:
        rules = archive.read("proguard.txt").decode("utf-8")
    except KeyError as exc:
        raise SystemExit("Published Binder contract AAR does not carry consumer ProGuard/R8 rules") from exc

required = {
    "-keep class io.github.daniele21.localllm.transport.binder.contract.*Parcel { *; }",
    "-keep class io.github.daniele21.localllm.transport.binder.contract.*Parcel$* { *; }",
    "-keep interface io.github.daniele21.localllm.transport.binder.contract.I* { *; }",
    "-keep class io.github.daniele21.localllm.transport.binder.contract.I*$* { *; }",
}
missing = sorted(rule for rule in required if rule not in rules)
if missing:
    raise SystemExit("Published Binder contract AAR is missing cross-process wire rules: " + ", ".join(missing))
PY

./gradlew -p samples/external-consumer-android \
  -PconsumerSdkVersion="$SDK_VERSION" \
  :consumer:assembleDebug \
  :consumer-app:assembleRelease

EXTERNAL_MAPPING="$ROOT_DIR/samples/external-consumer-android/consumer-app/build/outputs/mapping/release/mapping.txt"
EXTERNAL_MAPPING="$EXTERNAL_MAPPING" python3 <<'PY'
import os
from pathlib import Path

path = Path(os.environ["EXTERNAL_MAPPING"])
if not path.is_file():
    raise SystemExit(f"External minified consumer mapping is missing: {path}")

critical = {
    "io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel",
    "io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel",
    "io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel",
    "io.github.daniele21.localllm.transport.binder.contract.ConsumerPresetParcel",
    "io.github.daniele21.localllm.transport.binder.contract.IConsumerControlPlaneResultCallback",
    "io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService",
}
resolved = {}
for raw in path.read_text(encoding="utf-8").splitlines():
    if raw.startswith(" ") or " -> " not in raw or not raw.endswith(":"):
        continue
    source, target = raw[:-1].split(" -> ", 1)
    if source in critical:
        resolved[source] = target

missing = sorted(critical - resolved.keys())
renamed = sorted((source, target) for source, target in resolved.items() if source != target)
if missing:
    raise SystemExit("External minified consumer is missing kept wire classes: " + ", ".join(missing))
if renamed:
    details = ", ".join(f"{source}->{target}" for source, target in renamed)
    raise SystemExit("External minified consumer renamed cross-process wire classes: " + details)
PY

if grep -R --line-number -E 'project\(|includeBuild\(|android-local-llm-harness' \
  samples/external-consumer-android --include='*.gradle.kts'; then
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
