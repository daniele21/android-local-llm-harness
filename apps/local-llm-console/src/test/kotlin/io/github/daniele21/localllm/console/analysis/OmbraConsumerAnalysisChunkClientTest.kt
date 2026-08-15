package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerInferenceMetrics
import io.github.daniele21.localllm.contracts.ConsumerInferenceResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerPreparedSelection
import io.github.daniele21.localllm.contracts.ConsumerPresetOption
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.ConsumerStopReason
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class OmbraConsumerAnalysisChunkClientTest {
    @Test
    fun `prepare accepts only host-owned OMBRA defaults and returns advertised limits`() {
        val fake = FakeConsumerClient()
        val client = adapter(fake)
        var result: Result<ConsumerLimits>? = null

        client.prepare(OmbraOperationId(1)) { result = it }

        assertEquals(fake.capabilities.limits, requireNotNull(result).getOrThrow())
        assertEquals(UseCaseId("document-pii-detection"), fake.lastPrepareRequest?.useCaseId)
        assertEquals(null, fake.lastPrepareRequest?.selection?.preset)
        assertEquals(null, fake.lastPrepareRequest?.selection?.outputConstraint)
        assertEquals(null, fake.lastPrepareRequest?.selection?.sessionKind)
    }

    @Test
    fun `advertised preset alternatives fail closed before prepare`() {
        val alternative = InferencePresetRef(InferencePresetId("alternative"), 1)
        val fake =
            FakeConsumerClient(
                capabilities =
                validCapabilities().copy(
                    presets =
                    listOf(
                        ConsumerPresetOption(TEST_DEFAULT_PRESET, isDefault = true),
                        ConsumerPresetOption(alternative, isDefault = false),
                    ),
                ),
            )
        val client = adapter(fake)
        var result: Result<ConsumerLimits>? = null

        client.prepare(OmbraOperationId(7)) { result = it }

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE, failure.code)
        assertNull(fake.lastPrepareRequest)
    }

    @Test
    fun `substituted prepared preset fails closed before session creation`() {
        val fake =
            FakeConsumerClient(
                preparedPreset = InferencePresetRef(InferencePresetId("substituted"), 1),
            )
        val client = adapter(fake)
        var result: Result<ConsumerLimits>? = null

        client.prepare(OmbraOperationId(8)) { result = it }

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE, failure.code)
        assertFalse(fake.sessionCreated)
    }

    @Test
    fun `substituted execution preset fails closed and cancels generation`() {
        val fake =
            FakeConsumerClient(
                executionPreset = InferencePresetRef(InferencePresetId("substituted"), 1),
            )
        val client = adapter(fake)
        client.prepare(OmbraOperationId(9)) {}
        var result: Result<String>? = null

        client.generate(OmbraOperationId(9), validChunk()) { result = it }

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE, failure.code)
        assertTrue(fake.generationCancelled)
    }

    @Test
    fun `generation uses fixed JSON schema constraint and returns only final answer`() {
        val fake = FakeConsumerClient()
        val client = adapter(fake)
        client.prepare(OmbraOperationId(2)) {}
        var result: Result<String>? = null

        client.generate(
            operationId = OmbraOperationId(2),
            request =
            OmbraStructuredChunkRequest(
                ordinal = 0,
                instruction = "instruction",
                dataPayload = "{\"segments\":[]}",
                outputJsonSchema = "{\"type\":\"object\"}",
            ),
            onResult = { result = it },
        )

        val request = requireNotNull(fake.lastGenerationRequest)
        assertTrue(request.input is ConsumerGenerationInput.Text)
        assertEquals("instruction\n\nDATA:\n{\"segments\":[]}", (request.input as ConsumerGenerationInput.Text).value)
        assertEquals(
            "{\"type\":\"object\"}",
            (request.outputConstraint as ConsumerOutputConstraint.JsonSchema).schema,
        )
        assertEquals("{\"schemaVersion\":1,\"findings\":[]}", requireNotNull(result).getOrThrow())
    }

    @Test
    fun `unexpected reasoning capability fails closed before prepare`() {
        val fake = FakeConsumerClient(
            capabilities = validCapabilities().copy(reasoning = ConsumerReasoningCapability.SURFACED_OPTIONAL),
        )
        val client = adapter(fake)
        var result: Result<ConsumerLimits>? = null

        client.prepare(OmbraOperationId(3)) { result = it }

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE, failure.code)
        assertNull(fake.lastPrepareRequest)
    }

    @Test
    fun `transport runtime failure maps to disconnected when connection is down`() {
        val fake = FakeConsumerClient(generationFailure = ConsumerFailure(ConsumerErrorCode.RUNTIME_FAILURE, "transport"))
        var connected = true
        val client = adapter(fake) { connected }
        client.prepare(OmbraOperationId(4)) {}
        connected = false
        var result: Result<String>? = null

        client.generate(
            operationId = OmbraOperationId(4),
            request = validChunk(),
            onResult = { result = it },
        )

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.DISCONNECTED, failure.code)
    }

    @Test
    fun `cancellation acknowledgement follows terminal cancellation event`() {
        val fake = FakeConsumerClient(deferGeneration = true)
        val client = adapter(fake)
        client.prepare(OmbraOperationId(5)) {}
        var result: Result<String>? = null
        var cancelled = false

        client.generate(OmbraOperationId(5), validChunk()) { result = it }
        client.cancel(OmbraOperationId(5)) { cancelled = true }

        assertFalse(cancelled)
        assertNull(result)
        fake.completeCancellation()
        assertTrue(cancelled)
        assertNull(result)
    }

    @Test
    fun `reasoning delta is rejected and remote generation is cancelled`() {
        val fake = FakeConsumerClient(sendReasoningDelta = true)
        val client = adapter(fake)
        client.prepare(OmbraOperationId(6)) {}
        var result: Result<String>? = null

        client.generate(OmbraOperationId(6), validChunk()) { result = it }

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisChunkException
        assertEquals(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE, failure.code)
        assertTrue(fake.generationCancelled)
    }

    private fun adapter(fake: FakeConsumerClient, connected: () -> Boolean = { true }): OmbraConsumerAnalysisChunkClient =
        OmbraConsumerAnalysisChunkClient(
            client = fake,
            lifecycleExecutor = Executor { command -> command.run() },
            transportConnected = connected,
        )

    private fun validChunk(): OmbraStructuredChunkRequest = OmbraStructuredChunkRequest(
        ordinal = 0,
        instruction = "instruction",
        dataPayload = "{\"segments\":[]}",
        outputJsonSchema = "{\"type\":\"object\"}",
    )

    private class FakeConsumerClient(
        val capabilities: UseCaseCapabilities = validCapabilities(),
        private val generationFailure: ConsumerFailure? = null,
        private val deferGeneration: Boolean = false,
        private val sendReasoningDelta: Boolean = false,
        private val preparedPreset: InferencePresetRef =
            capabilities.defaultPreset ?: error("Default preset required"),
        private val executionPreset: InferencePresetRef = preparedPreset,
    ) : ConsumerLocalLlmClient {
        var lastPrepareRequest: ConsumerPrepareRequest? = null
        var lastGenerationRequest: ConsumerGenerationRequest? = null
        var generationCancelled: Boolean = false
        var sessionCreated: Boolean = false
        private var deferredListener: ConsumerGenerationListener? = null
        private var deferredRequestId: RequestId? = null

        override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = ConsumerCapabilityResult.Available(capabilities)

        override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult {
            lastPrepareRequest = request
            return ConsumerPrepareResult.Prepared(
                ConsumerPreparedSelection(
                    preparedId = ConsumerPreparedId("prepared-1"),
                    useCaseId = request.useCaseId,
                    capabilityRevision = capabilities.capabilityRevision,
                    preset = preparedPreset,
                    reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
                    outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
                    sessionKind = SessionKind.STATELESS,
                ),
            )
        }

        override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult {
            sessionCreated = true
            return ConsumerSessionResult.Created(SessionId("session-1"))
        }

        override fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult {
            lastGenerationRequest = request
            val handle = FakeHandle(request.requestId) {
                generationCancelled = true
            }
            if (deferGeneration) {
                deferredListener = listener
                deferredRequestId = request.requestId
            } else if (sendReasoningDelta) {
                listener.onEvent(
                    ConsumerGenerationEvent.ContentDelta(
                        requestId = request.requestId,
                        text = "reasoning",
                        contentType = ConsumerContentType.REASONING,
                    ),
                )
            } else if (generationFailure != null) {
                listener.onEvent(ConsumerGenerationEvent.Failed(request.requestId, generationFailure))
            } else {
                listener.onEvent(ConsumerGenerationEvent.Prepared(request.requestId, executionIdentity(executionPreset)))
                listener.onEvent(
                    ConsumerGenerationEvent.Completed(
                        request.requestId,
                        ConsumerInferenceResult(
                            answer = "{\"schemaVersion\":1,\"findings\":[]}",
                            surfacedReasoning = null,
                            metrics = metrics(),
                            execution = executionIdentity(executionPreset),
                        ),
                    ),
                )
            }
            return ConsumerGenerationStartResult.Accepted(handle)
        }

        override fun closeSession(sessionId: SessionId) = Unit

        fun completeCancellation() {
            val listener = requireNotNull(deferredListener)
            val requestId = requireNotNull(deferredRequestId)
            listener.onEvent(
                ConsumerGenerationEvent.Failed(
                    requestId,
                    ConsumerFailure(ConsumerErrorCode.CANCELLED, "cancelled"),
                ),
            )
        }
    }

    private class FakeHandle(override val requestId: RequestId, private val onCancel: () -> Unit) : ConsumerGenerationHandle {
        override fun cancel() = onCancel()
    }

    companion object {
        private val TEST_DEFAULT_PRESET = InferencePresetRef(InferencePresetId("qwen35-json"), 1)

        private fun validCapabilities(): UseCaseCapabilities = UseCaseCapabilities(
            useCaseId = UseCaseId("document-pii-detection"),
            readiness = UseCaseReadiness.READY,
            presets = listOf(ConsumerPresetOption(TEST_DEFAULT_PRESET, isDefault = true)),
            defaultPreset = TEST_DEFAULT_PRESET,
            reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
            outputConstraints = setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
            defaultOutputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            sessionKinds = setOf(SessionKind.STATELESS),
            defaultSessionKind = SessionKind.STATELESS,
            limits = ConsumerLimits(8_192, 1, 8_192),
            capabilityRevision = "ombra-test-r1",
        )

        private fun executionIdentity(preset: InferencePresetRef): ConsumerExecutionIdentity = ConsumerExecutionIdentity(
            useCaseId = UseCaseId("document-pii-detection"),
            capabilityRevision = "ombra-test-r1",
            preset = preset,
            reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
            outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
        )

        private fun metrics(): ConsumerInferenceMetrics = ConsumerInferenceMetrics(
            outputTokens = 1,
            timeToFirstTokenMs = 1,
            totalMs = 1,
            decodeTokensPerSecond = 1.0,
            inputTokens = 1,
            reasoningTokens = 0,
            answerTokens = 1,
            queueMs = 0,
            stopReason = ConsumerStopReason.GRAMMAR_COMPLETE,
        )
    }
}
