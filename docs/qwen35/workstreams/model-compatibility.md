# Qwen3.5 model and backend compatibility

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.compatibility
Read when: implementing Qwen3.5 GGUF detection, admission, backend validation or typed compatibility failures
Last reviewed: 2026-08-07

## Goal

Create a hard compatibility boundary so only explicitly supported **dense Qwen3.5 0.8B and 2B** artifacts can enter the specialized runtime path.

## Admission model

Admission is layered:

```text
artifact bytes
  -> SHA-256 identity
  -> GGUF metadata inspection
  -> architecture classification
  -> trusted tier/catalog consistency
  -> backend capability check
  -> supported Qwen3.5 descriptor
```

Filename, download URL and display name are never authoritative architecture signals.

Expected dense Qwen3.5 GGUF architecture metadata should be verified against the chosen reference artifacts during implementation rather than hard-coded from documentation alone.

## Required domain concepts

Introduce or adapt domain types for:

- `Qwen35Tier`: `B0_8`, `B2`;
- `Qwen35ModelDescriptor`;
- `Qwen35BackendCompatibility`;
- typed unsupported-family / unsupported-variant / unsupported-tier failures;
- certification state kept separate from basic compatibility.

Do not create multi-family adapter abstractions in this phase.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-COMP-01 | PLANNED | Resolve and record the current pinned `llama.cpp` revision/build used by Android. |
| Q35-COMP-02 | PLANNED | Select exact reference 0.8B and 2B GGUF artifacts already intended for the curated catalog and record SHA-256/quantization metadata. |
| Q35-COMP-03 | PLANNED | Inspect reference GGUF metadata and define the minimal dense-Qwen3.5 classification rule. |
| Q35-COMP-04 | PLANNED | Add `Qwen35ModelDescriptor` without backend-native types. |
| Q35-COMP-05 | PLANNED | Map trusted catalog/import metadata to `B0_8` or `B2`; reject unsupported tiers. |
| Q35-COMP-06 | PLANNED | Add explicit rejection for Qwen3.5 MoE and non-Qwen3.5 architectures. |
| Q35-COMP-07 | PLANNED | Prove load, tokenize and minimal generation on both reference artifacts using the pinned backend. |
| Q35-COMP-08 | PLANNED | If the pin lacks required support, update the pin as a separate reviewed dependency change and rerun Android packaging gates. |
| Q35-COMP-09 | PLANNED | Persist privacy-safe compatibility metadata with exact model digest and backend revision. |
| Q35-COMP-10 | PLANNED | Add deterministic unit/integration tests for accepted, mismatched and unsupported artifacts. |

## Failure behavior

Failures must be typed and occur at the narrowest safe boundary. Examples:

- unsupported model family;
- unsupported Qwen3.5 variant such as MoE;
- unsupported tier such as 4B/9B;
- catalog descriptor inconsistent with inspected artifact;
- backend revision not validated for required Qwen3.5 capability;
- malformed or missing required GGUF metadata.

Do not leak native backend error strings through public contracts.

## Certification separation

Compatibility answers: **can the runtime safely attempt this supported Qwen3.5 class?**

Certification answers: **has this exact artifact/quantization/backend/device envelope passed the required evidence?**

A compatible manually imported artifact may run in the Playground as `UNVERIFIED`; it must not inherit certification from another digest.

## Acceptance criteria

Q35-1 is complete when:

- 0.8B and 2B reference GGUFs are recognized from inspected metadata and trusted identity;
- their minimal inference smoke path succeeds with the recorded backend revision;
- 4B, 9B, MoE and non-Qwen3.5 artifacts are rejected before native preparation;
- filename changes do not affect classification;
- catalog-tier mismatch is detected;
- compatibility metadata contains no private filesystem path or prompt/output content;
- deterministic tests cover success and each typed rejection class;
- applicable repository validation gates pass.
