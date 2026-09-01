# Background/process lifecycle hardening

Status: active
Document type: workstream-state
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Read when: coordinating detached inference-job, Binder reconnect, host background execution or process-recovery implementation
Last reviewed: 2026-09-01

Repository priority and integrated/blocker truth remain owned by [`../current-state.md`](../current-state.md). This workstream coordinates only the bounded implementation sequence.

## Outcome

Make explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, host-owned runtime/model authority, exact accepted configuration revisions, explicit cancellation, bounded resources and privacy-safe recovery. Ordinary connection-scoped Consumer inference keeps ADR 0012 lifecycle semantics.

Canonical architectural decision: [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Invariants

- UI lifecycle, consumer workflow lifecycle, Binder lifecycle and runtime/model lifecycle are separate.
- Durable jobs are explicit/capability-negotiated; passive setup/readiness and ordinary legacy inference never create one implicitly.
- Binder death/unbind removes transport observers; it does not semantically cancel an accepted durable job.
- Model unload is driven by explicit unload, runtime residency/idle policy, memory pressure or controlled runtime shutdown, never merely by UI/background transition or Binder reference count.
- Logical job identity is stable across reconnect and idempotent resubmit.
- Accepted durable jobs pin authenticated application/use-case plus exact use-case, binding and preset revisions; reconnect never silently re-resolves against newer configuration.
- Harness owns `ConsumerInferenceJobId`, execution and residency. RedactGuard owns its product `AnalysisJobId` and maps it to the Harness job identity.
- Existing `ActivationResidencyCoordinator`, `RuntimeOrchestrator`, `SessionLifecycle`, `GenerationLifecycle` and `SingleDecodeScheduler` remain canonical; the job layer does not duplicate them.
- Callback delivery is advisory; revisioned snapshot/query state is authoritative after reconnect.
- Host process death is an interruption boundary, never reported as continued RUNNING.
- Persistent metadata is privacy-safe only; prompt/document/generated content is not added to durable storage or normal evidence.
- Real device behavior is not inferred from emulator/CI evidence.

## Resource ownership target

| Resource | Semantic owner | Transport loss | Terminal/cleanup owner |
| --- | --- | --- | --- |
| Binder registration/death link/callback dispatcher | connection | removed | connection cleanup |
| durable logical inference job | host job registry/orchestrator | survives | explicit terminal/job policy |
| activation/residency demand | durable job + existing residency owner | survives | job terminal/resource policy |
| active generation handle | durable job | survives observer loss | job terminal/cancel/runtime policy |
| session retained by active job | durable job/runtime | survives | job terminal/session policy |
| idle client session | authenticated client/session policy | bounded cleanup allowed | session policy |
| loaded model | runtime residency policy | unaffected | explicit unload/idle/pressure/shutdown |

## Parallel lanes

### Lane A — architecture and ownership

| ID | State | Task |
| --- | --- | --- |
| HBG-00 | DONE | Accept successor ADR separating Binder/client lifetime from explicit durable jobs/runtime lifetime. |
| HBG-01 | IN_PROGRESS | Align ADR index, implementation plan, shared-runtime host specification/architecture/target and current state with active shared service + durable-job target. |
| HBG-10 | DONE | Freeze resource ownership matrix for connection, logical job, activation/residency, session, request handle and model residency. |

### Lane B — logical job foundation

| ID | State | Task |
| --- | --- | --- |
| HBG-20 | DONE | Add host logical job identity/state/revision/attempt/runtime-session primitives. |
| HBG-21 | DONE | Add deterministic transition/idempotency tests including stale-revision rejection. |
| HBG-22 | DONE | Introduce bounded process-local job registry independent from Binder callbacks. |
| HBG-23 | DONE | Add authenticated application/use-case-scoped idempotency lookup and duplicate-submit convergence. |
| HBG-24 | TODO | Extend accepted job identity with exact use-case/binding/preset revision pins before public submission wiring. |

### Lane C — transport/session decoupling

| ID | State | Task |
| --- | --- | --- |
| HBG-30 | DONE | Separate connection cleanup from semantic durable-job cancellation in service host. |
| HBG-31 | DONE | Keep durable execution independent from Binder callback delivery after submission. |
| HBG-32 | DONE | Transfer active logical-job session/generation-handle ownership to the durable job coordinator instead of the connection ledger. |
| HBG-33 | IN_PROGRESS | Capability-negotiated `ConsumerInferenceJobId` submit/query/result/cancel + Consumer SDK adapter are wired; complete observe/reattach contract semantics and configuration revision pinning before declaring the public durable-job protocol complete. |
| HBG-34 | DONE | Preserve ADR 0012 connection-scoped cleanup for clients/requests that do not opt into durable jobs. |

### Lane D — host execution and recovery

| ID | State | Task |
| --- | --- | --- |
| HBG-40 | DONE | Add started-service execution ownership while durable logical-job demand exists. |
| HBG-41 | DONE | Add foreground/user-visible execution adapter and notification path for durable work. |
| HBG-42 | TODO | Add runtimeSessionId and stale non-terminal job reconciliation after host process restart. |
| HBG-43 | TODO | Add retry-attempt semantics only when required input remains safely available; otherwise fail closed as source-required/interrupted. |
| HBG-50 | TODO | Integrate durable jobs with existing activation and warm-idle/model-residency policy without a second runtime owner. |

### Lane E — consumer convergence and evidence

| ID | State | Task |
| --- | --- | --- |
| HBG-55 | TODO | RedactGuard maps stable `AnalysisJobId` to Harness `ConsumerInferenceJobId` and reconciles on reconnect without reconstructing Harness runtime state. |
| HBG-60 | TODO | Unit/integration tests: unbind/death does not cancel durable job; explicit cancel does. |
| HBG-61 | TODO | Two-APK emulator reconnect/process-death/fault-injection journeys. |
| HBG-62 | TODO | Require privacy-safe state artifact + UI screenshots for lifecycle E2E journeys. |
| HBG-63 | TODO | STRONG automated preflight on exact head/base. |
| HBG-64 | TODO | Representative ARM64 same-signer two-APK + real-GGUF evidence. |

## Current implementation evidence

As of 2026-09-01, PR #502 has a green repository `Validate` FULL run on the exact branch head used for the implementation iteration. That run covers deterministic Android tests, lint/Detekt, AndroidTest assembly and native packaging. It is implementation evidence, not a substitute for HBG-61/HBG-63/HBG-64: lifecycle reconnect/process-death journeys, exact-head STRONG remote preflight and representative physical ARM64 + real-GGUF evidence remain open.

The current host path stores accepted logical jobs in a bounded process-local registry. Connection cleanup still cancels legacy connection-owned requests/sessions, but does not enumerate or cancel logical jobs. Logical-job execution retains its own Consumer client/session/generation handle until terminal completion or explicit cancellation. Durable-job demand drives the Android started/foreground service adapter independently from Binder connection count.

## Integration points

1. Lane A and the process-local Lane B foundation can ship without public protocol changes.
2. HBG-24 must pin exact accepted configuration identity before HBG-33 is considered complete for public durable submission.
3. HBG-22/23 are stable and HBG-30/31/32 now decouple accepted logical work from connection cleanup; targeted disconnect/death evidence remains HBG-60/HBG-61.
4. HBG-33 is the contract integration point consumed by RedactGuard HBG-55/LAS-08B reconciliation.
5. HBG-40/41 now reflect logical-job demand in Android service lifetime; residency integration remains HBG-50.
6. Physical validation begins only after automated lifecycle E2E/preflight is green.

## Fault matrix

| Event | Required semantic result |
| --- | --- |
| Host UI recreation | no inference/job change |
| Consumer UI recreation/navigation | no inference/job change; no duplicate job |
| Consumer app background | accepted durable job continues under valid Android execution policy |
| Binder callback death/unbind | transport observer removed; durable job survives |
| Consumer process death | host job survives while host process survives; later authenticated reconciliation |
| Host process death | old non-terminal job becomes INTERRUPTED/recoverable-or-source-required, never zombie RUNNING |
| Explicit cancel while detached | idempotent semantic cancellation with one terminal result |
| Authorization loss | explicit terminal/fail-closed outcome; no cross-caller reattachment |
| Model/config conflict | explicit fail-closed outcome; no silent substitution |
| LOW_MEMORY | runtime policy may cancel/release according to declared pressure policy; impossible continuation is explicit interruption |
| idle residency deadline | only idle/unprotected runtime resources become unloadable |
| persisted evidence inspection | no prompt/document/generated-output content present |

## Documentation impact

Expected durable owners: ADR index, shared-runtime host specification/architecture/target, implementation plan, Consumer SDK/API docs, `.engineering/e2e.json`, current state and affected README usage only if public setup/behavior changes. Stable repository mission/positioning remains unchanged.

## Validation

Default classification: STRONG once executable shared-runtime/Binder/service behavior changes. Pure state primitives may use focused module tests while iterating, but publication cannot downgrade required cross-boundary gates. Emulator fidelity remains simulated/emulated; representative background/process/model claims require physical ARM64 evidence.
