package io.github.daniele21.localllm.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLoadExecutionIdentityTest {
    @Test
    fun `materializes all legacy mmap mlock combinations`() {
        val cases = listOf(
            NativeModelExecutionRequest(0, useMmap = false, useMlock = false) to MaterializedModelLoadMode.NONE,
            NativeModelExecutionRequest(0, useMmap = true, useMlock = false) to MaterializedModelLoadMode.MMAP,
            NativeModelExecutionRequest(0, useMmap = false, useMlock = true) to MaterializedModelLoadMode.MLOCK,
            NativeModelExecutionRequest(0, useMmap = true, useMlock = true) to MaterializedModelLoadMode.MMAP_MLOCK,
        )

        cases.forEach { (request, expected) ->
            assertEquals(expected, request.materializedLoadMode)
        }
    }
}
