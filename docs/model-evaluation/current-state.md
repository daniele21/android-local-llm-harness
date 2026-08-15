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
| EVAL-2 Dataset system | IN PROGRESS | D-01 manifest/canonical JSONL schema is frozen; D-02/D-03/D-04 are in active parser/validator/digest convergence before D-05/D-07 replay. |
| EVAL-3 Deterministic evaluators | DONE | Registry, six deterministic scorer families, suite aggregation, golden/adversarial coverage and evaluator v1 compatibility semantics are frozen. |
| EVAL-4 Evaluation runner | IN PROGRESS | R-01 through R-05 are integrated: controlled preflight, explicit model preparation/unscored warm-up and isolated stateless session/context ownership now precede scorer, telemetry, timeout and active-cancellation wiring. |
| EVAL-5 Persistence and comparison | IN PROGRESS | P-01 contracts, P-02 bounded in-memory parity and P-06 lifecycle persistence are integrated; P-03 Room design and P-08 comparison remain active. |
| EVAL-6 General Purpose v1 | IN PROGRESS | GP-01 exact public-source inventory is complete; GP-02 license/attribution treatment and GP-05/GP-06 Harness-owned authoring are ready independently. |
| EVAL-7 Performance UI/custom import | IN PROGRESS | U-01 state/effect vocabulary and U-02 fake-driven reducer/ViewModel are integrated; navigation/state rendering and connected selectors remain. |
| EVAL-8 Validation/device evidence | IN PROGRESS | V-01 identity/hash golden evidence is integrated; V-03 evaluator corpus is ready while final Android/device evidence remains late-gated. |

## Frozen evaluator boundary

`evaluation/evaluators` contains the fail-closed registry, all six v1 deterministic scorer families and quality aggregation. [`evaluator-semantics-v1.md`](evaluator-semantics-v1.md) freezes their dataset-visible type/version/parameter/scoring behavior.

The quality boundary remains explicit:

- `SCORED` preserves exact evaluator score including partial values;
- invalid output, timeout and runtime failure contribute quality `0`;
- cancelled cases are excluded from quality denominators;
- category scores are arithmetic means;
- suite aggregation is either fully weighted with renormalization or fully unweighted;
- runtime, resources and reliability remain separate result families.

EVAL-3 is complete. No v1 evaluator uses an LLM judge, arbitrary executable code or user-provided regular expressions.

## Integrated implementation foundations

The integrated implementation foundation now includes five concrete seams without prematurely coupling production dataset/runtime behavior:

- `evaluation/engine` owns R-01 single-run lifecycle/progress/cancellation, R-02 controlled resolution of exactly one supported installed model artifact, R-03 deterministic production preflight, R-04 normal-path model preparation/unscored warm-up and R-05 fresh stateless session/context ownership per scored case;
- `evaluation/in-memory-store` owns the P-02 deterministic parity implementation of `EvaluationResultRepository`, including lifecycle validation, active-run deletion protection and bounded terminal retention;
- `evaluation/persistence` owns P-06 lifecycle/progress persistence around `EvaluationEngine` without absorbing Room or per-case persistence ownership;
- the phone Performance shell owns U-01 typed Run/Datasets/History/Compare state, intents and effects;
- U-02 adds the fake-driven pure reducer and `StateFlow` ViewModel used by later connected Performance surfaces.

R-01 through R-05 do not claim evaluator dispatch, telemetry correlation, per-case timeout, active-decode cancellation or production dataset-to-generation wiring. P-06 does not claim P-07 per-case outcome persistence. U-02 does not claim connected Compose behavior.

## Frozen dataset, persistence and source foundations

D-01 is satisfied by `DatasetSchemaContracts.kt`, its contract tests and [`dataset-schema-v1.md`](dataset-schema-v1.md), which fixes the manifest and JSONL wire representation.

P-01 is satisfied by `PersistenceContracts.kt` and `PersistenceContractsTest.kt`; P-02 provides the in-memory behavioral baseline and P-06 now persists lifecycle/progress through that repository boundary.

GP-01 is satisfied by [`general-purpose-source-inventory.md`](general-purpose-source-inventory.md), which pins immutable MMLU-Pro, IFEval, GSM8K and ARC Challenge source revisions plus stable provenance/source-record identity rules without authorizing redistribution.

V-01 is satisfied by `EvaluationIdentityGoldenTest`, which pins stable v1 digests/fingerprints and proves equivalent clean run construction remains deterministic.

## Ready or active now

The following work can proceed concurrently unless it touches the same module-registration files:

- `EVAL-D-02` / `EVAL-D-03` / `EVAL-D-04` — parser, full-pack validation and canonical digest convergence;
- `EVAL-GP-02` — public-source license, attribution and redistribution treatment review;
- `EVAL-GP-05` — 20 Harness structured-output cases;
- `EVAL-GP-06` — 20 Harness context-retrieval cases;
- `EVAL-R-06` — dispatch completed output to the declared deterministic evaluator;
- `EVAL-R-07` — correlate normal request identity with privacy-safe telemetry;
- `EVAL-R-08` — enforce typed per-case timeout and cleanup;
- `EVAL-R-09` — extend cooperative cancellation through active case execution;
- `EVAL-P-03` — Room entity design for privacy-safe evaluation persistence;
- `EVAL-P-08` — typed comparison service;
- `EVAL-U-03` — top-level Performance navigation and subnavigation;
- `EVAL-U-04` — deterministic loading/empty/unavailable/error states;
- `EVAL-U-11` — installed supported-model selector;
- `EVAL-U-13` — execution-profile selector and compatibility explanation;
- `EVAL-V-03` — reusable evaluator golden/edge corpus.

D-05 install and D-07 sampling already have implementation branches, but they remain child lanes until the corrected D-02/D-03/D-04 base is green and integrated.

## Parallel fan-out strategy

```text
Dataset:      D-02 ─┐
              D-03 ─┼─> D-05 install
              D-04 ─┘
                    └─> D-07 sampling -> D-08 presets

Runner:       R-01 DONE
              R-02 DONE
              R-03 DONE -> R-04/R-05 DONE -> R-06/R-07/R-08/R-09

Persistence:  P-02 DONE
              P-06 DONE
              P-03 ──────> P-04/P-05 parity
              P-08 ──────> P-09 comparison deltas

General pack: GP-01 DONE -> GP-02 -> GP-03 -> GP-04
              GP-05 ─┐
              GP-06 ─┴─> later GP-07 assembly

UI:           U-01 DONE -> U-02 DONE -> U-03/U-04/U-11/U-13
Validation:   V-03 independent evaluator evidence
```

Module-registration files (`settings.gradle.kts`, CI module lists and `evaluation/AGENTS.md`) remain shared integration hotspots. Parallel branches may implement behavior independently, but replay/merge order must keep those files synchronized rather than resolving them by dropping one lane.

## External dependencies

- Existing repository Q35-6 physical tuning does not block host-side model-evaluation implementation.
- Final representative runtime comparison evidence for EVAL-V-21/EVAL-V-22 requires Q35-6 measured profiles so candidate runtime settings are not mistaken for certified defaults.
- Built-in public-derived benchmark cases require explicit source/license/redistribution review before packaging.

## Current blockers

There is no external blocker for the active host-side lanes. The dataset child lanes are intentionally waiting on their corrected D-02/D-03/D-04 base gate rather than bypassing it.

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
