# Model Evaluation — Coding Agent Guide

This guide owns navigation and validation for model-evaluation implementation under `evaluation/`. Current progress belongs in [`../docs/model-evaluation/current-state.md`](../docs/model-evaluation/current-state.md).

## Read order

1. Read the repository [`../AGENTS.md`](../AGENTS.md).
2. Read [`../docs/model-evaluation/README.md`](../docs/model-evaluation/README.md) for capability routing.
3. Read only the focused model-evaluation workstream that owns the change.
4. Read runtime or observability specifications only when the change crosses those existing boundaries.

## Current ownership

- `evaluation/contracts` owns backend-independent dataset, evaluator, sampling, run, identity, compatibility, persistence-interface and failure contracts plus deterministic canonical hashing.
- `evaluation/evaluators` owns the versioned evaluator registry, deterministic scorer implementations and quality aggregation. Registry entries are declarative and fail closed; scorer-specific work stays inside this module.
- `evaluation/engine` owns fake-friendly evaluation lifecycle orchestration and controlled resolution of one explicitly selected supported installed model. It does not own production dataset installation, persistence, telemetry storage or UI state.

Do not create dataset-store or persistence implementation modules until the corresponding workstream contains real behavior. New evaluation modules must have concrete ownership, tests and an explicit navigation entry before they are registered in Gradle.

## Contract invariants

- Depend only on lower-level public contracts required for stable value semantics; never make `evaluation/contracts` depend on Compose, Room or `llama.cpp` implementation types.
- Dataset, sample-set, evaluator-set, semantic-execution and run identities are immutable and content/configuration-derived.
- Canonical fingerprints use explicit field ordering and exact scalar representation; never depend on map or filesystem iteration order.
- Quality identity excludes the selected model and physical device so supported models can be compared on identical semantic work.
- Runtime compatibility adds device/runtime/tuning/load/warm-up identity without collapsing those dimensions into quality.
- Evaluator specs are declarative and bounded; they cannot name classes, scripts, URLs or executable code.
- Failure contracts use bounded typed codes rather than arbitrary backend exception text.
- Prompt, expected-answer and generated-answer content is not part of persistent run/result contracts.
- `evaluation/engine` may resolve only curated product-supplied model profiles and verified local artifacts; it must not create or mutate ordinary application model bindings.
- R-01 cooperative cancellation between phases/cases is only a lifecycle foundation. Active decode cancellation, timeout cleanup and unattempted-case accounting remain R-08/R-09 after isolated per-case runtime ownership exists.

## Validation

For contract-only iteration run:

```bash
./gradlew :evaluation:contracts:spotlessCheck \
  :evaluation:contracts:testDebugUnitTest \
  :evaluation:contracts:compileDebugKotlin \
  :evaluation:contracts:lintDebug
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
```

For evaluator work run the equivalent scoped checks for `:evaluation:evaluators` in addition to repository-wide `spotlessCheck`, `detekt` and `verifyNoModelArtifacts`.

For engine work run the equivalent scoped checks for `:evaluation:engine`, including unit tests and lint. Runner tests must use fake preflight/model-preparation/case-execution ports until the owning runner tasks explicitly integrate production dataset/runtime boundaries.

Because adding or changing the module list affects repository navigation and build configuration, also run the repository documentation/navigation guards, CI-scope script tests and the applicable repository-wide gate before merge.
