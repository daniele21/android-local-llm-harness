# LLRT-3 next physical CPU evidence wave

Status: ready-for-device-evidence
Owner: qwen35 / llama-cpp-runtime

## Baseline already closed

The bounded Qwen3.5 2B Q4_K_M screening completed on 2026-08-20 and is preserved as schema-v3 evidence. Its purpose was candidate narrowing, not measured-profile promotion. The priority balanced candidate is `t4 / bt2 / b128 / ub64`; the `b64 / ub32` case remains experimental because it reached Android thermal status 3.

Do not rerun that bounded 2B search-space screen merely to produce schema-v4 data. Existing v3 evidence remains valid inside its original identity envelope.

## Next evidence

### 1. Bounded 0.8B screening

Run the same bounded four-case methodology against the exact curated Qwen3.5 0.8B Q4_K_M artifact:

```bash
bash scripts/run-llama-cpp-cpu-deltas.sh \
  --model /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --tier 0.8b \
  --output-dir build/llrt3
```

The runner verifies the curated SHA-256, uses one cold plus five warm samples by default, gates every new case on comparable thermal start state and never promotes a runtime profile automatically.

### 2. Focused 2B realistic-prefill comparison

The completed bounded 2B evidence used only 19 input tokens. Because `batchThreads=2` traded slower prefill/TTFT for lower observed peak PSS and better sustained stability, the next test compares only the baseline and that candidate under larger deterministic prompts:

```bash
bash scripts/run-qwen35-prefill-validation.sh \
  --model /path/Qwen3.5-2B-Q4_K_M.gguf
```

Default workload construction uses 256-, 512- and 1024-word deterministic prompts. These are workload labels, not token claims. Exact `inputTokens` from runtime telemetry is the authoritative token count.

The focused comparison intentionally excludes `generationThreads=2` and `batch=64/ubatch=32`; the bounded screen already deprioritized those as a balanced production candidate.

## Evidence schema v4

New LLRT-3 physical runs use schema v4. The only identity extension from v3 is `promptDigest`, a SHA-256 digest of the exact UTF-8 prompt. Prompt content is never written to the evidence JSONL/CSV.

The Android instrumentation test accepts the prompt as base64 for transport, recomputes its digest on-device and rejects a supplied digest mismatch. The host runner includes the digest in file/case identity, so evidence from different prompts cannot be silently resumed or combined.

`scripts/summarize-qwen35-tuning.py` remains backward compatible with schema-v3 evidence so the completed 2B baseline is not rewritten or invalidated.

## Physical serialization

0.8B bounded runs and 2B prefill runs may have their software preparation in parallel. On one physical phone, performance/thermal execution remains serialized and each case must pass the thermal-start gate. Separate representative devices may execute independent model tracks in parallel.

## Exit gate

This wave is complete when:

- bounded 0.8B evidence is complete and reviewed;
- the 2B priority candidate has realistic-prefill evidence versus baseline;
- each result preserves exact artifact/backend/Harness/device/runtime/prompt identity;
- accepted candidates are handed to Q35 profile review rather than automatically marked `MEASURED`.

Wider 4096/8192-context measurements remain requirement-driven follow-up evidence rather than mandatory Cartesian expansion of this bounded wave.
