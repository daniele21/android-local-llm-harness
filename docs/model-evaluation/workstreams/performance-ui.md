# Performance UI workstream

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.performance-ui
Read when: implementing the connected Performance surface, benchmark run setup, history, dataset import or model comparison UX
Last reviewed: 2026-08-15

## Goal

Expose model evaluation as a connected developer workflow in `apps/local-llm-phone-test` without mixing semantic model quality with the existing Diagnostics runtime-regression controls.

The target top-level information architecture is:

```text
Performance
  Run
  Datasets
  History
  Compare
```

The existing Diagnostics benchmark content remains the surface for telemetry-derived baselines/regression health. Performance owns active dataset execution and model-quality comparison.

## Run setup

Before execution the user explicitly selects:

```text
Model
Dataset
Sample preset/count
Execution profile
```

Initial sample controls:

- Smoke — 20;
- Quick — 50;
- Standard — 100, default for General Purpose v1;
- Extended — 200;
- All;
- Custom — step of 10 where supported by dataset size.

Unavailable presets are disabled rather than silently downsampled. A custom dataset smaller than a preset remains runnable with `All`.

Model selection shows only installed product-supported artifacts. Missing models route the developer to Models rather than creating an evaluation-specific download/install path.

Execution profile selection must expose at least direct deterministic and thinking modes where compatible. Changing execution profile visibly changes comparison identity.

## Run progress

An active run shows:

- dataset and exact preset/count;
- selected model;
- execution profile;
- completed/total cases;
- current category when safe/useful;
- elapsed duration;
- quality summary only from completed scored cases, explicitly marked partial;
- completed/error/invalid counts;
- Cancel action.

There is no pause/resume in v1. Process death may leave a persisted partial/failed run; it does not resume a model decode automatically.

## Result hierarchy

Completed result detail separates four sections.

### Quality

- transparent aggregate suite score when the dataset defines weights;
- category scores;
- passed/incorrect/invalid counts;
- exact Harness dataset/subset/version label.

### Runtime

- model preparation/load metric when available;
- TTFT median/p95;
- total latency median/p95;
- prefill/decode throughput summaries;
- sample counts supporting each metric.

### Resources

- memory observations available from the resource source;
- thermal state/range where captured;
- clear `Unavailable` instead of zero for missing source data.

### Reliability

- completed/scored;
- invalid output;
- timeout;
- runtime failure;
- cancelled/skipped.

The UI must not visually imply that a lower RAM value or higher token/s contributes automatically to the quality score.

## Case-level inspection

History may show privacy-safe case rows containing:

- case ID;
- category;
- normalized score;
- evaluator outcome type;
- terminal runtime status;
- privacy-safe per-case metrics.

Prompt, expected answer and generated output are not reconstructed from telemetry. The current active run may show generated output ephemerally when explicitly requested, but persistence of content is outside v1.

## Compare

V1 compare supports at least two selected completed runs. The comparison service supplies typed compatibility; UI does not infer it.

When quality-compatible:

- show aggregate quality delta;
- category-by-category score delta;
- reliability delta.

When runtime-compatible:

- additionally show TTFT, latency, throughput and resource deltas.

When incompatible:

- display exact incompatibility reasons such as different dataset version, sample set, execution profile, evaluator version, backend/device/runtime identity;
- suppress calculated deltas for the incompatible dimension;
- still allow raw run summaries to be inspected.

A future quality-vs-speed Pareto chart may be added after the comparison data model is stable. It must not name a universal winner without explicit scenario weights.

## Datasets

Dataset management shows:

- built-in packs and version;
- custom imported packs and version;
- case count/categories;
- source type;
- installed/available state if built-in delivery becomes downloadable;
- validation/import errors;
- explicit delete for user-imported packs where allowed.

Import uses Android document selection and accepts the canonical format defined by [`datasets.md`](datasets.md). Validation completes before the dataset appears as usable.

## State/effect boundary

Performance follows the connected app's ViewModel/UDF direction. Renderable state and intents must not remain as Activity-owned mutable mirrors.

Conceptually:

```text
PerformanceState
  selectedSection
  runSetupState
  activeRunState
  datasetState
  historyState
  compareState
  loading/error state

PerformanceIntent
  SelectModel
  SelectDataset
  SelectSamplePreset
  SelectExecutionProfile
  StartRun
  CancelRun
  ImportDataset
  DeleteDataset
  OpenRun
  SelectCompareRun
  Refresh

PerformanceEffect
  OpenDocumentPicker
  NavigateToModels
  ShowMessage
```

Exact names should align with the existing app architecture conventions.

U-01 freezes the Performance route vocabulary and typed state/intent/effect surface. It deliberately contains no fake results or production runner/storage assumptions. `Standard` is the setup default and custom counts must be positive multiples of 10; dataset-specific availability remains a connected-state concern.

## Task ledger — navigation and state shell

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-U-01 | DONE | EVAL-C-05 | Define Performance route/navigation placement and state/effect contract consistent with phone-app architecture. |
| EVAL-U-02 | READY | EVAL-U-01 | Implement ViewModel/reducer using fake evaluation, dataset and history repositories. |
| EVAL-U-03 | PLANNED | EVAL-U-02 | Add top-level Performance entry and Run/Datasets/History/Compare subnavigation for compact/expanded shells. |
| EVAL-U-04 | PLANNED | EVAL-U-02 | Add deterministic loading, empty, unavailable and error states before backend connection. |

