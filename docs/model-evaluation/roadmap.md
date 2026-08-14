# Model evaluation roadmap and dependency graph

Status: active
Document type: roadmap
Owner: model-evaluation
Canonical scope: model-evaluation.roadmap
Read when: selecting the next model-evaluation task, checking dependencies or deciding which work can run in parallel
Last reviewed: 2026-08-14

This roadmap owns implementation order and dependency relationships for model evaluation. Detailed acceptance criteria remain in the owning workstream documents.

## Dependency principles

- Contracts and identity precede concrete storage, evaluators, runner and UI integration.
- Dataset ingestion and evaluator implementation can progress in parallel once their shared contracts are stable.
- UI shell/state modeling can start from contracts before the real runner is available, using deterministic fakes.
- General Purpose v1 source/licensing work can start independently of runtime execution after the canonical dataset format is fixed.
- Persistence can be implemented in parallel with the runner after run/result contracts are stable.
- Custom import depends on dataset validation/storage, not on built-in dataset completion.
- Physical-device evidence depends on an integrated runner, real persistence and connected UI/runner configuration; it does not block host-side feature development.
- Existing Q35-6 physical tuning and this feature can run in parallel except where the same physical devices or human review capacity are shared.

## Milestone dependency graph

```text
EVAL-0 Plan and architecture
        |
        v
EVAL-1 Contracts and identity
   |          |           |            |
   v          v           v            v
EVAL-2      EVAL-3      EVAL-4*      EVAL-5*
Datasets    Evaluators   Runner       Persistence
   |          |           |            |
   |          +------->---+------------+
   |                      |
   +-------> EVAL-6       |
   |       General v1     |
   |                      v
   +-------------------> EVAL-7
                          UI/import
                            |
                            v
                         EVAL-8
                      Device evidence
```

`EVAL-4*` may begin with fake datasets/evaluators after EVAL-1, but cannot close until EVAL-2 and EVAL-3 provide production implementations. `EVAL-5*` may implement schema/repositories after EVAL-1 and closes when the runner writes complete real results.

## Parallel work lanes

### Lane A — contracts and engine

```text
EVAL-1 -> runner skeleton -> runtime orchestration -> telemetry correlation
```

Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane B — datasets

```text
EVAL-1 -> pack validation -> deterministic sampling -> storage/import
                                      |
                                      +-> General Purpose v1
```

Owned by [`workstreams/datasets.md`](workstreams/datasets.md).

### Lane C — evaluators

```text
EVAL-1 -> evaluator registry -> exact/numeric/MCQ/JSON/constraint evaluators
```

Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane D — persistence and comparison

```text
EVAL-1 -> repository contract -> in-memory store -> Room schema -> comparison service
```

Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane E — UI

```text
EVAL-1 -> fake state/effects -> run setup UI
                      |
EVAL-4 + EVAL-5 ------+-> connected execution/results/compare
EVAL-2 -----------------> custom import
```

Owned by [`workstreams/performance-ui.md`](workstreams/performance-ui.md).

### Lane F — validation

```text
contracts -> deterministic host matrix
runner + stores -> Android integration
full feature -> representative physical-device evidence
```

Owned by [`workstreams/validation.md`](workstreams/validation.md).

## Milestone plan

### EVAL-0 — Plan and architecture

State: `DONE`

Exit gate:

- capability is separate from runtime regression benchmarking;
- canonical documents and ownership exist;
- initial dependency graph and maintenance rules are defined.

### EVAL-1 — Contracts and identity

State: `READY`
Depends on: `EVAL-0`

Exit gate:

- canonical dataset/case/evaluator/run/result contracts exist;
- execution, dataset and sample-set identity is hashable and versioned;
- quality-compatible and runtime-compatible comparison rules are explicit;
- invalid or incomplete configurations fail before inference.

Unlocks in parallel: `EVAL-2`, `EVAL-3`, runner skeleton, persistence skeleton, UI fake state.

### EVAL-2 — Dataset system

State: `PLANNED`
Depends on: `EVAL-1`

Exit gate:

- manifests and JSONL cases validate deterministically;
- app-private dataset installation is atomic and digest-verified;
- nested stratified sampling is deterministic;
- custom canonical JSONL can be imported without executing arbitrary code;
- dataset content never enters normal telemetry.

