# Curated model catalog

Status: active
Document type: feature-specification
Owner: models/model-catalog
Canonical scope: models.catalog
Read when: changing curated releases, compatibility targeting or application-reviewed model profiles
Last reviewed: 2026-08-08

The repository uses an administrator-curated GGUF catalog through `CuratedModelCatalog` in `models/model-catalog`.

The catalog is metadata only. Model binaries are downloaded through the secure transfer path and published through `ModelStore` only after size, SHA-256 and GGUF verification.

## Current product catalog

[ADR 0011](adr/0011-qwen35-only-product-support.md) closes the product model surface to Qwen3.5 dense 0.8B and 2B releases. Users do not add arbitrary models, URLs or local GGUF files.

The executable bootstrap now contains only the seven reviewed Qwen3.5 0.8B/2B releases below. Earlier LFM, SmolLM, Qwen3 and Qwen3.5 4B bootstrap definitions were removed rather than represented as unsupported product states. Remaining Q35-1 cleanup is owned by [`qwen35/workstreams/curated-model-baseline.md`](qwen35/workstreams/curated-model-baseline.md).

## Releases

| Model ID | Artifact | SHA-256 | Size | Architecture | Minimum RAM | Recommended RAM | Profile key |
| --- | --- | --- | ---: | --- | ---: | ---: | --- |
| `qwen35-08b-q4-k-m` | `Qwen3.5-0.8B-Q4_K_M.gguf` | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` | 532,517,120 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-q4-k-m-ctx4096` |
| `qwen35-08b-q5-k-m` | `Qwen3.5-0.8B-Q5_K_M.gguf` | `c3ef5827b322c4be08a3a26ce424460d8b37daf593356c2cc1f4e40a7ab0581b` | 590,057,728 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-q5-k-m-ctx4096` |
| `qwen35-08b-q8-0` | `Qwen3.5-0.8B-Q8_0.gguf` | `0ad885ffd4bb022fc4f0d33a3308fa108ef8613159d3b3a67e23abca056b7a6c` | 811,843,840 | `qwen35` | 4,000,000,000 | 6,000,000,000 | `qwen35-08b-q8-0-ctx4096` |
| `qwen35-08b-ud-iq2-xxs` | `Qwen3.5-0.8B-UD-IQ2_XXS.gguf` | `a369165c8ec45a92d55cbad98b5377e2c32ca8dfc824c899d9d05b33b6b53e54` | 338,227,456 | `qwen35` | 3,000,000,000 | 4,000,000,000 | `qwen35-08b-ud-iq2-xxs-ctx2048` |
| `qwen35-2b-q4-k-m` | `Qwen3.5-2B-Q4_K_M.gguf` | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | 1,280,835,840 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen35-2b-q4-k-m-ctx4096` |
| `qwen35-2b-q5-k-m` | `Qwen3.5-2B-Q5_K_M.gguf` | `1885b3a9195f8cc09da9a7a7a75afdc1e8d5cbf9fc4a499c3961dddea37098ac` | 1,435,238,656 | `qwen35` | 6,000,000,000 | 8,000,000,000 | `qwen35-2b-q5-k-m-ctx4096` |
| `qwen35-2b-ud-iq2-xxs` | `Qwen3.5-2B-UD-IQ2_XXS.gguf` | `43aedbd2b03a3c2cc39f49ccf74fcd3c394ed0b2a1ede8a30ee652ee9cfc27ef` | 768,270,592 | `qwen35` | 4,000,000,000 | 6,000,000,000 | `qwen35-2b-ud-iq2-xxs-ctx2048` |

All releases require Android API 26 or later, `arm64-v8a`, and the `llama.cpp` backend.

The initial certification candidates are `qwen35-08b-q4-k-m` and `qwen35-2b-q4-k-m`. Other listed quantizations remain curated candidates until their own evidence matrix is completed.

## Product acquisition path

```text
curated release
  -> compatibility with target/device
  -> verified HTTPS download
  -> byte-count + SHA-256 validation
  -> GGUF inspection
  -> ModelStore installation
  -> explicit application/use-case binding
```

There is no consumer-facing manual import path. Developer-only device validation may inject an exact test artifact into an isolated test application; that path is not part of catalog or consumer API semantics.

## Availability and certification

Catalog availability is an administrator lifecycle axis. It does not imply backend compatibility or certification.

Certification is separate evidence keyed by exact artifact SHA-256, quantization, backend build and validated device/runtime envelope. A quantization cannot inherit certification from another artifact.

## Target mapping

Each supplied use case is mapped to the internal phone-test application ID:

```text
play-internal-phone-test
```

The bootstrap use cases include:

```text
manual-inference-playground
physical-device-validation
```

`manual-inference-playground` means manual prompting against a curated model; it does not mean manual model import.

Exact `applicationId + useCaseId` filtering remains enforced.

## Context size mapping

The catalog wire contract does not own runtime load parameters. Context size is represented by the application-reviewed `profileKey`:

```text
*-ctx2048
*-ctx4096
```

Each key resolves to a reviewed `GgufModelProfile` with the corresponding context size and runtime settings. The catalog does not invent backend load parameters.

## Trust and verification

The URLs, byte sizes, hashes and license identifiers are reviewed catalog metadata. Before installation, the download pipeline still:

1. enforces HTTPS and redirect policy;
2. streams to an app-private partial file;
3. verifies the exact byte count;
4. calculates and compares SHA-256;
5. inspects the GGUF artifact;
6. publishes only through `ModelStore`.

No model file is committed to the repository.
