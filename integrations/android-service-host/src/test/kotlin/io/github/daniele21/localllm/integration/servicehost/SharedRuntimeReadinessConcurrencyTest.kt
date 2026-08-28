package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadiness
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SharedRuntimeReadinessConcurrencyTest {
    @Test
    fun `readiness completes while model control lane is occupied`() {
        val caller = AuthorizedCaller(
            uid = 10001,
            packageName = "io.example.client",
            applicationId = ApplicationId("consumer-app"),
            allowedUseCases = setOf(UseCaseId("document-pii-detection")),
        )
        val ledger = ClientConnectionLedger()
        val token = (
            ledger.register(
                caller = caller,
                negotiatedMinor = 4,
                enabledFeatures = setOf(
                    BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                    BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                    BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1,
                ),
            ) as LedgerResult.Success
            ).value
        val controlExecutor = BoundedSerialHostControlExecutor()
        val readinessExecutor = BoundedSerialHostControlExecutor()
        val delegate = SharedRuntimeHostDelegate(
            client = UnusedLocalLlmClient,
            protocolInfo = hostProtocolInfo(
                hostBuildId = "test",
                consumerApiEnabled = true,
                consumerControlPlaneEnabled = true,
                consumerRuntimeReadinessEnabled = true,
            ),
            consumerRuntimeReadinessHost = ReadyReadinessHost,
            ledger = ledger,
            controlExecutor = controlExecutor,
            readinessExecutor = readinessExecutor,
        )
        val controlStarted = CountDownLatch(1)
        val releaseControl = CountDownLatch(1)
        val readinessCompleted = CountDownLatch(1)
        var result: ConsumerRuntimeReadinessResultParcel? = null

        assertTrue(
            controlExecutor.execute {
                controlStarted.countDown()
                releaseControl.await(5, TimeUnit.SECONDS)
            },
        )
        assertTrue(controlStarted.await(1, TimeUnit.SECONDS))

        delegate.readinessOperations.runtimeReadiness(
            caller = caller,
            request = ConsumerControlPlaneRequestParcel(
                clientToken = ClientTokenParcel(token.value),
                operationId = "readiness-concurrent",
                activationId = "activation-1",
            ),
            callback = HostResultCallback { value ->
                result = value
                readinessCompleted.countDown()
            },
        )

        assertTrue(readinessCompleted.await(1, TimeUnit.SECONDS))
        assertEquals(ConsumerRuntimePhase.READY.name, result?.phaseTag)

        releaseControl.countDown()
        delegate.close()
    }

    private object ReadyReadinessHost : ConsumerRuntimeReadinessHost {
        override fun runtimeReadiness(
            ownerId: String,
            applicationId: ApplicationId,
            activationId: ConsumerActivationId,
        ): ConsumerRuntimeReadinessResult = ConsumerRuntimeReadinessResult.Available(
            ConsumerRuntimeReadiness(
                activationId = activationId,
                phase = ConsumerRuntimePhase.READY,
            ),
        )
    }

    private object UnusedLocalLlmClient : LocalLlmClient {
        override fun runtimeSnapshot(): RuntimeSnapshot = error("unused")

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = error("unused")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId = error("unused")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId = error("unused")

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle = error("unused")

        override fun closeSession(sessionId: SessionId) = Unit
    }
}
