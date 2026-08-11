# ADR 0010: Model-aware runtime with embedded-first deployment

- Status: Accepted
- Date: 2026-08-01

Support-envelope amendment: [ADR 0011](0011-qwen35-only-product-support.md) narrows “multi-model” product support to explicitly selected Qwen3.5 dense 0.8B/2B artifacts and profiles. The binding and embedded-first decisions below remain in force.

## Context

Applications may require different GGUF models, quantizations, prompts and context configurations. The first implementation must be embedded in each Android application, while a later shared host should deduplicate artifacts and coordinate memory.

## Decision

The runtime is model-aware and multi-model, but it does not silently select models. `applicationId + useCaseId` resolves to one explicit `AppModelBinding`, `UseCaseProfile`, `GgufModelProfile` and artifact digest.

The embedded runtime uses an in-process transport. Public contracts remain transport-safe so that Binder can replace the transport later without changing product-level model binding.

## Consequences

- Different applications can intentionally use different models.
- Identical model digests can be deduplicated by the future shared host.
- Fallbacks must be declared and observable.
- Native pointers and backend-specific objects cannot appear in public contracts.
- The embedded and shared deployments must execute the same data plane.

## Alternatives considered

- Automatic model selection by the runtime: rejected because it hides product behavior and complicates reproducibility.
- Shared service from the first release: rejected because Android lifecycle, IPC and installation complexity would delay validation of the inference data plane.
