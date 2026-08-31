# ADR 0016: Detached shared-runtime jobs survive transport loss

- Status: Accepted
- Date: 2026-08-31
- Supersedes: lifecycle/background-lifetime portions of ADR 0012

## Context

ADR 0012 deliberately chose a bound-only v1 shared runtime: Binder client death, explicit unbind or unregister cancels caller-owned work and closes caller-owned sessions. That made initial cleanup deterministic, but it couples transport lifetime to inference lifetime. A consumer moving to the background, losing its Binder connection, recreating UI, or being killed by Android can therefore terminate otherwise-valid local inference. The host can also discard a resident model because demand was inferred from connection lifetime rather than semantic work and residency policy.

The current product requires a stronger lifecycle contract. User-visible inference must continue independently from Activity/Compose lifecycle and transient Binder connectivity, while preserving same-signer authorization, host-owned model authority, bounded resources, explicit cancellation, privacy-safe evidence and Android background-execution rules.

## Decision

### Independent lifetimes

The shared deployment has four distinct lifetimes:

```text
UI lifecycle != consumer workflow lifecycle != Binder transport lifecycle != host runtime/model lifecycle
```

Binder is a control/observation transport. Losing a Binder client or callback does not by itself semantically cancel accepted inference work. Activity recreation and consumer navigation have no host-runtime side effect.

### Logical inference jobs

Accepted generation work is owned by a host logical job, not by a Binder callback. A logical job has a stable host `jobId`, a caller-provided idempotency key (`clientRequestId`), monotonic `revision`, `attempt`, terminal/non-terminal status and a process-session identity. The host may keep transient prompt/output material in bounded process memory only as required to finish or reconcile that job; it must not persist prompt, document text, generated output or other model content in normal storage, telemetry or evidence.

The caller-provided idempotency key is scoped to the authenticated application/use case and cannot grant access to another caller's work. Repeating an accepted submit with the same key resolves to the same logical job rather than starting duplicate inference.

### Transport loss and reattachment

Binder death/unbind removes connection-scoped callbacks, death recipients and transport bookkeeping. It does not cancel a detached logical job. A later authenticated connection can query/reattach only to jobs in its own caller scope using the published job identity/idempotency semantics.

Callback delivery is an optimization, not the source of truth. Snapshot/query state is authoritative after reconnect; stale revisions are ignored.

### Sessions

Transport connection ownership no longer defines the semantic lifetime of a session used by a detached job. A session needed by an active logical job remains host-owned until the job reaches a terminal state or explicit semantic cancellation/host policy releases it. Client-created idle sessions that are not retained by a job remain bounded and cleanable under the published session policy.

### Cancellation

Only explicit semantic cancellation, timeout/policy cancellation, memory-pressure policy, controlled host shutdown, or a fatal runtime failure may terminate accepted work. Cancellation is idempotent. A consumer can persist only privacy-safe cancellation intent and resend it after reconnect.

### Host service execution

The proof host evolves from purely bound lifetime to a started + bound service when accepted detached work requires the host process to remain eligible for execution. The service remains bound for API access and started only while semantic work/residency policy requires it. Long-running user-visible background work must use the Android execution mechanism required by the platform, including foreground-service notification when applicable; this ADR does not authorize hidden indefinite background execution.

Service lifetime and model residency remain separate. Service stop does not implicitly unload installed bytes or a model protected by runtime residency policy, and Binder reference counts do not independently decide residency.

### Host process death

Native inference cannot survive host process death. Every host process/runtime life receives a `runtimeSessionId`. A non-terminal job recorded against an older runtime session cannot be reported as still running after restart; it becomes `INTERRUPTED` and follows an explicit recovery policy.

The initial recovery policy retries the same logical job with `attempt + 1` only when the required input is still safely available to the owning process/workflow. This is restart-from-input, not token-level resume. Persisting KV cache, native context, sampler state or generated content is out of scope.

If privacy policy means required sensitive input disappeared with process death, recovery must fail closed with a state that requires the user/source to be reopened; the system must not invent transparent recovery.

### Persistence and privacy

Durable job metadata, when needed, is restricted to privacy-safe identity/state such as job/idempotency hashes, status/stage, revision, attempt, runtime-session identity, timestamps and stable safe error codes. Prompt text, document text, findings, generated output, raw Binder payloads and private paths are excluded.

### Security boundary retained

ADR 0012 remains authoritative for same-signer trust, signature permission, per-call caller verification, host-owned application/use-case/model authority, protocol compatibility, diagnostics separation and bounded wire payloads. Reattachment never weakens caller isolation.

## Consequences

- App switching and transient Binder loss no longer imply inference cancellation.
- The host needs a logical job registry and a protocol surface for submit/query/observe/cancel/reattach.
- Connection cleanup becomes transport cleanup; semantic job/session cleanup moves to job ownership and runtime policy.
- The Consumer SDK must reconcile state after reconnect and treat callback loss as transport loss rather than generation failure.
- Background execution becomes explicit and user-visible where Android requires it.
- Host process death remains a real interruption boundary; recovery semantics are truthful rather than pretending native continuation.
- Privacy-safe metadata may survive process death, while sensitive inference payloads remain process-local by default.

## Alternatives considered

### Keep bound-only lifecycle and ask consumers to stay foreground

Rejected because it makes ordinary Android app switching a semantic failure and pushes host-runtime lifetime into presentation behavior.

### Stop cancelling on unbind without adding logical jobs

Rejected because sessions/handles would become orphaned, reconnecting callers could not safely reconcile state, and callback failure would still terminate or lose work.

### Persist prompt/output so every job can transparently resume

Rejected because it expands the sensitive-data boundary and contradicts current privacy defaults. Recovery capability must reflect what can actually be reconstructed safely.

### Persist native/KV state for token-exact resume

Rejected for this workstream. It adds backend-specific memory/storage complexity before evidence shows the benefit justifies it.

## Implementation gate

Implementation proceeds in vertical slices: logical job/state primitives and tests; host registry/idempotency; Binder protocol + Consumer SDK query/observe/cancel; transport-death cleanup separation; started/bound host execution; RedactGuard reconciliation; emulator fault-injection E2E with screenshot artifacts; then representative same-signer two-APK + real-GGUF evidence. Shared-contract, Binder, persistence, service/manifest and packaging changes require STRONG validation.