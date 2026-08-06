# Admin-managed model catalog and secure GGUF download plan

Status: historical
Document type: historical-plan
Owner: models
Last reviewed: 2026-08-06

This is the original phase and pull-request plan. Current model-distribution behavior is owned by [`../../model-catalog-download-plan.md`](../../model-catalog-download-plan.md) and the focused model specifications it routes to.

**Status:** Active implementation tracker

**Canonical base:** `main`

**Base commit audited:** `dfba2a05ed8166ef79a12261089078e13fd3902e`

**Implementation branch:** `agent/model-catalog-download-implementation`

**Last updated:** 2026-08-04

## 1. Purpose

This document defines the implementation plan for an administrator-managed catalog of downloadable GGUF models in the Android Local LLM Harness.

An administrator publishes a controlled set of supported model releases. The Android application retrieves the catalog, filters releases by application and use case, evaluates device compatibility, lets the user explicitly download a model, verifies the downloaded artifact and installs it through the existing content-addressed `ModelStore`.

The catalog is a distribution control plane. It must not become part of the inference data plane.

```text
Admin catalog
    -> validated catalog document
    -> application/use-case filtering
    -> device compatibility evaluation
    -> explicit user download
    -> private temporary file
    -> byte-size and SHA-256 verification
    -> GGUF inspection
    -> ModelStore.import()
    -> ModelStore.verify()
    -> installed release metadata
    -> explicit model selection
    -> existing RuntimeOrchestrator
```

A remote URL is only a download location. Model identity remains the immutable SHA-256 digest.

## 2. Main-branch baseline

This plan is based only on the current `main` branch. Historical or open feature branches are not implementation dependencies.

### 2.1 Existing capabilities that must be reused

The current repository already provides:

- a pinned `llama.cpp` Android backend;
- GGUF metadata inspection;
- model profiles and application/use-case binding concepts;
- SHA-256 content-addressed model import;
- streaming hashing and expected-size validation;
- atomic or fail-closed publication into app-private storage;
- model lookup, verification, removal and inventory snapshots;
- embedded runtime orchestration;
- deterministic and streaming generation;
- cancellation and session lifecycle handling;
- integrity caching and health checks;
- telemetry, resource and benchmark foundations;
- a Play-installable phone application;
- Storage Access Framework GGUF import;
- a manual local inference playground;
- a merged UX/UI plan that identifies `apps/local-llm-phone-test` as the first connected surface.

These foundations must not be duplicated.

### 2.2 Existing `ModelStore` authority

The current model-store contract remains authoritative for final GGUF bytes:

```kotlin
interface ModelStore {
    fun find(digest: ModelDigest): StoredModel?
    fun import(source: File, artifact: GgufArtifact): StoredModel
    fun verify(digest: ModelDigest): VerificationResult
    fun remove(digest: ModelDigest): Boolean
    fun snapshot(): ModelStoreSnapshot
}
```

The catalog feature must never:

- create an alternative final model directory;
- derive identity from a URL or display name;
- write directly into `models/sha256/...`;
- duplicate the import and conflict logic in `FileSystemModelStore`;
- report a model as installed before `ModelStore.import()` and verification succeed.

### 2.3 Missing capabilities

The following still need implementation:

- remote catalog contracts and decoding;
- strict catalog validation;
- catalog synchronization and local caching;
- persistent release metadata separate from raw model bytes;
- application/use-case filtering;
- device compatibility evaluation;
- HTTPS model download;
- private partial-download state;
- progress and cancellation;
- downloaded byte-size and digest enforcement;
- GGUF inspection before installation registration;
- update, deprecation and revocation behavior;
- catalog and download health checks;
- phone-app catalog UI;
- signed-manifest verification and key rotation;
- physical-device download and offline-inference evidence.

### 2.4 Unmerged overlap

Open PRs and branches are reference material only.

- PR #34 contains standalone-console model mutation work but is not merged.
- PR #40 contains a large connected Compose implementation but is not merged.
- The merged Harness UX plan currently excludes internet model download.

The new implementation must compile from `main` without either PR. UI-independent logic must remain reusable when those lines of work are later reconciled.

