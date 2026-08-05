package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogTarget
import io.github.daniele21.localllm.contracts.ModelDigest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class InstalledCatalogModelMetadata(
    val digest: ModelDigest,
    val modelId: String,
    val version: String,
    val displayName: String,
    val profileKey: String,
    val applicationId: String,
    val useCaseId: String,
    val fileName: String,
    val sizeBytes: Long,
    val architecture: String,
    val quantization: String,
    val installedAtEpochMs: Long,
) {
    fun asImportedPhoneModel(): ImportedPhoneModel =
        ImportedPhoneModel(
            digest = digest,
            fileName = fileName,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
        )

    companion object {
        fun from(
            release: CatalogModelRelease,
            target: CatalogTarget,
            installedAtEpochMs: Long,
        ): InstalledCatalogModelMetadata =
            InstalledCatalogModelMetadata(
                digest = release.artifact.digest,
                modelId = release.id.modelId.value,
                version = release.id.version.value,
                displayName = release.displayName,
                profileKey = release.profileKey.value,
                applicationId = target.applicationId.value,
                useCaseId = target.useCaseId.value,
                fileName = release.artifact.fileName,
                sizeBytes = release.artifact.sizeBytes,
                architecture = release.artifact.architecture,
                quantization = release.artifact.quantization,
                installedAtEpochMs = installedAtEpochMs,
            )
    }
}

internal interface InstalledCatalogMetadataRepository {
    fun loadAll(): List<InstalledCatalogModelMetadata>

    fun save(metadata: InstalledCatalogModelMetadata): Boolean

    fun remove(digest: ModelDigest): Boolean
}

internal class FileInstalledCatalogMetadataRepository(rootDirectory: File) : InstalledCatalogMetadataRepository {
    private val root = rootDirectory.canonicalFile

    init {
        require(root.exists() || root.mkdirs()) { "Unable to create installed-model metadata directory" }
        require(root.isDirectory) { "Installed-model metadata path must be a directory" }
    }

    override fun loadAll(): List<InstalledCatalogModelMetadata> =
        root.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            .orEmpty()
            .mapNotNull(::read)
            .sortedByDescending(InstalledCatalogModelMetadata::installedAtEpochMs)

    override fun save(metadata: InstalledCatalogModelMetadata): Boolean {
        if (!valid(metadata)) return false
        val destination = fileFor(metadata.digest)
        val temporary =
            runCatching { File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, root) }.getOrNull()
                ?: return false
        return try {
            FileOutputStream(temporary).use { output ->
                encode(metadata).store(output, null)
                output.fd.sync()
            }
            moveIntoPlace(temporary, destination)
            true
        } catch (_: IOException) {
            false
        } finally {
            temporary.delete()
        }
    }

    override fun remove(digest: ModelDigest): Boolean {
        val file = fileFor(digest)
        return !file.exists() || file.delete()
    }

    private fun read(file: File): InstalledCatalogModelMetadata? =
        runCatching {
            val properties = Properties()
            file.inputStream().use(properties::load)
            decode(properties).takeIf(::valid)
        }.getOrNull()

    private fun decode(properties: Properties): InstalledCatalogModelMetadata {
        require(properties.required(KEY_SCHEMA_VERSION).toInt() == SCHEMA_VERSION) {
            "Unsupported installed-model metadata schema"
        }
        return InstalledCatalogModelMetadata(
            digest = ModelDigest(properties.required(KEY_DIGEST)),
            modelId = properties.required(KEY_MODEL_ID),
            version = properties.required(KEY_VERSION),
            displayName = properties.required(KEY_DISPLAY_NAME),
            profileKey = properties.required(KEY_PROFILE_KEY),
            applicationId = properties.required(KEY_APPLICATION_ID),
            useCaseId = properties.required(KEY_USE_CASE_ID),
            fileName = properties.required(KEY_FILE_NAME),
            sizeBytes = properties.required(KEY_SIZE_BYTES).toLong(),
            architecture = properties.required(KEY_ARCHITECTURE),
            quantization = properties.required(KEY_QUANTIZATION),
            installedAtEpochMs = properties.required(KEY_INSTALLED_AT).toLong(),
        )
    }

    private fun encode(metadata: InstalledCatalogModelMetadata): Properties =
        Properties().apply {
            setProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION.toString())
            setProperty(KEY_DIGEST, metadata.digest.sha256)
            setProperty(KEY_MODEL_ID, metadata.modelId)
            setProperty(KEY_VERSION, metadata.version)
            setProperty(KEY_DISPLAY_NAME, metadata.displayName)
            setProperty(KEY_PROFILE_KEY, metadata.profileKey)
            setProperty(KEY_APPLICATION_ID, metadata.applicationId)
            setProperty(KEY_USE_CASE_ID, metadata.useCaseId)
            setProperty(KEY_FILE_NAME, metadata.fileName)
            setProperty(KEY_SIZE_BYTES, metadata.sizeBytes.toString())
            setProperty(KEY_ARCHITECTURE, metadata.architecture)
            setProperty(KEY_QUANTIZATION, metadata.quantization)
            setProperty(KEY_INSTALLED_AT, metadata.installedAtEpochMs.toString())
        }

    private fun valid(metadata: InstalledCatalogModelMetadata): Boolean =
        SHA_256.matches(metadata.digest.sha256) &&
            validText(metadata.modelId) &&
            validText(metadata.version) &&
            validText(metadata.displayName) &&
            validText(metadata.profileKey) &&
            validText(metadata.applicationId) &&
            validText(metadata.useCaseId) &&
            validFileName(metadata.fileName) &&
            metadata.sizeBytes > 0L &&
            validText(metadata.architecture) &&
            validText(metadata.quantization) &&
            metadata.installedAtEpochMs >= 0L

    private fun validText(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_TEXT_LENGTH && value.none(Char::isISOControl)

    private fun validFileName(value: String): Boolean =
        validText(value) &&
            value.lowercase().endsWith(".gguf") &&
            !value.contains('/') &&
            !value.contains('\\') &&
            value != "." &&
            value != ".."

    private fun fileFor(digest: ModelDigest): File {
        require(SHA_256.matches(digest.sha256)) { "Invalid installed-model digest" }
        return File(root, digest.sha256 + FILE_SUFFIX)
    }

    private fun moveIntoPlace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)).also { value -> require(value.isNotBlank()) }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_TEXT_LENGTH = 2_000
        const val FILE_SUFFIX = ".properties"
        const val TEMP_PREFIX = "installed-catalog-model-"
        const val TEMP_SUFFIX = ".tmp"
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_DIGEST = "digest"
        const val KEY_MODEL_ID = "modelId"
        const val KEY_VERSION = "version"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_PROFILE_KEY = "profileKey"
        const val KEY_APPLICATION_ID = "applicationId"
        const val KEY_USE_CASE_ID = "useCaseId"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_SIZE_BYTES = "sizeBytes"
        const val KEY_ARCHITECTURE = "architecture"
        const val KEY_QUANTIZATION = "quantization"
        const val KEY_INSTALLED_AT = "installedAtEpochMs"
        val SHA_256 = Regex("^[0-9a-f]{64}$")
    }
}
