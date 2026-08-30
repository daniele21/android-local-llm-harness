# Bounded multi-model residency

Status: active
Document type: target-specification
Owner: runtime-memory
Canonical scope: memory-management.bounded-multi-model-residency
Read when: changing resident-model capacity, cross-model activation, model eviction, aggregate model admission or multi-resident lifecycle semantics
Last reviewed: 2026-08-30

## Goal

Define the contract for keeping more than one model resident in the shared Android runtime without creating a second loader/cache, weakening memory admission or enabling concurrent production decode.

This is the MRES-00 design contract from the LLUP workstream. It is intentionally implementation-ready but not an opt-in to capacity greater than `1`: implementation waits for the llama.cpp promotion decision, and production capacity remains `1` until representative physical evidence qualifies a larger bounded policy.

## Existing owners that must evolve

The current runtime already has the required ownership boundaries:

- `ModelResidencyLifecycle` owns in-memory model-handle identity and load/unload transitions, today for one model;
- `RuntimeOrchestrator` serializes resource mutation, creates sessions, performs model switching and owns terminal cleanup;
- `ActivationResidencyCoordinator` connects activation leases to residency protection, today by rejecting a different digest while another digest is protected;
- `UseCaseActivationLeaseRegistry` owns activation/application/use-case identity and can query leases by model digest;
- `MemoryAdmissionController` owns headroom and projected-allocation admission;
- `SingleDecodeScheduler` globally serializes production decode and remains the decode-concurrency owner;
- `WarmIdleResidencyController` and memory-pressure handling decide when idle resources should be released without taking ownership of native handles;
- `backends/llama-cpp` owns native model/context allocation and release, not product residency policy.

Multi-residency extends these owners. It must not add an independent model cache, native handle registry, scheduler or eviction subsystem beside them.

## Compatibility invariant

`residentModelCapacity = 1` is the compatibility policy.

Under capacity `1`, externally observable runtime behavior must remain equivalent to the current single-resident implementation:

- the same model/profile is reused warm;
- a different model is not loaded while a session/request makes the current model non-evictable;
- an eligible idle model is released before a different model takes its slot;
- normal warm-idle and memory-pressure release semantics stay bounded;
- shutdown converges to zero resident models and zero native model handles;
- production decode remains globally single-active.

The capacity-1 proof is a mandatory MRES-20 gate rather than an assumption.

## Residency identity and state

A residency entry is keyed by the exact logical/native identity required to decide reuse:

```text
ModelResidencyKey = profileId + modelDigest
```

The digest prevents aliasing different artifacts; the profile ID prevents reusing one native handle when model-level runtime semantics require a distinct profile identity.

Each key independently uses the existing transition vocabulary:

```text
ABSENT -> LOADING -> RESIDENT -> UNLOADING -> ABSENT
             |                      |
             +---- load failure     +---- unload failure -> RESIDENT
                   -> ABSENT
```

The lifecycle owns one bounded set of entries rather than one second-level cache around a singleton record.

A `LOADING` or `UNLOADING` entry reserves its slot until the transition reaches a terminal state. Capacity therefore cannot be exceeded by counting only fully reusable `RESIDENT` entries.

## Capacity policy

Resident-model capacity is explicit runtime policy:

```text
residentModelCapacity >= 1
production default = 1
```

Capacity is a hard upper bound on lifecycle entries that reserve physical-model capacity (`LOADING`, `RESIDENT` or `UNLOADING`). A load may start only when:

1. the exact key is not already loading/resident;
2. a slot is available, or an eligible victim has been fully unloaded first;
3. aggregate memory admission allows the candidate load;
4. runtime lifecycle state permits resource mutation.

Increasing capacity is not an instruction to fill every slot eagerly. Models remain demand-loaded.

## Pinning and eviction eligibility

A resident model is not evictable while any owner still requires its physical handle. At minimum this includes:

- a live session bound to the model;
- a materialized context or request that still references the model;
- an activation lease whose `modelDigest` matches the resident model;
- a lifecycle transition already in progress.

Activation leases remain digest-based and may protect different model digests simultaneously once multi-residency is enabled. Lease identity, ownership and release rules stay in `UseCaseActivationLeaseRegistry`.

`ActivationResidencyCoordinator` must therefore stop treating “another protected digest exists” as an unconditional global conflict. A different-model activation may proceed only when the residency/admission policy can satisfy it without evicting protected state. Capacity `1` preserves the current conflict outcome.

## Deterministic victim selection

Eviction is considered only when no free slot exists or policy explicitly needs release. Eligible victims are resident, idle and unprotected.

Selection must be deterministic. The initial policy is:

1. exclude any pinned/non-resident-transition entry;
2. prefer the least-recently-demanded eligible resident using a runtime-owned monotonic demand sequence;
3. use `ModelResidencyKey` ordering as a stable tie-breaker.

Wall-clock time is not required for ordering and must not make tests timing-dependent.

The selector returns a candidate; `ModelResidencyLifecycle` remains the transition/handle owner and `RuntimeOrchestrator` performs the backend release. A selector never frees native state directly.

## Aggregate memory admission

The existing `RuntimeResidencySnapshot.modelLoaded: Boolean` is insufficient once capacity may exceed `1`.

MRES-10 must evolve the backend-neutral snapshot so admission can represent at least:

- resident/reserved model count;
- aggregate resident-model cost known to runtime policy;
- resident context count;
- active and queued decode state;
- the candidate model/context incremental and peak cost.

Admission remains conservative and observation-backed. A second model load is evaluated against current process PSS/available-memory signals plus all already-resident model/context state; GGUF file size alone is not a memory estimate.

