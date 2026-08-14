# Model Evaluation — Coding Agent Guide

This guide owns navigation and validation for model-evaluation implementation under `evaluation/`. Current progress belongs in [`../docs/model-evaluation/current-state.md`](../docs/model-evaluation/current-state.md).

## Read order

1. Read the repository [`../AGENTS.md`](../AGENTS.md).
2. Read [`../docs/model-evaluation/README.md`](../docs/model-evaluation/README.md) for capability routing.
3. Read only the focused model-evaluation workstream that owns the change.
4. Read runtime or observability specifications only when the change crosses those existing boundaries.

## Current ownership

`evaluation/contracts` owns backend-independent dataset, evaluator, sampling, run, identity, compatibility, persistence and failure contracts plus deterministic canonical hashing.

`evaluation/in-memory-store` owns the deterministic privacy-safe in-memory implementation of `EvaluationResultRepository`. It is the parity reference for persistent stores: run configuration is immutable, lifecycle transitions are validated, case results remain bounded to the selected sample set, history ordering is deterministic, active runs cannot be deleted, and retention applies only to terminal runs.

Do not add Room or other durable storage behavior to the in-memory module. Durable persistence belongs to its own implementation module and must preserve the same public repository semantics.

## Contract invariants

- Depend only on lower-level public contracts required for stable value semantics; never depend on Compose, Room, `llama.cpp` implementation types or app state.
- Dataset, sample-set, evaluator-set, semantic-execution and run identities are immutable and content/configuration-derived.
- Canonical fingerprints use explicit field ordering and exact scalar representation; never depend on map or filesystem iteration order.
- Quality identity excludes the selected model and physical device so supported models can be compared on identical semantic work.
- Runtime compatibility adds device/runtime/tuning/load/warm-up identity without collapsing those dimensions into quality.
- Evaluator specs are declarative and bounded; they cannot name classes, scripts, URLs or executable code.
- Failure contracts use bounded typed codes rather than arbitrary backend exception text.
- Prompt, expected-answer and generated-answer content is not part of persistent run/result contracts.
- Persistence implementations must not invent alternate lifecycle, ordering, deletion or retention semantics.

## Validation

For evaluation contract/store iteration run:

```bash
./gradlew :evaluation:contracts:spotlessCheck \
  :evaluation:contracts:testDebugUnitTest \
  :evaluation:contracts:compileDebugKotlin \
  :evaluation:contracts:lintDebug \
  :evaluation:in-memory-store:testDebugUnitTest \
  :evaluation:in-memory-store:lintDebug
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
```

Because adding or changing the module list affects repository navigation and build configuration, also run the repository documentation/navigation guards and the applicable repository-wide gate before merge.
