package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.MAX_NATIVE_SEED
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolHardeningTest {
    @Test
    fun `fixed seed outside native range fails at wire boundary`() {
        val failure =
            assertThrows(WireProtocolException::class.java) {
                validateGenerationOverrides(
                    emptyOverrides().copy(
                        seedPolicyTag = WireTags.SEED_FIXED,
                        seedValue = MAX_NATIVE_SEED + 1,
                    ),
                )
            }

        assertEquals(WireErrorCodes.INVALID_WIRE_REQUEST, failure.wireCode)
    }

    @Test
    fun `future metric enum values fall back to explicit unknown`() {
        val metrics =
            GenerationMetricsParcel(
                queueMs = 1,
                modelLoadMs = null,
                timeToFirstTokenMs = null,
                totalMs = 2,
                inputTokens = null,
                outputTokens = null,
                decodeTokensPerSecond = null,
                prefillMs = null,
                decodeMs = null,
                modelLoadKind = "FUTURE_LOAD_KIND",
                stopReason = "FUTURE_STOP_REASON",
                promptPlanningMs = null,
                contextCreationMs = null,
                timeToFirstAnswerMs = null,
                reasoningTokens = null,
                answerTokens = null,
            ).toCore()

        assertEquals(ModelLoadKind.UNKNOWN, metrics.modelLoadKind)
        assertEquals(StopReason.UNKNOWN, metrics.stopReason)
    }

    private fun emptyOverrides() = GenerationOverridesParcel(
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
    )
}
