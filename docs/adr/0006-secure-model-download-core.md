# ADR 0006: Verify remote model bytes before installation

- Status: Accepted
- Date: 2026-08-05

## Context

ADR 0005 separates administrator-managed model distribution from local inference and keeps `ModelStore` as the only final publisher of GGUF artifacts. The catalog now contains immutable SHA-256 digests, exact sizes and approved remote locations for candidate models.

Remote model files are large, mutable at their distribution location and potentially attacker-controlled. A downloader must therefore enforce source, redirect, storage and integrity policy before any byte can be handed to installation code.

## Decision

Introduce `models/model-download` as a UI-independent secure transfer core.

- accept only a validated `CatalogGgufArtifact` rather than an arbitrary URL;
- require HTTPS, an explicit host allowlist and public resolved network addresses;
- reject URL credentials, fragments, IP literals, local hostnames and disallowed ports;
- follow redirects manually with a bounded count and revalidate every target;
- disable transparent response compression and require identity bytes;
- write only to unpredictable app-private `.part` files;
- enforce declared size, configured maximum size and storage headroom;
- compute SHA-256 while streaming and require an exact digest match;
- atomically publish only verified bytes into a digest-keyed holding area and rehash after publication;
- deduplicate both completed and in-progress operations by digest;
- persist only privacy-safe operation metadata: operation ID, digest, byte count, normalized host, partial basename, timestamp and attempt;
- purge partials after cancellation or terminal failure and recover interrupted operations after process restart;
- return an opaque `VerifiedDownloadHandle`, not an unrestricted filesystem path;
- keep WorkManager, foreground execution, GGUF inspection, `ModelStore` import, binding activation and runtime loading outside this slice.

The initial implementation restarts an interrupted download from zero. HTTP range resume remains deferred until server and CDN semantics are controlled and tested.

## Consequences

- a downloaded file is not an installed model;
- applications must invoke the synchronous downloader off the main thread;
- temporary verified bytes consume storage until installation or explicit discard;
- signed URL query parameters remain in memory only for the active request and are not persisted or logged;
- DNS preflight rejects private and local resolution, but the default `HttpURLConnection` transport does not pin the validated addresses. A hardened custom transport is required when the DNS resolver itself is not trusted;
- final format and architecture trust still requires GGUF inspection before `ModelStore` import;
- durable Android scheduling and user-visible foreground progress can wrap the same contracts without moving network policy into the runtime.

## Alternatives considered

### Android DownloadManager

Rejected for the first implementation because it does not provide the required app-private partial-file ownership, redirect revalidation, integrated streaming digest verification and typed lifecycle control.

### Stream directly into ModelStore

Rejected because unverified remote bytes must never cross the final publication boundary and because the existing store owns its own staging and conflict guarantees.

### Trust Content-Length or the catalog digest without hashing

Rejected because response metadata and remote locations are mutable. The complete received byte stream must be measured and hashed locally.

### Persist the complete signed URL for resume

Rejected because signed URLs may contain credentials. The initial restart policy needs only privacy-safe operation metadata and discards the partial file after interruption.
