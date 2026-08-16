# Memory observability and validation

Status: active
Document type: feature-specification
Owner: runtime-memory
Canonical scope: memory-management.observability-validation
Read when: changing memory metrics, regression policy, soak tests or physical memory evidence
Last reviewed: 2026-08-16

## Goal

Make memory behavior reviewable across changes without confusing allocator retention with a leak or emulator data with physical-device evidence.

## Existing signals

The Android resource probe already exposes nullable process PSS, native heap, Java heap used, available system memory, platform low-memory state and thermal status. These remain the canonical raw resource observations.

The memory workstream adds summarization/comparison rather than creating a second resource probe.

## Resource windows

A controlled workload may associate a bounded resource window with one benchmark/evidence identity. Useful derived values include:

- baseline PSS before work;
- peak PSS during work;
- post-release PSS after a defined recovery interval;
- peak delta from baseline;
- residual delta after release;
- minimum available system memory;
- maximum thermal status;
- sample count and missing-signal status.

A window is invalid for a metric when too few non-null samples exist. Missing values remain unavailable.

## Regression policy

Memory regression policy is independent from latency/throughput policy. Candidate thresholds are configured and reviewed rather than silently reused from existing performance ratios.

Comparison can fail when, for the same controlled identity, current evidence exceeds allowed peak/residual memory growth or crosses a minimum available-memory floor.

A throughput improvement does not cancel a memory regression. UIs may present both dimensions together, but the underlying checks remain separate and typed.

## Leak versus retained memory

The validation model does not require memory to return to the exact cold baseline after every request. Android, native allocators, mmap/page cache and llama.cpp internals may retain reusable pages.

The stronger leak signal is repeated-cycle growth:

```text
load/generate/release x N
   |
measure post-release residual each cycle
   |
stable plateau -> retained/cache candidate
monotonic/unbounded trend -> investigate leak/unbounded state
```

Soak evidence records the trend and cleanup events rather than declaring a leak from one high snapshot.

## Test matrix

Repository tests:

- resource-window summarization with nullable data;
- peak/min/residual calculations;
- policy boundary comparisons;
- no baseline/comparison when samples are insufficient;
- retention bounds for resource evidence;
- lifecycle tests assert logical resource counts independent from PSS.

Physical-device tests:

- cold model load and unload;
- context tiers for both supported Qwen3.5 sizes;
- repeated warm generation cycles;
- session/context close;
- cancellation during prefill/decode;
- critical memory-pressure release;
- shared-runtime warm-idle expiry;
- model switch/reload;
- queue/admission overload;
- recovery generation after cleanup.

## Evidence identity

Physical memory evidence reuses exact artifact/backend/harness/device/runtime identity from Q35 tuning where possible. Memory-cost profiles are never generalized across artifact/backend revisions merely because the marketing model name is the same.

## Release interpretation

Repository CI can prove deterministic policy mathematics and lifecycle state. Emulator instrumentation can prove Android wiring. Only representative physical runs support statements about peak PSS, safe context tiers, memory headroom, thermal behavior or leak/recovery characteristics on target devices.
