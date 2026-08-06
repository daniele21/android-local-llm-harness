# Phone model management

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.models.management
Read when: changing connected-app import, removal, selection or model action controls
Last reviewed: 2026-08-06

The phone Models surface manages installed GGUF artifacts through the shared `ModelStore`; it does not create a second inventory or expose filesystem paths.

## Operations

- **Verify integrity** recomputes the stored artifact verification through `ModelStore.verify` and reports only a privacy-safe result.
- **Remove installed model** always requires an explicit second confirmation.
- A model selected for inference or currently owned by the runtime is protected from catalog-card removal. The selected-model card can remove it only after the playground runtime has been released.
- Successful catalog removal also deletes the path-free installed catalog metadata. A later refresh reconciles any stale metadata if storage changed externally.

Verification and removal run off the UI thread. Only one catalog distribution or management operation runs at a time. Catalog actions are exposed to Compose through one immutable `PhoneModelDistributionActions` value so rendering code does not own model-store or runtime dependencies.

## Boundaries

Removal deletes the app-private installed copy. It does not delete the user's original SAF document. Download URLs, signed URLs and storage paths are never displayed or persisted by the management state.

The legacy standalone console inventory and manual SAF staging path are intentionally not restored. The connected phone flow remains catalog selection, verified download, explicit installation and explicit runtime activation through the shared store.

## Recovery validation

The recovered implementation is accepted only after the source-level controller, UI wiring and stateful test doubles compile and pass without retaining self-modifying repair workflows. Verification detail and pending-removal confirmation are part of the published UI state and therefore covered by controller tests. The final branch contains only normal Kotlin source, tests and this operational documentation.
