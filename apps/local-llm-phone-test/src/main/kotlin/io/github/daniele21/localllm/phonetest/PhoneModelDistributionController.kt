@file:Suppress("TooManyFunctions")

package io.github.daniele21.localllm.phonetest

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.github.daniele21.localllm.catalog.CatalogCompatibilityEvaluator
import io.github.daniele21.localllm.catalog.CatalogDeviceProfile
import io.github.daniele21.localllm.catalog.CatalogModelDocument
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogProfileResolver
import io.github.daniele21.localllm.catalog.CatalogQueries
import io.github.daniele21.localllm.catalog.CatalogTarget
import io.github.daniele21.localllm.catalog.CatalogValidator
import io.github.daniele21.localllm.catalog.CatalogVersionMatcher
import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.catalog.ModelProfileKey
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.download.AllowedSourceHost
import io.github.daniele21.localllm.download.AllowlistedHttpsSourcePolicy
import io.github.daniele21.localllm.download.DownloadCancellationToken
import io.github.daniele21.localllm.download.DownloadProgress
import io.github.daniele21.localllm.download.DownloadProgressObserver
import io.github.daniele21.localllm.download.FileSystemVerifiedDownloadAccess
import io.github.daniele21.localllm.download.ModelDownloadRequest
import io.github.daniele21.localllm.download.ModelDownloadResult
import io.github.daniele21.localllm.download.SecureModelDownloader
import io.github.daniele21.localllm.download.VerifiedDownloadHandle
import io.github.daniele21.localllm.install.ModelInstallationObserver
import io.github.daniele21.localllm.install.ModelInstallationRequest
import io.github.daniele21.localllm.install.ModelInstallationResult
import io.github.daniele21.localllm.install.ResolvedInstallationProfile
import io.github.daniele21.localllm.install.VerifiedDownloadRetentionPolicy
import io.github.daniele21.localllm.install.VerifiedModelInstaller
import io.github.daniele21.localllm.llamacpp.LlamaCppGgufArtifactInspector
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal enum class PhoneCatalogLoadStatus {
    LOADING,
    READY,
    FAILED,
}

internal enum class PhoneCatalogModelStatus {
    INCOMPATIBLE,
    READY_TO_DOWNLOAD,
    DOWNLOADING,
    VERIFIED_READY_TO_INSTALL,
    INSTALLING,
    INSTALLED,
    CANCELLED,
    FAILED,
}

internal data class PhoneCatalogModelUi(
    val stableId: String,
    val displayName: String,
    val description: String,
    val fileName: String,
    val sizeBytes: Long,
    val architecture: String,
    val quantization: String,
    val profileKey: String,
    val licenseName: String,
    val status: PhoneCatalogModelStatus,
    val compatible: Boolean,
    val compatibilityReasons: List<String>,
    val compatibilityWarnings: List<String>,
    val bytesDownloaded: Long = 0L,
    val expectedBytes: Long = sizeBytes,
    val detail: String? = null,
    val installedModel: InstalledCatalogModelMetadata? = null,
)

internal data class PhoneModelDistributionState(
    val catalogStatus: PhoneCatalogLoadStatus = PhoneCatalogLoadStatus.LOADING,
    val catalogRevision: Long? = null,
    val sourceLabel: String = "Administrator-curated bootstrap catalog",
    val models: List<PhoneCatalogModelUi> = emptyList(),
    val message: String = "Loading model catalog…",
    val operationActive: Boolean = false,
)

internal fun interface PhoneModelDistributionListener {
    fun onStateChanged(state: PhoneModelDistributionState)
}

internal fun interface PhoneModelDownloadGateway {
    fun download(
        release: CatalogModelRelease,
        observer: DownloadProgressObserver,
        cancellationToken: DownloadCancellationToken,
    ): ModelDownloadResult
}

internal fun interface PhoneModelInstallGateway {
    fun install(request: ModelInstallationRequest, observer: ModelInstallationObserver): ModelInstallationResult
}

internal data class PhoneModelDistributionEnvironment(
    val catalog: CatalogModelDocument,
    val target: CatalogTarget,
    val compatibilityEvaluator: CatalogCompatibilityEvaluator,
    val deviceProfile: CatalogDeviceProfile,
)

