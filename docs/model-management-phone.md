# Phone model management

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.models.management
Read when: changing connected-app import, removal, selection or model action controls
Last reviewed: 2026-09-07

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

Each model variant exposes one state-dependent lifecycle action. Download, install and load are primary when applicable; a loaded model exposes unload. Contextual row actions use compact inline controls rather than full-width CTA buttons. Full-width buttons remain reserved for focused form/recovery surfaces, not list-row management actions.

User-facing lifecycle labels use compact sentence-case language (`Available`, `Ready to install`, `Installed`, `Selected`, `In memory`, `Unavailable`, `Needs attention`, `Needs recovery`) and do not compete visually with model identity. Critical meaning remains textual rather than color-only.

When a catalog model is loaded, the screen publishes one compact **Active model** summary above model choice with source-backed identity, quantization, artifact size and runtime state. The active card exposes explicit unload through an inline secondary action; opening the model identity goes to Details. It does not estimate model RAM usage when no measured runtime value is available.

Navigation, opening Models, opening Playground and refreshing the catalog never load a model implicitly. Explicit Load prepares the model for Playground; inference may safely call prepare again against the already warm runtime.

## Decision hierarchy and progressive disclosure

Models is a model-choice and lifecycle-management surface, not a catalog/debug log. The default information order is:

```text
local library summary
 -> active model when present
 -> current operation/feedback when present
 -> choose-model size controls
 -> one model tier section per supported parameter size
 -> one curated starting variant per tier
 -> quantization alternatives on demand
 -> model Details
 -> maintenance / diagnostics
```

The **model tier is the primary catalog unit**. A GGUF quantization is a subordinate variant, not an equal-weight card. Each Qwen3.5 size tier therefore owns one section/card containing its variants rather than rendering a separate large card for every artifact.

The initial tier view shows only the reviewed starting variant. `N other variants` expands the alternative quantizations in place and `Show fewer` collapses them again. This progressive disclosure remains active even when a size or availability filter is used; filtering narrows the set, it does not implicitly request all technical variants to dominate the screen.

Size choice is the primary browsing control (`All`, `0.8B`, `2B`, `4B`). Availability is secondary and stays behind one compact filter control instead of permanently consuming a second filter row.

Curated starting points are source-backed product policy, not runtime ranking:

- Qwen3.5 0.8B `Q4_K_M` is the lightweight default already described by the curated release;
- Qwen3.5 2B `Q4_K_M` is the quality default already described by the curated release;
- Qwen3.5 4B `UD-Q4_K_XL` is the Unsloth-guided reviewed starting variant and preferred Harnex validation candidate defined by ADR 0019.

A **Recommended** label means "start with this reviewed artifact for this size tier". It does not claim measured superiority, certification or device performance. Compatibility, certification and measured performance remain separate source-backed evidence.

Variant rows keep only what is required to choose and act: quantization, artifact size, lifecycle state and the one valid lifecycle action. The row identity opens Details; there is no repeated `Details` button beside every variant. Verify integrity and Remove from device remain in the installed-model overflow menu.

When every visible variant in a tier is incompatible for the same source-backed reason, the tier shows that reason once above its variant rows instead of repeating an identical incompatibility paragraph seven times. If incompatibility differs by variant, the differing reason remains attached to the affected variant and is available in Details.

Download/install progress remains visible in the affected row. Download cancellation stays visible because it is part of the current operation rather than a maintenance action.

Technical catalog source/revision, digest, architecture, backend, minimum SDK, exact compatibility evidence and recovery diagnostics do not dominate the default list. Per-model technical identity belongs on the model detail route; deeper execution evidence remains owned by diagnostics.

Removal uses the shared `HarnessConfirmationDialog`, names the affected model and explains the storage/selection consequence. The confirmation is modal rather than expanding an inventory row, so list hierarchy and scroll position remain stable. Overflow controls carry explicit accessibility semantics and all interactive controls preserve the 48 dp minimum touch target.

## Boundaries

Removal deletes the app-private installed copy. It does not delete the user's original SAF document. Download URLs, signed URLs and storage paths are never displayed or persisted by the management state.

The legacy standalone console inventory and manual SAF staging path are intentionally not restored. The connected phone flow remains catalog selection, verified download, explicit installation and explicit runtime activation through the shared store.

## Recovery validation

The recovered implementation is accepted only after the source-level controller, UI wiring and stateful test doubles compile and pass without retaining self-modifying repair workflows. Verification detail and pending-removal confirmation are part of the published UI state and therefore covered by controller tests. Material Models UX integration requires full-media emulator evidence for the affected model-management journey; representative ARM64/model/resource behavior remains separate REAL_ENVIRONMENT release evidence.
