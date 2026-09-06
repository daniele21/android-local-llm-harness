package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.RegisteredApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessIndependentSignerAuthorizationGatewayTest {
    @Test
    fun `pending observed identity requires explicit enable before authorization`() {
        val store = InMemoryHostControlPlaneStore(state(ApplicationRegistrationState.PENDING))
        val gateway = StoreHarnessApplicationsGateway(store, epochClock = { 50 })

        val before = gateway.snapshot().applications.single()
        assertEquals(HarnessApplicationStatus.PENDING, before.status)

        val result = gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(APP_ID.value, enabled = true),
        )

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        val application = store.snapshot().applications.single()
        assertEquals(ApplicationRegistrationState.AUTHORIZED, application.state)
        assertEquals(SIGNER, application.signerSha256)
        assertEquals(50, application.lastSeenAtEpochMs)
    }

    @Test
    fun `signature changed identity stays blocked until explicit reauthorization`() {
        val store = InMemoryHostControlPlaneStore(state(ApplicationRegistrationState.SIGNATURE_CHANGED))
        val gateway = StoreHarnessApplicationsGateway(store, epochClock = { 75 })

        assertEquals(HarnessApplicationStatus.IDENTITY_CHANGED, gateway.snapshot().applications.single().status)

        val result = gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(APP_ID.value, enabled = true),
        )

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        assertEquals(ApplicationRegistrationState.AUTHORIZED, store.snapshot().applications.single().state)
    }

    @Test
    fun `unavailable identity cannot be authorized`() {
        val store = InMemoryHostControlPlaneStore(state(ApplicationRegistrationState.UNAVAILABLE))
        val gateway = StoreHarnessApplicationsGateway(store)

        val result = gateway.setApplicationConnectionEnabled(
            HarnessSetApplicationConnectionEnabledCommand(APP_ID.value, enabled = true),
        )

        assertEquals(
            HarnessControlPlaneMutationResult.Rejected("This application identity is unavailable and cannot be authorized"),
            result,
        )
        assertEquals(ApplicationRegistrationState.UNAVAILABLE, store.snapshot().applications.single().state)
    }

    private fun state(registrationState: ApplicationRegistrationState) = HostControlPlaneState(
        applications = listOf(
            RegisteredApplication(
                applicationId = APP_ID,
                packageName = "io.github.daniele21.redactguard",
                signerSha256 = SIGNER,
                displayName = "RedactGuard",
                state = registrationState,
                firstSeenAtEpochMs = 10,
                lastSeenAtEpochMs = 20,
            ),
        ),
    )

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        const val SIGNER = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
    }
}
