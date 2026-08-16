# Memory management

Status: active
Document type: feature-index
Owner: runtime-memory
Canonical scope: memory-management.routing
Read when: locating the memory-management target, current state, roadmap or owning workstream
Last reviewed: 2026-08-16

This is the entry point for repository-wide memory-management work. It covers runtime residency, native lifecycle, proactive admission, Android memory pressure, bounded queueing, resource observability and physical memory evidence without creating a second runtime or duplicating Qwen3.5 tuning policy.

## Objective

The Harness now has the software control plane needed to answer three questions before expensive work starts:

1. what memory is already resident;
2. what the requested model/context operation is expected to cost;
3. whether the device has enough safe headroom to allow, downshift or reject the operation.

The remaining work is evidence, not a second architecture: representative-device calibration must populate reviewed `MEASURED` cost profiles and prove recovery/headroom behavior before device-safety claims are made.

## Status at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| MEM-0 Plan and ownership | DONE | Canonical target, architecture, roadmap and workstream routing are integrated. |
| MEM-1 Shutdown finalization | DONE | Session drain, context release, model unload and backend shutdown converge deterministically. |
| MEM-2 Budget foundation | DONE | Backend-neutral observations, estimates, budgets, residency snapshots and typed admission reasons are integrated. |
| MEM-3 Runtime admission | DONE | Model/context materialization is gated before native allocation; context downshift and privacy-safe decision telemetry are integrated. |
| MEM-4 Shared-runtime residency | DONE | Warm-idle residency is bounded by explicit TTL policy without making Binder lifetime the resource owner. |
| MEM-5 Memory regression evidence | DONE | Bounded resource windows and independent PSS/available-memory regression evaluation are integrated. |
| MEM-6 Queue/backpressure bounds | DONE | Outstanding decode work, resident contexts and per-consumer host requests have explicit bounds. |
| MEM-7 Device calibration | PARTIAL | Android observation adapters plus context/model cost registries exist; representative physical `MEASURED` profiles are still missing. |
| MEM-8 Certification gate | PLANNED | Physical cleanup, headroom, reconnect, pressure and soak evidence must close the readiness gate. |

## Integrated software control loop

```text
Android ResourceSnapshot
        |
RuntimeMemoryObservation
        |
MemoryCostProfile + RuntimeResidencySnapshot
        |
MemoryAdmissionController
        |
ALLOW / DOWNSHIFT / REJECT
        |
RuntimeOrchestrator
        |
backend model/context allocation
        |
bounded evidence window + regression evaluation
```

Model and context cost lookups are identity-bound and provenance-aware. No production `MEASURED` value is inferred from GGUF file size, model name or a pure-KV formula.

## Remaining delivery

The software lanes MEM-0 through MEM-6 are complete. The remaining sequence is hardware-gated:

- **MEM-7:** run the controlled Qwen3.5/device matrix, capture model-load/context-tier peaks and recovery windows, then publish versioned `MEASURED` profiles only for exact compatible identities;
- **MEM-8:** exercise cancellation, critical pressure, warm-idle expiry, model switch/reload, overload and repeated-cycle soak, then promote readiness only when cleanup/headroom/recovery evidence passes.

A repository or emulator test may validate policy semantics and Android wiring, but it cannot promote MEM-7 or MEM-8 by itself.

## What to read

| Need | Read |
| --- | --- |
| Intended behavior and non-goals | [`target.md`](target.md) |
| Ownership and control flow | [`architecture.md`](architecture.md) |
| Current workstream state | [`current-state.md`](current-state.md) |
| Milestones, dependencies and exit gates | [`roadmap.md`](roadmap.md) |
| Native/runtime shutdown correctness | [`workstreams/lifecycle.md`](workstreams/lifecycle.md) |
| Memory budgets and admission | [`workstreams/admission-and-budgeting.md`](workstreams/admission-and-budgeting.md) |
| Shared-runtime residency and TTL | [`workstreams/shared-runtime-residency.md`](workstreams/shared-runtime-residency.md) |
| Resource metrics, regressions and soak evidence | [`workstreams/observability-and-validation.md`](workstreams/observability-and-validation.md) |

## Ownership boundaries

- `core/runtime-core` owns runtime memory policy, residency decisions, admission and backpressure.
- `backends/llama-cpp` owns native model/context lifetime and backend-specific allocation mechanics; it does not decide product admission policy.
- `observability` owns measurement contracts, resource snapshots, bounded retention and regression mathematics.
- runtime-owning apps/services compose Android probes and residency policy without moving domain logic into UI/service classes.
- `docs/qwen35` owns model-family-specific physical tuning inputs; this workstream consumes those measurements but does not duplicate model tuning policy.

## Readiness rule

Repository-side tests may prove deterministic lifecycle and policy semantics. They do not prove Android memory safety under real load. Claims about peak PSS, residual memory, thermal behavior, safe context tiers or measured defaults require representative physical-device evidence.
