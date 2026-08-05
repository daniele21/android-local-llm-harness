package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.contracts.ModelDigest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class SecureModelDownloaderTest {
    @Test
    fun downloadsVerifiesPublishesAndDeduplicatesByDigest() {
        val root = temporaryDirectory()
        val bytes = "verified-model".repeat(1_000).toByteArray()
        val transport = QueueTransport(FakeResponse(200, bytes))
        val progress = mutableListOf<DownloadProgress>()
        val downloader = downloader(root, transport)

        val first = downloader.download(
            ModelDownloadRequest(artifact(bytes)),
            DownloadProgressObserver(progress::add),
        )

        assertTrue(first is ModelDownloadResult.Success)
        first as ModelDownloadResult.Success
        assertFalse(first.deduplicated)
        val file = downloader.resolveVerifiedFile(first.handle)
        assertNotNull(file)
        assertArrayEquals(bytes, file!!.readBytes())
        assertEquals(DownloadStage.PREPARING, progress.first().stage)
        assertEquals(DownloadStage.COMPLETED, progress.last().stage)

        val second = downloader.download(ModelDownloadRequest(artifact(bytes)))
        assertTrue(second is ModelDownloadResult.Success)
        assertTrue((second as ModelDownloadResult.Success).deduplicated)
        assertEquals(1, transport.requestedUris.size)
        assertTrue(downloader.discardVerifiedDownload(first.handle))
        assertFalse(file.exists())
    }

    @Test
    fun followsOnlyRedirectsAcceptedByTheSourcePolicy() {
        val bytes = "redirected-model".repeat(100).toByteArray()
        val allowed = downloader(
            temporaryDirectory(),
            QueueTransport(
                FakeResponse(302, redirectLocation = "https://cdn.example.com/model.gguf"),
                FakeResponse(200, bytes),
            ),
        ).download(ModelDownloadRequest(artifact(bytes)))
        assertTrue(allowed is ModelDownloadResult.Success)
        assertEquals("cdn.example.com", (allowed as ModelDownloadResult.Success).sourceHost)

        val blocked = downloader(
            temporaryDirectory(),
            QueueTransport(
                FakeResponse(
                    302,
                    redirectLocation = "https://evil.example/model.gguf?token=private",
                ),
            ),
        ).download(ModelDownloadRequest(artifact(bytes)))
        assertFailure(blocked, DownloadFailureCode.DOWNLOAD_REDIRECT_INVALID)
        blocked as ModelDownloadResult.Failure
        assertTrue("token" !in blocked.failure.detail)
        assertTrue("private" !in blocked.failure.detail)

        val downgrade = downloader(
            temporaryDirectory(),
            QueueTransport(
                FakeResponse(302, redirectLocation = "http://huggingface.co/model.gguf"),
            ),
        ).download(ModelDownloadRequest(artifact(bytes)))
        assertFailure(downgrade, DownloadFailureCode.DOWNLOAD_REDIRECT_INVALID)
    }

    @Test
    fun rejectsTruncatedOversizedEncodedAndDigestMismatchedBodies() {
        val expected = "expected-body".repeat(50).toByteArray()
        val truncated = expected.copyOf(expected.size - 1)
        val oversized = expected + byteArrayOf(1)
        val wrongDigest = expected.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertDownloadFailure(expected, truncated, DownloadFailureCode.DOWNLOAD_SIZE_MISMATCH)
        assertDownloadFailure(expected, oversized, DownloadFailureCode.DOWNLOAD_SIZE_EXCEEDED)
        val encoded = downloader(
            temporaryDirectory(),
            QueueTransport(FakeResponse(200, expected, contentEncoding = "gzip")),
        ).download(ModelDownloadRequest(artifact(expected)))
        assertFailure(encoded, DownloadFailureCode.CONTENT_ENCODING_UNSUPPORTED)
        assertDownloadFailure(expected, wrongDigest, DownloadFailureCode.SHA256_MISMATCH)
    }

    @Test
    fun acceptsMissingContentLengthAndStillVerifiesTheStream() {
        val bytes = "unknown-length".repeat(100).toByteArray()
        val result = downloader(
            temporaryDirectory(),
            QueueTransport(FakeResponse(200, bytes, contentLengthBytes = null)),
        ).download(ModelDownloadRequest(artifact(bytes)))

        assertTrue(result is ModelDownloadResult.Success)
    }

    @Test
    fun cancellationDuringStreamingPurgesThePartialFile() {
        val root = temporaryDirectory()
        val bytes = ByteArray(200_000) { index -> (index % 251).toByte() }
        val cancelled = AtomicBoolean(false)
        val response = CancellingResponse(bytes) { cancelled.set(true) }
        val result = downloader(root, QueueTransport(response)).download(
            ModelDownloadRequest(artifact(bytes)),
            cancellationToken = DownloadCancellationToken(cancelled::get),
        )

        assertTrue(result is ModelDownloadResult.Cancelled)
        assertTrue(File(root, "partials").listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "verified").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun retriesRetryableFailuresAndCancelsDuringBackoff() {
        val bytes = "retry-model".repeat(100).toByteArray()
        val transport = QueueTransport(
            IOException("offline"),
            FakeResponse(503),
            FakeResponse(200, bytes),
        )
        val result = downloader(
            temporaryDirectory(),
            transport,
            retryPolicy = DownloadRetryPolicy(
                maxAttempts = 3,
                initialDelayMs = 0,
                maxDelayMs = 0,
                jitterRatio = 0.0,
            ),
        ).download(ModelDownloadRequest(artifact(bytes)))
        assertTrue(result is ModelDownloadResult.Success)
        assertEquals(3, transport.requestedUris.size)

        val cancelled = AtomicBoolean(false)
        val duringBackoff = downloader(
            root = temporaryDirectory(),
            transport = QueueTransport(IOException("offline"), FakeResponse(200, bytes)),
            sleeper = DownloadSleeper { cancelled.set(true) },
        ).download(
            ModelDownloadRequest(artifact(bytes)),
            cancellationToken = DownloadCancellationToken(cancelled::get),
        )
        assertTrue(duringBackoff is ModelDownloadResult.Cancelled)
        assertEquals(
            DownloadFailureCode.DOWNLOAD_CANCELLED,
            (duringBackoff as ModelDownloadResult.Cancelled).failure.code,
        )
    }

    @Test
    fun rejectsInsufficientStorageBeforeOpeningTheNetwork() {
        val bytes = "large-model".repeat(100).toByteArray()
        val transport = QueueTransport(FakeResponse(200, bytes))
        val result = downloader(
            root = temporaryDirectory(),
            transport = transport,
            storageProbe = DownloadStorageProbe { bytes.size.toLong() },
        ).download(ModelDownloadRequest(artifact(bytes)))

        assertFailure(result, DownloadFailureCode.INSUFFICIENT_STORAGE)
        assertTrue(transport.requestedUris.isEmpty())
    }

    @Test
    fun duplicateConcurrentRequestDoesNotOpenAnotherConnection() {
        val root = temporaryDirectory()
        val bytes = "blocking-model".repeat(500).toByteArray()
        val enteredRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val transport = BlockingTransport(bytes, enteredRead, releaseRead)
        val downloader = downloader(root, transport)
        val executor = Executors.newSingleThreadExecutor()
        val first = executor.submit<ModelDownloadResult> {
            downloader.download(ModelDownloadRequest(artifact(bytes)))
        }
        assertTrue(enteredRead.await(5, TimeUnit.SECONDS))

        val duplicate = downloader.download(ModelDownloadRequest(artifact(bytes)))
        assertTrue(duplicate is ModelDownloadResult.AlreadyRunning)
        assertEquals(1, transport.openCount)
        val journalText = File(root, "operations").listFiles().orEmpty().single().readText()
        assertTrue("huggingface.co" in journalText)
        assertTrue("token" !in journalText)
        assertTrue("private" !in journalText)

        releaseRead.countDown()
        assertTrue(first.get(5, TimeUnit.SECONDS) is ModelDownloadResult.Success)
        executor.shutdownNow()
    }

    @Test
    fun recoveryPurgesInterruptedPartialWithoutAStoredUrl() {
        val root = temporaryDirectory()
        val operations = File(root, "operations").apply { mkdirs() }
        val partials = File(root, "partials").apply { mkdirs() }
        File(root, "verified").mkdirs()
        val operationId = "11111111-1111-1111-1111-111111111111"
        val partial = File(partials, "model-interrupted.part").apply { writeText("partial") }
        val digest = "a".repeat(64)
        val properties = Properties().apply {
            setProperty("operationId", operationId)
            setProperty("digest", digest)
            setProperty("expectedBytes", "100")
            setProperty("sourceHost", "huggingface.co")
            setProperty("partialFile", partial.name)
            setProperty("startedAtEpochMs", "123")
            setProperty("attempt", "1")
        }
        File(operations, "$operationId.properties").outputStream().use {
            properties.store(it, null)
        }

        val report = downloader(root, QueueTransport()).recoverInterruptedDownloads()

        assertEquals(1, report.interrupted.size)
        assertEquals(ModelDigest(digest), report.interrupted.single().digest)
        assertFalse(partial.exists())
        assertTrue(operations.listFiles().orEmpty().isEmpty())
    }

    private fun assertDownloadFailure(
        expected: ByteArray,
        received: ByteArray,
        code: DownloadFailureCode,
    ) {
        val result = downloader(
            temporaryDirectory(),
            QueueTransport(FakeResponse(200, received, contentLengthBytes = null)),
        ).download(ModelDownloadRequest(artifact(expected)))
        assertFailure(result, code)
    }

    private fun downloader(
        root: File,
        transport: DownloadTransport,
        storageProbe: DownloadStorageProbe = DownloadStorageProbe { Long.MAX_VALUE },
        retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(
            maxAttempts = 2,
            initialDelayMs = 0,
            maxDelayMs = 0,
            jitterRatio = 0.0,
        ),
        sleeper: DownloadSleeper = DownloadSleeper {},
    ): SecureModelDownloader = SecureModelDownloader(
        rootDirectory = root,
        sourcePolicy = AllowlistedHttpsSourcePolicy(
            setOf(
                AllowedSourceHost("huggingface.co"),
                AllowedSourceHost("cdn.example.com"),
            ),
        ),
        transport = transport,
        networkPolicy = ArtifactNetworkPolicy { NetworkAddressPolicyResult.Allowed },
        configuration = DownloadConfiguration(
            storageHeadroomBytes = 16,
            progressMinBytesDelta = 1,
            progressMinIntervalMs = 0,
            retryPolicy = retryPolicy,
        ),
        clock = DownloadClock { 1_000L },
        sleeper = sleeper,
        jitterSource = DownloadJitterSource { 0.5 },
        storageProbe = storageProbe,
    )

    private fun artifact(
        bytes: ByteArray,
        uri: String = "https://huggingface.co/model.gguf?download=true&token=private",
    ): CatalogGgufArtifact = CatalogGgufArtifact(
        digest = ModelDigest(sha256(bytes)),
        sizeBytes = bytes.size.toLong(),
        downloadUri = URI(uri),
        architecture = "llama",
        quantization = "Q4_K_M",
        fileName = "server-controlled-name.gguf",
    )

    private fun assertFailure(result: ModelDownloadResult, code: DownloadFailureCode) {
        assertTrue(result is ModelDownloadResult.Failure)
        assertEquals(code, (result as ModelDownloadResult.Failure).failure.code)
    }

    private fun temporaryDirectory(): File =
        Files.createTempDirectory("secure-downloader-test-").toFile()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private class QueueTransport(vararg responses: Any) : DownloadTransport {
        private val queue = ArrayDeque(responses.toList())
        val requestedUris = mutableListOf<URI>()

        override fun execute(request: DownloadTransportRequest): DownloadTransportResponse {
            requestedUris += request.uri
            return when (val next = queue.removeFirst()) {
                is IOException -> throw next
                is DownloadTransportResponse -> next
                else -> error("Unsupported fake transport response")
            }
        }
    }

    private class FakeResponse(
        override val statusCode: Int,
        private val body: ByteArray = byteArrayOf(),
        override val contentLengthBytes: Long? = body.size.toLong(),
        override val contentEncoding: String? = null,
        override val redirectLocation: String? = null,
    ) : DownloadTransportResponse {
        override fun openBody(): InputStream = ByteArrayInputStream(body)
        override fun close() = Unit
    }

    private class CancellingResponse(
        private val body: ByteArray,
        private val afterFirstRead: () -> Unit,
    ) : DownloadTransportResponse {
        override val statusCode: Int = 200
        override val contentLengthBytes: Long = body.size.toLong()
        override val contentEncoding: String? = null
        override val redirectLocation: String? = null

        override fun openBody(): InputStream = object : ByteArrayInputStream(body) {
            private var firstRead = true

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length.coerceAtMost(32_768))
                if (firstRead && count > 0) {
                    firstRead = false
                    afterFirstRead()
                }
                return count
            }
        }

        override fun close() = Unit
    }

    private class BlockingTransport(
        private val body: ByteArray,
        private val enteredRead: CountDownLatch,
        private val releaseRead: CountDownLatch,
    ) : DownloadTransport {
        @Volatile
        var openCount: Int = 0
            private set

        override fun execute(request: DownloadTransportRequest): DownloadTransportResponse {
            openCount += 1
            return object : DownloadTransportResponse {
                override val statusCode: Int = 200
                override val contentLengthBytes: Long = body.size.toLong()
                override val contentEncoding: String? = null
                override val redirectLocation: String? = null

                override fun openBody(): InputStream = object : InputStream() {
                    private var delegate: InputStream? = null
                    private val opened = AtomicBoolean(false)

                    private fun ensureOpened() {
                        if (opened.compareAndSet(false, true)) {
                            enteredRead.countDown()
                            assertTrue(releaseRead.await(5, TimeUnit.SECONDS))
                            delegate = ByteArrayInputStream(body)
                        }
                    }

                    override fun read(): Int {
                        ensureOpened()
                        return delegate!!.read()
                    }

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        ensureOpened()
                        return delegate!!.read(buffer, offset, length)
                    }
                }

                override fun close() = Unit
            }
        }
    }
}
