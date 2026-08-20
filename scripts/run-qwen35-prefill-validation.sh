#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL=""
DEVICE=""
OUTPUT_DIR="$ROOT_DIR/build/llrt3-prefill"
WORD_TIERS="256,512,1024"
REPETITIONS=5
TIMEOUT_SECONDS=600
THERMAL_START_MAX=1

usage() {
    cat <<'EOF'
Usage: bash scripts/run-qwen35-prefill-validation.sh --model /path/Qwen3.5-2B-Q4_K_M.gguf [options]

Runs focused physical prefill validation for the bounded 2B priority candidate.
It compares only the original CPU baseline (t4/bt4/b128/ub64) with the balanced
candidate (t4/bt2/b128/ub64) across deterministic prompts of increasing length.

Options:
  --device SERIAL              ADB serial. Optional when exactly one device is online.
  --word-tiers CSV             Deterministic prompt word counts (default: 256,512,1024).
  --repetitions N              Warm samples per case, >= 5 (default: 5).
  --timeout-seconds N          Per-generation timeout (default: 600).
  --thermal-start-max N|off    Forwarded thermal start gate (default: 1).
  --output-dir PATH            Evidence root (default: build/llrt3-prefill).
  --help                       Show this help.

Word tiers are workload construction labels, not claimed token counts. The exact input
token count is produced by the runtime and recorded in each evidence record. Prompt
content is local-only; evidence persists only its SHA-256 digest.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model) MODEL="${2:-}"; shift 2 ;;
        --device) DEVICE="${2:-}"; shift 2 ;;
        --word-tiers) WORD_TIERS="${2:-}"; shift 2 ;;
        --repetitions) REPETITIONS="${2:-}"; shift 2 ;;
        --timeout-seconds) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
        --thermal-start-max) THERMAL_START_MAX="${2:-}"; shift 2 ;;
        --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [[ ! -f "$MODEL" || ! -r "$MODEL" ]]; then
    echo "--model must point to the readable curated Qwen3.5 2B Q4_K_M artifact" >&2
    exit 2
fi
if [[ ! "$REPETITIONS" =~ ^[0-9]+$ ]] || ((REPETITIONS < 5)); then
    echo "--repetitions must be an integer >= 5" >&2
    exit 2
fi
if [[ ! "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || ((TIMEOUT_SECONDS < 1)); then
    echo "--timeout-seconds must be a positive integer" >&2
    exit 2
fi
if [[ "$THERMAL_START_MAX" != "off" && ! "$THERMAL_START_MAX" =~ ^[0-6]$ ]]; then
    echo "--thermal-start-max must be 0..6 or off" >&2
    exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required" >&2
    exit 2
fi

mkdir -p "$OUTPUT_DIR/prompts"
PROMPT_MANIFEST="$OUTPUT_DIR/prompt-manifest.json"
python3 - "$WORD_TIERS" "$OUTPUT_DIR/prompts" "$PROMPT_MANIFEST" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

raw_tiers = sys.argv[1]
prompt_dir = Path(sys.argv[2])
manifest_path = Path(sys.argv[3])
try:
    tiers = [int(item) for item in raw_tiers.split(",") if item]
except ValueError as exc:
    raise SystemExit("--word-tiers must be comma-separated positive integers") from exc
if not tiers or any(value <= 0 for value in tiers) or len(set(tiers)) != len(tiers):
    raise SystemExit("--word-tiers must contain unique positive integers")
if any(value > 1500 for value in tiers):
    raise SystemExit("word tiers above 1500 are intentionally rejected by this bounded 2048-context runner")

vocabulary = (
    "local runtime evidence mobile inference privacy memory thermal context prefill "
    "decode deterministic workload android model measurement latency stability"
).split()
manifest = []
for words in tiers:
    tokens = [vocabulary[index % len(vocabulary)] for index in range(words)]
    prompt = " ".join(tokens) + "."
    path = prompt_dir / f"prefill-{words}-words.txt"
    path.write_text(prompt, encoding="utf-8")
    digest = hashlib.sha256(prompt.encode("utf-8")).hexdigest()
    manifest.append({"wordTier": words, "promptSha256": digest, "file": path.name})
manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

DEVICE_ARGS=()
if [[ -n "$DEVICE" ]]; then
    DEVICE_ARGS=(--device "$DEVICE")
fi

old_ifs="$IFS"
IFS=','
set -- $WORD_TIERS
IFS="$old_ifs"
for words in "$@"; do
    if [[ ! "$words" =~ ^[0-9]+$ ]] || ((words < 1)); then
        echo "Invalid generated word tier: $words" >&2
        exit 2
    fi
    prompt_file="$OUTPUT_DIR/prompts/prefill-${words}-words.txt"
    tier_output="$OUTPUT_DIR/w${words}"
    echo "Running 2B focused prefill comparison for ${words}-word deterministic prompt"
    bash "$ROOT_DIR/scripts/run-llama-cpp-cpu-deltas.sh" \
        --model "$MODEL" \
        --tier 2b \
        "${DEVICE_ARGS[@]}" \
        --context 2048 \
        --threads 4 \
        --batch-threads 4 \
        --batch 128 \
        --ubatch 64 \
        --repetitions "$REPETITIONS" \
        --max-output-tokens 64 \
        --thinking-mode DISABLED \
        --timeout-seconds "$TIMEOUT_SECONDS" \
        --prompt-file "$prompt_file" \
        --case baseline,batch-threads-2 \
        --thermal-start-max "$THERMAL_START_MAX" \
        --output-dir "$tier_output"
done

echo "Focused Qwen3.5 2B prefill evidence complete."
echo "Prompt manifest: $PROMPT_MANIFEST"
echo "Evidence root: $OUTPUT_DIR"
echo "Use recorded inputTokens—not the word-tier labels—for technical token-count claims."
