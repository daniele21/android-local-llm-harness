# Memory-management roadmap

Status: active
Document type: roadmap
Owner: runtime-memory
Canonical scope: memory-management.roadmap
Read when: planning memory-management milestones, dependencies, parallel work or completion gates
Last reviewed: 2026-08-16

## Delivery principle

Memory governance is split into backend-neutral policy, platform composition, observability and physical evidence. Repository tests may close deterministic software milestones; only representative-device evidence may close measured calibration or certification claims.

## Current dependency graph

```text
MEM-0 plan/ownership ........ DONE
   |
   +--> MEM-1 lifecycle ..... DONE
   +--> MEM-2 budget ........ DONE
   +--> MEM-4 warm TTL ...... DONE
   +--> MEM-5 regressions ... DONE
          |          |
          +--> MEM-3 admission .... DONE
                    |
                    +--> MEM-6 bounds .... DONE
                              |
                              +--> MEM-7 calibration .... PARTIAL
                                         |
                                         +--> MEM-8 certification .... PLANNED
```

Q35 physical tuning evidence feeds MEM-7. Relevant Q35/SR physical lifecycle evidence feeds MEM-8. No emulator or repository-only run can promote those two milestones to DONE.

## Milestones

### MEM-0 — Plan and ownership

State: DONE

Integrated:

- canonical memory-management index, target, architecture, roadmap and workstream specifications;
- repository documentation routing and ownership boundaries;
- explicit separation of runtime policy, backend mechanics, observability, Android composition and Qwen-specific physical tuning.

Exit gate: satisfied.

### MEM-1 — Shutdown finalization

State: DONE
Priority: P0
Owner: `core/runtime-core`

Integrated:

- persistent shutdown intent across active cancellation/drain;
- eventual context release, model unload and backend shutdown;
- authoritative `SessionLifecycle` for request admission, close intent, drain and release reservation;
- retry-safe physical release and idempotent close behavior;
- deterministic close-during-generation/release-failure regression coverage.

Exit gate: satisfied by repository lifecycle tests; physical recovery behavior remains part of MEM-8.

### MEM-2 — Memory-budget foundation

State: DONE
Priority: P0/P1
Owner: `core/runtime-core`

Integrated:

- backend-neutral `RuntimeMemoryObservation`;
- `RuntimeMemoryBudget`, `MemoryCostEstimate` provenance and `RuntimeResidencySnapshot`;
- overflow-safe `MemoryAdmissionController` for model/context resources;
- typed allow/reject reasons including low-memory, headroom, PSS and resident-context limits;
- fail-closed behavior for required unknown observations.

Exit gate: satisfied without Android or llama.cpp dependencies.

### MEM-3 — Runtime admission and context downshift

State: DONE
Priority: P1
Owner: `core/runtime-core`
Depends on: MEM-1, MEM-2

Integrated:

- context admission immediately before native `createContext()`;
- model-load admission after old-model release and before backend initialization/native load;
- approved-tier context downshift without violating prompt/output minimum capacity;
- typed context `MEMORY_BUDGET_EXCEEDED` failure and typed model-load admission failure;
- warm model/context reuse without unnecessary re-admission;
- structured `memory.admission` ALLOW/DOWNSHIFT/REJECT telemetry with no prompt/generated content;
- integration tests proving reject paths perform zero forbidden native allocation calls.

Exit gate: satisfied for software semantics. Safe production thresholds remain evidence-gated by MEM-7.

### MEM-4 — Shared-runtime warm-idle residency

State: DONE
Priority: P1
Owner: shared-runtime host / phone-test composition

Integrated:

- explicit warm-idle TTL coordination;
- Binder disconnect treated as demand absence, not resource ownership;
- reconnect/rebind cancellation of pending expiry;
- idle unload delegated to the process-owned runtime and rescheduled while busy;
- critical pressure override and deterministic injected clock/scheduler tests.

Exit gate: satisfied for bounded residency semantics. The current candidate TTL is tuned only after MEM-7/8 evidence.

### MEM-5 — Memory regression evidence

State: DONE
Priority: P1
Owner: `observability`

Integrated:

- bounded `MemoryWindowRecorder` over canonical resource snapshots;
- baseline/peak/residual PSS and minimum-available-memory summarization;
- independent memory regression policy and typed PASS/WARN/FAIL output;
- WARN behavior for insufficient/missing comparable measurements;
- regression evaluation independent from throughput/latency outcomes.

Exit gate: satisfied for software capture/comparison. Representative baselines are still physical evidence.

### MEM-6 — Queue and resident-context bounds

State: DONE
Priority: P1/P2
Owner: `core/runtime-core`, shared host boundary
Depends on: MEM-1, MEM-3

Integrated:

- global decode capacity with default maximum 64 outstanding active + queued requests;
- atomic capacity reservation/release across completion, cancellation, enqueue failure and shutdown;
- typed `DecodeQueueCapacityExceededException`;
- resident-context maximum enforced by memory admission before context creation;
- shared-host quotas for connections, sessions and outstanding requests per consumer (`8/8/16` defaults);
- scheduler close-race regression coverage.

Exit gate: satisfied: queued work, consumer requests and resident contexts all have explicit configured bounds.

### MEM-7 — Physical calibration and memory-cost profiles

State: PARTIAL
Priority: P1
Owner: Q35 evidence + runtime-memory consumption
Depends on: MEM-3, MEM-5, Q35 physical tuning

Software already integrated:

- Android `ResourceSnapshotProvider` to `RuntimeMemoryObservation` adapter;
- identity-bound context cost registry;
- identity-bound model-load cost registry;
- `THEORETICAL` / `CANDIDATE` / `MEASURED` provenance gates;
- fail-closed profile lookup by exact model profile/digest and backend ID/revision.

Physical work still required:

- capture cold/model/context/generation/recovery windows on representative devices;
- preserve exact artifact/backend/harness/device/runtime identity;
- derive reviewed model/context costs and conservative safety reserve;
- promote only compatible records to `MEASURED`;
- identify supported safe context tiers and thermal/headroom envelope.

Exit gate: not satisfied until representative physical measurements exist.

### MEM-8 — Memory certification gate

State: PLANNED
Priority: P1
Depends on: MEM-1 through MEM-7 plus relevant Q35/SR device evidence

Required physical scenarios:

- repeated load/generate/release/reload cycles;
- cancellation during active work;
- critical memory-pressure cleanup and recovery generation;
- warm-idle expiry plus disconnect/reconnect;
- model switch/reload;
- queue/admission overload;
- residual-memory plateau/trend and minimum available-memory checks;
- thermal observation under the exact supported build identity.

Exit gate: the supported build has representative evidence that memory use remains bounded, cleanup converges and the runtime recovers after pressure/cancellation.

## Remaining execution order

1. Run MEM-7 controlled physical calibration for supported Qwen3.5/device identities.
2. Review and version `MEASURED` model/context cost records and safety margins.
3. Replay admission/downshift behavior against those exact records on device.
4. Run the MEM-8 lifecycle/pressure/reconnect/overload/soak matrix.
5. Promote readiness only after evidence is attached to the exact supported build identity.

## Validation expectations

Software changes continue to use scoped module tests plus repository guards/Android validation. JNI changes additionally require native host/packaging validation. Physical evidence remains a separate release gate and must never be inferred from emulator survival or a single successful allocation.