internal data class PhoneModelDistributionServices(
    val downloader: PhoneModelDownloadGateway,
    val installer: PhoneModelInstallGateway,
    val discardVerifiedDownload: (VerifiedDownloadHandle) -> Boolean,
    val metadataRepository: InstalledCatalogMetadataRepository,
    val modelExists: (ModelDigest) -> Boolean,
    val clock: () -> Long,
)

internal class PhoneModelDistributionController(
    environment: PhoneModelDistributionEnvironment,
    services: PhoneModelDistributionServices,
    private val listener: PhoneModelDistributionListener,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    private val catalog = environment.catalog
    private val target = environment.target
    private val compatibilityEvaluator = environment.compatibilityEvaluator
    private val deviceProfile = environment.deviceProfile
    private val downloader = services.downloader
    private val installer = services.installer
    private val discardVerifiedDownload = services.discardVerifiedDownload
    private val metadataRepository = services.metadataRepository
    private val modelExists = services.modelExists
    private val clock = services.clock
    private val lock = Any()
    private val releases = CatalogQueries.releasesForTarget(catalog, target).associateBy(::stableId)
    private val compatibility = releases.mapValues { (_, release) ->
        compatibilityEvaluator.evaluate(release, target, deviceProfile)
    }
    private val pendingDownloads = mutableMapOf<String, VerifiedDownloadHandle>()
    private val operationStates = mutableMapOf<String, RuntimeModelState>()

    private var activeOperation: ActiveOperation? = null
    private var installed = reconcileInstalledMetadata()
    private var state = PhoneModelDistributionState()

    init {
        state = loadInitialState()
        listener.onStateChanged(state)
    }

    fun snapshot(): PhoneModelDistributionState = synchronized(lock) { state }

    fun refresh() {
        val next = synchronized(lock) {
            installed = reconcileInstalledMetadata()
            buildState("Catalog and installed models refreshed")
        }
        listener.onStateChanged(next)
    }

    fun download(stableId: String) {
        val release = releases[stableId] ?: return
        val cancellation = AtomicBoolean(false)
        val initialState = synchronized(lock) {
            if (activeOperation != null) return@synchronized null
            val compatibilityResult = compatibility.getValue(stableId)
            if (!compatibilityResult.compatible || installed.containsKey(release.artifact.digest)) {
                return@synchronized null
            }
            activeOperation = ActiveOperation(stableId, cancellation)
            operationStates[stableId] = RuntimeModelState(
                status = PhoneCatalogModelStatus.DOWNLOADING,
                detail = "Preparing secure download",
            )
            publishLocked("Downloading ${release.displayName}")
        } ?: return
        listener.onStateChanged(initialState)

        executor.execute {
            val result = downloader.download(
                release = release,
                observer = DownloadProgressObserver { progress -> onDownloadProgress(stableId, progress) },
                cancellationToken = DownloadCancellationToken(cancellation::get),
            )
            val next = synchronized(lock) {
                when (result) {
                    is ModelDownloadResult.Success -> {
                        pendingDownloads[stableId] = result.handle
                        operationStates[stableId] = RuntimeModelState(
                            status = PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL,
                            bytesDownloaded = result.sizeBytes,
                            expectedBytes = result.sizeBytes,
                            detail = if (result.deduplicated) {
                                "Verified download already available"
                            } else {
                                "Download verified. Ready to install."
                            },
                        )
                        activeOperation = null
                        buildState("${release.displayName} is verified and ready to install")
                    }

                    is ModelDownloadResult.Cancelled -> {
                        operationStates[stableId] = RuntimeModelState(
                            status = PhoneCatalogModelStatus.CANCELLED,
                            detail = "Download cancelled",
                        )
                        activeOperation = null
                        buildState("Download cancelled")
                    }

                    is ModelDownloadResult.AlreadyRunning -> {
                        operationStates[stableId] = RuntimeModelState(
                            status = PhoneCatalogModelStatus.FAILED,
                            detail = "Another download for this artifact is already running",
                        )
                        activeOperation = null
                        buildState("Download could not start")
                    }

                    is ModelDownloadResult.Failure -> {
                        operationStates[stableId] = RuntimeModelState(
                            status = PhoneCatalogModelStatus.FAILED,
                            detail = result.failure.code.name,
                        )
                        activeOperation = null
                        buildState("Download failed: ${result.failure.code.name}")
                    }
                }
            }
            listener.onStateChanged(next)
        }
    }

    fun cancelDownload(stableId: String) {
        val next = synchronized(lock) {
            val active = activeOperation
            if (active == null || active.stableId != stableId) return@synchronized null
            active.cancellation.set(true)
            operationStates[stableId] = operationStates[stableId].orEmpty().copy(
                detail = "Cancellation requested",
            )
            publishLocked("Cancelling download…")
        }
        next?.let(listener::onStateChanged)
    }

    fun install(stableId: String) {
        val release = releases[stableId] ?: return
        val start = synchronized(lock) {
            if (activeOperation != null) return@synchronized null
            val pending = pendingDownloads[stableId] ?: return@synchronized null
            activeOperation = ActiveOperation(stableId, AtomicBoolean(false))
            operationStates[stableId] = RuntimeModelState(
                status = PhoneCatalogModelStatus.INSTALLING,
                detail = "Validating GGUF metadata",
            )
            pending to publishLocked("Installing ${release.displayName}")
        } ?: return

        val handle = start.first
        listener.onStateChanged(start.second)

        executor.execute {
            val request = ModelInstallationRequest(
                handle = handle,
                release = release,
                target = target,
                profile = ResolvedInstallationProfile(
                    key = release.profileKey,
                    artifact = release.toProfileArtifact(),
                ),
                retentionPolicy = VerifiedDownloadRetentionPolicy.RETAIN,
            )
            val result = installer.install(
                request,
                ModelInstallationObserver { progress ->
                    val next = synchronized(lock) {
                        operationStates[stableId] = operationStates[stableId].orEmpty().copy(
                            status = PhoneCatalogModelStatus.INSTALLING,
                            detail = progress.stage.name.replace('_', ' ').lowercase(),
                        )
                        publishLocked("Installing ${release.displayName}")
                    }
                    listener.onStateChanged(next)
                },
            )

            val next = synchronized(lock) {
                when (result) {
                    is ModelInstallationResult.Success -> {
                        val metadata = InstalledCatalogModelMetadata.from(release, target, clock())
                        if (!metadataRepository.save(metadata)) {
                            operationStates[stableId] = RuntimeModelState(
                                status = PhoneCatalogModelStatus.FAILED,
                                detail = "Model installed, but metadata persistence failed",
                            )
                            activeOperation = null
                            return@synchronized buildState(
                                "Installation completed but metadata could not be persisted",
                            )
                        }
                        installed[metadata.digest] = metadata
                        pendingDownloads.remove(stableId)
                        discardVerifiedDownload(handle)
                        operationStates.remove(stableId)
                        activeOperation = null
                        buildState("${release.displayName} installed successfully")
                    }

                    is ModelInstallationResult.Failure -> {
                        operationStates[stableId] = RuntimeModelState(
                            status = PhoneCatalogModelStatus.FAILED,
                            detail = result.code.name,
                        )
                        activeOperation = null
                        buildState("Installation failed: ${result.code.name}")
                    }
                }
            }
            listener.onStateChanged(next)
        }
    }

    override fun close() {
        synchronized(lock) { activeOperation?.cancellation?.set(true) }
        executor.shutdownNow()
    }

    private fun onDownloadProgress(stableId: String, progress: DownloadProgress) {
        val next = synchronized(lock) {
            operationStates[stableId] = RuntimeModelState(
                status = PhoneCatalogModelStatus.DOWNLOADING,
                bytesDownloaded = progress.bytesDownloaded,
                expectedBytes = progress.expectedBytes,
                detail = progress.stage.name.replace('_', ' ').lowercase(),
            )
            publishLocked("Downloading ${releases.getValue(stableId).displayName}")
        }
        listener.onStateChanged(next)
    }

    private fun loadInitialState(): PhoneModelDistributionState {
        val validation = CatalogValidator().validate(catalog, clock())
        return if (!validation.valid) {
            PhoneModelDistributionState(
                catalogStatus = PhoneCatalogLoadStatus.FAILED,
                catalogRevision = catalog.revision,
                message = "Catalog validation failed: ${validation.violations.first().code.name}",
            )
        } else {
            buildState("${releases.size} catalog models loaded")
        }
    }

    private fun reconcileInstalledMetadata(): MutableMap<ModelDigest, InstalledCatalogModelMetadata> {
        val valid = linkedMapOf<ModelDigest, InstalledCatalogModelMetadata>()
        metadataRepository.loadAll().forEach { metadata ->
            if (modelExists(metadata.digest)) {
                valid[metadata.digest] = metadata
            } else {
                metadataRepository.remove(metadata.digest)
            }
        }
        return valid
    }

    private fun buildState(message: String): PhoneModelDistributionState {
        val models = releases.map { (id, release) -> modelUi(id, release) }
        return PhoneModelDistributionState(
            catalogStatus = PhoneCatalogLoadStatus.READY,
            catalogRevision = catalog.revision,
            models = models,
            message = message,
            operationActive = activeOperation != null,
        ).also { state = it }
    }

    private fun publishLocked(message: String): PhoneModelDistributionState = buildState(message)

    private fun modelUi(stableId: String, release: CatalogModelRelease): PhoneCatalogModelUi {
        val result = compatibility.getValue(stableId)
        val installedMetadata = installed[release.artifact.digest]
        val runtime = operationStates[stableId]
        val status = when {
            installedMetadata != null -> PhoneCatalogModelStatus.INSTALLED
            runtime != null -> runtime.status
            !result.compatible -> PhoneCatalogModelStatus.INCOMPATIBLE
            pendingDownloads.containsKey(stableId) -> PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL
            else -> PhoneCatalogModelStatus.READY_TO_DOWNLOAD
        }
        return PhoneCatalogModelUi(
            stableId = stableId,
            displayName = release.displayName,
            description = release.description,
            fileName = release.artifact.fileName,
            sizeBytes = release.artifact.sizeBytes,
            architecture = release.artifact.architecture,
            quantization = release.artifact.quantization,
            profileKey = release.profileKey.value,
            licenseName = release.license.displayName,
            status = status,
            compatible = result.compatible,
            compatibilityReasons = result.reasons.map { it.name },
            compatibilityWarnings = result.warnings.map { it.name },
            bytesDownloaded = runtime?.bytesDownloaded ?: 0L,
            expectedBytes = runtime?.expectedBytes ?: release.artifact.sizeBytes,
            detail = runtime?.detail,
            installedModel = installedMetadata,
        )
    }

    private fun RuntimeModelState?.orEmpty(): RuntimeModelState =
        this ?: RuntimeModelState(status = PhoneCatalogModelStatus.READY_TO_DOWNLOAD)

    private fun CatalogModelRelease.toProfileArtifact(): GgufArtifact = GgufArtifact(
        digest = artifact.digest,
        fileName = artifact.fileName,
        sizeBytes = artifact.sizeBytes,
        architecture = artifact.architecture,
        quantization = artifact.quantization,
        source = ArtifactSource.Download("administrator-curated-catalog"),
    )

    private data class RuntimeModelState(
        val status: PhoneCatalogModelStatus,
        val bytesDownloaded: Long = 0L,
        val expectedBytes: Long = 0L,
        val detail: String? = null,
    )

    private data class ActiveOperation(val stableId: String, val cancellation: AtomicBoolean)

    companion object {
        private const val CATALOG_VALIDITY_MS = 365L * 24L * 60L * 60L * 1000L
        private const val DOWNLOAD_DIRECTORY = "verified-model-downloads"
        private const val INSTALL_STAGING_DIRECTORY = "model-install-staging"
        private const val METADATA_DIRECTORY = "installed-catalog-metadata"
        private val TARGET = CatalogTarget(
            ApplicationId("play-internal-phone-test"),
            UseCaseId("manual-inference-playground"),
        )

        fun from(
            context: Context,
            runtimeGraph: HarnessRuntimeGraph,
            listener: PhoneModelDistributionListener,
        ): PhoneModelDistributionController {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            val catalog = CuratedModelCatalog.document(now, safeCatalogExpiry(now))
            val profileKeys = CuratedModelCatalog.releases.mapTo(mutableSetOf()) { it.profileKey }
            val evaluator = CatalogCompatibilityEvaluator(
                versionMatcher = PhoneCatalogVersionMatcher,
                profileResolver = PhoneCatalogProfileResolver(TARGET, profileKeys),
            )
            val downloadRoot = File(appContext.noBackupFilesDir, DOWNLOAD_DIRECTORY)
            val verifiedAccess = FileSystemVerifiedDownloadAccess(downloadRoot)
            val secureDownloader = SecureModelDownloader(
                rootDirectory = downloadRoot,
                sourcePolicy = AllowlistedHttpsSourcePolicy(
                    setOf(
                        AllowedSourceHost("huggingface.co", includeSubdomains = true),
                        AllowedSourceHost("hf.co", includeSubdomains = true),
                    ),
                ),
            )
            secureDownloader.recoverInterruptedDownloads()
            val installer = VerifiedModelInstaller(
                stagingDirectory = File(appContext.cacheDir, INSTALL_STAGING_DIRECTORY),
                verifiedDownloads = verifiedAccess,
                inspector = LlamaCppGgufArtifactInspector(),
                modelStore = runtimeGraph.modelStore,
            )
            val metadataRepository = FileInstalledCatalogMetadataRepository(
                File(appContext.noBackupFilesDir, METADATA_DIRECTORY),
            )
            return PhoneModelDistributionController(
                environment =
                PhoneModelDistributionEnvironment(
                    catalog = catalog,
                    target = TARGET,
                    compatibilityEvaluator = evaluator,
                    deviceProfile = appContext.catalogDeviceProfile(),
                ),
                services =
                PhoneModelDistributionServices(
                    downloader = object : PhoneModelDownloadGateway {
                        override fun download(
                            release: CatalogModelRelease,
                            observer: DownloadProgressObserver,
                            cancellationToken: DownloadCancellationToken,
                        ): ModelDownloadResult = secureDownloader.download(
                            ModelDownloadRequest(release.artifact),
                            observer,
                            cancellationToken,
                        )
                    },
                    installer = object : PhoneModelInstallGateway {
                        override fun install(
                            request: ModelInstallationRequest,
                            observer: ModelInstallationObserver,
                        ): ModelInstallationResult = installer.install(request, observer)
                    },
                    discardVerifiedDownload = verifiedAccess::discard,
                    metadataRepository = metadataRepository,
                    modelExists = { digest -> runtimeGraph.modelStore.find(digest)?.verified == true },
                    clock = System::currentTimeMillis,
                ),
                listener = listener,
            )
        }

        private fun stableId(release: CatalogModelRelease): String = "${release.id.modelId.value}@${release.id.version.value}"

        private fun safeCatalogExpiry(now: Long): Long =
            runCatching { Math.addExact(now, CATALOG_VALIDITY_MS) }.getOrDefault(Long.MAX_VALUE)
    }
}

