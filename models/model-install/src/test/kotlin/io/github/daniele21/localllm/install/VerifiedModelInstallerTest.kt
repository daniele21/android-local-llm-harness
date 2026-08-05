package io.github.daniele21.localllm.install

import io.github.daniele21.localllm.catalog.CatalogAvailability
import io.github.daniele21.localllm.catalog.CatalogCompatibility
import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.catalog.CatalogLicense
import io.github.daniele21.localllm.catalog.CatalogModelId
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogModelVersion
import io.github.daniele21.localllm.catalog.CatalogReleaseId
import io.github.daniele21.localllm.catalog.CatalogTarget
import io.github.daniele21.localllm.catalog.ModelProfileKey
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.download.VerifiedDownloadAccess
import io.github.daniele21.localllm.download.VerifiedDownloadAccessFailureCode
import io.github.daniele21.localllm.download.VerifiedDownloadCopyRequest
import io.github.daniele21.localllm.download.VerifiedDownloadCopyResult
import io.github.daniele21.localllm.download.VerifiedDownloadHandle
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportErrorCode
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import java.io.File
import java.net.URI
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedModelInstallerTest {
    @Test
    fun installsVerifiedDownloadWithoutActivatingRuntimeState() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root)
        val stages = mutableListOf<ModelInstallationStage>()
        val installer =
            VerifiedModelInstaller(
                stagingDirectory = File(root, "install-staging"),
                verifiedDownloads = access,
                inspector = GgufArtifactInspector {
                    GgufArtifactInspectionResult.Success(
                        GgufArtifactMetadata(
                            version = 3u,
                            architecture = ARCHITECTURE,
                            name = "test-model",
                            fileType = 15L,
                        ),
                    )
                },
                modelStore = store,
            )

        val result = installer.install(request()) { stages += it.stage }

        result as ModelInstallationResult.Success
        assertEquals(DIGEST, result.installed.digest)
        assertEquals(MODEL_BYTES.size.toLong(), result.installed.sizeBytes)
        assertTrue(result.verifiedDownloadDiscarded)
        assertTrue(access.discarded)
        assertEquals(1, store.importCount)
        assertEquals(1, store.verifyCount)
        assertEquals(
            listOf(
                ModelInstallationStage.VALIDATING,
                ModelInstallationStage.STAGING,
                ModelInstallationStage.INSPECTING,
                ModelInstallationStage.IMPORTING,
                ModelInstallationStage.VERIFYING,
                ModelInstallationStage.COMPLETED,
            ),
            stages,
        )
        assertTrue(File(root, "install-staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rejectsProfileMismatchBeforeReadingVerifiedBytes() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root)
        val installer = installer(root, access, store)
        val invalid =
            request().copy(
                profile = request().profile.copy(key = ModelProfileKey("other-profile")),
            )

        val result = installer.install(invalid)

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.PROFILE_MISMATCH, result.code)
        assertEquals(0, access.copyCount)
        assertEquals(0, store.importCount)
    }

    @Test
    fun rejectsTargetNotAllowedBeforeReadingVerifiedBytes() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root)
        val disallowed =
            CatalogTarget(
                applicationId = ApplicationId("other-app"),
                useCaseId = UseCaseId("manual"),
            )

        val result = installer(root, access, store).install(request().copy(target = disallowed))

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.TARGET_NOT_ALLOWED, result.code)
        assertEquals(0, access.copyCount)
        assertEquals(0, store.importCount)
    }

    @Test
    fun rejectsUnavailableVerifiedDownloadWithoutImporting() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access =
            FakeVerifiedDownloadAccess(
                bytes = MODEL_BYTES,
                failure = VerifiedDownloadAccessFailureCode.VERIFIED_DOWNLOAD_MISSING,
            )
        val store = FakeModelStore(root)

        val result = installer(root, access, store).install(request())

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.VERIFIED_DOWNLOAD_UNAVAILABLE, result.code)
        assertEquals(0, store.importCount)
    }

    @Test
    fun rejectsArchitectureMismatchAndRetainsVerifiedDownload() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root)
        val installer =
            VerifiedModelInstaller(
                File(root, "staging"),
                access,
                GgufArtifactInspector {
                    GgufArtifactInspectionResult.Success(
                        GgufArtifactMetadata(3u, "llama", "wrong", 15L),
                    )
                },
                store,
            )

        val result = installer.install(request())

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.ARCHITECTURE_MISMATCH, result.code)
        assertFalse(access.discarded)
        assertEquals(0, store.importCount)
    }

    @Test
    fun mapsModelStoreImportFailureAndRetainsVerifiedDownload() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root, importFailure = true)

        val result = installer(root, access, store).install(request())

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.MODEL_STORE_IMPORT_FAILED, result.code)
        assertFalse(access.discarded)
        assertEquals(0, store.verifyCount)
    }

    @Test
    fun removesImportedModelWhenPostImportVerificationFails() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root, verificationValid = false)

        val result = installer(root, access, store).install(request())

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.POST_IMPORT_VERIFICATION_FAILED, result.code)
        assertEquals(1, store.removeCount)
        assertFalse(access.discarded)
    }

    @Test
    fun removesImportedModelWhenPostImportVerificationThrows() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root, verificationFailure = true)

        val result = installer(root, access, store).install(request())

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.POST_IMPORT_VERIFICATION_FAILED, result.code)
        assertEquals(1, store.removeCount)
        assertFalse(access.discarded)
    }

    @Test
    fun rejectsRevokedReleaseBeforeStaging() {
        val root = Files.createTempDirectory("model-install-test").toFile()
        val access = FakeVerifiedDownloadAccess(MODEL_BYTES)
        val store = FakeModelStore(root)
        val revoked = request().copy(release = release().copy(availability = CatalogAvailability.REVOKED))

        val result = installer(root, access, store).install(revoked)

        result as ModelInstallationResult.Failure
        assertEquals(ModelInstallationFailureCode.RELEASE_UNAVAILABLE, result.code)
        assertEquals(0, access.copyCount)
    }

    private fun installer(
        root: File,
        access: VerifiedDownloadAccess,
        store: ModelStore,
    ): VerifiedModelInstaller =
        VerifiedModelInstaller(
            File(root, "staging"),
            access,
            GgufArtifactInspector {
                GgufArtifactInspectionResult.Success(
                    GgufArtifactMetadata(3u, ARCHITECTURE, "test", 15L),
                )
            },
            store,
        )

    private fun request(): ModelInstallationRequest =
        ModelInstallationRequest(
            handle = VerifiedDownloadHandle(DIGEST.sha256),
            release = release(),
            target = TARGET,
            profile =
                ResolvedInstallationProfile(
                    key = PROFILE_KEY,
                    artifact =
                        GgufArtifact(
                            digest = DIGEST,
                            fileName = FILE_NAME,
                            sizeBytes = MODEL_BYTES.size.toLong(),
                            architecture = ARCHITECTURE,
                            quantization = QUANTIZATION,
                            source = ArtifactSource.Download("catalog"),
                        ),
                ),
        )

    private fun release(): CatalogModelRelease =
        CatalogModelRelease(
            id = CatalogReleaseId(CatalogModelId("test-model"), CatalogModelVersion("1.0.0")),
            displayName = "Test model",
            description = "Test release",
            artifact =
                CatalogGgufArtifact(
                    digest = DIGEST,
                    sizeBytes = MODEL_BYTES.size.toLong(),
                    downloadUri = URI("https://models.example/model.gguf"),
                    architecture = ARCHITECTURE,
                    quantization = QUANTIZATION,
                    fileName = FILE_NAME,
                ),
            compatibility = CatalogCompatibility(minSdk = 26, supportedAbis = setOf("arm64-v8a")),
            availability = CatalogAvailability.CANDIDATE,
            allowedTargets = setOf(TARGET),
            profileKey = PROFILE_KEY,
            license = CatalogLicense("Apache-2.0", "Apache-2.0"),
        )

    private class FakeVerifiedDownloadAccess(
        private val bytes: ByteArray,
        private val failure: VerifiedDownloadAccessFailureCode? = null,
    ) : VerifiedDownloadAccess {
        var copyCount = 0
        var discarded = false

        override fun copyTo(
            request: VerifiedDownloadCopyRequest,
            destination: File,
        ): VerifiedDownloadCopyResult {
            copyCount += 1
            failure?.let { return VerifiedDownloadCopyResult.Failure(it) }
            destination.writeBytes(bytes)
            return VerifiedDownloadCopyResult.Success(request.expectedDigest, bytes.size.toLong())
        }

        override fun discard(handle: VerifiedDownloadHandle): Boolean {
            discarded = true
            return true
        }
    }

    private class FakeModelStore(
        private val root: File,
        private val verificationValid: Boolean = true,
        private val verificationFailure: Boolean = false,
        private val importFailure: Boolean = false,
    ) : ModelStore {
        var importCount = 0
        var verifyCount = 0
        var removeCount = 0

        override fun find(digest: ModelDigest): StoredModel? = null

        override fun import(source: File, artifact: GgufArtifact): StoredModel {
            importCount += 1
            if (importFailure) {
                throw ModelImportException(ModelImportErrorCode.IO_FAILURE, "fixed")
            }
            if (!source.isFile) {
                throw ModelImportException(ModelImportErrorCode.INVALID_SOURCE, "missing")
            }
            return StoredModel(
                artifact.digest,
                File(root, "installed.gguf"),
                artifact.sizeBytes,
                verified = true,
            )
        }

        override fun verify(digest: ModelDigest): VerificationResult {
            verifyCount += 1
            if (verificationFailure) throw IllegalStateException("fixed")
            return VerificationResult(verificationValid, digest, "fixed")
        }

        override fun remove(digest: ModelDigest): Boolean {
            removeCount += 1
            return true
        }

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0L, emptyList())
    }

    private companion object {
        val MODEL_BYTES = "verified-model".toByteArray()
        val DIGEST = ModelDigest("a".repeat(64))
        val TARGET = CatalogTarget(ApplicationId("phone-test"), UseCaseId("manual"))
        val PROFILE_KEY = ModelProfileKey("test-q4-profile")
        const val ARCHITECTURE = "qwen35"
        const val QUANTIZATION = "Q4_K_M"
        const val FILE_NAME = "model.gguf"
    }
}
