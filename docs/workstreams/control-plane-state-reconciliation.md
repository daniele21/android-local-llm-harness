# Control-plane state reconciliation workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test + models/control-plane-room-store
Canonical scope: workstream.control-plane-state-reconciliation
Read when: implementing or coordinating persisted Harness control-plane bootstrap, repair and upgrade safety
Last reviewed: 2026-08-27

## Goal

Make the persisted Harness control plane converge at process startup to the mandatory built-in application/use-case/preset graph before either Apps UI or Binder consumers can observe it, while preserving valid user state, remaining idempotent across restarts/upgrades and failing closed on conflicting built-in identity.

## Non-goals

- no RedactGuard protocol/API redesign;
- no phone-global model fallback or implicit model selection/load/download/inference;
- no destructive database reset to repair partial state;
- no silent re-enable of explicitly disabled valid bindings or replacement of a valid user-selected default;
- no Room schema bump unless implementation evidence proves provenance cannot be handled safely with current identities;
- no claim that CI/emulator evidence proves upgrade or two-APK physical behavior.

## Invariants

- `HarnessRuntimeGraph` remains the single process-scoped owner of the Room control-plane store shared by UI and service surfaces;
- Binder discovery/activation reads do not seed or repair persistent state as a side effect;
- reconciliation is atomic, deterministic and idempotent: a second pass over a reconciled state is an exact semantic no-op;
- missing mandatory built-in state may be reconstructed; incompatible built-in identity fails closed instead of being overwritten;
- unrelated applications, custom presets, valid defaults, timestamps and explicit disabled state are preserved;
- external consumers still require explicit application/use-case activation; no global selected-model fallback returns;
- prompts, generated content and private document data never enter reconciliation diagnostics/evidence.

## Work graph

| ID | Work | Owns/writes | Depends on | Parallel | State |
| --- | --- | --- | --- | --- | --- |
| CPREC-00 | Freeze root cause, scope, invariants and execution DAG | this workstream + state links only | — | — | DONE |
| CPREC-10 | Canonical built-in control-plane spec + pure reconciliation contract/algorithm | app-owned built-in spec/reconciler sources + focused unit tests | CPREC-00 | CPREC-20 | DONE |
| CPREC-20 | Persistence/atomicity/reopen regression harness for partial v2 state | `models/control-plane-room-store/src/androidTest/**` isolated tests and fixtures | CPREC-00 | CPREC-10 | DONE |
| CPREC-30 | Cut startup composition over to reconciliation and remove Binder-path seeding | `HarnessRuntimeGraph.kt`, `HarnessSharedRuntimeService.kt`, `HarnessConsumerControlPlaneHost.kt` + direct tests | CPREC-10 | CPREC-40 | DONE |
| CPREC-40 | Build app-level regression matrix against the settled reconciliation contract | isolated phone-app test files/fixtures | CPREC-10 | CPREC-30 | DONE |
| CPREC-50 | Prove UI gateway and Binder discovery/activation observe one reconciled canonical graph | integration tests across Applications gateway + consumer control-plane host | CPREC-20, CPREC-30, CPREC-40 | — | DONE |
| CPREC-70 | Integrate current `dev`, run cumulative exact-head repository gates and produce unambiguous candidate | integration/validation/packaging metadata | CPREC-50 | — | DONE |
| CPREC-80 | Physical upgrade-repair proof without uninstall/clear-data | device evidence/runbook output only | CPREC-70 | CPREC-90 logically; serialize on one device | READY |
| CPREC-90 | Clean physical two-APK HCP/ACUX proof on the repaired candidate | device evidence/runbook output only | CPREC-70 | CPREC-80 logically; serialize on one device | READY |
| CPREC-100 | Transfer durable behavior/evidence, unblock dependent work and delete temporary workstream | durable docs/current-state + workstream cleanup | CPREC-80, CPREC-90 | — | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

Repository implementation converged through PRs #455, #459, #457, #458, #460 and the main-thread startup regression fix #461. PR #464 cut the unambiguous post-reconciliation Harness v30 candidate. These repository-side slices are complete; physical evidence remains independent and cannot be inferred from CI.

## Current executable slices

`CPREC-80` and `CPREC-90` are repository-ready physical gates. Because CRV now changes the same consumer activation/runtime path, do not spend closure evidence on a candidate that will immediately be invalidated by material CRV integration. Prefer the next exact integrated Harness/RedactGuard candidate after CRV deterministic gates, then reuse one representative two-APK session where criteria align while recording each CPREC/ACUX/HCP/CRV result independently.

### Integrated repository acceptance

Repository evidence covers:

- one canonical app-owned definition for mandatory built-in application/use-case/preset/binding/exposure identities;
- conservative repair of missing built-ins without replacing unrelated/custom/default/disabled state;
- exact repeated-reconciliation no-op and fail-closed identity conflicts;
- Room close/reopen atomic persistence and rollback behavior for partial state;
- startup reconciliation before UI/Binder readers are exposed;
- no Binder-path persistent seeding side effect;
- startup persistence work kept off the Android main thread while preserving typed failures;
- Applications gateway and consumer control-plane host observing the same reconciled graph.

## Required regression matrix

Repository tests cover the required empty/partial/preservation/idempotence/conflict/reopen/cross-surface cases. Physical gates must additionally prove the same behavior survives real package/process lifecycle and same-signer cross-app execution.

## Integration points

- `HarnessBuiltInControlPlaneSpec` is app-owned policy; the generic Room store persists state but does not know built-in semantics.
- `HarnessControlPlaneReconciler` operates on neutral `HostControlPlaneState`/`HostControlPlaneStore` contracts and returns bounded success/no-op/conflict behavior.
- `HarnessRuntimeGraph` completes reconciliation before exposing the process-scoped graph; service/UI do not open a parallel database owner.
- `HarnessConsumerControlPlaneHost` is discovery/activation only; no `ensureSeeded()` or equivalent persistent mutation remains in read paths.
- Reconciliation persistence is executed off the Android main thread while remaining a startup readiness barrier.

## Physical evidence gates

CPREC-80 must install the exact signed Harness candidate over the existing application without uninstall or clear-data, then prove Apps/RedactGuard assignment recovery while already-installed GGUF and valid persisted configuration remain intact.

CPREC-90 then runs the clean same-signer two-APK path and proves persisted default after Harness restart, real consumer discover/activate/infer with exact application/use-case/binding/preset identity, and stale/invalid fail-closed behavior. Existing ACUX-90/HCP evidence may be satisfied only by exact candidate evidence that meets their independent criteria.

Material changes to control-plane activation, runtime binding or consumer execution after a device run invalidate the affected exact-head evidence. Therefore the preferred closure candidate is the post-CRV integrated build rather than the earlier v30 repository candidate.

## Durable documentation destinations

- `docs/architecture.md`: startup/control-plane ownership only if the durable lifecycle description changes materially;
- `docs/features/application-control-plane-ux.md`: durable user-visible/effective behavior if Apps semantics change;
- `docs/current-state.md`: blocker/next-action status and final evidence result, not implementation diary;
- tests/contracts: executable truth for idempotence, preservation, conflict and cross-surface consistency.

## Completion

The workstream is complete only when repository behavior, persistence/restart behavior, exact-head validation, upgrade-repair physical evidence, clean two-APK evidence and durable documentation agree. Then update dependent ACUX/HCP state, remove the temporary current-state link and delete this file by default.
