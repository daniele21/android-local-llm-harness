# Background/process lifecycle hardening

Status: active
Document type: workstream
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Started: 2026-08-31

## Outcome

Make local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, host-owned runtime/model authority, explicit cancellation, bounded resources and privacy-safe recovery.

Canonical architectural decision: [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Invariants

- UI lifecycle, consumer workflow lifecycle, Binder lifecycle and runtime/model lifecycle are separate.
- Binder death/unbind removes transport observers; it does not semantically cancel an accepted detached job.
- Model unload is driven by explicit unload, runtime residency/idle policy, memory pressure or controlled runtime shutdown, never merely by UI/background transition or Binder reference count.
- Logical job identity is stable across reconnect and idempotent resubmit.
- Callback delivery is advisory; revisioned snapshot/query state is authoritative after reconnect.
- Host process death is an interruption boundary, never reported as continued RUNNING.
- Persistent metadata is privacy-safe only; prompt/document/generated content is not added to durable storage or normal evidence.
- Real device behavior is not inferred from emulator/CI evidence.

## Parallel lanes

### Lane A — architecture and ownership

| ID | State | Task |
| --- | --- | --- |
| HBG-00 | DONE | Accept successor ADR separating Binder/client lifetime from logical jobs/runtime lifetime. |
| HBG-01 | IN_PROGRESS | Align ADR index, implementation plan, shared-runtime host specification and current state with active shared service + detached-job target. |
| HBG-10 | IN_PROGRESS | Document/test resource ownership matrix for connection, logical job, session, request handle and model residency. |

### Lane B — logical job foundation

| ID | State | Task |
| --- | --- | --- |
| HBG-20 | IN_PROGRESS | Add backend-neutral host logical job identity/state/revision/attempt primitives. |
| HBG-21 | IN_PROGRESS | Add deterministic transition/idempotency tests including stale-revision rejection. |
| HBG-22 | TODO | Introduce bounded process-local job registry independent from Binder callbacks. |
| HBG-23 | TODO | Add authenticated caller-scoped idempotency key lookup and duplicate-submit convergence. |

### Lane C — transport/session decoupling

| ID | State | Task |
| --- | --- | --- |
| HBG-30 | TODO | Separate transport cleanup from semantic job cancellation in service host. |
| HBG-31 | TODO | Prevent callback delivery failure from cancelling detached accepted work. |
| HBG-32 | TODO | Transfer active session/request ownership from connection ledger to logical job owner. |
| HBG-33 | TODO | Add reconnect/query/observe/cancel Binder contract and Consumer SDK adapter. |

### Lane D — host execution and recovery

| ID | State | Task |
| --- | --- | --- |
| HBG-40 | TODO | Add started + bound execution ownership while detached work exists. |
| HBG-41 | TODO | Add Android foreground/user-visible execution path where platform rules require it. |
| HBG-42 | TODO | Add runtimeSessionId and stale non-terminal job reconciliation after host process restart. |
| HBG-43 | TODO | Add retry-attempt semantics without pretending token/KV checkpoint resume. |
| HBG-50 | TODO | Integrate detached jobs with existing warm-idle/model-residency policy. |

### Lane E — evidence

| ID | State | Task |
| --- | --- | --- |
| HBG-60 | TODO | Unit/integration tests: unbind/death does not cancel detached job; explicit cancel does. |
| HBG-61 | TODO | Two-APK emulator reconnect/process-death/fault-injection journeys. |
| HBG-62 | TODO | Require privacy-safe state artifact + UI screenshots for lifecycle E2E journeys. |
| HBG-63 | TODO | STRONG automated preflight on exact head/base. |
| HBG-64 | TODO | Representative ARM64 same-signer two-APK + real-GGUF evidence. |

## Integration points

1. Lane A and Lane B can ship without public protocol changes.
2. HBG-22/23 must be stable before HBG-30/31 removes current connection-owned cancellation.
3. HBG-33 is the contract integration point consumed by RedactGuard reconciliation.
4. HBG-40/41 follows logical-job ownership so service lifetime reflects semantic work rather than Binder count.
5. Physical validation begins only after automated E2E/preflight is green.

## Fault matrix

| Event | Required semantic result |
| --- | --- |
| Host UI recreation | no inference/job change |
| Consumer UI recreation/navigation | no inference/job change |
| Consumer app background | accepted job continues under valid Android execution policy |
| Binder callback death | transport observer removed; detached job survives |
| Consumer process death | host job survives while host process survives; later authenticated reconciliation |
| Host process death | old non-terminal job becomes INTERRUPTED/recoverable-or-source-required, never zombie RUNNING |
| Explicit cancel | idempotent semantic cancellation |
| LOW_MEMORY | runtime policy may cancel/release according to declared pressure policy |
| idle residency deadline | only idle/unprotected runtime resources become unloadable |

## Documentation impact

Expected durable owners: ADR index, shared-runtime host specification/architecture, implementation plan, Consumer SDK/API docs, `.engineering/e2e.json`, current state and affected README usage only if public setup/behavior changes. Stable repository mission/positioning remains unchanged.

## Validation

Default classification: STRONG once executable shared-runtime/Binder/service behavior changes. Pure state primitives may use focused module tests while iterating, but publication cannot downgrade required cross-boundary gates. Emulator fidelity remains simulated/emulated; representative background/process/model claims require physical ARM64 evidence.