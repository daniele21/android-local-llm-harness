# ADR 0005: Separate remote model distribution from local inference

- Status: Proposed
- Date: 2026-08-04

## Context

The runtime already identifies GGUF artifacts by SHA-256 and stores them through `ModelStore` in application-private content-addressed storage. The Play-installable phone application can import user-selected GGUF files and run them through the embedded runtime.

The product now needs an administrator-managed list of approved GGUF releases that users can inspect and download. Treating a remote URL as a model identity, allowing remote data to define unrestricted runtime profiles or writing downloaded bytes directly into the final store would weaken the existing integrity and application/use-case binding guarantees.

## Decision

Remote distribution is a control-plane concern separated from the local inference data plane.

- `models/model-catalog` owns catalog domain models, validation, target filtering and compatibility policy.
- a later `models/model-download` module owns HTTPS transfer, partial files, cancellation, byte counting, hashing and installation orchestration;
- `models/model-store` remains the only owner of final GGUF artifact publication;
- model identity remains the canonical SHA-256 digest rather than a URL, display name or remote release identifier;
- remote catalog entries select an application-reviewed `profileKey`; they cannot inject arbitrary prompts, file paths, code or unrestricted llama.cpp settings;
- application ID and use-case ID filtering is exact and fail closed;
- downloading, installing, selecting and loading are separate explicit operations;
- installed inference remains available without network access;
- catalog refresh or download failure cannot invalidate an otherwise healthy installed artifact;
- normal telemetry excludes credentials, signed URLs, private paths, model bytes, prompts and generated output.

The first connected surface is `apps/local-llm-phone-test`, while the catalog and download contracts remain UI independent and reusable by future embedded or shared-runtime integrations.

## Consequences

- the existing content-addressed store and integrity checks are reused rather than duplicated;
- installation temporarily requires enough storage for download and `ModelStore` staging copies unless a separately reviewed adopt/stream API is added;
- catalog release metadata requires storage separate from raw model bytes;
- administrator changes can publish or deprecate releases without shipping a new application, while application-owned profile policy remains under code review;
- adding model download requires explicit Android network permission, host policy and privacy review;
- production third-party catalog distribution requires signed-manifest verification, key rotation and rollback protection;
- cross-application installation remains deferred until a protected bridge or shared runtime exists.

## Alternatives considered

### Store the URL in `GgufArtifact` and let the runtime fetch on demand

Rejected because it couples inference to networking, makes runtime preparation non-deterministic and treats a mutable location as model identity.

### Write verified downloads directly into `models/sha256/...`

Rejected because it duplicates and bypasses `ModelStore` conflict, staging and publication guarantees.

### Let the catalog define complete prompts and runtime settings

Rejected because a compromised catalog could alter application behavior beyond selecting approved model bytes. The application resolves a bounded `profileKey` instead.

### Use Android `DownloadManager`

Deferred because the first implementation requires app-private partial files, strict redirect/host policy, integrated SHA-256 calculation and typed install phases. The transport remains abstract so this can be revisited.
