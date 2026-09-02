# Background/process lifecycle hardening

Status: active
Document type: workstream-state
Owner: shared-runtime/runtime lifecycle
Canonical scope: workstream.background-process-lifecycle
Read when: coordinating detached inference jobs, Binder reconnect, Host background execution, process recovery or downstream lifecycle evidence
Last reviewed: 2026-09-02

Repository priority and integrated/blocker truth remain owned by [`../current-state.md`](../current-state.md). This workstream coordinates only the bounded implementation sequence.

## Goal

Make explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, Harnex-owned runtime/model authority, exact accepted execution identity, explicit cancellation, bounded resources and privacy-safe recovery. Ordinary connection-scoped Consumer inference keeps ADR 0012 lifecycle semantics.

Canonical architectural decision: [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Non-goals

- Do not make passive setup/readiness create durable work or residency demand.
- Do not persist prompt/document/generated content to manufacture process-death recovery.
- Do not treat Binder reconnect as permission to re-resolve an accepted job against newer configuration.
- Do not patch model/resolver/runtime behavior to satisfy a downstream generic failure before typed evidence identifies the canonical owner.
- Do not infer physical ARM64/JNI/GGUF/OEM behavior from emulator evidence.

## Invariants

- UI lifecycle, consumer workflow lifecycle, Binder lifecycle and runtime/model lifecycle are separate.
- Durable jobs are explicit/capability-negotiated; passive setup/readiness and ordinary legacy inference never create one implicitly.
- Binder death/unbind removes transport observers; it does not semantically cancel an accepted durable job.
- Logical job identity is stable across reconnect and idempotent resubmit.
- Accepted jobs pin the exact `ConsumerExecutionIdentity` returned by preparation; reconnect never silently re-resolves the same job against newer configuration.
- Harnex owns `ConsumerInferenceJobId`, execution/session ownership, Android service lifetime and model/runtime authority. RedactGuard owns product `AnalysisJobId` and document orchestration.
- Callback delivery is advisory. Revisioned `query/result` state is authoritative after reconnect; generated content is not turned into a durable callback log.
- Existing connection-scoped prepare/session/generate behavior remains backward-compatible.
- Host process death is an interruption boundary, never reported as continued RUNNING.
- Active durable demand composes with the existing single-resident-model/runtime-resource policy; it does not create a second residency owner.
- Prompt/document/generated content is not added to durable storage, telemetry or shared evidence.
- Real-device background/model behavior is not inferred from emulator/CI evidence.

## Resource ownership

| Resource | Semantic owner | Transport loss | Terminal/cleanup owner |
| --- | --- | --- | --- |
| Binder registration/death link/callback dispatcher | connection | removed | connection cleanup |
| durable logical inference job | Host logical-job registry/coordinator | survives | job terminal/policy |
| session/generation handle retained by durable work | logical job/runtime | survives | job terminal/cancel/runtime policy |
| started/foreground service demand | active durable-job demand | survives | final active durable job terminal |
| idle client session | authenticated connection/session policy | bounded cleanup allowed | session policy |
| loaded model | existing runtime/residency policy | unaffected by observer loss | explicit unload/idle/pressure/shutdown |

## Work graph

| ID | Work | Owns/writes | Depends on | Parallel | State |
| --- | --- | --- | --- | --- | --- |
| HBG-00 | Successor ADR separating Binder/client lifetime from explicit durable jobs/runtime lifetime | `docs/adr/0016-*` | — | yes | DONE |
| HBG-10 | Resource ownership matrix | ADR/workstream/runtime ownership | HBG-00 | yes | DONE |
| HBG-20 | Logical job primitives/state/revision/attempt/runtime-session model | Host logical-job contracts/runtime | HBG-00 | yes | DONE |
| HBG-21 | Deterministic transition/idempotency tests | logical-job tests | HBG-20 | yes | DONE |
| HBG-22 | Bounded process-local job registry | Host logical-job registry | HBG-20 | yes | DONE |
| HBG-23 | Authenticated application/use-case idempotency lookup | Host registry/control plane | HBG-22 | yes | DONE |
| HBG-24 | Exact prepared `ConsumerExecutionIdentity` pinning | Consumer logical-job contract/Host | HBG-20..23 | no | DONE |
| HBG-30 | Separate connection cleanup from durable-job cancellation | Binder Service/Host | HBG-22 | yes | DONE |
| HBG-31 | Detached execution independent from callbacks | Host coordinator/Binder | HBG-30 | yes | DONE |
| HBG-32 | Transfer session/generation-handle ownership to durable coordinator | Host/runtime | HBG-31 | no | DONE |
| HBG-33 | Publish protocol minor 6 logical-job API | Binder protocol/Consumer SDK | HBG-24/32 | no | DONE |
| HBG-34 | Preserve ADR 0012 legacy connection cleanup | Binder compatibility | HBG-30/33 | yes | DONE |
| HBG-40 | Started-service ownership while durable demand exists | Android Host Service | HBG-32 | yes | DONE |
| HBG-41 | Foreground/user-visible execution adapter | Android Host Service/notification | HBG-40 | no | DONE |
| HBG-42 | Reconcile non-terminal metadata after real Host process restart | Host logical-job recovery/tests/docs | HBG-22/40 | yes | READY |
| HBG-43 | Retry-attempt semantics only where safe input remains available | Host recovery/contracts | HBG-42 | no | BLOCKED |
| HBG-50 | Prove active durable demand composes with residency/memory policy | runtime/residency tests/evidence | HBG-32/40 | yes | READY |
| HBG-55 | RedactGuard stable `AnalysisJobId` + logical-job consumption | downstream RedactGuard owner | HBG-33 | yes | DONE |
| HBG-56 | Attribute current setup-path failure from typed downstream evidence before any Harnex correction | setup/control-plane owner selected by evidence | RedactGuard LAS-09/10 | no | BLOCKED |
| HBG-60 | Complete disconnect/reconnect/cancel deterministic matrix | Binder/Host tests | HBG-33/55 | yes | READY |
| HBG-61 | Complete dedicated Two-APK lifecycle journeys | cross-repo E2E/workflows | HBG-55/60 | no | READY |
| HBG-62 | Privacy-safe lifecycle state artifact + screenshots/video | E2E evidence | HBG-61 | no | BLOCKED |
| HBG-63 | Final exact-head STRONG automated preflight | repository automation | HBG-42/50/60..62 | no | BLOCKED |
| HBG-64 | Representative ARM64 same-signer GGUF/model-residency/OEM evidence | real device evidence | HBG-63 | no | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## Current executable slices

### HBG-42 — Host restart reconciliation

Acceptance:

- Process-local non-terminal logical-job metadata from an earlier Host process cannot reappear as `RUNNING` after actual Host restart.
- Recovered impossible native work becomes a stable structured interruption/process-loss outcome.
- No prompt, document or generated content persistence is introduced.
- Query/result after restart remains caller/use-case isolated.
- The implementation does not imply native session/decode/model RAM survived process death.

Validation:

- deterministic registry/recovery unit tests;
- affected Binder/Host integration tests;
- repository `auto` selector, expected `STRONG` for shared runtime/lifecycle behavior;
- later Two-APK fault injection for Android process behavior.

### HBG-50 / HBG-60 — independent deterministic evidence

These may progress in parallel only where they do not write the same canonical Host/runtime owners as HBG-42. HBG-50 proves residency composition rather than adding a new model owner. HBG-60 closes transport disconnect/reconnect/cancel semantics without changing recovery persistence.

## Downstream setup-resolution checkpoint

Harnex functional `dev` source `f86b53ad29d2396660f095d5eaadd41c19bda8c7` contains PR #511 and published Consumer SDK `0.1.0-alpha.9`; the current `dev` successor changes only lifecycle planning documentation.

RedactGuard exact Two-APK run `33594812860` proves the Host build and initial cross-process control-plane path reaches protocol minor 6, setup-resolution capability negotiation, one assigned use case and one validated published preset. The downstream product then reports generic incompatibility before a setup-resolution diagnostic is emitted.

That generic symptom is insufficient to assign a Harnex model/resolver/runtime defect. RedactGuard LAS-09/10 must first preserve and expose the original typed `ConsumerControlPlaneErrorCode`. Only then may HBG-56 select the confirmed canonical owner and create a bounded Harnex correction if the evidence actually points here.

## Fault matrix

| Event | Required semantic result |
| --- | --- |
| Host UI recreation | no inference/job change |
| Consumer UI recreation/navigation | no inference/job change; no duplicate job |
| Consumer app background | accepted durable job continues under valid Android execution policy |
| Binder callback death/unbind | transport observer removed; durable job survives |
| Consumer process death | Host job may survive while Host process survives; later authorized reconciliation does not expose another caller's work |
| Host process death | old native work becomes interrupted/process-lost; never zombie RUNNING |
| Explicit cancel while detached | idempotent semantic cancellation with one terminal result |
| Authorization loss | explicit fail-closed outcome; no cross-caller reattachment |
| Model/config conflict | explicit fail-closed outcome; no silent substitution |
| Critical memory pressure | runtime policy may cancel/release for system health; impossible continuation is explicit interruption |
| Idle residency deadline | only genuinely idle/unprotected resources become unloadable |
| Persisted evidence inspection | no prompt/document/generated-output content present |

## Integration points

1. HBG-24/HBG-33 remain the Consumer contract prerequisites consumed by RedactGuard.
2. HBG-42 owns Host process-restart reconciliation only; it must not add durable sensitive payload persistence.
3. HBG-50 composes durable demand with the existing runtime/residency owner rather than adding a second policy.
4. HBG-56 is evidence-gated: no Harnex setup/model correction from the current generic downstream `INCOMPATIBLE` symptom.
5. HBG-61/62 integrate with RedactGuard's Two-APK workflow after the typed setup blocker is resolved.
6. Physical validation begins after deterministic lifecycle E2E and exact-head automated preflight are green.

## Durable documentation destinations

- `docs/adr/0016-detached-shared-runtime-jobs.md`: durable lifecycle/ownership decision.
- `docs/shared-runtime/consumer-android-sdk.md`: public logical-job/reconnect behavior when it changes.
- `.engineering/e2e.json`: lifecycle journeys, automated environment and residual physical gaps.
- `docs/current-state.md`: integrated state/blockers only.
- tests/contracts: executable state-transition, restart, isolation and cleanup truth.

README identity is unchanged. README usage changes only if the public Consumer setup/API path changes.

## Validation

Shared-runtime/Binder/Service/recovery behavior is `STRONG` by blast radius. CI/global Gradle/module-inventory changes remain `FULL` when the selector requires them. Deterministic compile/unit/static/lint/Binder/emulator/packaging work is `AGENT_LOCAL` only with an equivalent environment; otherwise it is `REMOTE_AUTOMATED` through repository-owned automation.

Representative same-signer ARM64/JNI/GGUF model residency, OEM process policy, memory/thermal behavior and protected-device evidence remain separate `REAL_ENVIRONMENT` gates.

## Completion

This workstream completes only when code, Host/Consumer contracts, downstream consumption, process-loss semantics, resource ownership, automated lifecycle evidence, durable docs and required physical evidence agree. Then transfer integrated state to `docs/current-state.md` and delete this workstream by default under repository documentation policy.
