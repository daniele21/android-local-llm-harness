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
| EVAL-1 Contracts and identity | DONE | `evaluation/contracts` freezes v1 identity, scoring, run, compatibility, hashing and failure contracts with deterministic tests. |
| EVAL-2 Dataset system | READY | `EVAL-D-01` can define manifest/JSONL schemas from the frozen contracts. |
| EVAL-3 Deterministic evaluators | IN PROGRESS | Registry and six deterministic scorer implementations are integrated; v1 golden/adversarial coverage is complete and suite aggregation remains active. |
| EVAL-4 Evaluation runner | READY | `EVAL-R-01` and `EVAL-R-02` can progress independently against contracts/fakes. |
| EVAL-5 Persistence and comparison | READY | `EVAL-P-01` can freeze repository/query/retention behavior. |
| EVAL-6 General Purpose v1 | PLANNED | Source/license and pack assembly depend on dataset/evaluator foundations. |
| EVAL-7 Performance UI/custom import | READY | `EVAL-U-01` can define the Performance UDF/navigation contract against fakes. |
| EVAL-8 Validation/device evidence | PLANNED | `EVAL-V-01` is ready incrementally; final Android/device evidence remains late-gated. |

## Integrated evaluator boundary

`evaluation/evaluators` contains the versioned fail-closed registry and deterministic v1 scorer set:

- normalized exact match with explicit case/whitespace policy;
- multiple choice with bounded allowed-label extraction and ambiguity rejection;
- locale-independent numeric final answer with declared bounded tolerance;
- JSON-fields scoring with a strict bounded parser and transparent partial field score;
- repository-defined regex/format checks only, without arbitrary user regex execution;
- declarative instruction constraints with transparent satisfied/declared partial scoring and bounded format delegation.

Every scorer exposes a versioned `EvaluatorRegistration`. `EvaluatorRegistry` remains composition-based rather than hardcoding a global scorer list; concrete evaluator composition belongs to the runner/factory integration. No evaluator uses an LLM judge or arbitrary executable code.

EVAL-E-09 adds deterministic golden fixtures for every scorer v1 plus registry composition, and adversarial fixtures for ambiguity, locale-dependent numeric text, duplicate JSON keys, out-of-pattern text, duplicate instruction constraints and unknown evaluator versions.

## Ready now

These tasks are mutually independent unless the same developer/review capacity is shared:

- `EVAL-D-01` — versioned manifest and canonical JSONL case schema;
- `EVAL-E-08` — category and weighted suite quality aggregation, including zero-score runtime-failure semantics;
- `EVAL-R-01` — evaluation lifecycle engine against fakes;
- `EVAL-R-02` — controlled selected-model evaluation binding/profile resolution;
- `EVAL-P-01` — evaluation repository/query/retention contract;
- `EVAL-U-01` — Performance navigation/UDF state/effect contract;
- `EVAL-V-01` — identity/hash golden fixtures and cross-run deterministic serialization tests.

`EVAL-E-10` remains gated on EVAL-E-08 and will freeze/document evaluator v1 semantics for dataset-pack compatibility after aggregation is integrated.

## Parallel fan-out

Dataset, evaluator, runner, persistence, UI-shell and deterministic-validation lanes may proceed concurrently.

General Purpose source/license inventory unlocks after `EVAL-D-01`; production runner scoring remains gated on dataset/evaluator implementations; connected history/compare remains gated on persistence; final runtime comparison evidence remains gated on Q35-6 measured profiles.

## External dependencies

- Existing repository Q35-6 physical tuning does not block host-side model-evaluation implementation.
- Final representative runtime comparison evidence for EVAL-V-21/EVAL-V-22 requires Q35-6 measured profiles so candidate runtime settings are not mistaken for certified defaults.
- Built-in public-derived benchmark cases require explicit source/license/redistribution review before packaging.

## Current blockers

None for the ready host-side tasks listed above.

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
