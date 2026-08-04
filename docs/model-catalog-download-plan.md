# Model Catalog and Secure Download — Implementation Plan

## 1. Purpose

This document tracks the implementation of an admin-managed catalog of downloadable local LLM models.

The catalog allows an administrator to publish a controlled list of supported open-weight GGUF artifacts. An Android user can inspect the models available for the current application and use case, verify device compatibility, download a selected artifact, install it in the existing content-addressed model store and make it available to the local runtime.

The feature must preserve the core runtime invariant that inference resolves to an explicit model artifact and configuration. A remote URL is only a distribution location. It is never the identity of a model and must never be passed directly to the inference backend.

The canonical flow is:

```text
Admin catalog
    -> signed/versioned catalog manifest
    -> catalog synchronization
    -> application/use-case filtering
    -> device compatibility evaluation
    -> explicit user download
    -> temporary local file
    -> size and SHA-256 verification
    -> GGUF inspection and policy validation
    -> atomic import into ModelStore
    -> InstalledModel registration
    -> explicit AppModelBinding activation
    -> RuntimeOrchestrator
```

---

## 2. Progress tracking

### Status legend

| Marker | Meaning |
|---|---|
| `[ ]` | Not started |
| `[-]` | In progress |
| `[x]` | Completed and validated |
| `[!]` | Blocked or requires a decision |

### Overall status

| Workstream | Status | Notes |
|---|---:|---|
| Existing GGUF and ModelStore foundations | `[x]` | SHA-256 import, integrity verification and GGUF inspection already exist in the current runtime branch |
| Architecture and contracts | `[ ]` | Catalog, artifact source and installation contracts must be defined |
| Remote catalog client | `[ ]` | Manifest retrieval, caching and synchronization |
| Compatibility evaluator | `[ ]` | Runtime, ABI, RAM, storage and GGUF policy checks |
| Download manager | `[ ]` | Resumable, cancellable, bounded and observable downloads |
| Artifact verification and installation | `[ ]` | Integrate temporary downloads with the existing ModelStore |
| Catalog administration backend | `[ ]` | Admin-facing publishing is outside the Android runtime but its contract must be specified |
| Console and user experience | `[ ]` | Catalog, progress, errors, installed models and updates |
| Observability and diagnostics | `[ ]` | Structured events, download metrics and privacy-safe diagnostics |
| Security hardening | `[ ]` | Manifest trust, host policy, redirect policy and negative testing |
| End-to-end validation | `[ ]` | Real device, real GGUF, interruption and recovery tests |

### Progress update rule

Whenever a task is completed:

1. change its marker to `[x]` only after tests and acceptance criteria pass;
2. add the relevant pull request or commit reference in the progress log;
3. update any architecture decision affected by the implementation;
4. record deviations from this plan in the decision log;
5. do not mark a phase complete while any mandatory acceptance criterion remains open.

---

## 3. Goals

The implementation must:

- let an administrator define which GGUF models are available;
- let the Android app retrieve and locally cache a versioned catalog;
- show only models allowed for the current `applicationId + useCaseId`;
- evaluate compatibility before offering a download;
- require explicit user action before downloading a large model;
- verify the expected byte size and SHA-256 digest;
- inspect the GGUF before installation;
- import the verified artifact into the existing content-addressed `ModelStore`;
- deduplicate identical artifacts even when distributed through different URLs;
- support cancellation, retry and process-death recovery;
- expose progress and typed errors to native Android and future Capacitor integrations;
- make catalog, download and installation activity observable in the developer console;
- support future migration from embedded storage to a shared central model store.

---

## 4. Non-goals

The first implementation will not:

- automatically select a model based on opaque quality scoring;
- silently replace one model with another;
- allow arbitrary user-provided download URLs in production mode;
- execute inference directly from a temporary download path;
- treat the URL as the model identity;
- download models without explicit user consent;
- support peer-to-peer model distribution;
- synchronize model files across devices;
- redistribute models whose license does not permit redistribution;
- implement differential or binary-patch model updates;
- automatically delete an installed model that is still bound to an application or use case;
- load a newly downloaded model before integrity and compatibility checks succeed.

A developer-only manual import flow may remain available independently of the managed catalog.

---

## 5. Architectural principles

### 5.1 Distribution is separate from execution

The runtime backend must only receive a verified local artifact reference. It must not know about HTTP clients, URLs, manifests, authentication or catalog administration.

```text
Remote catalog and download layer
              |
              v
Verified local artifact in ModelStore
              |
              v
GgufModelProfile / UseCaseProfile / AppModelBinding
              |
              v
RuntimeOrchestrator
              |
              v
llama.cpp backend
```

### 5.2 Artifact identity remains content-addressed

The immutable artifact identity is its SHA-256 digest.

```text
artifactId = sha256(file bytes)
```

`modelId` and `version` are catalog-level identifiers. They may point to a specific immutable artifact digest, but they must not replace it.

Two catalog entries or URLs that resolve to the same digest must reuse the same installed artifact.

### 5.3 Explicit activation

Downloading and installing a model does not automatically activate it for inference.

Activation requires an explicit binding or binding update:

```text
applicationId + useCaseId
    -> UseCaseProfile
    -> GgufModelProfile
    -> installed artifact digest
```

