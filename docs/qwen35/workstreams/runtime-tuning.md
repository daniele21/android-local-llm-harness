# Qwen3.5 runtime, context and Android tuning

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.runtime-tuning
Read when: changing Qwen3.5 context policy, cache/session reuse, Android CPU parameters or performance benchmark keys
Last reviewed: 2026-08-07

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

The profile can control only parameters the backend exposes and the harness can validate, such as:

- generation CPU threads;
- batch/prefill threads;
- batch size;
- micro-batch size;
- approved context tier;
- mmap/mlock policy where meaningful on Android;
- flash-attention flag only when backend/model/device evidence supports it;
- KV/cache data types only when semantically and operationally valid.

Do not add chipset-specific JNI branches when llama.cpp/runtime CPU dispatch already owns the kernel choice.

## Context policy

Qwen's current 0.8B/2B model cards advertise a very large default context. That is not an Android allocation target.

The harness must retain the current Auto principle:

```text
prompt tokens
  + requested output budget
  + safety reserve
    -> smallest approved Qwen3.5 mobile context tier
```

Initial candidate tiers may be explored at 2K/4K/8K/16K or similar, but no tier becomes a default until physical-device memory and latency evidence supports it.

Manual context remains bounded by model/backend/device policy.

## Hybrid/recurrent state

Upstream `llama.cpp` Qwen3.5 uses recurrent memory for linear-attention layers. Therefore:

- do not equate Qwen3.5 session state with pure KV cache;
- do not enable prefix snapshot, checkpoint restore or context reuse merely because the generic API exists;
- record backend revision when validating each reuse capability;
- treat unsupported/unknown reuse as disabled;
- verify cancellation, model switch, memory pressure and context close against recurrent-state cleanup.

## Benchmark identity

A Qwen3.5 performance result is comparable only when its key includes at least:

```text
artifact SHA-256
quantization
Qwen3.5 tier
llama.cpp revision/build
device model
Android version
CPU capability summary
threads / batch threads
batch / ubatch
context tier
thinking mode
generation profile version
cold/warm classification
```

Existing benchmark storage should be extended rather than forked.

## Separate tuning tracks

Do not assume the same configuration is optimal for 0.8B and 2B.

### 0.8B track

Optimize for:

- lowest supported memory footprint;
- fast TTFT and responsive short tasks;
- stable thinking/non-thinking streaming;
- thermal sustainability across repeated runs.

### 2B track

Optimize for:

- higher quality while preserving acceptable phone responsiveness;
- bounded peak PSS and context growth;
- stable prefill/decode throughput;
- thermal and battery behavior under repeated longer generations.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-RT-01 | PLANNED | Add Qwen3.5 runtime capability model keyed by backend revision. |
| Q35-RT-02 | PLANNED | Define approved candidate mobile context tiers and safety reserve policy. |
| Q35-RT-03 | PLANNED | Gate prefix/session restore and reuse capabilities for Qwen3.5. |
| Q35-RT-04 | PLANNED | Extend benchmark identity with exact Qwen3.5 artifact/backend/configuration fields. |
| Q35-RT-05 | PLANNED | Define controlled tuning matrix for threads, batch, ubatch and context. |
| Q35-RT-06 | PLANNED | Run 0.8B tuning matrix on representative physical Android devices. |
| Q35-RT-07 | PLANNED | Run 2B tuning matrix independently on the same device classes. |
| Q35-RT-08 | PLANNED | Select versioned default profiles from measured memory/TTFT/throughput/thermal evidence. |
| Q35-RT-09 | PLANNED | Validate model switch, memory pressure, cancellation and idle unload with Qwen3.5. |
| Q35-RT-10 | PLANNED | Preserve diagnostic overrides without allowing them to masquerade as certified defaults. |

## Acceptance criteria

Q35-4 and Q35-5 are complete when:

- context selection never blindly allocates the model-advertised maximum;
- runtime capability flags default conservative and are backed by the exact backend revision;
- 0.8B and 2B have separately measured default profiles;
- benchmark comparisons reject mismatched identity dimensions;
- selected defaults have recorded TTFT, prefill/decode throughput, peak memory and thermal evidence;
- cancellation, close, switch and memory-pressure paths leave the runtime reusable;
- unvalidated cache/snapshot optimizations remain disabled;
- physical-device evidence is ready for certification consumption.

## Upstream reference

- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
