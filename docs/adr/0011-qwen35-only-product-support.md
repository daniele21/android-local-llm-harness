# ADR 0011: Qwen3.5-only product support over family-neutral contracts

- Status: Accepted
- Date: 2026-08-08

## Context

The repository was designed as a model-aware, multi-model GGUF harness. Its catalog and test fixtures currently include several model families and Qwen variants. The active product direction is intentionally narrower: optimize, validate and support only Qwen3.5 dense 0.8B and 2B on Android ARM64 through the pinned `llama.cpp` backend.

The product does not need to act as a generic local-model manager. Users will choose from a repository-reviewed catalog of known Qwen3.5 artifacts rather than import arbitrary GGUF files.

Making every core contract Qwen-specific would still couple public lifecycle APIs to one family and weaken the existing separation between application bindings, runtime orchestration and backend execution. The product model surface can be closed without sacrificing that internal architectural neutrality.

## Decision

The supported product envelope is Qwen3.5 dense 0.8B and 2B GGUF, Android `arm64-v8a`, CPU-first, text generation only.

Product model acquisition is closed:

- users can download and select only releases present in the built-in curated catalog;
- consumer-facing manual GGUF import is removed;
- arbitrary model URLs, families, architectures and local artifacts are not product inputs;
- non-Qwen3.5 and unsupported Qwen3.5 tiers are removed from the executable product catalog and product fixtures rather than retained as supported edge cases;
- no legacy-model compatibility or presentation layer is maintained.

Core public lifecycle contracts remain model-family neutral. Application/use-case binding, immutable digest identity, sessions, streaming, cancellation, output constraints, telemetry and backend interfaces must not expose Qwen or `llama.cpp` implementation types. Public intent such as thinking enabled or disabled is neutral; Qwen3.5 policy maps it to template arguments and sampler behavior internally.

“Multi-model” in ADR 0010 now means explicit selection among curated Qwen3.5 0.8B/2B artifacts, quantizations and profiles. It no longer promises multi-family product support or user-supplied models.

The current multi-family bootstrap and manual-import product paths are implementation debt to delete. They are not migration inputs that require compatibility preservation. Developer-only validation tooling may continue to inject exact test artifacts into isolated test applications when needed for device evidence; that path must remain outside consumer APIs.

Initial certification candidates are the exact Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M artifacts selected by the certification plan. Other curated 0.8B/2B quantizations may be experimental, but they are not certified until their exact evidence matrix passes.

## Consequences

- Product behavior and model choice become intentionally small and deterministic.
- Existing neutral lifecycle and transport boundaries remain reusable and independently testable.
- Generic manual-import and multi-family product code can be removed instead of expanded with unsupported/legacy states.
- Verified catalog download/install remains the sole consumer acquisition path.
- Compatibility work validates exact curated artifacts and the pinned backend rather than attempting to classify arbitrary user models.
- A future family, tier or user-import capability requires a new product decision; it cannot enter through a generic fallback.

## Alternatives considered

### Make public contracts Qwen3.5-specific

Rejected because model-family details do not belong in transport-safe lifecycle contracts and would force consumers to understand template and backend semantics.

### Keep generic user model import with rejection logic

Rejected because it creates code and UX for cases the product does not intend to support. A closed curated model set is simpler and easier to validate.

### Maintain legacy installed-model states

Rejected because there is no product requirement to preserve a generic model-manager migration path. Retired model cases are removed from the product surface rather than represented indefinitely.
