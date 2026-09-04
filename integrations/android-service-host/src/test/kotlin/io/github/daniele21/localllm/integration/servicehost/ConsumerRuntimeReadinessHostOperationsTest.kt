package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadiness
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumerRuntimeReadinessHostOperationsTest {
    private val useCaseId = io.github.daniele21.localllm.contracts.UseCaseId("document-pii-detection")
    private val caller =
        AuthorizedCaller(
            uid = 10001,
            packageName = "io.example.client",
            applicationId = ApplicationId("consumer-app"),
            allowedUseCases = setOf(useCaseId),
        )

    @Test
    fun `wired provider returns only consumer-safe runtime lifecycle`() {
        val host = RecordingReadinessHost()
        val activity = ConsumerRuntimeActivityTracker()
        val fixture = fixture(host = host, readinessFeature = true, activity = activity)
        activity.beginPreparation(fixture.token)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-1", "activation-1"),
            HostResultCallback { result = it },
        )

        val actual = requireNotNull(result)
        assertNull(actual.error)
        assertEquals("activation-1", actual.activationId)
        assertEquals(ConsumerRuntimePhase.PREPARING.name, actual.phaseTag)
        assertEquals(ConsumerPreparationAction.REUSING.name, actual.preparationActionTag)
        assertEquals(1, host.calls)
        assertFalse(actual.toString().contains("digest", ignoreCase = true))
        assertFalse(actual.toString().contains("path", ignoreCase = true))
        assertFalse(actual.toString().contains("model", ignoreCase = true))
    }

    @Test
    fun `preparation owned by another connection is masked`() {
        val host = RecordingReadinessHost()
        val activity = ConsumerRuntimeActivityTracker()
        val fixture = fixture(host = host, readinessFeature = true, activity = activity)
        activity.beginPreparation(HostClientToken("other-connection"))
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-private-prep", "activation-1"),
            HostResultCallback { result = it },
        )

        assertEquals(ConsumerRuntimePhase.READY.name, result?.phaseTag)
        assertEquals(ConsumerPreparationAction.NONE.name, result?.preparationActionTag)
    }

    @Test
    fun `generation owned by another connection is masked`() {
        val host = RecordingReadinessHost(
            ConsumerRuntimeReadiness(
                activationId = ConsumerActivationId("activation-1"),
                phase = ConsumerRuntimePhase.GENERATING,
            ),
        )
        val activity = ConsumerRuntimeActivityTracker()
        val fixture = fixture(host = host, readinessFeature = true, activity = activity)
        activity.beginGeneration(HostClientToken("other-connection"))
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-private-generation", "activation-1"),
            HostResultCallback { result = it },
        )

        assertEquals(ConsumerRuntimePhase.READY.name, result?.phaseTag)
    }

    @Test
    fun `connection-scoped failure overrides shared runtime state`() {
        val host = RecordingReadinessHost()
        val activity = ConsumerRuntimeActivityTracker()
        val fixture = fixture(host = host, readinessFeature = true, activity = activity)
        activity.finishPreparation(fixture.token, ConsumerRuntimeIssue.MODEL_UNAVAILABLE)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-failure", "activation-1"),
            HostResultCallback { result = it },
        )

        assertEquals(ConsumerRuntimePhase.FAILED.name, result?.phaseTag)
        assertEquals(ConsumerRuntimeIssue.MODEL_UNAVAILABLE.name, result?.issueTag)
    }

    @Test
    fun `provider is not consulted when readiness feature was not negotiated`() {
        val host = RecordingReadinessHost()
        val fixture = fixture(host = host, readinessFeature = false)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-2", "activation-2"),
            HostResultCallback { result = it },
        )

        assertEquals("FEATURE_UNAVAILABLE", result?.error?.code)
        assertEquals(0, host.calls)
    }

    @Test
    fun `missing real readiness provider fails closed`() {
        val fixture = fixture(host = null, readinessFeature = true)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-3", "activation-3"),
            HostResultCallback { result = it },
        )

        assertEquals("FEATURE_UNAVAILABLE", result?.error?.code)
    }

    @Test
    fun `missing activation identity is rejected before provider access`() {
        val host = RecordingReadinessHost()
        val fixture = fixture(host = host, readinessFeature = true)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        fixture.operations.runtimeReadiness(
            caller,
            request(fixture.token, "readiness-4", null),
            HostResultCallback { result = it },
        )

        assertEquals("INVALID_REQUEST", result?.error?.code)
        assertEquals(0, host.calls)
    }

    private fun fixture(
        host: ConsumerRuntimeReadinessHost?,
        readinessFeature: Boolean,
        activity: ConsumerRuntimeActivityTracker = ConsumerRuntimeActivityTracker(),
    ): Fixture {
        val ledger = ClientConnectionLedger()
        val features = buildSet {
            add(BinderProtocolV1.FEATURE_CONSUMER_API_V1)
            add(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1)
            if (readinessFeature) add(BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1)
        }
        val token =
            ledger.register(
                caller,
                negotiatedMinor = if (readinessFeature) 4 else 3,
                enabledFeatures = features,
            ) as LedgerResult.Success
        return Fixture(
            token = token.value,
            operations =
            ConsumerRuntimeReadinessHostOperations(
                ledger = ledger,
                host = host,
                readinessExecutor =
                HostControlExecutor { task ->
                    task()
                    true
                },
                activity = activity,
            ),
        )
    }

    private fun request(token: HostClientToken, operationId: String, activationId: String?) = ConsumerControlPlaneRequestParcel(
        clientToken = ClientTokenParcel(token.value),
        operationId = operationId,
        activationId = activationId,
    )

    private data class Fixture(val token: HostClientToken, val operations: ConsumerRuntimeReadinessHostOperations)

    private class RecordingReadinessHost(
        private val readiness: ConsumerRuntimeReadiness =
            ConsumerRuntimeReadiness(
                activationId = ConsumerActivationId("activation-1"),
                phase = ConsumerRuntimePhase.PREPARING,
                preparationAction = ConsumerPreparationAction.REUSING,
            ),
    ) : ConsumerRuntimeReadinessHost {
        var calls = 0

        override fun runtimeReadiness(
            ownerId: String,
            applicationId: ApplicationId,
            activationId: ConsumerActivationId,
        ): ConsumerRuntimeReadinessResult {
            calls += 1
            return ConsumerRuntimeReadinessResult.Available(readiness.copy(activationId = activationId))
        }
    }
}
