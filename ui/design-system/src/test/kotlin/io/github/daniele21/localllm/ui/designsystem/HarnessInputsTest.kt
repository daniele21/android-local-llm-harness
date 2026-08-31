package io.github.daniele21.localllm.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessInputsTest {
    @Test
    fun integerInputAcceptsDigitsAndBlankOnly() {
        assertEquals("", normalizeHarnessNumberInput("", HarnessNumberInputMode.INTEGER))
        assertEquals("4096", normalizeHarnessNumberInput("4096", HarnessNumberInputMode.INTEGER))
        assertNull(normalizeHarnessNumberInput("4k", HarnessNumberInputMode.INTEGER))
        assertNull(normalizeHarnessNumberInput("4.5", HarnessNumberInputMode.INTEGER))
    }

    @Test
    fun decimalInputNormalizesLocaleCommaAndRejectsMalformedValues() {
        assertEquals("0.95", normalizeHarnessNumberInput("0,95", HarnessNumberInputMode.DECIMAL))
        assertEquals("1.25", normalizeHarnessNumberInput("1.25", HarnessNumberInputMode.DECIMAL))
        assertNull(normalizeHarnessNumberInput("1.2.5", HarnessNumberInputMode.DECIMAL))
        assertNull(normalizeHarnessNumberInput("1x", HarnessNumberInputMode.DECIMAL))
    }
}
