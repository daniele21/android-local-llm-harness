package io.github.daniele21.localllm.store

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class FileSystemModelStore(
    private val rootDirectory: File,
    private val bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES,
) : ModelStore {
    init {
        require(bufferSizeBytes > 0) { "bufferSizeBytes must be positive" }
    }

    override fun find(digest: ModelDigest): StoredModel? {
        val canonicalDigest = ModelStoreLayout.canonicalDigest(digest)
        val file = artifactFile(canonicalDigest)
        if (!file.isFile) return null

        return StoredModel(
            digest = canonicalDigest,
            file = file,
            sizeBytes = file.length(),
            verified = false,
        )
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel {
        validateSource(source)
        val expectedDigest = canonicalImportDigest(artifact.digest)
        val expectedSize = artifact.sizeBytes
        if (expectedSize < 0) {
            throw ModelImportException(
                ModelImportErrorCode.SIZE_MISMATCH,
                "Expected model size must not be negative: $expectedSize",
            )
        }

        val destination = artifactFile(expectedDigest)
        existingImport(destination, expectedDigest, expectedSize)?.let { return it }

        val stagingDirectory = File(rootDirectory, STAGING_DIRECTORY)
        ensureDirectory(stagingDirectory)
        val temporaryFile = try {
            File.createTempFile("model-import-", ".part", stagingDirectory)
        } catch (error: IOException) {
            throw ModelImportException(
                ModelImportErrorCode.IO_FAILURE,
                "Unable to create a model import staging file",
                error,
            )
        }

        try {
            val copied = copyAndDigest(source, temporaryFile)
            if (copied.sizeBytes != expectedSize) {
                throw ModelImportException(
                    ModelImportErrorCode.SIZE_MISMATCH,
                    "Model size mismatch: expected $expectedSize bytes, copied ${copied.sizeBytes} bytes",
                )
            }
            if (copied.digest != expectedDigest) {
                throw ModelImportException(
                    ModelImportErrorCode.DIGEST_MISMATCH,
                    "Model digest mismatch: expected ${expectedDigest.sha256}, computed ${copied.digest.sha256}",
                )
            }

            ensureDirectory(destination.parentFile)
            existingImport(destination, expectedDigest, expectedSize)?.let { return it }
            moveIntoPlace(temporaryFile, destination)

            return StoredModel(
                digest = expectedDigest,
                file = destination,
                sizeBytes = expectedSize,
                verified = true,
            )
        } catch (error: ModelImportException) {
            throw error
        } catch (error: IOException) {
            throw ModelImportException(
                ModelImportErrorCode.IO_FAILURE,
                "Unable to import model into the content-addressed store",
                error,
            )
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    override fun verify(digest: ModelDigest): VerificationResult {
        val expectedDigest = try {
            ModelStoreLayout.canonicalDigest(digest)
        } catch (error: IllegalArgumentException) {
            return VerificationResult(
                valid = false,
                actualDigest = null,
                detail = error.message ?: "Invalid SHA-256 digest",
            )
        }
        val file = artifactFile(expectedDigest)
        if (!file.isFile) {
            return VerificationResult(
                valid = false,
                actualDigest = null,
                detail = "Stored model is missing",
            )
        }

        return try {
            val actualDigest = digest(file)
            VerificationResult(
                valid = actualDigest == expectedDigest,
                actualDigest = actualDigest,
                detail = if (actualDigest == expectedDigest) {
                    "Stored model SHA-256 matches its content-addressed path"
                } else {
                    "Stored model SHA-256 does not match its content-addressed path"
                },
            )
        } catch (error: IOException) {
            VerificationResult(
                valid = false,
                actualDigest = null,
                detail = "Unable to read stored model: ${error.message}",
            )
        }
    }

    override fun remove(digest: ModelDigest): Boolean {
        val canonicalDigest = ModelStoreLayout.canonicalDigest(digest)
        val file = artifactFile(canonicalDigest)
        if (!file.exists()) return false
        if (!file.delete()) return false

        pruneEmptyParents(file.parentFile)
        return true
    }

    override fun snapshot(): ModelStoreSnapshot {
        val shaRoot = File(rootDirectory, SHA_ROOT_DIRECTORY)
        if (!shaRoot.isDirectory) return ModelStoreSnapshot(0, 0, emptyList())

        val entries = shaRoot.walkTopDown()
            .filter { it.isFile && it.name == ARTIFACT_FILE_NAME }
            .mapNotNull(::snapshotEntry)
            .sortedBy { it.digest.sha256 }
            .toList()

        return ModelStoreSnapshot(
            modelCount = entries.size,
            totalBytes = entries.sumOf { it.sizeBytes },
            entries = entries,
        )
    }

    private fun validateSource(source: File) {
        if (!source.isFile || !source.canRead()) {
            throw ModelImportException(
                ModelImportErrorCode.INVALID_SOURCE,
                "Model source must be a readable regular file: ${source.path}",
            )
        }
    }

    private fun canonicalImportDigest(digest: ModelDigest): ModelDigest = try {
        ModelStoreLayout.canonicalDigest(digest)
    } catch (error: IllegalArgumentException) {
        throw ModelImportException(
            ModelImportErrorCode.INVALID_DIGEST,
            error.message ?: "Invalid SHA-256 digest",
            error,
        )
    }

    private fun existingImport(
        destination: File,
        expectedDigest: ModelDigest,
        expectedSize: Long,
    ): StoredModel? {
        if (!destination.exists()) return null
        if (!destination.isFile) {
            throw destinationConflict(destination, "destination is not a regular file")
        }
        if (destination.length() != expectedSize) {
            throw destinationConflict(destination, "stored size differs from the expected size")
        }

        val actualDigest = try {
            digest(destination)
        } catch (error: IOException) {
            throw ModelImportException(
                ModelImportErrorCode.IO_FAILURE,
                "Unable to verify existing model object: ${destination.path}",
                error,
            )
        }
        if (actualDigest != expectedDigest) {
            throw destinationConflict(destination, "stored content does not match its SHA-256 path")
        }

        return StoredModel(
            digest = expectedDigest,
            file = destination,
            sizeBytes = expectedSize,
            verified = true,
        )
    }

    private fun destinationConflict(destination: File, detail: String): ModelImportException =
        ModelImportException(
            ModelImportErrorCode.DESTINATION_CONFLICT,
            "Content-addressed destination conflict at ${destination.path}: $detail",
        )

    private fun copyAndDigest(source: File, destination: File): DigestedFile {
        val messageDigest = MessageDigest.getInstance(SHA_256)
        var copiedBytes = 0L

        source.inputStream().buffered(bufferSizeBytes).use { input ->
            destination.outputStream().buffered(bufferSizeBytes).use { output ->
                val buffer = ByteArray(bufferSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    messageDigest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    copiedBytes += read
                }
                output.flush()
            }
        }

        return DigestedFile(
            digest = ModelDigest(messageDigest.digest().toHex()),
            sizeBytes = copiedBytes,
        )
    }

    private fun digest(file: File): ModelDigest {
        val messageDigest = MessageDigest.getInstance(SHA_256)
        file.inputStream().buffered(bufferSizeBytes).use { input ->
            val buffer = ByteArray(bufferSizeBytes)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) messageDigest.update(buffer, 0, read)
            }
        }
        return ModelDigest(messageDigest.digest().toHex())
    }

    private fun moveIntoPlace(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            try {
                Files.move(source.toPath(), destination.toPath())
            } catch (race: FileAlreadyExistsException) {
                throw destinationConflict(destination, "another import created the destination concurrently")
            }
        } catch (error: FileAlreadyExistsException) {
            throw destinationConflict(destination, "another import created the destination concurrently")
        }
    }

    private fun ensureDirectory(directory: File?) {
        if (directory == null) {
            throw ModelImportException(ModelImportErrorCode.IO_FAILURE, "Model store directory is unavailable")
        }
        if (directory.isDirectory) return
        if (directory.exists() || !directory.mkdirs()) {
            throw ModelImportException(
                ModelImportErrorCode.IO_FAILURE,
                "Unable to create model store directory: ${directory.path}",
            )
        }
    }

    private fun artifactFile(digest: ModelDigest): File =
        File(rootDirectory, ModelStoreLayout.relativeArtifactPath(digest))

    private fun snapshotEntry(file: File): StoredModel? {
        val digestDirectory = file.parentFile ?: return null
        val digest = try {
            ModelStoreLayout.canonicalDigest(ModelDigest(digestDirectory.name))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return StoredModel(
            digest = digest,
            file = file,
            sizeBytes = file.length(),
            verified = false,
        )
    }

    private fun pruneEmptyParents(start: File?) {
        val stop = File(rootDirectory, SHA_ROOT_DIRECTORY)
        var current = start
        while (current != null && current != stop && current.isDirectory) {
            val children = current.list()
            if (children == null || children.isNotEmpty() || !current.delete()) return
            current = current.parentFile
        }
    }

    private fun ByteArray.toHex(): String {
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = HEX_DIGITS[value ushr 4]
            result[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return String(result)
    }

    private data class DigestedFile(val digest: ModelDigest, val sizeBytes: Long)

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
        const val SHA_256 = "SHA-256"
        const val STAGING_DIRECTORY = "models/.staging"
        const val SHA_ROOT_DIRECTORY = "models/sha256"
        const val ARTIFACT_FILE_NAME = "model.gguf"
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
