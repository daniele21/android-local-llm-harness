package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.RunStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessDiagnosticsExportTest {
    @Test
    fun `run export contains diagnostic timings and configuration without content`() {
        val run = GenerationRunRecord(
            requestId = RequestId("request-1234567890"),
            applicationId = ApplicationId("play-internal-phone-test"),
            useCaseId = UseCaseId("manual-inference-playground"),
            modelDigest = ModelDigest("a".repeat(64)),
            startedAtEpochMs = 1_000,
            completedAtEpochMs = 1_276_779,
            status = RunStatus.COMPLETED,
            queueMs = 8,
            modelLoadMs = 1_200,
            timeToFirstTokenMs = 18_960,
            totalMs = 1_275_779,
            inputTokens = 42,
            outputTokens = 1_100,
            decodeTokensPerSecond = 0.88,
            errorCode = null,
            prefillMs = 18_300,
            decodeMs = 1_250_000,
            modelLoadKind = ModelLoadKind.COLD,
            presetId = InferencePresetId("qwen35-balanced"),
            presetVersion = 2,
            temperature = 0.6f,
            topP = 0.9f,
            topK = 40,
            minP = 0.05f,
            presencePenalty = 0f,
            thinkingMode = ThinkingMode.DISABLED,
            repeatPenalty = 1.05f,
            repeatLastN = 64,
            seedPolicy = SeedPolicyType.RANDOM,
            maxOutputTokens = 2_048,
            contextSize = 2_048,
            promptTokenCount = 42,
            stopReason = StopReason.MAX_OUTPUT_TOKENS,
            promptPlanningMs = 23,
            contextCreationMs = 311,
        )

        val exported = HarnessDiagnosticsExport.renderRun(0, run)

        assertTrue(exported.contains("ttftMs=18960"))
        assertTrue(exported.contains("totalMs=1275779"))
        assertTrue(exported.contains("decodeTokPerSec=0.880"))
        assertTrue(exported.contains("prefillMs=18300"))
        assertTrue(exported.contains("decodeMs=1250000"))
        assertTrue(exported.contains("modelLoadMs=1200"))
        assertTrue(exported.contains("contextCreationMs=311"))
        assertTrue(exported.contains("contextSize=2048"))
        assertTrue(exported.contains("thinking=DISABLED"))
        assertTrue(exported.contains("stopReason=MAX_OUTPUT_TOKENS"))
        assertFalse(exported.contains("prompt="))
        assertFalse(exported.contains("output="))
        assertFalse(exported.contains("/data/"))
        assertFalse(exported.contains("http://"))
        assertFalse(exported.contains("https://"))
    }
}
