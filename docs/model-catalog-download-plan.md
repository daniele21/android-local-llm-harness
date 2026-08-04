# Admin-managed model catalog and secure GGUF download plan

**Status:** Active implementation tracker  
**Canonical base:** `main`  
**Base commit audited:** `dfba2a05ed8166ef79a12261089078e13fd3902e`  
**Implementation branch:** `agent/model-catalog-download-implementation`  
**Last updated:** 2026-08-04

## 1. Purpose

This document defines and tracks the implementation of an administrator-managed catalog of downloadable GGUF models for the Android Local LLM Harness.

An administrator publishes a controlled set of model artifacts. The Android application retrieves that catalog, shows only entries allowed for the current application and use case, evaluates device compatibility, lets the user explicitly download a model, verifies the downloaded bytes and installs the artifact into the existing content-addressed `ModelStore`.

The feature must preserve the repository's existing model identity and runtime invariants:

```text
applicationId + useCaseId
        -> explicit application-owned binding/profile policy
        -> exact GGUF artifact digest
        -> verified content-addressed ModelStore object
        -> RuntimeOrchestrator
        -> llama.cpp
```

A download URL is only a distribution location. It is never a model identity and must never be passed directly to the inference backend.

---

## 2. Main-branch baseline audit

This plan is based only on the current `main` branch. Historical feature branches are not implementation bases.

### 2.1 Existing foundations in `main`

The following capabilities already exist and must be reused rather than duplicated:

- [x] pinned `llama.cpp` Android backend;
- [x] GGUF metadata inspection without full model loading;
- [x] `GgufArtifact`, model profile and application/use-case binding concepts;
- [x] streaming SHA-256 content-addressed model import;
- [x] deterministic artifact path derived from SHA-256;
- [x] size and digest verification during import;
- [x] existing-object deduplication and destination conflict protection;
- [x] explicit `find`, `verify`, `remove` and `snapshot` operations on `ModelStore`;
- [x] atomic or fail-closed publication into app-private model storage;
- [x] embedded runtime orchestration, generation, streaming and cancellation;
- [x] model integrity cache and health checks;
- [x] model inventory presentation through console boundaries;
- [x] Play-installable Android application with Storage Access Framework GGUF import;
- [x] manual on-device inference playground using a selected imported GGUF;
- [x] privacy-safe telemetry, health, resource and benchmark foundations;
- [x] a repository-approved Harness UX/UI implementation plan targeting `apps/local-llm-phone-test`.

### 2.2 Current `ModelStore` boundary

The current contract is intentionally artifact-oriented:

```kotlin
interface ModelStore {
    fun find(digest: ModelDigest): StoredModel?
    fun import(source: File, artifact: GgufArtifact): StoredModel
    fun verify(digest: ModelDigest): VerificationResult
    fun remove(digest: ModelDigest): Boolean
    fun snapshot(): ModelStoreSnapshot
}
```

This boundary remains authoritative for installed GGUF bytes.

The catalog implementation must not:

- create another final artifact directory;
- invent another digest identity;
- write directly into `models/sha256/...`;
- duplicate `FileSystemModelStore` hashing, conflict or atomic-publication logic;
- mark a model installed before `ModelStore.import()` completes successfully.

### 2.3 Missing capabilities on `main`

The following do not yet exist on `main`:

- [ ] a remote model-catalog contract;
- [ ] a catalog parser and validator;
- [ ] catalog synchronization and local cache;
- [ ] persistent user-facing model metadata beyond the raw artifact store;
- [ ] application/use-case filtering of remote entries;
- [ ] device compatibility evaluation before download;
- [ ] an HTTPS model downloader;
- [ ] partial-download state and cleanup policy;
- [ ] explicit download cancellation and progress reporting;
- [ ] remote byte-size and SHA-256 enforcement before installation;
- [ ] update, replacement, deprecation and revocation semantics;
- [ ] downloaded-model cache health;
- [ ] catalog and download screens connected to the real phone runtime;
- [ ] manifest authenticity beyond ordinary HTTPS transport;
- [ ] real-device evidence for remote download and installation.

### 2.4 Overlapping work that is not part of `main`

Open draft branches and pull requests must not be treated as dependencies.

In particular:

- PR #34 contains standalone-console model mutation work but is not merged;
- PR #40 contains a large connected Compose implementation but is not merged;
- the current `main` UX plan explicitly excluded internet model download.

Therefore this feature must:

1. compile and test against `main` alone;
2. avoid copying code from unmerged branches unless it is deliberately recovered and reviewed;
3. keep UI-independent catalog/download logic outside app screens;
4. make later integration with the connected Compose work possible without depending on it;
5. update the UX scope when the actual download UI is introduced.

---