private object PhoneCatalogVersionMatcher : CatalogVersionMatcher {
    override fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean {
        val current = SemanticVersion.parse(currentVersion)
            ?: return minimumInclusive == null && maximumExclusive == null
        val minimum = minimumInclusive?.let(SemanticVersion::parse) ?: SemanticVersion.ZERO
        val maximum = maximumExclusive?.let(SemanticVersion::parse)
        return current >= minimum && (maximum == null || current < maximum)
    }
}

private class PhoneCatalogProfileResolver(
    private val supportedTarget: CatalogTarget,
    private val supportedProfileKeys: Set<ModelProfileKey>,
) : CatalogProfileResolver {
    override fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean =
        target == supportedTarget && profileKey in supportedProfileKeys
}

private data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        val ZERO = SemanticVersion(0, 0, 0)

        fun parse(value: String): SemanticVersion? {
            val parts = value.substringBefore('-').split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val values = parts.map { it.toIntOrNull() ?: return null }
            return SemanticVersion(
                major = values.getOrElse(0) { 0 },
                minor = values.getOrElse(1) { 0 },
                patch = values.getOrElse(2) { 0 },
            )
        }
    }
}

private fun Context.catalogDeviceProfile(): CatalogDeviceProfile {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val versionName = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "0.0.0" }
    return CatalogDeviceProfile(
        sdkInt = Build.VERSION.SDK_INT,
        supportedAbis = Build.SUPPORTED_ABIS.toSet(),
        totalMemoryBytes = memory.totalMem,
        availableStorageBytes = noBackupFilesDir.usableSpace,
        harnessVersion = versionName,
        backendId = "llama.cpp",
    )
}
