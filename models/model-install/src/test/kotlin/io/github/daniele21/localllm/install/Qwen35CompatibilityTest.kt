package io.github.daniele21.localllm.install

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen35CompatibilityTest {
    @Test
    fun referenceDescriptorsMatchCuratedCatalogAndPinnedBackend() {
        assertEquals(2, Qwen35CompatibilityManifest.references.size)
        assertEquals("aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3", Qwen35CompatibilityManifest.LLAMA_CPP_REVISION)

        Qwen35CompatibilityManifest.references.forEach { descriptor ->
            val release = CuratedModelCatalog.releases.single { it.id == descriptor.releaseId }
            assertEquals(descriptor.digest, release.artifact.digest)
            assertEquals(descriptor.sizeBytes, release.artifact.sizeBytes)
            assertEquals("qwen35", release.artifact.architecture)
            assertEquals("Q4_K_M", release.artifact.quantization)
            val evidence = Qwen35CompatibilityManifest.evidenceFor(descriptor)
            assertEquals(descriptor.digest, evidence.modelDigest)
            assertEquals("llama.cpp", evidence.backendId)
            assertEquals(Qwen35CompatibilityManifest.LLAMA_CPP_REVISION, evidence.backendRevision)
        }
    }

    @Test
    fun exactInspectedFingerprintsAreAccepted() {
        Qwen35CompatibilityManifest.references.forEach { descriptor ->
            val release = CuratedModelCatalog.releases.single { it.id == descriptor.releaseId }
            assertNull(descriptor.mismatch(release, descriptor.metadata()))
        }
    }

    @Test
    fun structuralMismatchIsRejected() {
        val descriptor = Qwen35CompatibilityManifest.references.first()
        val release = CuratedModelCatalog.releases.single { it.id == descriptor.releaseId }
        val mismatch = descriptor.mismatch(
            release,
            descriptor.metadata().copy(embeddingLength = descriptor.embeddingLength + 1),
        )
        assertTrue(mismatch?.contains("embedding") == true)
    }

    private fun Qwen35ArtifactDescriptor.metadata() = GgufArtifactMetadata(
        version = ggufVersion,
        architecture = architecture,
        name = name,
        fileType = fileType,
        quantization = quantization,
        keyValueCount = keyValueCount,
        tensorCount = tensorCount,
        contextLength = contextLength,
        blockCount = blockCount,
        embeddingLength = embeddingLength,
    )
}
