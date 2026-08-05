package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.contracts.ModelDigest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest

data class VerifiedDownloadCopyRequest(val handle: VerifiedDownloadHandle, val expectedDigest: ModelDigest, val expectedSizeBytes: Long)

sealed interface VerifiedDownloadCopyResult {
    data class Success(val digest: ModelDigest, val sizeBytes: Long) : VerifiedDownloadCopyResult

    data class Failure(val code: VerifiedDownloadAccessFailureCode) : VerifiedDownloadCopyResult
}

enum class VerifiedDownloadAccessFailureCode {
    INVALID_DESCRIPTOR,
    HANDLE_MISMATCH,
    VERIFIED_DOWNLOAD_MISSING,
    SIZE_MISMATCH,
    DIGEST_MISMATCH,
    INVALID_DESTINATION,
    IO_FAILURE,
}

interface VerifiedDownloadAccess {
    fun copyTo(request: VerifiedDownloadCopyRequest, destination: File): VerifiedDownloadCopyResult

    fun discard(handle: VerifiedDownloadHandle): Boolean
}

@Suppress("ReturnCount")
class FileSystemVerifiedDownloadAccess(rootDirectory: File, private val bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES) :
    VerifiedDownloadAccess {
    private val fileStore = DownloadFileStore(rootDirectory)

    init {
        require(bufferSizeBytes in MIN_BUFFER_SIZE_BYTES..MAX_BUFFER_SIZE_BYTES) {
            "Verified download copy buffer is outside the supported range"
        }
    }

    override fun copyTo(request: VerifiedDownloadCopyRequest, destination: File): VerifiedDownloadCopyResult {
        val expectedDigest =
            canonicalDigest(request.expectedDigest)
                ?: return failure(VerifiedDownloadAccessFailureCode.INVALID_DESCRIPTOR)
        if (request.expectedSizeBytes <= 0L) {
            return failure(VerifiedDownloadAccessFailureCode.INVALID_DESCRIPTOR)
        }
        if (request.handle.value != expectedDigest.sha256) {
            return failure(VerifiedDownloadAccessFailureCode.HANDLE_MISMATCH)
        }

        val source =
            fileStore.resolve(request.handle)
                ?: return failure(VerifiedDownloadAccessFailureCode.VERIFIED_DOWNLOAD_MISSING)
        if (source.length() != request.expectedSizeBytes) {
            return failure(VerifiedDownloadAccessFailureCode.SIZE_MISMATCH)
        }
        if (!validDestination(destination)) {
            return failure(VerifiedDownloadAccessFailureCode.INVALID_DESTINATION)
        }

        return try {
            val copied = copyAndDigest(source, destination, request.expectedSizeBytes)
            when {
                copied.sizeBytes != request.expectedSizeBytes -> {
                    destination.delete()
                    failure(VerifiedDownloadAccessFailureCode.SIZE_MISMATCH)
                }

                copied.digest != expectedDigest -> {
                    destination.delete()
                    failure(VerifiedDownloadAccessFailureCode.DIGEST_MISMATCH)
                }

                else -> VerifiedDownloadCopyResult.Success(expectedDigest, copied.sizeBytes)
            }
        } catch (_: IOException) {
            destination.delete()
            failure(VerifiedDownloadAccessFailureCode.IO_FAILURE)
        }
    }

    override fun discard(handle: VerifiedDownloadHandle): Boolean = fileStore.discard(handle)

    private fun copyAndDigest(source: File, destination: File, maximumBytes: Long): CopyDigestResult {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
        val total =
            FileInputStream(source).use { input ->
                FileOutputStream(destination, false).use { output ->
                    copyStream(input, output, digest, maximumBytes).also { output.fd.sync() }
                }
            }
        if (total > maximumBytes) {
            return CopyDigestResult(digest = null, sizeBytes = total)
        }
        val actualDigest =
            ModelDigest(
                digest.digest().joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                },
            )
        return CopyDigestResult(actualDigest, total)
    }

    private fun copyStream(input: InputStream, output: OutputStream, digest: MessageDigest, maximumBytes: Long): Long {
        val buffer = ByteArray(bufferSizeBytes)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return total
            if (count.toLong() > maximumBytes - total) return maximumBytes + 1L
            total += count.toLong()
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
    }

    private fun validDestination(destination: File): Boolean {
        if (
            destination.exists() &&
            (!destination.isFile || Files.isSymbolicLink(destination.toPath()))
        ) {
            return false
        }
        val parent = destination.parentFile ?: return false
        if (parent.exists()) return parent.isDirectory && !Files.isSymbolicLink(parent.toPath())
        return parent.mkdirs() && parent.isDirectory && !Files.isSymbolicLink(parent.toPath())
    }

    private fun canonicalDigest(digest: ModelDigest): ModelDigest? {
        val value = digest.sha256.lowercase()
        return value.takeIf { it.matches(SHA_256) }?.let(::ModelDigest)
    }

    private fun failure(code: VerifiedDownloadAccessFailureCode): VerifiedDownloadCopyResult.Failure =
        VerifiedDownloadCopyResult.Failure(code)

    private data class CopyDigestResult(val digest: ModelDigest?, val sizeBytes: Long)

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 65_536
        const val MIN_BUFFER_SIZE_BYTES = 4_096
        const val MAX_BUFFER_SIZE_BYTES = 1_048_576
        const val SHA_256_ALGORITHM = "SHA-256"
        val SHA_256 = Regex("^[0-9a-f]{64}$")
    }
}
