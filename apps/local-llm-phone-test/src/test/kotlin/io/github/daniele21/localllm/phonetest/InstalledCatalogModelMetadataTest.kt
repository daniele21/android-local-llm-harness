package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledCatalogModelMetadataTest {
    @Test
    fun roundTripsPathFreeCatalogAndProfileMetadata() {
        val root = Files.createTempDirectory("installed-catalog-metadata").toFile()
        val repository = FileInstalledCatalogMetadataRepository(root)
        val metadata = metadata()

        assertTrue(repository.save(metadata))

        assertEquals(listOf(metadata), repository.loadAll())
    }

    @Test
    fun rejectsUnsupportedPersistentSchema() {
        val root = Files.createTempDirectory("installed-catalog-metadata").toFile()
        val repository = FileInstalledCatalogMetadataRepository(root)
        val metadata = metadata()
        assertTrue(repository.save(metadata))
        val file = root.resolve(metadata.digest.sha256 + ".properties")
        file.writeText(file.readText().replace("schemaVersion=1", "schemaVersion=999"))

        assertTrue(repository.loadAll().isEmpty())
    }

    @Test
    fun removesMetadataByImmutableDigest() {
        val root = Files.createTempDirectory("installed-catalog-metadata").toFile()
        val repository = FileInstalledCatalogMetadataRepository(root)
        val metadata = metadata()
        assertTrue(repository.save(metadata))

        assertTrue(repository.remove(metadata.digest))
        assertTrue(repository.loadAll().isEmpty())
    }

    @Test
    fun rejectsUnsafeFileNameMetadata() {
        val root = Files.createTempDirectory("installed-catalog-metadata").toFile()
        val repository = FileInstalledCatalogMetadataRepository(root)

        assertFalse(repository.save(metadata().copy(fileName = "../model.gguf")))
        assertTrue(repository.loadAll().isEmpty())
    }

    private fun metadata(): InstalledCatalogModelMetadata =
        InstalledCatalogModelMetadata(
            digest = ModelDigest("a".repeat(64)),
            modelId = "test-model",
            version = "1.0.0",
            displayName = "Test model",
            profileKey = "test-profile",
            applicationId = "play-internal-phone-test",
            useCaseId = "manual-inference-playground",
            fileName = "test-model.gguf",
            sizeBytes = 128L,
            architecture = "qwen35",
            quantization = "Q4_K_M",
            installedAtEpochMs = 1234L,
        )
}
