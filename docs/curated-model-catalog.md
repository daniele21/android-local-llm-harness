# Curated candidate model catalog

The repository includes a bootstrap catalog for four administrator-curated GGUF releases through `CuratedModelCatalog` in `models/model-catalog`.

This bootstrap is metadata only. It does not bundle or download model bytes, and it does not bypass the secure download and `ModelStore` installation flow.

## Included releases

| Model ID | Artifact | SHA-256 | Size | Architecture | Minimum RAM | Recommended RAM | Profile key |
| --- | --- | --- | ---: | --- | ---: | ---: | --- |
| `qwen3.5-0.8b-instruct-q4-k-m` | `Qwen3.5-0.8B-Q4_K_M.gguf` | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` | 532,517,120 | `qwen35` | 3,000,000,000 | 6,000,000,000 | `qwen3.5-0.8b-instruct-q4-k-m-ctx4096` |
| `lfm2.5-1.2b-instruct-q4-k-m` | `LFM2.5-1.2B-Instruct-Q4_K_M.gguf` | `b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5` | 730,895,168 | `lfm2` | 4,000,000,000 | 6,000,000,000 | `lfm2.5-1.2b-instruct-q4-k-m-ctx4096` |
| `smollm2-360m-instruct-q4-k-m` | `SmolLM2-360M-Instruct-Q4_K_M.gguf` | `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2` | 270,590,880 | `llama` | 2,000,000,000 | 4,000,000,000 | `smollm2-360m-instruct-q4-k-m-ctx2048` |
| `qwen3.5-2b-instruct-q4-k-m` | `Qwen3.5-2B-Q4_K_M.gguf` | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | 1,280,835,840 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen3.5-2b-instruct-q4-k-m-ctx4096` |

All releases require Android API 26 or later, `arm64-v8a`, the `llama.cpp` backend and Q4_K_M quantization.

## Candidate lifecycle

The supplied `candidate` state maps to `CatalogAvailability.CANDIDATE`. Candidate releases remain selectable when all target and device checks pass, but compatibility evaluation emits `CatalogCompatibilityWarning.RELEASE_CANDIDATE` so the UI can distinguish them from fully approved active releases.

Promotion to `ACTIVE`, deprecation, revocation and replacement remain administrator-controlled catalog lifecycle operations.

## Target mapping

Each supplied use case is mapped to the internal phone-test application ID:

```text
play-internal-phone-test
```

The bootstrap also includes:

```text
manual-inference-playground
physical-device-validation
```

These two targets match the current phone-test runtime integration boundary. Exact `applicationId + useCaseId` filtering remains enforced.

## Context size mapping

The catalog wire contract intentionally does not own runtime load parameters. The supplied context size is therefore represented by the application-reviewed `profileKey`:

```text
*-ctx2048
*-ctx4096
```

A later application integration must resolve each key to a `GgufModelProfile` with the corresponding context size and reviewed batch, thread, mmap and cache settings. The catalog must not invent those remaining runtime parameters.

## Trust and verification

The URLs, byte sizes, hashes and license identifiers are recorded exactly as supplied for this curated revision. They are not treated as proof that the remote artifact is healthy.

Before installation, the download pipeline must still:

1. enforce the HTTPS and redirect policy;
2. stream to an app-private partial file;
3. verify the exact byte count;
4. calculate and compare SHA-256;
5. inspect the GGUF artifact;
6. publish only through `ModelStore`.

No model file is committed to the repository.
