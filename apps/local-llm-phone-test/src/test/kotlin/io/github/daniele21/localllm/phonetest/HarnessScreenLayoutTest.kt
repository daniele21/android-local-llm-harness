package io.github.daniele21.localllm.phonetest

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessScreenLayoutTest {
    @Test
    fun `screen padding grows with available width`() {
        assertEquals(16.dp, harnessScreenHorizontalPadding(360.dp))
        assertEquals(24.dp, harnessScreenHorizontalPadding(600.dp))
        assertEquals(32.dp, harnessScreenHorizontalPadding(840.dp))
    }
}