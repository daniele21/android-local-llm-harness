# Console model management

## Scope

The Models view can manage models only when a `ConsoleModelControl` capability is connected. Inventory discovery and model mutation remain separate boundaries:

- `ConsoleModelInventoryProvider` reads installed-model metadata;
- `ConsoleModelControl` exposes explicit import, verification and removal operations.

Refreshing, opening or navigating the console does not import, verify, remove, load or unload a model.

## Control boundary

`ModelStoreConsoleModelControl` adapts the existing `ModelStore` contract. It does not create a parallel registry or content-addressed storage implementation.

Supported operations are:

- `importModel(ConsoleModelImportRequest)`;
- `verify(ModelDigest)`;
- `remove(ModelDigest)`.

The control reports capability availability, execution state, source identity and the latest operation outcome. The disconnected implementation exposes no actions and returns the fixed error `Model management unavailable`.

## Import flow

Import starts only after the user selects `Select and import GGUF` and supplies architecture and quantization labels. Android then opens a Storage Access Framework document picker.

`AndroidModelImportStager`:

1. reads the display name and optional size from the selected document;
2. requires a `.gguf` filename;
3. copies the stream into the console application's private cache directory;
4. calculates SHA-256 during the copy;
5. checks the provider-reported size when available;
6. creates a `ConsoleModelImportRequest`;
7. deletes the staging file after success or failure.

The staged request is passed to `ModelStore.import()`, which remains responsible for content-addressed destination layout, size verification, digest verification, deduplication, atomic placement and destination-conflict handling.

Import runs on the console's single-thread diagnostics executor, never on the Android main thread. While any model operation is active, all model-management actions are disabled.

## Architecture and quantization metadata

Architecture and quantization are required by the existing `GgufArtifact` import contract. The current `ModelStore` persists only the content-addressed artifact, digest and size; it does not persist these profile labels.

The console therefore does not claim that architecture or quantization can be recovered from the installed-model inventory. A later runtime profile or GGUF metadata-inspection workflow must provide durable profile metadata separately.

Importing a model does not:

- create or update a `ModelProfileRegistry` entry;
- bind the model to an application or use case;
- load the model into a runtime;
- create a session;
- start inference.

## Verification

Each installed model can be explicitly verified through `ModelStore.verify()`.

The control maps the result to either:

- `Model integrity verified`;
- `Model integrity check failed`.

`ModelStore.snapshot()` is observational and does not persist the most recent verification outcome. The UI therefore displays explicit verification as the latest operation in the current console session and does not silently rewrite inventory state.

## Removal

Removal requires two independent safeguards:

- the presenter disables removal when the runtime snapshot identifies the same digest as loaded;
- `ModelStoreConsoleModelControl` rejects the operation again when its loaded-model provider reports the target digest.

The standalone console has no connected runtime, so its own sandboxed store normally has no loaded-model signal. An embedded console must supply the actual runtime-loaded digest provider to enforce the control-layer guard.

Before an enabled removal is executed, Android shows a confirmation dialog containing the complete target digest. The mutation then runs outside the main thread through `ModelStore.remove()`.

Removal does not unload a model or close sessions. Runtime lifecycle changes remain separate explicit operations and are not inferred by the console.

## Privacy and failures

The Models presenter never receives or renders backing-file paths, document URIs or arbitrary backend exception text.

`ModelImportErrorCode` values are mapped to fixed messages:

- selected source unavailable;
- invalid digest;
- size verification failed;
- digest verification failed;
- destination conflict;
- import failed.

Unexpected import, verification, removal or discovery failures become `Model management unavailable`. The original message, URI and filesystem path are not included.

A model-management failure does not suppress runtime state, telemetry, health, cache diagnostics or model inventory.

## Standalone and embedded wiring

The standalone console connects `ModelStoreConsoleModelControl` to its private `FileSystemModelStore`. It can therefore import, verify and remove only artifacts inside its own application sandbox.

An application embedding the console data layer can connect the same control to its actual `ModelStore` and provide a loaded-model digest supplier derived from its runtime state.

Cross-application model management is not implemented. It requires the future signature-protected diagnostics bridge with authenticated callers and explicit mutation permissions.

## Testing and validation

Pure JVM tests cover:

- connected and disconnected capability discovery;
- import metadata forwarding;
- verification-result sanitization;
- loaded-model removal blocking;
- import-error sanitization;
- presenter action eligibility and disabling;
- latest-operation presentation;
- independent data-source failure isolation.

Repository validation additionally covers Android compilation of the Storage Access Framework picker and staging flow, Spotless, ktlint, Detekt, Android Lint, APK packaging and the repository model-artifact guard.
