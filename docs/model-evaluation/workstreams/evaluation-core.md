# Model evaluation core workstream

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.core
Read when: implementing model-evaluation contracts, deterministic evaluators, runtime orchestration, persistence or comparison logic
Last reviewed: 2026-08-14

## Goal

Provide the backend-independent contracts and deterministic engine required to execute evaluation cases through the existing runtime, score outputs, persist privacy-safe outcomes and compare compatible runs.

This workstream does not own dataset-pack installation or Performance UI composition.

## Contract rules

- Evaluation contracts remain independent from Compose, Room and `llama.cpp` implementation types.
- Run identity uses immutable content/configuration fingerprints rather than display names.
- Every evaluator type and evaluator behavior is versioned.
- Unknown schema/evaluator versions fail during preflight, before model inference.
- Generated output is passed to an evaluator in memory but is not persisted by default.
- A case outcome distinguishes semantic incorrectness from runtime/format failure.
- Comparison compatibility is computed in domain code, never inferred from labels in the UI.

## EVAL-1 contract freeze

`evaluation/contracts` is the only module introduced by EVAL-1. Engine, dataset-store and persistence modules remain deferred until their workstreams contain concrete behavior.

The frozen v1 contract boundary provides:

- value-semantic dataset, case, category, evaluator, execution-profile, sampling and run identifiers;
- lowercase SHA-256 wrappers for dataset, sample-set, evaluator-set, case-semantics, semantic-execution and run fingerprints;
- declarative evaluator type/version/parameter contracts and normalized `[0,1]` outcomes;
- ordered sampling selection with digest verification and explicit policy/version/seed;
- run config, lifecycle, progress, privacy-safe case metrics/results, quality/reliability summaries and typed failures;
- semantic execution identity over backend/template/context/preset/thinking/sampling/output semantics using exact scalar representation;
- full run identity over exact model, dataset, sampling, evaluator, semantic execution and runtime environment;
- separate typed quality/runtime incompatibility reasons without a universal score.

Canonical hashing uses explicit field order, length-prefixed text, raw float bits and sorted parameter maps. Prompt, expected-answer and generated-answer content are deliberately absent from persistent result contracts and run fingerprints.

## Core contract target

At minimum the domain needs stable equivalents of:

```text
EvaluationDatasetId / DatasetDigest
EvaluationCaseId
EvaluatorSpec / EvaluatorVersion
EvaluationOutcome
SamplingSelection / SampleSetDigest
EvaluationExecutionProfile
EvaluationRunConfig
EvaluationRunIdentity
EvaluationRunState
EvaluationCaseResult
EvaluationRunSummary
EvaluationProgress
EvaluationCompatibility
```

Concrete names may change only through an intentional contract change with affected consumers/tests updated; semantic responsibilities remain explicit.

## Runner behavior

The engine must preflight the complete selected sample set before starting inference. A run cannot discover halfway through that one case references an unknown evaluator.

Per case:

```text
create isolated session/context
 -> generate through normal runtime path
 -> collect request ID and terminal generation outcome
 -> read/correlate privacy-safe telemetry
 -> evaluate completed output
 -> persist case outcome
 -> close case session/context
 -> emit progress
```

Model residency may remain warm across cases. Case conversational state may not.

## Persistence behavior

Persistent evaluation results use a separate repository from ordinary telemetry. Default persisted result content is limited to identities, normalized scores, typed evaluator details, request correlation, privacy-safe metrics, typed errors/stops and timestamps.

Run/result retention is independently bounded. Removing evaluation history must not remove generation telemetry or dataset packs.

