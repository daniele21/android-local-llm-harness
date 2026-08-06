# Harness unified model inventory

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Last reviewed: 2026-08-06

## Purpose

The connected Models surface reconciles several valid but independent views of model state:

```text
curated catalog releases
installed catalog metadata
external GGUF imports
application selection
runtime-loaded ownership
```

The inventory is a derived immutable projection. It is not another persistence layer and it must not infer identity from filenames or display labels.

## Identity

Catalog items retain a stable release identity. A SHA-256 digest is attached only when a real installed or runtime state provides it.

External imports use their verified digest as physical identity. The projection never infers a digest from:

- display name;
- filename;
- logical model ID;
- version string;
- architecture or quantization.

Download URLs, signed URLs, document URIs and filesystem paths never enter the inventory state.

## Origins

Each item has one origin:

- `CATALOG`: administrator-curated release;
- `IMPORTED`: external GGUF installed through the local import flow;
- `RUNTIME`: loaded ownership that cannot be reconciled with current catalog or imported items.

An imported GGUF is a valid installed and selectable item even when it is absent from the curated catalog.

## Lifecycle

Catalog distribution state maps into product lifecycle first. Selection and runtime ownership then apply explicit precedence:

```text
ownership mismatch -> DEGRADED
loaded             -> LOADED
selected           -> SELECTED
catalog/import     -> mapped installation lifecycle
```

The product must preserve these distinctions:

- downloaded but not installed;
- installed but not selected;
- selected but not loaded;
- loaded and selected;
- loaded but inconsistent with selection;
- installed but incompatible or unavailable;
- failed integrity verification.

Installation does not select or load a model. Selection does not imply RAM residency. Runtime release does not delete the installed artifact.

## Degraded states

The projection uses deterministic product reasons rather than arbitrary backend messages.

- `LOADED_MODEL_NOT_IN_INVENTORY`: the runtime owns a digest absent from catalog-installed and imported items.
- `LOADED_MODEL_DIFFERS_FROM_SELECTION`: the runtime owns one known model while another known model is selected.

Additional unavailable, incompatible, orphaned or verification-failed states must remain explicit and recoverable. No automatic destructive cleanup occurs during projection or bootstrap reconciliation.

## State integration

`HarnessUiState.modelInventory` is rebuilt when catalog, installed metadata, selection or loaded ownership changes.

The reducer remains pure:

- catalog refresh preserves the last known runtime ownership;
- selection changes preserve loaded ownership;
- stale removal confirmation is cleared when identity changes;
- aggregate installed count, installed bytes and degraded count derive from the immutable projection;
- no Android, filesystem, network or runtime side effect occurs during reconciliation.

## Effects boundary

`ModelEffects` is the Activity-scoped boundary for:

- initial catalog/selection/runtime snapshot;
- import document launch;
- catalog refresh, download, cancellation and installation commands;
- installed-model selection;
- integrity verification;
- confirmed removal;
- runtime release or adoption recovery.

`HarnessModelActions` is ViewModel-owned and applies busy guards, confirmation and deterministic state transitions before invoking effects.

Android launchers, executors, controllers and native runtime resources remain Activity-scoped. Attaching effects must not load a model. Detaching effects prevents a recreated Activity from retaining stale callbacks.

## Connected rendering

Models, Overview, Health, Benchmarks, Validation, Settings and Storage consume the same selected-model and inventory state. The Activity must not maintain parallel copies for individual screens.

The Models surface supports explicit:

- import;
- catalog refresh;
- download and cancellation;
- installation;
- selection;
- verification;
- detail navigation;
- runtime ownership recovery;
- confirmed storage removal.

Opening details is observational. It does not download, install, select, load, verify, release or remove a model.

## Recovery

Known loaded-versus-selected mismatch may be resolved by explicitly adopting the compatible loaded catalog model as the application selection.

Unknown or mismatched runtime ownership may be released only after confirmation and only when the runtime reports that unload is safe. Release:

- does not delete the GGUF;
- does not remove installed metadata;
- does not infer a new selection;
- refreshes ownership from `RuntimeSnapshot.loadedModel` after completion.

A successful unload must converge the inventory by clearing loaded ownership rather than retaining a configured-but-not-loaded identity.

## Persistence and restart

Installed catalog metadata and imported model metadata are durable and path-free. The product projection must be rebuilt after restart from:

- current catalog releases;
- persisted installed metadata;
- `ModelStore` snapshot;
- persisted application selection;
- current runtime snapshot.

Reconciliation must preserve valid external imports, expose missing artifacts or stale metadata and avoid automatic destructive deletion.

## Testing

Deterministic coverage includes:

- catalog and import mapping;
- identity separation and digest attachment;
- selection and loaded lifecycle precedence;
- loaded-model absence and loaded-versus-selected mismatch;
- catalog refresh preserving runtime ownership;
- reducer convergence from events in different orders;
- initial effects snapshot and effect detachment;
- busy command rejection;
- download/install/select/verify/remove delegation;
- removal confirmation and active-model protection;
- model-detail route identity and presentation;
- explicit adoption and runtime-release recovery;
- successful unload clearing runtime ownership;
- restart reconciliation, missing artifact and corrupted metadata behavior.

Connected download, installation, restart and recovery flows require representative physical-device evidence before release readiness.
