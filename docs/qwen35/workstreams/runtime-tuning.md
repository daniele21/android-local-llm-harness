# Qwen3.5 runtime, context and Android tuning

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.runtime-tuning
Read when: changing Qwen3.5 context policy, cache/session reuse, Android CPU parameters or performance benchmark keys
Last reviewed: 2026-08-20

## Goal

Choose safe Android runtime defaults for Qwen3.5 0.8B and 2B from measured device behavior while respecting the model's hybrid/recurrent execution state.

## Planning function

Treat runtime tuning as a resolved policy:

```text
DeviceCapabilities
  + Qwen35Tier
  + exact quantization/artifact
  + workload/context tier
  + validated backend capabilities
    -> Qwen35RuntimeTuningProfile
```

The profile can control only parameters the backend exposes and the harness can validate, such as generation CPU threads, batch/prefill threads, batch size, micro-batch size, approved context tier, mmap/mlock policy and only evidence-backed backend optimizations.

Do not add chipset-specific JNI branches when llama.cpp/runtime CPU dispatch already owns the kernel choice.

## Q35-5 capability model

Q35-5 is complete.

- runtime capabilities are bound to pinned llama.cpp revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`;
- approved Qwen3.5 mobile context tiers are `1024`, `2048`, `4096` and `8192` tokens;
- context planning reserves 256 tokens and selects the smallest approved tier that can satisfy prompt + requested output + reserve;
- candidate runtime defaults use 2048 tokens rather than the model-advertised maximum;
- `supportsStatelessContextReuse`, `supportsPrefixSnapshot`, `supportsSessionRestore` and `supportsPrefixReuse` remain false unless exact-backend evidence proves them safe;
- unknown or backend-mismatched capability state fails closed.

Manual context remains bounded by model/backend/device policy.

## Hybrid/recurrent state

Upstream `llama.cpp` Qwen3.5 uses recurrent memory for linear-attention layers. Therefore:

- do not equate Qwen3.5 session state with pure KV cache;
- do not enable prefix snapshot, checkpoint restore or context reuse merely because the generic API exists;
- record backend revision when validating each reuse capability;
- treat unsupported/unknown reuse as disabled;
- verify cancellation, model switch, memory pressure and context close against recurrent-state cleanup.

## Benchmark identity

A benchmark baseline is now keyed by an execution-identity SHA-256 fingerprint in addition to application/use-case/model/load-kind identity. The fingerprint is derived from the effective context, preset/version, thinking mode, sampler values, seed policy/effective seed, output budget, chat-template identity/source and system-prompt version.

This makes benchmark matching family-neutral while preventing comparisons between semantically different generation configurations. Room schema v8 persists this identity for current/history baselines. During 7 -> 8 migration, old benchmark baselines are discarded rather than assigned fabricated execution identity; generation-run telemetry is retained.

The physical Qwen3.5 evidence schema additionally records exact artifact/backend/harness/device/runtime dimensions, including:

```text
artifact SHA-256
quantization
Qwen3.5 tier
llama.cpp revision
Harness commit
device model / Android / ABI
runtime profile id/version
generation profile id/version
threads / batch threads
batch / ubatch
context tier
max output tokens
thinking mode
cold/warm classification
```

Physical evidence schema v3 makes the output-token budget a required identity field. Evidence produced with different output budgets is therefore not silently comparable or resumable as the same case.

## Candidate runtime profiles

0.8B and 2B remain separate tuning tracks. Neither profile is `MEASURED` yet.

Current conservative candidate defaults:

```text
context: 2048
batch: 128
ubatch: 64
max generation threads: 4
max batch threads: 4
mmap: true
mlock: false
flash attention: false
```

The controlled matrix explores, independently for both curated Q4_K_M reference artifacts:

```text
context: 1024 / 2048 / 4096 / 8192
threads: 2 / 4
batch/ubatch: 64/32 / 128/64
thinking: DISABLED / ENABLED
```

The 2B bounded LLRT-3 screening has now narrowed the CPU candidate set. On Samsung SM-A566B, the strongest balanced bounded candidate is currently:

```text
generation threads: 4
batch threads: 2
batch: 128
ubatch: 64
context: 2048
max output: 64
thinking: DISABLED
```

This is still a `CANDIDATE`, not a `MEASURED` default. The bounded run used a short 19-token prompt on one device and therefore requires realistic longer-prompt prefill validation plus the remaining Q35 evidence gates before promotion.

## Physical-device evidence runners

### Bounded LLRT-3 CPU delta runner

Use the bounded runner first when the goal is to identify promising CPU-side deltas without paying for the full Cartesian tuning matrix:

```bash
bash scripts/run-llama-cpp-cpu-deltas.sh \
  --model /path/Qwen3.5-2B-Q4_K_M.gguf \
  --tier 2b \
  --output-dir build/llrt3