## 3. Product behavior

### 3.1 Administrator responsibilities

For every model release, the administrator defines:

- stable model ID;
- release version;
- display name and description;
- exact SHA-256 digest;
- expected byte size;
- HTTPS download URL;
- architecture and quantization metadata;
- allowed application IDs and use-case IDs;
- minimum Android API;
- supported ABIs;
- minimum and recommended memory;
- minimum free-storage requirement;
- compatible Harness/runtime version range;
- license and source metadata;
- lifecycle status;
- optional replacement release;
- application-reviewed profile key.

The catalog is data only. It cannot inject executable code, arbitrary paths, Android intents, prompts or unrestricted backend settings.

### 3.2 User responsibilities

The user can:

- refresh the approved catalog;
- inspect release size, quantization, license and compatibility;
- see explicit incompatibility reasons;
- confirm a large download;
- monitor progress;
- cancel or retry;
- distinguish downloading, verification and installation;
- select an installed model for an authorized use case;
- verify or remove an installed model;
- see update, deprecation and revocation states.

The application must not silently download model files on startup, navigation or catalog refresh.

### 3.3 Canonical lifecycle

```text
NOT_PRESENT
    -> QUEUED
    -> DOWNLOADING
    -> DOWNLOADED_TEMPORARY
    -> VERIFYING_BYTES
    -> INSPECTING_GGUF
    -> IMPORTING_TO_MODEL_STORE
    -> INSTALLED
```

Recoverable or terminal states include:

```text
CANCELLED
FAILED_NETWORK
FAILED_HTTP
FAILED_SIZE
FAILED_DIGEST
FAILED_GGUF
FAILED_COMPATIBILITY
FAILED_STORAGE
FAILED_IMPORT
DEPRECATED
REVOKED
UPDATE_AVAILABLE
```

A release is `INSTALLED` only when its exact digest exists in `ModelStore` and passes required verification.

## 4. Architecture

### 4.1 Target modules

```text
models/
├── model-profile        existing application-owned profiles
├── model-store          existing final artifact owner
├── model-catalog        new catalog domain, validation and policy
└── model-download       new Android transfer and installation orchestration
```

Modules are introduced only when they contain real behavior.

### 4.2 Dependency direction

```text
apps/local-llm-phone-test
        |
        +--> model-catalog
        +--> model-download
        +--> model-profile
        +--> model-store
        +--> runtime contracts

model-download
        +--> model-catalog
        +--> model-profile
        +--> model-store
        +--> GGUF inspection boundary

model-catalog
        +--> core contracts

model-store
        X--> model-catalog
        X--> networking
        X--> UI
```

`core/runtime-core` must not depend on HTTP clients, catalog JSON, remote URLs or download state.

### 4.3 Distribution versus inference

```text
Remote catalog and download control plane
        -> verified installed artifact
        -> existing local inference data plane
```

Inference must continue offline after installation. Catalog or network failure must not break a healthy installed model.

### 4.4 First connected surface

The first product integration targets `apps/local-llm-phone-test` because it already owns the real app-private model store and embedded runtime.

The standalone developer console may consume neutral catalog contracts later. Cross-application installation remains deferred until a protected bridge or shared runtime exists.

## 5. Catalog domain model

### 5.1 Document

```kotlin
data class CatalogModelDocument(
    val schemaVersion: Int,
    val catalogId: CatalogId,
    val revision: Long,
    val generatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val entries: List<CatalogModelRelease>,
)
```

Document rules:

- supported schema version;
- stable non-empty catalog ID;
- non-negative revision;
- valid generation and expiry window;
- bounded entry count;
- unique release identity;
- no conflicting size metadata for one digest;
- expired or untrusted documents cannot authorize new downloads.

### 5.2 Release

```kotlin
data class CatalogModelRelease(
    val id: CatalogReleaseId,
    val displayName: String,
    val description: String,
    val artifact: CatalogGgufArtifact,
    val compatibility: CatalogCompatibility,
    val availability: CatalogAvailability,
    val allowedTargets: Set<CatalogTarget>,
    val profileKey: ModelProfileKey,
    val license: CatalogLicense,
    val replacement: CatalogReleaseId?,
)
```

