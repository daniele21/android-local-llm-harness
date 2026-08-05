#!/usr/bin/env python3
"""Refactor connected model distribution dependencies and metadata I/O."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/PhoneModelDistributionController.kt"
TEST = ROOT / "apps/local-llm-phone-test/src/test/kotlin/io/github/daniele21/localllm/phonetest/PhoneModelDistributionControllerTest.kt"
METADATA = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/InstalledCatalogModelMetadata.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label} match, found {count}")
    return text.replace(old, new, 1)


def refactor_controller() -> None:
    text = CONTROLLER.read_text(encoding="utf-8")
    anchor = '''internal fun interface PhoneModelInstallGateway {
    fun install(request: ModelInstallationRequest, observer: ModelInstallationObserver): ModelInstallationResult
}

'''
    additions = anchor + '''internal data class PhoneModelDistributionEnvironment(
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

'''
    text = replace_once(text, anchor, additions, "distribution dependency data classes")
    old_constructor = '''internal class PhoneModelDistributionController(
    private val catalog: CatalogModelDocument,
    private val target: CatalogTarget,
    private val compatibilityEvaluator: CatalogCompatibilityEvaluator,
    private val deviceProfile: CatalogDeviceProfile,
    private val downloader: PhoneModelDownloadGateway,
    private val installer: PhoneModelInstallGateway,
    private val discardVerifiedDownload: (VerifiedDownloadHandle) -> Boolean,
    private val metadataRepository: InstalledCatalogMetadataRepository,
    private val modelExists: (ModelDigest) -> Boolean,
    private val clock: () -> Long,
    private val listener: PhoneModelDistributionListener,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    private val lock = Any()
'''
    new_constructor = '''internal class PhoneModelDistributionController(
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
'''
    text = replace_once(text, old_constructor, new_constructor, "controller constructor")
    old_factory = '''            return PhoneModelDistributionController(
                catalog = catalog,
                target = TARGET,
                compatibilityEvaluator = evaluator,
                deviceProfile = appContext.catalogDeviceProfile(),
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
                    override fun install(request: ModelInstallationRequest, observer: ModelInstallationObserver): ModelInstallationResult =
                        installer.install(request, observer)
                },
                discardVerifiedDownload = verifiedAccess::discard,
                metadataRepository = metadataRepository,
                modelExists = { digest -> runtimeGraph.modelStore.find(digest)?.verified == true },
                clock = System::currentTimeMillis,
                listener = listener,
            )
'''
    new_factory = '''            return PhoneModelDistributionController(
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
'''
    text = replace_once(text, old_factory, new_factory, "controller factory")
    CONTROLLER.write_text(text, encoding="utf-8")


def refactor_test() -> None:
    text = TEST.read_text(encoding="utf-8")
    old = '''    ): PhoneModelDistributionController = PhoneModelDistributionController(
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
        downloader = downloadGateway,
        installer = installGateway,
        discardVerifiedDownload = discard,
        metadataRepository = metadataRepository,
        modelExists = installedDigests::contains,
        clock = { NOW },
        listener = PhoneModelDistributionListener(listener),
        executor = executor,
    )
'''
    new = '''    ): PhoneModelDistributionController = PhoneModelDistributionController(
        environment =
        PhoneModelDistributionEnvironment(
            catalog = DOCUMENT,
            target = TARGET,
            compatibilityEvaluator =
            CatalogCompatibilityEvaluator(
                versionMatcher = object : CatalogVersionMatcher {
                    override fun isInRange(
                        currentVersion: String,
                        minimumInclusive: String?,
                        maximumExclusive: String?,
                    ): Boolean = true
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
'''
    text = replace_once(text, old, new, "test controller construction")
    TEST.write_text(text, encoding="utf-8")


def refactor_metadata() -> None:
    text = METADATA.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''            moveIntoPlace(temporary, destination)
            true
''',
        '''            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
''',
        "metadata atomic move",
    )
    old_method = '''    private fun moveIntoPlace(source: File, destination: File) {
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

'''
    text = replace_once(text, old_method, "", "metadata move helper")
    METADATA.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    refactor_controller()
    refactor_test()
    refactor_metadata()
