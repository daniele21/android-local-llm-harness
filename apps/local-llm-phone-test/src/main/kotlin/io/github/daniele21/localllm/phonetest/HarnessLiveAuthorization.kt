package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy

internal fun HarnessRuntimeGraph.liveAuthorizedClientPolicies(): List<AuthorizedClientPolicy> =
    HarnessSharedRuntimePolicy.liveAuthorizedClients(
        basePolicies = authorizedClientPolicies,
        state = controlPlaneStore.snapshot(),
    )