### 5.3 Artifact

```kotlin
data class CatalogGgufArtifact(
    val digest: ModelDigest,
    val sizeBytes: Long,
    val downloadUri: URI,
    val architecture: String,
    val quantization: String,
    val fileName: String,
)
```

The digest is the identity. The URL is mutable distribution metadata.

### 5.4 Target

```kotlin
data class CatalogTarget(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
)
```

Target matching is exact and fail closed. Wildcards are deferred.

### 5.5 Availability

```kotlin
enum class CatalogAvailability {
    ACTIVE,
    DEPRECATED,
    REVOKED,
    UNAVAILABLE,
}
```

- `ACTIVE`: downloadable and selectable when compatible.
- `DEPRECATED`: allowed with warning and optional replacement.
- `REVOKED`: new download and selection blocked.
- `UNAVAILABLE`: temporarily not downloadable.

### 5.6 Application-owned profile resolution

Remote catalog data cannot define arbitrary runtime behavior.

```kotlin
interface CatalogProfileResolver {
    fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean
}
```

A later resolver produces or validates application-owned model and use-case profiles. Unknown profile keys fail before download or selection.

## 6. Catalog validation

Validation is fail closed and bounded.

Required checks:

- schema version;
- identifier syntax and length;
- document revision and time window;
- maximum entry count;
- duplicate model/version pairs;
- conflicting digest metadata;
- exact 64-character hexadecimal SHA-256;
- positive bounded byte size;
- HTTPS download URL;
- no URL user information;
- safe `.gguf` file name without path separators;
- non-empty architecture and quantization;
- valid compatibility fields;
- at least one allowed target;
- valid license metadata;
- no release that replaces itself.

Invalid entries cannot become downloadable releases. Document-level trust failure rejects the whole refresh.

## 7. Compatibility evaluation

### 7.1 Device profile

```kotlin
data class CatalogDeviceProfile(
    val sdkInt: Int,
    val supportedAbis: Set<String>,
    val totalMemoryBytes: Long?,
    val availableStorageBytes: Long,
    val harnessVersion: String,
    val backendId: String,
)
```

### 7.2 Hard blockers

- target not allowed;
- revoked or unavailable release;
- unsupported Android API;
- unsupported ABI;
- unsupported backend;
- unsupported Harness version;
- unknown profile key;
- total RAM below hard minimum;
- insufficient storage;
- storage arithmetic overflow.

### 7.3 Warnings

- deprecated release;
- RAM below recommendation but above minimum;
- stale or expiring catalog;
- expected slow performance;
- low remaining storage headroom.

### 7.4 Storage policy

Before download, required storage includes:

```text
partial download copy
+ ModelStore import staging copy
+ catalog-declared minimum free storage
+ configurable safety margin
```

The current implementation uses two artifact-size copies plus a 128 MiB safety margin. This reflects the current `ModelStore.import()` copy behavior. Any future adopt-file or verified-stream optimization requires a separate integrity review.

## 8. Catalog synchronization

### 8.1 Source boundary

```kotlin
interface ModelCatalogSource {
    fun fetch(request: CatalogFetchRequest): CatalogFetchResult
}
```

The HTTP implementation must enforce:

- HTTPS only;
- configured endpoint;
- timeout limits;
- response-size limit;
- conditional requests where available;
- typed status handling;
- cancellation;
- privacy-safe errors;
- no sensitive headers or response bodies in logs.

### 8.2 Repository boundary

```kotlin
interface ModelCatalogRepository {
    fun current(): CatalogSnapshot
    fun replace(document: CatalogModelDocument, metadata: CatalogSyncMetadata)
    fun markRefreshFailure(failure: CatalogFailure)
}
```

Persistence must be app-private, schema-versioned, bounded and atomically replaced. A failed refresh cannot corrupt the last validated snapshot.

### 8.3 Refresh policy

Initial policy:

- explicit user refresh;
- cached catalog readable offline;
- no model download during refresh;
- no periodic background refresh in the first slice;
- stale state shown explicitly;
- installed models remain discoverable when the endpoint is unavailable.

## 9. Secure download engine

### 9.1 Strategy

Use an application-owned download client behind an interface rather than Android `DownloadManager` for the first implementation.

Reasons:

- strict host and redirect policy;
- app-private temporary files;
- integrated byte counting and SHA-256;
- typed phase events;
- cooperative cancellation;
- verification before publication.

The first implementation may use `HttpsURLConnection` behind a transport abstraction.

### 9.2 Contract

```kotlin
interface ModelDownloadClient {
    fun start(
        request: ModelDownloadRequest,
        listener: ModelDownloadListener,
    ): ModelDownloadHandle
}
```

Events include:

- queued;
- started;
- progress;
- verifying;
- inspecting;
- importing;
- installed;
- cancelled;
- failed with stable code.

### 9.3 Network controls

- HTTPS scheme only;
- default-deny host allowlist;
- explicit port policy;
- bounded redirects;
- HTTPS redirect targets only;
- every redirect target allowlisted;
- no URL user information;
- no loopback, local or link-local production hosts;
- connect and read timeouts;
- maximum response size;
- content-length validation when present;
- actual byte-count validation always;
- unexpected content encoding rejected;
- no credentials in persisted state or telemetry.

### 9.4 Temporary files

```text
<app-private>/model-downloads/
    <release-id>/
        artifact.part
        state.tmp
```

Rules:

- partial files never appear in `ModelStore.snapshot()`;
- cancellation closes streams;
- failed size or digest files are deleted;
- stale partials are cleaned during recovery;
- signed URLs and authorization headers are never persisted;
- the first release may restart from zero after process death;
- range resume is deferred until validator support exists.

### 9.5 Concurrency

Initial policy:

- one active model download per app process;
- bounded or rejected additional requests;
- import and removal serialized;
- removal of an installing digest rejected;
- inference using another installed model may continue;
- download cancellation does not cancel inference.

## 10. Verification and installation

Required sequence:

```text
copy to private partial file
    -> count bytes
    -> compare SHA-256
    -> inspect GGUF
    -> compare inspectable metadata
    -> ModelStore.import()
    -> ModelStore.verify()
    -> persist installed release metadata
    -> delete partial state
```

Catalog metadata is descriptive until verified against the artifact where possible.

### 10.1 Installed metadata

Raw model storage intentionally does not own user-facing release metadata.

```kotlin
interface InstalledModelCatalog {
    fun find(digest: ModelDigest): InstalledModelRecord?
    fun upsert(record: InstalledModelRecord)
    fun remove(digest: ModelDigest)
    fun snapshot(): InstalledModelCatalogSnapshot
    fun reconcile(modelStoreSnapshot: ModelStoreSnapshot): ReconciliationResult
}
```

Records contain release identity, digest, display metadata, profile key, timestamps, selected targets and catalog revision. They do not persist private absolute paths.

### 10.2 Reconciliation

- metadata without artifact becomes orphaned and repairable;
- artifact without metadata remains visible as manually imported;
- integrity failure blocks activation;
- duplicate metadata collapses by digest and release identity;
- removed remote release does not silently delete local bytes;
- revocation follows explicit policy.

## 11. Selection and activation

Installation and selection are separate.

```text
installed release
    -> application-owned profile resolution
    -> target authorization
    -> compatibility re-check
    -> explicit user selection
    -> selection persistence
    -> existing runtime preparation on inference
```

Rules:

- installing does not load or activate the model;
- runtime model switches respect existing active-session protection;
- unknown profile keys block selection;
- revoked releases cannot be newly selected;
- replacing selection does not remove the previous artifact;
- selection remains explicit per application/use case.

## 12. Updates, deprecation and revocation

### 12.1 Update detection

An update exists when the same stable model ID has a newer active, allowed and compatible release with a different digest.

Version ordering must use a documented parser rather than lexical comparison.

### 12.2 Side-by-side update

