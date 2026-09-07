package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessIndependentSignerReconciliationTest {
    private val requirement = HarnessBuiltInApplicationRequirement(
        applicationId = ApplicationId("redactguard"),
        acceptedPackageNames = setOf("io.github.daniele21.redactguard"),
        acceptedSignerSha256 = setOf(PLAY_SIGNER),
        displayName = "RedactGuard",
        initialState = ApplicationRegistrationState.PENDING,
        allowObservedSignerChange = true,
    )
    private val reconciler = HarnessControlPlaneReconciler(HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement)))

    @Test
    fun `new independently signed consumer is pending until explicit authorization`() {
        val result = success(reconciler.reconcile(HostControlPlaneState(), observedAtEpochMs = 100))

        val application = result.state.applications.single()
        assertEquals(ApplicationRegistrationState.PENDING, application.state)
        assertEquals(PLAY_SIGNER, application.signerSha256)
    }

    @Test
    fun `legacy co-signed registration becomes signature changed with observed Play signer`() {
        val legacy = requirement.newRegistration(10).copy(
            signerSha256 = HARNEX_SIGNER,
            state = ApplicationRegistrationState.AUTHORIZED,
            lastSeenAtEpochMs = 20,
        )

        val result = success(
            reconciler.reconcile(
                HostControlPlaneState(applications = listOf(legacy)),
                observedAtEpochMs = 100,
            ),
        )

        val application = result.state.applications.single()
        assertEquals(PLAY_SIGNER, application.signerSha256)
        assertEquals(ApplicationRegistrationState.SIGNATURE_CHANGED, application.state)
        assertEquals(10, application.firstSeenAtEpochMs)
        assertEquals(100, application.lastSeenAtEpochMs)
    }

    @Test
    fun `observed signer change remains fail closed until user authorizes`() {
        val changed = requirement.newRegistration(10).copy(state = ApplicationRegistrationState.SIGNATURE_CHANGED)
        val result = success(
            reconciler.reconcile(
                HostControlPlaneState(applications = listOf(changed)),
                observedAtEpochMs = 100,
            ),
        )

        assertEquals(ApplicationRegistrationState.SIGNATURE_CHANGED, result.state.applications.single().state)
    }

    private fun success(result: HarnessControlPlaneReconciliationResult): HarnessControlPlaneReconciliationResult.Success {
        assertTrue(result is HarnessControlPlaneReconciliationResult.Success)
        return result as HarnessControlPlaneReconciliationResult.Success
    }

    private companion object {
        const val PLAY_SIGNER = "b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2"
        const val HARNEX_SIGNER = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
    }
}
