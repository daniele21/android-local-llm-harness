# ADR 0016 — Durable consumer inference jobs across UI/Binder detachment

- Status: Proposed
- Date: 2026-08-31
- Supersedes: ADR 0012 lifecycle decision only for explicitly durable Consumer jobs

## Context

ADR 0012 deliberately made shared-runtime v1 bound-only: Binder death, unregister or unbind owns cleanup of connection-scoped requests and sessions. That decision is correct for ordinary connection-scoped inference, but it cannot satisfy LAS background continuity. A user-started RedactGuard analysis may span multiple local-LLM generations and must not be cancelled merely because the consumer Activity is backgrounded, recreated or temporarily detached.

Current source confirms the mismatch. `HarnessSharedRuntimeService` is bound-only and `SharedRuntimeHostDelegate.cleanupConnection()` cancels connection-owned request handles, closes connection-owned sessions and releases all activation leases. Warm-idle retention protects reuse only after demand ends; it is not an execution owner. Therefore keeping an Activity/ViewModel or Binder connection alive longer would only move the accidental owner rather than define the product lifecycle.

ADR 0015 already establishes the complementary rule: product activation/residency is distinct from Binder connection/session lifetime and Harness owns exact model/runtime/residency policy.

## Decision

### 1. Durable job is an explicit opt-in lifecycle

Add an additive Consumer job surface for user-started long-running operations. Ordinary Consumer API prepare/session/generate remains connection-scoped and keeps ADR 0012 cleanup semantics.

A durable job has one opaque host identity, `ConsumerInferenceJobId`, and a host-owned state machine:

```text
CREATED -> PREPARING -> RUNNING -> COMPLETED
                    \-> CANCELLING -> CANCELLED
                    \-> INTERRUPTED
                    \-> FAILED
```

Terminal states are immutable. Cancellation is explicit and idempotent.

### 2. Binder/UI are command and observation channels, not lifetime owners

The authenticated connection that creates a durable job owns authority to control it, but the connection object does not own the job lifetime. UI stop/destroy, configuration change, backgrounding, observer detach, temporary unbind and later rebind do not imply cancellation.

A consumer may reattach using its stable app-owned job correlation plus the opaque Harness job identity while its authenticated application identity still matches. Connection death removes observers/callbacks immediately but does not terminate a durable job.

### 3. Harness owns execution and residency for the job

The durable job owns the activation/residency demand needed by its exact pinned use-case/binding/preset revision. While PREPARING/RUNNING/CANCELLING, normal UI-hidden/background warm-idle policy cannot unload the model required by the job.

`ActivationResidencyCoordinator`, `RuntimeOrchestrator`, `SessionLifecycle`, `GenerationLifecycle` and `SingleDecodeScheduler` remain their existing canonical owners. The job orchestrator composes these owners; it does not create a second model loader, scheduler, session registry or cancellation state machine.

The default invariant remains one resident model and one active production decode. Incompatible new jobs fail explicitly under existing resource/conflict policy.

### 4. Process death is interruption, not fake continuation

Native model/context memory cannot survive Harness process death. Host process death, force-stop, package replacement and unrecoverable critical-memory termination therefore transition in-flight durable work to truthful interruption semantics after restart/reconciliation; they are never reported as having continued invisibly.

LAS-08A does not authorize persistence of prompt/document text, generated output or native state. Transparent replay after process death requires a separate privacy/security decision because replay would require enough sensitive input state to reconstruct work.

### 5. Persist only bounded non-content job metadata

Harness may persist the minimum non-content job envelope needed to distinguish completed/interrupted work across process recreation:

- opaque job identity;
- authenticated application/use-case/preset/binding revision identity;
- lifecycle phase/terminal reason;
- created/updated timestamps;
- bounded progress counters that contain no document/prompt/output content.

Prompt text, pasted/document text, schemas containing user content, findings and raw model output remain process-memory only unless a later security/data ADR explicitly changes that rule.

