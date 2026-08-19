# llama.cpp bounded CPU delta runbook

Status: active
Document type: runbook
Owner: llama-cpp-runtime
Canonical scope: runbook.llama-cpp-cpu-deltas
Read when: measuring LLRT-3 CPU parameter deltas after a Q35-6 baseline candidate is selected
Last reviewed: 2026-08-19

## Purpose

This runbook measures a deliberately small set of CPU-side llama.cpp deltas without replacing or expanding the full Q35-6 tuning matrix. The baseline remains a reviewed Qwen3.5 runtime configuration. LLRT-3 changes one dimension at a time and records sustained warm-run drift so a faster peak result cannot hide thermal degradation.

The runner does not select or promote a runtime profile. Physical evidence remains subject to Q35-6/Q35-7 and memory acceptance rules.

## Default experiment

For one exact curated Qwen3.5 artifact, the default baseline is:

```text
context = 2048
cpu threads = 4
batch threads = 4
batch = 128
ubatch = 64
thinking = DISABLED
```

The runner executes at most four unique cases:

1. exact baseline;
2. generation threads changed to 2;
3. batch/prefill threads changed to 2;
4. batch/ubatch changed to 64/32.

If a supplied baseline already equals a delta, the duplicate is skipped. Context and thinking mode are fixed for the experiment so LLRT-3 does not create another Cartesian tuning matrix.

## Run

```bash
bash scripts/run-llama-cpp-cpu-deltas.sh \
  --model /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --tier 0.8b \
  --device <adb-serial>
```

Run the 0.8B and 2B artifacts independently. The runner verifies the curated artifact SHA-256 before pushing it to the device and requires `arm64-v8a`.

Useful explicit baseline overrides:

```bash
bash scripts/run-llama-cpp-cpu-deltas.sh \
  --model /path/Qwen3.5-2B-Q4_K_M.gguf \
  --tier 2b \
  --context 2048 \
  --threads 4 \
  --batch-threads 4 \
  --batch 128 \
  --ubatch 64 \
  --repetitions 8 \
  --max-output-tokens 128 \
  --thinking-mode DISABLED
```

`--repetitions` must be at least 5. Increase it when a representative device requires a longer sustained window, but do not change the value between cases being compared.

## Evidence

The runner reuses the Q35 physical tuning instrumentation and writes:

```text
build/llama-cpp-cpu-deltas/llama-cpp-cpu-deltas-evidence.jsonl
build/llama-cpp-cpu-deltas/llama-cpp-cpu-deltas-summary.csv
```

The summary retains cold, warm median and warm p95 metrics and additionally reports first-to-last warm drift for:

- TTFT;
- total generation duration;
- decode tokens/second;
- thermal status.

Positive duration drift means later warm samples became slower. Negative decode-throughput drift means later warm samples lost throughput. These are observations, not automatic pass/fail thresholds.

Review each case together with peak PSS, minimum available memory, maximum thermal status, stop reasons and the exact evidence identity. A parameter delta is not accepted merely because its peak tokens/second is higher.

## Boundaries

- Do not use desktop/emulator results for an Android performance claim.
- Do not change context, thinking mode or several CPU parameters simultaneously inside one LLRT-3 delta.
- Do not auto-promote `CANDIDATE` profiles to `MEASURED`.
- Do not interpret thermal-status equality as proof of sustained stability when the measurement window is too short.
- Do not run this work on a different llama.cpp revision and compare it as if execution identity were unchanged.
