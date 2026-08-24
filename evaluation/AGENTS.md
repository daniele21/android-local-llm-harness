# Model Evaluation — Coding Agent Guide

This guide owns navigation and validation for model-evaluation implementation under `evaluation/`. Current progress belongs in [`../docs/model-evaluation/current-state.md`](../docs/model-evaluation/current-state.md).

## Read order

1. Read the repository [`../AGENTS.md`](../AGENTS.md).
2. Read [`../docs/model-evaluation/README.md`](../docs/model-evaluation/README.md) for capability routing.
3. Read only the focused model-evaluation workstream that owns the change.
4. Read runtime or observability specifications only when the change crosses those existing boundaries.

## Current ownership

- `evaluation/contracts` owns backend-independent dataset, evaluator, sampling, run, identity, compatibility, persistence-interface and failure contracts plus deterministic canonical hashing.
- `evaluation/comparison` owns typed quality/runtime compatibility assessment. It does not query persistence or calculate numeric deltas.
- `evaluation/datasets` owns bounded canonical JSONL parsing, pack-level semantic validation and ordered content-digest verification. It does not install packs, sample cases or execute inference.
- `evaluation/dataset-adapter` owns production adaptation from registry-published immutable dataset packs to runner preflight/case-definition/category access. It must re-verify the canonical pack before exposing cases and must not execute inference or own installation.
- `evaluation/evaluators` owns the versioned evaluator registry, deterministic scorer implementations and quality aggregation. Registry entries are declarative and fail closed; scorer-specific work stays inside this module.
- `evaluation/engine` owns fake-friendly evaluation lifecycle orchestration and controlled resolution of one explicitly selected supported installed model. It does not own production dataset installation, persistence, telemetry storage or UI state.
- `evaluation/runtime-adapter` owns production composition from the backend-neutral `EvaluationBatchExecutionPort` to the runtime-only bounded evaluation-batch client, including isolated session lifecycle, exact result attribution, timeout/cancellation and one-case serial fallback. It must not depend on a concrete backend such as `llama.cpp`.
- `evaluation/in-memory-store` owns the deterministic privacy-safe in-memory implementation of `EvaluationResultRepository`. It is the parity reference for durable stores: run configuration is immutable, lifecycle transitions are validated, case results stay bounded to the selected sample set, history ordering is deterministic, active runs cannot be deleted, and retention applies only to terminal runs.
- `evaluation/persistence` owns runner-to-repository lifecycle persistence orchestration. It creates and advances privacy-safe run summaries around `EvaluationEngine` without making storage part of the engine; durable repository implementation remains a separate module and per-case persistence remains separately owned.
- `evaluation/room-store` owns the Room-specific privacy-safe evaluation-history schema. DAO, repository and database wiring remain separate P-04 work, and telemetry Room ownership stays under `observability/room-store`.

Do not add Room or other durable storage behavior to the in-memory module. Durable persistence belongs to its own implementation module and must preserve the same public repository semantics. New evaluation modules must have concrete ownership, tests and an explicit navigation entry before they are registered in Gradle.

## Contract invariants

- Depend only on lower-level public contracts required for stable value semantics; never make `evaluation/contracts` depend on Compose, Room or `llama.cpp` implementation types.
- Dataset, sample-set, evaluator-set, semantic-execution and run identities are immutable and content/configuration-derived.
- Canonical fingerprints use explicit field ordering and exact scalar representation; never depend on map or filesystem iteration order.
- Dataset JSONL parsing is bounded and fail-closed: malformed UTF-8, CRLF, missing LF termination, duplicate/unknown fields, unsupported schemas and non-integral integer fields are rejected rather than normalized silently.
- Dataset content digests are calculated over canonical ordered case content, not filesystem order, raw source formatting or map iteration order.
- Dataset prompts, expected answers and source metadata remain dataset content and must not leak into ordinary telemetry, logs or diagnostic export.
- Quality identity excludes the selected model and physical device so supported models can be compared on identical semantic work.
- Runtime compatibility adds device/runtime/tuning/load/warm-up identity without collapsing those dimensions into quality.
- Evaluator specs are declarative and bounded; they cannot name classes, scripts, URLs or executable code.
- Failure contracts use bounded typed codes rather than arbitrary backend exception text.
- Prompt, expected-answer and generated-answer content is not part of persistent run/result contracts.
- `evaluation/engine` may resolve only curated product-supplied model profiles and verified local artifacts; it must not create or mutate ordinary application model bindings.
- `evaluation/dataset-adapter` may read only registry-published packs and must verify manifest identity, case shape and canonical content digest before returning cases or categories.
- R-01 cooperative cancellation between phases/cases is only a lifecycle foundation. Active decode cancellation, timeout cleanup and unattempted-case accounting remain R-08/R-09 after isolated per-case runtime ownership exists.
- Persistence implementations must not invent alternate lifecycle, ordering, deletion or retention semantics.
- `evaluation/persistence` may observe and persist lifecycle/progress, but it must not absorb Room ownership, runner execution logic or prompt/output content.

## Validation

For contract-only iteration run:

```bash
./gradlew :evaluation:contracts:spotlessCheck \
  :evaluation:contracts:testDebugUnitTest \
  :evaluation:contracts:compileDebugKotlin \
  :evaluation:contracts:lintDebug
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
```

For dataset work run:

```bash
./gradlew :evaluation:datasets:testDebugUnitTest \
  :evaluation:datasets:compileDebugKotlin \
  :evaluation:datasets:lintDebug
./gradlew --no-configuration-cache spotlessCheck detekt verifyNoModelArtifacts
```

For dataset-to-runner adapter work run:

```bash
./gradlew :evaluation:dataset-adapter:testDebugUnitTest \
  :evaluation:dataset-adapter:compileDebugKotlin \
  :evaluation:dataset-adapter:lintDebug
./gradlew --no-configuration-cache spotlessCheck detekt verifyNoModelArtifacts
```

For evaluator work run the equivalent scoped checks for `:evaluation:evaluators` in addition to repository-wide `spotlessCheck`, `detekt` and `verifyNoModelArtifacts`.

For engine work run the equivalent scoped checks for `:evaluation:engine`, including unit tests and lint. Runner tests must use fake preflight/model-preparation/case-execution ports until the owning runner tasks explicitly integrate production dataset/runtime boundaries.

For runtime-adapter work run `:evaluation:runtime-adapter:compileDebugKotlin`, `:evaluation:runtime-adapter:testDebugUnitTest` and `:evaluation:runtime-adapter:lintDebug` together with repository-wide formatting/static-analysis/model-artifact and navigation guards. The adapter may depend on runtime-core but never on a concrete backend.

For in-memory persistence work run `:evaluation:in-memory-store:testDebugUnitTest` and `:evaluation:in-memory-store:lintDebug` together with repository-wide formatting/static-analysis/model-artifact guards.

For lifecycle persistence work run `:evaluation:persistence:compileDebugKotlin`, `:evaluation:persistence:testDebugUnitTest` and `:evaluation:persistence:lintDebug` together with repository-wide formatting/static-analysis/model-artifact and navigation guards.

For Room-schema/comparison work run the equivalent compile, unit-test and lint tasks for `:evaluation:room-store` and `:evaluation:comparison`, then the repository-wide formatting/static-analysis/model-artifact and navigation guards.

Because adding or changing the module list affects repository navigation and build configuration, also run the repository documentation/navigation guards, CI-scope script tests and the applicable repository-wide gate before merge.
