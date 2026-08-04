# Model catalog

`models/model-catalog` contains the UI-independent control-plane layer for administrator-managed GGUF releases.

The module represents and validates catalog entries, evaluates whether a release may be offered to an application/use-case target, decodes a bounded JSON wire format, persists the last validated snapshot atomically and coordinates explicit refreshes through an abstract source.

It does not implement HTTP, download model bytes, publish artifacts into `ModelStore` or load models into the inference runtime.

## Responsibilities

- represent catalog documents and releases;
- preserve SHA-256 as the immutable artifact identity;
- validate bounded catalog metadata;
- filter releases by exact `applicationId + useCaseId`;
- evaluate release, platform, backend, profile, RAM and storage compatibility;
- decode and encode the strict catalog JSON schema;
- persist the last validated catalog in app-private storage;
- reject revision rollback, same-revision conflicts and catalog identity changes;
- expose fresh, stale and expired cache states;
- coordinate explicit refresh, conditional metadata and typed failures;
- preserve the last good catalog when refresh fails.

## Non-responsibilities

- choosing the catalog endpoint;
- HTTP, authentication, redirects or certificate policy;
- background refresh scheduling;
- signed-manifest verification;
- partial-file management;
- model download;
- GGUF inspection;
- final artifact publication;
- model selection or runtime loading;
- Android UI.

Those concerns belong to later source, download and application-integration layers. Final GGUF publication remains owned by `models/model-store`.

## Catalog domain

`CatalogModelDocument` represents one immutable catalog revision. Every release connects a stable model/version identity to an exact SHA-256 digest, byte size, HTTPS distribution URI, compatibility policy, allowed targets, application-reviewed `ModelProfileKey` and license metadata.

The URL is a location, not model identity. Installation and deduplication continue to use the digest.

Use `CatalogValidator` after decoding and before persistence or display:

```kotlin
val validation = CatalogValidator().validate(
    document = document,
    nowEpochMs = clock.nowEpochMs(),
)

if (!validation.valid) {
    validation.violations.forEach { violation ->
        logger.record(violation.code, violation.path)
    }
}
```

Violation paths are structural and privacy-safe. They do not contain remote values.

## JSON codec

`CatalogJsonCodec` provides a deterministic schema-versioned representation without relying on Android JSON APIs or reflection.

```kotlin
val decoded = when (val result = CatalogJsonCodec().decode(responseBytes)) {
    is CatalogDecodeResult.Success -> result.document
    is CatalogDecodeResult.Failure -> return recordFailure(result.error.code)
}
```

Default limits are:

```text
maximum document bytes: 1 MiB
maximum JSON depth: 16
maximum JSON nodes: 20,000
maximum string length: 8,192 UTF-16 code units
```

The decoder fails closed on malformed UTF-8, invalid Unicode, duplicate object keys, duplicate set values, unknown fields, missing fields, wrong types, non-integral numeric fields, invalid enum values and invalid URI syntax. Optional schema fields are represented explicitly as JSON `null`; omission is not treated as a default.

Encoding is canonical for the current schema. Object fields have a stable order and set-backed values are sorted before serialization.

## App-private persistence

`FileModelCatalogRepository` stores one state envelope in a caller-supplied app-private directory:

```text
<catalog-root>/catalog-state.json
```

The envelope contains:

- a storage schema version;
- the canonical catalog JSON;
- local fetch metadata such as ETag and Last-Modified;
- the last typed refresh failure;
- the local persistence timestamp.

Replacement is written to a temporary file in the same directory, flushed and synchronized, then moved into place atomically when supported. An unsupported atomic move falls back to a same-filesystem replacement. Abandoned repository-owned temporary files are removed when the repository is first opened.

Before replacement, the repository:

1. validates the incoming document at the current time;
2. validates bounded local metadata;
3. rejects a different `catalogId` once a catalog is established;
4. rejects a lower revision;
5. rejects a different payload at the same revision;
6. encodes the complete candidate state;
7. publishes it only after the durable write succeeds.

A failed refresh or rejected candidate does not delete or replace the last good catalog.

## Freshness and authorization

A cached document is exposed with one of four states:

- `EMPTY`: no validated catalog is available;
- `FRESH`: the document has not reached `expiresAtEpochMs`;
- `STALE`: the document is expired but remains available for diagnostics during the configured grace period;
- `EXPIRED`: the grace period has also elapsed.

`CatalogSnapshot.canAuthorizeDownloads` is true only while the snapshot is `FRESH`. Stale or expired entries may be shown with their status but cannot authorize a new model download.

The default stale grace period is seven days and can be replaced by application policy.

## Explicit synchronization

`ModelCatalogSynchronizer` connects three replaceable boundaries:

```text
ModelCatalogSource
        ↓
CatalogDocumentCodec + CatalogValidator
        ↓
ModelCatalogRepository
```

A source receives `CatalogFetchRequest` containing the current ETag, Last-Modified value and revision. It returns updated bytes, `NotModified`, or a typed failure. The source owns transport behavior; the synchronizer owns decoding, validation, revision enforcement, persistence and failure normalization.

Fetch time is recorded from the local `CatalogClock`, not trusted from remote data. A `NotModified` response refreshes local response metadata but does not extend the document's own expiry window or re-authorize a stale catalog.

Refresh is explicit. The module does not start jobs, register alarms or perform background work.

## Threading

Domain objects and codec operations are synchronous. `FileModelCatalogRepository` serializes its mutable state with an internal lock and performs file I/O synchronously. `ModelCatalogSynchronizer` invokes its source synchronously.

Applications should call persistence and refresh operations from an appropriate worker dispatcher rather than the Android main thread. The module does not own executors or coroutine scopes.

## Errors and privacy

The module exposes fixed error enums through `CatalogCodecErrorCode`, `CatalogFailureCode`, `CatalogReplaceRejectionCode`, validation violations and compatibility reasons.

Do not log or persist outside the private state envelope:

- authorization headers or credentials;
- signed download URLs;
- private filesystem paths;
- model bytes;
- prompts or generated output.

## Tests

Run the targeted gate before pushing catalog changes:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :models:model-catalog:compileDebugKotlin \
  :models:model-catalog:compileDebugUnitTestKotlin \
  :models:model-catalog:testDebugUnitTest \
  :models:model-catalog:lintDebug
```

Tests cover deterministic codec behavior, malformed and oversized input, duplicate data, atomic persistence, process-restart reload, rollback and conflict rejection, freshness transitions, conditional refresh, source failure and preservation of the last good catalog.
