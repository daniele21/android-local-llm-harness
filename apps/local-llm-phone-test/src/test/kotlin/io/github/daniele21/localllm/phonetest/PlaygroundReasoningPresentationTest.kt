package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaygroundReasoningPresentationTest {
    @Test
    fun `structured reasoning never replaces final answer`() {
        val presentation = HarnessUiState(
            playground = PlaygroundState(
                phase = PlaygroundPhase.COMPLETED,
                output = "analysis</think>\n\nfinal answer",
                reasoningOutput = "analysis",
                answerOutput = "final answer",
                detail = "Generation completed",
                metrics = PlaygroundMetrics(
                    queueMs = 1,
                    modelLoadMs = null,
                    timeToFirstTokenMs = 3,
                    prefillMs = 2,
                    decodeMs = 10,
                    totalMs = 14,
                    inputTokens = 5,
                    outputTokens = 7,
                    decodeTokensPerSecond = 700.0,
                    modelLoadKind = "WARM",
                    timeToFirstAnswerMs = 8,
                    reasoningTokens = 4,
                    answerTokens = 3,
                ),
            ),
        ).toPlaygroundPresentation()

        assertEquals("analysis", presentation.reasoningText)
        assertEquals("final answer", presentation.answerText)
        assertEquals("final answer", presentation.responseText)
        assertEquals("8 ms", presentation.timeToFirstAnswer)
    }

    @Test
    fun `legacy output remains supported when structured channels are absent`() {
        val presentation = HarnessUiState(
            playground = PlaygroundState(
                phase = PlaygroundPhase.COMPLETED,
                output = "legacy answer",
            ),
        ).toPlaygroundPresentation()

        assertEquals("", presentation.reasoningText)
        assertEquals("legacy answer", presentation.answerText)
        assertEquals("legacy answer", presentation.responseText)
    }
}