Where admission policy cannot establish required headroom, the request is rejected rather than overcommitting memory. Capacity is an upper bound, not proof that the device can afford that many models.

## Session and activation behavior

With capacity greater than `1`, a session for model B may coexist with sessions for model A when:

- A remains pinned by its live session/context state;
- B can be reused or admitted into another slot;
- no required eviction targets A or another protected entry;
- the global decode scheduler still serializes production execution.

This removes the current single-model switch precondition that all sessions/queued work must be empty only for evidence-qualified multi-resident policy. Capacity `1` retains that precondition semantically.

A prepared/resident model does not imply a context remains allocated. Context lifetime stays request/session-policy owned and independently bounded by `maxResidentContexts` and memory admission.

## Warm idle

Warm-idle policy becomes per resident key while timer infrastructure stays process-bounded.

- releasing the final demand/lease for a model may make that model warm-idle;
- reconnect/re-demand for the same key cancels or supersedes its pending expiry;
- expiry makes only that eligible key a release candidate;
- critical memory pressure may release multiple eligible residents immediately;
- repeated demand/release must not create unbounded timers or stale callbacks.

A warm-idle resident is still subject to eviction earlier than its TTL when another load needs capacity and the resident is otherwise eligible.

## Memory pressure and shutdown

Normal pressure may evict eligible idle residents according to existing policy. Critical pressure keeps the current authority to cancel/release active work when required and then converges all residents through lifecycle-owned unload transitions.

Shutdown is terminal and deterministic:

1. stop new work;
2. cancel/close scheduler work according to existing ownership;
3. drain/release sessions and contexts;
4. unload every resident model exactly once;
5. shut down the backend only after native model/context ownership reaches zero.

Partial unload failure must leave the corresponding entry representing physical ownership; it must not disappear from lifecycle state until release succeeds or terminal degraded-state handling records the retained resource.

## Concurrency invariant

Multi-model residency does **not** change production decode concurrency.

`SingleDecodeScheduler` remains the only production decode admission/execution lane and continues to expose at most one active request. Evaluation-only multi-sequence behavior remains separately scoped and must not be used to justify concurrent production decodes.

This separation lets residency reduce model reload latency without multiplying simultaneous KV/context/decode pressure.

## Required API shape changes

MRES-10 should prefer additive/internal evolution rather than public Consumer API widening.

Expected internal changes include:

- singular lifecycle queries become keyed/set queries while capacity-1 convenience behavior may remain where it is unambiguous;
- residency snapshots expose bounded multi-entry state instead of one `residentModel`;
- `RuntimeOrchestrator.ensureModelLoaded` resolves exact-key reuse, free-slot admission or deterministic eviction;
- activation residency asks the lifecycle/admission owner whether a different digest can coexist instead of enforcing a global digest conflict itself;
- memory snapshots replace the single `modelLoaded` assumption with model-count/aggregate state needed by admission;
- telemetry reports counts/decisions/identities already allowed by privacy policy, never prompt content.

No `llama.cpp` type or native pointer enters the Consumer API or backend-neutral memory contracts.

## Failure and rollback rules

- failed model load removes only the candidate `LOADING` entry and preserves unrelated residents;
- failed unload returns that exact entry to `RESIDENT` with the same handle;
- admission rejection changes no resident state;
- eviction must complete before its slot is reused;
- cancellation during prepare/switch cannot orphan a newly loaded handle;
- an activation/session acquisition that cannot be backed by residency must fail without leaking its lease/session ownership;
- retries must be idempotent with respect to native handle release.

## MRES-20 automated proof

Expected validation depth is **STRONG** because shared lifecycle, resource admission and native ownership are involved.

Required deterministic cases include:

- capacity `1` current load/reuse/switch behavior;
- capacity `1` protected model blocks different-model replacement;
- explicit test capacity `2` keeps A+B resident;
- same exact key reuses one handle rather than consuming another slot;
- live A lease/session prevents A eviction while B is admitted elsewhere;
- final release makes a model eligible without forcing immediate unload before policy requires it;
- deterministic victim selection among multiple eligible residents;
- aggregate admission rejects a second model despite a free policy slot when headroom is insufficient;
- load failure rolls back only the candidate entry;
- unload failure preserves the same physical handle;
- warm-idle expiry targets the correct model;
- critical pressure and shutdown converge all residents safely;
- repeated A -> B -> A demand reuses or reloads according to capacity without stale handles;
- one active production decode remains invariant.

Tests use deterministic sequence/clock/probe inputs and do not depend on sleeps.

## MRES-30 physical qualification

Capacity greater than `1` remains **REAL_ENVIRONMENT** evidence-gated. On the same representative device/profile identity, compare at least:

- one resident model idle/active;
- two resident models without active contexts;
- two resident models with the bounded context scenario intended for product use;
- A -> B -> A latency and reload avoidance;
- process PSS baseline/peak/recovery and native/Java heap signals where available;
- low-memory/headroom behavior;
- cancellation, pressure and repeated-cycle residual memory;
- thermal state and throughput/TTFT impact;
- exact backend/model/profile/execution identity.

A device/profile policy may opt into capacity `2` only when this evidence and existing memory guardrails support it. There is no global inference that a larger RAM device is safe solely from nominal memory size.

## Delivery gates

MRES-00 is complete when this contract is integrated and agrees with the LLUP workstream and existing memory ownership.

MRES-10 remains blocked until LLUP-70 establishes a stable promoted backend baseline. MRES-20 then proves deterministic software semantics. MRES-30 provides representative-device evidence. MRES-40 may promote only evidence-qualified policy; otherwise the production default remains `1`.
