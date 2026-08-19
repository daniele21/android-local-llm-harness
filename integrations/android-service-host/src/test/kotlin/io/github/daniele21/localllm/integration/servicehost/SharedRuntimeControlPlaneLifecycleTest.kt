package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedRuntimeControlPlaneLifecycleTest {
    private val caller = AuthorizedCaller(
        uid = 10001,
        packageName = "io.example.consumer",
        applicationId = ApplicationId("consumer-app"),
        allowedUseCases = setOf(UseCaseId("document-pii-detection")),
    )

    @Test
    fun `Binder owner death releases every activation for the exact connection token`() {
        val controlPlane = RecordingControlPlaneHost()
        val lifecycle = FakeLifecycle()
        val delegate = SharedRuntimeHostDelegate(
            client = NoOpLocalLlmClient,
            protocolInfo = protocolInfo(),
            consumerControlPlaneHost = controlPlane,
            controlExecutor = HostControlExecutor { task ->
                task()
                true
            },
            callbackDispatcherFactory = HostCallbackDispatcherFactory {
                HostCallbackDispatcher { task ->
                    task()
                    true
                }
            },
        )
        var registration: RegistrationResultParcel? = null
        delegate.registerClient(caller, hello(), lifecycle, HostResultCallback { registration = it })
        val token = requireNotNull(registration?.clientToken)
        assertNull(registration?.error)

        lifecycle.die()

        assertEquals(listOf(token.value to caller.applicationId), controlPlane.releaseAllCalls)
    }

    private fun hello() = ClientHelloParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        requiredFeatures = listOf(
            BinderProtocolV1.FEATURE_CONSUMER_API_V1,
            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
        ),
        clientBuildId = "control-plane-lifecycle-test",
    )

    private fun protocolInfo() = ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        hostBuildId = "host-test",
    )

    private class FakeLifecycle : ClientLifecycleLinker {
        private var onDeath: (() -> Unit)? = null

        override fun link(onDeath: () -> Unit): ClientDeathLink {
            this.onDeath = onDeath
            return ClientDeathLink { this.onDeath = null }
        }

        fun die() {
            onDeath?.invoke()
        }
    }

    private class RecordingControlPlaneHost : ConsumerControlPlaneHost {
        val releaseAllCalls = mutableListOf<Pair<String, ApplicationId>>()

        override fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult =
            error("Discovery is not expected in lifecycle cleanup test")

        override fun publishedPresets(
            applicationId: ApplicationId,
            useCaseId: UseCaseId,
        ): ConsumerPublishedPresetsResult = error("Preset discovery is not expected in lifecycle cleanup test")

        override fun activate(
            ownerId: String,
            applicationId: ApplicationId,
            request: ConsumerActivationRequest,
        ): ConsumerActivationResult = error("Activation is not expected in lifecycle cleanup test")

        override fun deactivate(
            ownerId: String,
            applicationId: ApplicationId,
            activationId: ConsumerActivationId,
        ): ConsumerDeactivationResult = error("Deactivation is not expected in lifecycle cleanup test")

        override fun releaseAll(ownerId: String, applicationId: ApplicationId) {
            releaseAllCalls += ownerId to applicationId
        }
    }

    private object NoOpLocalLlmClient : LocalLlmClient {
        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.IDLE, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult =
            error("Runtime prepare is not expected in lifecycle cleanup test")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
            error("Session creation is not expected in lifecycle cleanup test")

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle =
            error("Generation is not expected in lifecycle cleanup test")

        override fun closeSession(sessionId: SessionId) = Unit
    }
}
