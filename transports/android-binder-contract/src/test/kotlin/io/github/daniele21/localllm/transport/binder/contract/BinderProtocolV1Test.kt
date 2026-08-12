package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ConversationMessage
import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.OutputConstraint
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderProtocolV1Test {
    @Test
    fun `compatible minor ranges negotiate highest common minor`() {
        val result =
            negotiateProtocol(
                host =
                ProtocolInfoParcel(
                    protocolMajor = 1,
                    protocolMinor = 3,
                    minSupportedMinor = 1,
                    supportedFeatures = listOf(BinderProtocolV1.FEATURE_MESSAGE_INPUT, "future-host-feature"),
                    hostBuildId = "host-test",
                ),
                client =
                ClientHelloParcel(
                    protocolMajor = 1,
                    protocolMinor = 2,
                    minSupportedMinor = 0,
                    requiredFeatures = listOf(BinderProtocolV1.FEATURE_MESSAGE_INPUT),
                    clientBuildId = "client-test",
                ),
            )

        assertEquals(2, result.minor)
        assertEquals(setOf(BinderProtocolV1.FEATURE_MESSAGE_INPUT), result.enabledFeatures)
    }

    @Test
    fun `major mismatch fails closed`() {
        val failure =
            assertThrows(WireProtocolException::class.java) {
                negotiateProtocol(
                    ProtocolInfoParcel(1, 0, 0, emptyList(), "host"),
                    ClientHelloParcel(2, 0, 0, emptyList(), "client"),
                )
            }

        assertEquals(WireErrorCodes.PROTOCOL_INCOMPATIBLE, failure.wireCode)
    }

    @Test
    fun `missing required feature fails before runtime mapping`() {
        val failure =
            assertThrows(WireProtocolException::class.java) {
                negotiateProtocol(
                    ProtocolInfoParcel(1, 0, 0, emptyList(), "host"),
                    ClientHelloParcel(
                        1,
                        0,
                        0,
                        listOf(BinderProtocolV1.FEATURE_JSON_SCHEMA_CONSTRAINT),
                        "client",
                    ),
                )
            }

        assertEquals(WireErrorCodes.FEATURE_UNAVAILABLE, failure.wireCode)
    }

    @Test
    fun `generation request maps without trusting application identity`() {
        val request =
            GenerationRequest(
                requestId = RequestId("external-request"),
                sessionId = SessionId("external-session"),
                applicationId = ApplicationId("untrusted-client-application"),
                useCaseId = UseCaseId("summarize"),
                input =
                GenerationInput.Messages(
                    listOf(
                        ConversationMessage(ConversationRole.USER, "Summarize this fixture"),
                        ConversationMessage(ConversationRole.ASSISTANT, "Previous bounded answer"),
                    ),
                ),
                overrides =
                GenerationOverrides(
                    maxOutputTokens = 128,
                    temperature = 0.2f,
                    topP = 0.9f,
                    topK = 20,
                    seedPolicy = SeedPolicy.Fixed(42),
                    repeatPenalty = 1.05f,
                    repeatLastN = 64,
                    thinkingMode = ThinkingMode.DISABLED,
                    minP = 0.05f,
                    presencePenalty = 0.1f,
                ),
                outputConstraint = OutputConstraint.JsonSchema("{\"type\":\"object\"}"),
            )

        val wire = request.toWire(ClientTokenParcel("opaque-token"))
        val mapped =
            wire.toCore(
                applicationId = ApplicationId("host-owned-application"),
                internalSessionId = SessionId("internal-session"),
                internalRequestId = RequestId("internal-request"),
            )

        assertEquals(ApplicationId("host-owned-application"), mapped.applicationId)
        assertEquals(RequestId("internal-request"), mapped.requestId)
        assertEquals(SessionId("internal-session"), mapped.sessionId)
        assertEquals(request.useCaseId, mapped.useCaseId)
        assertEquals(request.input, mapped.input)
        assertEquals(request.overrides, mapped.overrides)
        assertEquals(request.outputConstraint, mapped.outputConstraint)
        assertFalse(wire.toString().contains("untrusted-client-application"))
    }

    @Test
    fun `unknown wire input tag fails closed`() {
        val request =
            minimalWireRequest().copy(
                input = GenerationInputParcel(typeTag = "FUTURE_INPUT", text = "fixture", messages = emptyList()),
            )

        val failure = assertThrows(WireProtocolException::class.java) { validateGenerationRequest(request) }
        assertEquals(WireErrorCodes.INVALID_WIRE_REQUEST, failure.wireCode)
    }

    @Test
    fun `oversized request fails with payload code`() {
        val request = minimalWireRequest("x".repeat(32_768))

        val failure = assertThrows(WireProtocolException::class.java) { validateGenerationRequest(request) }
        assertEquals(WireErrorCodes.PAYLOAD_TOO_LARGE, failure.wireCode)
    }

    @Test
    fun `delta chunking never splits surrogate pairs`() {
        val prefix = "a".repeat(BinderProtocolV1.MAX_DELTA_CHARACTERS - 1)
        val text = prefix + "😀" + "tail"
        val chunks = chunkDelta(text)

        assertEquals(text, chunks.joinToString(separator = ""))
        assertTrue(chunks.all { it.length <= BinderProtocolV1.MAX_DELTA_CHARACTERS })
        chunks.dropLast(1).forEach { chunk ->
            assertFalse(Character.isHighSurrogate(chunk.last()))
        }
    }

    @Test
    fun `reconstructor enforces order and assembles terminal answer`() {
        val reconstructor = GenerationEventReconstructor("external", RequestId("internal"))
        reconstructor.accept(
            GenerationEventParcel(
                externalRequestId = "external",
                sequence = 0,
                eventTag = WireTags.EVENT_TEXT_DELTA,
                deltaText = "reason ",
                generatedTokens = 1,
                contentTypeTag = WireTags.CONTENT_REASONING,
            ),
        )
        reconstructor.accept(
            GenerationEventParcel(
                externalRequestId = "external",
                sequence = 1,
                eventTag = WireTags.EVENT_TEXT_DELTA,
                deltaText = "answer",
                generatedTokens = 2,
                contentTypeTag = WireTags.CONTENT_ANSWER,
            ),
        )
        val completed =
            reconstructor.accept(
                GenerationEventParcel(
                    externalRequestId = "external",
                    sequence = 2,
                    eventTag = WireTags.EVENT_COMPLETED,
                    metrics = metrics().toWireFixture(),
                ),
            ) as GenerationEvent.Completed

        assertEquals("reason ", completed.reasoningOutput)
        assertEquals("answer", completed.answerOutput)
        assertEquals("answer", completed.output)
        assertThrows(IllegalStateException::class.java) {
            reconstructor.accept(
                GenerationEventParcel(
                    externalRequestId = "external",
                    sequence = 3,
                    eventTag = WireTags.EVENT_FAILED,
                    error = WireErrorParcel(WireErrorCodes.TRANSPORT_FAILURE, "failure", false),
                ),
            )
        }
    }

    @Test
    fun `sequence gap becomes protocol failure`() {
        val reconstructor = GenerationEventReconstructor("external", RequestId("internal"))
        val failure =
            assertThrows(WireProtocolException::class.java) {
                reconstructor.accept(
                    GenerationEventParcel(
                        externalRequestId = "external",
                        sequence = 1,
                        eventTag = WireTags.EVENT_QUEUED,
                        queuePosition = 0,
                    ),
                )
            }

        assertEquals(WireErrorCodes.TRANSPORT_FAILURE, failure.wireCode)
    }

    @Test
    fun `native error is redacted before crossing binder`() {
        val wire = LocalLlmError.NativeRuntime("/data/user/0/private/model.gguf llama backend exploded").toSafeWire()

        assertEquals(WireErrorCodes.RUNTIME_FAILURE, wire.code)
        assertFalse(wire.safeMessage.contains("/data/"))
        assertFalse(wire.safeMessage.contains("llama"))
    }

    @Test
    fun `prepared and metrics fields survive wire reconstruction`() {
        val metadata =
            EffectiveGenerationMetadata(
                preset = null,
                temperature = 0.1f,
                topP = 0.9f,
                topK = 20,
                repeatPenalty = 1.0f,
                repeatLastN = 64,
                requestedSeedPolicy = SeedPolicyType.FIXED,
                effectiveSeed = 7,
                maxOutputTokens = 64,
                contextSize = 2_048,
                promptTokenCount = 12,
                chatTemplateId = "qwen-test",
                chatTemplateSource = ChatTemplateSource.GGUF,
                systemPromptVersion = "fixture-v1",
                thinkingMode = ThinkingMode.DISABLED,
                minP = 0.05f,
                presencePenalty = 0.0f,
            )
        val core = GenerationEvent.Prepared(RequestId("internal"), ModelDigest("abc"), metadata)
        val wire = core.toWire("external", 0)
        val reconstructed =
            GenerationEventReconstructor("external", RequestId("internal-2")).accept(wire) as GenerationEvent.Prepared

        assertEquals(metadata, reconstructed.configuration)
        assertEquals(ModelDigest("abc"), reconstructed.modelDigest)
    }

    private fun minimalWireRequest(text: String = "fixture") = GenerationRequestParcel(
        clientToken = ClientTokenParcel("token"),
        externalRequestId = "request",
        externalSessionId = "session",
        useCaseId = "use-case",
        input = GenerationInputParcel(WireTags.INPUT_TEXT, text, emptyList()),
        overrides =
        GenerationOverridesParcel(
            presetId = null,
            presetVersion = null,
            maxOutputTokens = null,
            temperature = null,
            topP = null,
            topK = null,
            seedPolicyTag = null,
            seedValue = null,
            repeatPenalty = null,
            repeatLastN = null,
            thinkingModeTag = null,
            minP = null,
            presencePenalty = null,
        ),
        outputConstraint = OutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null),
    )

    private fun metrics() = GenerationMetrics(
        queueMs = 1,
        modelLoadMs = 2,
        timeToFirstTokenMs = 3,
        totalMs = 4,
        inputTokens = 5,
        outputTokens = 6,
        decodeTokensPerSecond = 7.0,
        prefillMs = 8,
        decodeMs = 9,
        modelLoadKind = ModelLoadKind.WARM,
        stopReason = StopReason.END_OF_GENERATION,
        promptPlanningMs = 10,
        contextCreationMs = 11,
        timeToFirstAnswerMs = 12,
        reasoningTokens = 2,
        answerTokens = 4,
    )

    private fun GenerationMetrics.toWireFixture() = GenerationMetricsParcel(
        queueMs = queueMs,
        modelLoadMs = modelLoadMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        prefillMs = prefillMs,
        decodeMs = decodeMs,
        modelLoadKind = "WARM",
        stopReason = "END_OF_GENERATION",
        promptPlanningMs = promptPlanningMs,
        contextCreationMs = contextCreationMs,
        timeToFirstAnswerMs = timeToFirstAnswerMs,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
    )
}
