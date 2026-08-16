# Memory-management roadmap

Status: active
Document type: roadmap
Owner: runtime-memory
Canonical scope: memory-management.roadmap
Read when: planning memory-management milestones, dependencies, parallel work or completion gates
Last reviewed: 2026-08-16

## Delivery principle

Memory work is split into small vertical slices with explicit owners. Parallel branches must avoid overlapping lifecycle files unless one branch is intentionally based on another. Repository tests prove policy and cleanup semantics; physical-device evidence is required before measured memory-safety claims.

## Dependency graph

```text
MEM-0 plan
  |
  +----------------+----------------+----------------+
  |                |                |                |
MEM-1 lifecycle  MEM-2 budget     MEM-4 TTL       MEM-5 regressions
  |                |                |                |
  +-------+--------+                |                |
          v                         |                |
       MEM-3 admission              |                |
          |                         |                |
          +------------+------------+                |
                       v                             |
                    MEM-6 bounds                     |
                       |                             |
                       +-------------+---------------+
                                     v
                                  MEM-7 device calibration
                                     |
                                     v
                                  MEM-8 certification
```

MEM-6 may start after MEM-1 lands because both change scheduler/runtime lifecycle behavior. MEM-7 also depends on Q35-6 physical tuning evidence. MEM-8 consumes Q35-7 and SR-6 evidence where those flows exercise the same runtime.

## Milestones

### MEM-0 — Plan and ownership

State: ACTIVE

Deliverables:

- canonical memory-management index, target, architecture and roadmap;
- focused lifecycle, budgeting, shared-runtime residency and validation specifications;
- repository documentation routing;
- explicit first-wave PR ownership and dependency plan.

Exit gate: the workstream is navigable from `docs/README.md` and current repository state without duplicating Q35 or observability ownership.

### MEM-1 — Shutdown finalization

State: PLANNED
Priority: P0
Owner: `core/runtime-core`

Deliverables:

- make shutdown a persistent pending state until all sessions/requests are drained;
- guarantee eventual model unload and backend shutdown when close races active cancellation;
- keep repeated close calls idempotent;
- add deterministic regression coverage for close-during-generation and reuse/terminal invariants.

Exit gate: no test path can leave a model/backend resident after final shutdown convergence.

### MEM-2 — Memory-budget foundation

State: PLANNED
Priority: P0/P1
Owner: `core/runtime-core`

Deliverables:

- backend-neutral memory observation value object;
- configured budget/headroom policy;
- cost estimate and provenance model;
- resident-resource snapshot;
- typed admission decision and reason codes;
- deterministic controller tests for allow/reject/unknown/critical-pressure cases.

Exit gate: admission mathematics is unit-testable without Android, llama.cpp or observability storage.

### MEM-3 — Runtime admission and context downshift

State: PLANNED
Priority: P1
Owner: `core/runtime-core`
Depends on: MEM-1, MEM-2

Deliverables:

- gate model/context materialization through the admission controller;
- adapt existing context tier selection to memory headroom without violating required token capacity;
- expose typed resource rejection;
- record admission outcome/reason without prompts or generated content;
- preserve one-model/one-decode invariants.

Exit gate: unsafe context allocation is rejected or safely downshifted before JNI allocation.

### MEM-4 — Shared-runtime warm-idle residency

State: PLANNED
Priority: P1
Owner: shared-runtime host composition / phone-test composition

Deliverables:

- explicit warm-idle state and TTL policy after the last bound consumer disconnects;
- immediate release on configured critical pressure;
- cancellation of pending expiry when a consumer reconnects;
- no model load merely from observing/binding the service;
- deterministic clock/scheduler tests.

Exit gate: shared runtime cannot retain a warm idle model indefinitely solely because the process remains alive.

### MEM-5 — Memory regression evidence

State: PLANNED
Priority: P1
Owner: `observability`

Deliverables:

- bounded resource-window summarization for baseline/peak/residual PSS and available-memory floor;
- memory regression policy separate from latency/throughput thresholds;
- typed PASS/WARN/FAIL comparison output;
- no fabricated metrics when snapshots are unavailable;
- docs linking resource evidence to benchmark/tuning identity.

Exit gate: a configuration with materially worse memory behavior can fail a memory comparison even when throughput improves.

### MEM-6 — Queue and resident-context bounds

State: PLANNED
Priority: P1/P2
Owner: `core/runtime-core`, consumer capability policy
Depends on: MEM-1, MEM-3

Deliverables:

- global queued-work maximum;
- per-consumer outstanding-work limit on the shared boundary;
- resident-context cap enforced before context creation;
- typed backpressure rejection and queue telemetry;
- cancellation/close semantics remain deterministic at all limits.

Exit gate: prompts/listeners/session contexts cannot grow without an explicit configured bound.

### MEM-7 — Physical calibration and memory-cost profiles

State: PLANNED
Priority: P1
Owner: Q35 evidence + runtime-memory consumption
Depends on: MEM-3, MEM-5, Q35-6

Deliverables:

- derive model/context/batch memory deltas from representative physical-device runs;
- distinguish theoretical, candidate and measured profiles;
- bind measured costs to exact artifact/backend/runtime identity;
- record peak PSS, minimum available memory, thermal ceiling and post-release residual;
- choose conservative safety margins from evidence rather than a universal hard-coded percentage.

Exit gate: supported Qwen3.5 tiers have reviewable memory-cost evidence for target device classes.

### MEM-8 — Memory certification gate

State: PLANNED
Priority: P1
Depends on: MEM-1 through MEM-7 plus relevant Q35-7/SR-6 device evidence

Deliverables:

- repeated load/generate/cancel/close/reload soak cycles;
- memory-pressure recovery and runtime-reuse evidence;
- residual-memory trend check after release;
- queue/admission overload scenarios;
- release checklist language that prevents emulator-only memory-readiness claims.

Exit gate: the exact supported build has representative evidence that memory use remains bounded, cleanup converges, and the runtime recovers after pressure/cancellation.

## First-wave PR layout

The initial branches are intentionally independent:

| Branch | Milestone | Primary paths | Can merge independently? |
| --- | --- | --- | --- |
| `agent/mm-shutdown-lifecycle` | MEM-1 | `core/runtime-core` lifecycle/tests | Yes |
| `agent/mm-budget-foundation` | MEM-2 | new runtime memory policy files/tests | Yes |
| `agent/mm-memory-benchmarks` | MEM-5 | `observability/benchmark-engine` + tests | Yes |
| `agent/mm-shared-runtime-ttl` | MEM-4 | runtime-host app/service composition + tests | Yes |

`agent/memory-management-plan` owns documentation only and may merge before or alongside the first wave.

## Integration waves

### Wave 1 — independent foundations

Merge MEM-1, MEM-2, MEM-4 and MEM-5 in any order after their own gates pass.

### Wave 2 — governor integration

Create a fresh branch from the then-current `dev` for MEM-3. After MEM-1 is integrated, create MEM-6 from current `dev` so scheduler/backpressure changes do not conflict with shutdown hardening.

### Wave 3 — evidence-backed calibration

Run Q35-6 with resource evidence, derive reviewed memory-cost profiles, then validate MEM-3/MEM-6 behavior on device.

### Wave 4 — certification

Combine Q35-7 lifecycle validation, SR-6 shared-runtime process/reconnect cases and memory soak evidence into MEM-8 readiness gates.

## Validation expectations

Each code PR runs the narrowest module gate plus direct consumers. Shared public contracts or multi-module changes use the repository-wide Android gate. JNI changes additionally run native host tests. No first-wave PR requires JNI changes.

Physical evidence is explicitly pending until executed on representative hardware.