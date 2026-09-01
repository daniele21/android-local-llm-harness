# ADR 0016: Detached shared-runtime jobs survive transport loss

- Status: Accepted
- Date: 2026-08-31
- Supersedes: lifecycle/background-lifetime portions of ADR 0012 for explicitly durable Consumer jobs

## Context

ADR 0012 deliberately chose a bound-only v1 shared runtime: Binder client death, explicit unbind or unregister cancels caller-owned work and closes caller-owned sessions. That made initial cleanup deterministic, but it couples transport lifetime to inference lifetime. A consumer moving to the background, losing its Binder connection, recreating UI, or being killed by Android can therefore terminate otherwise-valid local inference. The host can also discard a resident model because demand was inferred from connection lifetime rather than semantic work and residency policy.

The current product requires a stronger lifecycle contract. User-visible inference must continue independently from Activity/Compose lifecycle and transient Binder connectivity, while preserving same-signer authorization, host-owned model authority, bounded resources, explicit cancellation, privacy-safe evidence and Android background-execution rules.

ADR 0015 already establishes the complementary ownership rule: product activation/residency is distinct from Binder connection/session lifetime. This ADR changes lifecycle only for an explicit durable-job capability; ordinary connection-scoped prepare/session/generate calls keep ADR 0012 cleanup semantics.

## Decision

### Independent lifetimes

The shared deployment has four distinct lifetimes:

```text
UI lifecycle != consumer workflow lifecycle != Binder transport lifecycle != host runtime/model lifecycle
```

Binder is a control/observation transport. Losing a Binder client or callback does not by itself semantically cancel accepted durable inference work. Activity recreation, consumer navigation and ordinary app switching have no host-runtime side effect.

### Durable logical inference jobs are explicit opt-in

Long-running Consumer work opts into an additive durable-job capability. Existing clients that do not negotiate this capability retain the bound/connection-scoped behavior defined by ADR 0012.

A durable logical job has a stable opaque public `ConsumerInferenceJobId`, a caller-provided idempotency key (`clientRequestId`), monotonic `revision`, `attempt`, terminal/non-terminal status and a process-session identity. Host-internal job identity may use a transport/private representation, but the supported Consumer API must not expose host storage or runtime handles.

The job pins the authenticated application, use case and exact accepted Harness-owned configuration revisions needed for execution, including use-case, binding and preset revision identity. A reconnect must never silently resolve the same logical job against newer configuration.

The host may keep transient prompt/output material in bounded process memory only as required to finish or reconcile that job; it must not persist prompt, document text, generated output or other model content in normal storage, telemetry or evidence.

The caller-provided idempotency key is scoped to the authenticated application/use case and cannot grant access to another caller's work. Repeating an accepted submit with the same key resolves to the same logical job rather than starting duplicate inference.

### Transport loss and reattachment

Binder death/unbind removes connection-scoped callbacks, death recipients and transport bookkeeping. It does not cancel a durable logical job. A later authenticated connection can query/reattach only to jobs in its own caller scope using the published job identity/idempotency semantics.

Callback delivery is an optimization, not the source of truth. Revisioned snapshot/query state is authoritative after reconnect; stale revisions are ignored. Live observation is bounded and connection-scoped and must not become a generated-content replay log.

### Existing runtime and residency owners remain canonical

The durable job is an orchestration owner, not a second inference runtime. It composes the existing `ActivationResidencyCoordinator`, `RuntimeOrchestrator`, `SessionLifecycle`, `GenerationLifecycle` and `SingleDecodeScheduler` owners.

While a durable job is preparing, running or cancelling, the job owns the semantic activation/residency demand for its pinned execution identity. UI-hidden/background state, Binder reference count and warm-idle policy cannot independently evict a model protected by that demand. The default invariant remains one resident model and one active production decode; incompatible new work fails explicitly under existing resource/conflict policy.

Passive setup/readiness inspection remains observational and must never create a durable job, activation lease, runtime binding or model load.

### Sessions and request handles

Transport connection ownership no longer defines the semantic lifetime of sessions and generation handles retained by a durable job. A session or active request needed by a durable job remains host-owned until the job reaches the relevant terminal/cleanup state or explicit semantic cancellation/runtime policy releases it. Client-created idle sessions that are not retained by a durable job remain bounded and cleanable under the published session policy.

### Cancellation

Only explicit semantic cancellation, timeout/policy cancellation, memory-pressure policy, controlled host shutdown, authorization loss or a fatal runtime failure may terminate accepted work. Cancellation is idempotent. A consumer can persist only privacy-safe cancellation intent and resend it after reconnect.

Temporary observer loss is not a failure and cannot be surfaced as cancellation.

### Host service execution

The proof host evolves from purely bound lifetime to a started + bound service when accepted durable work requires the host process to remain eligible for execution. The service remains bound for API access and started only while semantic work/residency policy requires it. Long-running user-visible background work must use the Android execution mechanism required by the platform, including foreground-service notification when applicable; this ADR does not authorize hidden indefinite background execution.

Service lifetime and model residency remain separate. Service stop does not implicitly redefine model residency, and Binder reference counts do not independently decide residency. No always-on service is introduced.