U-02 can now run in parallel with engine/storage implementation. Connected behavior remains gated by the owning domain tasks below.

## Task ledger — run configuration and execution

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-U-10 | PLANNED | EVAL-U-02,EVAL-D-06 | Implement dataset selector with version/case-count metadata and General Purpose default. |
| EVAL-U-11 | PLANNED | EVAL-U-02,EVAL-R-02 | Implement selector for installed supported model artifacts only. |
| EVAL-U-12 | PLANNED | EVAL-U-02,EVAL-D-08 | Implement Smoke/Quick/Standard/Extended/All/Custom sample controls with availability validation. |
| EVAL-U-13 | PLANNED | EVAL-U-02,EVAL-C-06 | Implement execution-profile selector and compatibility explanation. |
| EVAL-U-14 | PLANNED | EVAL-U-10,EVAL-U-11,EVAL-U-12,EVAL-U-13,EVAL-R-03 | Connect preflight/readiness and disable Start with typed reasons when invalid. |
| EVAL-U-15 | PLANNED | EVAL-U-14,EVAL-R-10 | Connect active run progress, partial counters and elapsed state. |
| EVAL-U-16 | PLANNED | EVAL-U-15,EVAL-R-09 | Connect cooperative cancellation and terminal cancelled/skipped presentation. |
| EVAL-U-17 | PLANNED | EVAL-U-15,EVAL-P-06 | Handle process recreation by restoring persisted run state without resuming decode implicitly. |

## Task ledger — results and history

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-U-20 | PLANNED | EVAL-P-01,EVAL-R-10 | Implement run history list with dataset/model/profile identity and terminal state. |
| EVAL-U-21 | PLANNED | EVAL-U-20 | Implement Quality result section with aggregate/category score and subset/version labeling. |
| EVAL-U-22 | PLANNED | EVAL-U-20 | Implement Runtime section with nullable median/p95/throughput metrics and supporting sample counts. |
| EVAL-U-23 | PLANNED | EVAL-U-20 | Implement Resources and Reliability sections with unavailable/typed failure states. |
| EVAL-U-24 | PLANNED | EVAL-P-07,EVAL-U-20 | Implement privacy-safe case result list/detail without persisted prompt/expected/generated text. |
| EVAL-U-25 | PLANNED | EVAL-U-21,EVAL-U-22,EVAL-U-23,EVAL-U-24 | Add result-state tests for partial, cancelled, failed, metric-missing and complete runs. |

## Task ledger — comparison

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-U-30 | PLANNED | EVAL-P-08,EVAL-U-20 | Implement completed-run selection for comparison. |
| EVAL-U-31 | PLANNED | EVAL-U-30,EVAL-P-09 | Render quality compatibility, aggregate/category deltas and mismatch reasons. |
| EVAL-U-32 | PLANNED | EVAL-U-30,EVAL-P-09 | Render runtime/resource deltas only when runtime compatibility passes. |
| EVAL-U-33 | PLANNED | EVAL-U-31,EVAL-U-32 | Add comparison tests proving incompatible dimensions suppress calculated deltas. |
| EVAL-U-34 | DEFERRED | EVAL-U-33 | Add optional quality-vs-speed Pareto visualization with memory representation. |

## Task ledger — custom dataset management

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-U-40 | PLANNED | EVAL-U-02,EVAL-D-10 | Connect Android document picker to canonical JSONL import. |
| EVAL-U-41 | PLANNED | EVAL-U-40 | Show parsing/validation errors with line/case context without logging sensitive case content. |
| EVAL-U-42 | PLANNED | EVAL-D-06,EVAL-U-40 | Show installed custom datasets and immutable local identity/version metadata. |
| EVAL-U-43 | PLANNED | EVAL-D-11,EVAL-U-42 | Connect explicit custom dataset deletion with active-run protection. |
| EVAL-U-44 | PLANNED | EVAL-U-41,EVAL-U-43 | Add document-picker/import/delete state and recreation tests. |

EVAL-7 closes when EVAL-U-01 through EVAL-U-33 and EVAL-U-40 through EVAL-U-44 are `DONE`. EVAL-U-34 is explicitly not required for v1.

## Parallel execution guidance

EVAL-U-02 can proceed now using fake evaluation, dataset and history repositories. After it exists:

- run-selector UI EVAL-U-10/U-12/U-13 can be built against fakes independently;
- history/result presentation EVAL-U-20 onward can use fixture repositories while persistence is implemented;
- custom import EVAL-U-40 onward can start as soon as EVAL-D-10 exists;
- compare presentation can start from fake `EvaluationCompatibility` results before the real comparison repository is connected.

Connected integration should happen only after the owning domain contracts are stable; UI must not invent temporary alternate schemas that then leak into core contracts.

## Completion gates

- developer can configure and start a run without touching Diagnostics internals;
- no unsupported/uninstalled model appears runnable;
- sample count and dataset version are visible before execution;
- partial and terminal progress are unambiguous;
- quality/runtime/resources/reliability remain visibly separate;
- incompatible run comparison cannot show false deltas;
- custom import errors remain actionable without leaking dataset content into logs;
- compact, expanded, landscape, large-font and accessibility acceptance follow the repository phone-app UX requirements.
