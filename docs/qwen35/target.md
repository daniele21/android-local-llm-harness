# Qwen3.5-only product target

Status: active
Document type: target-specification
Owner: qwen35
Canonical scope: qwen35.target
Read when: deciding whether a model, runtime feature or API behavior belongs in the Qwen3.5-only product envelope
Last reviewed: 2026-08-08

## Objective

Turn the Android Local LLM Harness into a product that is intentionally optimized, validated and safe-by-default for **Qwen3.5 dense 0.8B and 2B GGUF models**. This is the complete supported product envelope, not a preferred path beside a generic fallback.

For an application developer, the intended experience is:

```text
supported Qwen3.5 model + use case
  -> validated artifact
  -> Qwen3.5-aware prompt/thinking/sampling plan
  -> bounded context/runtime configuration
  -> llama.cpp execution
  -> typed stream/result + privacy-safe telemetry
```

The harness, not each consumer application, owns Qwen3.5-specific template semantics, sampling defaults, context policy, generation guards and Android tuning.

## Product focus versus contract neutrality

The product is Qwen3.5-only. Public request, binding, session, streaming, cancellation, result and telemetry contracts remain model-family neutral so the runtime and transport layers do not depend on Qwen implementation types.

Neutral contracts do not authorize another model family. Admission policy rejects unsupported artifacts before runtime preparation, and a future family requires an explicit target and ADR change. Public thinking intent is family-neutral; only the internal Qwen3.5 planner knows how it maps to `enable_thinking`.

## Supported envelope

### Models

- Qwen3.5 dense `0.8B`;
- Qwen3.5 dense `2B`;
- GGUF artifacts whose supported class is proven from structural metadata and, for catalog-managed artifacts, a matching trusted manifest;
- exact artifact SHA-256 remains the immutable identity.

The initial certification candidates are Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M. Other 0.8B/2B quantizations may become compatible or experimental, but certification is never inherited across digests or quantizations.

### Runtime

- Android `arm64-v8a`;
- CPU-first execution;
- repository-pinned and explicitly validated `llama.cpp`;
- text input and text generation;
- one active decode by default, preserving the current repository scheduling boundary.

### Generation

- non-thinking mode;
- model-family-neutral thinking intent mapped internally to Qwen3.5 template configuration;
- streaming and cooperative cancellation;
- `TEXT`, `JSON` and `JSON_SCHEMA` output modes already owned by the generation layer;
- Qwen3.5-aware sampling presets with explicit effective configuration;
- bounded generation guards with typed stop reasons.

## Explicit non-goals for this plan

The following must not be pulled into this plan unless the target is intentionally revised:

- Qwen3.5 `4B` or `9B`;
- Qwen3.5 MoE variants;
- Qwen3, Qwen2.5 or any non-Qwen3.5 family;
- image/video input or vision encoder support;
- tool calling / agent protocol parsing;
- Vulkan/GPU production support;
- speculative decoding or MTP execution;
- shared Binder/AIDL runtime;
- Capacitor integration;
- maximizing advertised model context length on phone.

Unsupported model families must fail explicitly rather than silently falling back to generic behavior.

## Legacy transition

The current catalog and installed inventory may contain models outside this envelope. The transition is non-destructive:

- unsupported releases stop being eligible for new installation or selection;
- unsupported bindings become invalid and require explicit rebinding;
- already installed bytes remain visible as legacy/unsupported until the user removes them;
- no migration, admission failure or runtime release deletes an installed GGUF.

Detailed states and acceptance criteria belong to [`workstreams/product-migration.md`](workstreams/product-migration.md).

## Product invariants

1. **Metadata over filename.** Architecture admission never depends on filename parsing.
2. **Exact identity.** Certification is attached to an artifact digest and reproducible backend/configuration evidence.
3. **Qwen3.5-aware by default.** Template, thinking and sampler semantics resolve in the harness.
4. **Mobile bounds first.** Context and runtime resources are chosen from approved Android tiers, not model-advertised maxima.
5. **Backend owns math.** `llama.cpp` owns Qwen3.5 kernels and model execution; Kotlin/JNI must not reimplement Gated DeltaNet or attention math.
6. **Capability-gated reuse.** Cache/session optimizations are enabled only after Qwen3.5 hybrid/recurrent behavior is validated.
7. **No silent substitution.** An unsupported artifact, mode or capability returns a typed failure.
8. **Evidence before certification.** Emulator or desktop success is insufficient for production Android compatibility claims.
9. **Neutral core, focused product.** Generic contracts preserve architecture but never act as a support fallback.
10. **Non-destructive retirement.** Legacy model bytes remain user-controlled even when they are no longer runnable.

## Success criteria

The transition is complete when:

- supported 0.8B and 2B reference artifacts pass metadata and backend compatibility checks;
- unsupported architectures are rejected before native preparation;
- thinking/non-thinking render through the correct Qwen3.5 template semantics without `/think` or `/nothink` hacks;
- recommended Qwen3.5 sampling baselines can be represented and overridden deterministically;
- anomalous repetition/thinking has bounded detection and typed termination;
- context, cache and reuse behavior is safe for the backend's Qwen3.5 hybrid/recurrent execution model;
- separate evidence-backed Android runtime profiles exist for 0.8B and 2B;
- tokenizer/template/output/streaming/cancellation golden and integration tests pass;
- representative physical-device memory, thermal and latency evidence passes the certification gate;
- catalog entries distinguish certified artifacts from merely compatible or unverified imports;
- no non-Qwen3.5 or unsupported-tier catalog entry, binding or imported artifact can reach runtime preparation;
- legacy installed artifacts remain visible and removable without being silently loaded or deleted.

Milestone sequencing is owned by [`roadmap.md`](roadmap.md).
