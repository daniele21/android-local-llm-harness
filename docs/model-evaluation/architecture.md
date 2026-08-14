# Model evaluation architecture

Status: active
Document type: architecture
Owner: model-evaluation
Canonical scope: model-evaluation.architecture
Read when: changing model-evaluation module ownership, dependency direction, execution flow, persistence or runtime integration
Last reviewed: 2026-08-14

## Boundary

Model evaluation is a control-plane capability that actively drives the existing embedded runtime. It must not become a second inference engine, model store, telemetry system or model-selection path.

Target flow:

```text
Performance UI
    |
EvaluationRunConfig
    |
EvaluationEngine
    |---------------- DatasetRepository
    |---------------- EvaluatorRegistry
    |---------------- EvaluationRepository
    |
    +---- controlled evaluation binding/profile
    |
LocalLlmClient / RuntimeOrchestrator
    |
normal model resolution + runtime lifecycle
    |
llama.cpp backend
    |
TelemetryRepository + resource observations
    |
correlated case metrics
    |
EvaluationRepository
```

The runtime remains the owner of generation lifecycle and observability remains the owner of runtime measurements. Model evaluation owns orchestration, task-quality scoring and evaluation-specific persistence.

## Intended ownership

Implementation should introduce modules only when concrete behavior justifies the boundary. The target ownership map is:

```text
evaluation/contracts
    dataset, case, evaluator, run, result and comparison contracts

evaluation/engine
    sampling orchestration, runtime execution, evaluator dispatch,
    cancellation, progress and result aggregation

evaluation/dataset-store
    manifest/case validation, app-private installation, digest identity,
    built-in/custom pack discovery and import

evaluation/in-memory-store
    deterministic test repository for runs/results

evaluation/room-store
    Android persistence, migrations and bounded retention

apps/local-llm-phone-test
    Performance UI, document picker and presentation state/effects
```

If the first implementation can preserve these responsibilities with fewer Gradle modules, packages may be used initially. New modules should be extracted when independent dependencies, persistence/platform isolation or test/reuse boundaries become concrete.

## Dependency direction

```text
apps
  |
evaluation engine ----------------------+
  |                                     |
evaluation contracts                    |
  |                                     |
dataset / result store contracts        |
  |                                     |
core public/runtime contracts            |
  |                                     |
observability contracts <---------------+
  |
backend interface
  |
llama.cpp implementation
```

Forbidden dependencies include:

- runtime-core importing evaluation packages;
- llama.cpp backend importing dataset/evaluator types;
- observability stores importing evaluation case content;
- UI directly invoking JNI/backend APIs;
- evaluator implementations depending on Android UI or Room;
- dataset packs containing executable evaluator code.

## Controlled model execution

The product currently resolves runtime behavior through explicit application/use-case/model bindings. Evaluation must preserve that invariant while allowing a developer to choose one installed supported model for a run.

The engine therefore needs a controlled evaluation binding/profile layer that:

1. accepts only a currently supported, installed curated artifact;
2. resolves an exact model profile without synthesizing unsupported compatibility state;
3. applies the selected versioned evaluation execution profile;
4. produces normal runtime identifiers and request lifecycle;
5. never mutates the developer's ordinary application/use-case binding as a side effect;
6. releases evaluation-owned sessions/contexts on completion, failure or cancellation.

The concrete binding mechanism must be decided in EVAL-1/EVAL-4 without bypassing `RuntimeOrchestrator` or the model store.

## Dataset pack architecture

Canonical installed layout is conceptually:

```text
evaluation-datasets/
  <dataset-id>/
    <version>/
      manifest.json
      cases.jsonl
      source-license-metadata...
```

Identity is content-derived. Installation is staged, fully validated and atomically published only after:

- schema validation;
- evaluator-type validation;
- bounded-size/count checks;
- duplicate-case-ID detection;
- ordered content digest calculation;
- manifest/digest consistency checks.

Built-in packs and imported packs use the same read contract after installation/discovery.

## Dataset manifest

The manifest owns at least:

```text
schemaVersion
datasetId
version
displayName
description
contentDigest
caseCount
categories + weights
supported presets
sampling policy/version
source records
license records
default execution profile compatibility
```

Source/license metadata is required for built-in redistributed packs. Custom local packs may use `source=USER_IMPORT` and must still have immutable local identity.

## Case contract

The canonical case representation supports structured messages/input while remaining evaluator-neutral:

```text
caseId
category
input
expected
evaluatorSpec
tags
bounded metadata
```

`input` and `expected` are evaluation-sensitive content and must not cross into normal telemetry.

