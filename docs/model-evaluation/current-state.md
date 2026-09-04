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
| EVAL-2 Dataset system | IN PROGRESS | D-01 through D-12 are integrated. The generic dataset layer is complete; milestone closure now depends only on production composition consuming installed packs through the canonical dataset boundary. |
| EVAL-3 Deterministic evaluators | DONE | Registry, six deterministic scorer families, suite aggregation, golden/adversarial coverage and evaluator v1 compatibility semantics are frozen. |
| EVAL-4 Evaluation runner | IN PROGRESS | R-01 through R-09 are integrated by this change. R-10 has an integrated deterministic aggregation domain but still needs terminal production wiring; R-11 cleanup evidence is now ready. |
| EVAL-5 Persistence and comparison | IN PROGRESS | P-01/P-02/P-03/P-06/P-07/P-08/P-09 are integrated. P-04 durable Room repository wiring is active and gates P-05 parity; P-10 remains the integration closeout. |
| EVAL-6 General Purpose v1 | IN PROGRESS | GP-01 exact public-source inventory is complete; GP-02 license/attribution treatment and GP-05/GP-06 Harness-owned authoring are ready independently. |
| EVAL-7 Performance UI/custom import | IN PROGRESS | U-01 state/effect vocabulary and U-02 fake-driven reducer/ViewModel are integrated; navigation/state rendering and connected selectors remain. |
| EVAL-8 Validation/device evidence | IN PROGRESS | V-01 identity/hash evidence and the reusable V-03 six-scorer golden/edge corpus are integrated; final Android/device evidence remains late-gated. |

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

The integrated implementation foundation now includes the following concrete seams without collapsing dataset, runtime, persistence and UI ownership:

- `evaluation/datasets` owns D-01 through D-12: bounded canonical parsing, validation, digest, atomic install, discovery, deterministic sampling/presets, regression fixtures, Android canonical-document import, protected deletion and the explicit developer-facing import contract in [`custom-dataset-import.md`](custom-dataset-import.md);
- `evaluation/engine` owns R-01 through R-09 after this change: lifecycle, controlled model resolution, full preflight, preparation/unscored warm-up, fresh stateless context per scored case, deterministic evaluator dispatch, exact request telemetry correlation, typed per-case timeout and active run cancellation through the active case job/generation handle;
- `EvaluationRunAggregator` in `evaluation/engine` is the integrated R-10 domain foundation for quality/runtime/resource/reliability aggregation, but the production terminal composition remains open;
- `evaluation/in-memory-store` owns the P-02 deterministic parity implementation of `EvaluationResultRepository`, including lifecycle validation, active-run deletion protection and bounded terminal retention;
- `evaluation/persistence` owns P-06 lifecycle/progress persistence plus P-07 durable per-case outcome persistence through the repository boundary, without storing prompt/expected/generated text;
- `evaluation/room-store` owns the P-03 normalized privacy-safe Room entity graph; P-04 DAO/repository/database implementation is the active durable-store lane and is not yet integrated;
- `evaluation/comparison` owns P-08 typed quality/runtime compatibility and integrated P-09 compatibility-gated quality/runtime/resource deltas;
- the phone Performance shell owns U-01 typed Run/Datasets/History/Compare state, intents and effects;
- U-02 adds the fake-driven pure reducer and `StateFlow` ViewModel used by later connected Performance surfaces.

R-09 records an interrupted active case as attempted but not completed and prevents later cases from starting; it does not fabricate a scored `CANCELLED` case outcome without dataset category/evaluator identity. R-10 remains responsible for aggregate accounting. P-07 persists completed case outcomes but durable Room behavior is not production-complete until P-04/P-05 close. U-02 does not claim connected Compose behavior.

## Frozen dataset, persistence and source foundations

D-01 is satisfied by `DatasetSchemaContracts.kt`, its contract tests and [`dataset-schema-v1.md`](dataset-schema-v1.md), which fixes the manifest and JSONL wire representation. D-10 extends the same canonical boundary to Android-selected documents, D-11 adds exact-identity protected deletion, and [`custom-dataset-import.md`](custom-dataset-import.md) records the v1 import limits/privacy/error contract for D-12. All generic dataset tasks are complete.

P-01 is satisfied by `PersistenceContracts.kt` and `PersistenceContractsTest.kt`; P-02 provides the in-memory behavioral baseline, P-03 freezes the Room persistence shape, P-06 persists lifecycle/progress, P-07 persists completed privacy-safe case outcomes, P-08 rejects incompatible comparisons with typed reasons and P-09 computes deltas only when the corresponding compatibility level passes.

