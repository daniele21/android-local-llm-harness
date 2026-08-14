# Model evaluation current state

Status: active
Document type: workstream-state
Owner: model-evaluation
Canonical scope: model-evaluation.state
Read when: determining current model-evaluation progress, blockers or the next ready tasks
Last reviewed: 2026-08-14

This is the operational status ledger for model evaluation. Repository-level integrated state and immediate sequencing remain in [`../current-state.md`](../current-state.md). Capability sequencing inside this feature is owned by [`roadmap.md`](roadmap.md); detailed acceptance criteria remain in the owning workstream specifications.

## Milestone state

| Milestone | State | Current outcome |
| --- | --- | --- |
| EVAL-0 Plan and architecture | DONE | Scope, ownership, dependency graph and maintenance rules are documented. |
| EVAL-1 Contracts and identity | READY | First implementation milestone; EVAL-C-01 is ready. |
| EVAL-2 Dataset system | PLANNED | Waits for shared contracts. |
| EVAL-3 Deterministic evaluators | PLANNED | Waits for evaluator contracts. |
| EVAL-4 Evaluation runner | PLANNED | Runner skeleton unlocks after EVAL-1. |
| EVAL-5 Persistence and comparison | PLANNED | Repository skeleton unlocks after EVAL-1. |
| EVAL-6 General Purpose v1 | PLANNED | Source/license and pack assembly depend on dataset/evaluator foundations. |
| EVAL-7 Performance UI/custom import | PLANNED | UI fake shell unlocks after EVAL-1; connected completion depends on engine/stores. |
| EVAL-8 Validation/device evidence | PLANNED | Deterministic validation proceeds incrementally; final device evidence is late-gated. |

## Ready now

- `EVAL-C-01` — establish concrete package/module ownership for model-evaluation contracts.

No implementation task beyond EVAL-C-01 is `READY` yet because the initial contract boundary is intentionally serialized before parallel work fans out.

## Next fan-out

EVAL-1 is designed to unlock parallel development. After EVAL-C-01 through EVAL-C-09 are `DONE`, independent lanes can start for:

- dataset parser/store/sampling;
- deterministic evaluators;
- runner skeleton against fakes;
- persistence repositories/schema;
- Performance UI state/effect shell against fakes.

General Purpose source/license inventory begins after the canonical dataset schema is fixed and does not need the real runner.

## External dependencies

- Existing repository Q35-6 physical tuning does not block host-side model-evaluation implementation.
- Final representative runtime comparison evidence for EVAL-V-21/EVAL-V-22 requires Q35-6 measured profiles so candidate runtime settings are not mistaken for certified defaults.
- Built-in public-derived benchmark cases require explicit source/license/redistribution review before packaging.

## Current blockers

None for EVAL-1.

Potential later blockers are tracked as dependencies rather than hidden assumptions:

- upstream dataset redistribution/attribution constraints for General Purpose v1;
- representative physical-device availability for final performance evidence;
- Q35-6 measured-profile completion for production-facing runtime comparisons.

## Maintenance rule

When a task is merged:

1. mark the owning task `DONE` in its workstream ledger;
2. move all newly unblocked tasks from `PLANNED` to `READY`;
3. update this file with the currently active/ready tasks;
4. update [`roadmap.md`](roadmap.md) only if dependencies or milestone scope changed;
5. update repository-level state only when the feature changes repository sequencing or completion claims.

Do not mark a task `DONE` from code presence alone; tests and required documentation must be part of the same completion evidence.