## 3. Product behavior

### 3.1 Administrator behavior

An administrator can publish a versioned catalog containing approved model releases.

For every release the administrator defines:

- stable catalog model ID;
- release version;
- user-facing name and description;
- exact GGUF SHA-256;
- exact expected byte size;
- HTTPS download URL;
- architecture and quantization metadata;
- supported application IDs and use-case IDs;
- minimum Android API;
- supported ABIs;
- minimum and recommended memory;
- minimum required free storage;
- compatible Harness/runtime version range;
- license identifier and attribution/source links;
- lifecycle state such as active, deprecated, revoked or unavailable;
- optional replacement release;
- profile key understood by the application.

The remote catalog is data. It must not be able to inject executable code, arbitrary file paths, arbitrary Android intents or unrestricted runtime configuration.

### 3.2 User behavior

The user can:

1. refresh the approved catalog;
2. inspect model name, size, quantization, compatibility and license information;
3. see why an incompatible model cannot be downloaded;
4. explicitly start a download;
5. observe byte and percentage progress when total size is known;
6. cancel an active download;
7. retry a recoverable failure;
8. see verification and installation phases separately;
9. select an installed compatible model for an application-owned use case;
10. verify or remove an installed model through existing model-management capabilities;
11. see update, deprecation or revocation states.

The application must not silently download large model files on refresh, navigation or startup.

### 3.3 Canonical state flow

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

Terminal or recoverable states:

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
REVOKED
DEPRECATED
UPDATE_AVAILABLE
```

A model is `INSTALLED` only when the exact digest exists in `ModelStore` and passes the required installation verification.

---

## 4. Architecture

### 4.1 Target module boundaries

Introduce modules only when their implementation is added:

```text
models/
├── model-profile        existing
├── model-store          existing final artifact owner
├── model-catalog        new pure catalog domain and policy
└── model-download       new Android download and installation orchestration
```

Potential later module, only if persistence grows beyond the first implementation:

```text
models/model-catalog-store
```

The initial implementation may keep catalog persistence inside `model-catalog` behind a neutral repository interface. Android-specific persistence must remain outside pure domain types.

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
        +--> model-profile identifiers where required

model-store
        X--> model-catalog
        X--> networking
        X--> UI
```

`core/runtime-core` must not depend on remote URLs, HTTP clients, catalog JSON or Android download state.

### 4.3 Data plane separation

```text
Remote catalog and download control plane
        -> verified installed artifact
        -> existing local inference data plane
```

Inference remains possible without network access after a model is installed.

Catalog refresh or download failure must not break already-installed model inference.

### 4.4 First connected product surface

The first connected implementation targets:

```text
apps/local-llm-phone-test
```

Reasoning:

- it is already Play-installable;
- it owns the real app-private `FileSystemModelStore`;
- it already imports and runs external GGUF files;
- it already provides the manual playground;
- the merged UX plan designates it as the first connected Harness surface.

The standalone `apps/local-llm-console` may consume the same neutral contracts later, but cross-application model installation remains deferred until the protected diagnostics/shared-runtime architecture exists.

---

## 5. Domain model

### 5.1 Catalog document

Proposed logical shape:

```json
{
  "schemaVersion": 1,
  "catalogId": "harness-public-models",
  "revision": 42,
  "generatedAtEpochMs": 1785880800000,
  "expiresAtEpochMs": 1786485600000,
  "entries": []
}
```

Required document rules:

- supported `schemaVersion`;
- non-empty stable `catalogId`;
- monotonically comparable revision;
- bounded number of entries;
- unique release identity;
- no duplicate model ID and version pairs;
- no duplicate artifact digest with conflicting size;
- valid temporal window;
- unknown fields ignored only when forward-compatible;
- invalid required fields reject the affected entry;
- document-level trust failure rejects the whole refresh.

### 5.2 Catalog release

Proposed Kotlin domain model:

```kotlin
data class CatalogModelRelease(
    val modelId: CatalogModelId,
    val version: CatalogModelVersion,
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

Artifact metadata:

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

The URI is never used as identity. Equality and installation correlation use the digest.

### 5.3 Allowed target

```kotlin
data class CatalogTarget(
    val applicationId: String,
    val useCaseId: String,
)
```

Filtering must be exact and fail closed. Wildcards are deferred unless a concrete product need is documented.

### 5.4 Availability

```kotlin
enum class CatalogAvailability {
    ACTIVE,
    DEPRECATED,
    REVOKED,
    UNAVAILABLE,
}
```

Semantics:

- `ACTIVE`: can be offered and downloaded;
- `DEPRECATED`: installed use may continue, but the UI recommends replacement;
- `REVOKED`: new download and activation are blocked; already-installed handling follows explicit policy;
- `UNAVAILABLE`: temporarily not downloadable, without implying a security incident.

### 5.5 Application-owned profile mapping

The catalog must not define arbitrary prompts or unrestricted runtime settings.

The application owns a registry such as:

```kotlin
interface CatalogProfileResolver {
    fun resolve(
        release: CatalogModelRelease,
        target: CatalogTarget,
    ): ResolvedCatalogProfile
}
```

`profileKey` selects an application-reviewed configuration template.

The resolver is responsible for producing or validating:

- `GgufModelProfile`;
- context-size bounds;
- CPU thread bounds;
- GPU-layer policy;
- chat template policy;
- use-case prompt and generation policy;
- explicit application/use-case binding.

Unknown profile keys fail closed before download or activation.

---

## 6. Catalog synchronization

### 6.1 Catalog source

```kotlin
interface ModelCatalogSource {
    fun fetch(request: CatalogFetchRequest): CatalogFetchResult
}
```

The HTTP implementation must support:

- HTTPS only;
- configured catalog endpoint rather than user-entered arbitrary URLs;
- connect and read timeouts;
- response-size limit;
- conditional requests through ETag and/or Last-Modified when available;
- explicit status handling;
- cancellation;
- fixed privacy-safe errors;
- no prompt, generated output or model contents in logs.

### 6.2 Local catalog repository

```kotlin
interface ModelCatalogRepository {
    fun current(): CatalogSnapshot
    fun replace(document: ModelCatalogDocument, metadata: CatalogSyncMetadata)
    fun markRefreshFailure(failure: CatalogFailure)
    fun clearExpiredTemporaryState()
}
```

The persisted snapshot must contain enough information to render a previously validated catalog offline.

Persistence must be:

- app-private;
- schema-versioned;
- written through temporary file plus atomic replacement where supported;
- bounded;
- recoverable when the newest file is malformed;
- independent from model bytes;
- free of secrets and authentication tokens.

### 6.3 Refresh policy

Initial policy:

- user-triggered refresh is always available;
- application startup may read the cached catalog but does not automatically download models;
- optional foreground refresh can occur only when stale and explicitly enabled;
- no background periodic refresh in the first slice;
- expired cached entries remain visible with an explicit stale state but cannot authorize a new download when policy requires a fresh catalog;
- installed models remain discoverable from local metadata even when the catalog endpoint is unavailable.

---

## 7. Compatibility evaluation

### 7.1 Device inputs

```kotlin
data class CatalogDeviceProfile(
    val sdkInt: Int,
    val supportedAbis: Set<String>,
    val totalMemoryBytes: Long?,
    val availableStorageBytes: Long,
    val harnessVersion: String,
    val backendId: String,
    val backendVersion: String,
)
```

### 7.2 Compatibility result

```kotlin
data class CatalogCompatibilityResult(
    val compatible: Boolean,
    val reasons: List<CatalogCompatibilityReason>,
    val warnings: List<CatalogCompatibilityWarning>,
)
```

Hard blockers:

- unsupported Android API;
- no compatible ABI;
- unsupported Harness version;
- unsupported backend/profile key;
- insufficient storage including staging overhead;
- known unsupported architecture or quantization;
- revoked release;
- target not authorized.

Warnings:

- total RAM below recommendation but above hard minimum;
- catalog nearing expiry;
- deprecated release;
- expected slow performance;
- model size close to remaining storage budget.

### 7.3 Storage calculation

Before download, require at least:

```text
expected GGUF size
+ partial/staging file overhead
+ ModelStore import staging overhead
+ configurable safety margin
```

Because the current `ModelStore.import()` copies the source into its own staging file, the first implementation may temporarily require close to two additional copies during installation. The UI and compatibility policy must account for this honestly.

A later optimization may add a verified-stream or adopt-file import API, but that change must preserve `ModelStore` integrity guarantees and requires its own design review.

---

## 8. Download engine

### 8.1 First implementation strategy

Use an explicit application-owned download engine rather than Android `DownloadManager`.

Reasoning:

- exact redirect and host policy is required;
- byte-size and SHA-256 must be computed under Harness control;
- temporary files must remain app-private;
- cancellation and phase reporting must integrate with installation;
- model bytes must never be published before verification;
- download state must map to typed domain events.

The first slice may use `HttpsURLConnection` behind a transport interface to avoid coupling domain logic to a third-party HTTP library.

### 8.2 Download contract

```kotlin
interface ModelDownloadClient {
    fun start(
        request: ModelDownloadRequest,
        listener: ModelDownloadListener,
    ): ModelDownloadHandle
}
```

Events:

```kotlin
sealed interface ModelDownloadEvent {
    data object Queued : ModelDownloadEvent
    data class Started(val totalBytes: Long?) : ModelDownloadEvent
    data class Progress(val downloadedBytes: Long, val totalBytes: Long?) : ModelDownloadEvent
    data object Verifying : ModelDownloadEvent
    data object Inspecting : ModelDownloadEvent
    data object Importing : ModelDownloadEvent
    data class Installed(val digest: ModelDigest) : ModelDownloadEvent
    data object Cancelled : ModelDownloadEvent
    data class Failed(val code: ModelDownloadErrorCode) : ModelDownloadEvent
}
```

### 8.3 Network policy

Mandatory controls:

- only `https` scheme;
- default-deny host allowlist;
- explicit port policy;
- maximum redirect count;
- redirect only to HTTPS;
- redirect target must remain allowlisted;
- no URL user info;
- no local, loopback or link-local hosts in production configuration;
- connect/read timeout;
- maximum response size;
- `Content-Length` validation when present;
- actual byte count validation regardless of header;
- no transparent content decompression for GGUF artifacts;
- reject unexpected content encoding;
- fixed user agent without sensitive identifiers;
- authentication headers supplied only by a scoped provider and never persisted.

### 8.4 Partial files

Temporary layout:

```text
<app files or no-backup>/model-downloads/
    <release-id>/
        artifact.part
        state.tmp
