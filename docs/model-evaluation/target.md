# Model evaluation target behavior

Status: active
Document type: target-specification
Owner: model-evaluation
Canonical scope: model-evaluation.target
Read when: changing product behavior, scoring semantics, benchmark configuration or comparison policy for model evaluation
Last reviewed: 2026-08-14

## Goal

Model evaluation actively executes a versioned test set against an explicitly selected supported local model and produces reproducible evidence for model choice. It measures task quality and runtime cost in the same run without conflating them.

The primary product question is:

> Given this dataset, execution profile and Android device, which supported model provides the best quality/performance trade-off for my scenario?

## Distinction from runtime regression benchmarking

[`../benchmark-engine.md`](../benchmark-engine.md) remains the owner of telemetry-derived runtime baselines and regression health. It does not execute inference, choose test cases or score semantic correctness.

Model evaluation owns:

- dataset and sample-set identity;
- active benchmark execution;
- expected-answer/evaluator definitions;
- quality scoring;
- run orchestration and progress;
- correlation with existing telemetry/resource observations;
- model-to-model comparison for compatible evaluation runs.

The two capabilities may share privacy-safe telemetry contracts and presentation primitives but must not duplicate ownership.

## Initial built-in experience

The default built-in pack is `general-purpose-v1`. The target pack contains 200 immutable cases so the UI can expose nested presets:

- `Smoke` — 20 cases;
- `Quick` — 50 cases;
- `Standard` — 100 cases, default;
- `Extended` — 200 cases;
- `All` — every case in the selected dataset;
- `Custom` — a user-selected count in steps of 10 where dataset size permits.

Preset membership is deterministic and nested: the 20-case set is contained in 50, 50 in 100 and 100 in 200. Running the same pack version and preset therefore selects the same sample IDs on every device.

The planned General Purpose v1 composition is:

| Category | Source family | Cases in 200 | Quality weight |
| --- | --- | ---: | ---: |
| General knowledge and reasoning | MMLU-Pro subset | 60 | 30% |
| Instruction following | IFEval-style/public subset | 40 | 20% |
| Mathematical reasoning | GSM8K subset | 30 | 15% |
| Science/commonsense reasoning | ARC Challenge subset | 30 | 15% |
| Structured output | Harness-owned cases | 20 | 10% |
| Context retrieval | Harness-owned cases | 20 | 10% |

The exact redistributed artifacts, licenses and case IDs are not final until the dataset workstream completes source/licensing review. A pack must not ship merely because an upstream dataset is publicly downloadable.

## Dataset semantics

Every installed dataset pack has immutable identity composed from:

```text
datasetId
+ version
+ canonical manifest
+ ordered case content digest
= datasetDigest
```

Every case has:

- stable case ID unique within the dataset version;
- category;
- prompt/messages or structured input;
- expected answer payload;
- evaluator specification and evaluator version;
- optional case tags used for deterministic stratification;
- bounded metadata such as source ID and difficulty;
- no executable code.

Imported data is treated as potentially sensitive and remains app-private. Dataset content does not enter normal telemetry, logs or diagnostic export.

## Evaluation modes

An evaluation run selects an explicit versioned execution profile. V1 provides at least:

- `direct-deterministic-v1` — thinking disabled, deterministic/fixed generation configuration;
- `thinking-v1` — thinking enabled with an explicit bounded output/thinking budget, when the selected supported model/profile permits it.

Results from different execution profiles are not directly quality-comparable. The UI may display them side by side only with an incompatibility warning and must not calculate a misleading delta.

Case-specific output constraints, such as JSON, are part of effective execution identity.

## Case isolation

A suite run may keep the selected model resident in memory, but scored cases are logically independent:

1. prepare/load the selected model through the normal runtime path;
2. optionally perform an explicitly labelled warm-up that is never scored;
3. create an isolated session/context for one case;
4. execute one case;
5. collect the correlated telemetry/resource outcome;
6. evaluate the output;
7. close the case session/context;
8. continue to the next case while model residency remains governed by runtime policy.

No case may inherit conversational or context state from a previous scored case.

## Quality scoring

Every evaluator returns a normalized score in `[0, 1]` plus a typed outcome. V1 supports deterministic scoring only.

Required evaluator families are:

- exact normalized match;
- multiple choice;
- numeric final answer;
- JSON schema/field comparison;
- regex/format constraint;
- instruction-constraint aggregation.

