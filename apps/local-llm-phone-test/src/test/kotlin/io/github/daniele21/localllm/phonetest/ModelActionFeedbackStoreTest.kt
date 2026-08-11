package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelActionFeedbackStoreTest {
    @Test
    fun `failed messages are classified as errors`() {
        assertEquals(
            ModelActionFeedbackTone.ERROR,
            ModelActionFeedbackStore.classify("Failed: installed model is no longer available"),
        )
    }

    @Test
    fun `successful playground selection is classified as success`() {
        assertEquals(
            ModelActionFeedbackTone.SUCCESS,
            ModelActionFeedbackStore.classify("model.gguf selected for Playground; runtime loads on first inference"),
        )
    }

    @Test
    fun `in progress messages remain informational`() {
        assertEquals(
            ModelActionFeedbackTone.INFO,
            ModelActionFeedbackStore.classify("Verifying installed model before Playground selection"),
        )
    }
}
