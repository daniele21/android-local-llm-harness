#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.daniele21.localllm.devicetest.debug"
ADB_BIN="${ADB:-adb}"
MODEL=""
TIER=""
DEVICE=""
CONTEXT=2048
THREADS=""
BATCH_THREADS=4
BATCH=128
UBATCH=64
MAX_OUTPUT_TOKENS=64
GENERATION_SEED=42
TIMEOUT_SECONDS=900
REPETITIONS=4
WIDTH_SCOPE="all"
THERMAL_START_MAX=1
COOLDOWN_TIMEOUT_SECONDS=1800
COOLDOWN_POLL_SECONDS=30
OUTPUT_DIR="$ROOT_DIR/build/llrt9"
RESET_OUTPUT=false
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"

usage() {
  cat <<'EOF'
Usage: bash scripts/run-llama-cpp-evaluation-batch-evidence.sh --model /path/model.gguf --tier 0.8b|2b [options]

Runs bounded LLRT-9C physical-device evidence comparing the normal serial LocalLlmClient path with
the evaluation-only native multi-sequence RuntimeEvaluationBatchClient path on the same resident model.
No prompt or generated text is persisted: schema-v7 evidence contains SHA-256 digests only.

Options:
  --device SERIAL                 ADB serial when more than one device is online.
  --context N                     Per-sequence context tokens (default: 2048).
  --threads N                     Generation threads (default: 2 for 0.8b, 4 for 2b).
  --batch-threads N               Prefill/batch threads (default: 4).
  --batch N                       llama.cpp batch size (default: 128).
  --ubatch N                      llama.cpp micro-batch size (default: 64).
  --max-output-tokens N           Output budget per case (default: 64).
  --seed N                        Fixed non-negative generation seed (default: 42).
  --timeout-seconds N             Per serial-vs-batch pair timeout (default: 900).
  --repetitions N                 Even repetitions per width, >= 4 (default: 4).
  --width 2|3|4|all               Batch widths to evaluate (default: all).
  --thermal-start-max N|off       Require Android thermal status <= N before each pair (default: 1).
  --cooldown-timeout-seconds N    Maximum thermal-gate wait (default: 1800).
  --cooldown-poll-seconds N       Thermal polling interval (default: 30).
  --output-dir PATH               Evidence root (default: build/llrt9).
  --reset-output                  Discard evidence for this exact run identity.
  --help                          Show this help.

Each width alternates SERIAL_FIRST and BATCH_FIRST samples. The even repetition requirement keeps
the order balanced. Exact serial/native output digests and per-case output-token counts are hard
correctness gates. The runner rejects tracked worktree changes and an unexpected llama.cpp pin.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL="${2:-}"; shift 2 ;;
    --tier) TIER="${2:-}"; shift 2 ;;
    --device) DEVICE="${2:-}"; shift 2 ;;
    --context) CONTEXT="${2:-}"; shift 2 ;;
    --threads) THREADS="${2:-}"; shift 2 ;;
    --batch-threads) BATCH_THREADS="${2:-}"; shift 2 ;;
    --batch) BATCH="${2:-}"; shift 2 ;;
    --ubatch) UBATCH="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --seed) GENERATION_SEED="${2:-}"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --repetitions) REPETITIONS="${2:-}"; shift 2 ;;
    --width) WIDTH_SCOPE="${2:-}"; shift 2 ;;
    --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
    --cooldown-timeout-seconds) COOLDOWN_TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --cooldown-poll-seconds) COOLDOWN_POLL_SECONDS="${2:-}"; shift 2 ;;
    --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
    --reset-output) RESET_OUTPUT=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -f "$MODEL" && -r "$MODEL" ]] || { echo "--model must point to a readable GGUF file" >&2; exit 2; }
[[ "$TIER" == "0.8b" || "$TIER" == "2b" ]] || { echo "--tier must be 0.8b or 2b" >&2; exit 2; }
[[ "$WIDTH_SCOPE" == "all" || "$WIDTH_SCOPE" == "2" || "$WIDTH_SCOPE" == "3" || "$WIDTH_SCOPE" == "4" ]] || {
  echo "--width must be 2, 3, 4 or all" >&2; exit 2;
}
[[ "$THERMAL_START_MAX" == "off" || "$THERMAL_START_MAX" =~ ^[0-6]$ ]] || {
  echo "--thermal-start-max must be 0..6 or off" >&2; exit 2;
}

