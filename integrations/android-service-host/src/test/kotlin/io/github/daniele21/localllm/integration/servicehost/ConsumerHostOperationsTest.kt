package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerHostOperationsTest {
    private val useCaseId = UseCaseId("summarize")
    private val caller =
        AuthorizedCaller(
            uid = 10001,
            packageName = "io.example.consumer",
            applicationId = ApplicationId("consumer-app"),
            allowedUseCases = setOf(useCaseId),
        )

    @Test
    fun `consumer registration derives application identity and capabilities stay policy scoped`() {
        val consumer = FakeConsumerClient(useCaseId)
        var factoryApplicationId: ApplicationId? = null
        val delegate =
            SharedRuntimeHostDelegate(
                client = UnusedLocalClient(),
                protocolInfo = protocolInfo(),
                consumerClientFactory = { applicationId ->
                    factoryApplicationId = applicationId
                    consumer
                },
                controlExecutor = immediateControlExecutor(),
                callbackDispatcherFactory = immediateDispatcherFactory(),
            )
        val token = register(delegate)
        var result: io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel? = null

        delegate.consumerOperations.capabilities(
            caller,
            ConsumerRequestParcel(
                clientToken = token,
                operationId = "capabilities-1",
                useCaseId = useCaseId.value,
            ),
            HostResultCallback { result = it },
        )

        assertEquals(caller.applicationId, factoryApplicationId)
        assertEquals(1, consumer.capabilityCalls)
        assertNull(result?.error)
        assertEquals(useCaseId.value, result?.capabilities?.useCaseId)
        assertEquals("cap-rev-1", result?.capabilities?.capabilityRevision)
        assertEquals(listOf(ConsumerOutputConstraintKind.TEXT.name), result?.capabilities?.outputConstraintTags)
    }

    @Test
    fun `unauthorized consumer use case is rejected before public client`() {
        val consumer = FakeConsumerClient(useCaseId)
        val delegate =
            SharedRuntimeHostDelegate(
                client = UnusedLocalClient(),
                protocolInfo = protocolInfo(),
                consumerClientFactory = { consumer },
                controlExecutor = immediateControlExecutor(),
                callbackDispatcherFactory = immediateDispatcherFactory(),
            )
        val token = register(delegate)
        var errorCode: String? = null

        delegate.consumerOperations.capabilities(
            caller,
            ConsumerRequestParcel(
                clientToken = token,
                operationId = "capabilities-denied",
                useCaseId = "forbidden",
            ),
            HostResultCallback { errorCode = it.error?.code },
        )

        assertEquals(WireErrorCodes.UNAUTHORIZED_USE_CASE, errorCode)
        assertEquals(0, consumer.capabilityCalls)
    }

    private fun register(delegate: SharedRuntimeHostDelegate): ClientTokenParcel {
        var result: RegistrationResultParcel? = null
        delegate.registerClient(caller, consumerHello(), FakeLifecycle(), HostResultCallback { result = it })
        assertNull(result?.error)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in requireNotNull(result).enabledFeatures)
        return requireNotNull(result?.clientToken)
    }

    private fun consumerHello() =
        ClientHelloParcel(
            protocolMajor = BinderProtocolV1.MAJOR,
            protocolMinor = BinderProtocolV1.MINOR,
            minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
            requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1),
            clientBuildId = "consumer-host-test",
        )

    private fun protocolInfo() =
        ProtocolInfoParcel(
            protocolMajor = BinderProtocolV1.MAJOR,
            protocolMinor = BinderProtocolV1.MINOR,
            minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
            supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
            hostBuildId = "host-test",
        )

    private fun immediateControlExecutor() = HostControlExecutor { task ->
        task()
        true
    }

    private fun immediateDispatcherFactory() =
        HostCallbackDispatcherFactory {
            HostCallbackDispatcher { task ->
                task()
                true
            }
        }

    private class FakeLifecycle : ClientLifecycleLinker {
        override fun link(onDeath: () -> Unit): ClientDeathLink = ClientDeathLink {}
    }

    private class FakeConsumerClient(private val useCaseId: UseCaseId) : ConsumerLocalLlmClient {
        var capabilityCalls = 0

        override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult {
            capabilityCalls += 1
            return ConsumerCapabilityResult.Available(
                UseCaseCapabilities(
                    useCaseId = this.useCaseId,
                    readiness = UseCaseReadiness.READY,
                    presets = emptyList(),
                    defaultPreset = null,
                    reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
                    outputConstraints = setOf(ConsumerOutputConstraintKind.TEXT),
                    defaultOutputConstraint = ConsumerOutputConstraintKind.TEXT,
                    sessionKinds = setOf(SessionKind.STATELESS),
                    defaultSessionKind = SessionKind.STATELESS,
                    limits = ConsumerLimits(32_768, 128, 32_768),
                    capabilityRevision = "cap-rev-1",
                ),
            )
        }

        override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult =
            ConsumerPrepareResult.Rejected(
                io.github.daniele21.localllm.contracts.ConsumerFailure(
                    io.github.daniele21.localllm.contracts.ConsumerErrorCode.PREPARE_FAILED,
                    "unused",
                ),
            )

        override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult =
            ConsumerSessionResult.Rejected(
                io.github.daniele21.localllm.contracts.ConsumerFailure(
                    io.github.daniele21.localllm.contracts.ConsumerErrorCode.SESSION_NOT_FOUND,
                    "unused",
                ),
            )

        override fun generate(
            request: ConsumerGenerationRequest,
            listener: ConsumerGenerationListener,
        ): ConsumerGenerationStartResult =
            ConsumerGenerationStartResult.Accepted(
                object : ConsumerGenerationHandle {
                    override val requestId: RequestId = request.requestId
                    override fun cancel() = Unit
                },
            )

        override fun closeSession(sessionId: SessionId) = Unit
    }

    private class UnusedLocalClient : LocalLlmClient {
        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.IDLE, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId) =
            PrepareResult(false, null, "unused")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId) = SessionId("unused")

        override fun createSession(
            applicationId: ApplicationId,
            useCaseId: UseCaseId,
            options: SessionOptions,
        ) = SessionId("unused")

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle =
            object : GenerationHandle {
                override val requestId: RequestId = request.requestId
                override fun cancel() = Unit
            }

        override fun closeSession(sessionId: SessionId) = Unit
    }
}
