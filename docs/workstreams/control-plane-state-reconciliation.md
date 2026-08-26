# Control-plane state reconciliation workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test + models/control-plane-room-store
Canonical scope: workstream.control-plane-state-reconciliation
Read when: implementing or coordinating persisted Harness control-plane bootstrap, repair and upgrade safety
Last reviewed: 2026-08-26

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
| CPREC-10 | Canonical built-in control-plane spec + pure reconciliation contract/algorithm | new app-owned built-in spec/reconciler sources + focused unit tests | CPREC-00 | CPREC-20 | READY |
| CPREC-20 | Persistence/atomicity/reopen regression harness for partial v2 state | `models/control-plane-room-store/src/androidTest/**` new/isolated tests and fixtures only | CPREC-00 | CPREC-10 | READY |
| CPREC-30 | Cut startup composition over to reconciliation and remove Binder-path seeding | `HarnessRuntimeGraph.kt`, `HarnessSharedRuntimeService.kt`, `HarnessConsumerControlPlaneHost.kt` + direct tests | CPREC-10 | CPREC-40 | BLOCKED |
| CPREC-40 | Build app-level regression matrix against the settled reconciliation contract | new/isolated phone-app test files/fixtures only; no production sources | CPREC-10 | CPREC-30 | BLOCKED |
| CPREC-50 | Prove UI gateway and Binder discovery/activation observe one reconciled canonical graph | new integration tests across Applications gateway + consumer control-plane host | CPREC-20, CPREC-30, CPREC-40 | — | BLOCKED |
| CPREC-70 | Integrate current `dev`, run cumulative exact-head repository gates and produce signed candidate | integration branch, validation/packaging metadata only | CPREC-50 | — | BLOCKED |
| CPREC-80 | Physical upgrade-repair proof without uninstall/clear-data | device evidence/runbook output only | CPREC-70 | CPREC-90 logically; serialize on one device | BLOCKED |
| CPREC-90 | Clean physical two-APK HCP/ACUX proof on the repaired candidate | device evidence/runbook output only | CPREC-70 | CPREC-80 logically; serialize on one device | BLOCKED |
| CPREC-100 | Transfer durable behavior/evidence, unblock dependent work and delete temporary workstream | durable docs/current-state + workstream cleanup | CPREC-80, CPREC-90 | — | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

Parallel work has explicit non-conflicting write ownership. CPREC-10 and CPREC-20 may start immediately. After CPREC-10 fixes the integration contract, CPREC-30 production composition and CPREC-40 tests may proceed in parallel. CPREC-80 and CPREC-90 are logically independent evidence gates but cannot execute simultaneously on the same physical device.

## Current executable slices

`CPREC-10` and `CPREC-20`.

### CPREC-10 acceptance

- one canonical app-owned definition supplies mandatory built-in application/use-case/preset/binding/exposure identities;
- reconciliation distinguishes missing state from incompatible built-in identity;
- missing built-ins are merged conservatively without replacing unrelated state;
- explicit disabled state and valid user-selected defaults remain unchanged;
- repeated reconciliation does not create revisions, duplicate entities or timestamp churn;
- no Room/app transport types leak into the pure reconciliation contract.

Validation:

- focused `:models:model-profile` and phone-app unit tests covering canonical-state invariants and reconciliation behavior.

### CPREC-20 acceptance

- a Room v2 database can be opened with representative partial control-plane state;
- transaction/reopen tests prove no partial write is exposed after failure and a successful reconciled state persists across close/reopen;
- the generic Room module remains free of Harness/RedactGuard built-in policy.

Validation:

- `:models:control-plane-room-store` instrumented migration/store tests on emulator/device-capable CI where available; host-side checks remain preflight only.

## Required regression matrix

Repository tests must cover at least: empty state; application present but PII graph absent; use case present but binding absent; binding present but exposure absent; unrelated app/custom preset preservation; valid custom/default preservation; explicit disabled-state preservation; exact second-pass no-op; conflicting built-in identity fail-closed; Room close/reopen persistence; Apps gateway and Binder discovery seeing the same app/use-case/binding/preset identity.

## Integration points

- `HarnessBuiltInControlPlaneSpec` (name may vary) is app-owned policy; the generic Room store persists state but does not know built-in semantics.
- `HarnessControlPlaneReconciler` (name may vary) operates on neutral `HostControlPlaneState`/`HostControlPlaneStore` contracts and returns a bounded success/no-op/conflict outcome.
- `HarnessRuntimeGraph.from(...)` completes reconciliation before exposing the process-scoped graph; service/UI do not open a parallel database owner.
- `HarnessConsumerControlPlaneHost` becomes discovery/activation only; no `ensureSeeded()` or equivalent persistent mutation remains in read paths.
- If CPREC-10 proves current identities cannot safely distinguish preserve-vs-conflict semantics, stop and explicitly re-plan a schema/provenance slice plus migration tests instead of adding an opportunistic Room v3 migration.

## Physical evidence gates

CPREC-80 must install the new signed Harness candidate over the existing application without uninstall or clear-data, then prove Apps/RedactGuard assignment recovery while already-installed GGUF and valid persisted configuration remain intact.

CPREC-90 then runs the clean same-signer two-APK path and proves persisted default after Harness restart, real consumer discover/activate/infer with exact application/use-case/binding/preset identity, and stale/invalid fail-closed behavior. Existing ACUX-90/HCP evidence may be satisfied only by exact candidate evidence that meets their independent criteria.

## Durable documentation destinations

- `docs/architecture.md`: startup/control-plane ownership only if the durable lifecycle description changes materially;
- `docs/features/application-control-plane-ux.md`: durable user-visible/effective behavior if Apps semantics change;
- `docs/current-state.md`: blocker/next-action status and final evidence result, not implementation diary;
- tests/contracts: executable truth for idempotence, preservation, conflict and cross-surface consistency.

## Completion

The workstream is complete only when repository behavior, persistence/restart behavior, exact-head validation, upgrade-repair physical evidence, clean two-APK evidence and durable documentation agree. Then update dependent ACUX/HCP state, remove the temporary current-state link and delete this file by default.