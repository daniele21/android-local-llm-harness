# Harness unified model inventory state

This document defines the connected Models ViewModel/UDF boundary for the phone application.

## Purpose

The phone application exposes several valid but separate views of model state:

```text
administrator catalog release state
installed catalog metadata
external GGUF selection
runtime-loaded model ownership
```

Treating one of those views as the complete inventory would hide real product states. The unified inventory is therefore a derived, immutable projection rather than another persistence layer.

## Identity

Catalog entries retain their stable catalog release identifier. A digest is attached only when the current state actually provides one, such as installed catalog metadata or an imported GGUF.

The projection does not infer a digest from a display name, filename, model ID, or version. It also does not persist download URLs, signed URLs, or filesystem paths.

## Origins

Each item has one explicit origin:

- `CATALOG`: an administrator-curated release;
- `IMPORTED`: an external GGUF selected through the existing import flow;
- `RUNTIME`: a model owned by the runtime but absent from the current catalog/import inventory.

An external imported model is a valid installed and selected item. It is not marked degraded merely because it is absent from the administrator catalog.

## Lifecycle precedence

Catalog download and installation states are mapped directly into the unified lifecycle. Runtime and selection ownership then apply this precedence:

```text
runtime mismatch -> DEGRADED
loaded            -> LOADED
selected          -> SELECTED
catalog state     -> mapped lifecycle
```

This keeps a selected-but-not-loaded model distinct from the model currently owned by the runtime.

## Degraded states

The inventory records two deterministic degradation reasons:

- `LOADED_MODEL_NOT_IN_INVENTORY`: the runtime owns a digest that is absent from both catalog-installed and imported items;
- `LOADED_MODEL_DIFFERS_FROM_SELECTION`: the runtime owns one known model while another known model is selected.

These are product-state inconsistencies, not backend exception messages. No private path or prompt/output content is included.

## UDF state integration

`HarnessUiState.modelInventory` is rebuilt by the reducer whenever one of these typed events arrives:

- `ModelDistributionChanged`;
- `ModelChanged`;
- `LoadedModelChanged`.

Catalog refreshes preserve the last known loaded digest. Selection changes preserve runtime ownership and clear stale removal confirmation. The projection remains pure and has no Android, filesystem, network, or runtime side effects.

Inventory items also expose only the metadata needed by connected presentation: size, architecture and quantization. Aggregate installed count and bytes, active selection and degraded count are derived from the immutable inventory.

## Connected effects boundary

`ModelEffects` is the Activity-scoped boundary around Android and controller operations. It exposes:

- one initial snapshot containing catalog distribution, selected model and runtime-loaded digest;
- import-document launch;
- grouped typed catalog commands;
- installed-model selection;
- selected-model verification;
- selected-model removal.

Catalog mutations use `ModelCatalogCommand` rather than a wide effect interface. `HarnessModelActions` is owned by `HarnessViewModel` and applies busy guards, confirmation transitions and safe effect invocation before returning a synchronous acceptance result to the UI.

The Activity deliberately retains ownership of document launchers, controllers, executors and the process-scoped native runtime graph. Attaching the boundary does not transfer those Android resources into the ViewModel. Detaching it prevents a recreated Activity from leaving stale effects connected.

## Connected rendering and ownership sync

The Models destination renders from `HarnessUiState.modelInventory` and `HarnessUiState.modelDistribution`. Import, refresh, download, cancellation, installation, selection, verification and removal enter through `HarnessViewModel.models`.

The Activity no longer mirrors:

- selected/imported model;
- catalog distribution state;
- selected-model removal confirmation;
- the selected model used by diagnostics.

Overview, Health, Benchmarks, Validation, Settings and Storage read the same selected-model state. Runtime ownership is republished after Playground state changes, validation reports and runtime release, preserving the distinction between selected and loaded models.

Selecting or removing a model still waits for Playground runtime release. This keeps existing native ownership and cleanup behavior unchanged while removing duplicate presentation state.

## Validation boundary

The inventory foundation is covered by pure reconciliation and reducer convergence tests. The connected effect slice adds fake-effects coverage for:

- initial snapshot publication;
- grouped catalog command delegation;
- busy-state rejection;
- installed selection and selected verification;
- selected-removal confirmation and completion;
- effect detachment.

Focused Actions run `31082897050` passed Spotless, Detekt, phone-test JVM tests, Android Lint, Kotlin compilation and an explicit guard confirming that the four Activity-owned model-state mirrors were removed.

This evidence validates the connected Kotlin/Compose contract. It does not constitute emulator, physical-device, download-server, real-GGUF or inference evidence.

## Current boundary

PR #74 implements the model-detail and deterministic-recovery slice:

1. URL-safe model-detail routes use the digest when available and stable catalog identity otherwise;
2. one pure presentation contract reports compatibility, integrity, installation, selection and loaded ownership;
3. loaded-versus-selected mismatch can adopt the compatible loaded catalog model explicitly;
4. unknown or mismatched runtime ownership can be released only after confirmation and never deletes a model file;
5. loaded ownership comes from `RuntimeSnapshot.loadedModel`, not the graph's configured model identity, so successful unload converges the inventory;
6. route, presentation, reducer and effects behavior has deterministic JVM coverage.

Connected UI/restoration execution and representative physical-device evidence remain validation gates. They are not inferred from JVM tests, Android assembly or emulator preflight.
