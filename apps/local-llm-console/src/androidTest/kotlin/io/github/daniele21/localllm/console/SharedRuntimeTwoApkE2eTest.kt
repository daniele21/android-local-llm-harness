package io.github.daniele21.localllm.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.client.BinderLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SharedRuntimeTwoApkE2eTest {
    private lateinit var client: BinderLocalLlmClient

    @Before
    fun connectToProofHost() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = BinderLocalLlmClient.create(
            context = context,
            hostConfig = SharedRuntimeHostConfig.create(
                BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
            ),
            applicationId = APPLICATION_ID,
            clientBuildId = "sr4-e2e-${BuildConfig.VERSION_NAME}",
        )
        client.connect()
        assertTrue(
            "Console did not connect to the separately installed proof host; inspect the typed connection state locally.",
            awaitConnection(SharedRuntimeConnectionState.CONNECTED),
        )
    }

    @After
    fun closeClient() {
        if (::client.isInitialized) client.close()
    }

    @Test
    fun prepareSessionStreamCompleteAndCloseCrossRealBinderBoundary() {
        val prepare = client.prepare(APPLICATION_ID, USE_CASE_ID)
        assertTrue(
            "Proof host is reachable but its curated model is not ready. Install and select a supported Qwen3.5 model in the host first.",
            prepare.ready,
        )
        assertNotNull(prepare.modelDigest)

        val sessionId = client.createSession(APPLICATION_ID, USE_CASE_ID)
        val terminal = CountDownLatch(1)
        val sawDelta = AtomicBoolean(false)
        val terminalEvent = AtomicReference<GenerationEvent>()
        val requestId = RequestId("sr4-complete-${UUID.randomUUID()}")

        client.generate(
            GenerationRequest(
                requestId = requestId,
                sessionId = sessionId,
                applicationId = APPLICATION_ID,
                useCaseId = USE_CASE_ID,
                input = "Return a short acknowledgement for a local Binder integration test.",
                overrides = GenerationOverrides(maxOutputTokens = 64, temperature = 0f, seed = 42L),
            ),
        ) { event ->
            when (event) {
                is GenerationEvent.TextDelta -> sawDelta.set(true)

                is GenerationEvent.Completed,
                is GenerationEvent.Failed,
                -> {
                    terminalEvent.set(event)
                    terminal.countDown()
                }

                else -> Unit
            }
        }

        assertTrue("Shared-runtime generation did not reach a terminal event", terminal.await(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue("Expected a successful cross-process completion", terminalEvent.get() is GenerationEvent.Completed)
        assertTrue("Expected at least one streamed delta across Binder", sawDelta.get())

        client.closeSession(sessionId)
    }

    @Test
    fun cancelDuringGenerationCrossesBinderAndLeavesSessionClosable() {
        val prepare = client.prepare(APPLICATION_ID, USE_CASE_ID)
        assertTrue(
            "Proof host is reachable but its curated model is not ready. Install and select a supported Qwen3.5 model in the host first.",
            prepare.ready,
        )

        val sessionId = client.createSession(APPLICATION_ID, USE_CASE_ID)
        val started = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        val requestId = RequestId("sr4-cancel-${UUID.randomUUID()}")
        val handle = client.generate(
            GenerationRequest(
                requestId = requestId,
                sessionId = sessionId,
                applicationId = APPLICATION_ID,
                useCaseId = USE_CASE_ID,
                input = "Generate a long numbered sequence, one item per line, without stopping early.",
                overrides = GenerationOverrides(maxOutputTokens = 512, temperature = 0f, seed = 43L),
            ),
        ) { event ->
            when (event) {
                is GenerationEvent.Started,
                is GenerationEvent.Prepared,
                is GenerationEvent.TextDelta,
                -> started.countDown()

                is GenerationEvent.Completed,
                is GenerationEvent.Failed,
                -> {
                    terminalEvent.set(event)
                    terminal.countDown()
                }

                else -> Unit
            }
        }

        assertTrue("Generation never started before cancellation", started.await(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        handle.cancel()
        assertTrue("Cancelled generation did not terminate", terminal.await(CANCEL_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val event = terminalEvent.get()
        assertTrue("Expected a typed failed terminal event after cancellation", event is GenerationEvent.Failed)
        val error = (event as GenerationEvent.Failed).error
        assertTrue("Expected LocalLlmError.Cancelled", error is LocalLlmError.Cancelled)
        assertEquals(requestId, event.requestId)

        client.closeSession(sessionId)
    }

    private fun awaitConnection(expected: SharedRuntimeConnectionState): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECTION_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (client.connectionSnapshot.state == expected) return true
            val current = client.connectionSnapshot.state
            if (current in TERMINAL_CONNECTION_FAILURES) return false
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return client.connectionSnapshot.state == expected
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("local-llm-console")
        val USE_CASE_ID = UseCaseId("console-inference-playground")
        val TERMINAL_CONNECTION_FAILURES = setOf(
            SharedRuntimeConnectionState.HOST_NOT_INSTALLED,
            SharedRuntimeConnectionState.PERMISSION_DENIED,
            SharedRuntimeConnectionState.INCOMPATIBLE,
            SharedRuntimeConnectionState.CONNECTION_LOST,
            SharedRuntimeConnectionState.CLOSED,
        )
        const val CONNECTION_TIMEOUT_SECONDS = 15L
        const val GENERATION_TIMEOUT_SECONDS = 120L
        const val CANCEL_TIMEOUT_SECONDS = 30L
        const val POLL_INTERVAL_MS = 50L
    }
}
