# Control-plane state reconciliation workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test + models/control-plane-room-store
Canonical scope: workstream.control-plane-state-reconciliation
Read when: implementing or coordinating persisted Harness control-plane bootstrap, repair and upgrade safety
Last reviewed: 2026-08-28

Repository implementation is complete; physical evidence remains pending.

## Goal

Make the persisted Harness control plane converge at process startup to the mandatory built-in application/use-case/preset graph before either Apps UI or Binder consumers can observe it, while preserving valid user state, remaining idempotent across restarts/upgrades and failing closed on conflicting built-in identity.

## Invariants

- `HarnessRuntimeGraph` remains the single process-scoped owner of the Room control-plane store shared by UI and service surfaces.
- Binder discovery/activation reads do not seed or repair persistent state as a side effect.
- Reconciliation is atomic, deterministic and idempotent; a second pass over reconciled state is a semantic no-op.
- Missing mandatory built-in state may be reconstructed; incompatible built-in identity fails closed instead of being overwritten.
- Unrelated applications, custom presets, valid defaults, timestamps and explicit disabled state are preserved.
- External consumers require explicit application/use-case activation; there is no phone-global selected-model fallback.
- Reconciliation diagnostics/evidence never contain prompts, generated content or private document data.

## Work graph

| ID | Work | Evidence | State |
| --- | --- | --- | --- |
| CPREC-00 | Scope, invariants and execution DAG | #451 | DONE |
| CPREC-10 | Canonical built-in spec + pure reconciliation | #455 | DONE |
| CPREC-20 | Room partial-state persistence/atomicity/reopen regression | #459; #456 retained as superseded historical evidence | DONE |
| CPREC-30 | Startup composition cutover; remove Binder-path seeding | #457 | DONE |
| CPREC-40 | Upgrade/reconciliation regression matrix | #458 | DONE |
| CPREC-50 | Cross-surface Applications/Binder consistency | #460 | DONE |
| CPREC-60 | Main-thread startup regression correction | #461 | DONE |
| CPREC-70 | Integrated reconciled Harness candidate and automated gates | #464 v30; superseded for the shared physical session by CRV Harness v31 | DONE |
| CPREC-80 | Physical upgrade-repair proof without uninstall/clear-data | representative ARM64 device | READY |
| CPREC-90 | Clean physical two-APK HCP/ACUX proof | shared CRV-110 physical session where acceptance criteria overlap | READY |
| CPREC-100 | Durable handoff and temporary workstream cleanup | CPREC-80 + CPREC-90 | BLOCKED |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## Integrated repository behavior

Repository-side reconciliation is no longer a planned repair. Current `dev` already owns the fix:

- a canonical Harness-owned built-in graph defines mandatory application/use-case/preset/binding/exposure identities;
- startup performs conservative atomic reconciliation before UI/Binder readers are exposed;
- valid custom/default/disabled state is preserved and incompatible built-in identity fails closed;
- startup persistence work stays off the Android main thread while remaining a readiness barrier;
- Binder discovery/activation is read-only with respect to bootstrap/repair;
- Applications and the consumer control-plane host read the same reconciled persisted graph;
- Room close/reopen, rollback, idempotence and cross-surface consistency are covered by deterministic repository tests.

The original CPREC v30 candidate established post-reconciliation lineage. CRV subsequently produced Harness v31 at source `a30f67b21e24adc6efea838e9a9d65cc78446f28`, which includes the integrated CPREC behavior plus the converged runtime-readiness work and is the current frozen Harness source for the shared physical two-APK session.

## Remaining physical evidence

### CPREC-80 — upgrade repair

Install the signed Harness candidate over representative pre-reconciliation persisted state **without uninstall or clear-data**. Prove that mandatory built-ins are repaired while already-installed GGUF, unrelated/custom state, valid defaults and explicit disabled state remain intact. A conflict must fail closed rather than reset or overwrite state.

### CPREC-90 — clean two-APK path

On a clean target, prove persisted default after Harness restart plus real RedactGuard discovery, activation and inference against the exact application/use-case/binding/preset identity, including a stale/invalid fail-closed path.

CPREC-90 may share one physical session with CRV-110 / RG-HCP-8 / ACUX-90 where the exact candidate and scenario satisfy each gate independently. Shared execution does not collapse their acceptance criteria.

CI, emulator and package evidence do not satisfy CPREC-80/90.

## Completion

After CPREC-80 and CPREC-90 pass on one recorded exact identity set, transfer the evidence to durable current-state/feature owners, reconcile dependent ACUX/HCP state, and remove this temporary workstream by default.