GP-01 is satisfied by [`general-purpose-source-inventory.md`](general-purpose-source-inventory.md), which pins immutable MMLU-Pro, IFEval, GSM8K and ARC Challenge source revisions plus stable provenance/source-record identity rules without authorizing redistribution.

V-01 is satisfied by `EvaluationIdentityGoldenTest`, which pins stable v1 digests/fingerprints and proves equivalent clean run construction remains deterministic. V-03 adds a reusable 24-case corpus covering golden, ambiguous, malformed and edge output shapes across all six frozen v1 scorers, with deterministic outcome, score and registry assertions.

## Ready or active now

The following work can proceed concurrently unless it touches the same module-registration files:

- `EVAL-P-04` — complete and validate Room DAO/repository/database migration wiring;
- `EVAL-R-10` — wire the integrated aggregator into production `AGGREGATING` with canonical dataset category definitions;
- `EVAL-R-11` — validate cleanup for completion, evaluator failure, runtime failure, timeout and cancellation;
- production dataset-to-runner composition — consume only registry-published packs through the canonical parser/identity boundary, closing the remaining EVAL-2 integration gap;
- `EVAL-GP-02` — public-source license, attribution and redistribution treatment review;
- `EVAL-GP-05` — author 20 Harness structured-output cases;
- `EVAL-GP-06` — author 20 Harness context-retrieval cases;
- `EVAL-U-03` — top-level Performance navigation and subnavigation;
- `EVAL-U-04` — deterministic loading/empty/unavailable/error states;
- `EVAL-U-11` — installed supported-model selector;
- `EVAL-U-13` — execution-profile selector and compatibility explanation.

After P-04 is integrated, `EVAL-P-05` Room/in-memory parity becomes ready. R-10 production wiring and R-11 can now proceed in parallel; R-12 becomes ready when both close. The generic dataset implementation no longer has an independent code lane after D-12; remaining dataset work is production runner composition plus General Purpose v1 content/licensing.

## Parallel fan-out strategy

```text
Dataset:      D-01..D-12 DONE
              production adapter -> runner/composition

Runner:       R-01..R-09 DONE
              R-10 aggregation wiring ─┐
              R-11 cleanup evidence   ─┴─> R-12 integration

Persistence:  P-02 DONE
              P-06/P-07 DONE
              P-03 DONE -> P-04 active -> P-05 parity -> P-10
              P-08/P-09 DONE ────────────────────────────┘

General pack: GP-01 DONE -> GP-02 -> GP-03 -> GP-04
              GP-05 ─┐
              GP-06 ─┴─> later GP-07 assembly

UI:           U-01 DONE -> U-02 DONE -> U-03/U-04/U-11/U-13
Validation:   V-03 DONE; remaining deterministic gates are dependency-bound
```

Module-registration files (`settings.gradle.kts`, CI module lists and `evaluation/AGENTS.md`) remain shared integration hotspots. Parallel branches may implement behavior independently, but replay/merge order must keep those files synchronized rather than resolving them by dropping one lane.

## External dependencies

- Existing repository Q35-6 physical tuning does not block host-side model-evaluation implementation.
- Final representative runtime comparison evidence for EVAL-V-21/EVAL-V-22 requires Q35-6 measured profiles so candidate runtime settings are not mistaken for certified defaults.
- Built-in public-derived benchmark cases require explicit source/license/redistribution review before packaging.

## Current blockers

There is no external blocker for the active host-side runner, persistence, Harness-owned dataset or UI lanes.

Potential later blockers are tracked as dependencies rather than hidden assumptions:

- upstream dataset redistribution/attribution constraints for General Purpose v1;
- representative physical-device availability for final performance evidence;
- Q35-6 measured-profile completion for production-facing runtime comparisons.

P-04 is still gated on exact-head durable-store validation. Its prior formatter and static-analysis issues were implementation-quality blockers rather than architectural blockers; no P-04 completion claim is made until the final Room repository head is green and integrated.

## Maintenance rule

When a task is merged:

1. mark the owning task `DONE` in its workstream ledger;
2. move all newly unblocked tasks from `PLANNED` to `READY`;
3. update this file with the currently active/ready tasks;
4. update [`roadmap.md`](roadmap.md) only if dependencies or milestone scope changed;
5. update repository-level state only when the feature changes repository sequencing or completion claims.

Do not mark a task `DONE` from code presence alone; tests and required documentation must be part of the same completion evidence.
