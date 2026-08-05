# Phone model catalog, download and installation

`apps/local-llm-phone-test` connects the administrator-curated bootstrap catalog to the existing secure download and explicit installation boundaries.

## User flow

```text
open Models
  -> load and validate curated catalog
  -> filter releases for the phone-test target
  -> evaluate device compatibility
  -> Download
  -> progress or Cancel
  -> verified, ready to install
  -> Install verified model
  -> persist catalog/profile metadata
  -> installed
  -> Use in Playground
  -> verify installed artifact
  -> select model for runtime use
```

Downloading, installing and selecting a model are separate actions. Installation does not modify an application binding, load `llama.cpp`, create a context or start inference.

## Catalog source

The first connected implementation loads `CuratedModelCatalog`, the reviewed bootstrap catalog committed as Kotlin metadata in `models/model-catalog`.

At application start the catalog is:

- generated with an explicit validity window;
- validated through `CatalogValidator`;
- filtered for:
  - application: `play-internal-phone-test`;
  - use case: `manual-inference-playground`;
- evaluated against the real device profile.

The device profile includes:

- Android API level;
- supported ABIs;
- total RAM;
- available app-private storage;
- Harness application version;
- backend ID `llama.cpp`.

The Models screen displays compatible and incompatible releases. Incompatible releases include typed compatibility reasons and cannot be downloaded.

Remote catalog synchronization remains a separate follow-up. The current bootstrap catalog is already administrator controlled, but changing it still requires an application update.

## Secure download

The phone application depends on `models/model-download` and declares Android's `INTERNET` permission.

Downloads use `SecureModelDownloader` with:

- HTTPS-only source validation;
- an explicit Hugging Face host allowlist;
- redirect and address-class protection;
- storage-headroom checks;
- bounded retries;
- exact size enforcement;
- streaming SHA-256 verification;
- restart cleanup of interrupted partial files;
- app-private verified storage;
- opaque `VerifiedDownloadHandle` results.

The Compose UI exposes:

```text
READY_TO_DOWNLOAD
DOWNLOADING
VERIFIED_READY_TO_INSTALL
CANCELLED
FAILED
```

During download it displays downloaded bytes, expected bytes, percentage and a cancellation action. Cancellation is cooperative and reaches the same token used by the secure downloader.

The verified backing path is never exposed to the UI.

## Explicit installation

The Install action uses `VerifiedModelInstaller` from `models/model-install`.

It performs:

- exact catalog, target and application-profile reconciliation;
- revalidation of the verified transfer;
- metadata-only GGUF inspection through `LlamaCppGgufArtifactInspector`;
- architecture and available quantization validation;
- import through the process-scoped `ModelStore`;
- post-import integrity verification.

The UI exposes `INSTALLING`, then either `INSTALLED` or `FAILED`.

The verified transfer is retained until installed metadata is persisted successfully. After successful persistence it is discarded explicitly. A metadata-persistence failure does not silently mark the release as installed in the catalog UI.

## Installed metadata persistence

Catalog-installed model metadata is stored below the application's `noBackupFilesDir` in:

```text
installed-catalog-metadata/
  <sha256>.properties
```

Each record includes:

- model digest;
- catalog model ID and version;
- display name;
- application-owned profile key;
- application ID and use-case ID;
- GGUF file name and size;
- architecture and quantization;
- installation timestamp.

The storage format has an explicit schema version, validates bounded text and safe file names, writes through a temporary file and uses atomic replacement where supported.

Records contain no download URL, signed URL, filesystem path, prompt, generated output or model bytes.

On startup and refresh, persisted records are reconciled with the shared content-addressed `ModelStore`. Metadata whose digest is no longer installed is removed.

## Playground selection

An installed model is not selected automatically.

The user must tap **Use in Playground**. `PhoneTestController` then:

1. resolves the digest from the shared `ModelStore`;
2. confirms the stored artifact is marked verified;
3. performs an integrity verification;
4. persists the selected `ImportedPhoneModel` descriptor;
5. updates the existing Playground and physical-validation flows.

This preserves the separation between distribution and runtime activation.

## Threading and lifecycle

`PhoneModelDistributionController` owns a single-thread executor and allows one distribution operation at a time.

- catalog and installed-state snapshots are synchronized;
- download and installation run off the Android main thread;
- UI state is posted through a listener;
- download cancellation is requested when the controller closes;
- active distribution work contributes to the application's busy and keep-screen-on states.

The implementation is intentionally in-process and uses the same `HarnessRuntimeGraph` and `ModelStore` as manual import, Playground inference and physical validation.

## Current limitations

- the bootstrap catalog is bundled with the application rather than fetched from a remote admin endpoint;
- verified-ready-to-install state is process-memory state, although the verified bytes remain app-private and deduplicated;
- installation itself is synchronous below the controller and cannot be cancelled during `ModelStore.import()`;
- only one download or installation operation runs at a time;
- real download, installation and inference evidence is still required on representative physical Android devices;
- automatic binding or runtime activation remains intentionally unsupported.

## Validation

Unit tests cover:

- metadata round-trip and removal;
- rejection of unsupported storage schemas and unsafe file names;
- catalog compatibility presentation;
- download progress to verified-ready state;
- cooperative cancellation;
- installation and durable metadata creation;
- cleanup of stale metadata when the model artifact no longer exists.

Repository validation must also pass Android compilation, Lint, packaging and the existing model-distribution tests before merge.
