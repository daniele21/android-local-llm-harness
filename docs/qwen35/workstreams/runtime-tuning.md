# Qwen3.5 runtime, context and Android tuning

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.runtime-tuning
Read when: changing Qwen3.5 context policy, cache/session reuse, Android CPU parameters or performance benchmark keys
Last reviewed: 2026-08-21

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

Exact physical LLRT-4 probes were completed on both curated 0.8B and 2B artifacts on 2026-08-21. All supported save/restore equivalence checks passed with `maxDelta=0`, including append-only, divergent restore, clear/restore, repeated restore and full-sequence remove/restore. Partial rollback is unsupported on both tiers, so the explicit product verdict is `KEEP_DISABLED`: recurrent/prefix reuse remains off.

## Benchmark identity

A benchmark baseline is keyed by an execution-identity SHA-256 fingerprint in addition to application/use-case/model/load-kind identity. The fingerprint is derived from the effective context, preset/version, thinking mode, sampler values, seed policy/effective seed, output budget, chat-template identity/source and system-prompt version.

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
prompt digest
```

Physical evidence schema **v4** makes output-token budget and prompt identity material evidence dimensions. Evidence produced with different workloads is therefore not silently comparable or resumable as the same case.

## Candidate runtime profiles

0.8B and 2B remain separate tuning tracks. Neither profile is `MEASURED` yet.

Current conservative common envelope:

```text
context: 2048
batch: 128
ubatch: 64
mmap: true
mlock: false
flash attention: false
```

The controlled search space can explore, independently for both curated Q4_K_M reference artifacts:

```text
context: 1024 / 2048 / 4096 / 8192
threads: 2 / 4
batch/ubatch: 64/32 / 128/64
thinking: DISABLED / ENABLED
```

The 2026-08-21 evidence now narrows the CPU candidates differently by tier.

### 0.8B bounded priority candidate

On Samsung SM-A566B, the bounded 0.8B screening favors:

```text
generation threads: 2
batch threads: 4
batch: 128
ubatch: 64
context: 2048
max output: 64
thinking: DISABLED
```

This candidate improved warm total latency from roughly 70.0 s baseline to 65.9 s while keeping thermal status at 0. It is still a bounded `CANDIDATE`, not a `MEASURED` default: the workload used a 19-token prompt on one device and broader/representative Q35 validation remains open.

### 2B current CPU candidate

The short-prompt bounded 2B run had initially prioritized `t4 / bt2 / b128 / ub64` because of its memory-oriented trade-off. Focused realistic prefill validation on 2026-08-21 **rejected that candidate**: the prefill/TTFT regression grew consistently as the prompt increased.

The current 2B CPU candidate therefore remains:

```text
generation threads: 4
batch threads: 4
batch: 128
ubatch: 64
context: 2048
max output: 64
thinking: DISABLED
```

This is not yet a `MEASURED` default. Broader product workloads, representative-device evidence and lifecycle/memory acceptance remain separate gates.

## Physical-device evidence runners

### Bounded LLRT-3 CPU delta runner

Use the bounded runner first when the goal is to identify promising CPU-side deltas without paying for the full Cartesian tuning matrix:

```bash
bash scripts/run-llama-cpp-cpu-deltas.sh \
  --model /path/model.gguf \
  --tier 0.8b|2b \
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

The four deterministic cases are baseline, generation threads = 2, batch/prefill threads = 2, and batch/ubatch = 64/32. The runner supports `--case NAME` to execute only one case and resumes complete cases for the exact same evidence identity. Raw evidence is appended only after a case completes, so interruption does not leave a partial active case.

Before every new case, the runner queries `PowerManager.currentThermalStatus` through instrumentation and waits until the device is at or below the configured start threshold. This keeps deterministic case order while reducing order bias from running a later configuration on an already-heated phone. If thermal status is unavailable, the default behavior fails closed; bypass requires the explicit `--thermal-start-max off` option.

The runner protects evidence ownership in two ways:

- a custom output directory ending in `0.8b` or `2b` must match `--tier`;
- files are keyed by context, output budget, warm repetition count, thinking mode and prompt digest, and existing evidence must match schema, artifact, backend, Harness commit, device and common run identity before it can be resumed.

`--reset-output` is the only path that discards evidence for the exact current run identity. There is no silent truncation of an existing raw evidence file.

### Focused realistic-prefill runner

For a candidate whose main uncertainty is prefill behavior, use:

```bash
bash scripts/run-qwen35-prefill-validation.sh \
  --model /path/Qwen3.5-2B-Q4_K_M.gguf \
  --device <adb-serial>
```

The runner compares baseline `t4/bt4/b128/ub64` with candidate `t4/bt2/b128/ub64` across deterministic 256/512/1024-word prompts. Word tiers are workload construction labels only; the runtime-recorded input token count is authoritative. The 1024-word workload may require a larger per-generation timeout on CPU-only devices; interrupted incomplete cases can be resumed without overwriting complete evidence.

### 2026-08-20 bounded 2B evidence

Bounded Qwen3.5 2B Q4_K_M screening is preserved for the following historical evidence envelope:

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

All 24 generations completed successfully and all four cases were comparable evidence. This run narrowed the search space but did not promote a profile. Its bounded preference for `t4/bt2/b128/ub64` was explicitly subjected to the realistic-prefill follow-up below rather than being treated as final.

### 2026-08-21 bounded 0.8B evidence

Bounded Qwen3.5 0.8B Q4_K_M screening is complete for:

```text
device: Samsung SM-A566B
Android: 16 / SDK 36 / arm64-v8a
artifact SHA-256: bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517
llama.cpp: aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3
Harness commit: b35d303f9f019e6304a5e628d20fefc5b944765f
context: 2048
input tokens: 19
max output: 64
thinking: DISABLED
samples: 4 cases × (1 cold + 5 warm) = 24 generations
```

All four cases were evidence-eligible. Representative warm medians were:

| Case | TTFT | Prefill | Decode | Total | Max thermal |
| --- | ---: | ---: | ---: | ---: | ---: |
| baseline `t4/bt4/b128/ub64` | 12.99 s | 5.43 s | 64.09 s | 69.99 s | 0 |
| `t2/bt4/b128/ub64` | 12.54 s | 5.46 s | 60.08 s | 65.87 s | 0 |
| `t4/bt2/b128/ub64` | 13.96 s | 6.16 s | 65.10 s | 72.26 s | 1 |
| `t4/bt4/b64/ub32` | 13.34 s | 5.74 s | 65.38 s | 72.70 s | 1 |

The bounded priority candidate is `t2/bt4/b128/ub64`. This completes the search-space-reduction slice only; the 0.8B profile remains unmeasured.

### 2026-08-21 focused 2B prefill evidence

The 2B focused run used the same Samsung/device/backend/Harness identity as the 0.8B follow-up, exact 2B artifact SHA `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`, context 2048, 64 output tokens and thinking disabled.

Warm median results:

| Actual input tokens | Baseline TTFT | Candidate TTFT | Baseline prefill | Candidate prefill | Baseline total | Candidate total |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 283 | 179.0 s | 203.6 s | 161.6 s | 187.9 s | 313.7 s | 330.0 s |
| 553 | 332.3 s | 379.2 s | 315.0 s | 364.1 s | 465.3 s | 505.4 s |
| 1094 | 635.2 s | 737.3 s | 619.6 s | 721.3 s | 754.3 s | 862.6 s |

`candidate = t4/bt2/b128/ub64`; `baseline = t4/bt4/b128/ub64`.

The candidate is **REJECTED**. At 1094 input tokens it adds roughly 102 s to median prefill and 108 s to total latency, while the bounded memory advantage no longer provides a compelling trade-off. The baseline remains the current 2B CPU candidate.

The 1094-token baseline prefill of roughly 10.3 minutes is specific to this device, backend and configuration; it is not generalized to all Android hardware. It does establish a concrete CPU baseline for later accelerator comparisons.

### 2026-08-21 recurrent-state correctness evidence

The LLRT-4 native probe ran against both exact curated artifacts and returned the same result:

```text
append-only-equivalence         PASS maxDelta=0
divergent-restore-equivalence   PASS maxDelta=0
clear-restore-equivalence       PASS maxDelta=0
repeated-restore-equivalence    PASS maxDelta=0
full-sequence-remove            SUPPORTED
full-remove-restore-equivalence PASS maxDelta=0
partial-rollback                UNSUPPORTED
LLRT4_NATIVE_VERDICT            KEEP_DISABLED
```

The evidence-backed decision is therefore:

```text
Qwen3.5 0.8B recurrent/prefix reuse: KEEP_DISABLED
Qwen3.5 2B recurrent/prefix reuse:   KEEP_DISABLED
```

A negative qualification is valid evidence. No product capability flag changes to true.

### Full Q35-6 tuning matrix

Run only when broader Q35 acceptance requires it:

```bash
bash scripts/run-qwen35-tuning-matrix.sh \
  --model-08b /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --model-2b /path/Qwen3.5-2B-Q4_K_M.gguf \
  --device <adb-serial> \
  --repetitions 3
```

`--device` is optional when exactly one device is online. The runner verifies both curated SHA-256 values before installation/testing and requires `arm64-v8a`.

For each tuning case the instrumentation test records one true cold run and warm repeats while keeping the same model/runtime loaded. Evidence schema v4 records TTFT, prefill/decode time and throughput, process PSS snapshots, available memory, thermal status, stop reason, output budget and exact benchmark identity dimensions. Evidence is returned to the host through Android instrumentation status output rather than relying on plain test stdout.

The full matrix runner writes:

```text
build/qwen35-tuning/qwen35-tuning-evidence.jsonl
build/qwen35-tuning/qwen35-tuning-summary.csv
```

The bounded runner writes tier-separated, run-identity-specific files under its output root. The summarizer validates sample completeness and identity consistency, calculates cold metrics plus warm median/p95, sustained first-to-last drift, peak PSS, minimum available memory and maximum thermal status, and marks a case only as `eligibleForProfileSelection`. It never changes a runtime profile to `MEASURED` automatically.

## Current evidence wave

The priority evidence checkpoint is complete, but Q35-6 remains a broader certification track.

1. **0.8B bounded screening:** DONE; `t2/bt4/b128/ub64` is the bounded priority candidate.
2. **2B realistic prefill:** DONE; `t4/bt2/b128/ub64` is REJECTED and baseline `t4/bt4/b128/ub64` remains the current CPU candidate.
3. **Recurrent-state correctness:** DONE; both tiers return `KEEP_DISABLED` because partial rollback is unsupported.
4. **Profile promotion:** still blocked on broader/representative evidence plus lifecycle/memory acceptance; no bounded result is automatically `MEASURED`.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-RT-01 | DONE | Add Qwen3.5 runtime capability model keyed by backend revision. |
| Q35-RT-02 | DONE | Define approved candidate mobile context tiers and safety reserve policy. |
| Q35-RT-03 | DONE | Gate prefix/session restore and reuse capabilities for Qwen3.5; 2026-08-21 physical evidence explicitly confirms `KEEP_DISABLED` for both curated tiers. |
| Q35-RT-04 | DONE | Extend benchmark identity and persistence with exact execution-configuration identity. |
| Q35-RT-05 | DONE | Define controlled tuning matrix plus repeatable physical-device evidence tooling. |
| Q35-RT-06 | IN PROGRESS | 0.8B physical tuning track: bounded screening is DONE with `t2/bt4/b128/ub64` as the bounded priority candidate; broader/representative validation remains before profile promotion. |
| Q35-RT-07 | IN PROGRESS | 2B physical tuning track: bounded and realistic longer-prompt validation are DONE; `t4/bt2/b128/ub64` is REJECTED, baseline remains current candidate, and broader representative validation remains. |
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

The completed bounded/focused evidence satisfies search-space reduction and candidate rejection/selection only. It must remain preserved as immutable evidence rather than being overwritten or reinterpreted as the final Q35-6 matrix.

## Upstream reference

- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