```

The default bounded identity is intentionally shorter than the original exploratory lane:

```text
context: 2048
warm repetitions: 5
max output tokens: 64
thinking: DISABLED
thermal start gate: <= 1 (LIGHT)
per-generation timeout: 600 seconds
```

The four deterministic cases remain baseline, generation threads = 2, batch/prefill threads = 2, and batch/ubatch = 64/32. The runner supports `--case NAME` to execute only one case and resumes complete cases for the exact same evidence identity. Raw evidence is appended only after a case completes, so interruption does not leave a partial active case.

Before every new case, the runner queries `PowerManager.currentThermalStatus` through instrumentation and waits until the device is at or below the configured start threshold. This keeps deterministic case order while reducing order bias from running a later configuration on an already-heated phone. If thermal status is unavailable, the default behavior fails closed; bypass requires the explicit `--thermal-start-max off` option.

The runner protects evidence ownership in two ways:

- a custom output directory ending in `0.8b` or `2b` must match `--tier`;
- files are keyed by context, output budget, warm repetition count and thinking mode, and existing evidence must match schema, artifact, backend, Harness commit, device and common run identity before it can be resumed.

`--reset-output` is the only path that discards evidence for the exact current run identity. There is no silent truncation of an existing raw evidence file.

### 2026-08-20 bounded 2B evidence

Bounded Qwen3.5 2B Q4_K_M screening is complete for the following evidence envelope:

```text
device: Samsung SM-A566B
Android: 16 / SDK 36 / arm64-v8a
artifact SHA-256: aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
llama.cpp: aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3
Harness commit: ac0f5adf9aefded4e516abe5e47f62ced3f11ba8
context: 2048
input tokens: 19
max output: 64
thinking: DISABLED
samples: 4 cases × (1 cold + 5 warm) = 24 generations
```

All 24 generations completed successfully, all reached the 64-token output budget, no crash/timeout/OOM occurred and all four cases were classified as comparable evidence with `eligibleForProfileSelection=true`.

Observed bounded decision:

- `t4 / bt2 / b128 / ub64` is the current priority candidate because it materially reduced observed peak process PSS and sustained drift while keeping total latency close to baseline and thermal status controlled;
- `t4 / bt4 / b64 / ub32` produced the best median latency/decode throughput but reached thermal status `3`, so it remains experimental;
- `t2 / bt4 / b128 / ub64` did not provide a compelling typical-performance improvement and is deprioritized.

The bounded 2B screening has fulfilled its search-space-reduction purpose. It does not by itself satisfy Q35-RT-07 or promote a measured profile because realistic longer-prompt prefill, wider-context/product requirements where applicable, lifecycle/memory validation and representative-device evidence remain separate gates.

### Full Q35-6 tuning matrix

Run:

```bash
bash scripts/run-qwen35-tuning-matrix.sh \
  --model-08b /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --model-2b /path/Qwen3.5-2B-Q4_K_M.gguf \
  --device <adb-serial> \
  --repetitions 3
