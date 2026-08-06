# Curated candidate model catalog

Status: active
Document type: feature-specification
Owner: models/model-catalog
Canonical scope: models.catalog
Read when: changing curated releases, compatibility targeting or application-reviewed model profiles
Last reviewed: 2026-08-06

The repository includes a bootstrap catalog for administrator-curated GGUF releases through `CuratedModelCatalog` in `models/model-catalog`.

This bootstrap is metadata only. It does not bundle or download model bytes, and it does not bypass the secure download and `ModelStore` installation flow.

## Included releases

| Model ID | Artifact | SHA-256 | Size | Architecture | Minimum RAM | Recommended RAM | Profile key |
| --- | --- | --- | ---: | --- | ---: | ---: | --- |
| `lfm2.5-1.2b-instruct-q4-k-m` | `LFM2.5-1.2B-Instruct-Q4_K_M.gguf` | `b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5` | 730,895,168 | `lfm2` | 4,000,000,000 | 6,000,000,000 | `lfm2.5-1.2b-instruct-q4-k-m-ctx4096` |
| `smollm2-360m-instruct-q4-k-m` | `SmolLM2-360M-Instruct-Q4_K_M.gguf` | `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2` | 270,590,880 | `llama` | 2,000,000,000 | 4,000,000,000 | `smollm2-360m-instruct-q4-k-m-ctx2048` |
| `qwen3-8b-ud-iq1-s` | `Qwen3-8B-UD-IQ1_S.gguf` | `210ada67841bb71977b869095daf9ca70e93592eec9857144f7821ce0fce6f5d` | 2,275,379,008 | `qwen3` | 8,000,000,000 | 12,000,000,000 | `qwen3-8b-ud-iq1-s-ctx2048` |
| `qwen3-8b-ud-iq1-m` | `Qwen3-8B-UD-IQ1_M.gguf` | `04d2d05a45283155fabfaa410731dcf20c90fd3c182b1fda5a1ad6abb034cbe6` | 2,396,489,536 | `qwen3` | 8,000,000,000 | 12,000,000,000 | `qwen3-8b-ud-iq1-m-ctx2048` |
| `qwen3-8b-q2-k` | `Qwen3-8B-Q2_K.gguf` | `7226e0183d31dca14d81c6f799ada2944be62160b8b7549a70254fba4124a5cf` | 3,281,733,440 | `qwen3` | 8,000,000,000 | 12,000,000,000 | `qwen3-8b-q2-k-ctx2048` |
| `qwen3-8b-q3-k-m` | `Qwen3-8B-Q3_K_M.gguf` | `4924cf38a3b3c4b27ead5ccb93e27027f9418738506ac50a24a70dfe8581a007` | 4,124,161_856 | `qwen3` | 12,000,000,000 | 16,000,000,000 | `qwen3-8b-q3-k-m-ctx2048` |
| `qwen3-8b-q4-k-m` | `Qwen3-8B-Q4_K_M.gguf` | `120307ba529eb2439d6c430d94104dabd578497bc7bfe7e322b5d9933b449bd4` | 5,027,784,512 | `qwen3` | 12,000,000,000 | 16,000,000,000 | `qwen3-8b-q4-k-m-ctx2048` |
| `qwen3-8b-q5-k-m` | `Qwen3-8B-Q5_K_M.gguf` | `159c694b93271e4edc1dc2a305b10cf981032c8f3035a7da00973312f0331504` | 5,851,113,280 | `qwen3` | 16,000,000,000 | 24,000,000,000 | `qwen3-8b-q5-k-m-ctx2048` |
| `qwen35-08b-q4-k-m` | `Qwen3.5-0.8B-Q4_K_M.gguf` | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` | 532,517,120 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-q4-k-m-ctx4096` |
| `qwen35-08b-q5-k-m` | `Qwen3.5-0.8B-Q5_K_M.gguf` | `c3ef5827b322c4be08a3a26ce424460d8b37daf593356c2cc1f4e40a7ab0581b` | 590,057,728 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-q5-k-m-ctx4096` |
| `qwen35-08b-q8-0` | `Qwen3.5-0.8B-Q8_0.gguf` | `0ad885ffd4bb022fc4f0d33a3308fa108ef8613159d3b3a67e23abca056b7a6c` | 811,843,840 | `qwen35` | 4,000,000,000 | 6,000,000,000 | `qwen35-08b-q8-0-ctx4096` |
| `qwen35-08b-ud-iq2-xxs` | `Qwen3.5-0.8B-UD-IQ2_XXS.gguf` | `a369165c8ec45a92d55cbad98b5377e2c32ca8dfc824c899d9d05b33b6b53e54` | 338,227,456 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-ud-iq2-xxs-ctx2048` |
| `qwen35-2b-q4-k-m` | `Qwen3.5-2B-Q4_K_M.gguf` | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | 1,280,835,840 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen35-2b-q4-k-m-ctx4096` |
| `qwen35-2b-q5-k-m` | `Qwen3.5-2B-Q5_K_M.gguf` | `1885b3a9195f8cc09da9a7a7a75afdc1e8d5cbf9fc4a499c3961dddea37098ac` | 1,435,238,656 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen35-2b-q5-k-m-ctx4096` |
| `qwen35-2b-ud-iq2-xxs` | `Qwen3.5-2B-UD-IQ2_XXS.gguf` | `43aedbd2b03a3c2cc39f49ccf74fcd3c394ed0b2a1ede8a30ee652ee9cfc27ef` | 768,270,592 | `qwen35` | 4,000,000,000 | 6,000,000,000 | `qwen35-2b-ud-iq2-xxs-ctx2048` |
| `qwen35-4b-q4-k-m` | `Qwen3.5-4B-Q4_K_M.gguf` | `00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4` | 2,740,937,888 | `qwen35` | 8,000,000,000 | 12,000,000,000 | `qwen35-4b-q4-k-m-ctx4096` |
| `qwen35-4b-q5-k-m` | `Qwen3.5-4B-Q5_K_M.gguf` | `8814232b85594dcd46c50e5b8b29324a7efe9e746edbe8a3d1df3d3fce7aad39` | 3,143,656,608 | `qwen35` | 12,000,000,000 | 16,000,000,000 | `qwen35-4b-q5-k-m-ctx4096` |
| `qwen35-4b-ud-iq2-xxs` | `Qwen3.5-4B-UD-IQ2_XXS.gguf` | `4c1ba794e8d6098f4fb6482b4db6e880c80b5ee0b4c64d8668afaf9541163677` | 1,520,217,248 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen35-4b-ud-iq2-xxs-ctx2048` |

All releases require Android API 26 or later, `arm64-v8a`, and the `llama.cpp` backend.

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
