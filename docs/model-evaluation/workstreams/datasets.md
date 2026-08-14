# Model evaluation dataset workstream

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.datasets
Read when: implementing evaluation dataset formats, storage, sampling, import or the built-in General Purpose pack
Last reviewed: 2026-08-14

## Goal

Provide immutable, versioned and reproducible evaluation packs that can be installed or imported locally, sampled deterministically and consumed without exposing evaluation content to ordinary telemetry.

This workstream owns both the generic dataset mechanism and the initial built-in `general-purpose-v1` pack. Evaluator behavior is owned by [`evaluation-core.md`](evaluation-core.md).

## Dataset pack contract

Canonical installed form is conceptually:

```text
<datasetId>/<version>/
  manifest.json
  cases.jsonl
  optional source/license metadata
```

A pack is valid only when all records validate and the canonical ordered case content matches the manifest digest. Publication to the active dataset registry is atomic.

The initial import surface accepts the canonical JSONL case schema only. CSV/Excel mapping is deferred until the native schema and evaluator semantics are stable.

## Content and privacy policy

Dataset content is not ordinary telemetry. Imported prompts, expected answers and source metadata may contain sensitive information and therefore:

- remain in app-private dataset storage;
- are never copied into generation logs or telemetry metadata;
- are excluded from normal diagnostic export;
- are deleted only by an explicit dataset deletion action;
- are parsed as untrusted data with bounded record/count/size limits;
- cannot specify executable code, arbitrary class names or network fetches.

## Deterministic sampling

The dataset layer returns an ordered list of case IDs for a requested sampling policy. V1 sampling must provide:

- deterministic category stratification;
- stable order independent of filesystem/map ordering;
- nested fixed presets where the dataset declares them;
- custom count in multiples of 10 where the dataset size permits;
- `All` for the full pack;
- explicit sampling policy version and seed/rank identity;
- an immutable `SampleSetDigest` over the final ordered case IDs.

For `general-purpose-v1`, preset membership is frozen with the pack version rather than re-randomized at runtime.

## General Purpose v1 target

The target pack contains 200 cases with a default 100-case Standard run:

| Category | Planned source | Cases | Weight |
| --- | --- | ---: | ---: |
| General knowledge/reasoning | MMLU-Pro subset | 60 | 30% |
| Instruction following | IFEval-compatible/public source subset | 40 | 20% |
| Mathematical reasoning | GSM8K subset | 30 | 15% |
| Science/commonsense | ARC Challenge subset | 30 | 15% |
| Structured output | Harness-owned | 20 | 10% |
| Context retrieval | Harness-owned | 20 | 10% |

The exact source artifacts are gated on redistribution/license review. Until that gate closes, these source names define intended benchmark families rather than authorization to copy upstream data into the repository or APK.

## Harness-owned structured-output cases

The initial 20 cases should cover representative local-app patterns such as:

- entity/field extraction;
- classification into a bounded taxonomy;
- dates/numbers normalization;
- nested JSON objects;
- arrays with explicit ordering requirements;
- null/missing-value behavior;
- rejection of extra fields when schema requires it;
- short transformation tasks with exact output constraints.

Cases must be domain-neutral and synthetic enough to avoid personal or proprietary data. Expected values should support deterministic JSON/field scoring.

## Harness-owned context-retrieval cases

The initial 20 cases should test explicit retrieval from provided context, with controlled distractors and target location variation. Cases should span approved mobile context tiers where practical and include targets near the beginning, middle and end of input.

V1 evaluates answer retrieval, not long-context model maximum claims. Case metadata records approximate input-token tier only after tokenization behavior is validated for the supported execution path.