1. Download and verify the new digest.
2. Install it independently.
3. Let the user select it.
4. Retain the previous artifact until explicit removal or approved retention policy.
5. Never delete the only working model before replacement succeeds.

### 12.3 Revocation decision

Before production release, define:

- whether an already-installed revoked model can run offline;
- whether new selection is blocked;
- whether current selection is disabled;
- whether removal is recommended or mandatory;
- how stale offline catalog state affects emergency revocation.

Until approved, new download and new selection fail closed, while local user data is not silently deleted.

## 13. Phone-app UI integration

The connected Models destination must distinguish:

- installed models;
- catalog models available for download;
- incompatible releases;
- active download;
- verification and import phases;
- update available;
- deprecated and revoked releases;
- manually imported artifacts;
- stale or offline catalog.

Before download, show:

- expected download size;
- temporary storage requirement;
- available storage;
- license and source information;
- confirmation that inference remains local after installation.

Progress labels must remain distinct:

```text
Downloading
Verifying file
Inspecting GGUF
Installing locally
Installed
```

Manual SAF import remains an advanced local option.

The existing UX plan must be updated when the UI slice lands because it currently excludes internet model download.

## 14. Observability and privacy

### 14.1 Safe events

- catalog refresh started, completed or failed;
- cached catalog used, stale or expired;
- compatibility evaluated;
- download queued, started, progressed, completed, cancelled or failed;
- size and digest verification result;
- GGUF inspection result;
- import result;
- metadata reconciliation result;
- update, deprecation or revocation detected.

### 14.2 Allowed fields

- catalog ID and revision;
- stable release ID;
- expected and actual byte counts;
- duration;
- HTTP status class;
- typed error code;
- compatibility reason codes;
- lifecycle phase.

### 14.3 Forbidden fields

- signed URLs;
- authorization headers;
- cookies;
- private file paths;
- arbitrary server bodies;
- GGUF bytes;
- prompts or generated output;
- unrestricted exception messages.

Health checks remain observational unless explicit repair is invoked.

## 15. Security model

Untrusted inputs include catalog bytes, strings, numbers, URLs, redirects, headers, file names, model bytes and partial state.

Mandatory protections:

- bounded schema and strings;
- bounded entry count and response size;
- strict SHA-256 syntax;
- positive size with overflow checks;
- HTTPS and host allowlist;
- no destination path derived from server file name;
- no final publication before verification;
- application-owned profile allowlist;
- fixed privacy-safe error mapping;
- signed catalog before production third-party distribution;
- key rotation and rollback protection.

Adding model download also requires explicit review of:

- `android.permission.INTERNET`;
- cleartext-traffic prohibition;
- Android network-security configuration;
- app backup/no-backup placement;
- Play privacy disclosures;
- managed-network behavior;
- offline-first operation after installation.

No remote inference capability is introduced.

## 16. Stable error model

Catalog errors include:

```text
NETWORK_UNAVAILABLE
TIMEOUT
HTTP_REJECTED
RESPONSE_TOO_LARGE
MALFORMED_DOCUMENT
UNSUPPORTED_SCHEMA
TRUST_FAILURE
EXPIRED_DOCUMENT
ROLLBACK_REJECTED
PERSISTENCE_FAILURE
```

Download errors include:

```text
BUSY
NOT_AUTHORIZED_FOR_TARGET
INCOMPATIBLE_DEVICE
INVALID_DOWNLOAD_URI
REDIRECT_REJECTED
NETWORK_UNAVAILABLE
TIMEOUT
HTTP_REJECTED
UNEXPECTED_CONTENT_ENCODING
RESPONSE_TOO_LARGE
INSUFFICIENT_STORAGE
SIZE_MISMATCH
DIGEST_MISMATCH
INVALID_GGUF
METADATA_MISMATCH
IMPORT_FAILURE
VERIFICATION_FAILURE
CANCELLED
INTERNAL_FAILURE
```

User-visible messages are stable and privacy-safe.

## 17. Testing strategy

### 17.1 Catalog unit tests

