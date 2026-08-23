package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessThemePreferenceStoreTest {
    @Test
    fun `known persisted theme is restored`() {
        assertEquals(
            HarnessThemePreference.SYSTEM,
            HarnessThemePreferenceStore.decode("SYSTEM"),
        )
    }

    @Test
    fun `missing or unknown persisted theme fails to stable default`() {
        assertEquals(HarnessThemePreference.DARK, HarnessThemePreferenceStore.decode(null))
        assertEquals(HarnessThemePreference.DARK, HarnessThemePreferenceStore.decode("UNKNOWN"))
    }
}
