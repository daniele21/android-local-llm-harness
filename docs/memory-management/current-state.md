# Memory-management current state

Status: active
Document type: workstream-state
Owner: runtime-memory
Canonical scope: memory-management.current-state
Read when: determining what memory-management behavior is already integrated, open or next
Last reviewed: 2026-08-16

Repository operational state remains owned by [`../current-state.md`](../current-state.md); this source tracks only the memory-management workstream.

## Integrated baseline

The current `dev` baseline already provides the lifecycle foundation needed for a stronger governor:

- opaque native model/context handles and RAII cleanup in the llama.cpp backend;
- one loaded model and one active decode by default;
- explicit session-owned context lifecycle;
- smallest-approved context tier selection based on prompt/output capacity;
- Android `UI_HIDDEN`, background and low-memory callbacks mapped to runtime actions;
- critical-pressure cancellation and deferred resource release;
- resource snapshots for process PSS, native heap, Java heap, available memory, low-memory and thermal state;
- Qwen3.5 tuning evidence schema that already captures process PSS and device memory/thermal dimensions;
- shared-runtime host composition that avoids loading one model copy per consumer process.

This means the main gap is not basic release mechanics. It is quantitative governance before allocation and bounded residency across multiple sessions/consumers.

## Confirmed gaps

### Shutdown convergence

`RuntimeOrchestrator.close()` can initiate cancellation while an active request still owns a session/context. Final resource release occurs asynchronously through the request terminal path. The current deferred-unload state is not designed as a permanent shutdown-pending state, so shutdown convergence needs an explicit regression-safe finalization contract.

### Resident contexts

Single decode bounds active compute but does not by itself bound the number of session contexts kept resident. Multiple sessions can therefore retain context/recurrent state simultaneously even though only one request decodes at a time.

### Proactive admission

The runtime does not currently evaluate projected memory headroom before model/context materialization. Android low-memory callbacks react after the platform reports pressure; they are not an admission-control substitute.

### Queue bounds

The decode scheduler serializes work through a priority queue. Outstanding queued requests need explicit configured limits so prompts/listeners/lifecycle objects cannot grow without bound under load.

### Shared-runtime warm residency

The process-scoped runtime intentionally supports warm reuse, but an explicit last-consumer warm-idle TTL remains open. Process liveness must not imply indefinite model residency.

### Memory regressions

Resource snapshots exist and physical Q35 evidence records memory, but generic benchmark regression policy still focuses on latency/throughput. Peak and residual memory need independent comparison semantics.

## First implementation wave

Four independent branches start from `dev`:

- `agent/mm-shutdown-lifecycle` — MEM-1 shutdown convergence;
- `agent/mm-budget-foundation` — MEM-2 neutral budget/admission primitives;
- `agent/mm-shared-runtime-ttl` — MEM-4 shared-runtime warm-idle policy;
- `agent/mm-memory-benchmarks` — MEM-5 resource-window regression mathematics.

The documentation branch is `agent/memory-management-plan`.

## Immediate next block

1. integrate the documentation plan so the memory workstream has a canonical owner;
2. validate and merge MEM-1/MEM-2/MEM-4/MEM-5 independently;
3. branch MEM-3 from the updated `dev` and wire memory admission into model/context materialization;
4. branch MEM-6 after MEM-1 to add scheduler and resident-context bounds without lifecycle merge conflicts;
5. use Q35-6 physical runs to calibrate conservative memory-cost profiles;
6. close MEM-8 only with representative cancellation, pressure, recovery and soak evidence.

## Evidence status

No new physical-device memory claim is made by this workstream yet. Existing Q35 tuning infrastructure can collect the needed PSS/available-memory/thermal evidence, but measured defaults and memory certification remain pending representative device execution.