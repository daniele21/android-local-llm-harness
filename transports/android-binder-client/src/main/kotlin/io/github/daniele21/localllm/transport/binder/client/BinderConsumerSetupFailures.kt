package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionResult

internal fun setupFeatureUnavailable() = ConsumerSetupResolutionResult.Rejected(
    ConsumerControlPlaneFailure(
        ConsumerControlPlaneErrorCode.FEATURE_UNAVAILABLE,
        "Consumer setup resolution is unavailable",
    ),
)

internal fun setupTransportFailure() = ConsumerSetupResolutionResult.Rejected(
    ConsumerControlPlaneFailure(
        ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE,
        "Shared runtime transport is unavailable",
    ),
)
