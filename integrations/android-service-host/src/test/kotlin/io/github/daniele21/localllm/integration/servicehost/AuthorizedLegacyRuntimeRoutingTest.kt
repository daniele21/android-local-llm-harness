package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationInputParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationOverridesParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OutputConstraintParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionOptionsParcel
import io.github.daniele21.localllm.transport.binder.contract.WireTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizedLegacyRuntimeRoutingTest {
    private val caller =
        AuthorizedCaller(
            uid = 42,
            packageName = "io.redactguard",
            applicationId = ApplicationId("redactguard"),
            allowedUseCases = setOf(UseCaseId("redaction")),
        )

    @Test
    fun `legacy Binder operations use the client created from the verified caller`() {
        val ledger = ClientConnectionLedger()
        val token =
            (ledger.register(caller, BinderProtocolV1.MINOR, emptySet()) as LedgerResult.Success).value
        val tokenParcel = ClientTokenParcel(token.value)
        val resources = HostRuntimeResources()
        resources.attachCallbackDispatcher(
            token,
            HostCallbackDispatcher { task ->
                task()
                true
            },
        )
        val fallback = RoutingFakeClient("fallback")
        val scoped = RoutingFakeClient("scoped")
        val seenCallers = mutableListOf<AuthorizedCaller>()
        val operations =
            HostRuntimeOperations(
                client = fallback,
                ledger = ledger,
                resources = resources,
                controlExecutor =
                    HostControlExecutor { task ->
                        task()
                        true
                    },
                authorizedRuntimeClientFactory =
                    AuthorizedRuntimeClientFactory { verifiedCaller ->
                        seenCallers += verifiedCaller
                        scoped
                    },
            )

        var prepareError: String? = "not-called"
        operations.prepare(
            caller,
            PrepareRequestParcel(tokenParcel, "prepare", "redaction"),
            HostResultCallback { prepareError = it.error?.code },
        )
        assertNull(prepareError)

        var sessionError: String? = "not-called"
        operations.openSession(
            caller,
            OpenSessionRequestParcel(
                clientToken = tokenParcel,
                operationId = "open",
                externalSessionId = "external-session",
                useCaseId = "redaction",
                options = SessionOptionsParcel(WireTags.CONTEXT_AUTO, null, WireTags.SESSION_STATELESS),
            ),
            HostResultCallback { sessionError = it.error?.code },
        )
        assertNull(sessionError)

        operations.generate(caller, generationRequest(tokenParcel), HostEventCallback {})
        operations.closeSession(
            caller,
            CloseSessionRequestParcel(tokenParcel, "external-session"),
        )

        assertEquals(listOf(caller, caller, caller, caller), seenCallers)
        assertEquals(1, scoped.prepareCalls)
        assertEquals(1, scoped.createSessionCalls)
        assertNotNull(scoped.lastGenerationRequest)
        assertEquals(caller.applicationId, scoped.lastGenerationRequest?.applicationId)
        assertEquals(listOf(SessionId("scoped-session")), scoped.closedSessions)
        assertEquals(0, fallback.prepareCalls)
        assertEquals(0, fallback.createSessionCalls)
        assertNull(fallback.lastGenerationRequest)
        assertEquals(emptyList<SessionId>(), fallback.closedSessions)
    }

    private fun generationRequest(token: ClientTokenParcel) =
        GenerationRequestParcel(
            clientToken = token,
            externalRequestId = "external-request",
            externalSessionId = "external-session",
            useCaseId = "redaction",
            input = GenerationInputParcel(WireTags.INPUT_TEXT, "secret", emptyList()),
            overrides = GenerationOverridesParcel(null, null, null, null, null, null, null, null, null, null, null, null, null),
            outputConstraint = OutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null),
        )

    private class RoutingFakeClient(private val name: String) : LocalLlmClient {
        var prepareCalls = 0
        var createSessionCalls = 0
        var lastGenerationRequest: GenerationRequest? = null
        val closedSessions = mutableListOf<SessionId>()

        override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(RuntimeState.READY, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
            prepareCalls += 1
            return PrepareResult(true, null, "$name-ready")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
            createSession(applicationId, useCaseId, SessionOptions())

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId {
            createSessionCalls += 1
            return SessionId("scoped-session")
        }

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            lastGenerationRequest = request
            return object : GenerationHandle {
                override val requestId: RequestId = request.requestId

                override fun cancel() = Unit
            }
        }

        override fun closeSession(sessionId: SessionId) {
            closedSessions += sessionId
        }
    }
}
