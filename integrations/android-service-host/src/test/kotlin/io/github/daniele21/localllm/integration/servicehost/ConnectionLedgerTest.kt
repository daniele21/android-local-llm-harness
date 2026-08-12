package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLedgerTest {
    @Test
    fun sameExternalIdsAreIsolatedAcrossClients() {
        val identifiers = FakeIdentifiers()
        val ledger = ClientConnectionLedger(identifiers = identifiers)
        val callerA = caller(uid = 10001, packageName = "io.example.a")
        val callerB = caller(uid = 10002, packageName = "io.example.b")
        val tokenA = successValue(ledger.register(callerA))
        val tokenB = successValue(ledger.register(callerB))

        val requestA = successValue(ledger.allocateRequest(tokenA, callerA, "request-1"))
        val requestB = successValue(ledger.allocateRequest(tokenB, callerB, "request-1"))
        successValue(ledger.registerSession(tokenA, callerA, "session-1", SessionId("internal-session-a")))
        successValue(ledger.registerSession(tokenB, callerB, "session-1", SessionId("internal-session-b")))

        assertNotEquals(requestA, requestB)
        assertEquals(SessionId("internal-session-a"), successValue(ledger.sessionId(tokenA, callerA, "session-1")))
        assertEquals(SessionId("internal-session-b"), successValue(ledger.sessionId(tokenB, callerB, "session-1")))
    }

    @Test
    fun crossCallerTokenUseFailsClosed() {
        val ledger = ClientConnectionLedger(identifiers = FakeIdentifiers())
        val owner = caller(uid = 10001, packageName = "io.example.owner")
        val other = caller(uid = 10002, packageName = "io.example.other")
        val token = successValue(ledger.register(owner))

        assertFailure(LedgerFailure.CLIENT_TOKEN_INVALID, ledger.allocateRequest(token, other, "request-1"))
    }

    @Test
    fun quotasRejectBeforeUnboundedGrowth() {
        val ledger =
            ClientConnectionLedger(
                quotas = HostQuotas(maxConnections = 1, maxSessionsPerConnection = 1, maxRequestsPerConnection = 1),
                identifiers = FakeIdentifiers(),
            )
        val caller = caller(uid = 10001, packageName = "io.example.client")
        val token = successValue(ledger.register(caller))

        assertFailure(LedgerFailure.CONNECTION_LIMIT, ledger.register(caller(uid = 10002, packageName = "io.example.second")))
        successValue(ledger.registerSession(token, caller, "session-1", SessionId("internal-session-1")))
        assertFailure(
            LedgerFailure.SESSION_LIMIT,
            ledger.registerSession(token, caller, "session-2", SessionId("internal-session-2")),
        )
        successValue(ledger.allocateRequest(token, caller, "request-1"))
        assertFailure(LedgerFailure.REQUEST_LIMIT, ledger.allocateRequest(token, caller, "request-2"))
    }

    @Test
    fun closingConnectionRejectsNewWorkAndReturnsOwnedResources() {
        val ledger = ClientConnectionLedger(identifiers = FakeIdentifiers())
        val caller = caller(uid = 10001, packageName = "io.example.client")
        val token = successValue(ledger.register(caller))
        successValue(ledger.registerSession(token, caller, "session-1", SessionId("internal-session-1")))
        val requestId = successValue(ledger.allocateRequest(token, caller, "request-1"))

        val closing = successValue(ledger.beginClose(token, caller))

        assertEquals(listOf(requestId), closing.requestIds)
        assertEquals(listOf(SessionId("internal-session-1")), closing.sessionIds)
        assertFailure(LedgerFailure.CLIENT_CLOSING, ledger.allocateRequest(token, caller, "request-2"))
        successValue(ledger.finishClose(token, caller))
        assertFailure(LedgerFailure.CLIENT_TOKEN_INVALID, ledger.beginClose(token, caller))
    }

    @Test
    fun duplicateExternalIdsWithinOneClientFailClosed() {
        val ledger = ClientConnectionLedger(identifiers = FakeIdentifiers())
        val caller = caller(uid = 10001, packageName = "io.example.client")
        val token = successValue(ledger.register(caller))

        successValue(ledger.registerSession(token, caller, "session-1", SessionId("internal-session-1")))
        assertFailure(
            LedgerFailure.DUPLICATE_EXTERNAL_SESSION_ID,
            ledger.registerSession(token, caller, "session-1", SessionId("internal-session-2")),
        )
        successValue(ledger.allocateRequest(token, caller, "request-1"))
        assertFailure(LedgerFailure.DUPLICATE_EXTERNAL_REQUEST_ID, ledger.allocateRequest(token, caller, "request-1"))
    }

    private fun caller(uid: Int, packageName: String) = AuthorizedCaller(
        uid = uid,
        packageName = packageName,
        applicationId = ApplicationId("app-$uid"),
        allowedUseCases = setOf(UseCaseId("summarize")),
    )

    private fun <T> successValue(result: LedgerResult<T>): T {
        assertTrue(result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private fun assertFailure(expected: LedgerFailure, result: LedgerResult<*>) {
        assertTrue(result is LedgerResult.Failure)
        assertEquals(expected, (result as LedgerResult.Failure).reason)
    }

    private class FakeIdentifiers : HostIdentifierFactory {
        private var tokenCounter = 0
        private var requestCounter = 0

        override fun newClientToken(): HostClientToken {
            tokenCounter += 1
            return HostClientToken("token-$tokenCounter")
        }

        override fun newRequestId(): RequestId {
            requestCounter += 1
            return RequestId("request-$requestCounter")
        }
    }
}
