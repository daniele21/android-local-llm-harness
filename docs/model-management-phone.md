# Phone model management

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.models.management
Read when: changing connected-app import, removal, selection or model action controls
Last reviewed: 2026-08-10

The phone Models surface manages installed GGUF artifacts through the shared `ModelStore`; it does not create a second inventory or expose filesystem paths.

## Operations

- **Load model** is an explicit lifecycle action. It verifies the stored artifact, prepares the Playground runtime and loads the model before publishing it as the selected model. A failed preparation releases the attempted runtime and does not publish the model as successfully loaded.
- **Unload model** releases the idle Playground runtime and model memory without deleting the installed GGUF copy or clearing the selected model. The same model can be loaded again later.
- **Verify integrity** recomputes the stored artifact verification through `ModelStore.verify` and reports only a privacy-safe result.
- **Remove model** always requires an explicit second confirmation.
- A model selected for inference or currently owned by the runtime is protected from catalog-card removal. A loaded model must be unloaded before its local copy can be removed.
- Successful catalog removal also deletes the path-free installed catalog metadata. A later refresh reconciles any stale metadata if storage changed externally.

Loading, verification and removal run off the UI thread. Only one catalog distribution or model-management operation runs at a time. Catalog actions are exposed to Compose through immutable action values so rendering code does not own model-store or runtime dependencies.

## Lifecycle presentation

Installed storage and runtime memory are separate states. The Models surface distinguishes `INSTALLED`, `LOADING`, `LOADED` and the existing download/install/error states instead of treating an installed file as an active runtime model.

Each catalog card uses one state-dependent primary lifecycle action rather than permanently stacking all available operations. Download, install and load are primary actions when applicable; a loaded model exposes unload. Secondary operations such as View details, Verify integrity and Remove model live in the overflow menu. Tapping the card header also opens model details.

When a catalog model is loaded, the screen publishes one compact **Active model** summary above the catalog with its source-backed identity, quantization, artifact size and `LOADED` status. The UI does not estimate model RAM usage when no measured runtime value is available.

Navigation, opening Models, opening Playground and refreshing the catalog never load a model implicitly. Explicit Load prepares the model for Playground; inference may safely call prepare again against the already warm runtime.

## Boundaries

Removal deletes the app-private installed copy. It does not delete the user's original SAF document. Download URLs, signed URLs and storage paths are never displayed or persisted by the management state.

The legacy standalone console inventory and manual SAF staging path are intentionally not restored. The connected phone flow remains catalog selection, verified download, explicit installation and explicit runtime activation through the shared store.

## Recovery validation

The recovered implementation is accepted only after the source-level controller, UI wiring and stateful test doubles compile and pass without retaining self-modifying repair workflows. Verification detail and pending-removal confirmation are part of the published UI state and therefore covered by controller tests. The final branch contains only normal Kotlin source, tests and this operational documentation.
