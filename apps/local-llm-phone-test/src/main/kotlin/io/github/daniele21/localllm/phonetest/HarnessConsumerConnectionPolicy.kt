package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState

/**
 * Runtime-side eligibility for dynamically registered document-PII consumers.
 * Binder package/signature authorization is necessary but not sufficient: the application must also remain
 * authorized in the canonical control plane and hold the current enabled OMBRA assignment.
 */
internal fun HostControlPlaneState.isAuthorizedOmbraConsumer(applicationId: ApplicationId): Boolean {
    val application = applications.singleOrNull { it.applicationId == applicationId } ?: return false
    if (application.state != ApplicationRegistrationState.AUTHORIZED) return false
    return latestBinding(applicationId, HarnessSharedRuntimeBindings.ombraUseCaseId)?.enabled == true
}
