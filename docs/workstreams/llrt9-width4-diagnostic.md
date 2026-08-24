# LLRT-9 width=4 diagnostic

Status: active
Document type: workstream-state
Owner: qwen35/runtime-tuning
Canonical scope: qwen35.llrt9.width4-diagnostic
Read when: investigating the short-profile Qwen3.5 2B native-batch width=4 output divergence observed on physical Android hardware
Last reviewed: 2026-08-24

## Problem

A short-profile physical LLRT-9 run on Samsung `SM-A566B`, exact Harness commit `016467c300e84decb16697850aaef40d5e592753`, pinned llama.cpp `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`, Qwen3.5 2B Q4_K_M, context `1024`, max output `8`, seed `42`, batch/ubatch `128/64`, threads `4/4` produced exact serial/native output parity at widths `2` and `3`, then failed the hard digest gate at width `4` because source prompt index `2` diverged while the other three prompts remained identical.

The observed run is diagnostic only. It does not close canonical LLRT-9C and does not justify a production concurrency cap or policy promotion.

## Hypotheses

- **H1 — slot/sequence attribution defect:** divergence follows native sequence slot `2` after prompt permutation, indicating possible sequence/KV/logits attribution drift.
- **H2 — prompt-sensitive numerical sampling divergence:** divergence follows source prompt `2` after permutation, while slot `2` becomes stable.
- **H3 — stochastic-only divergence:** quality sampling diverges but greedy sampling preserves exact serial/native output parity at width `4`, supporting numerical sensitivity rather than cross-sequence contamination.
- **H4 — deterministic structural divergence:** greedy sampling still diverges, requiring deeper native multi-sequence investigation before width `4` can be considered safe.

## Diagnostic matrix

The dedicated runner executes three non-qualifying cases:

| Case | Prompt source order | Sampling | Discriminates |
| --- | --- | --- | --- |
| `baseline-quality` | `0,1,2,3` | Quality | Reproduce the observed width-4 mismatch on the exact diagnostic build. |
| `swap02-quality` | `2,1,0,3` | Quality | Distinguish prompt-following from slot-2-following divergence. |
| `baseline-greedy` | `0,1,2,3` | Greedy (`temperature=0`) | Remove stochastic sampling while preserving width, context and batch path. |

The diagnostic instrumentation emits only digests, token counts, prompt source indices, timing, PSS, available memory and thermal state. Prompt text and generated output are not persisted.

## Decision table

| Baseline quality | Swap 0/2 quality | Baseline greedy | Interpretation | Next action |
| --- | --- | --- | --- | --- |
| mismatch at slot 2/source 2 | mismatch moves to slot 0/source 2 | match | Prompt-sensitive stochastic/numerical divergence is favored. | Keep canonical exact-match gate; evaluate whether width 4 should remain unqualified under quality sampling. |
| mismatch at slot 2/source 2 | mismatch remains at slot 2/source 0 | any | Slot/sequence attribution defect is favored. | Investigate native output-row/KV/sequence attribution before further qualification. |
| mismatch | mismatch pattern follows prompt or slot | mismatch | Deterministic structural divergence remains. | Treat width 4 as unsafe and investigate native multi-sequence correctness. |
| match | match | match | Original mismatch was not reproduced on the diagnostic build. | Repeat bounded diagnostic before drawing a conclusion; do not promote from one clean rerun. |

## Run

```bash
bash scripts/run-llrt9-width4-diagnostic.sh \
  --model "$HOME/.lmstudio/models/unsloth/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf" \
  --tier 2b \
  --device RZGL41MQ0EB \
  --context 1024 \
  --max-output-tokens 8 \
  --case all \
  --output-dir build/llrt9-width4-diagnostic
```

Resume semantics are case-based. Use `--reset-output` only when intentionally discarding diagnostics for the exact run identity.

## Exit condition

This workstream closes only when the width-4 mismatch is classified with physical diagnostic evidence and the resulting durable decision is transferred to `docs/qwen35/current-state.md` and the owning runtime-tuning/qualification documentation. Diagnostic records can never mark LLRT-9C `DONE` or promote runtime defaults.