## Task ledger — contracts and identity

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-C-01 | DONE | EVAL-0 | Establish concrete package/module ownership for contracts without speculative empty modules. |
| EVAL-C-02 | DONE | EVAL-C-01 | Define dataset, case, evaluator and execution-profile identifiers with value semantics. |
| EVAL-C-03 | DONE | EVAL-C-02 | Define evaluator spec, typed evaluator outcome and normalized `[0,1]` scoring contract. |
| EVAL-C-04 | DONE | EVAL-C-02 | Define sampling selection and ordered `SampleSetDigest` contract. |
| EVAL-C-05 | DONE | EVAL-C-02,EVAL-C-03,EVAL-C-04 | Define `EvaluationRunConfig`, lifecycle state, progress and summary contracts. |
| EVAL-C-06 | DONE | EVAL-C-05 | Define semantic execution identity and exact immutable fields included in its fingerprint. |
| EVAL-C-07 | DONE | EVAL-C-06 | Define quality-compatibility and runtime-compatibility result contracts with typed mismatch reasons. |
| EVAL-C-08 | DONE | EVAL-C-02,EVAL-C-06 | Implement canonical serialization/hash utilities with deterministic ordering and tests. |
| EVAL-C-09 | DONE | EVAL-C-03,EVAL-C-05 | Define bounded validation/error taxonomy for preflight, evaluation, cancellation and partial persistence failures. |

EVAL-1 is complete when this change passes the repository merge gates.

## Task ledger — deterministic evaluators

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-E-01 | DONE | EVAL-C-03,EVAL-C-09 | Implement versioned evaluator registry with fail-closed lookup and parameter validation. |
| EVAL-E-02 | DONE | EVAL-E-01 | Implement normalized exact-match evaluator with explicit whitespace/case normalization policy. |
| EVAL-E-03 | DONE | EVAL-E-01 | Implement multiple-choice evaluator that extracts only allowed answer labels and rejects ambiguity. |
| EVAL-E-04 | DONE | EVAL-E-01 | Implement numeric-final-answer evaluator with locale-independent parsing and bounded tolerance policy where declared. |
| EVAL-E-05 | DONE | EVAL-E-01 | Implement JSON parse/schema/field evaluator with deterministic partial field scoring. |
| EVAL-E-06 | DONE | EVAL-E-01 | Implement regex/format constraint evaluator using bounded repository-defined patterns only. |
| EVAL-E-07 | DONE | EVAL-E-01,EVAL-E-06 | Implement instruction-constraint aggregation for declarative verifiable constraints. |
| EVAL-E-08 | READY | EVAL-E-02,EVAL-E-03,EVAL-E-04,EVAL-E-05,EVAL-E-07 | Implement category and weighted suite score aggregation including zero-score failure semantics. |
| EVAL-E-09 | DONE | EVAL-E-02,EVAL-E-03,EVAL-E-04,EVAL-E-05,EVAL-E-06,EVAL-E-07 | Add adversarial/malformed-output fixtures and deterministic golden tests for every evaluator version. |
| EVAL-E-10 | PLANNED | EVAL-E-08,EVAL-E-09 | Document evaluator semantics and freeze v1 behavior for dataset-pack compatibility. |

EVAL-3 closes when EVAL-E-01 through EVAL-E-10 are `DONE`.

## Task ledger — evaluation runner

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-R-01 | READY | EVAL-C-05,EVAL-C-09 | Implement `EvaluationEngine` lifecycle/state machine against fake case source/evaluator/runtime interfaces. |
| EVAL-R-02 | READY | EVAL-C-06 | Define controlled evaluation binding/profile resolution for one selected installed supported artifact without mutating ordinary app bindings. |
| EVAL-R-03 | PLANNED | EVAL-R-02 | Implement full-run preflight for model installation/support, dataset compatibility, evaluator support and execution-profile validity. |
| EVAL-R-04 | PLANNED | EVAL-R-01,EVAL-R-03 | Integrate model preparation and optional unscored warm-up with explicit run identity. |
| EVAL-R-05 | PLANNED | EVAL-R-01,EVAL-R-03 | Implement isolated session/context lifecycle per scored case while preserving allowed warm model residency. |
| EVAL-R-06 | PLANNED | EVAL-R-05,EVAL-E-01 | Dispatch completed output to declared evaluator and create typed case result. |
| EVAL-R-07 | PLANNED | EVAL-R-05 | Correlate each case with normal generation request ID and privacy-safe telemetry/resource metrics. |
| EVAL-R-08 | PLANNED | EVAL-R-05 | Implement per-case timeout policy and typed timeout cleanup without leaving active decode/context ownership. |
| EVAL-R-09 | PLANNED | EVAL-R-01,EVAL-R-05 | Implement cooperative run cancellation including active case cancellation and unattempted-case accounting. |
| EVAL-R-10 | PLANNED | EVAL-R-06,EVAL-R-07 | Implement incremental progress and aggregate quality/runtime/resource/reliability summary calculation. |
| EVAL-R-11 | PLANNED | EVAL-R-04,EVAL-R-05,EVAL-R-08,EVAL-R-09 | Validate cleanup for completion, evaluator failure, runtime failure, timeout and cancellation. |
| EVAL-R-12 | PLANNED | EVAL-R-10,EVAL-R-11 | Add deterministic runner integration tests using fake runtime, fake telemetry and fixed case order. |

