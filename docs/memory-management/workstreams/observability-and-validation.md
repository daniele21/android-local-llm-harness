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

The Android resource probe exposes nullable process PSS, native heap, Java heap used, available system memory, platform low-memory state and thermal status. These remain the canonical raw resource observations.

The runtime admission adapter consumes the same snapshots; observability does not create a second Android probe.

## Resource windows

`MemoryWindowRecorder` retains a strict bounded window of canonical `ResourceSnapshot` samples and supports deterministic phase-boundary injection/reset for controlled runs.

`MemoryWindowSummarizer` currently derives:

- sample count;
- baseline PSS;
- peak PSS;
- post-window residual PSS;
- peak delta from baseline;
- residual delta after release;
- minimum available system memory.

Missing values remain unavailable. Native-heap and thermal fields remain present in raw snapshots but are not currently thresholded by `MemoryRegressionEvaluator`.

## Regression policy

Memory regression policy is independent from latency/throughput policy. `MemoryRegressionEvaluator` compares controlled windows using:

- maximum peak-PSS ratio;
- maximum residual-PSS ratio;
- optional minimum available-memory floor;
- minimum sample count.

The result is typed PASS/WARN/FAIL. Insufficient or incomparable evidence produces WARN rather than fabricated zeroes.

A throughput improvement does not cancel a memory regression.

## Admission telemetry

Configured memory governors emit `memory.admission` structured logs through the existing telemetry repository.

Allowed fields are policy/resource evidence only: resource, ALLOW/DOWNSHIFT/REJECT outcome, typed reasons, cost-profile identity/provenance and resident/peak estimate, plus requested/effective context tier when relevant.

Prompts, generated output and document content are not admitted to these records.

## Leak versus retained memory

Validation does not require memory to return to the exact cold baseline after every request. Android, native allocators, mmap/page cache and llama.cpp internals may retain reusable pages.

The stronger signal is repeated-cycle growth:

```text
load/generate/release x N
   |
measure post-release residual each cycle
   |
stable plateau -> retained/cache candidate
monotonic/unbounded trend -> investigate leak/unbounded state
```

Soak evidence records the trend and cleanup events rather than declaring a leak from one high snapshot.

## Repository test matrix

Repository coverage includes:

- bounded snapshot retention and oldest-sample eviction;
- nullable resource-window summarization;
- peak/minimum/residual calculations;
- regression boundary comparisons and insufficient-data WARN behavior;
- lifecycle logical resource counts independent from PSS;
- model/context admission rejection before native allocation;
- privacy-safe admission outcome/reason telemetry.

These tests prove deterministic semantics, not target-device memory safety.

## Physical-device matrix

Representative-device evidence still covers:

- cold model load and unload;
- supported context tiers for Qwen3.5 profiles;
- repeated warm generation cycles;
- session/context close;
- cancellation during active work;
- critical memory-pressure release;
- shared-runtime warm-idle expiry and reconnect;
- model switch/reload;
- queue/admission overload;
- recovery generation after cleanup;
- residual trend and thermal state across controlled cycles.

## Evidence identity

Physical memory evidence preserves exact artifact/backend/harness/device/runtime identity. Memory-cost profiles are never generalized across artifact/backend revisions merely because the marketing model name is the same.

Only reviewed compatible physical evidence may promote a cost record to `MEASURED`.

## Release interpretation

Repository CI can prove policy mathematics, bounds and lifecycle state. Emulator instrumentation can prove Android wiring. Only representative physical runs support statements about peak PSS, safe context tiers, headroom, thermal behavior or leak/recovery characteristics on target devices.
