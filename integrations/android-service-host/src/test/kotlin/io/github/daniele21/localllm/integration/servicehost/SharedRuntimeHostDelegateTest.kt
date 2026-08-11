package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationInputParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationOverridesParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OutputConstraintParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionOptionsParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeHostDelegateTest {
    private val caller =
        AuthorizedCaller(
            uid = 10001,
            packageName = "io.example.client",
            applicationId = ApplicationId("host-derived-app"),
            allowedUseCases = setOf(UseCaseId("summarize")),
        )

    @Test
    fun registrationNegotiatesWithoutTouchingRuntime() {
        val client = FakeClient()
        val delegate = delegate(client)
        var registration: RegistrationResultParcel? = null

        delegate.registerClient(caller, hello(), FakeLifecycle(), HostResultCallback { registration = it })

        assertEquals(0, client.prepareCalls)
        assertEquals(0, client.createSessionCalls)
        assertEquals(BinderProtocolV1.MINOR, registration?.negotiatedMinor)
        assertNull(registration?.error)
    }

    @Test
    fun prepareUsesHostDerivedIdentityAndAuthorizedUseCase() {
        val client = FakeClient()
        val delegate = delegate(client)
        val token = register(delegate)
        var ready = false

        delegate.prepare(
            caller,
            PrepareRequestParcel(token, "prepare-1", "summarize"),
            HostResultCallback { ready = it.ready },
        )

        assertTrue(ready)
        assertEquals(caller.applicationId, client.lastPreparedApplicationId)
        assertEquals(UseCaseId("summarize"), client.lastPreparedUseCase)
    }

    @Test
    fun deniedUseCaseNeverTouchesRuntime() {
        val client = FakeClient()
        val delegate = delegate(client)
        val token = register(delegate)
        var errorCode: String? = null

        delegate.prepare(
            caller,
            PrepareRequestParcel(token, "prepare-1", "forbidden"),
            HostResultCallback { errorCode = it.error?.code },
        )

        assertEquals(WireErrorCodes.UNAUTHORIZED_USE_CASE, errorCode)
        assertEquals(0, client.prepareCalls)
    }

    @Test
    fun sessionAndGenerationUseInternalIdsAndPreserveExternalCorrelation() {
        val client = FakeClient()
        val delegate = delegate(client)
        val token = register(delegate)
        openSession(delegate, token)
        val events = mutableListOf<io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel>()

        delegate.generate(caller, generationRequest(token), HostEventCallback(events::add))

        val coreRequest = requireNotNull(client.lastGenerationRequest)
        assertEquals(caller.applicationId, coreRequest.applicationId)
        assertEquals(SessionId("internal-session-1"), coreRequest.sessionId)
        assertNotEquals("external-request-1", coreRequest.requestId.value)

        client.emit(GenerationEvent.Queued(coreRequest.requestId, 0))
        client.emit(GenerationEvent.Started(coreRequest.requestId, ModelDigest("ab".repeat(32))))
        client.emit(
            GenerationEvent.TextDelta(
                coreRequest.requestId,
                "answer",
                generatedTokens = 1,
            ),
        )
        client.emit(GenerationEvent.Completed(coreRequest.requestId, "answer", metrics()))

        assertEquals(listOf(0L, 1L, 2L, 3L), events.map { it.sequence })
        assertTrue(events.all { it.externalRequestId == "external-request-1" })
        assertEquals(WireTags.EVENT_COMPLETED, events.last().eventTag)
        assertNull(events.last().deltaText)
    }

    @Test
    fun lifecycleDeathCancelsOwnedRequestAndClosesOwnedSession() {
        val client = FakeClient()
        val lifecycle = FakeLifecycle()
        val delegate = delegate(client)
        val token = register(delegate, lifecycle)
        openSession(delegate, token)
        delegate.generate(caller, generationRequest(token), HostEventCallback {})

        lifecycle.die()

        assertTrue(client.handle.cancelled)
        assertEquals(listOf(SessionId("internal-session-1")), client.closedSessions)
    }

    @Test
    fun rejectedControlSubmissionReturnsTypedTransportFailure() {
        val client = FakeClient()
        val delegate =
            SharedRuntimeHostDelegate(
                client = client,
                protocolInfo = protocolInfo(),
                controlExecutor = HostControlExecutor { false },
            )
        var result: RegistrationResultParcel? = null

        delegate.registerClient(caller, hello(), FakeLifecycle(), HostResultCallback { result = it })

        assertEquals(WireErrorCodes.TRANSPORT_FAILURE, result?.error?.code)
        assertEquals(0, client.prepareCalls)
    }

    private fun delegate(client: FakeClient) =
        SharedRuntimeHostDelegate(
            client = client,
            protocolInfo = protocolInfo(),
            controlExecutor = HostControlExecutor { task ->
                task()
                true
            },
        )

    private fun register(delegate: SharedRuntimeHostDelegate, lifecycle: FakeLifecycle = FakeLifecycle()): ClientTokenParcel {
        var result: RegistrationResultParcel? = null
        delegate.registerClient(caller, hello(), lifecycle, HostResultCallback { result = it })
        return requireNotNull(result?.clientToken)
    }

    private fun openSession(delegate: SharedRuntimeHostDelegate, token: ClientTokenParcel) {
        delegate.openSession(
            caller,
            OpenSessionRequestParcel(
                clientToken = token,
                operationId = "session-op-1",
                externalSessionId = "external-session-1",
                useCaseId = "summarize",
                options = SessionOptionsParcel(WireTags.CONTEXT_AUTO, null, WireTags.SESSION_CONVERSATIONAL),
            ),
            HostResultCallback { result -> assertNull(result.error) },
        )
    }

    private fun generationRequest(token: ClientTokenParcel) =
        GenerationRequestParcel(
            clientToken = token,
            externalRequestId = "external-request-1",
            externalSessionId = "external-session-1",
            useCaseId = "summarize",
            input = GenerationInputParcel(WireTags.INPUT_TEXT, "hello", emptyList()),
            overrides = GenerationOverridesParcel(null, null, null, null, null, null, null, null, null, null, null, null, null),
            outputConstraint = OutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null),
        )

    private fun hello() =
        ClientHelloParcel(
            BinderProtocolV1.MAJOR,
            BinderProtocolV1.MINOR,
            BinderProtocolV1.MIN_SUPPORTED_MINOR,
            emptyList(),
            "client-test",
        )

    private fun protocolInfo() =
        ProtocolInfoParcel(
            BinderProtocolV1.MAJOR,
            BinderProtocolV1.MINOR,
            BinderProtocolV1.MIN_SUPPORTED_MINOR,
            BinderProtocolV1.KNOWN_FEATURES.sorted(),
            "host-test",
        )

    private fun metrics() =
        GenerationMetrics(
            queueMs = 0,
            modelLoadMs = 0,
            timeToFirstTokenMs = 1,
            totalMs = 2,
            inputTokens = 1,
            outputTokens = 1,
            decodeTokensPerSecond = 1.0,
        )

    private class FakeLifecycle : ClientLifecycleLinker {
        private var onDeath: (() -> Unit)? = null
        private var linked = false

        override fun link(onDeath: () -> Unit): ClientDeathLink {
            this.onDeath = onDeath
            linked = true
            return ClientDeathLink {
                linked = false
                this.onDeath = null
            }
        }

        fun die() {
            if (linked) onDeath?.invoke()
        }
    }

    private class FakeClient : LocalLlmClient {
        var prepareCalls = 0
        var createSessionCalls = 0
        var lastPreparedApplicationId: ApplicationId? = null
        var lastPreparedUseCase: UseCaseId? = null
        var lastGenerationRequest: GenerationRequest? = null
        var listener: GenerationListener? = null
        val closedSessions = mutableListOf<SessionId>()
        val handle = FakeHandle()

        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.IDLE, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
            prepareCalls += 1
            lastPreparedApplicationId = applicationId
            lastPreparedUseCase = useCaseId
            return PrepareResult(true, ModelDigest("ab".repeat(32)), "internal detail must not cross Binder")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
            createSessionCalls += 1
            return SessionId("internal-session-$createSessionCalls")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId =
            createSession(applicationId, useCaseId)

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            lastGenerationRequest = request
            this.listener = listener
            handle.requestIdValue = request.requestId
            return handle
        }

        override fun closeSession(sessionId: SessionId) {
            closedSessions += sessionId
        }

        fun emit(event: GenerationEvent) {
            requireNotNull(listener).onEvent(event)
        }
    }

    private class FakeHandle : GenerationHandle {
        var requestIdValue = RequestId("unassigned")
        var cancelled = false

        override val requestId: RequestId
            get() = requestIdValue

        override fun cancel() {
            cancelled = true
        }
    }
}
