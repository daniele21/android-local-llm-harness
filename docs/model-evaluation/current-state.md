# Model evaluation current state

Status: active
Document type: workstream-state
Owner: model-evaluation
Canonical scope: model-evaluation.state
Read when: determining current model-evaluation progress, blockers or the next ready tasks
Last reviewed: 2026-08-15

This is the operational status ledger for model evaluation. Repository-level integrated state and immediate sequencing remain in [`../current-state.md`](../current-state.md). Capability sequencing inside this feature is owned by [`roadmap.md`](roadmap.md); detailed acceptance criteria remain in the owning workstream specifications.

## Milestone state

| Milestone | State | Current outcome |
| --- | --- | --- |
| EVAL-0 Plan and architecture | DONE | Scope, ownership, dependency graph and maintenance rules are documented. |
| EVAL-1 Contracts and identity | DONE | `evaluation/contracts` freezes v1 identity, scoring, run, dataset-schema, persistence, compatibility, hashing and failure contracts with deterministic tests. |
| EVAL-2 Dataset system | IN PROGRESS | D-01 manifest/canonical JSONL schema is frozen; parser, validator and digest lanes are ready in parallel. |
| EVAL-3 Deterministic evaluators | DONE | Registry, six deterministic scorer families, suite aggregation, golden/adversarial coverage and evaluator v1 compatibility semantics are frozen. |
| EVAL-4 Evaluation runner | READY | R-01 lifecycle engine and R-02 controlled model binding can progress independently. |
| EVAL-5 Persistence and comparison | READY | P-01 repository/query/retention contract is already frozen; P-02, P-03 and P-08 are ready independently. |
| EVAL-6 General Purpose v1 | READY | GP-01 public-source inventory plus GP-05/GP-06 Harness-owned case authoring can start independently. |
| EVAL-7 Performance UI/custom import | READY | U-01 can define the Performance UDF/navigation contract against fakes. |
| EVAL-8 Validation/device evidence | IN PROGRESS | V-01 identity/hash golden evidence is integrated; V-03 evaluator corpus is ready while final Android/device evidence remains late-gated. |

## Frozen evaluator boundary

`evaluation/evaluators` now contains the fail-closed registry, all six v1 deterministic scorer families and quality aggregation. [`evaluator-semantics-v1.md`](evaluator-semantics-v1.md) freezes their dataset-visible type/version/parameter/scoring behavior.

The quality boundary remains explicit:

- `SCORED` preserves exact evaluator score including partial values;
- invalid output, timeout and runtime failure contribute quality `0`;
- cancelled cases are excluded from quality denominators;
- category scores are arithmetic means;
- suite aggregation is either fully weighted with renormalization or fully unweighted;
- runtime, resources and reliability remain separate result families.

EVAL-3 is complete. No v1 evaluator uses an LLM judge, arbitrary executable code or user-provided regular expressions.

## Frozen dataset and persistence foundations

D-01 is satisfied by `DatasetSchemaContracts.kt`, its contract tests and [`dataset-schema-v1.md`](dataset-schema-v1.md), which fixes the manifest and JSONL wire representation.

P-01 is satisfied by `PersistenceContracts.kt` and `PersistenceContractsTest.kt`: bounded history queries, retention policy/result, active-run-aware delete status and `EvaluationResultRepository` are already part of the frozen contracts.

V-01 is satisfied by `EvaluationIdentityGoldenTest`, which pins stable v1 digests/fingerprints and proves equivalent clean run construction remains deterministic.

## Ready now

The following work can proceed concurrently unless it touches the same module-registration files:

- `EVAL-D-02` — bounded streaming JSONL parser;
- `EVAL-D-03` — full-pack validator;
- `EVAL-D-04` — canonical ordered content digest and manifest verification;
- `EVAL-GP-01` — exact public benchmark source/version/provenance inventory;
- `EVAL-GP-05` — 20 Harness structured-output cases;
- `EVAL-GP-06` — 20 Harness context-retrieval cases;
- `EVAL-R-01` — evaluation lifecycle engine against fakes;
- `EVAL-R-02` — controlled supported-model evaluation binding/profile resolution;
- `EVAL-P-02` — bounded in-memory result repository;
- `EVAL-P-03` — Room entity design for privacy-safe evaluation persistence;
- `EVAL-P-08` — typed comparison service;
- `EVAL-U-01` — Performance navigation/UDF state/effect contract;
- `EVAL-V-03` — reusable evaluator golden/edge corpus.

## Parallel fan-out strategy

Wave 2 is deliberately split into independent convergence points:

```text
Dataset:      D-02 ─┐
              D-03 ─┼─> D-05 install
              D-04 ─┘

Runner:       R-01 ─┐
              R-02 ─┴─> R-03/R-04/R-05

Persistence:  P-02 ─┐
              P-03 ─┼─> parity / Room wiring
              P-08  └─> comparison deltas

General pack: GP-01 -> GP-02/GP-03
              GP-05 ─┐
              GP-06 ─┴─> later GP-07 assembly

UI:           U-01 -> fake-driven run/history/compare states
Validation:   V-03 independent evaluator evidence
```

Module-registration files (`settings.gradle.kts`, CI module lists and `evaluation/AGENTS.md`) are shared integration hotspots. Parallel branches may implement behavior independently, but replay/merge order must keep those files synchronized rather than resolving them by dropping one lane.

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
