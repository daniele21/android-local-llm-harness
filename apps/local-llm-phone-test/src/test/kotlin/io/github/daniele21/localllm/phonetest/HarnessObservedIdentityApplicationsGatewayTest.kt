package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessObservedIdentityApplicationsGatewayTest {
    @Test
    fun `snapshot previews a newly observed identity without persisting it`() {
        val store = InMemoryHostControlPlaneStore()
        val gateway = gateway(store) { SIGNER_A }

        val preview = gateway.snapshot().applications.single()

        assertEquals(HarnessSharedRuntimeBindings.redactGuardApplicationId.value, preview.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE, preview.packageName)
        assertEquals(SIGNER_A, preview.signerSha256)
        assertEquals(HarnessApplicationStatus.PENDING, preview.status)
        assertTrue(store.snapshot().applications.isEmpty())

        val result = gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(
                applicationId = preview.applicationId,
                enabled = true,
            ),
        )

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        val persisted = store.snapshot().applications.single()
        assertEquals(SIGNER_A, persisted.signerSha256)
        assertEquals(ApplicationRegistrationState.AUTHORIZED, persisted.state)
    }

    @Test
    fun `reauthorization commits the exact signer shown by the observed preview`() {
        val store = InMemoryHostControlPlaneStore()
        var signer = SIGNER_A
        val gateway = gateway(store) { signer }

        val firstPreview = gateway.snapshot().applications.single()
        gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(
                applicationId = firstPreview.applicationId,
                enabled = true,
            ),
        )
        val initiallyAuthorized = store.snapshot().applications.single()
        assertEquals(SIGNER_A, initiallyAuthorized.signerSha256)
        assertEquals(ApplicationRegistrationState.AUTHORIZED, initiallyAuthorized.state)

        signer = SIGNER_B
        val changedPreview = gateway.snapshot().applications.single()

        assertEquals(SIGNER_B, changedPreview.signerSha256)
        assertEquals(HarnessApplicationStatus.IDENTITY_CHANGED, changedPreview.status)
        val stillPersisted = store.snapshot().applications.single()
        assertEquals(SIGNER_A, stillPersisted.signerSha256)
        assertEquals(ApplicationRegistrationState.AUTHORIZED, stillPersisted.state)

        val result = gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(
                applicationId = changedPreview.applicationId,
                enabled = true,
            ),
        )

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        val reauthorized = store.snapshot().applications.single()
        assertEquals(SIGNER_B, reauthorized.signerSha256)
        assertEquals(ApplicationRegistrationState.AUTHORIZED, reauthorized.state)
    }

    private fun gateway(store: InMemoryHostControlPlaneStore, signer: () -> String): StoreHarnessCustomPresetGateway {
        val reconciler = HarnessObservedApplicationIdentityReconciler(
            store = store,
            observedPolicies = { listOf(redactGuardPolicy(signer())) },
            epochClock = { 100L },
        )
        val access = HarnessPhoneControlPlaneAccess(
            store = store,
            applicationsRuntimeSource = NoHarnessApplicationsRuntimeSource,
            observedIdentityReconciler = reconciler,
        )
        return StoreHarnessCustomPresetGateway(access)
    }

    private fun redactGuardPolicy(signer: String) = AuthorizedClientPolicy(
        packageName = HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE,
        applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
        allowedUseCases = HarnessSharedRuntimeBindings.redactGuardUseCases,
        acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(signer)),
    )

    private companion object {
        val SIGNER_A = "a".repeat(64)
        val SIGNER_B = "b".repeat(64)
    }
}
