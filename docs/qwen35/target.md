# Qwen3.5 specialization target

Status: active
Document type: target-specification
Owner: qwen35
Canonical scope: qwen35.target
Read when: deciding whether a model, runtime feature or API behavior belongs in the Qwen3.5 specialization
Last reviewed: 2026-08-07

## Objective

Turn the Android Local LLM Harness into a runtime that is intentionally optimized, validated and safe-by-default for **Qwen3.5 dense 0.8B and 2B GGUF models**, instead of treating Qwen3.5 as one generic model family among many.

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

## Supported envelope

### Models

- Qwen3.5 dense `0.8B`;
- Qwen3.5 dense `2B`;
- GGUF artifacts whose architecture and compatibility are verified from metadata plus trusted catalog/manifest identity;
- exact artifact SHA-256 remains the immutable identity.

### Runtime

- Android `arm64-v8a`;
- CPU-first execution;
- repository-pinned and explicitly validated `llama.cpp`;
- text input and text generation;
- one active decode by default, preserving the current repository scheduling boundary.

### Generation

- non-thinking mode;
- thinking mode controlled through typed Qwen3.5 template configuration;
- streaming and cooperative cancellation;
- `TEXT`, `JSON` and `JSON_SCHEMA` output modes already owned by the generation layer;
- Qwen3.5-aware sampling presets with explicit effective configuration;
- bounded generation guards with typed stop reasons.

## Explicit non-goals for this plan

The following must not be pulled into this specialization unless the target is intentionally revised:

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

## Product invariants

1. **Metadata over filename.** Architecture admission never depends on filename parsing.
2. **Exact identity.** Certification is attached to an artifact digest and reproducible backend/configuration evidence.
3. **Qwen3.5-aware by default.** Template, thinking and sampler semantics resolve in the harness.
4. **Mobile bounds first.** Context and runtime resources are chosen from approved Android tiers, not model-advertised maxima.
5. **Backend owns math.** `llama.cpp` owns Qwen3.5 kernels and model execution; Kotlin/JNI must not reimplement Gated DeltaNet or attention math.
6. **Capability-gated reuse.** Cache/session optimizations are enabled only after Qwen3.5 hybrid/recurrent behavior is validated.
7. **No silent substitution.** An unsupported artifact, mode or capability returns a typed failure.
8. **Evidence before certification.** Emulator or desktop success is insufficient for production Android compatibility claims.

## Success criteria

The specialization is complete when:

- supported 0.8B and 2B reference artifacts pass metadata and backend compatibility checks;
- unsupported architectures are rejected before native preparation;
- thinking/non-thinking render through the correct Qwen3.5 template semantics without `/think` or `/nothink` hacks;
- recommended Qwen3.5 sampling baselines can be represented and overridden deterministically;
- anomalous repetition/thinking has bounded detection and typed termination;
- context, cache and reuse behavior is safe for the backend's Qwen3.5 hybrid/recurrent execution model;
- separate evidence-backed Android runtime profiles exist for 0.8B and 2B;
- tokenizer/template/output/streaming/cancellation golden and integration tests pass;
- representative physical-device memory, thermal and latency evidence passes the certification gate;
- catalog entries distinguish certified artifacts from merely compatible or unverified imports.

Milestone sequencing is owned by [`roadmap.md`](roadmap.md).
