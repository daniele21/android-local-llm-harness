# Background/process lifecycle hardening

Status: active
Document type: workstream-state
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Read when: coordinating durable inference jobs, Binder reconnect, Host process recovery or lifecycle evidence
Last reviewed: 2026-09-02

Repository priority/blocker truth remains in [`../current-state.md`](../current-state.md). Canonical architecture is [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Goal

Keep explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, exact execution identity, bounded resources and privacy-safe recovery.

## Non-goals

- Passive setup/readiness must not create durable work or residency demand.
- Do not persist prompt/document/generated content to manufacture process-death recovery.
- Do not claim native work survives Host process death or infer physical ARM64/JNI/GGUF/OEM behavior from emulator evidence.
- Do not move Harnex model/runtime/residency authority into a Consumer app to make lifecycle evidence pass.

## Invariants

- UI, Consumer workflow, Binder and runtime/model lifecycles are separate.
- Durable jobs are explicit/capability-negotiated; Binder death removes observers, not accepted durable work.
- `ConsumerInferenceJobId` is stable across reconnect/idempotent resubmit and pins the prepared `ConsumerExecutionIdentity`.
- Harnex owns inference jobs, execution/session lifetime, Host service lifetime and model/runtime authority; RedactGuard owns product `AnalysisJobId` and recovery UX.
- Revisioned `query/result` is authoritative after reconnect; callbacks are advisory and generated content is not a durable callback log.
- Legacy connection-scoped behavior remains backward-compatible.
- Host process death becomes explicit interruption/process loss, never zombie `RUNNING`.
- Failure identity is typed/versioned; free-form Host messages are not product policy.
- Sensitive content stays out of durable storage, normal telemetry and shared evidence.

## Resource ownership

| Resource | Owner | On transport loss | Cleanup |
| --- | --- | --- | --- |
| Binder registration/callback | connection | removed | connection |
| durable logical job | Host job coordinator | survives | terminal/policy |
| retained session/generation handle | logical job/runtime | survives ordinary Binder loss only | terminal/cancel/process loss |
| started/foreground service demand | active durable demand | survives ordinary Binder loss only | final active terminal/interruption |
| loaded model | runtime/residency policy | unchanged by Binder detachment | unload/idle/pressure/shutdown |

## Integration checkpoint

- PR #502 delivered durable logical jobs, exact prepared identity, detached execution ownership and started/foreground Host demand; logical jobs shipped in Consumer SDK `0.1.0-alpha.8`.
- PR #510 delivered the signature-protected emulator fault gate.
- PR #511 hardened durable Consumer setup-resolution/reconnect behavior; Consumer SDK `0.1.0-alpha.9` was published and resolved externally.
- PR #517 fixed the confirmed concrete Host forwarding defect for setup resolution through the warm-retention Host and is merged on `dev` at `50ca8bd8621bb5019a4539b36548a1500bbf2af9`.
- RedactGuard LAS-09..14 are integrated on its LAS parent; setup stage, product problem, recovery and technical identity are separated and setup observation/refresh is behind `RedactGuardProductViewModel`.
- RedactGuard Home/app-switch lifecycle evidence now uses AndroidX Activity lifecycle state rather than shell-text foreground inference and is integrated on its LAS parent at `4764251ab2f6fe6ca6ab31a64d24717aad58479e`.
- Binder disconnect/rebind coverage exists downstream and preserves the same Harnex logical-job identity without implicit cancellation; the canonical Two-APK workflow still needs to execute that journey.
- HBG-42 implementation head `9377db91389edd20d4f4e6ec0d856c54052172dc` passed repository-owned STRONG run `33684350491`: repository guards, selected Android validation, native packaging and repository validation all succeeded. This workstream update is followed by a final exact-head preflight before merge.

## Work graph

| ID | Work | Owns/writes | Depends on | Parallel | State |
| --- | --- | --- | --- | --- | --- |
| HBG-00 | Lifecycle ADR/resource ownership | ADR 0016 | — | yes | DONE |
| HBG-20 | Logical-job state/registry/idempotency | Host contracts/runtime/tests | HBG-00 | no | DONE |
| HBG-24 | Exact prepared execution identity | contracts/Host/SDK/tests | HBG-20 | no | DONE |
| HBG-30 | Detach connection cleanup from durable cancellation | Binder Host/tests | HBG-24 | no | DONE |
| HBG-33 | Minor-6 submit/query/result/cancel API | Binder/SDK/docs | HBG-30 | no | DONE |
| HBG-40 | Started/foreground Host lifetime | Host Service/Manifest/tests | HBG-33 | no | DONE |
| HBG-42 | Reconcile impossible non-terminal jobs after Host restart | Host recovery/tests/docs | HBG-40 | yes | DONE |
| HBG-43 | Retry-attempt semantics only with safely available input | recovery contracts/tests | HBG-42 | no | READY |
| HBG-50 | Prove active demand composes with existing residency policy | runtime/residency evidence | HBG-40, HBG-61 | no | BLOCKED |
| HBG-55 | RedactGuard logical-job consumption/reattach | downstream integration | HBG-33 | yes | DONE |
| HBG-56 | Route setup failure by typed evidence and fix confirmed owner | cross-repo evidence | RedactGuard LAS-09/10 | no | DONE |
| HBG-60 | Disconnect/reconnect/cancel matrix | Binder/Host + downstream tests | HBG-55, HBG-56 | no | ACTIVE |
| HBG-61 | Two-APK app-switch/reconnect/process-loss journeys | E2E/workflow | HBG-60 | no | READY |
| HBG-62 | Privacy-safe state + screenshot/video evidence | E2E evidence | HBG-61 | no | BLOCKED |
| HBG-63 | Final exact-head automated preflight | Harnex CI | HBG-43/50/62 | no | BLOCKED |
| HBG-64 | ARM64 same-signer real-GGUF/residency/OEM evidence | physical evidence | HBG-63 | no | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## HBG-42 completion evidence

HBG-42 establishes a durable, privacy-safe Host restart boundary:

- accepted logical-job transitions are persisted before becoming authoritative in memory;
- durable metadata contains logical identity/execution metadata only, never inference input, prompts, document text, generated output, reasoning, native handles or private model/runtime paths;
- a new Host runtime reconciles persisted non-terminal jobs from a previous `runtimeSessionId` to `INTERRUPTED` rather than reconstructing impossible native execution;
- reconciliation is idempotent for already interrupted and terminal jobs;
- persistence failure during execution aborts the current handle/session, releases durable execution demand and exposes an interrupted current-process view instead of allowing untracked work to continue;
- bounded terminal eviction updates durable metadata consistently with replacement admission;
- legacy connection-scoped inference contracts are unchanged.

The implementation head `9377db91389edd20d4f4e6ec0d856c54052172dc` passed STRONG run `33684350491`. Physical model-memory survival/reclamation claims remain explicitly outside that automated evidence.

## Current executable slice

`HBG-60 / HBG-61`

Immediate cross-repo evidence work:

1. run the existing Binder disconnect/rebind test in the canonical Two-APK workflow and preserve same logical-job identity/no implicit cancel;
2. run an active-job Host process-loss journey against the merged HBG-42 Host and require `INTERRUPTED` -> downstream typed `HOST_PROCESS_LOST` recovery;
3. keep Home/app-switch and ViewModel recreation evidence on the same exact RedactGuard/Harnex source identities;
4. add explicit-cancel Two-APK evidence where unit-level state-machine evidence is insufficient;
5. keep all evidence privacy-safe and bounded.

HBG-43 may proceed independently once it preserves the rule that retry cannot reconstruct sensitive input after the owning process has lost it.

## Memory-pressure owner

Critical memory-pressure policy already belongs to Harnex runtime-core:

- `RuntimeMemoryPolicy` maps `LOW_MEMORY` with active work to `CANCEL_AND_RELEASE_ALL`;
- `RuntimeOrchestrator.handleMemoryPressure(...)` owns execution of that runtime action;
- Android trim-memory callbacks map platform pressure into the runtime policy.

LAS/HBG lifecycle evidence must integrate durable logical-job state with this existing owner and prove a structured interruption/cleanup outcome. Do not create a duplicate Consumer-side memory policy.

## Integration points

1. HBG-42 owns Host restart reconciliation only; automatic attempt+1 retry remains HBG-43.
2. HBG-60/61 now own exact Binder-loss/app-switch/Host-process-loss Two-APK convergence against merged Harnex source.
3. Durable demand composes with the existing residency and memory-pressure owner; it never introduces a second model policy.
4. Physical validation follows deterministic Two-APK lifecycle evidence and exact-head automated preflight.

## Durable documentation destinations

- ADR 0016 for durable lifecycle decisions.
- shared-runtime/Consumer SDK docs for public contract changes.
- `.engineering/e2e.json` for journey/environment/fidelity truth.
- `docs/current-state.md` for integrated state/blockers.
- tests/contracts for transition, authorization and recovery truth.

## Completion

Complete only when Host restart cannot leave impossible jobs running, retry/recovery remains privacy-safe, exact downstream lifecycle journeys pass without duplicate generation, required deterministic exact-head gates pass, and residual physical ARM64/JNI/GGUF/OEM claims are separately evidenced. Then transfer durable truth and delete this workstream by default.