```

Rules:

- a partial file is never visible through `ModelStore.snapshot()`;
- cancellation closes streams promptly;
- failed digest/size files are deleted;
- stale partial files are removed by explicit maintenance and startup recovery;
- partial state contains no signed URLs or authorization headers;
- first release may restart from zero after process death;
- HTTP range resume is a later slice and requires validator support such as ETag.

### 8.5 Concurrency

Initial policy:

- one active model download per app process;
- additional requests receive a typed busy result or enter a bounded queue;
- model inference may continue using an already-installed model while another model downloads, subject to storage and memory policies;
- installation/import is serialized with other model mutations;
- removal of the digest being installed is rejected;
- download cancellation does not cancel inference.

---

## 9. Verification and installation

### 9.1 Required sequence

```text
network copy to private partial file
    -> exact byte count
    -> SHA-256 comparison
    -> GGUF metadata inspection
    -> catalog metadata consistency checks
    -> ModelStore.import(partial, GgufArtifact)
    -> ModelStore.verify(digest)
    -> installed metadata update
    -> partial cleanup
```

### 9.2 GGUF consistency

At minimum compare inspected metadata with catalog policy for:

- valid GGUF structure;
- supported architecture;
- quantization when reliably available;
- context or tensor metadata needed by the backend;
- chat-template presence only when required by the application profile.

Catalog metadata is descriptive until verified against the artifact where possible.

### 9.3 Installed metadata repository

The raw `ModelStore` intentionally does not persist user-facing release metadata.

Add a separate repository:

```kotlin
interface InstalledModelCatalog {
    fun find(digest: ModelDigest): InstalledModelRecord?
    fun upsert(record: InstalledModelRecord)
    fun remove(digest: ModelDigest)
    fun snapshot(): InstalledModelCatalogSnapshot
    fun reconcile(modelStoreSnapshot: ModelStoreSnapshot): ReconciliationResult
}
```

Suggested record:

```kotlin
data class InstalledModelRecord(
    val releaseId: CatalogReleaseId,
    val digest: ModelDigest,
    val displayName: String,
    val version: String,
    val architecture: String,
    val quantization: String,
    val sizeBytes: Long,
    val profileKey: ModelProfileKey,
    val installedAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long,
    val selectedTargets: Set<CatalogTarget>,
    val catalogRevision: Long,
)
```

This repository must not store private absolute file paths. The digest resolves the artifact through `ModelStore`.

### 9.4 Reconciliation

On startup or explicit refresh:

- metadata record with missing artifact -> mark orphaned metadata and repair/remove;
- artifact without metadata -> show as manually imported/unknown catalog artifact;
- digest mismatch -> fail integrity and block activation;
- duplicate records -> collapse by digest and release identity;
- catalog release removed -> retain installed metadata with unavailable status;
- revoked digest -> apply explicit revocation policy.

---

## 10. Selection and activation

Downloading and selecting are separate actions.

Installation must not automatically replace an active model binding.

Selection flow:

```text
installed release
    -> application-owned profile resolution
    -> target authorization
    -> compatibility re-check
    -> explicit user selection
    -> binding/selection persistence
    -> runtime prepare on next explicit inference