if [[ -z "$THREADS" ]]; then
  [[ "$TIER" == "0.8b" ]] && THREADS=2 || THREADS=4
fi
for value_name in CONTEXT THREADS BATCH_THREADS BATCH UBATCH MAX_OUTPUT_TOKENS TIMEOUT_SECONDS COOLDOWN_TIMEOUT_SECONDS COOLDOWN_POLL_SECONDS; do
  value="${!value_name}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 )) || { echo "$value_name must be a positive integer" >&2; exit 2; }
done
[[ "$REPETITIONS" =~ ^[0-9]+$ ]] && (( REPETITIONS >= 4 && REPETITIONS % 2 == 0 )) || {
  echo "--repetitions must be an even integer >= 4" >&2; exit 2;
}
[[ "$GENERATION_SEED" =~ ^[0-9]+$ ]] || { echo "--seed must be non-negative" >&2; exit 2; }
(( UBATCH <= BATCH )) || { echo "--ubatch must be <= --batch" >&2; exit 2; }
(( CONTEXT % 256 == 0 )) || { echo "--context must be a multiple of 256" >&2; exit 2; }
command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }
command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 2; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then openssl dgst -sha256 "$1" | awk '{print $NF}'
  else echo "A SHA-256 implementation is required" >&2; exit 2
  fi
}

case "$TIER" in
  0.8b)
    EXPECTED_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
    EXPECTED_MODEL_TIER="B0_8"
    ;;
  2b)
    EXPECTED_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
    EXPECTED_MODEL_TIER="B2"
    ;;
