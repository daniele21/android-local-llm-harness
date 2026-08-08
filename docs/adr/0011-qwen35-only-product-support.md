# ADR 0011: Qwen3.5-only product support over family-neutral contracts

- Status: Accepted
- Date: 2026-08-08

## Context

The repository was designed as a model-aware, multi-model GGUF harness. Its catalog and test fixtures currently include several model families and Qwen variants. The active product direction is narrower: optimize, validate and support only Qwen3.5 dense 0.8B and 2B on Android ARM64 through the pinned `llama.cpp` backend.

Making every core contract Qwen-specific would couple public lifecycle APIs to one model family and weaken the existing separation between application bindings, runtime orchestration and backend execution. Leaving the product envelope generic, however, would permit unsupported artifacts to reach preparation and would make certification claims ambiguous.

## Decision

The supported product envelope is Qwen3.5 dense 0.8B and 2B GGUF, Android `arm64-v8a`, CPU-first, text generation only. Other families, Qwen generations, Qwen3.5 tiers and variants are outside the supported envelope and fail through typed admission or preparation errors. There is no generic fallback or silent model substitution.

Core public contracts remain model-family neutral. Application/use-case binding, immutable digest identity, sessions, streaming, cancellation, output constraints, telemetry and backend interfaces must not expose Qwen or `llama.cpp` implementation types. Public intent such as thinking enabled or disabled is neutral; the Qwen3.5 policy maps it to template arguments and sampler behavior internally.

“Multi-model” in ADR 0010 now means explicit selection among supported Qwen3.5 artifacts, quantizations and profiles. It no longer promises multi-family product support. The template trust and generation-planning decisions in ADR 0009 remain, but the active family policy is owned by the harness and selected through reviewed local profiles rather than supplied by consumers or the remote catalog.

The current multi-family catalog is migration input, not the target state. Migration must:

- stop offering non-Qwen3.5, Qwen3.5 4B and other unsupported entries for new selection or installation;
- invalidate unsupported application bindings explicitly;
- retain already installed GGUF bytes until the user removes them;
- present retained artifacts as legacy and unsupported without deleting or loading them;
- preserve catalog availability, runtime compatibility and evidence certification as separate concepts.

Initial certification candidates are the exact Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M artifacts selected by the certification plan. Other supported-tier quantizations may be compatible or experimental, but they are not certified until their exact evidence matrix passes.

## Consequences

- Product behavior, catalog eligibility and certification become unambiguous and Qwen3.5-focused.
- Existing neutral lifecycle and transport boundaries remain reusable and independently testable.
- Admission, prompting, generation guards and tuning require Qwen3.5 policy, but execution mechanics stay with their existing domain owners.
- Existing catalog entries, bindings, fixtures and UI states require an explicit non-destructive migration.
- A future family or Qwen3.5 tier requires a new product decision and evidence plan; it cannot enter through a generic fallback.

## Alternatives considered

### Make public contracts Qwen3.5-specific

Rejected because model-family details do not belong in transport-safe lifecycle contracts and would force consumers to understand template and backend semantics.

### Keep a generic supported-family fallback

Rejected because it undermines the Qwen3.5-only support claim and allows unvalidated models to bypass specialized policy.

### Delete legacy installed artifacts during migration

Rejected because support-policy changes must not destroy user-managed model bytes. Unsupported artifacts remain visible and explicitly removable.
