# Qwen3.5 model and backend compatibility

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.compatibility
Read when: implementing exact curated Qwen3.5 artifact validation or pinned-backend compatibility proof
Last reviewed: 2026-08-08

## Goal

Prove that the exact curated **Qwen3.5 dense 0.8B and 2B** artifacts used by the product are valid and executable with the repository-pinned `llama.cpp` backend.

This is not a generic arbitrary-GGUF admission layer. Q35-1 closes the product model surface; Q35-2 validates known artifacts and protects that path from corrupted downloads, manifest mismatches and backend regressions.

## Validation model

```text
curated release
  -> expected SHA-256 / size / profile
  -> downloaded artifact bytes
  -> SHA-256 verification
  -> GGUF metadata inspection
  -> trusted structural fingerprint match
  -> pinned backend load
  -> tokenize
  -> minimal generation
  -> privacy-safe compatibility evidence
```

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-COMP-01 | DONE | Pinned Android backend revision is recorded as `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`. |
| Q35-COMP-02 | DONE | Exact 0.8B Q4_K_M and 2B Q4_K_M artifacts are pinned by SHA-256, size and quantization. |
| Q35-COMP-03 | DONE | Trusted minimal GGUF structural fingerprints are recorded from inspected artifacts. |
| Q35-COMP-04 | DONE | `Qwen35ArtifactDescriptor` carries stable inspected artifact facts only. |
| Q35-COMP-05 | DONE | Installer validation fails closed on catalog/descriptor/GGUF mismatch before publication/runtime use. |
| Q35-COMP-06 | DONE | Both reference artifacts pass load, tokenize and minimal generation with the pinned backend. |
| Q35-COMP-07 | DONE | Existing backend pin provides the required Qwen3.5 support; no dependency update was required. |
| Q35-COMP-08 | DONE | Compatibility evidence records exact model digest and backend build without private paths/content. |
| Q35-COMP-09 | DONE | Deterministic artifact mismatch tests plus the exact-backend smoke gate cover failure of the approved compatibility boundary. |

## Reference evidence

Pinned backend:

`aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`

Reference artifacts:

- Qwen3.5 0.8B Q4_K_M — SHA-256 `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`, size `532517120`;
- Qwen3.5 2B Q4_K_M — SHA-256 `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`, size `1280835840`.

Both inspected artifacts are GGUF v3, architecture `qwen35`, file type 15, 24 blocks and context length 262144. Embedding length is 1024 for 0.8B and 2048 for 2B.

Final `Qwen3.5 compatibility` run #6 passed exact download identity verification and `load -> tokenize -> minimal generate` for both artifacts. Repository Validate and Android Package gates also pass with the pinned backend/runtime packaging.

## Failure behavior

Failures describe problems with an expected curated artifact, not unsupported user choices: SHA/size mismatch, trusted-manifest mismatch, malformed/incomplete GGUF metadata, or inability of the pinned backend to execute the expected artifact. Native backend error strings do not leak through public contracts.

## Certification separation

Compatibility answers whether this exact curated artifact works with this exact backend build. Certification remains a later evidence layer and cannot be inherited across quantizations, artifact digests or device/runtime envelopes.

## Acceptance criteria

Q35-2 is complete: exact identity and inspected metadata are matched, both reference smoke paths succeed with the pinned backend, mismatch is rejected before runtime preparation, compatibility evidence is privacy-safe, deterministic failure coverage exists and applicable repository gates are green.