`evaluatorSpec` is declarative. Example types include exact match, numeric, multiple choice, JSON field comparison and instruction constraints. It cannot reference Java/Kotlin class names, scripts, URLs or dynamic code.

## Sampling

Sampling is separated from dataset loading. A `SamplingStrategy` receives immutable case metadata and produces an ordered sample-set identity.

V1 target:

- fixed preset counts 20/50/100/200 when available;
- custom counts in steps of 10;
- deterministic category stratification;
- nested preset membership;
- stable tie-breaking independent of filesystem iteration order;
- explicit sampling policy version and seed/rank identity.

Selected case IDs are hashed into `sampleSetDigest` and persisted with the run.

## Evaluator registry

The evaluator registry maps a bounded enum/version pair to a deterministic implementation:

```text
EvaluatorSpec
  -> type
  -> version
  -> validated parameters
  -> Evaluator
  -> EvaluationOutcome(score 0..1, typed details)
```

The registry rejects unknown versions before a run begins. Evaluators must be pure or deterministic with respect to case expected data and generated output.

No evaluator may perform network access or model inference in v1.

## Evaluation run lifecycle

```text
CREATED
  -> VALIDATING
  -> PREPARING_MODEL
  -> OPTIONAL_WARMUP
  -> RUNNING
       -> case N: session/context create
       -> generate
       -> correlate telemetry
       -> evaluate
       -> persist outcome
       -> close case session/context
  -> AGGREGATING
  -> COMPLETED

Any active state
  -> CANCELLING
  -> CANCELLED

Validation/runtime/persistence terminal failures
  -> FAILED
```

A persistence failure must be surfaced as an evaluation failure/partial-result condition but must not corrupt the underlying runtime. Runtime cleanup remains mandatory.

## Case execution isolation

Model residency and case context are separate concerns.

- Keeping the same model loaded across cases is allowed and preferred for warm per-case measurements.
- Each scored case receives a clean session/context so previous prompts and generated tokens cannot influence the next case.
- Warm-up is explicit, unscored and recorded in run identity.
- Case order is deterministic for a given sample-set/order policy.
- A cancelled run never evaluates cases that did not complete generation.

## Telemetry correlation

Each case receives a stable evaluation case-execution ID and normal generation request ID. The evaluation engine correlates the request ID with existing telemetry/resource records to attach privacy-safe metrics.

Do not copy prompt/output into telemetry to make correlation easier.

Metrics remain nullable. Aggregation excludes unavailable measurements from metric-specific distributions rather than substituting zero.

## Evaluation persistence

Evaluation persistence is separate because it has different privacy and retention semantics from ordinary generation telemetry.

Target persistent hierarchy:

```text
EvaluationRunEntity
    1 -> N EvaluationCaseResultEntity
```

Run entity stores identity/configuration, status, aggregate scores, metric summaries and timestamps. Case result stores case ID/category, evaluator outcome, correlated request ID, typed error/stop outcome and privacy-safe measurements.

Default persistence excludes case input, expected text and generated text.

A bounded retention policy applies independently to evaluation runs/results. Deleting an evaluation run must not delete ordinary telemetry or installed datasets.

## Comparison service

Comparison logic is domain logic, not UI logic. It receives two or more run summaries and returns:

- quality compatibility;
- runtime compatibility;
- incompatibility reasons;
- category score deltas when valid;
- aggregate quality delta when valid;
- latency/throughput/resource deltas only when runtime-compatible;
- Pareto-relevant values without choosing a universal winner.

The UI consumes these typed results and does not recreate compatibility checks ad hoc.

## Concurrency

Initial policy permits only one active evaluation run per embedded runtime process. Cases execute sequentially because the runtime default remains one active decode.

Dataset import/read and historical result queries may run off the UI thread concurrently when they do not mutate active runtime state.

Parallel model evaluation on one device is deferred until runtime concurrency policy changes from evidence.

## Security and privacy

- Custom dataset files are untrusted input.
- JSON/JSONL parsing is bounded and rejects unsupported schema/evaluator types.
- No dataset field becomes a filesystem path or URL to fetch automatically.
- Imported content remains app-private after explicit user selection.
- Prompt, expected and generated content stay outside normal telemetry/logs.
- Diagnostic export includes only privacy-safe evaluation identities/scores/metrics unless a future explicit export policy says otherwise.
- Dataset deletion and result deletion are separate explicit operations.

## Relationship to existing benchmark engine

`observability/benchmark-engine` continues to own:

- telemetry-derived cold/warm performance baselines;
- retained baseline history;
- runtime regression health checks.

Model evaluation may consume the same runtime measurements but must not replace those regression semantics. A future adapter may allow an evaluation run to intentionally seed a performance baseline, but that is deferred and would require an explicit ownership decision.