```

Rules:

- runtime model switches remain governed by existing active-session protection;
- selection does not load a model immediately;
- unknown or changed profile key blocks selection;
- revoked releases cannot become newly selected;
- replacing the selected model must not remove the old artifact automatically;
- installed models can serve different application-owned targets only when explicitly allowed.

---

## 11. Update, deprecation and revocation

### 11.1 Update detection

An update is available when:

- the same stable model ID has a newer allowed release;
- the newer release has a different digest;
- the current target and device remain compatible;
- the release is active.

Version ordering must use a documented parser. Do not rely on lexical string comparison.

### 11.2 Replacement policy

Updates are side-by-side installs:

1. download and verify the new digest;
2. install it independently;
3. allow explicit selection;
4. keep the previous artifact until the user removes it or retention policy is applied;
5. never delete the only working model before the replacement is installed and selectable.

### 11.3 Revocation policy

The first production policy must explicitly decide:

- whether revoked installed models remain runnable offline;
- whether only new selection is blocked;
- whether current selection is disabled;
- whether removal is recommended or mandatory;
- how emergency catalog trust works when the device is offline.

Until that decision is approved, implementation must fail safe for new downloads and new selections without silently deleting local user data.

---

## 12. UI integration

### 12.1 Models destination

The connected Models screen must distinguish:

- installed models;
- available catalog models;
- incompatible catalog models;
- active download;
- verification/import phase;
- update available;
- deprecated/revoked models;
- manually imported artifacts without catalog metadata;
- stale/offline catalog state.

### 12.2 Model card

Minimum content:

- display name;
- model/release version;
- architecture and quantization;
- download or installed size;
- compatibility status;
- license summary;
- installed/selected/update state;
- exact action appropriate for the current state.

Do not display raw full SHA-256 by default. Provide a shortened identifier and an explicit details view.

### 12.3 Download confirmation

Before starting:

- display exact expected download size;
- display temporary storage requirement;
- display available storage;
- explain that inference remains local after installation;
- show license/source information;
- require explicit confirmation.

### 12.4 Progress UI

Show distinct labels:

```text
Downloading
Verifying file
Inspecting GGUF
Installing locally
Installed
```

Do not show 100% downloaded as installed while hashing or import is still running.

### 12.5 Existing UX plan update

The merged Harness UX/UI plan currently excludes internet model download. When this implementation reaches the UI slice, update that document to:

- include catalog-backed models;
- preserve manual SAF import as an advanced/local option;
- define offline and stale-catalog states;
- add download confirmation and progress;
- document network permission and privacy changes;
- retain GGUF-only scope.

---

## 13. Observability and privacy

### 13.1 Structured events

Record privacy-safe events such as:

- catalog refresh started/completed/failed;
- catalog cache used/stale/expired;
- compatibility evaluated;
- download queued/started/progress/completed/cancelled/failed;
- byte-size verification passed/failed;
- SHA-256 verification passed/failed;
- GGUF inspection passed/failed;
- import started/completed/failed;
- installed metadata reconciled;
- update/deprecation/revocation detected.

### 13.2 Allowed fields

- catalog ID and revision;
- stable release ID;
- shortened or structured digest identifier where policy permits;
- expected and actual byte counts;
- duration;
- HTTP status class, not sensitive response body;
- typed error code;
- compatibility reason codes;
- phase and terminal status.

### 13.3 Forbidden fields

- signed URLs;
- query parameters containing credentials;
- authorization headers;
- cookies;
- private file paths;
- arbitrary server response bodies;
- GGUF bytes;
- prompts or generated output;
- stack traces in normal telemetry;
- unrestricted exception messages.

### 13.4 Health and cache integration

Add later health checks for:

- catalog cache validity;
- installed metadata vs `ModelStore` reconciliation;
- stale partial downloads;
- catalog-selected digest integrity;
- revoked selected release;
- download directory size and orphaned state.

Checks remain observational unless an explicit repair capability is invoked.

---

## 14. Security model

### 14.1 Trust boundaries

Untrusted inputs include:

- catalog response bytes;
- all catalog strings and numbers;
- redirects;
- HTTP headers;
- downloaded model bytes;
- server-provided file names;
- license/source URLs;
- stale local partial state.

### 14.2 Mandatory validation

- strict schema bounds;
- maximum string lengths;
- maximum entries per catalog;
- exact SHA-256 syntax;
- positive bounded size;
- HTTPS URI validation;
- host allowlist;
- file name normalization without path separators;
- no destination path derived from server file name;
- explicit integer-overflow checks;
- no trust in `Content-Length` alone;
- GGUF inspection before install registration;
- catalog target and profile-key allowlists;
- fixed error mapping.

### 14.3 Manifest authenticity

Implementation stages:

1. development fixture and ordinary HTTPS transport;
2. production endpoint with strict host policy;
3. detached or embedded signed catalog document;
4. embedded public-key trust store with key IDs and rotation policy;
5. rollback protection through revision and issuance metadata.

The feature is not production-ready for third-party catalog distribution until signed-manifest verification and key rotation are validated.

### 14.4 Network permission

`apps/local-llm-phone-test` currently operates without internet permission for its local model path.

Adding catalog download requires an explicit review of:

- `android.permission.INTERNET`;
- network security configuration;
- cleartext traffic disabled;
- backup/no-backup location for catalog and partial state;
- Play privacy disclosures;
- behavior when a managed device blocks the endpoint;
- offline-first behavior after installation.

No analytics permission or remote inference endpoint is introduced by this feature.

---

## 15. Error model

Proposed stable codes:

```kotlin
enum class CatalogErrorCode {
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    HTTP_REJECTED,
    RESPONSE_TOO_LARGE,
    MALFORMED_DOCUMENT,
    UNSUPPORTED_SCHEMA,
    TRUST_FAILURE,
    EXPIRED_DOCUMENT,
    ROLLBACK_REJECTED,
    PERSISTENCE_FAILURE,
}

