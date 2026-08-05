package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogAvailability
import io.github.daniele21.localllm.catalog.CatalogCompatibility
import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.catalog.CatalogLicense
import io.github.daniele21.localllm.catalog.CatalogModelId
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogModelVersion
import io.github.daniele21.localllm.catalog.CatalogReleaseId
import io.github.daniele21.localllm.catalog.ModelProfileKey
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest

class SecureModelDownloaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun validDownloadIsVerifiedAndImported() {
        val bytes = "tiny-gguf".toByteArray()
        val store = RecordingStore(temporaryFolder.newFolder("store"))
        val downloader = downloader(store, QueueTransport(response(200, bytes)), setOf("models.example"))

        val result = downloader.download(DownloadRequest(release(bytes)))

        assertTrue(result is DownloadResult.Installed)
        assertEquals(bytes.toList(), store.importedBytes?.toList())
        assertFalse(temporaryFolder.root.walkTopDown().any { it.extension == "part" })
    }

    @Test
    fun digestMismatchNeverReachesStore() {
        val expected = "expected".toByteArray()
        val downloader =
            downloader(
                RecordingStore(temporaryFolder.newFolder("store-mismatch")),
                QueueTransport(response(200, "tampered".toByteArray(), expected.size.toLong())),
                setOf("models.example"),
            )

        val result = downloader.download(DownloadRequest(release(expected)))

        assertEquals(
            DownloadErrorCode.DIGEST_MISMATCH,
            (result as DownloadResult.Failed).error.code,
        )
    }

    @Test
    fun redirectToUnlistedHostIsRejected() {
        val bytes = "content".toByteArray()
        val redirect = response(302, ByteArray(0), redirect = "https://evil.example/model.gguf")
        val downloader =
            downloader(
                RecordingStore(temporaryFolder.newFolder("store-redirect")),
                QueueTransport(redirect),
                setOf("models.example"),
            )

        val result = downloader.download(DownloadRequest(release(bytes)))

        assertEquals(
            DownloadErrorCode.INVALID_REDIRECT,
            (result as DownloadResult.Failed).error.code,
        )
    }

    @Test
    fun cancellationRemovesPartialFile() {
        val bytes = ByteArray(32) { it.toByte() }
        var checks = 0
        val request =
            DownloadRequest(
                release = release(bytes),
                cancellation = DownloadCancellation { ++checks > 2 },
            )
        val downloader =
            downloader(
                RecordingStore(temporaryFolder.newFolder("store-cancel")),
                QueueTransport(response(200, bytes)),
                setOf("models.example"),
                bufferSize = 4 * 1024,
            )

        val result = downloader.download(request)

        assertEquals(DownloadErrorCode.CANCELLED, (result as DownloadResult.Failed).error.code)
        assertFalse(temporaryFolder.root.walkTopDown().any { it.extension == "part" })
    }

    private fun downloader(store: ModelStore, transport: DownloadTransport, hosts: Set<String>, bufferSize: Int = 4 * 1024) =
        SecureModelDownloader(
            workingDirectory = temporaryFolder.newFolder(),
            transport = transport,
            modelStore = store,
            policy =
            SecureDownloadPolicy(
                hostPolicy = AllowlistedDownloadHosts(hosts),
                bufferSizeBytes = bufferSize,
            ),
        )

    private fun release(bytes: ByteArray): CatalogModelRelease = CatalogModelRelease(
        id = CatalogReleaseId(CatalogModelId("test"), CatalogModelVersion("1.0.0")),
        displayName = "Test",
        description = "Test release",
        artifact =
        CatalogGgufArtifact(
            digest = ModelDigest(sha256(bytes)),
            sizeBytes = bytes.size.toLong(),
            downloadUri = URI("https://models.example/model.gguf"),
            architecture = "llama",
            quantization = "Q4_K_M",
            fileName = "model.gguf",
        ),
        compatibility = CatalogCompatibility(minSdk = 26, supportedAbis = setOf("arm64-v8a")),
        availability = CatalogAvailability.CANDIDATE,
        allowedTargets = emptySet(),
        profileKey = ModelProfileKey("test-profile"),
        license = CatalogLicense("Apache-2.0", "Apache-2.0"),
    )

    private fun response(status: Int, bytes: ByteArray, length: Long? = bytes.size.toLong(), redirect: String? = null) =
        FakeResponse(status, length, redirect, bytes)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class QueueTransport(private vararg val responses: TransportResponse) : DownloadTransport {
        private var index = 0
        override fun open(request: TransportRequest): TransportResponse = responses[index++]
    }

    private class FakeResponse(
        override val statusCode: Int,
        override val contentLength: Long?,
        override val redirectLocation: String?,
        bytes: ByteArray,
    ) : TransportResponse {
        override val body = ByteArrayInputStream(bytes)
        override fun close() = body.close()
    }

    private class RecordingStore(private val directory: File) : ModelStore {
        var importedBytes: ByteArray? = null
        override fun find(digest: ModelDigest): StoredModel? = null
        override fun import(source: File, artifact: GgufArtifact): StoredModel {
            importedBytes = source.readBytes()
            val destination = File(directory, artifact.fileName).apply { writeBytes(importedBytes!!) }
            return StoredModel(artifact.digest, destination, destination.length(), true)
        }
        override fun verify(digest: ModelDigest) = VerificationResult(false, null, "missing")
        override fun remove(digest: ModelDigest) = false
        override fun snapshot() = ModelStoreSnapshot(0, 0, emptyList())
    }
}
