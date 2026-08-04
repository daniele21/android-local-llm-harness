# Model catalog

`models/model-catalog` contains the UI-independent domain and policy layer for administrator-managed GGUF releases.

The module does not perform networking, write final model bytes or load a model into the inference runtime. It validates catalog data and decides whether a release can be offered to a specific application/use-case target on a specific device.

## Responsibilities

- represent catalog documents and releases;
- preserve SHA-256 as the immutable artifact identity;
- validate bounded catalog metadata;
- filter releases by exact `applicationId + useCaseId`;
- evaluate release, platform, backend, profile, RAM and storage compatibility;
- expose stable reason and warning codes.

## Non-responsibilities

- HTTP catalog retrieval;
- JSON decoding or signature verification;
- partial-file management;
- model download;
- GGUF inspection;
- final artifact publication;
- model selection or runtime loading;
- Android UI.

Those concerns are implemented by later catalog persistence, download and application-integration layers. Final GGUF publication remains owned by `models/model-store`.

## Core types

### `CatalogModelDocument`

Represents one versioned catalog snapshot:

```kotlin
val document = CatalogModelDocument(
    schemaVersion = 1,
    catalogId = CatalogId("public-models"),
    revision = 42,
    generatedAtEpochMs = generatedAt,
    expiresAtEpochMs = expiresAt,
    entries = releases,
)
```

A document contains no executable behavior. Its entries are untrusted until `CatalogValidator` accepts them.

### `CatalogModelRelease`

Connects:

- stable model and release IDs;
- exact `ModelDigest` and expected byte size;
- HTTPS distribution URI;
- architecture and quantization labels;
- compatibility policy;
- allowed application/use-case targets;
- application-reviewed `ModelProfileKey`;
- license and lifecycle metadata.

The URI is a location, not identity. Installation and deduplication use the digest.

### `CatalogTarget`

```kotlin
val target = CatalogTarget(
    applicationId = ApplicationId("phone-test"),
    useCaseId = UseCaseId("playground"),
)
```

Target matching is exact. The module does not implement wildcards or implicit fallback.

## Validation

Use `CatalogValidator` before persisting or displaying a remotely supplied document:

```kotlin
val result = CatalogValidator().validate(
    document = document,
    nowEpochMs = clock.nowEpochMs(),
)

if (!result.valid) {
    result.violations.forEach { violation ->
        logger.record(violation.code, violation.path)
    }
}
```

The validator checks:

- supported schema;
- catalog ID, revision and validity window;
- maximum entry count;
- duplicate releases;
- conflicting size metadata for one digest;
- SHA-256 syntax and positive size;
- HTTPS URI and safe GGUF file name;
- architecture and quantization identifiers;
- compatibility fields;
- required targets;
- license links;
- replacement consistency.

Violation paths are structural and privacy-safe. They do not include remote values.

## Target filtering

```kotlin
val visibleReleases = CatalogQueries.releasesForTarget(document, target)
```

Filtering alone does not mean a release is downloadable. Every visible release must also pass compatibility evaluation.

## Compatibility evaluation

`CatalogCompatibilityEvaluator` requires two application-owned collaborators:

```kotlin
interface CatalogVersionMatcher {
    fun isInRange(
        currentVersion: String,
        minimumInclusive: String?,
        maximumExclusive: String?,
    ): Boolean
}

interface CatalogProfileResolver {
    fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean
}
```

The catalog cannot define arbitrary prompt or backend policy. `CatalogProfileResolver` only accepts keys reviewed and shipped by the application.

Example:

```kotlin
val result = evaluator.evaluate(
    release = release,
    target = target,
    device = CatalogDeviceProfile(
        sdkInt = sdkInt,
        supportedAbis = supportedAbis,
        totalMemoryBytes = totalMemoryBytes,
        availableStorageBytes = availableStorageBytes,
        harnessVersion = harnessVersion,
        backendId = "llama.cpp",
    ),
)

if (result.compatible) {
    showDownloadAction(result.requiredStorageBytes)
} else {
    showBlockedReasons(result.reasons)
}
```

Hard blockers include target authorization, release state, Android API, ABI, backend, Harness version, profile support, minimum RAM and storage. Deprecation and recommended-RAM shortfalls are warnings.

## Storage requirement

The default evaluator accounts for the current installation pipeline:

```text
2 × artifact size
+ catalog minimum free storage
+ 128 MiB safety margin
```

The two copies represent the private download file and the current `ModelStore.import()` staging copy. This policy must be revisited if `ModelStore` later gains a separately reviewed verified-stream or adopt-file API.

## Threading

The current module is immutable and synchronous. It does not start threads, perform I/O or own lifecycle resources. Callers may execute validation and compatibility evaluation on any thread appropriate for their workload.

## Errors and privacy

The module returns fixed enums such as `CatalogViolationCode` and `CatalogCompatibilityReason`. Callers should persist these codes rather than arbitrary remote values or exception messages.

Do not log:

- signed URLs;
- authorization data;
- private paths;
- model bytes;
- prompts or generated output.

## Tests

Run:

```bash
./gradlew :models:model-catalog:testDebugUnitTest
./gradlew :models:model-catalog:lintDebug
./gradlew detekt spotlessCheck
```

Repository CI includes this module in the mandatory validation matrix.