### 5.4 Fail closed

If catalog authenticity, artifact integrity, format support, license metadata or compatibility cannot be established, the model must not become usable.

### 5.5 Embedded-first, shared-compatible

The initial implementation runs inside the application process and installs files in the application-owned model store. Contracts must not assume that the store will always be process-local. A future Binder transport must be able to move catalog requests and installation commands to a central host without changing public model identities.

---

## 6. Proposed module boundaries

Modules must be introduced only when concrete behavior is implemented.

Target structure:

```text
models/
├── model-profile/       existing model and use-case profiles
├── model-store/         existing immutable artifact storage
├── model-catalog/       catalog contracts, parser, cache and filtering
├── model-download/      download orchestration and progress
└── model-install/       verification and atomic import workflow
```

The first iteration may combine `model-download` and `model-install` if they are too small to justify independent modules. They must still remain separate responsibilities internally.

### Dependency direction

```text
core/contracts
      ^
      |
model-profile      observability/contracts
      ^                     ^
      |                     |
model-catalog      model-download
      ^              |      |
      |              v      v
      +---------- model-install
                       |
                       v
                  model-store
```

Constraints:

- `model-store` must not depend on networking;
- `model-catalog` must not depend on `llama.cpp`;
- `model-download` must not activate runtime bindings;
- `model-install` may use GGUF inspection through a stable interface but must not expose native handles;
- UI modules depend on public contracts, not concrete HTTP or filesystem implementations;
- observability is emitted through interfaces rather than global loggers.

---

## 7. Domain model

### 7.1 Catalog manifest

The remote catalog should be a versioned document.

```kotlin
data class ModelCatalogManifest(
    val schemaVersion: Int,
    val catalogId: String,
    val revision: Long,
    val generatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val entries: List<ModelCatalogEntry>,
    val signature: CatalogSignature?,
)
```

Required behavior:

- reject unsupported future schema versions;
- support backward-compatible optional fields;
- preserve the last known valid manifest when refresh fails;
- distinguish stale, expired and invalid catalogs;
- prevent a lower revision from silently replacing a newer cached revision unless rollback is explicitly permitted.

### 7.2 Catalog entry

```kotlin
data class ModelCatalogEntry(
    val modelId: String,
    val version: String,
    val displayName: String,
    val description: String?,
    val artifact: CatalogArtifact,
    val compatibility: CompatibilityRequirements,
    val runtimeDefaults: RuntimeDefaults?,
    val allowedBindings: Set<CatalogBindingScope>,
    val license: ModelLicenseMetadata,
    val lifecycle: CatalogLifecycle,
    val tags: Set<String>,
)
```

### 7.3 Artifact descriptor

```kotlin
data class CatalogArtifact(
    val artifactId: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val format: String,
    val quantization: String?,
    val architecture: String?,
    val fileNameHint: String?,
    val mirrors: List<String> = emptyList(),
)
```

Rules:

- `artifactId` must equal or deterministically include the normalized SHA-256 digest;
- `format` must initially be `GGUF`;
- `sizeBytes` and `sha256` are mandatory;
- mirrors must declare the same artifact digest and size;
- query parameters and URLs must not become part of the local artifact identity.

### 7.4 Binding scope

```kotlin
data class CatalogBindingScope(
    val applicationId: String,
    val useCaseId: String,
    val profileId: String?,
)
```

A catalog entry is visible only when its binding scope permits the requesting application and use case.

The client must not use catalog filtering as an authorization boundary when the catalog endpoint contains sensitive commercial segmentation. The server must also return only entries allowed for the caller.

### 7.5 Compatibility requirements

```kotlin
data class CompatibilityRequirements(
    val minHarnessVersion: String?,
    val maxHarnessVersionExclusive: String?,
    val minAndroidApi: Int?,
    val supportedAbis: Set<String>,
    val minRamBytes: Long?,
    val recommendedRamBytes: Long?,
    val minFreeStorageBytes: Long?,
    val requiredGgufMetadata: Map<String, String>,
    val supportedBackendIds: Set<String>,
)
```

### 7.6 Installed model record

```kotlin
data class InstalledModelRecord(
    val artifactId: String,
    val sha256: String,
    val sizeBytes: Long,
    val localState: InstalledModelState,
    val installedAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long,
    val sourceCatalogId: String?,
    val sourceModelId: String?,
    val sourceVersion: String?,
)
```

Possible states:

```text
DISCOVERED
QUEUED
DOWNLOADING
PAUSED
DOWNLOADED
VERIFYING
INSTALLING
INSTALLED
VERIFICATION_FAILED
INCOMPATIBLE
REMOVAL_PENDING
REMOVED
```

Transient download state and durable installed state should not be conflated. A model is `INSTALLED` only after the final atomic import succeeds.

---

## 8. Catalog API contract

The Android implementation must not depend on a specific backend framework, but the expected server contract must be documented.

Suggested endpoint:

```http
GET /v1/model-catalog?applicationId=<id>&useCaseId=<id>&platform=android
```

Suggested headers:

```text
If-None-Match: <etag>
X-Harness-Version: <version>
X-Android-Api: <api>
X-Device-Abi: arm64-v8a
```

Suggested responses:

