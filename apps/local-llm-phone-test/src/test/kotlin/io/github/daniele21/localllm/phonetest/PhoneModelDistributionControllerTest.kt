package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogAvailability
import io.github.daniele21.localllm.catalog.CatalogCompatibility
import io.github.daniele21.localllm.catalog.CatalogCompatibilityEvaluator
import io.github.daniele21.localllm.catalog.CatalogDeviceProfile
import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.catalog.CatalogId
import io.github.daniele21.localllm.catalog.CatalogLicense
import io.github.daniele21.localllm.catalog.CatalogModelDocument
import io.github.daniele21.localllm.catalog.CatalogModelId
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogModelVersion
import io.github.daniele21.localllm.catalog.CatalogProfileResolver
import io.github.daniele21.localllm.catalog.CatalogReleaseId
import io.github.daniele21.localllm.catalog.CatalogTarget
import io.github.daniele21.localllm.catalog.CatalogVersionMatcher
import io.github.daniele21.localllm.catalog.ModelProfileKey
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.download.DownloadCancellationToken
import io.github.daniele21.localllm.download.DownloadFailure
import io.github.daniele21.localllm.download.DownloadFailureCode
import io.github.daniele21.localllm.download.DownloadOperationId
import io.github.daniele21.localllm.download.DownloadProgress
import io.github.daniele21.localllm.download.DownloadProgressObserver
import io.github.daniele21.localllm.download.DownloadStage
import io.github.daniele21.localllm.download.ModelDownloadResult
import io.github.daniele21.localllm.download.VerifiedDownloadHandle
import io.github.daniele21.localllm.install.GgufArtifactMetadata
import io.github.daniele21.localllm.install.InstalledModelDescriptor
import io.github.daniele21.localllm.install.ModelInstallationId
import io.github.daniele21.localllm.install.ModelInstallationObserver
import io.github.daniele21.localllm.install.ModelInstallationRequest
import io.github.daniele21.localllm.install.ModelInstallationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class PhoneModelDistributionControllerTest {
    @Test
    fun exposesCompatibleCatalogThenVerifiedAndInstalledStates() {
        val metadataRepository = InMemoryInstalledMetadataRepository()
        val installedDigests = mutableSetOf<ModelDigest>()
        val discarded = mutableListOf<VerifiedDownloadHandle>()
        var latest = PhoneModelDistributionState()
        val controller =
            controller(
                executor = ImmediateExecutorService(),
                metadataRepository = metadataRepository,
                installedDigests = installedDigests,
                downloadGateway = successfulDownloadGateway(),
                installGateway = PhoneModelInstallGateway { request, _ ->
                    installedDigests += request.release.artifact.digest
                    successfulInstallation(request)
                },
                discard = { handle ->
                    discarded += handle
                    true
                },
                listener = { latest = it },
            )

        val initial = latest.models.single()
        assertTrue(initial.compatible)
        assertEquals(PhoneCatalogModelStatus.READY_TO_DOWNLOAD, initial.status)

        controller.download(initial.stableId)

        val verified = latest.models.single()
        assertEquals(PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL, verified.status)
        assertEquals(RELEASE.artifact.sizeBytes, verified.bytesDownloaded)

        controller.install(initial.stableId)

        val installed = latest.models.single()
        assertEquals(PhoneCatalogModelStatus.INSTALLED, installed.status)
        assertNotNull(installed.installedModel)
        assertEquals(RELEASE.profileKey.value, installed.installedModel?.profileKey)
        assertEquals(listOf(VerifiedDownloadHandle(DIGEST.sha256)), discarded)
        assertEquals(1, metadataRepository.loadAll().size)
        controller.close()
    }

    @Test
    fun cancellationTransitionsDownloadToCancelledWithoutInstallMetadata() {
        val executor = ManualExecutorService()
        val metadataRepository = InMemoryInstalledMetadataRepository()
        var latest = PhoneModelDistributionState()
        val controller =
            controller(
                executor = executor,
                metadataRepository = metadataRepository,
                installedDigests = mutableSetOf(),
                downloadGateway = PhoneModelDownloadGateway { _, _, cancellationToken ->
                    if (cancellationToken.isCancelled()) {
                        ModelDownloadResult.Cancelled(
                            DownloadOperationId("download"),
                            DownloadFailure(
                                DownloadFailureCode.DOWNLOAD_CANCELLED,
                                retryable = false,
                                detail = "cancelled",
                            ),
                        )
                    } else {
                        error("Cancellation token was not observed")
                    }
                },
                installGateway = PhoneModelInstallGateway { _, _ -> error("Install must not run") },
                discard = { false },
                listener = { latest = it },
            )
        val stableId = latest.models.single().stableId

        controller.download(stableId)
        assertEquals(PhoneCatalogModelStatus.DOWNLOADING, latest.models.single().status)
        controller.cancelDownload(stableId)
        executor.runAll()

        assertEquals(PhoneCatalogModelStatus.CANCELLED, latest.models.single().status)
        assertTrue(metadataRepository.loadAll().isEmpty())
        controller.close()
    }

    @Test
    fun reconcilesStaleMetadataWhenModelStoreNoLongerContainsDigest() {
        val metadataRepository = InMemoryInstalledMetadataRepository().apply {
            save(InstalledCatalogModelMetadata.from(RELEASE, TARGET, 1L))
        }
        var latest = PhoneModelDistributionState()

        val controller =
            controller(
                executor = ImmediateExecutorService(),
                metadataRepository = metadataRepository,
                installedDigests = mutableSetOf(),
                downloadGateway = successfulDownloadGateway(),
                installGateway = PhoneModelInstallGateway { _, _ -> error("Install must not run") },
                discard = { false },
                listener = { latest = it },
            )

        assertEquals(PhoneCatalogModelStatus.READY_TO_DOWNLOAD, latest.models.single().status)
        assertTrue(metadataRepository.loadAll().isEmpty())
        controller.close()
    }

    @Test
    fun verificationAndConfirmedRemovalPublishExplicitManagementStates() {
        val metadataRepository = InMemoryInstalledMetadataRepository().apply {
  save(InstalledCatalogModelMetadata.from(RELEASE, TARGET, 1L))
        }
        val installedDigests = mutableSetOf(DIGEST)
        var latest = PhoneModelDistributionState()
        val controller = controller(
  executor = ImmediateExecutorService(),
  metadataRepository = metadataRepository,
  installedDigests = installedDigests,
  downloadGateway = successfulDownloadGateway(),
  installGateway = PhoneModelInstallGateway { _, _ -> error("Install must not run") },
  discard = { false },
  listener = { latest = it },
        )
        val stableId = latest.models.single().stableId

        controller.verifyInstalled(stableId)
        assertEquals("Model integrity verified", latest.models.single().detail)

        controller.requestRemove(stableId)
        assertTrue(latest.models.single().removalConfirmationPending)
        assertTrue(installedDigests.contains(DIGEST))

        controller.confirmRemove(stableId)
        assertFalse(latest.models.single().removalConfirmationPending)
        assertEquals(PhoneCatalogModelStatus.READY_TO_DOWNLOAD, latest.models.single().status)
        assertFalse(installedDigests.contains(DIGEST))
        assertTrue(metadataRepository.loadAll().isEmpty())
        controller.close()
    }

    private fun controller(
        executor: AbstractExecutorService,
        metadataRepository: InstalledCatalogMetadataRepository,
        installedDigests: MutableSet<ModelDigest>,
        downloadGateway: PhoneModelDownloadGateway,
        installGateway: PhoneModelInstallGateway,
        discard: (VerifiedDownloadHandle) -> Boolean,
        listener: (PhoneModelDistributionState) -> Unit,
    ): PhoneModelDistributionController = PhoneModelDistributionController(
        environment =
        PhoneModelDistributionEnvironment(
            catalog = DOCUMENT,
            target = TARGET,
            compatibilityEvaluator =
            CatalogCompatibilityEvaluator(
                versionMatcher = object : CatalogVersionMatcher {
                    override fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean = true
                },
                profileResolver = object : CatalogProfileResolver {
                    override fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean =
                        profileKey == PROFILE_KEY && target == TARGET
                },
            ),
            deviceProfile =
            CatalogDeviceProfile(
                sdkInt = 36,
                supportedAbis = setOf("arm64-v8a"),
                totalMemoryBytes = 8_000_000_000L,
                availableStorageBytes = 4_000_000_000L,
                harnessVersion = "1.0.0",
                backendId = "llama.cpp",
            ),
        ),
        services =
        PhoneModelDistributionServices(
            downloader = downloadGateway,
            installer = installGateway,
            discardVerifiedDownload = discard,
            metadataRepository = metadataRepository,
            modelExists = installedDigests::contains,
            clock = { NOW },
        ),
        listener = PhoneModelDistributionListener(listener),
        executor = executor,
    )

    private fun successfulDownloadGateway(): PhoneModelDownloadGateway = PhoneModelDownloadGateway { release, observer, cancellationToken ->
        assertFalse(cancellationToken.isCancelled())
        observer.onProgress(
            DownloadProgress(
                operationId = DownloadOperationId("download"),
                digestPrefix = release.artifact.digest.sha256.take(12),
                sourceHost = "models.example",
                stage = DownloadStage.DOWNLOADING,
                bytesDownloaded = release.artifact.sizeBytes / 2,
                expectedBytes = release.artifact.sizeBytes,
                attempt = 1,
            ),
        )
        ModelDownloadResult.Success(
            operationId = DownloadOperationId("download"),
            handle = VerifiedDownloadHandle(release.artifact.digest.sha256),
            digest = release.artifact.digest,
            sizeBytes = release.artifact.sizeBytes,
            sourceHost = "models.example",
            deduplicated = false,
        )
    }

    private fun successfulInstallation(request: ModelInstallationRequest): ModelInstallationResult.Success =
        ModelInstallationResult.Success(
            installationId = ModelInstallationId("install"),
            installed =
            InstalledModelDescriptor(
                digest = request.release.artifact.digest,
                sizeBytes = request.release.artifact.sizeBytes,
                releaseId = request.release.id,
                target = request.target,
                profileKey = request.release.profileKey,
                architecture = request.release.artifact.architecture,
                quantization = request.release.artifact.quantization,
                metadata = GgufArtifactMetadata(3u, "qwen35", "test", 15L),
            ),
            verifiedDownloadDiscarded = false,
        )

    private class InMemoryInstalledMetadataRepository : InstalledCatalogMetadataRepository {
        private val values = linkedMapOf<ModelDigest, InstalledCatalogModelMetadata>()

        override fun loadAll(): List<InstalledCatalogModelMetadata> = values.values.toList()

        override fun save(metadata: InstalledCatalogModelMetadata): Boolean {
            values[metadata.digest] = metadata
            return true
        }

        override fun remove(digest: ModelDigest): Boolean = values.remove(digest) != null
    }

    private class ImmediateExecutorService : AbstractExecutorService() {
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }

    private class ManualExecutorService : AbstractExecutorService() {
        private val commands = mutableListOf<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            commands += command
        }

        fun runAll() {
            val queued = commands.toList()
            commands.clear()
            queued.forEach(Runnable::run)
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val queued = commands.toMutableList()
            commands.clear()
            return queued
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && commands.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }

    private companion object {
        const val NOW = 1_000L
        val DIGEST = ModelDigest("a".repeat(64))
        val PROFILE_KEY = ModelProfileKey("test-profile")
        val TARGET =
            CatalogTarget(
                ApplicationId("play-internal-phone-test"),
                UseCaseId("manual-inference-playground"),
            )
        val RELEASE =
            CatalogModelRelease(
                id = CatalogReleaseId(CatalogModelId("test-model"), CatalogModelVersion("1.0.0")),
                displayName = "Test model",
                description = "Deterministic catalog model",
                artifact =
                CatalogGgufArtifact(
                    digest = DIGEST,
                    sizeBytes = 1_024L,
                    downloadUri = URI("https://models.example/test-model.gguf"),
                    architecture = "qwen35",
                    quantization = "Q4_K_M",
                    fileName = "test-model.gguf",
                ),
                compatibility =
                CatalogCompatibility(
                    minSdk = 26,
                    supportedAbis = setOf("arm64-v8a"),
                    supportedBackendIds = setOf("llama.cpp"),
                ),
                availability = CatalogAvailability.ACTIVE,
                allowedTargets = setOf(TARGET),
                profileKey = PROFILE_KEY,
                license = CatalogLicense("Apache-2.0", "Apache-2.0"),
            )
        val DOCUMENT =
            CatalogModelDocument(
                schemaVersion = 1,
                catalogId = CatalogId("test-catalog"),
                revision = 1L,
                generatedAtEpochMs = 0L,
                expiresAtEpochMs = 10_000L,
                entries = listOf(RELEASE),
            )
    }
}
