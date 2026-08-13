package io.github.daniele21.localllm.consumerfixture

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
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
class SharedRuntimeReleaseEvidenceTest {
    private lateinit var client: BinderLocalLlmClient

    @Before
    fun connectToReleaseHost() {
        client = createClient("sr6-positive-${BuildConfig.VERSION_NAME}")
        client.connect()
        assertTrue(
            "Packaged release client did not connect to the same-signer host.",
            awaitConnection(client, SharedRuntimeConnectionState.CONNECTED),
        )
    }

    @After
    fun closeClient() {
        if (::client.isInitialized) client.close()
    }

    @Test
    fun packagedReleaseClientCompletesStreamsCancelsAndCloses() {
        val modelDigest = prepareAndRecordRuntimeIdentity()
        runSuccessfulGeneration(modelDigest)
        runCancellation()
    }

    @Test
    fun hostProcessDeathProducesDisconnectAndCleanReconnect() {
        runShell("am force-stop ${BuildConfig.SHARED_RUNTIME_HOST_PACKAGE}")
        assertTrue(
            "Client did not converge to CONNECTION_LOST after host process termination.",
            awaitConnection(client, SharedRuntimeConnectionState.CONNECTION_LOST),
        )

        runShell(
            "am start -W -n " +
                "${BuildConfig.SHARED_RUNTIME_HOST_PACKAGE}/io.github.daniele21.localllm.phonetest.MainActivity",
        )
        client.connect()
        assertTrue(
            "Client did not reconnect after the host process was restarted.",
            awaitConnection(client, SharedRuntimeConnectionState.CONNECTED),
        )
        println("SR6_SHARED_RUNTIME processDeath disconnect=CONNECTION_LOST reconnect=CONNECTED")
    }

    private fun prepareAndRecordRuntimeIdentity(): ModelDigest {
        val prepare = client.prepare(APPLICATION_ID, USE_CASE_ID)
        assertTrue(
            "Release-like host is reachable but no curated model is ready. Install and select a supported Qwen3.5 model in the host first.",
            prepare.ready,
        )
        assertNotNull(prepare.modelDigest)
        val modelDigest = requireNotNull(prepare.modelDigest)
        val connection = client.connectionSnapshot
        println(
            "SR6_SHARED_RUNTIME identity " +
                "modelDigestSha256=${modelDigest.sha256} " +
                "negotiatedMinor=${connection.negotiatedMinor ?: -1} " +
                "enabledFeatures=${connection.enabledFeatures.sorted().joinToString(",")}",
        )
        return modelDigest
    }