### 6. Android service lifetime becomes execution-aware

ADR 0012's blanket “bound-only” rule is relaxed only for active durable jobs. The Android host integration must maintain a started execution lifetime while one or more durable jobs genuinely require continued compute and must return to bound-only/idle behavior when durable demand reaches zero.

Whether a particular Android version/device requires foreground-service promotion, notification presentation or rejects background start is a platform-policy concern owned by the Android host adapter. The adapter must follow the target SDK's legal service-start path and fail visibly if continued background execution is not permitted; it must not fake durability with wakelocks, polling or hidden keepalive loops.

No always-on service is introduced.

### 7. Observation is bounded and replay-safe

Durable job status is queryable as a snapshot. Live progress/event observation is optional and connection-scoped. Reattachment receives current snapshot plus only a bounded non-content event window if the implementation needs it; generated text is not retained as an event log for later observers.

### 8. Consumer product ownership stays separate

RedactGuard owns an application-scoped `AnalysisJobId` and its document-analysis state machine. Harness owns `ConsumerInferenceJobId` and local inference execution. RedactGuard maps the two identities but does not own Harness model/session/residency state.

A multi-chunk RedactGuard analysis may use one durable Harness job as its execution lease and submit bounded inference steps through that job. Product progress/review semantics remain RedactGuard-owned.

## Compatibility

- Existing protocol minors and ordinary connection-scoped inference are unchanged.
- Durable jobs are additive and capability-negotiated.
- Clients that do not negotiate durable jobs retain ADR 0012 behavior exactly.
- Passive LAS setup/readiness inspection remains side-effect free and never creates a durable job.

## Failure semantics

At minimum the public job surface must distinguish:

- explicit user cancellation;
- host/runtime failure;
- model/configuration conflict before execution;
- consumer authorization loss;
- Harness process/service interruption;
- critical-memory interruption when continuation is impossible.

Temporary observer loss is not a failure.

## Validation

Implementation is STRONG because it changes public Consumer/Binder contracts, Android Service/Manifest lifetime and model/runtime residency ownership.

Deterministic REMOTE_AUTOMATED evidence must include a two-APK emulator lifecycle matrix covering:

1. start job -> consumer Activity background/foreground -> job continues;
2. detach observer/unbind -> host job remains active -> authenticated reattach sees the same job;
3. consumer Activity recreation -> no duplicate job;
4. explicit cancel while detached -> exactly one terminal cancellation;
5. Host service/process kill -> reconnect reports interruption rather than completion/continuation;
6. model conflict and critical-memory fault injection remain fail-closed;
7. no prompt/document/output content enters persisted job metadata or logs.

Representative ARM64 JNI/GGUF memory residency, OEM process policy and thermal/resource behavior remain REAL_ENVIRONMENT evidence.

## Consequences

Positive:
- ordinary app switching is no longer coupled to inference cancellation;
- model residency is owned by product execution demand instead of UI/Binder presence;
- consumers can reattach without duplicating work;
- process death remains honest and privacy-preserving.

Costs:
- new additive Consumer job protocol and host orchestrator;
- Android execution-lifetime adapter and user-visible platform behavior where required;
- bounded job metadata persistence/reconciliation;
- cross-repository RedactGuard reattachment logic and lifecycle E2E.

## Rejected alternatives

### Keep the consumer Binder connection alive from an application singleton

Rejected. It improves Activity recreation but still makes transport presence the execution owner and fails on consumer process death.

### Convert the existing bound service into an always-on foreground service

Rejected. It broadens lifecycle and battery/user-visible behavior beyond actual durable demand and violates least-resource ownership.

### Persist inputs and automatically replay after process death

Rejected for this ADR. It silently expands the sensitive-data lifecycle and requires a separate privacy/security decision.

### Treat background/`UI_HIDDEN` as model-release authority during active work

Rejected. UI visibility is observer state; active durable execution is stronger residency demand.