- `200`: complete valid manifest;
- `304`: cached catalog remains current;
- `401/403`: caller is not authorized;
- `409`: client version is not supported;
- `429`: retry using server backoff;
- `5xx`: retain the last known valid catalog when policy allows.

The response should include:

- schema version;
- monotonic catalog revision;
- generation and optional expiry timestamps;
- ETag;
- complete entries, not partial updates, for the first version;
- optional detached or embedded signature metadata.

A delta protocol may be introduced later only if catalog size justifies the added complexity.

---

## 9. Security requirements

### 9.1 Transport security

- HTTPS is mandatory in production.
- Cleartext traffic must remain disabled in the Android network security configuration.
- Redirects from HTTPS to HTTP must be rejected.
- Redirect count must be bounded.
- Redirect targets must be revalidated against the host policy.
- Authentication tokens must not be persisted in download metadata or logs.

### 9.2 Allowed source policy

Implement a configurable source policy:

```kotlin
interface ArtifactSourcePolicy {
    fun validate(url: String): SourcePolicyResult
}
```

The initial production policy should support:

- explicit allowlisted hosts;
- controlled CDN hosts;
- optional signed URLs on approved hosts;
- rejection of `file:`, `content:`, `ftp:`, local network and loopback targets;
- rejection of URLs containing embedded user credentials;
- optional rejection of non-standard ports.

Developer builds may expose a clearly labeled relaxed policy, but the relaxed state must be visible in diagnostics.

### 9.3 Manifest trust

Phased approach:

1. HTTPS plus authenticated API and ETag validation;
2. signed manifest verification with a pinned public key;
3. key rotation through multiple trusted key IDs and bounded validity periods.

A signature must cover all security-relevant fields, including URLs, artifact digests, byte sizes, compatibility requirements, lifecycle state and catalog revision.

### 9.4 Download bounds

Before and during download:

- reject non-positive or policy-exceeding `sizeBytes`;
- reserve sufficient storage headroom;
- enforce a hard maximum number of bytes written;
- compare `Content-Length` when present;
- abort if the stream exceeds the declared maximum;
- store data only in an application-controlled temporary directory;
- use unpredictable temporary file names;
- never derive a filesystem path directly from server-provided names.

### 9.5 Integrity and format validation

Installation requires all checks to pass:

1. actual byte count matches the catalog descriptor;
2. computed SHA-256 matches the descriptor;
3. GGUF inspector recognizes the file;
4. architecture and quantization match declared policy when specified;
5. required metadata is present;
6. the backend build supports the GGUF/model architecture;
7. license metadata is present and accepted by product policy;
8. the artifact is atomically imported into `ModelStore`.

### 9.6 Logging and privacy

Never log:

- authentication tokens;
- signed URL query parameters;
- complete private download URLs;
- user prompt or generated content as part of this feature.

Logs may contain:

- catalog ID and revision;
- model ID and version;
- artifact digest prefix;
- host identifier after redaction;
- byte counts;
- durations;
- retry count;
- typed status and error codes.

---

## 10. Compatibility evaluation

Compatibility must be evaluated before download and re-evaluated before activation.

### Inputs

- Android API level;
- supported ABI;
- harness SDK version;
- backend ID and build metadata;
- available storage;
- total and currently available memory;
- declared model size;
- estimated runtime memory;
- GGUF architecture and metadata;
- application/use-case binding scope.

### Result

```kotlin
data class CompatibilityReport(
    val status: CompatibilityStatus,
    val reasons: List<CompatibilityReason>,
    val warnings: List<CompatibilityWarning>,
    val estimatedDownloadBytes: Long,
    val estimatedRuntimeMemoryBytes: Long?,
)
```

Statuses:

```text
COMPATIBLE
COMPATIBLE_WITH_WARNINGS
INCOMPATIBLE
UNKNOWN
```

Hard incompatibility examples:

- unsupported ABI;
- unsupported Android API;
- harness version outside the supported range;
- unsupported backend or model architecture;
- insufficient storage including safety headroom;
- catalog entry not allowed for the application/use case.

Warnings:

- RAM below the recommended threshold but above the hard minimum;
- expected slow performance;
- large download on a metered network;
- model version is deprecated but still usable;
- benchmark data is unavailable for the device class.

`UNKNOWN` must not be silently converted to compatible. Product policy must explicitly decide whether the user can proceed.

---

## 11. Download lifecycle

### 11.1 State machine

```text
NOT_REQUESTED
      |
      v
QUEUED
      |
      v
PREPARING
      |
      +---- policy failure ------> FAILED
      |
      v
DOWNLOADING <---- retry/backoff
      |   |
      |   +---- user cancel -----> CANCELLED
      |   +---- process death ---> RECOVERABLE
      v
DOWNLOADED
      |
      v
VERIFYING
      |
      +---- mismatch ------------> FAILED_AND_PURGED
      v
INSTALLING
      |
      +---- import failure ------> FAILED
      v
INSTALLED
```

### 11.2 Execution mechanism

Use Android WorkManager for durable background work unless experiments show that model size, foreground-service requirements or user-visible progress require a dedicated foreground service coordinator.

Expected behavior:

- unique work per artifact digest;
- duplicate requests attach observers to the same operation;
- constraints for network availability;
- optional unmetered-network preference;
- foreground notification for long-running downloads where Android requires it;
- persisted progress with bounded update frequency;
- cancellation by operation ID or artifact ID;
- retry only for retryable network/server failures;
- exponential backoff with jitter;
- no automatic retry for digest mismatch, format rejection or source-policy rejection.

### 11.3 Resume policy

Initial release decision:

- implement clean restart after interruption first;
- add HTTP range resume only after server/CDN behavior is controlled and tested.

When range resume is introduced:

- require `Accept-Ranges` or verified range responses;
- bind partial state to URL, ETag, expected digest and expected size;
- discard partial files if any identity field changes;
- recompute the complete SHA-256 before installation;
- never trust a partial hash as final integrity proof.

### 11.4 Concurrency

Initial policy:

- one active model download per application process;
- multiple queued downloads allowed;
- catalog refresh may occur independently;
- installation is serialized against deletion of the same artifact;
- runtime loading of an already installed artifact may continue while another artifact downloads;
- deletion of a currently loaded or referenced artifact must be rejected or deferred.

Concurrency should be configurable later, but unlimited parallel downloads are out of scope.

---

## 12. Installation workflow

Implement a single orchestration entrypoint:

```kotlin
interface ModelInstaller {
    suspend fun install(request: InstallModelRequest): InstallModelResult
}
```

Expected sequence:

1. resolve catalog entry and immutable artifact descriptor;
2. verify application/use-case visibility;
3. run pre-download compatibility evaluation;
4. check whether the artifact digest is already installed;
5. check available storage with safety headroom;
6. create a durable download operation;
7. download to an application-controlled temporary file;
8. verify exact byte count;
9. compute and verify SHA-256;
10. inspect GGUF metadata without full model load;
11. validate metadata against catalog and backend policy;
12. import through the existing atomic `ModelStore` API;
13. verify that the stored artifact resolves to the expected digest;
14. persist the installed-model record;
15. remove temporary and stale partial files;
16. emit completion metrics and state events;
17. return the installed artifact reference without automatically modifying bindings.

### Idempotency

The workflow must be idempotent for the same artifact digest.

If an artifact is already installed and verified:

- do not download it again;
- refresh source/catalog metadata if needed;
- return the existing installed reference;
- emit a deduplication event.

If the installed file exists but verification is stale:

- run the model-store integrity policy;
- redownload only when verification fails or policy requires revalidation.

---

## 13. Catalog lifecycle and model updates

### Catalog lifecycle states

```text
ACTIVE
DEPRECATED
WITHDRAWN
BLOCKED
```

Behavior:

- `ACTIVE`: can be discovered and downloaded;
- `DEPRECATED`: visible with a warning, replacement may be suggested;
- `WITHDRAWN`: no new downloads; existing local copy is not deleted automatically;
- `BLOCKED`: must not be loaded if the block is security-critical and product policy supports revocation.

The distinction between withdrawal and blocking is mandatory. A commercial catalog change must not silently delete user data. A security block must be explicit, auditable and narrowly scoped.

### Update detection

An update is available when:

```text
same modelId
and newer catalog version
and different artifact digest
```

The app must display:

- installed version;
- available version;
- download size;
- whether both versions can coexist;
- whether a binding migration is automatic, manual or prohibited;
- release notes when supplied.

Initial activation policy:

- download the new artifact;
- validate and install it side by side;
- require explicit binding migration;
- retain the previous artifact until no binding/session references it or the user removes it.

---

## 14. Removal and storage management

Removal must operate on artifact references, not catalog URLs.

Before deletion, check:

- active model handle;
- active or restorable sessions;
- `AppModelBinding` references;
- queued generation requests;
- in-progress verification or installation;
- shared references from multiple catalog entries or use cases.

Possible outcomes:

```text
REMOVED
DEFERRED_UNTIL_UNLOAD
REJECTED_IN_USE
REJECTED_BOUND
NOT_FOUND
FAILED
```

The UI should show:

- model file size;
- estimated reclaimable space;
- applications/use cases referencing the artifact;
- whether removal will also invalidate profiles or cached sessions.

A future shared runtime must use reference counting or an equivalent ownership registry before deleting a centrally stored artifact.

---

## 15. Public API proposal

```kotlin
interface ModelCatalogClient {
    suspend fun refresh(request: CatalogRefreshRequest): CatalogRefreshResult
    suspend fun getCachedCatalog(scope: CatalogScope): ModelCatalogSnapshot
    fun observeCatalog(scope: CatalogScope): Flow<ModelCatalogSnapshot>
}

interface ModelCompatibilityEvaluator {
    suspend fun evaluate(entry: ModelCatalogEntry): CompatibilityReport
}

interface ModelDownloadManager {
    suspend fun enqueue(request: ModelDownloadRequest): DownloadOperationId
    suspend fun cancel(operationId: DownloadOperationId): CancelDownloadResult
    fun observe(operationId: DownloadOperationId): Flow<ModelDownloadState>
    fun observeAll(): Flow<List<ModelDownloadState>>
}

interface InstalledModelRegistry {
    suspend fun findByArtifactId(artifactId: String): InstalledModelRecord?
    fun observeInstalledModels(): Flow<List<InstalledModelRecord>>
}
```

Public contracts must use stable IDs and serializable DTOs. They must not expose:

- OkHttp request/response objects;
- WorkManager internals;
- filesystem paths outside controlled diagnostic APIs;
- native handles;
- `llama.cpp` types.

---

## 16. Typed errors

Define stable error categories suitable for native SDK, Capacitor and future Binder transport.

```text
CATALOG_UNAVAILABLE
CATALOG_AUTHENTICATION_FAILED
CATALOG_SCHEMA_UNSUPPORTED
CATALOG_SIGNATURE_INVALID
CATALOG_EXPIRED
CATALOG_ROLLBACK_REJECTED
MODEL_NOT_ALLOWED_FOR_BINDING
MODEL_INCOMPATIBLE
SOURCE_URL_REJECTED
NETWORK_UNAVAILABLE
METERED_NETWORK_REJECTED
DOWNLOAD_HTTP_ERROR
DOWNLOAD_SIZE_EXCEEDED
DOWNLOAD_SIZE_MISMATCH
DOWNLOAD_CANCELLED
INSUFFICIENT_STORAGE
SHA256_MISMATCH
GGUF_INVALID
GGUF_UNSUPPORTED
DECLARED_METADATA_MISMATCH
INSTALL_IMPORT_FAILED
ARTIFACT_ALREADY_INSTALLED
ARTIFACT_IN_USE
ARTIFACT_BOUND
CLEANUP_FAILED
```

Each failure should declare:

- stable code;
- retryable flag;
- user-safe message key;
- diagnostic context without sensitive URL/token data;
- underlying cause for internal logs where safe.

---

## 17. Observability

### Structured events

At minimum emit:

```text
CatalogRefreshStarted
CatalogRefreshCompleted
CatalogRefreshFailed
CatalogSnapshotUsed
CompatibilityEvaluationCompleted
DownloadQueued
DownloadStarted
DownloadProgressed
DownloadPaused
DownloadRetried
DownloadCancelled
DownloadFailed
ArtifactVerificationStarted
ArtifactVerificationCompleted
ArtifactVerificationFailed
ArtifactInstallationStarted
ArtifactInstallationCompleted
ArtifactInstallationFailed
ArtifactDeduplicated
InstalledArtifactRemoved
InstalledArtifactRemovalDeferred
CatalogModelDeprecated
CatalogModelUpdateAvailable
```

### Metrics

Track:

- catalog refresh latency;
- manifest payload size;
- cache hit and `304` rate;
- models visible per application/use case;
- compatibility outcomes by reason;
- queued and active download counts;
- downloaded bytes;
- effective throughput;
- retry count;
- total download duration;
- SHA-256 verification duration;
- GGUF inspection duration;
- model-store import duration;
- temporary storage used;
- deduplicated bytes saved;
- failure counts by typed code;
- cleanup failures and orphaned partial-file count.

Progress events must be rate-limited to avoid excessive database writes and UI recompositions.

---

## 18. User and developer console experience

### Catalog list

Each card should show:

- display name;
- model version;
- quantization;
- download size;
- intended use case;
- compatibility status;
- estimated runtime memory when available;
- lifecycle state;
- installed/update state;
- license summary;
- primary action.

Primary actions:

```text
DOWNLOAD
CANCEL
RETRY
UPDATE
TEST
REMOVE
VIEW_DETAILS
```

### Model details

Show:

- model and artifact IDs;
- full SHA-256 in a diagnostic section;
- catalog revision;
- architecture and quantization;
- GGUF metadata summary;
- source/license attribution;
- supported application/use-case bindings;
- compatibility reasons and warnings;
- storage and memory estimates;
- installed and last-verified timestamps;
- benchmark results when available;
- replacement model when deprecated.

### Download progress

Show distinct stages rather than a single ambiguous percentage:

```text
Preparing
Downloading: bytes / total and percentage
Verifying integrity
Inspecting GGUF
Installing
Completed
```

### Error presentation

User-facing errors must explain the next action:

- free storage;
- connect to Wi-Fi;
- retry later;
- update the application;
- choose a smaller compatible model;
- report a catalog integrity issue.

Detailed internal errors remain available in the developer diagnostics view.

---

# 19. Implementation phases

## Phase A — Decisions and contracts

### Objective

Freeze the first-version boundaries before networking or UI implementation.

### Tasks

- [ ] Write an ADR for managed model distribution.
- [ ] Confirm that SHA-256 remains the immutable artifact identity.
- [ ] Decide whether the first catalog is public, authenticated or tenant-scoped.
- [ ] Define catalog schema versioning and rollback policy.
- [ ] Define host allowlist and redirect policy.
- [ ] Define signed-manifest rollout strategy.
- [ ] Define model license metadata requirements.
- [ ] Define compatibility hard failures versus warnings.
- [ ] Define download concurrency and retry policy.
- [ ] Define whether WorkManager is sufficient for the first release.
- [ ] Add catalog, compatibility, download and installed-model contracts.
- [ ] Add typed error codes.
- [ ] Add serialization compatibility tests.

### Deliverables

- ADR;
- Kotlin contract package;
- catalog JSON schema or equivalent documented schema;
- example valid and invalid manifests;
- error taxonomy.

### Acceptance criteria