EVAL-4 may begin at EVAL-R-01/R-02. It closes only after production dataset access from EVAL-2 and evaluator implementations from EVAL-3 are integrated into EVAL-R-06/R-12.

## Task ledger — persistence and comparison

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-P-01 | READY | EVAL-C-05,EVAL-C-07 | Define evaluation repository queries, retention and deletion contract. |
| EVAL-P-02 | PLANNED | EVAL-P-01 | Implement bounded in-memory repository with deterministic ordering and test parity baseline. |
| EVAL-P-03 | PLANNED | EVAL-P-01 | Design Room entities for run identity, aggregate summary and per-case privacy-safe outcome. |
| EVAL-P-04 | PLANNED | EVAL-P-03 | Implement Room DAO/repository and database migration wiring without coupling telemetry schema ownership. |
| EVAL-P-05 | PLANNED | EVAL-P-02,EVAL-P-04 | Add in-memory/Room parity tests for create, progress, terminal state, history, retention and deletion. |
| EVAL-P-06 | PLANNED | EVAL-R-01,EVAL-P-01 | Persist run lifecycle atomically enough to distinguish active, partial, cancelled, failed and completed runs after process restart. |
| EVAL-P-07 | PLANNED | EVAL-R-06,EVAL-P-06 | Persist each completed case outcome without prompt/expected/generated text. |
| EVAL-P-08 | PLANNED | EVAL-C-07,EVAL-P-01 | Implement comparison service with typed quality/runtime compatibility checks. |
| EVAL-P-09 | PLANNED | EVAL-P-08 | Implement valid category/aggregate deltas and runtime/resource deltas only when their compatibility level passes. |
| EVAL-P-10 | PLANNED | EVAL-P-05,EVAL-P-07,EVAL-P-09 | Add restart, retention, privacy and incompatible-comparison integration tests. |

EVAL-5 closes when EVAL-P-01 through EVAL-P-10 are `DONE` and the real runner persists end-to-end results.

## Parallel execution guidance

EVAL-1 unlocks independent dataset, evaluator, runner, persistence, UI-shell and deterministic-validation lanes.

Within the evaluator lane, EVAL-E-01 through EVAL-E-07 and EVAL-E-09 are integrated after this change. EVAL-E-08 aggregation remains ready; EVAL-E-10 follows only after EVAL-E-08 is complete. In persistence, EVAL-P-02 and EVAL-P-03/P-04 can run in parallel after EVAL-P-01.

## Completion gates

This workstream is complete only when:

- all v1 evaluator behavior is deterministic and versioned;
- every runner terminal path closes evaluation-owned runtime resources;
- no evaluation code bypasses supported model resolution or backend contracts;
- telemetry correlation does not leak evaluation content;
- persisted results remain useful after restart without storing prompt/output text;
- comparison refuses incompatible deltas rather than approximating compatibility;
- in-memory and Room behavior is equivalent for supported repository operations.
