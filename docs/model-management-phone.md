# Phone model management

The phone Models surface manages installed GGUF artifacts through the shared `ModelStore`; it does not create a second inventory or expose filesystem paths.

## Operations

- **Verify integrity** recomputes the stored artifact verification through `ModelStore.verify` and reports only a privacy-safe result.
- **Remove installed model** always requires an explicit second confirmation.
- A model selected for inference or currently owned by the runtime is protected from catalog-card removal. The selected-model card can remove it only after the playground runtime has been released.
- Successful catalog removal also deletes the path-free installed catalog metadata. A later refresh reconciles any stale metadata if storage changed externally.

Verification and removal run off the UI thread. Only one catalog distribution or management operation runs at a time.

## Boundaries

Removal deletes the app-private installed copy. It does not delete the user's original SAF document. Download URLs, signed URLs and storage paths are never displayed or persisted by the management state.
