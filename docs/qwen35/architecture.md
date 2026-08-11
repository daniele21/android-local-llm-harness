# Qwen3.5 product-policy architecture

Status: active
Document type: architecture
Owner: qwen35
Canonical scope: qwen35.architecture
Read when: changing Qwen3.5 module ownership, curated model handling, generation planning or backend/runtime boundaries
Last reviewed: 2026-08-08

This document defines only the Qwen3.5 policy delta. Repository dependency direction and generic execution mechanics remain owned by [`../architecture.md`](../architecture.md).

## Runtime flow

```text
built-in curated Qwen3.5 release
        |
        v
verified download + SHA-256
        |
        v
neutral GGUF inspector
        |
        v
Qwen35 artifact/backend validation
        |
        v
Qwen35 policy resolution
  /         |          \
prompt    context    guard thresholds
sampling  runtime
  \         |          /
   neutral execution plan
        |
        v
runtime lifecycle + llama.cpp
```

There is no consumer path for arbitrary GGUF import, family selection or architecture extension.

## Placement by existing owner

Qwen3.5 code may use focused packages inside existing modules. Do not create one `models/qwen35` Gradle module that owns unrelated lifecycle stages.

| Concern | Owner |
| --- | --- |
| Tier, reviewed generation profile and runtime-policy intent | `models/model-profile` |
| Verified installation and GGUF metadata inspection | existing model installation/store owners |
| Curated releases, availability and certification references | `models/model-catalog` |
| Resolution, streaming guard execution and terminal mapping | `core/runtime-core` |
| Template kwargs, sampler primitives and backend capabilities | `backends/llama-cpp` |
| Safe effective fields and evidence identity | existing observability owners |
| Product model presentation | connected app controllers over the owning contracts |

Do not introduce `ModelFamilyAdapter`, sibling family adapters or another runtime path. A new Gradle module requires a demonstrated dependency boundary and a separate architectural decision.

## Responsibility split

### Qwen3.5 policy owns

- curated tier declaration (`0.8B`, `2B`);
- reviewed generation and runtime profile mapping for exact catalog artifacts;
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

## Closed model boundary

Product model choice is represented by reviewed catalog identity, not by arbitrary model metadata supplied by the user.

```text
catalog release id
  -> exact artifact SHA-256
  -> reviewed profile key
  -> verified downloaded bytes
  -> inspected GGUF facts match expected artifact
  -> backend compatibility evidence
```

The catalog does not make runtime proof unnecessary. SHA-256, GGUF integrity and the pinned backend must still be validated for the exact artifact. Those checks protect the known product path from corruption or backend regressions; they are not a generic arbitrary-model admission system.

Developer validation tools may inject exact test artifacts into isolated test applications. This capability must not appear in consumer contracts or connected product UI.

## Hybrid/recurrent capability boundary

Qwen3.5 execution uses recurrent state in addition to full-attention behavior. Session reuse, prefix snapshots and context restore must therefore not assume that all persistent state is conventional transformer KV cache.

Architecture rule:

```text
optimization requested
  -> Qwen35RuntimeCapability says supported?
      -> exact backend revision proven?
          -> enable
          -> otherwise reject/disable explicitly
```

The initial safe state is conservative: ordinary context lifecycle is allowed; snapshot/restore or prefix-cache optimizations stay disabled until validated against the exact backend build and curated artifact.

## Artifact and evidence boundaries

A Qwen3.5 artifact descriptor contains inspected stable facts for a known catalog artifact:

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

Descriptor facts must agree with the trusted catalog manifest. Filename, display name and URL are never authoritative identity.

Compatibility evidence is keyed by the exact artifact and execution environment:

```kotlin
data class Qwen35CompatibilityEvidence(
    val artifactDigest: String,
    val backendBuildId: String,
    val validated: Boolean,
    val capabilities: Set<Qwen35RuntimeCapability>,
)
```

Certification is a separate record keyed by the exact artifact, backend, profiles and device envelope. It is not implied by catalog availability.

Upstream evidence is only a design input. Compatibility and risky runtime capabilities require proof against the repository's exact backend build; the owning checks are in [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md) and [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md).
