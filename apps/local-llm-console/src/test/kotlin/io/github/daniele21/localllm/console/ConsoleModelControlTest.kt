package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportErrorCode
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConsoleModelControlTest {
    private val digest = ModelDigest("a".repeat(64))

    @Test
    fun `connected model store exposes explicit capabilities`() {
        val state = ModelStoreConsoleModelControl(FakeModelStore(), "Embedded runtime").snapshot()

        assertTrue(state.available)
        assertTrue(state.importAvailable)
        assertTrue(state.verifyAvailable)
        assertTrue(state.removeAvailable)
        assertEquals("Embedded runtime", state.source)
    }

    @Test
    fun `import forwards staged digest size and profile metadata`() {
        val source = File.createTempFile("console-model", ".gguf").apply { writeText("model") }
        val store = FakeModelStore()
        val control = ModelStoreConsoleModelControl(store, "Local")
        val request = ConsoleModelImportRequest(
            source = source,
            fileName = "tiny.gguf",
            digest = digest,
            sizeBytes = source.length(),
            architecture = "qwen3",
            quantization = "Q4_K_M",
        )

        val outcome = control.importModel(request)

        assertTrue(outcome.success)
        assertEquals(digest, outcome.digest)
        assertEquals("qwen3", store.importedArtifact?.architecture)
        assertEquals("Q4_K_M", store.importedArtifact?.quantization)
        assertEquals(source.length(), store.importedArtifact?.sizeBytes)
        source.delete()
    }

    @Test
    fun `verify returns a privacy safe result`() {
        val store = FakeModelStore().apply {
            verificationResult = VerificationResult(false, ModelDigest("b".repeat(64)), "/private/model path mismatch")
        }

        val outcome = ModelStoreConsoleModelControl(store, "Local").verify(digest)

        assertFalse(outcome.success)
        assertEquals("Model integrity check failed", outcome.detail)
        assertFalse(outcome.toString().contains("/private/model"))
    }

    @Test
    fun `remove is blocked when the same model is loaded`() {
        val store = FakeModelStore()
        val control = ModelStoreConsoleModelControl(
            modelStore = store,
            source = "Embedded runtime",
            loadedModelDigest = { digest },
        )

        val outcome = control.remove(digest)

        assertFalse(outcome.success)
        assertEquals("Loaded model cannot be removed", outcome.detail)
        assertEquals(0, store.removeCalls)
    }

    @Test
    fun `import errors are mapped without backend details`() {
        val source = File.createTempFile("console-model", ".gguf").apply { writeText("model") }
        val store = FakeModelStore().apply {
            importFailure = ModelImportException(ModelImportErrorCode.IO_FAILURE, "/private/staging/model.gguf")
        }
        val request = ConsoleModelImportRequest(
            source = source,
            fileName = "tiny.gguf",
            digest = digest,
            sizeBytes = source.length(),
            architecture = "unknown",
            quantization = "unknown",
        )

        val outcome = ModelStoreConsoleModelControl(store, "Local").importModel(request)

        assertFalse(outcome.success)
        assertEquals("Model import failed", outcome.detail)
        assertFalse(outcome.toString().contains("/private/staging"))
        source.delete()
    }

    @Test
    fun `disconnected control returns a fixed unavailable state`() {
        val outcome = DisconnectedModelControl.verify(digest)

        assertFalse(DisconnectedModelControl.snapshot().available)
        assertEquals("Model management unavailable", outcome.sourceError)
    }
}

private class FakeModelStore : ModelStore {
    var importedArtifact: GgufArtifact? = null
    var importFailure: ModelImportException? = null
    var verificationResult = VerificationResult(true, null, "verified")
    var removeCalls = 0

    override fun find(digest: ModelDigest): StoredModel? = null

    override fun import(source: File, artifact: GgufArtifact): StoredModel {
        importFailure?.let { throw it }
        importedArtifact = artifact
        return StoredModel(artifact.digest, source, artifact.sizeBytes, verified = true)
    }

    override fun verify(digest: ModelDigest): VerificationResult = verificationResult

    override fun remove(digest: ModelDigest): Boolean {
        removeCalls += 1
        return true
    }

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}
