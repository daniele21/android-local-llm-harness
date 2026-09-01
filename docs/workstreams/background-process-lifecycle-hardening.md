# Background/process lifecycle hardening

Status: active
Document type: workstream-state
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Read when: coordinating detached inference-job, Binder reconnect, host background execution or process-recovery implementation
Last reviewed: 2026-09-01

Repository priority and integrated/blocker truth remain owned by [`../current-state.md`](../current-state.md). This workstream coordinates only the bounded implementation sequence.

## Outcome

Make explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, host-owned runtime/model authority, exact accepted execution identity, explicit cancellation, bounded resources and privacy-safe recovery. Ordinary connection-scoped Consumer inference keeps ADR 0012 lifecycle semantics.

Canonical architectural decision: [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Invariants

- UI lifecycle, consumer workflow lifecycle, Binder lifecycle and runtime/model lifecycle are separate.
- Durable jobs are explicit/capability-negotiated; passive setup/readiness and ordinary legacy inference never create one implicitly.
- Binder death/unbind removes transport observers; it does not semantically cancel an accepted durable job.
- Logical job identity is stable across reconnect and idempotent resubmit.
- Accepted jobs pin the exact `ConsumerExecutionIdentity` returned by preparation; reconnect never silently re-resolves the same job against newer configuration.
- Harness owns `ConsumerInferenceJobId`, execution/session ownership, Android service lifetime and model/runtime authority. RedactGuard owns its product `AnalysisJobId`.
- Callback delivery is advisory. Revisioned `query/result` state is authoritative after reconnect; generated content is not turned into a durable callback log.
- Existing connection-scoped prepare/session/generate behavior remains backward-compatible.
- Host process death is an interruption boundary, never reported as continued RUNNING.
- Prompt/document/generated content is not added to durable storage, telemetry or shared evidence.
- Real-device background/model behavior is not inferred from emulator/CI evidence.

## Resource ownership

| Resource | Semantic owner | Transport loss | Terminal/cleanup owner |
| --- | --- | --- | --- |
| Binder registration/death link/callback dispatcher | connection | removed | connection cleanup |
| durable logical inference job | host logical-job registry/coordinator | survives | job terminal/policy |
| session/generation handle retained by durable work | logical job/runtime | survives | job terminal/cancel/runtime policy |
| started/foreground service demand | active durable-job demand | survives | final active durable job terminal |
| idle client session | authenticated connection/session policy | bounded cleanup allowed | session policy |
| loaded model | existing runtime/residency policy | unaffected by observer loss | explicit unload/idle/pressure/shutdown |

## Parallel lanes

### Lane A — architecture and ownership

| ID | State | Task |
| --- | --- | --- |
| HBG-00 | DONE | Accept successor ADR separating Binder/client lifetime from explicit durable jobs/runtime lifetime. |
| HBG-01 | IN_PROGRESS | Reconcile remaining shared-runtime/current-state owners as implementation and downstream evidence land. |
| HBG-10 | DONE | Freeze resource ownership matrix for connection, logical job, service demand, session/request handle and model residency. |

### Lane B — logical job foundation

| ID | State | Task |
| --- | --- | --- |
| HBG-20 | DONE | Add host logical job identity/state/revision/attempt/runtime-session primitives. |
| HBG-21 | DONE | Add deterministic transition/idempotency tests including stale-revision rejection. |
| HBG-22 | DONE | Introduce bounded process-local job registry independent from Binder callbacks. |
| HBG-23 | DONE | Add authenticated application/use-case-scoped idempotency lookup and duplicate-submit convergence. |
| HBG-24 | DONE | Pin the exact prepared `ConsumerExecutionIdentity` into submit, host registry and public snapshots, rejecting scope/configuration mismatch instead of re-resolving silently. |

### Lane C — transport/session decoupling

| ID | State | Task |
| --- | --- | --- |
| HBG-30 | DONE | Separate connection cleanup from semantic durable-job cancellation in service host. |
| HBG-31 | DONE | Keep durable execution independent from Binder callback delivery after submission. |
| HBG-32 | DONE | Transfer logical-job session/generation-handle ownership to the durable coordinator instead of the connection ledger. |
| HBG-33 | DONE | Publish capability-negotiated `ConsumerInferenceJobId` submit/query/result/cancel through protocol minor 6 and Consumer SDK alpha.8; authenticated query/result is the authoritative reconnect/reattach path. |
| HBG-34 | DONE | Preserve ADR 0012 connection-scoped cleanup for clients/requests that do not opt into durable jobs. |

### Lane D — host execution and recovery

| ID | State | Task |
| --- | --- | --- |
| HBG-40 | DONE | Add started-service execution ownership while durable logical-job demand exists. |
| HBG-41 | DONE | Add foreground/user-visible execution adapter and notification path for durable work. |
| HBG-42 | TODO | Reconcile stale non-terminal metadata after actual host process restart; old native work must become `INTERRUPTED`, never zombie RUNNING. |
| HBG-43 | TODO | Add retry-attempt semantics only when required input remains safely available; otherwise fail closed as source-required/interrupted. |
| HBG-50 | IN_PROGRESS | Confirm active durable demand composes with existing residency/memory policy without a second model owner; deterministic and physical fault evidence remains. |

### Lane E — consumer convergence and evidence

| ID | State | Task |
| --- | --- | --- |
| HBG-55 | IN_PROGRESS | RedactGuard owns process-local `AnalysisJobId`/UI reattachment and must migrate chunk execution to alpha.8 logical jobs so Binder reconnect queries the same Harness job rather than duplicating generation. |
| HBG-60 | IN_PROGRESS | Unit/integration coverage exists for connection invalidation without implicit logical-job cancellation; complete full disconnect/reconnect/cancel matrix. |
| HBG-61 | TODO | Add dedicated two-APK emulator app-switch/Binder reconnect/process-loss/fault-injection journeys. |
| HBG-62 | TODO | Require privacy-safe state artifact plus UI screenshots and video for lifecycle E2E journeys. |
| HBG-63 | TODO | Run repository-owned STRONG remote preflight on the final exact head/base. |
| HBG-64 | TODO | Representative ARM64 same-signer two-APK + real-GGUF/model-residency/OEM evidence. |

## Current implementation state

PR #502 now contains the executable Harness side of the durable-job boundary:

- protocol minor 5 remains setup resolution; logical jobs are append-only protocol minor 6;
- `ConsumerLogicalJobSubmitRequest` carries `preparedId` plus exact `expectedExecution` and the returned snapshot echoes the accepted `execution` identity;
- host registry/idempotency is caller + use-case scoped and bounded;
- duplicate submit converges on the same logical job, while identity mismatch fails closed;
- accepted execution owns its session/generation handle independently from the Binder connection ledger;
- terminal output replay is bounded and process-local;
- active durable demand drives started/foreground service lifetime independently from bound-client count;
- Consumer SDK candidate is `0.1.0-alpha.8` with a frozen public ABI baseline and external-consumer validation.

This is not yet a claim of full lifecycle completion. Actual host process death/restart recovery, RedactGuard logical-job consumption, dedicated lifecycle E2E and representative ARM64/JNI/GGUF/OEM evidence remain open. Final automated publication evidence must be taken from the final exact PR head after documentation is current.

## Integration points

1. HBG-24 and HBG-33 are complete Harness contract prerequisites for RedactGuard LAS-08B.
2. RedactGuard must retain one stable product `AnalysisJobId` while mapping each active chunk execution to a stable Harness logical-job identity/idempotency key; UI recreation and Binder reconnect must not resubmit duplicate work.
3. HBG-40/41 keep the host process eligible for user-visible durable work, but do not pretend native inference survives host process death.
4. HBG-42/43 recovery work may persist only privacy-safe metadata unless a separate privacy/security decision explicitly expands the boundary.
5. Physical validation begins after deterministic two-APK lifecycle E2E and exact-head automated preflight are green.

## Fault matrix

| Event | Required semantic result |
| --- | --- |
| Host UI recreation | no inference/job change |
| Consumer UI recreation/navigation | no inference/job change; no duplicate job |
| Consumer app background | accepted durable job continues under valid Android execution policy |
| Binder callback death/unbind | transport observer removed; durable job survives |
| Consumer process death | host job may survive while host process survives; later authorized reconciliation must not expose another caller's work |
| Host process death | old native work is interrupted; never zombie RUNNING |
| Explicit cancel while detached | idempotent semantic cancellation with one terminal result |
| Authorization loss | explicit fail-closed outcome; no cross-caller reattachment |
| Model/config conflict | explicit fail-closed outcome; no silent substitution |
| Critical memory pressure | runtime policy may cancel/release for system health; impossible continuation is explicit interruption |
| Idle residency deadline | only genuinely idle/unprotected resources become unloadable |
| Persisted evidence inspection | no prompt/document/generated-output content present |

## Documentation impact

Durable owners for this slice are ADR 0016, repository implementation target, this workstream and Consumer Android SDK documentation. README identity is unchanged by lifecycle behavior; README usage changes only where current public setup/API examples become misleading. `.engineering/e2e.json` owns lifecycle journey/environment/fidelity truth as automation lands. `docs/current-state.md` remains the repository integration/blocker ledger and must describe the integrated boundary when this PR is promoted.

## Validation

Executable shared-runtime/Binder/service behavior is STRONG by blast radius. The branch also experienced FULL validation because earlier build/selector tooling changed; that stronger run does not reduce the final exact-head STRONG requirement.

Deterministic compile/unit/static/lint/Binder/emulator/packaging gates are `REMOTE_AUTOMATED` in the current ChatGPT execution environment. Representative same-signer ARM64/JNI/GGUF model residency, OEM process policy, thermal/resource and protected-signing claims remain `REAL_ENVIRONMENT`.
