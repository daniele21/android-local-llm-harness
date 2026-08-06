# Harness unified model inventory state

This document defines the first Models ViewModel/UDF foundation for the connected phone application.

## Purpose

The existing phone application exposes several valid but separate views of model state:

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

The first slice records two deterministic degradation reasons:

- `LOADED_MODEL_NOT_IN_INVENTORY`: the runtime owns a digest that is absent from both catalog-installed and imported items;
- `LOADED_MODEL_DIFFERS_FROM_SELECTION`: the runtime owns one known model while another known model is selected.

These are product-state inconsistencies, not backend exception messages. No private path or prompt/output content is included.

## UDF integration

`HarnessUiState.modelInventory` is rebuilt by the reducer whenever one of these typed events arrives:

- `ModelDistributionChanged`;
- `ModelChanged`;
- `LoadedModelChanged`.

Catalog refreshes preserve the last known loaded digest. Selection changes preserve runtime ownership and clear stale removal confirmation. The projection remains pure and has no Android, filesystem, network, or runtime side effects.

## Validation boundary

The foundation is covered by pure reconciliation tests and reducer convergence tests. Focused validation in Actions run `31079690251` includes Spotless, Detekt, phone-test JVM tests, Android Lint, and Kotlin compilation.

This evidence validates the state contract only. It does not constitute connected UI, emulator, physical-device, download/install, model-loading, or inference validation.

## Current boundary

This slice establishes the state and reducer contract. It does not yet migrate the model controller actions into `HarnessViewModel`, and it does not change download, verification, installation, selection, removal, or runtime-loading behavior.

The next Models slice must:

1. introduce a `ModelEffects` boundary around the existing controller actions;
2. dispatch controller snapshots and runtime ownership through typed events;
3. render the Models screen from `HarnessUiState.modelInventory`;
4. add `models/{digest}` details and deterministic recovery actions;
5. remove the corresponding Activity-owned model state only after the connected behavior is covered.
