# Phone model management

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.models.management
Read when: changing connected-app import, removal, selection or model action controls
Last reviewed: 2026-09-06

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

Each catalog card uses one state-dependent primary lifecycle action rather than permanently stacking all available operations. Download, install and load are primary actions when applicable; a loaded model exposes unload. User-facing status labels describe the current outcome (`Available`, `Ready to install`, `Installed`, `Selected`, `In memory`, `Needs attention`, `Needs recovery`) without erasing the underlying lifecycle distinction.

When a catalog model is loaded, the screen publishes one compact **Active model** summary above the catalog with its source-backed identity, quantization, artifact size and runtime state. The active card exposes Details and explicit unload-from-memory; it does not estimate model RAM usage when no measured runtime value is available.

Navigation, opening Models, opening Playground and refreshing the catalog never load a model implicitly. Explicit Load prepares the model for Playground; inference may safely call prepare again against the already warm runtime.

## Decision hierarchy and progressive disclosure

Models is a model-choice and lifecycle-management surface, not a catalog/debug log. Default information order is:

```text
local library summary
 -> active model when present
 -> current operation/feedback when present
 -> availability + size filters
 -> one curated starting point per Qwen3.5 size tier
 -> quantization alternatives on demand
 -> technical model details
```

The default unfiltered list progressively discloses quantization alternatives so the growing curated catalog does not force users to scan every artifact. Applying an availability or size filter is an explicit request for more detail and therefore shows all matching models.

Curated starting points are source-backed product policy, not runtime ranking:

- Qwen3.5 0.8B `Q4_K_M` is the lightweight default already described by the curated release;
- Qwen3.5 2B `Q4_K_M` is the quality default already described by the curated release;
- Qwen3.5 4B `UD-Q4_K_XL` is the Unsloth-guided 4-bit default selected by ADR 0019.

A **Recommended start** label means "start with this reviewed artifact for this size tier". It does not claim measured superiority, certification or device performance. Compatibility, certification and measured performance remain separate source-backed evidence.

Cards keep source-backed identity, quantization, size, current status and the one valid lifecycle action visible. **Details** is directly discoverable. Maintenance and destructive controls do not compete with the primary journey: Verify integrity and Remove from device stay in the installed-model overflow menu. Download cancellation is visible while a download is active because it is part of the current operation rather than a maintenance action.

Technical catalog source/revision, digest, architecture and recovery evidence do not dominate the default list. Per-model technical identity belongs on the model detail route; diagnostics remain the owner of deeper execution evidence.

Removal uses the shared `HarnessConfirmationDialog`, names the affected model and explains the storage/selection consequence. The confirmation is modal rather than expanding an inventory card, so the list hierarchy and scroll position remain stable. Overflow controls carry explicit accessibility semantics, interaction targets remain at least 48 dp and status meaning is always textual as well as tonal.

## Boundaries

Removal deletes the app-private installed copy. It does not delete the user's original SAF document. Download URLs, signed URLs and storage paths are never displayed or persisted by the management state.

The legacy standalone console inventory and manual SAF staging path are intentionally not restored. The connected phone flow remains catalog selection, verified download, explicit installation and explicit runtime activation through the shared store.

## Recovery validation

The recovered implementation is accepted only after the source-level controller, UI wiring and stateful test doubles compile and pass without retaining self-modifying repair workflows. Verification detail and pending-removal confirmation are part of the published UI state and therefore covered by controller tests. The final branch contains only normal Kotlin source, tests and this operational documentation.
