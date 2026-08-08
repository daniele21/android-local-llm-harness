# Qwen3.5 product-policy architecture

Status: active
Document type: architecture
Owner: qwen35
Canonical scope: qwen35.architecture
Read when: changing Qwen3.5 module ownership, model admission, generation planning or backend/runtime boundaries
Last reviewed: 2026-08-08

This document defines only the Qwen3.5 policy delta. Repository dependency direction and generic execution mechanics remain owned by [`../architecture.md`](../architecture.md).

## Runtime flow

```text
Qwen3.5 GGUF / curated manifest
        |
        v
neutral GGUF inspector
        |
        v
Qwen35CompatibilityValidator
        |
        +--> Qwen35ArtifactDescriptor
        +--> CompatibilityDecision
                     |
                     v
        Qwen35 policy resolution
          /         |          \
 prompt/sampling  context    guard thresholds
          \         |          /
           neutral execution plan
                     |
                     v
       runtime lifecycle + llama.cpp
```

The exact class names may change during implementation, but the ownership boundaries must remain.

## Placement by existing owner

Qwen3.5 code may use focused packages inside existing modules. Do not create one `models/qwen35` Gradle module that owns unrelated lifecycle stages.

| Concern | Owner |
| --- | --- |
| Tier, reviewed generation profile and runtime-policy intent | `models/model-profile` |
| Structural metadata adaptation and installation-time admission | `models/model-install`, using the neutral backend inspector |
| Curated eligibility, availability and certification references | `models/model-catalog` |
| Resolution, streaming guard execution and terminal mapping | `core/runtime-core` |
| Template kwargs, sampler primitives and backend capabilities | `backends/llama-cpp` |
| Safe effective fields and evidence identity | existing observability owners |
| Presentation and migration actions | connected app controllers over the owning contracts |

Do not introduce `ModelFamilyAdapter`, sibling family adapters or another runtime path. A new Gradle module requires a demonstrated dependency boundary and a separate architectural decision.

## Responsibility split

### Qwen3.5 policy owns

- dense-Qwen3.5 admission semantics;
- supported tier declaration (`0.8B`, `2B`);
- translation of neutral thinking intent into Qwen3.5 template semantics;
- chat-template kwargs required by Qwen3.5;
- Qwen3.5 sampler baseline selection;
- model-aware validation and versioned guard thresholds;
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

The runtime executes the guard because it owns streaming and terminal lifecycle. Qwen3.5 policy supplies bounded thresholds; it does not own a second decode loop.

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

## Artifact, decision and evidence boundaries

An artifact descriptor contains inspected, stable facts only:

```kotlin
data class Qwen35ArtifactDescriptor(
    val artifactDigest: String,
    val tier: Qwen35Tier,
    val quantization: String,
    val nativeContextLength: Int?,
    val templateFingerprint: String?,
    val structuralFingerprint: String,
)
```

`tier` and dense/MoE classification come from inspected structural metadata validated against a trusted manifest when one exists. Filename, display name, URL and user-entered import labels are never authoritative.

Compatibility is an explicit, testable decision over the artifact and the execution environment:

```kotlin
data class Qwen35CompatibilityDecision(
    val artifactDigest: String,
    val backendBuildId: String,
    val supported: Boolean,
    val capabilities: Set<Qwen35RuntimeCapability>,
    val failure: Qwen35CompatibilityFailure?,
)
```

Certification is a third record keyed by the exact artifact, backend, profiles and device envelope. It is not embedded in either the artifact descriptor or catalog availability. Field names are illustrative; the separation is mandatory.

Upstream evidence is only a design input. Compatibility and risky runtime capabilities require proof against the repository's exact backend build; the owning checks are in [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md) and [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md).
