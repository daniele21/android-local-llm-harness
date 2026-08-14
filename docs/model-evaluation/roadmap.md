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
- Dataset ingestion and evaluator implementation can progress in parallel once shared contracts are stable.
- UI shell/state modeling can start from contracts before the real runner, using deterministic fakes.
- General Purpose v1 source/licensing work starts after the canonical dataset format is fixed.
- Persistence can progress in parallel with the runner after run/result contracts are stable.
- Custom import depends on dataset validation/storage, not built-in dataset completion.
- Physical-device evidence depends on an integrated runner, persistence and connected configuration; it does not block host development.
- Existing Q35-6 physical tuning and this feature can run in parallel except for shared hardware/review capacity.

## Milestone dependency graph

```text
EVAL-0 Plan and architecture
        |
        v
EVAL-1 Contracts and identity  [DONE]
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

`EVAL-4*` can develop against fakes now but cannot close before EVAL-2/EVAL-3 production implementations. `EVAL-5*` can build repositories now and closes when the real runner persists complete results.

## Parallel work lanes

### Lane A — runner
```text
EVAL-R-01 + EVAL-R-02 -> preflight -> runtime orchestration -> telemetry correlation
```
Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane B — datasets
```text
EVAL-D-01 -> pack validation -> deterministic sampling -> storage/import
                                      |
                                      +-> General Purpose v1
```
Owned by [`workstreams/datasets.md`](workstreams/datasets.md).

### Lane C — evaluators
```text
EVAL-E-01 -> exact/numeric/MCQ/JSON/constraint evaluators
```
Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane D — persistence and comparison
```text
EVAL-P-01 -> in-memory store -> Room schema -> comparison service
```
Owned by [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md).

### Lane E — UI
```text
EVAL-U-01 -> fake state/effects -> run setup UI
                      |
EVAL-4 + EVAL-5 ------+-> connected execution/results/compare
EVAL-2 -----------------> custom import
```
Owned by [`workstreams/performance-ui.md`](workstreams/performance-ui.md).

### Lane F — validation
```text
EVAL-V-01 -> deterministic identity evidence
components -> incremental host matrix
full feature -> Android/device evidence
```
Owned by [`workstreams/validation.md`](workstreams/validation.md).

## Milestone plan

### EVAL-0 — Plan and architecture
State: `DONE`

Exit gate:
- capability is separate from runtime regression benchmarking;
- canonical documents and ownership exist;
- dependency graph and maintenance rules are defined.

### EVAL-1 — Contracts and identity
State: `DONE`
Depends on: `EVAL-0`

Exit evidence:
- `evaluation/contracts` is the one concrete contract module;
- dataset/case/evaluator/sampling/run/result value contracts exist;
- ordered sample, evaluator, semantic-execution and full-run identities are deterministic SHA-256 fingerprints;
- run identity includes model, dataset, sampling policy/version/seed, evaluator set, semantic execution and runtime environment;
- quality/runtime incompatibility and typed failure taxonomies are explicit;
- invalid value combinations fail at contract construction.

Unlocked in parallel: EVAL-2, EVAL-3, EVAL-4 skeleton/binding, EVAL-5 repository contract, EVAL-7 UI shell and EVAL-V-01 identity validation.

### EVAL-2 — Dataset system
State: `READY`
Depends on: `EVAL-1`

Exit gate:
- manifests/JSONL validate deterministically;
- app-private installation is atomic and digest-verified;
- nested stratified sampling is deterministic;
- custom canonical JSONL imports without executable code;
- dataset content never enters normal telemetry.

### EVAL-3 — Deterministic evaluators
State: `READY`
Depends on: `EVAL-1`

Exit gate:
- required evaluator types are versioned and registry-backed;
- exact, MCQ, numeric, JSON and instruction fixtures score reproducibly;
- malformed output yields typed outcomes, not parser crashes;
- no LLM-as-judge or arbitrary executable evaluator exists in v1.

### EVAL-4 — Evaluation runner
State: `READY`
Depends on: `EVAL-1`; closes after `EVAL-2` and `EVAL-3`

Exit gate:
- supported selected models execute through the normal harness runtime;
- each scored case uses isolated context while model residency may remain warm;
- request IDs correlate quality with telemetry;
- cancellation/timeout/failure leave runtime reusable;
- no model-selection/store bypass exists.

### EVAL-5 — Persistence and comparison
State: `READY`
Depends on: `EVAL-1`; closes with `EVAL-4`

Exit gate:
- in-memory and Room stores have equivalent behavior;
- summaries/outcomes survive restart with bounded retention;
- prompt, expected answer and generated text are not persisted by default;
- comparison rejects incompatible quality/runtime identities.

### EVAL-6 — General Purpose v1
State: `PLANNED`
Depends on: `EVAL-2`, `EVAL-3`

Exit gate:
- source/license records are reviewed per upstream component;
- fixed case IDs/content digest define an immutable pack version;
- 20/50/100/200 nested presets are reproducible and stratified;
- score fixtures pass independently of a real model.

### EVAL-7 — Performance UI and custom import
State: `READY`
Depends on: UI shell after `EVAL-1`; closes after `EVAL-2`, `EVAL-4`, `EVAL-5`, `EVAL-6`

Exit gate:
- model/dataset/sample/execution profile are explicit before run;
- progress, cancellation and typed failures are connected;
- results separate quality/runtime/resources/reliability;
- compare surfaces compatibility warnings/category deltas;
- JSONL import validates before installation.

### EVAL-8 — Validation and physical-device evidence
State: `PLANNED`
Depends on: `EVAL-2` through `EVAL-7`

Incremental deterministic validation starts now at `EVAL-V-01`; final closure still requires Android integration and representative device evidence.

## Critical path

```text
EVAL-0 -> EVAL-1 -> EVAL-2 minimum loader -> EVAL-3 minimum evaluators
       -> EVAL-4 runner -> EVAL-5 persistence -> EVAL-7 connected UI
       -> EVAL-8 device evidence
```

EVAL-6 is outside the engine critical path because fixture packs support development, but it is required before the default feature is complete.

## Parallelization now active

Seven entry tasks can proceed concurrently:

1. `EVAL-D-01` dataset schema;
2. `EVAL-E-01` evaluator registry;
3. `EVAL-R-01` runner state machine with fakes;
4. `EVAL-R-02` controlled model binding/profile resolution;
5. `EVAL-P-01` persistence repository contract;
6. `EVAL-U-01` Performance UDF/navigation contract;
7. `EVAL-V-01` identity/hash golden validation.

General Purpose source/license curation can join after EVAL-D-01 freezes the manifest/schema vocabulary.

## Scheduling against existing repository work

OMBRA remains the repository's immediate implementation block. Model-evaluation host-side work is structurally independent and can proceed in parallel. Q35-6 remains a physical release-readiness gate; final evaluation performance evidence should reuse its measured runtime defaults. Candidate profiles remain development-only evidence.
