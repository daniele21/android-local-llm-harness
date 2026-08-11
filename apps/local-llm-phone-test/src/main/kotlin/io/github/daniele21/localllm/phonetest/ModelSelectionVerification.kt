package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.store.ModelStore

/**
 * Validates that a catalog model can be selected for Playground use.
 *
 * ModelStore.find() intentionally does not re-hash the artifact, so StoredModel.verified is false
 * for lookups and snapshots. The authoritative integrity decision is ModelStore.verify().
 */
internal fun verifyStoredModelForSelection(modelStore: ModelStore, digest: ModelDigest) {
    requireNotNull(modelStore.find(digest)) {
        "Installed model is no longer available"
    }
    val verification = modelStore.verify(digest)
    check(verification.valid) { verification.detail }
}
