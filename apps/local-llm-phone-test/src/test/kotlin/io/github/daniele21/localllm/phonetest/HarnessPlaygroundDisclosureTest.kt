package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessPlaygroundDisclosureTest {
    @Test
    fun `default settings have no validation message`() {
        assertNull(playgroundSettingsValidationMessage(HarnessUiState()))
    }

    @Test
    fun `invalid temperature exposes the owning validation message`() {
        val state = HarnessUiState(playgroundTemperature = "3.7")

        assertEquals(
            "Temperature must be between 0.0 and 2.0",
            playgroundSettingsValidationMessage(state),
        )
    }

    @Test
    fun `invalid context exposes actionable inline validation`() {
        val state = HarnessUiState(playgroundContext = "not-a-number")

        assertEquals(
            "Context must be an integer or blank for Auto",
            playgroundSettingsValidationMessage(state),
        )
    }
}
