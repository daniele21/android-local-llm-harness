package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
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
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionOptionsParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeHostDelegateCloseTest {
    private val caller = AuthorizedCaller(
        uid = 10001,
        packageName = "io.example.client",
        applicationId = ApplicationId("host-derived-app"),
        allowedUseCases = setOf(UseCaseId("summarize")),
    )

    @Test
    fun `service close drains client resources and adapter executors idempotently`() {
        val client = FakeClient()
        val executor = RecordingExecutor()
        val dispatcher = RecordingDispatcher()
        val lifecycle = FakeLifecycle()
        val delegate = SharedRuntimeHostDelegate(
            client = client,
            protocolInfo = protocolInfo(),
            controlExecutor = executor,
            callbackDispatcherFactory = HostCallbackDispatcherFactory { dispatcher },
        )
        val token = register(delegate, lifecycle)
        openSession(delegate, token)
        delegate.generate(caller, generationRequest(token), HostEventCallback {})

        assertFalse(client.handle.cancelled)
        assertTrue(lifecycle.linked)
        assertFalse(dispatcher.closed)

        delegate.close()
        delegate.close()

        assertTrue(client.handle.cancelled)
        assertEquals(listOf(SessionId("internal-session-1")), client.closedSessions)
        assertFalse(lifecycle.linked)
        assertTrue(dispatcher.closed)
        assertTrue(executor.closed)
    }

    @Test
    fun `registration after service close fails without runtime access`() {
        val client = FakeClient()
        val delegate = SharedRuntimeHostDelegate(
            client = client,
            protocolInfo = protocolInfo(),
            controlExecutor = RecordingExecutor(),
        )
        delegate.close()
        var result: RegistrationResultParcel? = null

        delegate.registerClient(caller, hello(), FakeLifecycle(), HostResultCallback { result = it })

        assertEquals(WireErrorCodes.CLIENT_DISCONNECTED, result?.error?.code)
        assertEquals(0, client.prepareCalls)
    }

    private fun register(delegate: SharedRuntimeHostDelegate, lifecycle: FakeLifecycle): ClientTokenParcel {
        var result: RegistrationResultParcel? = null
        delegate.registerClient(caller, hello(), lifecycle, HostResultCallback { result = it })
        assertNull(result?.error)
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

    private fun generationRequest(token: ClientTokenParcel) = GenerationRequestParcel(
        clientToken = token,
        externalRequestId = "external-request-1",
        externalSessionId = "external-session-1",
        useCaseId = "summarize",
        input = GenerationInputParcel(WireTags.INPUT_TEXT, "hello", emptyList()),
        overrides = GenerationOverridesParcel(null, null, null, null, null, null, null, null, null, null, null, null, null),
        outputConstraint = OutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null),
    )

    private fun hello() = ClientHelloParcel(
        BinderProtocolV1.MAJOR,
        BinderProtocolV1.MINOR,
        BinderProtocolV1.MIN_SUPPORTED_MINOR,
        emptyList(),
        "client-test",
    )

    private fun protocolInfo() = ProtocolInfoParcel(
        BinderProtocolV1.MAJOR,
        BinderProtocolV1.MINOR,
        BinderProtocolV1.MIN_SUPPORTED_MINOR,
        BinderProtocolV1.KNOWN_FEATURES.sorted(),
        "host-test",
    )

    private class RecordingExecutor : HostControlExecutor, AutoCloseable {
        var closed = false

        override fun execute(task: () -> Unit): Boolean {
            if (closed) return false
            task()
            return true
        }

        override fun close() {
            closed = true
        }
    }

    private class RecordingDispatcher : HostCallbackDispatcher {
        var closed = false

        override fun dispatch(task: () -> Unit): Boolean {
            if (closed) return false
            task()
            return true
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeLifecycle : ClientLifecycleLinker {
        private var onDeath: (() -> Unit)? = null
        var linked = false
            private set

        override fun link(onDeath: () -> Unit): ClientDeathLink {
            this.onDeath = onDeath
            linked = true
            return ClientDeathLink {
                linked = false
                this.onDeath = null
            }
        }
    }

    private class FakeClient : LocalLlmClient {
        var prepareCalls = 0
        val closedSessions = mutableListOf<SessionId>()
        val handle = FakeHandle()
        private var sessionCounter = 0

        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.IDLE, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
            prepareCalls += 1
            return PrepareResult(true, ModelDigest("ab".repeat(32)), "ready")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
            sessionCounter += 1
            return SessionId("internal-session-$sessionCounter")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId =
            createSession(applicationId, useCaseId)

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            handle.requestIdValue = request.requestId
            return handle
        }

        override fun closeSession(sessionId: SessionId) {
            closedSessions += sessionId
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
