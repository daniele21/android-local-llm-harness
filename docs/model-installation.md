# Verified model installation

Status: active
Document type: feature-specification
Owner: models/model-install
Canonical scope: models.installation
Read when: changing verified-handle consumption, GGUF inspection, publication or rollback
Last reviewed: 2026-08-06

## Purpose

`models/model-install` converts an already verified remote transfer into an installed content-addressed model without activating application bindings or loading the runtime.

The boundary exists between:

```text
models/model-download
        -> VerifiedDownloadHandle
        -> models/model-install
        -> ModelStore
```

It is intentionally independent from Android UI, WorkManager, Binder, runtime orchestration and a specific inference backend.

## Lifecycle

```text
ModelInstallationRequest
        -> validate release, target and profile
        -> revalidate and copy opaque verified download
        -> inspect GGUF metadata
        -> validate architecture and quantization when available
        -> ModelStore.import()
        -> ModelStore.verify()
        -> optional verified-download discard
        -> InstalledModelDescriptor
```

A successful installation does not:

- create or update an `AppModelBinding`;
- call `LocalLlmClient.prepare()`;
- load a native model;
- create a context or session;
- start generation.

Those operations remain explicit later actions.

## Main contracts

### `VerifiedDownloadAccess`

Defined in `models/model-download`, this contract is the only supported bridge from the verified holding area.

It accepts:

- an opaque `VerifiedDownloadHandle`;
- expected SHA-256 digest;
- expected byte count;
- a caller-owned destination file.

`FileSystemVerifiedDownloadAccess` resolves the handle only inside the downloader-controlled root, checks that the handle equals the expected digest, verifies the source length, copies while recomputing SHA-256, synchronizes the destination and deletes failed output.

The source backing path is never returned.

### `GgufArtifactInspector`

A backend-neutral metadata inspection contract. It returns stable metadata or one of:

- `FILE_NOT_FOUND`;
- `INVALID_GGUF`;
- `INSPECTION_FAILED`.

The initial adapter is `LlamaCppGgufArtifactInspector`. It delegates to the existing metadata-only `LlamaCppBridge.inspectGguf()` operation and maps backend results without forwarding backend exception text or private paths.

### `VerifiedModelInstaller`

Coordinates the installation transaction.

The request contains:

- verified handle;
- complete `CatalogModelRelease`;
- explicit `CatalogTarget`;
- application-resolved profile key and `GgufArtifact`;
- verified-download retention policy.

The installer rejects the request before copying bytes when:

- the descriptor is invalid;
- the release is revoked or unavailable;
- the target is not explicitly allowed;
- the profile key differs from the catalog release;
- digest, size, file name, architecture or quantization differs between catalog and reviewed application profile.

After staging, the installer also requires inspected GGUF architecture to match. Quantization is checked when the backend provides a canonical quantization value; catalog/profile quantization equality is always required.

## Staging and storage

The installer owns a controlled staging directory and creates unpredictable `.gguf` temporary files within it.

The staging copy is removed after success or failure.

Final artifact publication remains exclusively owned by `ModelStore`. The installer does not reproduce content-addressed layout, deduplication, atomic publication or conflict logic.

The peak storage requirement during installation can include:

```text
verified holding artifact
+ installation staging copy
+ ModelStore import staging/final artifact
+ configured storage safety margin
```

Compatibility and UI storage calculations must account for this temporary overlap.

## Post-import verification

After `ModelStore.import()`, the installer calls `ModelStore.verify()` on the installed digest.

If verification reports invalid content or cannot complete, the installer returns `POST_IMPORT_VERIFICATION_FAILED` and retains the verified holding artifact for diagnosis or retry.

The failure path is deliberately non-destructive. The current `ModelStore.import()` contract does not expose whether it created a new artifact or deduplicated an existing one. Automatically deleting the digest could therefore remove a valid model that existed before this installation or was installed concurrently. Explicit repair or removal remains behind model-store maintenance controls until the store exposes transactional creation provenance.

On success, the default policy discards the verified holding artifact. `RETAIN` keeps it explicitly. Cleanup failure does not invalidate an already verified installed model; the success result exposes whether discard completed.

## Progress and threading

Installation exposes these stages:

```text
VALIDATING
STAGING
INSPECTING
IMPORTING
VERIFYING
COMPLETED | FAILED
```

Observer failures are isolated from installation.

The API is synchronous and performs file I/O, hashing, GGUF inspection and model-store import. Callers must execute it outside the Android main thread.

## Failure semantics

Public failures are typed and privacy-safe:

- `INVALID_DESCRIPTOR`;
- `RELEASE_UNAVAILABLE`;
- `TARGET_NOT_ALLOWED`;
- `PROFILE_MISMATCH`;
- `VERIFIED_DOWNLOAD_UNAVAILABLE`;
- `VERIFIED_DOWNLOAD_INVALID`;
- `STAGING_FAILURE`;
- `GGUF_INSPECTION_FAILED`;
- `ARCHITECTURE_MISMATCH`;
- `QUANTIZATION_MISMATCH`;
- `MODEL_STORE_IMPORT_FAILED`;
- `POST_IMPORT_VERIFICATION_FAILED`;
- `INTERNAL_FAILURE`.

Results do not include signed URLs, full filesystem paths, model bytes, prompts or generated output.

## Testing

Deterministic tests cover:

- successful opaque copy and digest revalidation;
- handle/digest mismatch;
- verified artifact tampering after publication;
- discard through the opaque handle;
- successful installation and ordered stage emission;
- rejection before I/O for profile mismatch, unavailable release and unauthorized target;
- missing or invalid verified download;
- GGUF architecture mismatch;
- import failure;
- non-destructive post-import verification failures;
- retention of verified bytes after failure;
- cleanup of installation staging files;
- mapping from `llama.cpp` inspection results to stable installation outcomes.

## Deferred work

The following remain separate implementation blocks:

- transactional import provenance if automatic rollback is required;
- durable installed-model metadata persistence;
- catalog/download/install Compose UI;
- explicit application/use-case binding after installation;
- cancellation during synchronous `ModelStore.import()`;
- WorkManager or foreground-service execution;
- real remote download and installation evidence on representative physical devices.

See ADR 0007 for the architectural decision.
