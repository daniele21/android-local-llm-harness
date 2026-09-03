# Background/process lifecycle hardening

Status: active
Document type: workstream-state
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Read when: coordinating durable inference jobs, Binder reconnect, Host process recovery or lifecycle evidence
Last reviewed: 2026-09-03

Repository priority/blocker truth remains in [`../current-state.md`](../current-state.md). Canonical architecture is [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Goal

Keep explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, exact execution identity, bounded resources and privacy-safe recovery.

## Invariants

- UI, Consumer workflow, Binder and runtime/model lifecycles are separate.
- Durable jobs are explicit/capability-negotiated; Binder death removes observers, not accepted durable work.
- `ConsumerInferenceJobId` is stable across reconnect/idempotent resubmit and pins the prepared `ConsumerExecutionIdentity`.
- Harnex owns inference jobs, execution/session lifetime, Host service lifetime and model/runtime authority; Consumer apps own product workflow/recovery.
- Revisioned `query/result` is authoritative after reconnect; callbacks are advisory.
- Host process death becomes `INTERRUPTED`; native work is never claimed to survive process death.
- Explicit user cancellation remains `CANCEL_REQUESTED -> CANCELLED`.
- Critical runtime pressure is a runtime failure boundary, not user cancellation and not Host process loss.
- Failure identity is typed/versioned; free-form Host messages are not product policy.
- Sensitive input/output stays out of durable storage, normal telemetry and shared evidence.

## Resource ownership

| Resource | Owner | On Binder loss | On critical pressure/process loss |
| --- | --- | --- | --- |
| Binder registration/callback | connection | removed | removed with process/connection |
| durable logical job | Host job coordinator | survives | terminalized/reconciled by Host owner |
| session/generation handle | logical job/runtime | survives ordinary Binder loss | cancelled/released |
| started/foreground demand | active durable demand | survives ordinary Binder loss | released after terminalization |
| loaded model | runtime/residency policy | unchanged | runtime-core pressure policy owns cleanup |

## Integrated checkpoint

- PR #502 delivered durable logical jobs, exact prepared identity, detached execution ownership and started/foreground Host demand.
- PR #510 delivered the signature-protected emulator generation fault gate.
- PR #511 published Consumer SDK `0.1.0-alpha.9` with concrete Binder setup-resolution forwarding.
- PR #517 fixed warm-retention Host setup-resolution forwarding.
- PR #518 / HBG-42 is merged on `dev` at `fc525a301a208f7f243ddbf87c0d523c39097627`: stale non-terminal metadata from a previous Host runtime is reconciled to `INTERRUPTED` without reconstructing native work or sensitive input.
- RedactGuard LAS-09..14, authoritative Home/app-switch lifecycle probing, Host-process-loss, RedactGuard-process-loss and explicit-cancel instrumentation are integrated on its LAS candidate.
- Existing Harnex tests already prove single-decode non-overlap and bounded logical-job admission; the current pressure slice adds explicit cross-consumer bounded-admission coverage.

## Active candidate: critical memory pressure

PR #525 owns the remaining Harnex-side LAS-08C pressure gap.

Target behavior:

1. Android `TRIM_MEMORY_RUNNING_CRITICAL` maps to existing `RuntimeMemoryPressure.LOW_MEMORY`.
2. Before runtime-core executes `CANCEL_AND_RELEASE_ALL`, the Host terminalizes active durable logical jobs as `FAILED_FINAL` with existing `RUNTIME_FAILURE` identity.
3. Host execution handles/sessions and durable execution demand are aborted/released.
4. Runtime-core remains the canonical owner of generation/session/model cleanup.
5. A late backend `CANCELLED` callback cannot rewrite an already terminal pressure failure.
6. `CANCELLED` therefore remains reserved for explicit cancellation, while `INTERRUPTED` remains the Host-process-loss boundary.

This adds no public Binder/Consumer protocol shape and no Consumer-side memory policy.

## Work graph

| ID | Work | State |
| --- | --- | --- |
| HBG-00 | Lifecycle ADR/resource ownership | DONE |
| HBG-20 | Logical-job state/registry/idempotency | DONE |
| HBG-24 | Exact prepared execution identity | DONE |
| HBG-30 | Detach connection cleanup from durable cancellation | DONE |
| HBG-33 | Minor-6 submit/query/result/cancel API | DONE |
| HBG-40 | Started/foreground Host lifetime | DONE |
| HBG-42 | Reconcile stale non-terminal jobs after Host restart | DONE |
| HBG-43 | Retry-attempt semantics only with safely available input | READY |
| HBG-50 | Prove active demand composes with residency policy | BLOCKED |
| HBG-55 | RedactGuard logical-job consumption/reattach | DONE |
| HBG-56 | Typed setup-failure routing / confirmed owner fix | DONE |
| HBG-60 | Disconnect/reconnect/cancel/pressure matrix | ACTIVE |
| HBG-61 | Canonical Two-APK app-switch/reconnect/process-loss/pressure journeys | ACTIVE |
| HBG-62 | Privacy-safe E2E evidence bundle | BLOCKED |
| HBG-63 | Final exact-head automated preflight | BLOCKED |
| HBG-64 | ARM64 same-signer real-GGUF/residency/OEM evidence | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## Remaining automated convergence

On exact Harnex/RedactGuard candidate identities:

1. execute Binder disconnect/rebind and preserve the same logical job with no implicit cancellation;
2. execute explicit cancel and prove `CANCEL_REQUESTED -> CANCELLED` plus waiter cleanup;
3. execute Host process loss and require downstream `HOST_PROCESS_LOST` recovery;
4. execute RedactGuard process loss and prove no sensitive process-local state is reconstructed;
5. execute Android critical-pressure injection through the real Host Service callback and require runtime-failure terminalization plus cleanup;
6. retain Home/app-switch and ViewModel recreation evidence;
7. combine bounded multi-consumer admission evidence with the existing single-decode no-overlap test;
8. run final exact-head selector-driven automated preflight.

Automatic retry after Host process loss remains HBG-43 and must never recreate sensitive input no longer owned by the process.

## Fidelity boundary

The API 35 Two-APK emulator can prove Android/Binder/job lifecycle semantics, including deterministic delivery of a critical trim callback. It does **not** prove physical RAM pressure, real memory reclamation, ARM64 JNI/llama.cpp execution, GGUF residency, thermal behavior or OEM-specific process policy.

Those claims remain HBG-64 / `REAL_ENVIRONMENT` evidence.

## Completion

This workstream completes only when deterministic cross-repo lifecycle journeys and exact-head automated gates pass, durable truth is transferred, and remaining physical claims are explicitly recorded rather than inferred from emulator evidence.