## Task ledger — generic dataset system

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-D-01 | READY | EVAL-C-02,EVAL-C-03 | Define versioned manifest and canonical JSONL case schemas from the shared contracts. |
| EVAL-D-02 | PLANNED | EVAL-D-01,EVAL-C-09 | Implement bounded streaming JSONL parser with typed line/field/schema errors. |
| EVAL-D-03 | PLANNED | EVAL-D-01,EVAL-E-01 | Implement full-pack validator for IDs, categories, evaluator specs, weights and supported schema versions. |
| EVAL-D-04 | PLANNED | EVAL-D-01,EVAL-C-08 | Implement canonical ordered content digest and manifest/digest verification. |
| EVAL-D-05 | PLANNED | EVAL-D-02,EVAL-D-03,EVAL-D-04 | Implement staged app-private installation with atomic publication and rollback cleanup. |
| EVAL-D-06 | PLANNED | EVAL-D-05 | Implement dataset registry/discovery for built-in and user-imported installed packs. |
| EVAL-D-07 | PLANNED | EVAL-C-04,EVAL-D-03 | Implement deterministic stratified sampling with stable tie-breaking and versioned policy. |
| EVAL-D-08 | PLANNED | EVAL-D-07 | Implement 20/50/100/200 preset resolution, `All`, and bounded custom multiple-of-10 counts. |
| EVAL-D-09 | PLANNED | EVAL-D-05,EVAL-D-08 | Add fixture packs and tests for malformed records, duplicate IDs, digest mismatch, rollback and deterministic sampling. |
| EVAL-D-10 | PLANNED | EVAL-D-05 | Implement Android document import adapter for canonical JSONL with generated local manifest metadata. |
| EVAL-D-11 | PLANNED | EVAL-D-06,EVAL-D-10 | Implement explicit dataset delete with protection against deleting a pack used by an active run. |
| EVAL-D-12 | PLANNED | EVAL-D-09,EVAL-D-11 | Document custom JSONL schema, limits, privacy behavior and import failure semantics. |

EVAL-2 closes when EVAL-D-01 through EVAL-D-12 are `DONE` and the real evaluation engine can consume installed packs through the dataset contract.

## Task ledger — General Purpose v1

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-GP-01 | PLANNED | EVAL-D-01 | Inventory exact upstream dataset/version candidates and stable source identifiers for the four public benchmark families. |
| EVAL-GP-02 | PLANNED | EVAL-GP-01 | Record license, attribution and redistribution requirements per source and choose bundle-vs-download treatment. |
| EVAL-GP-03 | PLANNED | EVAL-GP-02 | Block any source component whose redistribution/attribution path is not explicitly acceptable for the project. |
| EVAL-GP-04 | PLANNED | EVAL-D-07,EVAL-GP-03 | Define deterministic category selection rules and freeze upstream source case IDs for the 160 public-derived cases. |
| EVAL-GP-05 | PLANNED | EVAL-E-05 | Author 20 Harness structured-output cases with deterministic expected JSON/field outcomes. |
| EVAL-GP-06 | PLANNED | EVAL-E-02,EVAL-E-06 | Author 20 Harness context-retrieval cases with deterministic target answers and distractors. |
| EVAL-GP-07 | PLANNED | EVAL-GP-04,EVAL-GP-05,EVAL-GP-06 | Assemble the 200-case canonical pack and calculate immutable dataset digest. |
| EVAL-GP-08 | PLANNED | EVAL-D-08,EVAL-GP-07 | Freeze nested 20/50/100/200 preset membership with category proportions matching the declared policy. |
| EVAL-GP-09 | PLANNED | EVAL-E-08,EVAL-GP-08 | Validate category weights, aggregate score math and zero/failure semantics using synthetic model-output fixtures. |
| EVAL-GP-10 | PLANNED | EVAL-GP-02,EVAL-GP-07 | Add source/license/attribution metadata and exact Harness-subset labeling to the pack. |
| EVAL-GP-11 | PLANNED | EVAL-D-06,EVAL-GP-10 | Integrate General Purpose v1 into built-in discovery/install flow without special-case runner behavior. |
| EVAL-GP-12 | PLANNED | EVAL-GP-09,EVAL-GP-11 | Add reproducibility test proving identical pack digest and preset sample IDs across clean builds. |

EVAL-6 closes when EVAL-GP-01 through EVAL-GP-12 are `DONE`.

## Parallel execution guidance

EVAL-D-01 is now ready. After it fixes the schema, EVAL-D-02 parsing, EVAL-D-04 digesting and EVAL-GP-01 source inventory can proceed in parallel.

After evaluator semantics are available:

- EVAL-GP-05 and EVAL-GP-06 can be authored independently;
- public-source curation EVAL-GP-01 through EVAL-GP-04 can continue independently of Harness-owned case authoring;
- dataset storage EVAL-D-05/D-06 and sampling EVAL-D-07/D-08 can proceed as separate implementation lanes after their prerequisites.

General Purpose v1 assembly is intentionally late: source/legal review, evaluator semantics and deterministic sampling must be stable before the 200-case digest is frozen.

## Completion gates

- clean install/import produces the same dataset identity from the same bytes;
- a partially invalid pack never becomes visible as installed;
- fixed presets never silently change membership inside one dataset version;
- custom imports cannot execute code or trigger network access;
- built-in source/license metadata is sufficient to audit redistribution provenance;
- Harness subset scores are labelled as Harness subsets, never as official full benchmark scores.