    private fun runSuccessfulGeneration(expectedModelDigest: ModelDigest) {
        val sessionId = client.createSession(APPLICATION_ID, USE_CASE_ID)
        val terminalLatch = CountDownLatch(1)
        val sawDelta = AtomicBoolean(false)
        val preparedEvent = AtomicReference<GenerationEvent.Prepared>()
        val terminalEvent = AtomicReference<GenerationEvent>()
        val requestId = RequestId("sr6-complete-${UUID.randomUUID()}")
        val clientStartedAtNanos = System.nanoTime()

        client.generate(
            GenerationRequest(
                requestId = requestId,
                sessionId = sessionId,
                applicationId = APPLICATION_ID,
                useCaseId = USE_CASE_ID,
                input = "Return a short acknowledgement for a local Binder release evidence test.",
                overrides = GenerationOverrides(maxOutputTokens = 64, temperature = 0f, seed = 61L),
            ),
        ) { event ->
            when (event) {
                is GenerationEvent.Prepared -> preparedEvent.compareAndSet(null, event)
                is GenerationEvent.TextDelta -> sawDelta.set(true)
                is GenerationEvent.Completed,
                is GenerationEvent.Failed,
                -> {
                    terminalEvent.set(event)
                    terminalLatch.countDown()
                }
                else -> Unit
            }
        }

        assertTrue("Release generation did not terminate", terminalLatch.await(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val clientObservedTotalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - clientStartedAtNanos)
        val terminal = terminalEvent.get()
        assertTrue("Expected a successful packaged-client completion", terminal is GenerationEvent.Completed)
        assertTrue("Expected at least one streamed Binder delta", sawDelta.get())
        val prepared = requireNotNull(preparedEvent.get()) { "Expected prepared configuration before terminal event" }
        assertEquals(expectedModelDigest, prepared.modelDigest)
        recordGenerationProfile(prepared)
        recordGenerationMetrics(terminal as GenerationEvent.Completed, clientObservedTotalMs)
        client.closeSession(sessionId)
    }

    private fun runCancellation() {
        val sessionId = client.createSession(APPLICATION_ID, USE_CASE_ID)
        val startedLatch = CountDownLatch(1)
        val terminalLatch = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        val requestId = RequestId("sr6-cancel-${UUID.randomUUID()}")
        val handle = client.generate(
            GenerationRequest(
                requestId = requestId,
                sessionId = sessionId,
                applicationId = APPLICATION_ID,
                useCaseId = USE_CASE_ID,
                input = "Generate a long numbered sequence, one item per line, without stopping early.",
                overrides = GenerationOverrides(maxOutputTokens = 512, temperature = 0f, seed = 62L),
            ),
        ) { event ->
            when (event) {
                is GenerationEvent.Started,
                is GenerationEvent.Prepared,
                is GenerationEvent.TextDelta,
                -> startedLatch.countDown()
                is GenerationEvent.Completed,
                is GenerationEvent.Failed,
                -> {
                    terminalEvent.set(event)
                    terminalLatch.countDown()
                }
                else -> Unit
            }
        }

        assertTrue("Release generation never became cancellable", startedLatch.await(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val cancelRequestedAt = System.nanoTime()
        handle.cancel()
        assertTrue("Release cancellation did not terminate", terminalLatch.await(CANCEL_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val cancelled = terminalEvent.get()
        assertTrue("Expected a typed cancelled terminal", cancelled is GenerationEvent.Failed)
        assertTrue("Expected LocalLlmError.Cancelled", (cancelled as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
        assertEquals(requestId, cancelled.requestId)
        val cancellationLatencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelRequestedAt)
        println("SR6_SHARED_RUNTIME cancellation latencyMs=$cancellationLatencyMs terminal=CANCELLED")
        client.closeSession(sessionId)
    }

    private fun recordGenerationProfile(prepared: GenerationEvent.Prepared) {
        val configuration = prepared.configuration
        println(
            "SR6_SHARED_RUNTIME generationProfile " +
                "presetId=${configuration.preset?.id?.value ?: "none"} " +
                "presetVersion=${configuration.preset?.version ?: -1} " +
                "contextSize=${configuration.contextSize} " +
                "maxOutputTokens=${configuration.maxOutputTokens} " +
                "thinkingMode=${configuration.thinkingMode.name} " +
                "temperature=${configuration.temperature} " +
                "topP=${configuration.topP} " +
                "topK=${configuration.topK} " +
                "minP=${configuration.minP} " +
                "presencePenalty=${configuration.presencePenalty} " +
                "repeatPenalty=${configuration.repeatPenalty} " +
                "repeatLastN=${configuration.repeatLastN}",
        )
    }

    private fun recordGenerationMetrics(completed: GenerationEvent.Completed, clientObservedTotalMs: Long) {
        val metrics = completed.metrics
        val transportEnvelopeMs = (clientObservedTotalMs - metrics.totalMs).coerceAtLeast(0L)
        println(
            "SR6_SHARED_RUNTIME generation " +
                "ttftMs=${metrics.timeToFirstTokenMs ?: -1} " +
                "coreTotalMs=${metrics.totalMs} " +
                "clientObservedTotalMs=$clientObservedTotalMs " +
                "transportEnvelopeMs=$transportEnvelopeMs " +
                "inputTokens=${metrics.inputTokens ?: -1} " +
                "outputTokens=${metrics.outputTokens ?: -1} " +
                "decodeTokensPerSecond=${metrics.decodeTokensPerSecond ?: -1.0} " +
                "stopReason=${metrics.stopReason.name}",
        )
    }

    private fun runShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val buffer = ByteArray(1024)
            while (input.read(buffer) >= 0) {
                // Drain command output without persisting it in the evidence stream.
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class SharedRuntimeInvalidSignerTest {
    private lateinit var client: BinderLocalLlmClient

    @After
    fun closeClient() {
        if (::client.isInitialized) client.close()
    }

    @Test
    fun independentlySignedClientIsDeniedBeforeRuntimeNegotiation() {
        client = createClient("sr6-invalid-signer-${BuildConfig.VERSION_NAME}")
        client.connect()
        assertTrue(
            "Independently signed client was not denied by the signature-protected service.",
            awaitConnection(client, SharedRuntimeConnectionState.PERMISSION_DENIED),
        )
        assertEquals(SharedRuntimeConnectionState.PERMISSION_DENIED, client.connectionSnapshot.state)
        println("SR6_SHARED_RUNTIME invalidSigner state=PERMISSION_DENIED")
    }
}

private fun createClient(buildId: String): BinderLocalLlmClient {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return BinderLocalLlmClient.create(
        context = context,
        hostConfig =
        SharedRuntimeHostConfig.create(
            BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
            BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
        ),
        applicationId = APPLICATION_ID,
        clientBuildId = buildId,
    )
}

private fun awaitConnection(client: BinderLocalLlmClient, expected: SharedRuntimeConnectionState): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECTION_TIMEOUT_SECONDS)
    while (System.nanoTime() < deadline) {
        if (client.connectionSnapshot.state == expected) return true
        val current = client.connectionSnapshot.state
        if (current in TERMINAL_CONNECTION_FAILURES && current != expected) return false
        Thread.sleep(POLL_INTERVAL_MS)
    }
    return client.connectionSnapshot.state == expected
}

private val APPLICATION_ID = ApplicationId("local-llm-console")
private val USE_CASE_ID = UseCaseId("console-inference-playground")
private val TERMINAL_CONNECTION_FAILURES =
    setOf(
        SharedRuntimeConnectionState.HOST_NOT_INSTALLED,
        SharedRuntimeConnectionState.PERMISSION_DENIED,
        SharedRuntimeConnectionState.INCOMPATIBLE,
        SharedRuntimeConnectionState.CONNECTION_LOST,
        SharedRuntimeConnectionState.CLOSED,
    )
private const val CONNECTION_TIMEOUT_SECONDS = 20L
private const val GENERATION_TIMEOUT_SECONDS = 120L
private const val CANCEL_TIMEOUT_SECONDS = 30L
private const val POLL_INTERVAL_MS = 50L
