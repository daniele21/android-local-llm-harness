# Qwen3.5-only product target

Status: active
Document type: target-specification
Owner: qwen35
Canonical scope: qwen35.target
Read when: deciding whether a model, runtime feature or API behavior belongs in the Qwen3.5-only product envelope
Last reviewed: 2026-08-08

## Objective

Turn the Android Local LLM Harness into a product intentionally optimized and validated for **Qwen3.5 dense 0.8B and 2B GGUF models**.

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
- exact SHA-256 identity for every downloadable artifact;
- trusted catalog metadata reviewed with the application profile.

The initial certification candidates are Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M. Other curated 0.8B/2B quantizations may be used experimentally, but certification is never inherited across digests or quantizations.

### Runtime

- Android `arm64-v8a`;
- CPU-first execution;
- repository-pinned and explicitly validated `llama.cpp`;
- text input and text generation;
- one active decode by default, preserving the current scheduling boundary.

### Generation

- non-thinking mode;
- family-neutral thinking intent mapped internally to Qwen3.5 template configuration;
- streaming and cooperative cancellation;
- `TEXT`, `JSON` and `JSON_SCHEMA` output modes;
- Qwen3.5-aware sampling presets with explicit effective configuration;
- bounded generation guards with typed stop reasons.

## Explicit non-goals

The following are outside this plan:

- user-facing manual GGUF import;
- arbitrary remote model URLs or third-party catalog extension;
- compatibility with user-supplied GGUF artifacts;
- Qwen3.5 `4B` or `9B`;
- Qwen3.5 MoE variants;
- Qwen3, Qwen2.5 or non-Qwen3.5 families;
- image/video input or vision encoder support;
- tool calling / agent protocol parsing;
- Vulkan/GPU production support;
- speculative decoding or MTP execution;
- shared Binder/AIDL runtime;
- Capacitor integration;
- maximizing advertised model context length on phone.

Developer-only device validation may still inject an exact test artifact into an isolated test application. That path is not exposed as a consumer model-import capability.

## Product invariants

1. **Closed catalog.** Product model choice comes only from repository-reviewed Qwen3.5 0.8B/2B releases.
2. **Exact identity.** Every artifact is verified by immutable SHA-256 identity before installation.
3. **Qwen3.5-aware by default.** Template, thinking and sampler semantics resolve in the harness.
4. **Mobile bounds first.** Context and runtime resources are chosen from approved Android tiers, not model-advertised maxima.
5. **Backend owns math.** `llama.cpp` owns Qwen3.5 kernels and model execution; Kotlin/JNI does not reimplement model math.
6. **Capability-gated reuse.** Cache/session optimizations are enabled only after hybrid/recurrent behavior is validated.
7. **No generic fallback.** Public contract neutrality never creates another product model path.
8. **Evidence before certification.** Emulator or desktop success is insufficient for production Android claims.
9. **No legacy layer.** Retired product cases are deleted rather than preserved as selectable or representable model states.
10. **Catalog acquisition only.** Verified catalog download/install is the only consumer model acquisition path.

## Success criteria

The Qwen3.5-only product target is complete when:

- the executable catalog contains only curated Qwen3.5 dense 0.8B/2B releases;
- consumer-facing manual model import is removed;
- exact 0.8B and 2B reference artifacts pass metadata and backend compatibility checks;
- thinking/non-thinking render through correct Qwen3.5 template semantics without `/think` or `/nothink` hacks;
- recommended Qwen3.5 sampling baselines can be represented and overridden deterministically;
- anomalous repetition/thinking has bounded detection and typed termination;
- context, cache and reuse behavior is safe for the backend's Qwen3.5 hybrid/recurrent execution model;
- separate evidence-backed Android runtime profiles exist for 0.8B and 2B;
- tokenizer/template/output/streaming/cancellation golden and integration tests pass;
- representative physical-device memory, thermal and latency evidence passes the certification gate;
- certification is attached only to exact curated artifacts and validated backend/runtime evidence.

Milestone sequencing is owned by [`roadmap.md`](roadmap.md).
