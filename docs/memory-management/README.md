# Memory management

Status: active
Document type: feature-index
Owner: runtime-memory
Canonical scope: memory-management.routing
Read when: locating the memory-management target, current state, roadmap or owning workstream
Last reviewed: 2026-08-16

This is the entry point for repository-wide memory-management work. It covers runtime residency, native lifecycle, proactive admission, Android memory pressure, bounded queueing, resource observability and physical memory evidence without creating a second runtime or duplicating Qwen3.5 tuning policy.

## Objective

Move the Harness from lifecycle-safe, reactive memory cleanup to a bounded and measurable resource governor that can answer three questions before expensive work starts:

1. what memory is already resident;
2. what the requested model/context operation is expected to cost;
3. whether the device has enough safe headroom to admit, downshift or reject the operation.

The existing invariants remain: one loaded model, one active decode by default, opaque native ownership, explicit session/context lifecycle, and fail-safe cleanup on cancellation or pressure.

## Status at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| MEM-0 Plan and ownership | ACTIVE | Canonical target, architecture, roadmap and workstream routing are being established. |
| MEM-1 Shutdown finalization | PLANNED | Close/cancellation must deterministically converge to context release, model unload and backend shutdown. |
| MEM-2 Budget foundation | PLANNED | Add backend-neutral memory observations, estimates, limits and admission outcomes. |
| MEM-3 Runtime admission | PLANNED | Gate model/context materialization before unsafe allocations and support policy-driven context downshift. |
| MEM-4 Shared-runtime residency | PLANNED | Bound warm-idle residency with explicit TTL and disconnect semantics. |
| MEM-5 Memory regression evidence | PLANNED | Treat peak/residual memory as benchmark and validation signals alongside latency and throughput. |
| MEM-6 Queue/backpressure bounds | PLANNED | Bound outstanding work globally and per consumer before Java-heap growth becomes unbounded. |
| MEM-7 Device calibration | PLANNED | Derive versioned memory-cost evidence from representative physical-device runs. |
| MEM-8 Certification gate | PLANNED | Require cleanup, headroom and recovery evidence before memory-management readiness claims. |

## Parallel delivery model

The first wave is intentionally split by file ownership so it can progress in parallel:

- **Lane A — MEM-1:** runtime shutdown finalization and cleanup tests;
- **Lane B — MEM-2:** new budget/admission domain primitives and unit tests;
- **Lane C — MEM-4:** shared-runtime warm-idle TTL policy and host tests;
- **Lane D — MEM-5:** memory regression mathematics over existing resource snapshots.

The second wave integrates these foundations:

- **MEM-3** depends on MEM-1 and MEM-2 because `RuntimeOrchestrator` becomes the admission boundary;
- **MEM-6** follows MEM-1 because scheduler shutdown and queue-bound changes touch the same lifecycle owner;
- **MEM-7** depends on MEM-3/MEM-5 plus Q35-6 physical tuning evidence;
- **MEM-8** closes only after representative Q35-7/SR-6 lifecycle and device evidence.

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
- `backends/llama-cpp` owns native model/context lifetime and backend-specific memory mechanics; it does not decide product admission policy.
- `observability` owns measurement contracts, resource snapshots, bounded retention and regression mathematics.
- runtime-owning apps/services compose Android probes and residency policy without moving domain logic into UI/service classes.
- `docs/qwen35` owns model-family-specific measured tuning inputs; this workstream consumes those measurements but does not duplicate Qwen3.5 runtime tuning.

## Readiness rule

Repository-side tests may prove deterministic lifecycle and policy semantics. They do not prove Android memory safety under real load. Claims about peak PSS, residual memory, thermal behavior, safe context tiers or measured defaults require representative physical-device evidence.