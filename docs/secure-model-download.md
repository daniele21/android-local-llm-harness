# Secure model download core

Status: active
Document type: feature-specification
Owner: models/model-download
Canonical scope: models.download
Read when: changing network policy, transfer, verification, cancellation or partial-file cleanup
Last reviewed: 2026-08-06

The secure downloader transfers one catalog-approved GGUF artifact into a verified app-private holding area. It is intentionally separated from model installation and runtime activation.

## API flow

```kotlin
val policy = AllowlistedHttpsSourcePolicy(
    allowedHosts = setOf(
        AllowedSourceHost("huggingface.co"),
        AllowedSourceHost("hf.co", includeSubdomains = true),
        AllowedSourceHost("xethub.hf.co", includeSubdomains = true),
    ),
)

val downloader = SecureModelDownloader(
    rootDirectory = File(context.noBackupFilesDir, "model-downloads"),
    sourcePolicy = policy,
)

val result = downloader.download(
    ModelDownloadRequest(catalogRelease.artifact),
    DownloadProgressObserver { progress ->
        // Persist or render privacy-safe stage and byte counts.
    },
    cancellationToken,
)
```

The call is blocking and must execute on a background dispatcher or worker.

A successful result contains a `VerifiedDownloadHandle`. It does not expose a server filename or automatically install, activate or load the artifact.

## State and files

The configured root contains only downloader-owned data:

```text
partials/    unpredictable active .part files
operations/  privacy-safe restart-cleanup journals
verified/    SHA-256-named files awaiting installation
```

The journal never stores the complete URL, query string, credentials or private path. `recoverInterruptedDownloads()` removes the partial associated with an interrupted journal and cleans old orphan partials.

After installation code has safely imported and verified the artifact through `ModelStore`, it should call `discardVerifiedDownload(handle)` to release the duplicate holding copy.

## Source policy

Every initial URL and redirect target must pass both checks:

1. `AllowlistedHttpsSourcePolicy` validates URI structure, scheme, host and port;
2. `PublicNetworkAddressPolicy` resolves the approved hostname and rejects empty, loopback, link-local, site-local, multicast, carrier-grade NAT and IPv6 unique-local results.

A hostname with mixed public and private answers is rejected.

The exact CDN host list is an application or administrator deployment decision. It must be reviewed whenever a provider changes redirect infrastructure. Wildcard subdomains are never implicit; they require `includeSubdomains = true` on a specific suffix.

## Redirect and HTTP policy

- redirects are not delegated to `HttpURLConnection`;
- only 301, 302, 303, 307 and 308 are considered redirects;
- each target is resolved relative to the previous URI and revalidated;
- HTTPS-to-HTTP downgrade is rejected;
- redirect count is bounded;
- only HTTP 200 is accepted as the final response;
- 408, 425, 429, 500, 502, 503 and 504 are retryable;
- other HTTP responses are terminal;
- `Accept-Encoding: identity` is sent and encoded responses are rejected.

## Integrity and storage

Before connecting, the downloader checks:

- canonical 64-character SHA-256;
- positive declared size below the configured hard maximum;
- available app-private storage including safety headroom.

During transfer it aborts immediately when the stream would exceed the declared size. Completion requires:

- exact byte count;
- exact SHA-256;
- durable flush of the partial file;
- atomic move into the verified directory where supported;
- a second size and SHA-256 check after publication.

No unverified path is returned to consumers.

## Cancellation, retry and recovery

Cancellation is checked before connection, while streaming, before verified publication and during retry delay. Cancellation returns a typed result and purges the partial file.

Network I/O and selected server responses use bounded exponential retry with injected jitter. Integrity failures, source-policy failures, redirect-policy failures and size mismatches are never retried automatically.

The first version does not support range resume. Process death leaves a journal that is consumed on the next recovery pass, after which the operation starts again from zero.

## Deferred installation slice

The next layer must:

1. resolve the opaque verified handle inside the owning module boundary;
2. inspect GGUF metadata without loading the model;
3. compare architecture, quantization and required metadata with catalog and backend policy;
4. import through `ModelStore`;
5. verify the final stored digest;
6. persist an installed-model record;
7. discard the verified holding copy;
8. leave application binding activation as a separate explicit operation.