- [ ] No contract exposes networking, filesystem or native-backend implementation types.
- [ ] A catalog entry resolves to one exact immutable artifact digest.
- [ ] Binding visibility is explicit.
- [ ] Unknown schema versions fail predictably.
- [ ] Contracts can later cross Binder with no conceptual redesign.

---

## Phase B — Catalog parsing, cache and synchronization

### Objective

Retrieve and retain a trustworthy catalog snapshot independently of model downloads.

### Tasks

- [ ] Create the concrete `model-catalog` module.
- [ ] Implement manifest parser and semantic validation.
- [ ] Implement ETag-aware refresh.
- [ ] Implement last-known-valid local cache.
- [ ] Persist catalog ID, revision, ETag and timestamps.
- [ ] Reject revision rollback according to policy.
- [ ] Expose fresh, stale, expired and invalid states.
- [ ] Filter entries by application/use case.
- [ ] Add source URL policy validation during catalog ingestion.
- [ ] Add optional signature-verification interface.
- [ ] Emit catalog refresh telemetry.
- [ ] Implement fake catalog source for tests.

### Tests

- [ ] valid manifest parsing;
- [ ] unknown optional field tolerance;
- [ ] unsupported schema rejection;
- [ ] duplicate model/version rejection;
- [ ] duplicate artifact handling;
- [ ] invalid SHA-256 rejection;
- [ ] invalid size rejection;
- [ ] invalid binding scope rejection;
- [ ] `200` refresh;
- [ ] `304` cache reuse;
- [ ] offline last-known-valid behavior;
- [ ] expired catalog behavior;
- [ ] rollback attack simulation;
- [ ] signature success and failure when enabled.

### Acceptance criteria

- [ ] The app can operate from the last valid catalog during a transient outage.
- [ ] An invalid refresh never overwrites a valid cached catalog.
- [ ] Catalog state and revision are observable.
- [ ] Filtering is deterministic and tested.

---

## Phase C — Device compatibility evaluator

### Objective

Prevent unsupported or clearly unusable downloads before consuming bandwidth and storage.

### Tasks

- [ ] Implement device capability snapshot provider.
- [ ] Read Android API and ABI support.
- [ ] Read harness and backend build versions.
- [ ] Estimate storage requirement with configurable headroom.
- [ ] Add minimum and recommended RAM policies.
- [ ] Validate supported backend and architecture declarations.
- [ ] Validate application/use-case scope.
- [ ] Return typed incompatibility reasons and warnings.
- [ ] Re-evaluate after GGUF inspection and before activation.
- [ ] Expose compatibility output to console UI.

### Tests

- [ ] unsupported ABI;
- [ ] unsupported API level;
- [ ] insufficient storage;
- [ ] RAM below minimum;
- [ ] RAM between minimum and recommended;
- [ ] harness version mismatch;
- [ ] backend mismatch;
- [ ] unknown requirements;
- [ ] multiple simultaneous incompatibility reasons;
- [ ] compatibility result serialization.

### Acceptance criteria

- [ ] Hard-incompatible entries cannot be downloaded through the production API.
- [ ] Warning-only entries require an explicit policy/user decision.
- [ ] Reasons are stable enough for SDK and UI consumption.

---

## Phase D — Secure download manager

### Objective

Download large GGUF artifacts reliably without exposing the runtime to unverified files.

### Tasks

- [ ] Create the concrete `model-download` module or implementation package.
- [ ] Implement unique operations keyed by artifact digest.
- [ ] Implement application-controlled temporary storage.
- [ ] Enforce HTTPS and source policy.
- [ ] Enforce redirect policy.
- [ ] Enforce expected and maximum byte count.
- [ ] Implement storage preflight.
- [ ] Implement progress reporting with throttling.
- [ ] Implement cancellation.
- [ ] Implement bounded retry with backoff and jitter.
- [ ] Persist enough state for process-death recovery.
- [ ] Implement cleanup of abandoned temporary files.
- [ ] Add network constraints and metered-network policy.
- [ ] Add foreground execution where required by Android.
- [ ] Do not implement HTTP range resume until restart behavior is stable.

### Tests

- [ ] successful download;
- [ ] cancellation before connection;
- [ ] cancellation mid-stream;
- [ ] process death and clean restart;
- [ ] network disconnect and retry;
- [ ] non-retryable HTTP response;
- [ ] retryable server response;
- [ ] redirect to allowed host;
- [ ] redirect to disallowed host;
- [ ] HTTPS-to-HTTP downgrade rejection;
- [ ] response larger than declared size;
- [ ] truncated response;
- [ ] missing `Content-Length`;
- [ ] insufficient storage before start;
- [ ] storage exhaustion during write;
- [ ] duplicate enqueue deduplication;
- [ ] cleanup after failure.

### Acceptance criteria

- [ ] No unverified file reaches the model store.
- [ ] Duplicate requests do not start duplicate downloads.
- [ ] Cancellation leaves no installed artifact.
- [ ] Temporary-file leakage is detected and bounded.
- [ ] Signed URLs and credentials are redacted from telemetry.

---

## Phase E — Verification and atomic installation

### Objective

Convert a completed temporary download into a trusted installed GGUF artifact.

### Tasks

