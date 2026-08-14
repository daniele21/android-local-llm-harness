# Model Evaluation — Coding Agent Guide

This guide owns navigation and validation for model-evaluation implementation under `evaluation/`. Current progress belongs in [`../docs/model-evaluation/current-state.md`](../docs/model-evaluation/current-state.md).

## Read order

1. Read the repository [`../AGENTS.md`](../AGENTS.md).
2. Read [`../docs/model-evaluation/README.md`](../docs/model-evaluation/README.md) for capability routing.
3. Read only the focused model-evaluation workstream that owns the change.
4. Read runtime or observability specifications only when the change crosses those existing boundaries.

## Current ownership

- `evaluation/contracts` owns backend-independent dataset, evaluator, sampling, run, identity, compatibility and failure contracts plus deterministic canonical hashing.
- `evaluation/engine` owns evaluation-run lifecycle, single-run ownership, fake-friendly execution ports and controlled evaluation-only model resolution. It must not mutate ordinary app bindings or absorb scorer, persistence or Compose responsibilities.

Do not create dataset-store or persistence implementation modules until the corresponding workstream contains real behavior. New evaluation modules must have concrete ownership, tests and an explicit navigation entry before they are registered in Gradle.

## Contract invariants

- Depend only on lower-level public contracts required for stable value semantics; never depend on Compose, Room, `llama.cpp` implementation types or app state.
- Dataset, sample-set, evaluator-set, semantic-execution and run identities are immutable and content/configuration-derived.
- Canonical fingerprints use explicit field ordering and exact scalar representation; never depend on map or filesystem iteration order.
- Quality identity excludes the selected model and physical device so supported models can be compared on identical semantic work.
- Runtime compatibility adds device/runtime/tuning/load/warm-up identity without collapsing those dimensions into quality.
- Evaluator specs are declarative and bounded; they cannot name classes, scripts, URLs or executable code.
- Failure contracts use bounded typed codes rather than arbitrary backend exception text.
- Prompt, expected-answer and generated-answer content is not part of persistent run/result contracts.
- The engine executes exactly the ordered sample selection it receives; dataset parsing/sampling belong upstream.
- Evaluation-only model selection is explicit and read-only with respect to normal application binding/profile state.

## Validation

For contract-only iteration run:

```bash
./gradlew :evaluation:contracts:spotlessCheck \
  :evaluation:contracts:testDebugUnitTest \
  :evaluation:contracts:compileDebugKotlin \
  :evaluation:contracts:lintDebug
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
```

For engine work run the equivalent scoped checks for `:evaluation:engine` in addition to repository-wide `spotlessCheck`, `detekt` and `verifyNoModelArtifacts`.

Because adding or changing the module list affects repository navigation and build configuration, also run the repository documentation/navigation guards and the applicable repository-wide gate before merge.
