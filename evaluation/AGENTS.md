# Model Evaluation — Coding Agent Guide

This guide owns navigation and validation for model-evaluation implementation under `evaluation/`. Current progress belongs in [`../docs/model-evaluation/current-state.md`](../docs/model-evaluation/current-state.md).

## Read order

1. Read the repository [`../AGENTS.md`](../AGENTS.md).
2. Read [`../docs/model-evaluation/README.md`](../docs/model-evaluation/README.md) for capability routing.
3. Read only the focused model-evaluation workstream that owns the change.
4. Read runtime or observability specifications only when the change crosses those existing boundaries.

## Current ownership

- `evaluation/contracts` owns backend-independent dataset, evaluator, sampling, run, identity, compatibility, persistence-interface and failure contracts plus deterministic canonical hashing.
- `evaluation/comparison` owns typed quality/runtime compatibility assessment and compatibility-gated numeric deltas. It does not query persistence.
- `evaluation/datasets` owns bounded canonical JSONL parsing, semantic validation, ordered content-digest verification, app-private installation, discovery/import, sampling and protected deletion. It does not execute inference or own runner lifecycle.
- `evaluation/dataset-adapter` owns the concrete production bridge from a published installed dataset pack to the runner's dataset-preflight and case-definition ports. It may depend on `evaluation/datasets` and `evaluation/engine`, but neither lower-level module may depend back on the adapter. It must reuse canonical registry/parser/digest behavior rather than implement an alternate dataset path.
- `evaluation/evaluators` owns the versioned evaluator registry, deterministic scorer implementations and quality aggregation. Registry entries are declarative and fail closed; scorer-specific work stays inside this module.
- `evaluation/engine` owns fake-friendly evaluation lifecycle orchestration, controlled resolution of one explicitly selected supported installed model, scored execution, timeout/cancellation and run aggregation domain logic. It does not own production dataset storage, durable persistence, telemetry storage or UI state.
- `evaluation/in-memory-store` owns the deterministic privacy-safe in-memory implementation of `EvaluationResultRepository`. It is the parity reference for durable stores: run configuration is immutable, lifecycle transitions are validated, case results stay bounded to the selected sample set, history ordering is deterministic, active runs cannot be deleted, and retention applies only to terminal runs.
- `evaluation/persistence` owns runner-to-repository lifecycle and completed-case persistence orchestration. It does not own Room or runner execution logic.
- `evaluation/room-store` owns the Room-specific privacy-safe evaluation-history schema plus the durable repository/database implementation once its owning persistence task is integrated. Telemetry Room ownership stays under `observability/room-store`.

Do not add Room or other durable storage behavior to the in-memory module. Durable persistence belongs to its own implementation module and must preserve the same public repository semantics. New evaluation modules must have concrete ownership, tests and an explicit navigation entry before they are registered in Gradle.

## Contract invariants

- Depend only on lower-level public contracts required for stable value semantics; never make `evaluation/contracts` depend on Compose, Room or `llama.cpp` implementation types.
- Dataset, sample-set, evaluator-set, semantic-execution and run identities are immutable and content/configuration-derived.
- Canonical fingerprints use explicit field ordering and exact scalar representation; never depend on map or filesystem iteration order.
- Dataset JSONL parsing is bounded and fail-closed: malformed UTF-8, CRLF, missing LF termination, duplicate/unknown fields, unsupported schemas and non-integral integer fields are rejected rather than normalized silently.
- Dataset content digests are calculated over canonical ordered case content, not filesystem order, raw source formatting or map iteration order.
- Dataset prompts, expected answers and source metadata remain dataset content and must not leak into ordinary telemetry, logs or diagnostic export.
- Production dataset execution must resolve only registry-published packs, verify exact dataset identity/content, and reuse the canonical parser. Never load arbitrary paths directly in the engine.
- Quality identity excludes the selected model and physical device so supported models can be compared on identical semantic work.
- Runtime compatibility adds device/runtime/tuning/load/warm-up identity without collapsing those dimensions into quality.
- Evaluator specs are declarative and bounded; they cannot name classes, scripts, URLs or executable code.
- Failure contracts use bounded typed codes rather than arbitrary backend exception text.
- Prompt, expected-answer and generated-answer content is not part of persistent run/result contracts.
- `evaluation/engine` may resolve only curated product-supplied model profiles and verified local artifacts; it must not create or mutate ordinary application model bindings.
- Active run cancellation must propagate through evaluation-owned case execution to the normal generation handle while preserving external coroutine-cancellation semantics.
- Persistence implementations must not invent alternate lifecycle, ordering, deletion or retention semantics.
- `evaluation/persistence` may observe and persist lifecycle/progress/case outcomes, but it must not absorb Room ownership, runner execution logic or prompt/output content.

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

For dataset/runner bridge work run:

```bash
./gradlew :evaluation:dataset-adapter:testDebugUnitTest \
  :evaluation:dataset-adapter:compileDebugKotlin \
  :evaluation:dataset-adapter:lintDebug
./gradlew --no-configuration-cache spotlessCheck detekt verifyNoModelArtifacts
```

For evaluator work run the equivalent scoped checks for `:evaluation:evaluators` in addition to repository-wide `spotlessCheck`, `detekt` and `verifyNoModelArtifacts`.

For engine work run the equivalent scoped checks for `:evaluation:engine`, including unit tests and lint. Pure lifecycle/unit tests may use fake preflight/model-preparation/case-execution ports; production dataset execution must enter through the dedicated adapter boundary rather than filesystem logic inside the engine.

For in-memory persistence work run `:evaluation:in-memory-store:testDebugUnitTest` and `:evaluation:in-memory-store:lintDebug` together with repository-wide formatting/static-analysis/model-artifact guards.

For lifecycle persistence work run `:evaluation:persistence:compileDebugKotlin`, `:evaluation:persistence:testDebugUnitTest` and `:evaluation:persistence:lintDebug` together with repository-wide formatting/static-analysis/model-artifact and navigation guards.

For Room-schema/comparison work run the equivalent compile, unit-test and lint tasks for `:evaluation:room-store` and `:evaluation:comparison`, then the repository-wide formatting/static-analysis/model-artifact and navigation guards.

Because adding or changing the module list affects repository navigation and build configuration, also run the repository documentation/navigation guards, CI-scope script tests and the applicable repository-wide gate before merge.
