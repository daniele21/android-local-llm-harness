# Memory-management architecture

Status: active
Document type: architecture
Owner: runtime-memory
Canonical scope: memory-management.architecture
Read when: changing ownership, dependency direction or control flow for runtime memory management
Last reviewed: 2026-08-16

## Current foundation

The integrated runtime already has strong lifecycle foundations:

- one loaded model and one active decode by default;
- opaque native handles backed by RAII-owned model/context records;
- per-session context ownership and explicit release;
- Android trim/low-memory callbacks mapped into runtime memory-pressure actions;
- caller-driven resource snapshots for PSS, native heap, Java heap, available memory and thermal state;
- physical Qwen3.5 tuning evidence that already records memory and thermal signals.

The missing architectural layer is a proactive governor between request planning and expensive resource materialization.

## Target control plane

```text
AndroidResourceSnapshotProvider
          |
          v
  RuntimeMemoryObservation
          |
          +---------------------+
          |                     |
          v                     v
MemoryCostRegistry      RuntimeResidencySnapshot
          |                     |
          +----------+----------+
                     v
          MemoryAdmissionController
                     |
          +----------+-----------+
          |          |           |
          v          v           v
        ALLOW     DOWNSHIFT    REJECT
          |          |           |
          +----------+           +--> typed failure + telemetry
                     v
              RuntimeOrchestrator
                     |
          +----------+-----------+
          |                      |
          v                      v
      load model             create context
          |                      |
          +----------+-----------+
                     v
               llama.cpp backend
                     |
                     v
             Resource telemetry
```

## Ownership

### `core/runtime-core`

Owns policy and orchestration:

- memory-budget configuration;
- backend-neutral observations and cost estimates;
- resident-resource accounting;
- admission decisions;
- context-tier downshift policy;
- bounded queue/backpressure policy;
- memory-pressure actions;
- shutdown convergence and warm-idle release hooks.

This module must not depend on Android APIs or observability storage implementations.

### `backends/llama-cpp`

Owns backend mechanics:

- native model/context allocation and release;
- llama.cpp runtime initialization/shutdown;
- mmap/mlock and backend-supported runtime parameters;
- native handle registries and cancellation registry;
- backend-specific metrics if llama.cpp exposes them safely.

It does not decide whether the application may consume a device memory budget.

### `observability`

Owns measurement and evidence contracts:

- Android resource snapshots;
- bounded retention;
- memory regression mathematics;
- correlation of resource windows with controlled benchmark/evaluation identities;
- diagnostics presentation inputs.

Observability remains best-effort. Failure to persist a snapshot never corrupts inference or cleanup.

### Runtime-owning applications/services

Own composition only:

- create the Android resource probe;
- provide runtime memory policy/configuration;
- translate process/service lifecycle into shared-runtime residency signals;
- schedule warm-idle expiry without duplicating core admission decisions.

## Core domain model

The budget layer uses neutral value objects rather than Android or llama.cpp types.

```text
RuntimeMemoryObservation
  processPssBytes?
  nativeHeapBytes?
  javaHeapUsedBytes?
  availableMemoryBytes?
  lowMemory?

RuntimeMemoryBudget
  maxProcessPssBytes?
  minimumAvailableBytes
  safetyReserveBytes
  maxResidentContexts

MemoryCostEstimate
  residentBytes
  peakAdditionalBytes
  source: THEORETICAL | CANDIDATE | MEASURED

RuntimeResidencySnapshot
  modelLoaded
  residentContexts
  active/queued work

MemoryAdmissionRequest
  resource kind
  requested context tier when applicable
  cost estimate

MemoryAdmissionDecision
  Allow
  Downshift(targetContext)
  Reject(reason)
```

Unknown observations stay nullable. The controller owns whether a configured profile allows a fallback when a measurement is unavailable.

## Admission sequence

For context materialization:

```text
prompt plan
   |
resolve required token capacity
   |
select smallest approved tier
   |
lookup conservative memory cost
   |
read current observation/residency
   |
run admission
   |
   +-- ALLOW ------> create requested context
   |
   +-- DOWNSHIFT --> verify lower tier still satisfies required tokens
   |                  -> create smaller context
   |
   +-- REJECT ------> typed resource failure
```

Downshift never truncates a prompt or silently reduces requested output. It is legal only when a smaller approved tier still contains `prompt + output + safety reserve`.

## Model admission

The existing one-model invariant prevents transient dual-model residency during model switches. The admission layer preserves that ordering:

1. verify no active session/request blocks switching;
2. establish whether the target model can be admitted with configured headroom;
3. unload the previous model;
4. load the new model;
5. observe and record the resulting resource state.

A later implementation may use a measured model-load peak estimate, but it must not keep old and new models resident merely to make rollback convenient.

## Shutdown convergence

Shutdown is a state transition, not a best-effort sequence.

```text
close requested
   |
mark shutdown pending
   |
cancel queued/running work
   |
mark sessions closing
   |
release contexts as requests terminate
   |
when sessions == 0 and scheduler idle
   |
unload model
   |
shutdown backend
   |
terminal CLOSED/IDLE resource state
```

A shutdown-pending flag remains set until finalization. It cannot be cleared by an intermediate cleanup path. This prevents an active cancellation race from leaving the native model/backend resident after `close()` returns from its initiating thread.

## Shared-runtime residency

The Binder host may intentionally keep a model warm after the last consumer disconnects, but residency is bounded by policy:

```text
last consumer disconnects
        |
        v
   WARM_IDLE(deadline)
        |
   +----+----------------+
   |                     |
new consumer        deadline / pressure
   |                     |
reuse runtime         unload idle model
```

Critical Android memory pressure overrides the TTL and releases resources immediately according to the core memory-pressure policy.

## Queue and backpressure

Single decode limits compute concurrency but not queued Java objects. The target scheduler adds explicit global and per-consumer outstanding-work limits. Rejection happens before storing unbounded prompts/listeners/lifecycle objects.

Queue limits are independent from memory admission: a request may be rejected because the work queue is full even if the device has enough RAM.

## Evidence loop

Physical-device runs close the control loop:

```text
candidate configuration
      -> run controlled workload
      -> capture PSS/heap/available/thermal
      -> compute peak + residual + recovery
      -> review evidence
      -> publish measured MemoryCostProfile
      -> admission policy consumes profile
```

Q35 tuning remains the owner of model-family/device tuning evidence. The memory workstream defines how measured cost is consumed by generic runtime admission and release validation.