esac
ACTUAL_SHA="$(sha256_file "$MODEL" | tr '[:upper:]' '[:lower:]')"
[[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || { echo "$TIER model does not match the curated Q4_K_M reference" >&2; exit 2; }

cd "$ROOT_DIR"
if ! git diff --quiet --ignore-submodules=dirty -- || ! git diff --cached --quiet --ignore-submodules=dirty --; then
  echo "LLRT-9C evidence requires a clean tracked Harness worktree" >&2
  exit 2
fi
if [[ ! -e third_party/llama.cpp/.git ]]; then
  echo "third_party/llama.cpp is not initialized; initialize the pinned submodule before evidence capture" >&2
  exit 2
fi
if ! git -C third_party/llama.cpp diff --quiet -- || ! git -C third_party/llama.cpp diff --cached --quiet --; then
  echo "LLRT-9C evidence requires a clean llama.cpp submodule worktree" >&2
  exit 2
fi
ACTUAL_BACKEND_REVISION="$(git -C third_party/llama.cpp rev-parse HEAD)"
[[ "$ACTUAL_BACKEND_REVISION" == "$BACKEND_REVISION" ]] || {
  echo "Unexpected llama.cpp pin: expected $BACKEND_REVISION, got $ACTUAL_BACKEND_REVISION" >&2
  exit 2
}
HARNESS_COMMIT="$(git rev-parse HEAD)"
[[ "$HARNESS_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Unable to resolve exact Harness commit" >&2; exit 2; }

ADB_CMD=("$ADB_BIN")
[[ -n "$DEVICE" ]] && ADB_CMD+=("-s" "$DEVICE")
"${ADB_CMD[@]}" get-state >/dev/null
DEVICE_ABI="$("${ADB_CMD[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$DEVICE_ABI" == arm64-v8a* ]] || { echo "LLRT-9C requires arm64-v8a; device reports $DEVICE_ABI" >&2; exit 2; }
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$("${ADB_CMD[@]}" shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$("${ADB_CMD[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"

RUN_KEY="ctx${CONTEXT}-out${MAX_OUTPUT_TOKENS}-r${REPETITIONS}-seed${GENERATION_SEED}-t${THREADS}-bt${BATCH_THREADS}-b${BATCH}-ub${UBATCH}"
TIER_OUTPUT_DIR="$OUTPUT_DIR/$TIER"
mkdir -p "$TIER_OUTPUT_DIR"
JSONL="$TIER_OUTPUT_DIR/llrt9-${RUN_KEY}-evidence.jsonl"
CSV="$TIER_OUTPUT_DIR/llrt9-${RUN_KEY}-summary.csv"
if [[ "$RESET_OUTPUT" == true ]]; then : > "$JSONL"; rm -f "$CSV"; elif [[ ! -f "$JSONL" ]]; then : > "$JSONL"; fi

python3 - "$JSONL" "$EXPECTED_SHA" "$EXPECTED_MODEL_TIER" "$BACKEND_REVISION" "$HARNESS_COMMIT" "$DEVICE_MODEL" "$DEVICE_RELEASE" "$DEVICE_SDK" "$DEVICE_ABI" "$CONTEXT" "$BATCH" "$UBATCH" "$THREADS" "$BATCH_THREADS" "$MAX_OUTPUT_TOKENS" "$GENERATION_SEED" <<'PY'
import json, sys
from pathlib import Path
path=Path(sys.argv[1])
expected={
    "schemaVersion":7,
    "evidenceType":"LLRT9_SERIAL_VS_NATIVE_BATCH",
    "modelDigest":sys.argv[2],
    "modelTier":sys.argv[3],
    "backendRevision":sys.argv[4],
    "harnessCommit":sys.argv[5],
    "deviceModel":sys.argv[6],
    "androidRelease":sys.argv[7],
    "sdkInt":int(sys.argv[8]),
    "abi":sys.argv[9],
    "contextTokensPerSequence":int(sys.argv[10]),
    "batchSize":int(sys.argv[11]),
    "microBatchSize":int(sys.argv[12]),
    "cpuThreads":int(sys.argv[13]),
    "batchThreads":int(sys.argv[14]),
    "maxOutputTokens":int(sys.argv[15]),
    "generationSeed":int(sys.argv[16]),
    "seedPolicy":"FIXED",
    "thinkingMode":"DISABLED",
}
for n,line in enumerate(path.read_text().splitlines(),1):
    if not line.strip(): continue
    record=json.loads(line)
    for key,value in expected.items():
        if record.get(key)!=value:
            raise SystemExit(f"existing LLRT-9 evidence incompatible at line {n}: {key}")
    width=record.get("batchWidth")
    sample=record.get("sampleIndex")
    if width not in (2,3,4) or not isinstance(sample,int) or sample < 0:
        raise SystemExit(f"existing LLRT-9 evidence has invalid width/sample at line {n}")
    expected_order="SERIAL_FIRST" if sample % 2 == 0 else "BATCH_FIRST"
    if record.get("measurementOrder") != expected_order:
        raise SystemExit(f"existing LLRT-9 evidence has invalid measurementOrder at line {n}")
    if record.get("aggregateContextTokens") != record["contextTokensPerSequence"] * width:
        raise SystemExit(f"existing LLRT-9 evidence has invalid aggregate context at line {n}")
    serial_digests=record.get("serialOutputDigests")
    batch_digests=record.get("batchOutputDigests")
    serial_tokens=record.get("serialOutputTokensPerCase")
    batch_tokens=record.get("batchOutputTokensPerCase")
    if record.get("outputsMatch") is not True or serial_digests != batch_digests:
        raise SystemExit(f"existing LLRT-9 evidence has correctness drift at line {n}")
    if serial_tokens != batch_tokens:
        raise SystemExit(f"existing LLRT-9 evidence has per-case token drift at line {n}")
    if not all(isinstance(v,list) and len(v)==width for v in (serial_digests,batch_digests,serial_tokens,batch_tokens)):
        raise SystemExit(f"existing LLRT-9 evidence has invalid per-case arrays at line {n}")
PY

./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
APP_APK="$(find apps/device-test-runner/build/outputs/apk/debug -type f -name '*.apk' | sort | tail -n 1)"
TEST_APK="$(find apps/device-test-runner/build/outputs/apk/androidTest/debug -type f -name '*.apk' | sort | tail -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || { echo "Unable to locate device-test APKs" >&2; exit 1; }
"${ADB_CMD[@]}" install -r -t "$APP_APK"
"${ADB_CMD[@]}" install -r -t "$TEST_APK"
"${ADB_CMD[@]}" shell run-as "$APP_ID" mkdir -p files/e2e
"${ADB_CMD[@]}" shell -T run-as "$APP_ID" dd of=files/e2e/model.gguf bs=1048576 < "$MODEL" >/dev/null
trap '"${ADB_CMD[@]}" shell run-as "$APP_ID" rm -f files/e2e/model.gguf >/dev/null 2>&1 || true' EXIT
RUNNER="$("${ADB_CMD[@]}" shell pm list instrumentation | tr -d '\r' | grep -F "(target=$APP_ID)" | head -n 1 | sed -E 's/^instrumentation:([^ ]+).*/\1/' || true)"
[[ -n "$RUNNER" ]] || { echo "Unable to discover AndroidJUnitRunner" >&2; exit 1; }

read_thermal_status() {
  set +e
  out="$("${ADB_CMD[@]}" shell am instrument -w -r -e class io.github.daniele21.localllm.devicetest.Qwen35TuningInstrumentedTest#reportsThermalStatus "$RUNNER" 2>&1)"
  rc=$?
  set -e
  out="$(printf '%s' "$out" | tr -d '\r')"
  (( rc == 0 )) || { printf '%s\n' "$out" >&2; return 1; }
  status="$(printf '%s\n' "$out" | sed -n 's/^.*LOCAL_LLM_THERMAL_STATUS //p' | tail -n 1)"
  [[ "$status" =~ ^-?[0-9]+$ ]] || return 1
  printf '%s\n' "$status"
}

wait_for_thermal_gate() {
  [[ "$THERMAL_START_MAX" == "off" ]] && return 0
  deadline=$(( $(date +%s) + COOLDOWN_TIMEOUT_SECONDS ))
  while true; do
    thermal="$(read_thermal_status)" || { echo "Unable to read thermal status" >&2; exit 1; }
    (( thermal >= 0 )) || { echo "Thermal status unavailable; use --thermal-start-max off only intentionally" >&2; exit 1; }
    if (( thermal <= THERMAL_START_MAX )); then return 0; fi
    (( $(date +%s) < deadline )) || { echo "Thermal gate timed out" >&2; exit 1; }
    sleep "$COOLDOWN_POLL_SECONDS"
  done
}

record_exists() {
  width="$1" sample="$2"
  python3 - "$JSONL" "$width" "$sample" <<'PY'
import json,sys
from pathlib import Path
for line in Path(sys.argv[1]).read_text().splitlines():
    if line.strip():
        r=json.loads(line)
        if r.get("batchWidth")==int(sys.argv[2]) and r.get("sampleIndex")==int(sys.argv[3]): raise SystemExit(0)
raise SystemExit(1)
PY
}

run_pair() {
  width="$1" sample="$2"
  if record_exists "$width" "$sample"; then echo "Resume: width=$width sample=$sample already recorded"; return; fi
  wait_for_thermal_gate
  echo "LLRT-9C width=$width sample=$sample: serial vs native batch"
  set +e
  out="$("${ADB_CMD[@]}" shell am instrument -w -r \
    -e class io.github.daniele21.localllm.devicetest.Llrt9EvaluationBatchInstrumentedTest#recordsSerialVsNativeBatchEvidence \
    -e modelRelativePath files/e2e/model.gguf \
    -e modelSha256 "$EXPECTED_SHA" -e modelTier "$TIER" -e batchWidth "$width" \
    -e contextSize "$CONTEXT" -e batchSize "$BATCH" -e microBatchSize "$UBATCH" \
    -e cpuThreads "$THREADS" -e batchThreads "$BATCH_THREADS" -e maxOutputTokens "$MAX_OUTPUT_TOKENS" \
    -e generationSeed "$GENERATION_SEED" -e timeoutSeconds "$TIMEOUT_SECONDS" \
    -e sampleIndex "$sample" -e harnessCommit "$HARNESS_COMMIT" "$RUNNER" 2>&1)"
  rc=$?
  set -e
  out="$(printf '%s' "$out" | tr -d '\r')"
  printf '%s\n' "$out"
  if (( rc != 0 )) || printf '%s\n' "$out" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg='; then
    echo "LLRT-9C physical pair failed for width=$width sample=$sample" >&2; exit 1
  fi
  line="$(printf '%s\n' "$out" | sed -n 's/^.*LOCAL_LLM_LLRT9_JSON //p' | tail -n 1)"
  [[ -n "$line" ]] || { echo "No LLRT-9C evidence emitted" >&2; exit 1; }
  python3 - "$line" "$width" "$sample" <<'PY'
import json,sys
r=json.loads(sys.argv[1]); width=int(sys.argv[2]); sample=int(sys.argv[3])
assert r["schemaVersion"]==7
assert r["evidenceType"]=="LLRT9_SERIAL_VS_NATIVE_BATCH"
assert r["batchWidth"]==width and r["sampleIndex"]==sample
assert r["measurementOrder"] == ("SERIAL_FIRST" if sample % 2 == 0 else "BATCH_FIRST")
assert r["outputsMatch"] is True
assert r["serialOutputDigests"]==r["batchOutputDigests"]
assert r["serialOutputTokensPerCase"]==r["batchOutputTokensPerCase"]
assert len(r["serialOutputDigests"])==width
assert len(r["serialOutputTokensPerCase"])==width
assert r["aggregateContextTokens"]==r["contextTokensPerSequence"]*width
assert r["serialElapsedMs"]>0 and r["batchElapsedMs"]>0
PY
  printf '%s\n' "$line" >> "$JSONL"
}

widths=(2 3 4)
[[ "$WIDTH_SCOPE" != "all" ]] && widths=("$WIDTH_SCOPE")
for width in "${widths[@]}"; do
  for ((sample=0; sample<REPETITIONS; sample++)); do run_pair "$width" "$sample"; done
done

python3 - "$JSONL" "$CSV" "$REPETITIONS" <<'PY'
import csv,json,statistics,sys
from collections import defaultdict
from pathlib import Path
records=[json.loads(x) for x in Path(sys.argv[1]).read_text().splitlines() if x.strip()]
reps=int(sys.argv[3]); groups=defaultdict(list)
for r in records:
    if not r.get("outputsMatch"): raise SystemExit("LLRT-9C correctness mismatch present in evidence")
    if r.get("serialOutputDigests") != r.get("batchOutputDigests"):
        raise SystemExit("LLRT-9C output-digest mismatch present in evidence")
    if r.get("serialOutputTokensPerCase") != r.get("batchOutputTokensPerCase"):
        raise SystemExit("LLRT-9C per-case token mismatch present in evidence")
    groups[r["batchWidth"]].append(r)
with Path(sys.argv[2]).open("w",newline="") as f:
    w=csv.writer(f)
    w.writerow(["batchWidth","samples","serialFirstSamples","batchFirstSamples","serialMedianMs","batchMedianMs","medianSpeedup","maxObservedPssKb","maxThermalStatus"])
    for width in sorted(groups):
        rs=groups[width]
        selected=[r for r in rs if 0 <= r["sampleIndex"] < reps]
        if len(selected)!=reps or {r["sampleIndex"] for r in selected} != set(range(reps)):
            raise SystemExit(f"LLRT-9C width {width} does not contain exactly the requested balanced sample set")
        serial_first=sum(r["measurementOrder"]=="SERIAL_FIRST" for r in selected)
        batch_first=sum(r["measurementOrder"]=="BATCH_FIRST" for r in selected)
        if serial_first != reps//2 or batch_first != reps//2:
            raise SystemExit(f"LLRT-9C width {width} measurement order is not balanced")
        serial=statistics.median(r["serialElapsedMs"] for r in selected)
        batch=statistics.median(r["batchElapsedMs"] for r in selected)
        pss=max(max(r["processPssKbBefore"],r["processPssKbAfterSerial"],r["processPssKbAfterBatch"]) for r in selected)
        thermal=max(max(r["thermalStatusBefore"],r["thermalStatusAfterSerial"],r["thermalStatusAfterBatch"]) for r in selected)
        w.writerow([width,len(selected),serial_first,batch_first,round(serial,2),round(batch,2),round(serial/max(batch,1),4),pss,thermal])
PY

echo "LLRT-9C evidence: $JSONL"
echo "LLRT-9C summary:  $CSV"
echo "Review physical correctness, throughput, observed memory and thermal evidence before any policy promotion."