- valid document;
- unsupported schema;
- invalid time window and expiry;
- duplicate release;
- conflicting digest metadata;
- invalid digest and size;
- non-HTTPS URL;
- unsafe file name;
- invalid targets and profile key;
- active, deprecated, unavailable and revoked behavior;
- exact target filtering;
- API, ABI, backend and Harness compatibility;
- RAM minimum and warning;
- storage requirement and overflow;
- version ordering.

### 17.2 Download unit tests

Use fake transport and temporary directories for:

- successful transfer;
- unknown content length;
- header and actual size mismatch;
- maximum byte limit;
- cancellation before and during copy;
- timeout and HTTP rejection;
- redirect limit and host rejection;
- unexpected encoding;
- insufficient storage;
- digest mismatch;
- cleanup after every failure;
- concurrent request policy;
- no `ModelStore` visibility before import.

### 17.3 Integration tests

- downloaded artifact imported through real `FileSystemModelStore`;
- already-installed digest deduplicated;
- destination conflicts propagated;
- post-import verification;
- metadata written only after success;
- metadata/artifact reconciliation;
- side-by-side replacement;
- removal and active-model protection.

### 17.4 Android tests

- catalog survives Activity recreation;
- no download from navigation or refresh alone;
- confirmation and progress states;
- cancellation and retry;
- process restart cleanup;
- offline and storage errors;
- installed model works offline;
- privacy-safe state and accessibility.

### 17.5 Physical-device evidence

Before production readiness:

- download a real supported GGUF over HTTPS;
- cancel an active transfer;
- recover after network loss;
- verify exact size and digest;
- install into app-private storage;
- select and run inference offline;
- install an update side by side;
- remove a non-active model;
- confirm partial-file cleanup;
- capture resource baselines.

## 18. Implementation phases

### Status legend

- `[ ]` not started;
- `[-]` in progress or awaiting validation;
- `[x]` implemented and validated;
- `[!]` blocked by a decision.

### Phase 0 — Main audit and architecture

- [x] audit current `main`;
- [x] inspect existing model store and phone app;
- [x] identify unmerged overlap;
- [x] create branch from current `main`;
- [x] rewrite the plan from `main`;
- [-] validate and review ADR 0005.

### Phase 1 — Catalog domain and compatibility

- [-] register `models/model-catalog`;
- [-] define document and release contracts;
- [-] define target and profile resolver boundaries;
- [-] implement fail-closed validation;
- [-] implement exact target filtering;
- [-] implement device compatibility;
- [-] add deterministic tests;
- [ ] pass repository CI;
- [ ] document stable public API.

### Phase 2 — Catalog codec and persistence

- [ ] add bounded deterministic JSON codec;
- [ ] add source and repository interfaces;
- [ ] persist app-private validated snapshot;
- [ ] implement revision, expiry and stale state;
- [ ] add ETag/Last-Modified metadata;
- [ ] ensure failed refresh cannot corrupt cache;
- [ ] add tests and safe events.

### Phase 3 — Secure download

- [ ] add `models/model-download`;
- [ ] define request, event, handle and errors;
- [ ] implement HTTPS transport boundary;
- [ ] enforce host and redirect policy;
- [ ] implement timeouts and response limits;
- [ ] implement partial files and cancellation;
- [ ] stream byte count and SHA-256;
- [ ] serialize mutations;
- [ ] add deterministic tests.

### Phase 4 — Verified installation and metadata

- [ ] inspect GGUF before registration;
- [ ] compare inspectable metadata;
- [ ] import through existing `ModelStore`;
- [ ] verify after import;
- [ ] add installed release metadata;
- [ ] reconcile metadata and store;
- [ ] retain manual-import visibility;
- [ ] add integration tests.

### Phase 5 — Phone-app integration

- [ ] wire catalog services into phone app;
- [ ] preserve manual SAF import;
- [ ] expose refresh and stale states;
- [ ] expose confirmation, progress and cancellation;
- [ ] implement application-owned profile resolution;
- [ ] separate install and selection;
- [ ] persist selection per target;
- [ ] run selected model through existing playground offline.

### Phase 6 — Updates and lifecycle

