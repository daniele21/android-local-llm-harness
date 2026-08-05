# Secure model download

`models/model-download` owns the transfer of administrator-approved GGUF artifacts from a catalog URL into an app-private verified holding area.

It does not publish artifacts into `ModelStore`, activate an application binding or load a model. Those remain separate explicit operations.

## Security guarantees

- production sources must use HTTPS and an explicit host allowlist;
- URL credentials, fragments, non-standard ports, IP literals and local hostnames are rejected;
- each resolved host must contain only public internet addresses before a connection is opened;
- redirects are followed manually, bounded and revalidated with the same source and network policy;
- transparent response compression is disabled and non-identity encodings are rejected;
- downloads use unpredictable app-private `.part` files and ignore server-provided filenames;
- declared and configured byte limits are enforced before and during streaming;
- the complete stream is hashed with SHA-256 and must match the catalog digest;
- a completed file is atomically moved into a digest-keyed verified holding area and rehashed;
- signed URL query parameters are never written to the operation journal or failure details;
- duplicate requests for the same digest attach to the existing operation rather than opening another connection;
- terminal failures and cancellation purge their partial file;
- interrupted-operation journals support process-restart cleanup without retaining the source URL.

The default transport is `HttpURLConnection` with platform TLS and certificate validation. The module performs a DNS safety preflight before each request. This preflight is not DNS pinning; deployments requiring protection against a hostile resolver must provide a transport that binds the validated addresses while preserving TLS hostname verification.

## First-version lifecycle

```text
catalog artifact
    -> descriptor validation
    -> source and resolved-address policy
    -> storage preflight
    -> app-private .part file
    -> bounded HTTPS transfer
    -> exact size and SHA-256 verification
    -> atomic verified holding file
    -> VerifiedDownloadHandle
```

The API is synchronous and must run off the Android main thread. WorkManager or a foreground-service coordinator, GGUF metadata inspection and final `ModelStore` import are intentionally deferred to the installation orchestration slice.

HTTP range resume is not implemented. An interrupted operation is purged and restarted cleanly.