- [ ] Implement streaming SHA-256 verification.
- [ ] Verify exact byte count.
- [ ] Reuse existing GGUF inspection capability.
- [ ] Validate declared architecture, quantization and required metadata.
- [ ] Add backend compatibility validation.
- [ ] Integrate with existing atomic `ModelStore` import.
- [ ] Implement installation idempotency.
- [ ] Implement deduplication for already installed digests.
- [ ] Persist installed-model metadata.
- [ ] Delete temporary files after success or terminal verification failure.
- [ ] Re-run stored-artifact verification according to integrity policy.
- [ ] Emit installation and deduplication events.

### Tests

- [ ] correct digest and valid GGUF;
- [ ] SHA-256 mismatch;
- [ ] byte-count mismatch;
- [ ] malformed GGUF;
- [ ] valid GGUF with unsupported architecture;
- [ ] declared metadata mismatch;
- [ ] existing identical artifact;
- [ ] import interruption;
- [ ] atomicity under process death;
- [ ] cleanup failure reporting;
- [ ] concurrent install and remove of the same digest.

### Acceptance criteria

- [ ] `INSTALLED` is reachable only after final store verification.
- [ ] Installation is idempotent by artifact digest.
- [ ] Corrupt or mismatched artifacts are purged and never loadable.
- [ ] Existing model-store invariants remain unchanged.

---

## Phase F — Binding and runtime integration

### Objective

Make installed catalog artifacts usable through explicit existing profiles without coupling downloads to inference.

### Tasks

- [ ] Resolve catalog model/version to a `GgufModelProfile` template.
- [ ] Require the exact installed digest in the final profile.
- [ ] Add explicit binding activation API.
- [ ] Validate binding compatibility before activation.
- [ ] Prevent activation of missing, blocked or unverified artifacts.
- [ ] Preserve previous valid binding when activation fails.
- [ ] Add side-by-side model version support.
- [ ] Prevent deletion of loaded or bound artifacts.
- [ ] Emit binding migration and activation telemetry.
- [ ] Document how native and Capacitor apps select catalog models.

### Tests

- [ ] activate installed compatible model;
- [ ] reject non-installed artifact;
- [ ] reject digest mismatch;
- [ ] reject blocked model;
- [ ] reject incompatible profile;
- [ ] binding migration with previous artifact retained;
- [ ] rollback to previous binding;
- [ ] runtime load after activation;
- [ ] generation smoke test with downloaded GGUF.

### Acceptance criteria

- [ ] Download completion never silently changes the active model.
- [ ] Runtime resolution still follows explicit `AppModelBinding` rules.
- [ ] A failed update cannot leave the application without its previous valid binding.

---

## Phase G — Console and application UI

### Objective

Expose discovery, compatibility, progress, installation and lifecycle state clearly.

### Tasks

- [ ] Add catalog list screen.
- [ ] Add model details screen.
- [ ] Add compatibility reasons and warnings.
- [ ] Add storage and memory estimates.
- [ ] Add explicit download confirmation.
- [ ] Add Wi-Fi/metered-network preference.
- [ ] Add progress by lifecycle stage.
- [ ] Add cancel and retry actions.
- [ ] Add installed, update-available, deprecated and blocked states.
- [ ] Add activation/binding action separately from download.
- [ ] Add removal impact confirmation.
- [ ] Add developer diagnostics section.
- [ ] Add accessibility labels and large-download UX checks.

### Acceptance criteria

- [ ] The user can distinguish available, downloading, verifying, installed and active states.
- [ ] The UI never reports success before atomic installation completes.
- [ ] Errors provide a clear recovery action.
- [ ] The active model and installed models are visually distinct.

---

## Phase H — Administration contract and release workflow

### Objective

Define how an administrator safely publishes and changes catalog entries.

### Tasks

- [ ] Document required admin fields.
- [ ] Require SHA-256 and byte size before publication.
- [ ] Validate download URL and host policy server-side.
- [ ] Validate duplicate model ID/version rules.
- [ ] Require source and license metadata.
- [ ] Require application/use-case binding scope.
- [ ] Require compatibility requirements.
- [ ] Add draft, active, deprecated, withdrawn and blocked lifecycle states.
- [ ] Add catalog revision generation.
- [ ] Add manifest signing when enabled.
- [ ] Add audit trail for publication and lifecycle changes.
- [ ] Add replacement-model linkage.
- [ ] Define emergency block procedure.
- [ ] Define CDN upload and immutable-object policy.

### Acceptance criteria

- [ ] An admin cannot publish an entry without digest, size, license and scope.
- [ ] Published artifact bytes are immutable for a given digest/version.
- [ ] Every catalog change is auditable.
- [ ] Withdrawal and security blocking have different behavior.

---

## Phase I — End-to-end hardening

### Objective

Validate the complete workflow on real Android devices and under failure conditions.

### Mandatory end-to-end flow

```text
refresh catalog
    -> select compatible entry
    -> confirm download
    -> download
    -> cancel and retry
    -> verify SHA-256
    -> inspect GGUF
    -> import into ModelStore
    -> activate binding
    -> load model
    -> create context
    -> generate
    -> unload
    -> update to a new artifact
    -> retain previous version
    -> remove unreferenced artifact
```

### Device matrix

At minimum test:

- [ ] one lower-memory supported arm64 device;
- [ ] one mid-range arm64 device;
- [ ] one recent high-memory arm64 device;
- [ ] current target Android version;
- [ ] oldest supported Android API where feasible;
- [ ] Wi-Fi and metered connection behavior;
- [ ] low-storage scenario;
- [ ] app backgrounding and process recreation;
- [ ] thermal and memory-pressure interaction during installation and first load.

### Security and fault-injection tests

- [ ] malicious redirect;
- [ ] DNS/host policy bypass attempts;
- [ ] oversized response;
- [ ] digest mismatch;
- [ ] valid hash but invalid GGUF;
- [ ] catalog rollback;
- [ ] expired catalog;
- [ ] tampered signed manifest;
- [ ] duplicate operation race;
- [ ] install/remove race;
- [ ] process death during each state;
- [ ] disk-full during write and import;
- [ ] cleanup retry and orphan detection.

### Acceptance criteria

- [ ] No tested failure path makes an unverified artifact loadable.
- [ ] Recovery after app restart is deterministic.
- [ ] No unbounded temporary storage growth is observed.
- [ ] Metrics and diagnostics are sufficient to identify the failing stage.
- [ ] A real downloaded GGUF completes an inference smoke test on supported devices.

---

## 20. CI strategy

Add CI checks incrementally with each phase.

Required jobs:

```text
catalog schema validation
contract serialization tests
catalog parser tests
source-policy security tests
compatibility evaluator tests
download integration tests with local HTTP server
artifact verification tests
model-store integration tests
WorkManager/process recovery tests
Android lint and static analysis
real GGUF smoke test using a small licensed fixture outside normal repository history
```

Model binaries must not be committed to the repository. CI should download a pinned, license-compatible fixture using a verified digest or generate/use a minimal non-model GGUF fixture for parser tests.

Test fixtures must include:

- valid manifest;
- unsupported schema manifest;
- expired manifest;
- rollback revision;
- invalid signature;
- invalid digest;
- invalid source URL;
- valid and malformed GGUF samples;
- oversized and truncated HTTP responses.

---

## 21. Definition of Done

The managed model catalog feature is complete only when:

- [ ] an administrator can publish a complete valid catalog entry;
- [ ] Android can refresh and cache a versioned catalog;
- [ ] invalid catalog updates cannot replace a valid cached catalog;
- [ ] entries are scoped to explicit applications and use cases;
- [ ] device compatibility is evaluated before download;
- [ ] the user explicitly approves large downloads;
- [ ] downloads are cancellable, retryable and recoverable;
- [ ] HTTPS, source and redirect policies are enforced;
- [ ] byte size and SHA-256 are verified;
- [ ] GGUF format and metadata are inspected;
- [ ] installation uses the existing atomic content-addressed model store;
- [ ] identical artifacts are deduplicated by digest;
- [ ] download does not silently activate a model;
- [ ] explicit binding activation is validated and reversible;
- [ ] installed, active, deprecated, blocked and update states are observable;
- [ ] model removal respects active and binding references;
- [ ] telemetry excludes secrets and signed URL parameters;
- [ ] unit, integration, security and real-device tests pass;
- [ ] architecture, API and administration documentation are updated;
- [ ] the complete flow succeeds with a real supported GGUF on Android arm64.

---

## 22. Recommended implementation order

```text
1. ADR and contracts
2. catalog JSON schema and fixtures
3. catalog parser and last-known-valid cache
4. source policy and manifest trust interface
5. compatibility evaluator
6. durable download state machine
7. SHA-256 and byte-size verification
8. GGUF inspection policy
9. atomic ModelStore integration
10. installed-model registry
11. explicit binding activation
12. console catalog and progress UI
13. administration publication contract
14. security and fault-injection suite
15. real-device end-to-end validation
16. optional signed manifests
17. optional HTTP range resume
```

Signed manifests may move earlier if the catalog is distributed through an environment where authenticated HTTPS and backend access control are not sufficient for the threat model.

---

## 23. Open decisions

| ID | Decision | Options | Status |
|---|---|---|---|
| MC-001 | Catalog access model | public, authenticated, tenant-scoped | `[!]` |
| MC-002 | Manifest trust in v1 | HTTPS/auth only, signed from first release | `[!]` |
| MC-003 | Download execution | WorkManager, foreground service coordinator, hybrid | `[!]` |
| MC-004 | Metered network default | allow with confirmation, Wi-Fi only, product-configured | `[!]` |
| MC-005 | Unknown compatibility policy | block, allow with warning | `[!]` |
| MC-006 | Catalog persistence | file, DataStore, Room | `[!]` |
| MC-007 | Download state persistence | WorkManager only, Room registry, hybrid | `[!]` |
| MC-008 | Initial update policy | manual binding migration, automatic approved migration | `[!]` |
| MC-009 | Emergency security revocation | block future loads, warn only, product-specific | `[!]` |
| MC-010 | Resume support | restart-only v1, HTTP range in v1 | `[!]` |

Decisions must be resolved in ADRs before the affected implementation phase is considered complete.

---

## 24. Progress log

Add one row for every merged implementation increment.

| Date | Phase | Change | Commit / PR | Validation | Notes |
|---|---|---|---|---|---|
| 2026-08-04 | Planning | Initial model catalog and secure download plan | current change | document review pending | Created from the existing model-profile, model-store, GGUF inspection and explicit binding architecture |
