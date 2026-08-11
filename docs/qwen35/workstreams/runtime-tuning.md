# Qwen3.5 runtime, context and Android tuning

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.runtime-tuning
Read when: changing Qwen3.5 context policy, cache/session reuse, Android CPU parameters or performance benchmark keys
Last reviewed: 2026-08-09

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
thinking mode
cold/warm classification
```

## Candidate runtime profiles

0.8B and 2B remain separate tuning tracks. Both profiles are currently `CANDIDATE`, not `MEASURED`.

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

## Physical-device evidence runner

Run:

```bash
bash scripts/run-qwen35-tuning-matrix.sh \
  --model-08b /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --model-2b /path/Qwen3.5-2B-Q4_K_M.gguf \
  --device <adb-serial> \
  --repetitions 3
```

`--device` is optional when exactly one device is online. The runner verifies both curated SHA-256 values before installation/testing and requires `arm64-v8a`.

For each tuning case the instrumentation test records one true cold run and at least three warm runs while keeping the same model/runtime loaded. Evidence schema v2 records TTFT, prefill/decode time and throughput, process PSS snapshots, available memory, thermal status, stop reason and exact benchmark identity dimensions.

The runner writes:

```text
build/qwen35-tuning/qwen35-tuning-evidence.jsonl
build/qwen35-tuning/qwen35-tuning-summary.csv
```

The summarizer validates sample completeness and identity consistency, calculates cold metrics plus warm median/p95, peak PSS, minimum available memory and maximum thermal status, and marks a case only as `eligibleForProfileSelection`. It never changes a runtime profile to `MEASURED` automatically.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-RT-01 | DONE | Add Qwen3.5 runtime capability model keyed by backend revision. |
| Q35-RT-02 | DONE | Define approved candidate mobile context tiers and safety reserve policy. |
| Q35-RT-03 | DONE | Gate prefix/session restore and reuse capabilities for Qwen3.5. |
| Q35-RT-04 | DONE | Extend benchmark identity and persistence with exact execution-configuration identity. |
| Q35-RT-05 | DONE | Define controlled tuning matrix plus repeatable physical-device evidence tooling. |
| Q35-RT-06 | PLANNED | Run 0.8B tuning matrix on representative physical Android devices. |
| Q35-RT-07 | PLANNED | Run 2B tuning matrix independently on the same device classes. |
| Q35-RT-08 | PLANNED | Select versioned default profiles from measured memory/TTFT/throughput/thermal evidence. |
| Q35-RT-09 | PLANNED | Validate model switch, memory pressure, cancellation and idle unload with Qwen3.5 measured configurations. |
| Q35-RT-10 | DONE | Diagnostic candidate profiles and evidence summarization cannot masquerade as measured/certified defaults. |

## Acceptance criteria

Q35-5 is complete because context selection is bounded, exact-backend capabilities fail closed and unvalidated recurrent-state optimizations remain disabled.

Q35-6 remains `IN PROGRESS` until:

- 0.8B and 2B have separately measured default profiles;
- selected defaults have recorded TTFT, prefill/decode throughput, peak memory and thermal evidence;
- cancellation, close, switch and memory-pressure paths leave the measured runtime reusable;
- physical-device evidence is ready for certification consumption.

## Upstream reference

- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
