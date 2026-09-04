# Model evaluation core workstream

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.core
Read when: implementing model-evaluation contracts, deterministic evaluators, runtime orchestration, persistence or comparison logic
Last reviewed: 2026-08-15

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

`EvaluationResultRepository`, `EvaluationRunQuery`, `EvaluationRetentionPolicy`, `EvaluationRunDeleteStatus` and `EvaluationRetentionResult` freeze the P-01 repository boundary in `evaluation/contracts`, with bounded query/retention tests. `evaluation/in-memory-store` provides the deterministic P-02 parity baseline; Room must match those repository semantics rather than inventing a second contract.

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

EVAL-1 is complete.

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
| EVAL-E-08 | DONE | EVAL-E-02,EVAL-E-03,EVAL-E-04,EVAL-E-05,EVAL-E-07 | Implement category and weighted suite score aggregation including zero-score failure semantics. |
| EVAL-E-09 | DONE | EVAL-E-02,EVAL-E-03,EVAL-E-04,EVAL-E-05,EVAL-E-06,EVAL-E-07 | Add adversarial/malformed-output fixtures and deterministic golden tests for every evaluator version. |
| EVAL-E-10 | DONE | EVAL-E-08,EVAL-E-09 | Document evaluator semantics and freeze v1 behavior for dataset-pack compatibility. |

[`../evaluator-semantics-v1.md`](../evaluator-semantics-v1.md) is the dataset-visible v1 compatibility freeze. Any behavior change that can change an existing case score requires a new evaluator version.

EVAL-3 is complete.

### Quality aggregation v1

EVAL-E-08 defines quality aggregation only; runtime, resources and reliability remain separate result families.

- `SCORED` contributes its exact normalized evaluator score, including partial values.
- `INVALID_OUTPUT`, `TIMEOUT` and `RUNTIME_FAILURE` contribute quality `0`.
- `CANCELLED` is excluded from quality denominators.
- Category quality is the arithmetic mean of attempted quality contributions.
- Categories with no quality-attempted cases are omitted from the suite aggregate.
- If all scored categories declare weights, the suite score is their weighted mean renormalized over the categories actually scored.
- If none declare weights, the suite score is the arithmetic mean of category scores.
- Mixed weight declarations among scored categories fail closed.

## Task ledger — evaluation runner

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-R-01 | DONE | EVAL-C-05,EVAL-C-09 | Implement `EvaluationEngine` lifecycle/state machine against fake case source/evaluator/runtime interfaces. |
| EVAL-R-02 | DONE | EVAL-C-06 | Define controlled evaluation binding/profile resolution for one selected installed supported artifact without mutating ordinary app bindings. |
| EVAL-R-03 | DONE | EVAL-R-02 | Implement full-run preflight for model installation/support, dataset compatibility, evaluator support and execution-profile validity. |
| EVAL-R-04 | DONE | EVAL-R-01,EVAL-R-03 | Integrate model preparation and optional unscored warm-up with explicit run identity. |
| EVAL-R-05 | DONE | EVAL-R-01,EVAL-R-03 | Implement isolated session/context lifecycle per scored case while preserving allowed warm model residency. |
| EVAL-R-06 | DONE | EVAL-R-05,EVAL-E-01 | Dispatch completed output to declared evaluator and create typed case result. |
| EVAL-R-07 | DONE | EVAL-R-05 | Correlate each case with normal generation request ID and privacy-safe telemetry/resource metrics. |
| EVAL-R-08 | DONE | EVAL-R-05 | Implement per-case timeout policy and typed timeout cleanup without leaving active decode/context ownership. |
| EVAL-R-09 | DONE | EVAL-R-01,EVAL-R-05 | Implement cooperative run cancellation including active case cancellation and unattempted-case accounting. |
| EVAL-R-10 | IN PROGRESS | EVAL-R-06,EVAL-R-07 | Implement incremental progress and aggregate quality/runtime/resource/reliability summary calculation. |
| EVAL-R-11 | READY | EVAL-R-04,EVAL-R-05,EVAL-R-08,EVAL-R-09 | Validate cleanup for completion, evaluator failure, runtime failure, timeout and cancellation. |
| EVAL-R-12 | PLANNED | EVAL-R-10,EVAL-R-11 | Add deterministic runner integration tests using fake runtime, fake telemetry and fixed case order. |

R-01 establishes single-run ownership, ordered case execution, warm-up state, progress, typed failure and between-phase/case cooperative cancellation. R-02 resolves only product-supplied supported model profiles to the exact installed verified artifact; it never mutates ordinary application bindings. R-03 adds deterministic production preflight with controlled model resolution first and fail-fast dataset, evaluator and execution-profile compatibility checks. R-04/R-05 add exact runtime binding, explicit load-policy preparation, one optional unscored generation through the normal client path and a fresh stateless session/context closed around each scored case while preserving model residency.

