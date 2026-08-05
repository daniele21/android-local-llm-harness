package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import java.io.File

class SecureModelDownloader(
    private val workingDirectory: File,
    transport: DownloadTransport,
    private val modelStore: ModelStore,
    policy: SecureDownloadPolicy,
) {
    private val transferEngine = SecureArtifactTransfer(transport, policy)

    fun download(request: DownloadRequest): DownloadResult {
        val artifact = request.release.artifact
        val installed = modelStore.find(artifact.digest)
        return installed?.let(DownloadResult::AlreadyInstalled) ?: downloadMissing(artifact, request)
    }

    private fun downloadMissing(artifact: CatalogGgufArtifact, request: DownloadRequest): DownloadResult {
        val partFile = preparePartFile(artifact) ?: return directoryFailure()
        return try {
            when (val transfer = transferEngine.transfer(artifact, partFile, request)) {
                is ArtifactTransferResult.Failure -> DownloadResult.Failed(transfer.error)
                is ArtifactTransferResult.Success -> importVerified(partFile, artifact, transfer.bytes)
            }
        } finally {
            partFile.delete()
        }
    }

    private fun preparePartFile(artifact: CatalogGgufArtifact): File? {
        val directoryReady = workingDirectory.exists() || workingDirectory.mkdirs()
        return if (directoryReady) {
            File(workingDirectory, "${artifact.digest.sha256.lowercase()}.part").also(File::delete)
        } else {
            null
        }
    }

    private fun importVerified(partFile: File, artifact: CatalogGgufArtifact, downloadedBytes: Long): DownloadResult = try {
        val stored = modelStore.import(partFile, artifact.toStoreArtifact())
        DownloadResult.Installed(stored, downloadedBytes)
    } catch (error: ModelImportException) {
        storeFailure(error.message ?: error.code.name)
    } catch (error: IllegalStateException) {
        storeFailure(error.message ?: "Model store failure")
    }

    private fun CatalogGgufArtifact.toStoreArtifact() = GgufArtifact(
        digest = digest,
        fileName = fileName,
        sizeBytes = sizeBytes,
        architecture = architecture,
        quantization = quantization,
        source = ArtifactSource.Download(downloadUri.toString()),
    )

    private fun directoryFailure() = DownloadResult.Failed(
        DownloadError(DownloadErrorCode.IO_FAILURE, "Unable to create download directory"),
    )

    private fun storeFailure(detail: String) = DownloadResult.Failed(DownloadError(DownloadErrorCode.STORE_FAILURE, detail))
}
