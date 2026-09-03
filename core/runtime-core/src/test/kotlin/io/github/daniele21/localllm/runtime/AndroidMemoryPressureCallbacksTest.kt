package io.github.daniele21.localllm.runtime

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidMemoryPressureCallbacksTest {
    @Test
    fun `running critical trim maps to low memory`() {
        assertEquals(
            RuntimeMemoryPressure.LOW_MEMORY,
            AndroidTrimMemoryMapper.map(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
    }

    @Test
    fun `ui hidden and background keep their lifecycle meanings`() {
        assertEquals(
            RuntimeMemoryPressure.UI_HIDDEN,
            AndroidTrimMemoryMapper.map(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
        )
        assertEquals(
            RuntimeMemoryPressure.BACKGROUND,
            AndroidTrimMemoryMapper.map(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
        )
    }

    @Test
    fun `non critical running trim does not force runtime cleanup`() {
        assertNull(AndroidTrimMemoryMapper.map(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }
}