A runtime failure, timeout or required-format parse failure yields a zero case-quality score and is also counted separately in reliability metrics. The UI must preserve this distinction.

Category score is the arithmetic mean of scored cases in that category. The General Purpose aggregate quality score is the explicit weighted mean of category scores using the pack manifest weights. Category scores remain visible beside the aggregate.

No v1 score uses an external LLM judge, semantic embedding model or arbitrary evaluator script.

## Runtime and resource metrics

Model evaluation does not invent a second telemetry system. It correlates each case request with existing observability data where available, including:

- model preparation/load classification and duration;
- TTFT;
- prefill duration/throughput;
- decode duration/throughput;
- total latency;
- prompt/generated token counts;
- memory snapshots/peak observations supported by current resource capture;
- thermal observations;
- typed runtime/stop outcomes.

Unavailable metrics remain unavailable rather than becoming zero.

Run summaries expose median and p95 latency/throughput where sample count supports them and keep cold model preparation separate from warm per-case execution metrics.

## Reliability metrics

A run records counts and rates for at least:

- completed and scored;
- incorrect but valid output;
- invalid output;
- timeout;
- runtime failure;
- cancelled;
- skipped/not attempted after cancellation.

Cancellation does not reinterpret unexecuted cases as quality failures.

## Run identity

A reproducible run records or derives:

```text
runId
model artifact SHA-256
model profile/tier/quantization
backend revision
harness build/commit identity when available
dataset id/version/digest
ordered selected sample IDs hash
sampling policy/version/seed
execution profile id/version
effective context and generation identity
evaluator versions
device/runtime identity
timestamps and run state
```

The generated text itself is not required for run identity.

## Comparability

Comparison has two levels.

### Quality-compatible

Quality deltas require equality of:

- dataset digest;
- selected sample-set hash;
- evaluator versions;
- execution profile/effective semantic generation identity;
- output-constraint semantics.

Device identity is not required for quality comparison, but backend/template changes that alter semantic execution identity invalidate direct comparison.

### Runtime-compatible

Latency, throughput, memory and thermal deltas additionally require compatible:

- physical device identity/class;
- Android/ABI/runtime environment fields required by policy;
- backend revision;
- model-load/warm-up policy;
- runtime tuning profile dimensions.

When compatibility is incomplete, the UI shows the raw runs but suppresses calculated runtime deltas.

## Persistence and privacy

Dataset content and evaluation results are separate from normal telemetry ownership.

Default persistent sample outcome contains:

- case ID/category;
- normalized score and typed evaluator outcome;
- evaluator version;
- correlated request/run IDs;
- privacy-safe metrics and typed error/stop codes.

By default it does not persist:

- prompt/messages;
- expected answer text;
- generated answer text;
- arbitrary backend exception messages;
- filesystem/document URIs.

The current-run UI may display generated output ephemerally when available. Persistent output retention is deferred until an explicit privacy/retention design exists.

## Custom datasets

V1 accepts a canonical JSONL representation through Android document selection. Import must:

- validate every record before publication;
- reject unknown evaluator types and unsupported schema versions;
- enforce bounded record/count/size limits;
- generate or validate stable case IDs;
- compute immutable dataset identity;
- install atomically into app-private storage;
- never execute embedded scripts, templates or code;
- present validation errors before installation.

CSV mapping, remote community registries and executable custom scorers are deferred.

## UI behavior

The Performance surface provides:

```text
Performance
  -> Run
  -> Datasets
  -> History
  -> Compare
```

Run configuration requires explicit model, dataset, sample preset/count and execution profile. Results visually separate quality, runtime, resources and reliability.

Compare prioritizes category deltas and Pareto trade-offs instead of a universal score. A speed/quality plot may represent memory as a third visual dimension once the source data is complete and comparable.

## Non-goals for v1

- arbitrary GGUF/model-family import;
- online/cloud inference fallback;
- LLM-as-judge;
- subjective writing quality;
- embeddings or semantic similarity scoring;
- leaderboard publication;
- automatic production model switching;
- automatic scenario weighting;
- CSV/Excel dataset wizard;
- benchmark results presented as equivalent to official full upstream benchmark scores when only a Harness subset was run.

Harness subset labels must always identify the exact pack/version, for example `MMLU-Pro / Harness General Purpose v1 subset`, rather than claiming an official full-dataset score.
