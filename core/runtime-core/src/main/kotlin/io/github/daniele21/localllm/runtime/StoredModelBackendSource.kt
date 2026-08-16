package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.StoredModel

internal fun InferenceBackend.loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle = loadModel(
    source =
    BackendModelSource(
        digest = storedModel.digest,
        file = storedModel.file,
        sizeBytes = storedModel.sizeBytes,
    ),
    profile = profile,
)
