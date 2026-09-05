package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessObservedApplicationIdentityReconcilerTest {
    @Test
    fun `consumer installed after process startup is observed pending before authorization`() {
        val store = InMemoryHostControlPlaneStore()
        var policies = emptyList<AuthorizedClientPolicy>()
        val reconciler =
            HarnessObservedApplicationIdentityReconciler(
                store = store,
                observedPolicies = { policies },
                epochClock = { 100L },
            )

        reconciler.reconcileIfNeeded()
        assertTrue(store.snapshot().applications.isEmpty())

        policies = listOf(redactGuardPolicy(SIGNER_A))
        val observed = reconciler.reconcileIfNeeded()

        val application = observed.applications.single()
        assertEquals(HarnessSharedRuntimeBindings.redactGuardApplicationId, application.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE, application.packageName)
        assertEquals(SIGNER_A, application.signerSha256)
        assertEquals(ApplicationRegistrationState.PENDING, application.state)
        assertTrue(
            observed.bindings.any {
                it.applicationId == HarnessSharedRuntimeBindings.redactGuardApplicationId &&
                    it.useCaseId == HarnessSharedRuntimeBindings.ombraUseCaseId
            },
        )
    }

    @Test
    fun `later observed signer replacement revokes an authorized independent consumer`() {
        val store = InMemoryHostControlPlaneStore()
        var signer = SIGNER_A
        val reconciler =
            HarnessObservedApplicationIdentityReconciler(
                store = store,
                observedPolicies = { listOf(redactGuardPolicy(signer)) },
                epochClock = { 200L },
            )
        reconciler.reconcileIfNeeded()
        store.transact { current ->
            current.copy(
                applications =
                current.applications.map { application ->
                    application.copy(state = ApplicationRegistrationState.AUTHORIZED)
                },
            )
        }

        signer = SIGNER_B
        val changed = reconciler.reconcileIfNeeded().applications.single()

        assertEquals(SIGNER_B, changed.signerSha256)
        assertEquals(ApplicationRegistrationState.SIGNATURE_CHANGED, changed.state)
    }

    private fun redactGuardPolicy(signer: String) = AuthorizedClientPolicy(
        packageName = HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE,
        applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
        allowedUseCases = HarnessSharedRuntimeBindings.redactGuardUseCases,
        acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(signer)),
    )

    private companion object {
        const val SIGNER_A = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
        const val SIGNER_B = "b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2"
    }
}
