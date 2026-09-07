# Qwen3.5-only product target

Status: active
Document type: target-specification
Owner: qwen35
Canonical scope: qwen35.target
Read when: deciding whether a model, runtime feature or API behavior belongs in the Qwen3.5-only product envelope
Last reviewed: 2026-09-06

## Objective

Turn Harnex into a product intentionally optimized and validated for **Qwen3.5 dense 0.8B, 2B and reviewed 4B 4-bit GGUF models**.

For an application developer, the intended experience is:

```text
curated Qwen3.5 model + use case
  -> verified catalog artifact
  -> Qwen3.5-aware prompt/thinking/sampling plan
  -> bounded context/runtime configuration
  -> llama.cpp execution
  -> typed stream/result + privacy-safe telemetry
```

The harness, not each consumer application, owns Qwen3.5-specific template semantics, sampling defaults, context policy, generation guards and Android tuning.

## Closed model surface

The product model set is closed and repository-reviewed. Users can download and select the models exposed by the built-in catalog, but cannot add arbitrary GGUF files, URLs, model families or architectures.

The product therefore does not need a legacy-model compatibility layer, generic family fallback, arbitrary-import admission policy or unsupported-family presentation state. Cases outside the curated set are removed from product code rather than represented as runtime choices.

Public request, binding, session, streaming, cancellation, result and telemetry contracts remain model-family neutral so runtime and transport layers stay decoupled from Qwen implementation types. Neutral contracts are architectural reuse, not a product extension point.

## Supported envelope

### Models

- Qwen3.5 dense `0.8B` curated releases;
- Qwen3.5 dense `2B` curated releases;
- Qwen3.5 dense `4B` from `unsloth/Qwen3.5-4B-GGUF`, restricted to the reviewed 4-bit set `IQ4_XS`, `IQ4_NL`, `Q4_0`, `Q4_1`, `Q4_K_S`, `Q4_K_M`, `UD-Q4_K_XL`;
- exact SHA-256 identity for every downloadable artifact;
- trusted catalog metadata reviewed with the application profile.

The established certification candidates remain Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M. The 4B tier is admitted as a product candidate under ADR 0019, with `UD-Q4_K_XL` preferred for initial validation because Unsloth uses it in its `llama.cpp` examples. No 4B artifact is certified until exact physical-device evidence passes.

### Runtime

- Android `arm64-v8a`;
- CPU-first execution;
- repository-pinned and explicitly validated `llama.cpp`;
- text input and text generation;
- one active decode by default, preserving the current scheduling boundary;
- separate evidence identity for 0.8B, 2B and 4B tiers.

For the unmeasured 4B tier, catalog compatibility starts conservatively at 8 GB minimum total RAM and 12 GB recommended RAM. Runtime context remains bounded by Harnex Android tiers rather than model-advertised maximum context.

### Generation

- non-thinking mode;
- family-neutral thinking intent mapped internally to Qwen3.5 template configuration;
- streaming and cooperative cancellation;
- `TEXT`, `JSON` and `JSON_SCHEMA` output modes;
- Qwen3.5-aware sampling presets with explicit effective configuration;
- bounded generation guards with typed stop reasons;
- 4B sampler defaults derived from Unsloth's Qwen3.5 recommendations, without changing established 0.8B/2B defaults.

For Qwen3.5 4B, Harnex maps its presets as follows:

| Preset intent | Thinking | temperature | top_p | top_k | min_p | presence_penalty | repeat_penalty |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Fast / JSON | disabled | 0.7 | 0.8 | 20 | 0 | 1.5 | 1.0 |
| Quality / reasoning | disabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| Thinking | enabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| Precise / coding | enabled | 0.6 | 0.95 | 20 | 0 | 0 | 1.0 |

## Explicit non-goals

The following remain outside this plan:

- user-facing manual GGUF import;
- arbitrary remote model URLs or third-party catalog extension;
- compatibility with user-supplied GGUF artifacts;
- Qwen3.5 `9B` or larger dense tiers;
- Qwen3.5 4B quantizations outside the reviewed 4-bit set;
- Qwen3.5 MoE variants;
- Qwen3, Qwen2.5 or non-Qwen3.5 families;
- image/video input or vision encoder support;
- tool calling / agent protocol parsing;
- Vulkan/GPU production support;
- speculative decoding or MTP execution;
- shared Binder/AIDL runtime as a model-family extension point;
- Capacitor integration;
- maximizing advertised model context length on phone.

Developer-only device validation may still inject an exact test artifact into an isolated test application. That path is not exposed as a consumer model-import capability.

## Product invariants

1. **Closed catalog.** Product model choice comes only from repository-reviewed Qwen3.5 0.8B/2B releases and the ADR-0019 4B 4-bit set.
2. **Exact identity.** Every artifact is verified by immutable SHA-256 identity before installation.
3. **Qwen3.5-aware by default.** Template, thinking and sampler semantics resolve in the harness.
4. **Mobile bounds first.** Context and runtime resources are chosen from approved Android tiers, not model-advertised maxima.
5. **Backend owns math.** `llama.cpp` owns Qwen3.5 kernels and model execution; Kotlin/JNI does not reimplement model math.
6. **Capability-gated reuse.** Cache/session optimizations are enabled only after hybrid/recurrent behavior is validated.
7. **No generic fallback.** Public contract neutrality never creates another product model path.
8. **Evidence before certification.** Emulator or desktop success is insufficient for production Android claims.
9. **No legacy layer.** Retired product cases are deleted rather than preserved as selectable or representable model states.
10. **Catalog acquisition only.** Verified catalog download/install is the only consumer model acquisition path.
11. **4B means reviewed 4-bit only.** No other Qwen3.5 4B quantization is product-eligible without another explicit product decision.

## Success criteria

The target remains truthful when:

- the executable catalog contains only curated Qwen3.5 dense 0.8B/2B releases plus the exact reviewed 4B 4-bit set;
- consumer-facing manual model import is absent;
- exact catalog artifacts pass metadata/backend compatibility before any certification claim;
- thinking/non-thinking render through correct Qwen3.5 template semantics without `/think` or `/nothink` hacks;
- recommended Qwen3.5 sampling baselines can be represented and overridden deterministically;
- anomalous repetition/thinking has bounded detection and typed termination;
- context, cache and reuse behavior is safe for the backend's Qwen3.5 hybrid/recurrent execution model;
- separate Android runtime/evidence identities exist for 0.8B, 2B and 4B;
- tokenizer/template/output/streaming/cancellation golden and integration tests pass;
- representative physical-device memory, thermal and latency evidence passes before an exact artifact is certified;
- certification is attached only to exact curated artifacts and validated backend/runtime evidence.

Milestone sequencing is owned by [`roadmap.md`](roadmap.md). ADR 0011 remains the family/product-boundary decision and [ADR 0019](../adr/0019-qwen35-4b-four-bit-product-support.md) extends its tier envelope.
