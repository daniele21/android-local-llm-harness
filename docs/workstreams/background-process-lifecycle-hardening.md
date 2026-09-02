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
- Do not patch Harnex setup/model/runtime from RedactGuard's generic `INCOMPATIBLE`; wait for the typed `ConsumerControlPlaneErrorCode`.

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
| retained session/generation handle | logical job/runtime | survives | terminal/cancel |
| started/foreground service demand | active durable demand | survives | final active terminal |
| loaded model | runtime/residency policy | unchanged | unload/idle/pressure/shutdown |

## Integration checkpoint

- PR #502 delivered durable logical jobs, exact prepared identity, detached execution ownership and started/foreground Host demand; logical jobs shipped in Consumer SDK `0.1.0-alpha.8`.
- PR #510 delivered the signature-protected emulator fault gate.
- PR #511 fixed concrete Binder `resolveSetup` forwarding; Consumer SDK `0.1.0-alpha.9` was published and resolved externally.
- RedactGuard's LAS candidate consumes logical jobs/alpha.9 and includes app-switch, ViewModel and Binder lifecycle journeys.
- RedactGuard Two-APK run `33594812860` reached protocol minor 6, setup-resolution negotiation, one assigned use case and one validated preset, then stopped at generic `INCOMPATIBLE` before a setup-resolution diagnostic. This does not establish a Harnex defect; LAS must first preserve the typed failure.

## Work graph

| ID | Work | Owns/writes | Depends on | Parallel | State |
| --- | --- | --- | --- | --- | --- |
| HBG-00 | Lifecycle ADR/resource ownership | ADR 0016 | — | yes | DONE |
| HBG-20 | Logical-job state/registry/idempotency | Host contracts/runtime/tests | HBG-00 | no | DONE |
| HBG-24 | Exact prepared execution identity | contracts/Host/SDK/tests | HBG-20 | no | DONE |
| HBG-30 | Detach connection cleanup from durable cancellation | Binder Host/tests | HBG-24 | no | DONE |
| HBG-33 | Minor-6 submit/query/result/cancel API | Binder/SDK/docs | HBG-30 | no | DONE |
| HBG-40 | Started/foreground Host lifetime | Host Service/Manifest/tests | HBG-33 | no | DONE |
| HBG-42 | Reconcile impossible non-terminal jobs after Host restart | Host recovery/tests/docs | HBG-40 | yes | READY |
| HBG-43 | Retry-attempt semantics only with safely available input | recovery contracts/tests | HBG-42 | no | BLOCKED |
| HBG-50 | Prove active demand composes with existing residency policy | runtime/residency evidence | HBG-40, HBG-61 | no | BLOCKED |
| HBG-55 | RedactGuard logical-job consumption/reattach | downstream integration | HBG-33 | yes | DONE |
| HBG-56 | Route current setup failure by typed evidence | cross-repo evidence | RedactGuard LAS-09/10 | no | BLOCKED |
| HBG-60 | Disconnect/reconnect/cancel matrix | Binder/Host + downstream tests | HBG-55, HBG-56 | no | BLOCKED |
| HBG-61 | Two-APK app-switch/reconnect/process-loss journeys | E2E/workflow | HBG-60 | no | BLOCKED |
| HBG-62 | Privacy-safe state + screenshot/video evidence | E2E evidence | HBG-61 | no | BLOCKED |
| HBG-63 | Final exact-head automated preflight | Harnex CI | HBG-42/43/50/62 | no | BLOCKED |
| HBG-64 | ARM64 same-signer real-GGUF/residency/OEM evidence | physical evidence | HBG-63 | no | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## Current executable slice

`HBG-42`

Acceptance:

- after actual Host restart, recoverable non-terminal metadata whose native execution cannot exist becomes structured interrupted/process-lost state, never `RUNNING`;
- reconciliation is idempotent, bounded and caller/use-case isolated;
- no sensitive payload persistence is added;
- legacy connection-scoped inference remains unchanged;
- recovery metadata supports truthful explanation/retry without implying native RAM survived.

Validation:

- repository selector with `profile=auto`; expected `STRONG` for runtime/recovery behavior;
- owning logical-job tests plus direct Binder/Host consumers;
- unavailable deterministic Android gates via repository-owned remote automation;
- physical process/model-memory claims remain `REAL_ENVIRONMENT`.

## Setup-failure owner gate

No Harnex setup correction is READY. After RedactGuard LAS preserves a typed outcome:

| Outcome | Owner to inspect if evidence points to Harnex |
| --- | --- |
| `MODEL_UNAVAILABLE` / `MODEL_CONFLICT` | resolver, model store, binding/residency |
| configuration/use-case/preset/revision failures | control-plane state/reconciliation |
| `TRANSPORT_FAILURE` | Binder transport after request validity is proven |
| `FEATURE_UNAVAILABLE` | protocol/SDK artifact/concrete client delegation |
| `RUNTIME_FAILURE` | Host control-plane/runtime after transport validity |
| `INVALID_REQUEST` | downstream request first; Harnex only if request is valid |
| `Resolved` | downstream readiness/mapping owns the next investigation |

Do not change fixture models or weaken setup criteria merely to make E2E green.

## Integration points

1. HBG-42 owns Host restart reconciliation only; recovery persistence remains privacy-safe.
2. HBG-56 must resolve the setup blocker before HBG-60/61 can provide meaningful lifecycle acceptance evidence.
3. Durable demand composes with the existing residency owner; it never introduces a second model policy.
4. Physical validation follows deterministic Two-APK lifecycle evidence and exact-head automated preflight.

## Durable documentation destinations

- ADR 0016 for durable lifecycle decisions.
- shared-runtime/Consumer SDK docs for public contract changes.
- `.engineering/e2e.json` for journey/environment/fidelity truth.
- `docs/current-state.md` for integrated state/blockers.
- tests/contracts for transition, authorization and recovery truth.

## Completion

Complete only when Host restart cannot leave impossible jobs running, retry/recovery remains privacy-safe, exact downstream lifecycle journeys pass without duplicate generation, typed setup failures route to the correct owner, required deterministic exact-head gates pass, and residual physical ARM64/JNI/GGUF/OEM claims are separately evidenced. Then transfer durable truth and delete this workstream by default.