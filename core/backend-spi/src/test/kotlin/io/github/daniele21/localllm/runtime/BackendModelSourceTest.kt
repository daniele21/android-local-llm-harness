package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BackendModelSourceTest {
    @Test
    fun `source preserves immutable artifact identity and materialized file`() {
        val digest = ModelDigest("a".repeat(64))
        val file = File("model.gguf")

        val source = BackendModelSource(digest = digest, file = file, sizeBytes = 42)

        assertEquals(digest, source.digest)
        assertEquals(file, source.file)
        assertEquals(42, source.sizeBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `source rejects negative size`() {
        BackendModelSource(
            digest = ModelDigest("b".repeat(64)),
            file = File("model.gguf"),
            sizeBytes = -1,
        )
    }
}
