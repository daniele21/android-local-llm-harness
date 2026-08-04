package io.github.daniele21.localllm.console

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.daniele21.localllm.contracts.ModelDigest
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

@Suppress("TooGenericExceptionCaught")
class AndroidModelImportStager(context: Context, private val bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES) {
    private val appContext = context.applicationContext

    init {
        require(bufferSizeBytes > 0) { "bufferSizeBytes must be positive" }
    }

    fun stage(uri: Uri, architecture: String, quantization: String): ConsoleModelImportRequest {
        val metadata = queryMetadata(uri)
        require(metadata.fileName.lowercase().endsWith(".gguf")) { "Select a .gguf model file" }
        val stagedFile = createStagingFile()

        return try {
            val staged = copyAndDigest(uri, stagedFile)
            metadata.sizeBytes?.let { expected ->
                check(expected == staged.sizeBytes) { "Selected model size changed while staging" }
            }
            ConsoleModelImportRequest(
                source = stagedFile,
                fileName = metadata.fileName,
                digest = staged.digest,
                sizeBytes = staged.sizeBytes,
                architecture = architecture,
                quantization = quantization,
            )
        } catch (error: Throwable) {
            stagedFile.delete()
            throw error
        }
    }

    private fun createStagingFile(): File {
        val stagingDirectory = File(appContext.cacheDir, STAGING_DIRECTORY)
        check(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Model staging directory is unavailable" }
        return File.createTempFile("console-model-", ".gguf", stagingDirectory)
    }

    private fun copyAndDigest(uri: Uri, destination: File): StagedContent {
        val digest = MessageDigest.getInstance(SHA_256)
        val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
            "Selected model cannot be opened"
        }
        val copiedBytes = input.buffered(bufferSizeBytes).use { source ->
            destination.outputStream().buffered(bufferSizeBytes).use { output ->
                copy(source, output::write, digest)
            }
        }
        return StagedContent(
            digest = ModelDigest(digest.digest().toHex()),
            sizeBytes = copiedBytes,
        )
    }

    private fun copy(input: InputStream, write: (ByteArray, Int, Int) -> Unit, digest: MessageDigest): Long {
        val buffer = ByteArray(bufferSizeBytes)
        var copiedBytes = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read)
                write(buffer, 0, read)
                copiedBytes += read
            }
            read = input.read(buffer)
        }
        return copiedBytes
    }

    private fun queryMetadata(uri: Uri): DocumentMetadata {
        var displayName = DEFAULT_FILE_NAME
        var sizeBytes: Long? = null
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }
        return DocumentMetadata(
            fileName = File(displayName).name.ifBlank { DEFAULT_FILE_NAME },
            sizeBytes = sizeBytes,
        )
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

    private data class StagedContent(val digest: ModelDigest, val sizeBytes: Long)

    private data class DocumentMetadata(val fileName: String, val sizeBytes: Long?)

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
        const val STAGING_DIRECTORY = "model-imports"
        const val DEFAULT_FILE_NAME = "model.gguf"
        const val SHA_256 = "SHA-256"
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