- [ ] implement version ordering;
- [ ] detect compatible updates;
- [ ] install side by side;
- [ ] render deprecation;
- [ ] define revocation policy;
- [ ] protect active models from removal;
- [ ] add lifecycle tests.

### Phase 7 — Observability and health

- [ ] emit safe structured events;
- [ ] add catalog-cache health;
- [ ] add partial-download health;
- [ ] add metadata reconciliation health;
- [ ] add explicit cleanup and repair;
- [ ] verify no secret or private-path leakage.

### Phase 8 — Trust hardening

- [ ] select signature algorithm and canonical encoding;
- [ ] verify trusted key IDs;
- [ ] implement key rotation;
- [ ] implement rollback protection;
- [ ] add tampering tests;
- [ ] review network and Play privacy configuration.

### Phase 9 — Cumulative validation

- [ ] repository guards;
- [ ] Spotless and ktlint;
- [ ] Detekt;
- [ ] catalog unit tests;
- [ ] Android Lint;
- [ ] downstream application compilation;
- [ ] native tests and packaging;
- [ ] Android integration tests;
- [ ] real-device evidence;
- [ ] roadmap and Definition of Done update.

## 19. Pull-request sequence

Prefer reviewable vertical slices from `main`:

1. **Catalog domain and ADR**
   Contracts, validation, filtering and tests.
2. **Catalog persistence and compatibility**
   Codec, cache, synchronization and device policy.
3. **Secure download and installation**
   Transport, progress, cancellation, hashing, GGUF inspection and `ModelStore` integration.
4. **Installed metadata and selection**
   Durable release metadata, reconciliation and application-owned profile mapping.
5. **Connected Models UI**
   Phone-app wiring, confirmation, progress, selection and offline inference.
6. **Updates, health and trust hardening**
   Lifecycle policy, repair, signatures and physical evidence.

Do not accumulate the whole feature in one unreviewable pull request.

## 20. Definition of Done

The complete feature requires:

- implementation based on current `main`;
- no dependency on historical branches;
- validated cached catalog available offline;
- exact authorized target filtering;
- compatibility checked before transfer;
- explicit, cancellable and bounded download;
- fail-closed HTTPS, redirect and host policy;
- exact byte count and SHA-256 verification;
- GGUF inspection before installed metadata;
- final publication through existing `ModelStore`;
- URL never used as model identity;
- installed metadata reconciled with store contents;
- install and selection separated;
- selected model runs offline through existing runtime;
- explicit update, deprecation and revocation behavior;
- privacy-safe telemetry;
- documented contracts and failure modes;
- all repository gates green;
- physical-device evidence recorded;
- authoritative roadmap updated.

## 21. Open decisions

| Decision | Status | Required before |
|---|---:|---|
| Production endpoint and host allowlist | `[!]` | Real network integration |
| Catalog authentication | `[!]` | Private catalogs |
| Signature algorithm and canonical encoding | `[!]` | Production hardening |
| Key rotation and rollback policy | `[!]` | Production hardening |
| Offline behavior for revoked installed models | `[!]` | Lifecycle implementation |
| Hard RAM policy per release | `[!]` | Production compatibility policy |
| Exact storage safety margin | `[-]` | Download release |
| Catalog persistence format | `[ ]` | Phase 2 |
| Process-death resume requirement | `[ ]` | Download contract finalization |
| PR #34 recovery or replacement | `[!]` | Model-management consolidation |
| PR #40 integration sequence | `[!]` | Connected Compose UI |

Decisions not required for the current slice do not block pure contracts and deterministic tests.

## 22. Progress log

| Date | Branch or commit | Update | Validation |
|---|---|---|---|
| 2026-08-04 | `main` at `dfba2a05...` | Audited merged runtime, store, phone app and UX direction | Source and merged PR state inspected |
| 2026-08-04 | `agent/model-catalog-download-implementation` | Created a fresh feature branch from current `main` | Branch confirmed |
| 2026-08-04 | PR #41 | Added catalog domain, validation, compatibility, tests, ADR and CI coverage | Repository validation in progress |

The separate progress file is updated after every validated implementation slice. A task becomes `[x]` only after tests and acceptance criteria pass.
