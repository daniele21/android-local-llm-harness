# Qwen3.5 model and backend compatibility

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.compatibility
Read when: implementing exact curated Qwen3.5 artifact validation or pinned-backend compatibility proof
Last reviewed: 2026-08-08

## Goal

Prove that the exact curated **Qwen3.5 dense 0.8B and 2B** artifacts used by the product are valid and executable with the repository-pinned `llama.cpp` backend.

This is not a generic arbitrary-GGUF admission layer. Q35-1 already closes the product model surface, so Q35-2 validates known artifacts and protects that path from corrupted downloads, manifest mismatches or backend regressions.

## Validation model

```text
curated release
  -> expected SHA-256 / size / profile
  -> downloaded artifact bytes
  -> SHA-256 verification
  -> GGUF metadata inspection
  -> expected Qwen3.5 structural facts match
  -> pinned backend load
  -> tokenize
  -> minimal generation
  -> compatibility evidence
```

Filename, display name and download URL are descriptive metadata, never immutable identity. Exact artifact digest plus inspected GGUF facts must agree with the reviewed catalog manifest.

Expected Qwen3.5 metadata should be recorded from the chosen reference artifacts during implementation rather than inferred from filename conventions.

## Required domain concepts

Introduce or adapt only what the closed product path needs:

- `Qwen35Tier`: `B0_8`, `B2`;
- `Qwen35ArtifactDescriptor` containing inspected stable artifact facts;
- compatibility evidence keyed by exact artifact digest and backend build;
- typed failures for artifact integrity, manifest mismatch, malformed metadata and backend incompatibility;
- certification evidence kept separate from basic compatibility proof.

Do not introduce multi-family adapters or unsupported-family taxonomies.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-COMP-01 | PLANNED | Resolve and record the current pinned `llama.cpp` revision/build used by Android. |
| Q35-COMP-02 | PLANNED | Use the exact 0.8B Q4_K_M and 2B Q4_K_M reference artifacts and verify their SHA-256, size and quantization metadata. |
| Q35-COMP-03 | PLANNED | Record the minimal inspected GGUF facts required to prove each curated artifact matches its reviewed manifest. |
| Q35-COMP-04 | PLANNED | Add `Qwen35ArtifactDescriptor` without backend-native or environment-dependent fields. |
| Q35-COMP-05 | PLANNED | Validate downloaded artifact facts against the trusted catalog manifest before runtime preparation. |
| Q35-COMP-06 | PLANNED | Prove load, tokenize and minimal generation on both reference artifacts using the pinned backend. |
| Q35-COMP-07 | PLANNED | If the pin lacks required support, update it as a separate reviewed dependency change and rerun Android packaging gates. |
| Q35-COMP-08 | PLANNED | Produce privacy-safe compatibility evidence with exact model digest and backend build. |
| Q35-COMP-09 | PLANNED | Add deterministic unit/integration tests for valid artifacts, digest mismatch, manifest mismatch, malformed metadata and backend failure. |

## Validation evidence

Implementation uses pinned `llama.cpp` revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3` and exact Q4_K_M reference artifacts for Qwen3.5 0.8B and 2B. Trusted structural GGUF fingerprints, fail-closed manifest validation, privacy-safe compatibility evidence and deterministic installer/unit coverage are present on `dev`.

Repository validation and Android artifact packaging are green on the Q35-3 implementation, including the runtime `libllama-common.so` required by the typed Jinja chat-template renderer. This document change intentionally triggers the final exact-artifact compatibility smoke before Q35-2 is marked `DONE`.

## Failure behavior

The remaining failures describe problems with an expected curated artifact, not unsupported user choices. Examples:

- downloaded bytes do not match expected SHA-256 or size;
- inspected GGUF metadata does not match the reviewed manifest;
- GGUF metadata is malformed or incomplete;
- pinned backend cannot load or execute the expected Qwen3.5 artifact;
- validated backend build differs from the build used for recorded compatibility evidence.

Do not leak native backend error strings through public contracts.

## Certification separation

Compatibility answers: **does this exact curated artifact work correctly with this backend build?**

Certification answers: **has this exact artifact/quantization/backend/device envelope passed the complete validation evidence?**

Catalog availability does not imply either result, and certification cannot be inherited across quantizations or artifact digests.

## Acceptance criteria

Q35-2 is complete when:

- the exact 0.8B and 2B reference GGUFs match their trusted catalog identities and inspected metadata;
- their minimal load/tokenize/generate smoke path succeeds with the recorded backend revision;
- digest, size, manifest or metadata mismatch is detected before runtime preparation;
- compatibility evidence contains exact artifact digest and backend build without private paths or prompt/output content;
- deterministic tests cover the expected success path and artifact/backend failure paths;
- applicable repository validation gates pass.
