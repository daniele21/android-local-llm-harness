package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SharedRuntimeResilienceAcceptanceTest {
    @Test
    fun closingOneClientCannotAffectAnotherClientWithSameExternalIds() {
        val ledger = ClientConnectionLedger(identifiers = DeterministicIdentifiers())
        val callerA = caller(10_001, "io.example.client.a")
        val callerB = caller(10_002, "io.example.client.b")
        val tokenA = success(ledger.register(callerA))
        val tokenB = success(ledger.register(callerB))

        val requestA = success(ledger.allocateRequest(tokenA, callerA, EXTERNAL_REQUEST_ID))
        val requestB = success(ledger.allocateRequest(tokenB, callerB, EXTERNAL_REQUEST_ID))
        val sessionA = SessionId("internal-session-a")
        val sessionB = SessionId("internal-session-b")
        success(ledger.registerSession(tokenA, callerA, EXTERNAL_SESSION_ID, sessionA))
        success(ledger.registerSession(tokenB, callerB, EXTERNAL_SESSION_ID, sessionB))

        assertNotEquals(requestA, requestB)
        val closingA = success(ledger.beginClose(tokenA, callerA))
        assertEquals(listOf(requestA), closingA.requestIds)
        assertEquals(listOf(sessionA), closingA.sessionIds)
        success(ledger.finishClose(tokenA, callerA))

        assertEquals(requestB, success(ledger.requestId(tokenB, callerB, EXTERNAL_REQUEST_ID)))
        assertEquals(sessionB, success(ledger.sessionId(tokenB, callerB, EXTERNAL_SESSION_ID)))
        assertFailure(LedgerFailure.CLIENT_TOKEN_INVALID, ledger.requestId(tokenA, callerA, EXTERNAL_REQUEST_ID))
    }

    @Test
    fun boundedCallbackDispatcherRejectsOverflowInsteadOfGrowingWithoutBound() {
        val dispatcher = BoundedSerialHostCallbackDispatcher(queueCapacity = 1)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)

        try {
            assertTrue(
                dispatcher.dispatch {
                    firstStarted.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                },
            )
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            assertTrue(dispatcher.dispatch { secondRan.set(true) })
            assertFalse("A full callback queue must reject additional work", dispatcher.dispatch {})

            releaseFirst.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!secondRan.get() && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertTrue("Queued callback should run after pressure is released", secondRan.get())
        } finally {
            releaseFirst.countDown()
            dispatcher.close()
        }
    }

    @Test
    fun prepareFailureDoesNotProjectRuntimeDetailAcrossBinder() {
        val sentinel = "SR5_PRIVATE_SENTINEL_DO_NOT_PROJECT"
        val parcel = prepareResult(
            operationId = "prepare-1",
            result = PrepareResult(
                ready = false,
                modelDigest = null,
                detail = "internal runtime detail $sentinel",
            ),
        )

        assertEquals("Preparation failed", parcel.detail)
        assertFalse(parcel.detail.orEmpty().contains(sentinel))
        assertFalse(parcel.error?.safeMessage.orEmpty().contains(sentinel))
    }

    @Test
    fun successfulPrepareProjectsOnlyDigestAndStableStatus() {
        val digest = ModelDigest("a".repeat(64))
        val sentinel = "SR5_PRIVATE_SENTINEL_SUCCESS"
        val parcel = prepareResult(
            operationId = "prepare-2",
            result = PrepareResult(
                ready = true,
                modelDigest = digest,
                detail = "private host detail $sentinel",
            ),
        )

        assertTrue(parcel.ready)
        assertEquals(digest.sha256, parcel.modelDigestSha256)
        assertEquals("Model ready", parcel.detail)
        assertFalse(parcel.detail.orEmpty().contains(sentinel))
    }

    private fun caller(uid: Int, packageName: String) = AuthorizedCaller(
        uid = uid,
        packageName = packageName,
        applicationId = ApplicationId("app-$uid"),
        allowedUseCases = setOf(UseCaseId("console-inference-playground")),
    )

    private fun <T> success(result: LedgerResult<T>): T {
        assertTrue(result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private fun assertFailure(expected: LedgerFailure, result: LedgerResult<*>) {
        assertTrue(result is LedgerResult.Failure)
        assertEquals(expected, (result as LedgerResult.Failure).reason)
    }

    private class DeterministicIdentifiers : HostIdentifierFactory {
        private var client = 0
        private var request = 0

        override fun newClientToken(): HostClientToken {
            client += 1
            return HostClientToken("client-$client")
        }

        override fun newRequestId(): RequestId {
            request += 1
            return RequestId("internal-request-$request")
        }
    }

    private companion object {
        const val EXTERNAL_REQUEST_ID = "same-request"
        const val EXTERNAL_SESSION_ID = "same-session"
    }
}
