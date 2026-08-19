package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCase
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerPresetParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumerControlPlaneAuthorizationTest {
    private val allowedUseCase = UseCaseId("allowed")
    private val deniedUseCase = UseCaseId("denied")
    private val caller =
        AuthorizedCaller(
            uid = 10001,
            packageName = "io.example.client",
            applicationId = ApplicationId("consumer-app"),
            allowedUseCases = setOf(allowedUseCase),
        )

    @Test
    fun `discovery exposes only use cases allowed to the authenticated package`() {
        val host = RecordingHost()
        val fixture = fixture(host)
        var result: ConsumerControlPlaneResultParcel? = null

        fixture.operations.discoverUseCases(
            caller,
            request(fixture.token, "discover"),
            HostResultCallback { result = it },
        )

        assertEquals(listOf("allowed"), requireNotNull(result).assignments.map { it.useCaseId })
        assertNull(result?.error)
    }

    @Test
    fun `preset discovery for unauthorized use case fails before host access`() {
        val host = RecordingHost()
        val fixture = fixture(host)
        var result: ConsumerControlPlaneResultParcel? = null

        fixture.operations.discoverPresets(
            caller,
            request(fixture.token, "presets", deniedUseCase.value),
            HostResultCallback { result = it },
        )

        assertEquals("USE_CASE_NOT_ASSIGNED", result?.error?.code)
        assertEquals(0, host.presetCalls)
    }

    @Test
    fun `activation for unauthorized use case fails before host access`() {
        val host = RecordingHost()
        val fixture = fixture(host)
        var result: ConsumerControlPlaneResultParcel? = null
        val request =
            ConsumerControlPlaneRequestParcel(
                clientToken = ClientTokenParcel(fixture.token.value),
                operationId = "activate",
                useCaseId = deniedUseCase.value,
                useCaseRevision = 1,
                bindingRevision = 1,
                preset = ConsumerPresetParcel("balanced", 1),
            )

        fixture.operations.activate(caller, request, HostResultCallback { result = it })

        assertEquals("USE_CASE_NOT_ASSIGNED", result?.error?.code)
        assertEquals(0, host.activationCalls)
    }

    private fun fixture(host: RecordingHost): Fixture {
        val ledger = ClientConnectionLedger()
        val token =
            ledger.register(
                caller,
                negotiatedMinor = 2,
                enabledFeatures =
                    setOf(
                        BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                        BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                    ),
            ) as LedgerResult.Success
        return Fixture(
            token = token.value,
            operations =
                ConsumerControlPlaneHostOperations(
                    ledger = ledger,
                    host = host,
                    controlExecutor =
                        HostControlExecutor { task ->
                            task()
                            true
                        },
                ),
        )
    }

    private fun request(token: HostClientToken, operationId: String, useCaseId: String? = null) = ConsumerControlPlaneRequestParcel(
        clientToken = ClientTokenParcel(token.value),
        operationId = operationId,
        useCaseId = useCaseId,
    )

    private data class Fixture(val token: HostClientToken, val operations: ConsumerControlPlaneHostOperations)

    private inner class RecordingHost : ConsumerControlPlaneHost {
        var presetCalls = 0
        var activationCalls = 0

        override fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult =
            ConsumerAssignedUseCasesResult.Available(
                listOf(
                    assignment(allowedUseCase),
                    assignment(deniedUseCase),
                ),
            )

        override fun publishedPresets(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerPublishedPresetsResult {
            presetCalls += 1
            error("Unauthorized preset discovery must not reach host")
        }

        override fun activate(ownerId: String, applicationId: ApplicationId, request: ConsumerActivationRequest): ConsumerActivationResult {
            activationCalls += 1
            error("Unauthorized activation must not reach host")
        }

        override fun deactivate(
            ownerId: String,
            applicationId: ApplicationId,
            activationId: ConsumerActivationId,
        ): ConsumerDeactivationResult = error("Deactivation is not used in authorization tests")

        override fun releaseAll(ownerId: String, applicationId: ApplicationId) = Unit

        private fun assignment(useCaseId: UseCaseId) = ConsumerAssignedUseCase(
            useCaseId = useCaseId,
            useCaseRevision = 1,
            bindingRevision = 1,
            displayName = useCaseId.value,
            description = "Test assignment ${useCaseId.value}",
            isDefault = false,
        )
    }
}