Unlocks: production runner inputs, custom import UI and General Purpose v1 assembly.

### EVAL-3 — Deterministic evaluators

State: `PLANNED`
Depends on: `EVAL-1`

Exit gate:

- required evaluator types are versioned and registry-backed;
- fixtures cover exact, multiple-choice, numeric, JSON-field and instruction-constraint behavior;
- malformed model output produces typed outcomes rather than parser crashes;
- no LLM-as-judge or arbitrary executable evaluator exists in v1.

Unlocks: production runner scoring and General Purpose v1 validation.

### EVAL-4 — Evaluation runner

State: `PLANNED`
Depends on: `EVAL-1`; closes after `EVAL-2` and `EVAL-3`

Exit gate:

- selected supported models execute through the normal harness runtime;
- each scored case uses an isolated session/context while model residency may remain warm;
- request IDs correlate quality outcomes with telemetry;
- cancellation, timeout and partial failure leave the runtime reusable;
- no model-selection or model-store bypass exists.

Unlocks: real Performance execution and Android integration evidence.

### EVAL-5 — Persistence and comparison

State: `PLANNED`
Depends on: `EVAL-1`; closes with `EVAL-4`

Exit gate:

- in-memory and Room stores have equivalent behavior;
- run summaries and per-case outcomes survive restart with bounded retention;
- prompt, expected answer and generated text are not persisted by default;
- comparison rejects incompatible sample/execution identities;
- runtime comparisons additionally require compatible device/runtime identity.

Unlocks: history, run detail and model comparison UI.

### EVAL-6 — General Purpose v1

State: `PLANNED`
Depends on: `EVAL-2`, `EVAL-3`

Exit gate:

- source and license records are reviewed for every upstream dataset component;
- fixed case IDs and content digest define an immutable pack version;
- 20/50/100/200 nested presets are reproducible and category-stratified;
- category and aggregate expected-score fixtures pass independently of a real model.

Unlocks: default out-of-box benchmark experience.

### EVAL-7 — Performance UI and custom import

State: `PLANNED`
Depends on: UI shell after `EVAL-1`; closes after `EVAL-2`, `EVAL-4`, `EVAL-5`, `EVAL-6`

Exit gate:

- model, dataset, sample count and execution profile are explicit before run;
- progress, cancellation and typed failure states are connected;
- results separate quality, runtime, resources and reliability;
- compare view surfaces compatibility warnings and category deltas;
- canonical JSONL custom import exposes validation errors before installation.

Unlocks: end-to-end developer workflow.

### EVAL-8 — Validation and physical-device evidence

State: `PLANNED`
Depends on: `EVAL-2` through `EVAL-7`

Exit gate:

- deterministic host/unit/integration gates are green;
- Android instrumentation verifies real runtime orchestration and persistence;
- representative arm64 devices execute General Purpose v1 for supported reference tiers;
- quality and runtime comparison reports include exact identities and thermal/memory evidence;
- documentation and repository-level state are updated from measured evidence.

## Critical path

The shortest path to a useful connected benchmark is:

```text
EVAL-0
 -> EVAL-1 contracts
 -> EVAL-2 minimum dataset loader
 -> EVAL-3 minimum evaluators
 -> EVAL-4 runner
 -> EVAL-5 persistence
 -> EVAL-7 connected Performance surface
 -> EVAL-8 device evidence
```

`EVAL-6` is not on the engine critical path because development can use fixture packs, but it is required before the default feature is complete.

## Initial parallelization after EVAL-1

Once EVAL-1 is merged, at least five independent work packages may proceed concurrently:

1. dataset manifest/store/sampling;
2. deterministic evaluator implementations;
3. runner skeleton using fakes;
4. persistence schema/repositories;
5. Performance UI state/effect shell using fake repositories.

General Purpose v1 source/license curation may also begin once the dataset manifest and evaluator vocabulary are frozen, without waiting for the real runner.

## Scheduling against existing repository work

The repository's immediate operational gate remains Q35-6 physical tuning. Model-evaluation host-side implementation is structurally independent and may proceed in parallel. Physical evaluation evidence should reuse the Q35-6/Q35-7 measured runtime defaults once available; until then model-evaluation code may use candidate profiles only for development and must not publish them as certified performance baselines.