R-06 executes scored cases through the normal `LocalLlmClient.generate` path and dispatches terminal output to the frozen deterministic evaluator registry. R-07 enriches completed cases through the exact generation `requestId`; unavailable telemetry stays unavailable rather than becoming synthetic zero. R-08 enforces immutable per-case timeout around active generation, cancels the active handle when the timeout fires, emits typed `TIMEOUT` results and preserves ordinary coroutine cancellation rather than misclassifying it.

R-09 extends cooperative cancellation through the active scored case: the engine owns one child job for the active case, `cancel(runId)` cancels that job, coroutine cancellation reaches the R-08 generation-handle boundary, and the final progress snapshot records an interrupted active case as attempted but not completed while later sample members remain unattempted. External parent cancellation is still rethrown unless the engine's own cancellation flag was set.

R-10 now has an integrated deterministic aggregation domain in `EvaluationRunAggregator`: frozen quality aggregation plus metric-specific median/p95/sample counts, memory distributions, thermal counts and reliability accounting. It remains `IN PROGRESS` until the production composition supplies canonical dataset category definitions and wires the aggregation result into the `AGGREGATING` terminal path.

EVAL-4 is in progress. R-11 is now ready from the completed timeout/cancellation ownership boundary. R-10 production wiring and R-11 can proceed in parallel; R-12 follows when both close.

## Task ledger — persistence and comparison

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-P-01 | DONE | EVAL-C-05,EVAL-C-07 | Define evaluation repository queries, retention and deletion contract. |
| EVAL-P-02 | DONE | EVAL-P-01 | Implement bounded in-memory repository with deterministic ordering and test parity baseline. |
| EVAL-P-03 | DONE | EVAL-P-01 | Design Room entities for run identity, aggregate summary and per-case privacy-safe outcome. |
| EVAL-P-04 | IN PROGRESS | EVAL-P-03 | Implement Room DAO/repository and database migration wiring without coupling telemetry schema ownership. |
| EVAL-P-05 | PLANNED | EVAL-P-02,EVAL-P-04 | Add in-memory/Room parity tests for create, progress, terminal state, history, retention and deletion. |
| EVAL-P-06 | DONE | EVAL-R-01,EVAL-P-01 | Persist run lifecycle atomically enough to distinguish active, partial, cancelled, failed and completed runs after process restart. |
| EVAL-P-07 | DONE | EVAL-R-06,EVAL-P-06 | Persist each completed case outcome without prompt/expected/generated text. |
| EVAL-P-08 | DONE | EVAL-C-07,EVAL-P-01 | Implement comparison service with typed quality/runtime compatibility checks. |
| EVAL-P-09 | DONE | EVAL-P-08 | Implement valid category/aggregate deltas and runtime/resource deltas only when their compatibility level passes. |
| EVAL-P-10 | PLANNED | EVAL-P-05,EVAL-P-07,EVAL-P-09 | Add restart, retention, privacy and incompatible-comparison integration tests. |

P-02 is the behavioral reference implementation for persistence parity: immutable run configuration after create, valid lifecycle transitions, ordered case snapshots bounded to the selected sample set, deterministic filtered history, active-run delete protection and terminal-only retention. It persists no prompt, expected-answer or generated-answer text.

P-06 persists lifecycle and progress around `EvaluationEngine` through the repository contract. P-07 extends the same orchestration so every completed privacy-safe `EvaluationCaseResult` is written before it is forwarded to the external observer; a later case/run failure does not erase an already completed outcome.

P-03 freezes the normalized Room entity graph for run configuration/identity, ordered sampled cases, aggregate summaries and privacy-safe case outcomes. P-04 is the active DAO/repository/database implementation lane and is not complete until its exact head passes formatter/compiler/scoped/repository validation and is integrated. P-08/P-09 freeze compatibility and compatibility-gated deltas independently of persistence queries.

EVAL-5 is in progress. P-04 integration unlocks P-05 parity; P-10 then joins durable storage, per-case persistence and comparison into restart/retention/privacy evidence.

## Parallel execution guidance

The current fan-out is intentionally broad:

- EVAL-R-10 production aggregation wiring and EVAL-R-11 terminal-path cleanup evidence can proceed independently from the integrated R-01 through R-09 runner foundation;
- EVAL-P-04 Room repository wiring remains the persistence critical lane; P-05 becomes ready only after P-04 is integrated;
- P-07 case persistence and P-09 comparison deltas are already integrated and can feed later P-10 evidence;
- the generic dataset mechanism is stable through D-12 while the production dataset-to-runner adapter and General Purpose source/content work advance separately;
- the Performance UI shell can remain fake-driven until runner/persistence wiring lands.

The runner should not absorb persistence, dataset-installation or UI responsibilities merely to reduce module count. Those boundaries are deliberate test seams.

## Completion gates

This workstream is complete only when:

- all v1 evaluator behavior is deterministic and versioned;
- every runner terminal path closes evaluation-owned runtime resources;
- no evaluation code bypasses supported model resolution or backend contracts;
- telemetry correlation does not leak evaluation content;
- persisted results remain useful after restart without storing prompt/output text;
- comparison refuses incompatible deltas rather than approximating compatibility;
- in-memory and Room behavior is equivalent for supported repository operations.
