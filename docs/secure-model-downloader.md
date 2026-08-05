# Secure model downloader

## Scope

`models/model-download` downloads one administrator-authorized catalog artifact into an app-private temporary file and delegates final publication to `ModelStore` only after deterministic verification.

The module does not own the catalog, the final artifact layout, GGUF parsing, runtime loading or UI.

## Security properties

- only HTTPS URIs without user info or fragments are accepted;
- non-default ports are rejected;
- redirects are followed manually and are bounded;
- every redirect target must remain HTTPS and pass the host allowlist;
- transparent content encoding is disabled so byte counts remain deterministic;
- a declared `Content-Length`, when present, must match the catalog size;
- streamed bytes may never exceed the catalog size;
- the final byte count must exactly match the catalog size;
- SHA-256 is calculated while streaming and must match the catalog digest;
- cancellation is checked before opening and while copying;
- `.part` files are deleted after success, cancellation and failure;
- only a verified temporary file reaches `ModelStore.import`;
- `ModelStore` remains the sole publisher of final content-addressed artifacts.

## Main API

```kotlin
val downloader = SecureModelDownloader(
    workingDirectory = appPrivateDownloadDirectory,
    transport = HttpUrlConnectionDownloadTransport(),
    modelStore = modelStore,
    policy = SecureDownloadPolicy(
        hostPolicy = AllowlistedDownloadHosts(setOf("huggingface.co")),
    ),
)

val result = downloader.download(DownloadRequest(release))
```

Production callers should construct the allowlist from administrator-controlled configuration. Redirect hosts used by the selected distribution provider must be reviewed and included explicitly; the downloader never broadens the allowlist automatically.

## Error model

Failures are normalized to `DownloadErrorCode`, including URI rejection, disallowed hosts, redirect failures, HTTP errors, size mismatch, digest mismatch, cancellation, transport I/O and model-store rejection.

## Tests

Unit tests cover successful verified import, digest mismatch, redirect rejection, cancellation and temporary-file cleanup. Network behavior is tested through the replaceable `DownloadTransport` boundary without external connectivity.

## Deferred

This slice does not yet add UI wiring, WorkManager/background scheduling, resumable range requests, Android notification integration or GGUF metadata inspection before import.