enum class ModelDownloadErrorCode {
    BUSY,
    NOT_AUTHORIZED_FOR_TARGET,
    INCOMPATIBLE_DEVICE,
    INVALID_DOWNLOAD_URI,
    REDIRECT_REJECTED,
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    HTTP_REJECTED,
    UNEXPECTED_CONTENT_ENCODING,
    RESPONSE_TOO_LARGE,
    INSUFFICIENT_STORAGE,
    SIZE_MISMATCH,
    DIGEST_MISMATCH,
    INVALID_GGUF,
    METADATA_MISMATCH,
    IMPORT_FAILURE,
    VERIFICATION_FAILURE,
    CANCELLED,
    INTERNAL_FAILURE,
}
```

User-visible messages must be stable and privacy-safe. Diagnostic details may use structured safe fields but not arbitrary exception text.

---

## 16. Testing strategy

### 16.1 `model-catalog` unit tests

- valid document parsing;
- unsupported schema;
- missing required fields;
- duplicate release identity;
- conflicting digest metadata;
- invalid SHA-256;
- invalid size;
- non-HTTPS URI;
- target filtering;
- profile-key rejection;
- active/deprecated/revoked semantics;
- revision rollback;
- expiry handling;
- persistence atomicity and recovery;
- compatibility reason ordering;
- version ordering.

### 16.2 `model-download` unit tests

Use fake transport and temporary files for:

- successful download;
- progress ordering;
- unknown content length;
- content length mismatch;
- downloaded byte limit;
- cancellation before connect;
- cancellation during copy;
- timeout;
- rejected status;
- redirect limit;
- redirect host rejection;
- content encoding rejection;
- insufficient storage;
- SHA-256 mismatch;
- cleanup after every failure;
- concurrent request policy;
- no final-store visibility before import.

### 16.3 Installation integration tests

- real `FileSystemModelStore` import from downloaded temporary artifact;
- deduplication when digest is already installed;
- destination conflict propagation;
- post-import verification;
- installed metadata write only after success;
- reconciliation of artifact without metadata;
- reconciliation of metadata without artifact;
- replacement side-by-side installation;
- removal interaction;
- selected/loaded model protection.

### 16.4 HTTP test server

Use an in-process deterministic test server or a narrow fake socket server to validate:

- status codes;
- redirects;
- truncated response;
- delayed response;
- range behavior when later implemented;
- incorrect content length;
- unexpected encoding;
- connection interruption.

Avoid live internet dependencies in unit and CI tests.

### 16.5 Android tests

- catalog cache survives Activity recreation;
- explicit download confirmation;
- progress and cancellation states;
- no download on navigation or refresh alone;
- process restart recovery/cleanup policy;
- network unavailable state;
- storage unavailable state;
- selected model remains usable offline;
- prompt/output privacy remains unchanged;
- accessibility labels and dynamic text;
- compact and expanded layout behavior when the Compose surface is available.

### 16.6 Physical-device evidence

Required before production readiness:

- download a real supported GGUF over HTTPS;
- cancel an active model download;
- recover after network loss;
- verify exact digest and size;
- install into app-private content-addressed storage;
- select and run inference offline;
- install an update side-by-side;
- remove a non-active model;
- confirm storage cleanup;
- capture download, install and inference resource baselines;
- validate on a managed/restricted network where possible.

---

## 17. Implementation phases and progress

### Status legend

| Marker | Meaning |
|---|---|
| `[ ]` | not started |
| `[-]` | in progress |
| `[x]` | completed and validated |
| `[!]` | blocked or awaiting a decision |

### Phase 0 — main audit and branch control

- [x] confirm `main` is the canonical base;
- [x] audit current roadmap and merged capabilities;
- [x] audit `ModelStore` and `FileSystemModelStore` behavior;
- [x] identify existing phone app and UX-plan integration point;
- [x] identify PR #34 and PR #40 as unmerged, non-authoritative overlap;
- [x] create `agent/model-catalog-download-implementation` from current `main`;
- [x] rewrite this plan against the current main baseline;
- [ ] add an ADR for catalog/download ownership and trust boundaries.

**Phase status:** `[-]`

### Phase 1 — catalog contracts and validation

- [ ] add `models/model-catalog` module;
- [ ] define stable IDs and catalog document models;
- [ ] define availability, target, license and compatibility models;
- [ ] define parser/codec boundary;
- [ ] implement structural and semantic validation;
- [ ] implement application/use-case filtering;
- [ ] implement profile-key allowlist/resolver boundary;
- [ ] add unit tests;
- [ ] document public contracts.

**Acceptance:** malformed or unauthorized entries cannot become downloadable releases.

### Phase 2 — cached catalog synchronization

- [ ] define `ModelCatalogSource`;
- [ ] define `ModelCatalogRepository`;
- [ ] implement app-private atomic catalog persistence;
- [ ] implement revision, expiry and stale-state handling;
- [ ] implement ETag/Last-Modified metadata;
- [ ] implement typed refresh outcomes;
- [ ] add fake-source and persistence tests;
- [ ] add privacy-safe events.

**Acceptance:** the last validated catalog remains readable offline and a failed refresh cannot corrupt it.

### Phase 3 — compatibility evaluator

- [ ] define device profile provider boundary;
- [ ] implement target authorization;
- [ ] implement API and ABI checks;
- [ ] implement Harness/backend/profile compatibility;
- [ ] implement RAM and storage policy;
- [ ] calculate double-staging storage overhead;
- [ ] expose ordered blockers and warnings;
- [ ] add deterministic tests.

**Acceptance:** incompatible models are blocked before any network transfer begins.

### Phase 4 — secure download engine

- [ ] add `models/model-download` module;
- [ ] define request, event, handle and error contracts;
- [ ] implement HTTPS transport boundary;
- [ ] implement allowlisted redirect policy;
- [ ] implement timeout and response-size limits;
- [ ] implement app-private partial files;
- [ ] implement streaming byte count and SHA-256;
- [ ] implement cooperative cancellation;
- [ ] serialize concurrent download/mutation operations;
- [ ] implement cleanup and startup recovery;
- [ ] add deterministic transport tests.

**Acceptance:** no unverified bytes can enter `ModelStore`, and cancellation/failure leaves no published artifact.

### Phase 5 — GGUF inspection, installation and metadata

- [ ] inspect downloaded GGUF before registration;
- [ ] compare inspectable metadata with catalog policy;
- [ ] call the existing `ModelStore.import()`;
- [ ] call post-import `ModelStore.verify()`;
- [ ] add `InstalledModelCatalog` contract;
- [ ] implement app-private metadata persistence;
- [ ] implement store/metadata reconciliation;
- [ ] handle manual-import artifacts;
- [ ] add integration tests with real `FileSystemModelStore`;
- [ ] document temporary storage overhead.

**Acceptance:** installed status is derived from a verified content-addressed artifact, never from download completion alone.

### Phase 6 — selection and phone-app integration

- [ ] wire catalog services into the phone app composition root;
- [ ] preserve current manual SAF import as an advanced path;
- [ ] expose cached/remote catalog state;
- [ ] expose download confirmation;
- [ ] expose progress and cancellation;
- [ ] implement application-owned profile resolution;
- [ ] separate install from select;
- [ ] persist selected release by target;
- [ ] reuse existing playground/runtime path;
- [ ] prove installed model inference works offline;
- [ ] add UI/presenter tests.

**Acceptance:** an explicitly selected downloaded model can run through the existing local playground without network access.

### Phase 7 — updates and lifecycle policy

- [ ] implement semantic version ordering;
- [ ] detect updates;
- [ ] install replacements side-by-side;
- [ ] render deprecation;
- [ ] define and implement revocation policy;
- [ ] protect active/loaded models from removal;
- [ ] reconcile catalog deletion with local installation;
- [ ] add lifecycle tests.

**Acceptance:** updating never deletes the currently working model before the replacement is installed and explicitly selected.

### Phase 8 — observability, health and repair

- [ ] emit catalog/download/install structured events;
- [ ] add catalog cache health;
- [ ] add partial-download health;
- [ ] add installed metadata reconciliation health;
- [ ] add explicit cleanup/repair controls;
- [ ] keep health observational by default;
- [ ] update diagnostic documentation;
- [ ] verify no sensitive URL/header/path leakage.

**Acceptance:** failures are diagnosable through typed safe data without exposing credentials or private content.

### Phase 9 — manifest signing and production hardening

- [ ] choose signature algorithm and canonical serialization;
- [ ] implement trusted key IDs;
- [ ] verify signatures before repository replacement;
- [ ] implement key rotation;
- [ ] implement rollback protection;
- [ ] add signature and tampering tests;
- [ ] review Play privacy/network disclosure;
- [ ] review network-security configuration;
- [ ] complete threat-model review.

**Acceptance:** a modified or replayed unauthorized catalog cannot authorize a model download.

### Phase 10 — cumulative validation and physical evidence

- [ ] repository guards;
- [ ] Spotless/ktlint;
- [ ] Detekt;
- [ ] JVM tests;
- [ ] Android Lint;
- [ ] app and AAR compilation;
- [ ] native packaging verification;
- [ ] Android integration tests;
- [ ] real HTTPS GGUF physical-device run;
- [ ] cancellation and network-loss evidence;
- [ ] offline inference evidence;
- [ ] update/removal/storage-cleanup evidence;
- [ ] roadmap and Definition of Done update.

**Acceptance:** all repository gates pass and privacy-safe physical-device evidence is recorded.

---

## 18. Pull-request sequence

Prefer small, reviewable vertical slices from `main`:

1. **Catalog domain and ADR**  
   contracts, validation, filtering and tests.

2. **Catalog repository and compatibility**  
   cached synchronization boundaries, persistence and device policy.

3. **Secure download and verified installation**  
   transport, progress, cancellation, hash, GGUF inspection and `ModelStore` integration.

4. **Installed metadata and selection**  
   durable release metadata, reconciliation and application-owned profile mapping.

5. **Connected Models UI**  
   phone-app wiring, confirmation, progress, selection and offline inference.

6. **Updates, health and trust hardening**  
   lifecycle policy, repair, signed manifests and physical evidence.

Do not accumulate the whole feature in one unreviewable pull request.

---

## 19. Definition of Done

The feature is complete only when:

- [ ] the implementation is based on current `main`;
- [ ] no historical feature branch is required to build it;
- [ ] a validated cached catalog can be rendered offline;
- [ ] only authorized application/use-case releases are offered;
- [ ] incompatible models are blocked before download;
- [ ] every download is explicit, cancellable and bounded;
- [ ] HTTPS, redirect and host policies fail closed;
- [ ] byte count and SHA-256 are verified;
- [ ] GGUF inspection succeeds before installed metadata is written;
- [ ] final artifact publication uses the existing `ModelStore`;
- [ ] download URL is never treated as model identity;
- [ ] installed metadata reconciles with raw store contents;
- [ ] install and selection remain separate;
- [ ] the existing runtime and playground run the selected artifact offline;
- [ ] update/deprecation/revocation behavior is explicit;
- [ ] normal telemetry contains no secrets, private paths, prompts or output;
- [ ] all new contracts and failure modes are documented;
- [ ] all repository validation gates pass;
- [ ] real-device download, install, cancellation and offline-inference evidence is recorded;
- [ ] the authoritative roadmap is updated with exact validated status.

---

## 20. Open decisions

| Decision | Status | Required before |
|---|---|---|
| Production catalog endpoint and host allowlist | `[!]` | real network integration |
| Catalog authentication requirement | `[!]` | authenticated/private catalogs |
| Signed-manifest algorithm and canonical encoding | `[!]` | production hardening |
| Key rotation and rollback policy | `[!]` | production hardening |
| Revoked installed-model behavior while offline | `[!]` | lifecycle implementation |
| Hard minimum RAM policy per release | `[!]` | compatibility production policy |
| Exact storage safety margin | `[!]` | download release |
| First-slice persistence format | `[ ]` | Phase 2 implementation |
| Process-death download resume requirement | `[ ]` | download contract finalization |
| Whether to recover or supersede PR #34 | `[!]` | model-management consolidation |
| Integration sequencing with open PR #40 | `[!]` | connected Compose UI |

Decisions that are not required for the current slice must not block pure contracts and deterministic tests.

---

## 21. Progress log

| Date | Branch/commit | Update | Validation |
|---|---|---|---|
| 2026-08-04 | `main` at `dfba2a05...` | Audited current runtime, `ModelStore`, phone app, merged console and UX direction | Repository source and merged PR state inspected |
| 2026-08-04 | `agent/model-catalog-download-implementation` | Created a fresh feature branch directly from current `main` | Branch creation confirmed |
| 2026-08-04 | pending commit | Replaced the historical-branch plan with this main-based implementation plan and tracker | Documentation review pending |

Update this table after every validated implementation commit or pull request. A task marker changes to `[x]` only after its tests and acceptance criterion pass.
