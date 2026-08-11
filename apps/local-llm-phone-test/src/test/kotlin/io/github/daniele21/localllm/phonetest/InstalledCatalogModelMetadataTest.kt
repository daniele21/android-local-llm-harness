package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class InstalledCatalogModelMetadataTest {
    @Test
    fun roundTripsPathFreeCuratedCatalogAndProfileMetadata() {
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

    @Test
    fun rejectsMetadataThatDoesNotMatchTheCurrentCuratedRelease() {
        val root = Files.createTempDirectory("installed-catalog-metadata").toFile()
        val repository = FileInstalledCatalogMetadataRepository(root)

        assertFalse(repository.save(metadata().copy(modelId = "retired-model")))
        assertTrue(repository.loadAll().isEmpty())
    }

    private fun metadata(): InstalledCatalogModelMetadata {
        val release = CuratedModelCatalog.releases.first()
        val target = release.allowedTargets.first()
        return InstalledCatalogModelMetadata.from(
            release = release,
            target = target,
            installedAtEpochMs = 1_234L,
        )
    }
}