```

`--device` is optional when exactly one device is online. The runner verifies both curated SHA-256 values before installation/testing and requires `arm64-v8a`.

For each tuning case the instrumentation test records one true cold run and at least three warm runs while keeping the same model/runtime loaded. Evidence schema v3 records TTFT, prefill/decode time and throughput, process PSS snapshots, available memory, thermal status, stop reason, output budget and exact benchmark identity dimensions. Evidence is returned to the host through Android instrumentation status output rather than relying on plain test stdout.

The full matrix runner writes:

```text
build/qwen35-tuning/qwen35-tuning-evidence.jsonl
build/qwen35-tuning/qwen35-tuning-summary.csv
```

The bounded runner writes tier-separated, run-identity-specific files under its output root, for example:

```text
build/llrt3/2b/llama-cpp-cpu-deltas-ctx2048-out64-w5-disabled-evidence.jsonl
build/llrt3/2b/llama-cpp-cpu-deltas-ctx2048-out64-w5-disabled-summary.csv
```

The summarizer validates sample completeness and identity consistency, calculates cold metrics plus warm median/p95, sustained first-to-last drift, peak PSS, minimum available memory and maximum thermal status, and marks a case only as `eligibleForProfileSelection`. It never changes a runtime profile to `MEASURED` automatically.

## Current evidence wave

Physical CPU work and recurrent-state correctness share device resources but not acceptance ownership. Software preparation can run in parallel; thermal/performance runs on the same device must be serialized.

1. **0.8B bounded screening:** run the same LLRT-3 methodology on the exact curated 0.8B artifact.
2. **2B realistic prefill:** carry `t4 / bt2 / b128 / ub64` into longer-prompt validation and compare its TTFT/prefill/memory/thermal trade-off against the baseline.
3. **Recurrent-state correctness:** execute the separate LLRT-4 physical probe for both 0.8B and 2B; do not infer reuse safety from performance evidence.
4. **Profile decision:** only after the above evidence, select/reject versioned candidate defaults and continue Q35-RT-09 lifecycle/memory validation.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-RT-01 | DONE | Add Qwen3.5 runtime capability model keyed by backend revision. |
| Q35-RT-02 | DONE | Define approved candidate mobile context tiers and safety reserve policy. |
| Q35-RT-03 | DONE | Gate prefix/session restore and reuse capabilities for Qwen3.5. |
| Q35-RT-04 | DONE | Extend benchmark identity and persistence with exact execution-configuration identity. |
| Q35-RT-05 | DONE | Define controlled tuning matrix plus repeatable physical-device evidence tooling. |
| Q35-RT-06 | IN PROGRESS | 0.8B physical tuning track: bounded screening remains open, followed by the required broader/representative validation before profile promotion. |
| Q35-RT-07 | IN PROGRESS | 2B physical tuning track: bounded screening is DONE; realistic longer-prompt/broader validation remains before the track can close. |
| Q35-RT-08 | BLOCKED | Select versioned default profiles only after both model tracks provide sufficient measured memory/TTFT/throughput/thermal evidence. |
| Q35-RT-09 | BLOCKED | Validate model switch, memory pressure, cancellation and idle unload after measured configuration candidates are selected. |
| Q35-RT-10 | DONE | Diagnostic candidate profiles and evidence summarization cannot masquerade as measured/certified defaults. |

## Acceptance criteria

Q35-5 is complete because context selection is bounded, exact-backend capabilities fail closed and unvalidated recurrent-state optimizations remain disabled.

Q35-6 remains `IN PROGRESS` until:

- 0.8B and 2B have separately measured default profiles;
- selected defaults have recorded TTFT, prefill/decode throughput, peak memory and thermal evidence;
- cancellation, close, switch and memory-pressure paths leave the measured runtime reusable;
- physical-device evidence is ready for certification consumption.

The completed bounded 2B screening satisfies only the search-space-reduction slice of the 2B track. It must remain preserved as an immutable evidence baseline rather than being overwritten or reinterpreted as the final Q35-6 matrix.

## Upstream reference

- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