### Host process death

Native inference cannot survive host process death. Every host process/runtime life receives a `runtimeSessionId`. A non-terminal job recorded against an older runtime session cannot be reported as still running after restart; it becomes `INTERRUPTED` and follows an explicit recovery policy.

A later attempt may restart the same logical job with `attempt + 1` only when the required input is still safely available to the owning workflow under the active privacy policy. This is restart-from-input, not token-level resume. Persisting KV cache, native context, sampler state, prompt/document text or generated content is out of scope.

If sensitive input disappeared with process death, recovery fails closed with a source-required/interrupted state. This ADR does not authorize transparent durable replay of sensitive input.

### Persistence and privacy

Durable job metadata, when needed, is restricted to privacy-safe identity/state such as opaque job/idempotency hashes, authenticated application/use-case identity, pinned use-case/binding/preset revisions, status/stage, revision, attempt, runtime-session identity, timestamps, bounded non-content progress counters and stable safe error codes. Prompt text, document text, findings, generated output, raw Binder payloads and private paths are excluded.

### Consumer product ownership stays separate

Harness owns `ConsumerInferenceJobId`, execution, session/runtime coordination and model residency. Consumer applications own their product workflow identity and state machine.

For RedactGuard, the application owns an `AnalysisJobId` for the document-analysis workflow and maps it to the Harness `ConsumerInferenceJobId`. RedactGuard does not reconstruct or own Harness model/session/residency state. A multi-step or multi-chunk analysis may use one durable Harness job as its execution lifecycle while product progress/review semantics remain RedactGuard-owned.

### Security boundary retained

ADR 0012 remains authoritative for same-signer trust, signature permission, per-call caller verification, host-owned application/use-case/model authority, protocol compatibility, diagnostics separation and bounded wire payloads. Reattachment never weakens caller isolation.

## Failure semantics

The supported durable-job boundary must distinguish at least:

- explicit user/consumer cancellation;
- host/runtime failure;
- model or configuration conflict before execution;
- consumer authorization loss;
- Harness service/process interruption;
- critical-memory interruption when continuation is impossible.

Observer loss, Activity recreation, ordinary app backgrounding and temporary Binder detach are not semantic failures.

## Consequences

- App switching and transient Binder loss no longer imply durable inference cancellation.
- The host needs a logical job registry and an additive protocol surface for submit/query/observe/cancel/reattach.
- Connection cleanup becomes transport cleanup; semantic job/session cleanup moves to job ownership and runtime policy.
- The Consumer SDK must reconcile state after reconnect and treat callback loss as transport loss rather than generation failure.
- Background execution becomes explicit and user-visible where Android requires it.
- Host process death remains a real interruption boundary; recovery semantics are truthful rather than pretending native continuation.
- Privacy-safe metadata may survive process death, while sensitive inference payloads remain process-local by default.

## Alternatives considered

### Keep bound-only lifecycle and ask consumers to stay foreground

Rejected because it makes ordinary Android app switching a semantic failure and pushes host-runtime lifetime into presentation behavior.

### Keep the Binder connection alive from an application singleton

Rejected because it improves Activity recreation but still makes transport presence the execution owner and does not solve consumer-process death.

### Stop cancelling on unbind without adding logical jobs

Rejected because sessions/handles would become orphaned, reconnecting callers could not safely reconcile state, and callback failure would still terminate or lose work.

### Convert the service into an always-on foreground service

Rejected because it broadens lifecycle, battery and user-visible behavior beyond actual durable demand and violates least-resource ownership.

### Persist prompt/output so every job can transparently resume

Rejected because it expands the sensitive-data boundary and contradicts current privacy defaults. Recovery capability must reflect what can actually be reconstructed safely.

### Persist native/KV state for token-exact resume

Rejected for this workstream. It adds backend-specific memory/storage complexity before evidence shows the benefit justifies it.

## Validation

Implementation changes are STRONG because they affect public Consumer/Binder contracts, Android Service/Manifest behavior and runtime/residency ownership.

Deterministic two-APK emulator evidence must cover:

1. start durable job -> consumer Activity background/foreground -> same job continues;
2. observer detach/unbind -> host job remains active -> authenticated reattach sees the same job;
3. consumer Activity recreation -> no duplicate job;
4. explicit cancel while detached -> exactly one terminal cancellation;
5. Host service/process kill -> reconnect reports interruption, never fake completion/continuation;
6. model conflict and critical-memory fault injection remain fail-closed;
7. persisted metadata/logs contain no prompt, document or generated-output content.

Representative same-signer ARM64/JNI/GGUF model residency, OEM process policy and thermal/resource behavior remain REAL_ENVIRONMENT evidence.

## Implementation gate

Implementation proceeds in vertical slices: logical job/state primitives and tests; host registry/idempotency; Binder protocol + Consumer SDK query/observe/cancel; transport-death cleanup separation; started/bound host execution; RedactGuard reconciliation; emulator fault-injection E2E with screenshot artifacts; then representative same-signer two-APK + real-GGUF evidence. Shared-contract, Binder, persistence, service/manifest and packaging changes require STRONG validation.
