package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ContextPolicy
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderLifecycleAdapterTest {
    private val token = successfulRegistration().clientToken!!
    private val useCaseId = UseCaseId("console-inference")

    @Test
    fun `prepare waits off-main and maps successful result`() {
        val service = FakeSharedRuntimeRemoteService().apply {
            prepareHandler = { request, callback ->
                callback(
                    PrepareResultParcel(
                        operationId = request.operationId,
                        ready = true,
                        modelDigestSha256 = "a".repeat(64),
                        detail = "ready",
                        error = null,
                    ),
                )
            }
        }
        val adapter = adapter(service)

        val result = adapter.prepare(useCaseId)

        assertTrue(result.ready)
        assertEquals("a".repeat(64), result.modelDigest?.sha256)
        assertEquals("ready", result.detail)
    }

    @Test
    fun `prepare fails safely when disconnected`() {
        val adapter = BinderLifecycleAdapter(
            endpointProvider = { null },
            blockingCallGuard = BlockingCallGuard {},
            timeouts = BinderLifecycleTimeouts(10),
            correlationIds = deterministicIds(),
        )

        val result = adapter.prepare(useCaseId)

        assertFalse(result.ready)
        assertEquals("Shared runtime is not connected", result.detail)
    }

    @Test
    fun `blocking lifecycle operation rejects disallowed caller before transport use`() {
        val service = FakeSharedRuntimeRemoteService()
        val adapter = BinderLifecycleAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token) },
            blockingCallGuard = BlockingCallGuard { error("main thread") },
            correlationIds = deterministicIds(),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            adapter.prepare(useCaseId)
        }

        assertEquals("main thread", failure.message)
    }

    @Test
    fun `prepare timeout is bounded and safe`() {
        val service = FakeSharedRuntimeRemoteService().apply {
            prepareHandler = { _, _ -> Unit }
        }
        val adapter = adapter(service, timeoutMillis = 1)

        val result = adapter.prepare(useCaseId)

        assertFalse(result.ready)
        assertEquals("Shared runtime prepare timed out", result.detail)
    }

    @Test
    fun `prepare wakes immediately when its endpoint epoch is invalidated`() {
        val invalidations = FakeEndpointInvalidations()
        val service = FakeSharedRuntimeRemoteService()
        val endpoint = RegisteredSharedRuntimeEndpoint(service, token, connectionEpoch = 3L)
        service.prepareHandler = { _, _ -> invalidations.invalidate(3L, "host died") }
        val adapter = BinderLifecycleAdapter(
            endpointProvider = { endpoint },
            endpointInvalidations = invalidations,
            blockingCallGuard = BlockingCallGuard {},
            timeouts = BinderLifecycleTimeouts(5_000),
            correlationIds = deterministicIds(),
        )

        val result = adapter.prepare(useCaseId)

        assertFalse(result.ready)
        assertEquals("SERVICE_DISCONNECTED: host died", result.detail)
    }

    @Test
    fun `open session maps opaque external session id`() {
        val service = successfulSessionService()
        val adapter = adapter(service)

        val sessionId = adapter.openSession(
            useCaseId,
            SessionOptions(
                contextPolicy = ContextPolicy.Manual(2048),
                kind = SessionKind.CONVERSATIONAL,
            ),
        )

        assertEquals("id-2", sessionId.value)
    }

    @Test
    fun `open session surfaces host safe failure`() {
        val service = FakeSharedRuntimeRemoteService().apply {
            openSessionHandler = { request, callback ->
                callback(
                    SessionResultParcel(
                        operationId = request.operationId,
                        externalSessionId = null,
                        error = WireErrorParcel(
                            code = WireErrorCodes.SESSION_UNAVAILABLE,
                            safeMessage = "Session unavailable",
                            retryable = false,
                        ),
                    ),
                )
            }
        }
        val adapter = adapter(service)

        val failure = assertThrows(IllegalStateException::class.java) {
            adapter.openSession(useCaseId, SessionOptions())
        }

        assertEquals("Session unavailable", failure.message)
    }

    @Test
    fun `open session timeout closes the known external id best effort`() {
        val service = FakeSharedRuntimeRemoteService().apply {
            openSessionHandler = { _, _ -> Unit }
        }
        val adapter = adapter(service, timeoutMillis = 1)

        val failure = assertThrows(IllegalStateException::class.java) {
            adapter.openSession(useCaseId, SessionOptions())
        }

        assertEquals("Shared runtime open-session timed out", failure.message)
        assertEquals(1, service.closeSessionCalls)
    }

    @Test
    fun `open session endpoint invalidation becomes service disconnected`() {
        val invalidations = FakeEndpointInvalidations()
        val service = FakeSharedRuntimeRemoteService()
        val endpoint = RegisteredSharedRuntimeEndpoint(service, token, connectionEpoch = 5L)
        service.openSessionHandler = { _, _ -> invalidations.invalidate(5L, "binder died") }
        val adapter = BinderLifecycleAdapter(
            endpointProvider = { endpoint },
            endpointInvalidations = invalidations,
            blockingCallGuard = BlockingCallGuard {},
            timeouts = BinderLifecycleTimeouts(5_000),
            correlationIds = deterministicIds(),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            adapter.openSession(useCaseId, SessionOptions())
        }

        assertEquals("SERVICE_DISCONNECTED: binder died", failure.message)
    }

    @Test
    fun `close session is best effort and idempotent for its owning epoch`() {
        val service = successfulSessionService()
        val adapter = adapter(service)
        val sessionId = adapter.openSession(useCaseId, SessionOptions())

        adapter.closeSession(sessionId)
        adapter.closeSession(sessionId)

        assertEquals(1, service.closeSessionCalls)
    }

    @Test
    fun `old session is never closed against a replacement registration`() {
        val oldService = successfulSessionService()
        val newService = FakeSharedRuntimeRemoteService()
        var endpoint: RegisteredSharedRuntimeEndpoint? = RegisteredSharedRuntimeEndpoint(
            oldService,
            token,
            connectionEpoch = 1L,
        )
        val adapter = BinderLifecycleAdapter(
            endpointProvider = { endpoint },
            blockingCallGuard = BlockingCallGuard {},
            correlationIds = deterministicIds(),
        )
        val sessionId = adapter.openSession(useCaseId, SessionOptions())

        endpoint = RegisteredSharedRuntimeEndpoint(
            newService,
            successfulRegistration().clientToken!!,
            connectionEpoch = 2L,
        )
        adapter.closeSession(sessionId)

        assertEquals(0, oldService.closeSessionCalls)
        assertEquals(0, newService.closeSessionCalls)
    }

    private fun adapter(service: FakeSharedRuntimeRemoteService, timeoutMillis: Long = 100): BinderLifecycleAdapter =
        BinderLifecycleAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token) },
            blockingCallGuard = BlockingCallGuard {},
            timeouts = BinderLifecycleTimeouts(timeoutMillis),
            correlationIds = deterministicIds(),
        )

    private fun successfulSessionService(): FakeSharedRuntimeRemoteService = FakeSharedRuntimeRemoteService().apply {
        openSessionHandler = { request, callback ->
            callback(
                SessionResultParcel(
                    operationId = request.operationId,
                    externalSessionId = request.externalSessionId,
                    error = null,
                ),
            )
        }
    }

    private fun deterministicIds(): CorrelationIdSource {
        var next = 0
        return CorrelationIdSource {
            next += 1
            "id-$next"
        }
    }
}
