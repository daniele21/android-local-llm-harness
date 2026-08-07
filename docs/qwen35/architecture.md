# Qwen3.5 specialization architecture

Status: active
Document type: architecture
Owner: qwen35
Canonical scope: qwen35.architecture
Read when: changing Qwen3.5 module ownership, model admission, generation planning or backend/runtime boundaries
Last reviewed: 2026-08-07

This document defines only the architecture delta introduced by the Qwen3.5 specialization. Repository dependency direction remains owned by [`../architecture.md`](../architecture.md).

## Runtime flow

```text
Qwen3.5 GGUF / curated catalog entry
        |
        v
Qwen35MetadataInspector
        |
        v
Qwen35CompatibilityValidator
        |
        v
Qwen35ModelDescriptor
        |
        +---------------------+
        |                     |
        v                     v
Qwen35PromptPlanner     Qwen35RuntimeTuning
        |                     |
        v                     v
Qwen35SamplingPlanner  Qwen35ContextPolicy
        |                     |
        +----------+----------+
                   |
                   v
          effective generation plan
                   |
                   v
          Qwen35GenerationGuard
                   |
                   v
          llama.cpp backend adapter
                   |
                   v
             Android ARM64 CPU
```

The exact class names may change during implementation, but the ownership boundaries must remain.

## Proposed Qwen3.5 domain

Prefer a focused namespace instead of a generic multi-family adapter layer:

```text
models/
└── qwen35/
    ├── Qwen35ModelDescriptor
    ├── Qwen35MetadataInspector
    ├── Qwen35CompatibilityValidator
    ├── Qwen35GenerationProfile
    ├── Qwen35PromptPlanner
    ├── Qwen35ThinkingPolicy
    ├── Qwen35ContextPolicy
    ├── Qwen35GenerationGuard
    └── Qwen35RuntimeTuning
```

Do not introduce `ModelFamilyAdapter`, `GemmaAdapter`, `LlamaAdapter` or equivalent speculative abstractions in this phase. A future family can be added behind the existing domain/backend boundary when there is a real requirement.

## Responsibility split

### Qwen3.5 domain owns

- dense-Qwen3.5 admission semantics;
- supported tier declaration (`0.8B`, `2B`);
- thinking-mode intent;
- chat-template kwargs required by Qwen3.5;
- Qwen3.5 sampler baseline selection;
- model-aware validation and guard policy;
- approved context tiers and Qwen3.5 cache/reuse capabilities;
- mapping device capabilities plus model tier/quantization to runtime tuning intent;
- certification metadata required by the catalog.

### Existing generation/runtime layers own

- request/session lifecycle;
- exact tokenization call path;
- context allocation mechanics;
- streaming/cancellation contracts;
- output constraints;
- scheduler and model residency;
- privacy-safe telemetry and benchmark persistence.

Qwen3.5 policy should plug into those owners rather than fork them.

### llama.cpp backend owns

- GGUF/native model loading;
- Qwen3.5 tensor graph and kernels;
- Gated DeltaNet / recurrent-state implementation;
- full-attention implementation;
- backend sampler primitives;
- native context/memory operations.

Kotlin/JNI must not reimplement Qwen3.5 model math.

## Hybrid/recurrent capability boundary

Current upstream `llama.cpp` Qwen3.5 code uses recurrent memory support and loads Gated DeltaNet/SSM parameters. Therefore session reuse, prefix snapshots and context restore must not assume that all persistent model state is conventional transformer KV cache.

Architecture rule:

```text
optimization requested
  -> Qwen35RuntimeCapability says supported?
      -> backend revision proven?
          -> enable
          -> otherwise reject/disable explicitly
```

The initial safe state is conservative: existing basic context lifecycle is allowed; new snapshot/restore or prefix-cache optimizations stay disabled for Qwen3.5 until validated.

## Model descriptor boundary

A descriptor should contain enough stable information to plan execution without exposing backend internals, for example:

```kotlin
data class Qwen35ModelDescriptor(
    val artifactDigest: String,
    val tier: Qwen35Tier,
    val quantization: String,
    val nativeContextLength: Int?,
    val templateFingerprint: String?,
    val backendCompatibility: Qwen35BackendCompatibility,
)
```

`tier` is resolved from trusted catalog/import metadata plus verification rules; it must not be inferred solely from a filename.

## Backend compatibility

Compatibility must be explicit and testable:

```kotlin
data class Qwen35BackendCompatibility(
    val validatedLlamaCppRevision: String,
    val denseQwen35Supported: Boolean,
    val recurrentStateRestoreValidated: Boolean,
    val prefixReuseValidated: Boolean,
)
```

Field names are illustrative; the invariant is that risky runtime capabilities are derived from validated backend evidence, not assumed from model family alone.

## Upstream evidence boundary

As of 2026-08-07:

- Qwen's 0.8B and 2B model cards define thinking/non-thinking behavior and warn that these smaller variants can enter thinking loops;
- upstream `llama.cpp` contains a dedicated `qwen35.cpp` implementation using recurrent memory and Gated DeltaNet parameters.

These are design inputs, not proof that the repository's pinned backend revision is compatible. Q35-1 must validate the actual pin.

Upstream references:

- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
