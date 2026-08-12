package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessSharedRuntimeClientTest {
    private val applicationId = ApplicationId("external-app")
    private val useCaseId = UseCaseId("external-use-case")

    @Test
    fun `prepare without host selection stays inert`() {
        var runtimeFactoryCalls = 0
        val client = HarnessSharedRuntimeClient(
            activeClient = { null },
            prepareClient = {
                runtimeFactoryCalls += 1
                null
            },
        )

        val result = client.prepare(applicationId, useCaseId)

        assertFalse(result.ready)
        assertNull(result.modelDigest)
        assertEquals("Host model is not selected", result.detail)
        assertEquals(1, runtimeFactoryCalls)
        assertEquals(RuntimeState.IDLE, client.runtimeSnapshot().state)
    }

    @Test
    fun `prepare delegates only to supplied host runtime client`() {
        val runtime = FakeClient()
        val client = HarnessSharedRuntimeClient(
            activeClient = { runtime },
            prepareClient = { runtime },
        )

        val result = client.prepare(applicationId, useCaseId)

        assertEquals(1, runtime.prepareCalls)
        assertEquals(applicationId, runtime.preparedApplicationId)
        assertEquals(useCaseId, runtime.preparedUseCaseId)
        assertEquals(ModelDigest("ab".repeat(32)), result.modelDigest)
    }

    private class FakeClient : LocalLlmClient {
        var prepareCalls = 0
        var preparedApplicationId: ApplicationId? = null
        var preparedUseCaseId: UseCaseId? = null

        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.IDLE, null, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
            prepareCalls += 1
            preparedApplicationId = applicationId
            preparedUseCaseId = useCaseId
            return PrepareResult(true, ModelDigest("ab".repeat(32)), "ready")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId = error("not used")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId =
            error("not used")

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle = error("not used")

        override fun closeSession(sessionId: SessionId) = Unit
    }
}
