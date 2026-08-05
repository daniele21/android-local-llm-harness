package io.github.daniele21.localllm.install

import io.github.daniele21.localllm.catalog.CatalogAvailability
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogReleaseId
import io.github.daniele21.localllm.catalog.CatalogTarget
import io.github.daniele21.localllm.catalog.ModelProfileKey
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.download.VerifiedDownloadAccess
import io.github.daniele21.localllm.download.VerifiedDownloadAccessFailureCode
import io.github.daniele21.localllm.download.VerifiedDownloadCopyRequest
import io.github.daniele21.localllm.download.VerifiedDownloadCopyResult
import io.github.daniele21.localllm.download.VerifiedDownloadHandle
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import java.io.File
import java.io.IOException
import java.util.UUID

@JvmInline
value class ModelInstallationId(val value: String)

data class ResolvedInstallationProfile(val key: ModelProfileKey, val artifact: GgufArtifact)

data class ModelInstallationRequest(
    val handle: VerifiedDownloadHandle,
    val release: CatalogModelRelease,
    val target: CatalogTarget,
    val profile: ResolvedInstallationProfile,
    val retentionPolicy: VerifiedDownloadRetentionPolicy = VerifiedDownloadRetentionPolicy.DISCARD_AFTER_SUCCESS,
)

enum class VerifiedDownloadRetentionPolicy {
    DISCARD_AFTER_SUCCESS,
    RETAIN,
}

enum class ModelInstallationStage {
    VALIDATING,
    STAGING,
    INSPECTING,
    IMPORTING,
    VERIFYING,
    COMPLETED,
    FAILED,
}

data class ModelInstallationProgress(val installationId: ModelInstallationId, val stage: ModelInstallationStage)

fun interface ModelInstallationObserver {
    fun onProgress(progress: ModelInstallationProgress)
}

data class GgufArtifactMetadata(
    val version: UInt,
    val architecture: String?,
    val name: String?,
    val fileType: Long?,
    val quantization: String? = null,
)

sealed interface GgufArtifactInspectionResult {
    data class Success(val metadata: GgufArtifactMetadata) : GgufArtifactInspectionResult

    data class Failure(val code: GgufArtifactInspectionFailureCode) : GgufArtifactInspectionResult
}

enum class GgufArtifactInspectionFailureCode {
    FILE_NOT_FOUND,
    INVALID_GGUF,
    INSPECTION_FAILED,
}

fun interface GgufArtifactInspector {
    fun inspect(file: File): GgufArtifactInspectionResult
}

data class InstalledModelDescriptor(
    val digest: ModelDigest,
    val sizeBytes: Long,
    val releaseId: CatalogReleaseId,
    val target: CatalogTarget,
    val profileKey: ModelProfileKey,
    val architecture: String,
    val quantization: String,
    val metadata: GgufArtifactMetadata,
)

sealed interface ModelInstallationResult {
    val installationId: ModelInstallationId

    data class Success(
        override val installationId: ModelInstallationId,
        val installed: InstalledModelDescriptor,
        val verifiedDownloadDiscarded: Boolean,
    ) : ModelInstallationResult

    data class Failure(override val installationId: ModelInstallationId, val code: ModelInstallationFailureCode, val detail: String) :
        ModelInstallationResult
}

enum class ModelInstallationFailureCode {
    INVALID_DESCRIPTOR,
    RELEASE_UNAVAILABLE,
    TARGET_NOT_ALLOWED,
    PROFILE_MISMATCH,
    VERIFIED_DOWNLOAD_UNAVAILABLE,
    VERIFIED_DOWNLOAD_INVALID,
    STAGING_FAILURE,
    GGUF_INSPECTION_FAILED,
    ARCHITECTURE_MISMATCH,
    QUANTIZATION_MISMATCH,
    MODEL_STORE_IMPORT_FAILED,
    POST_IMPORT_VERIFICATION_FAILED,
    INTERNAL_FAILURE,
}

@Suppress("ComplexCondition", "ComplexMethod", "LongMethod", "ReturnCount", "TooGenericExceptionCaught")
class VerifiedModelInstaller(
    stagingDirectory: File,
    private val verifiedDownloads: VerifiedDownloadAccess,
    private val inspector: GgufArtifactInspector,
    private val modelStore: ModelStore,
) {
    private val stagingRoot = controlledStagingRoot(stagingDirectory)

    fun install(
        request: ModelInstallationRequest,
        observer: ModelInstallationObserver = ModelInstallationObserver {},
    ): ModelInstallationResult {
        val installationId = ModelInstallationId(UUID.randomUUID().toString())
        emit(observer, installationId, ModelInstallationStage.VALIDATING)
        validate(request)?.let { return fail(observer, installationId, it.code, it.detail) }

        val staged =
            createStagingFile()
                ?: return fail(
                    observer,
                    installationId,
                    ModelInstallationFailureCode.STAGING_FAILURE,
                    "Unable to create installation staging file",
                )

        return try {
            emit(observer, installationId, ModelInstallationStage.STAGING)
            copyVerifiedDownload(request, staged)?.let {
                return fail(observer, installationId, it.code, it.detail)
            }

            emit(observer, installationId, ModelInstallationStage.INSPECTING)
            val metadata = when (val inspected = inspector.inspect(staged)) {
                is GgufArtifactInspectionResult.Failure -> {
                    return fail(
                        observer,
                        installationId,
                        ModelInstallationFailureCode.GGUF_INSPECTION_FAILED,
                        inspected.code.name,
                    )
                }

                is GgufArtifactInspectionResult.Success -> inspected.metadata
            }
            validateInspectedMetadata(request, metadata)?.let {
                return fail(observer, installationId, it.code, it.detail)
            }

            emit(observer, installationId, ModelInstallationStage.IMPORTING)
            val stored = try {
                modelStore.import(staged, request.profile.artifact)
            } catch (error: ModelImportException) {
                return fail(
                    observer,
                    installationId,
                    ModelInstallationFailureCode.MODEL_STORE_IMPORT_FAILED,
                    error.code.name,
                )
            }

            emit(observer, installationId, ModelInstallationStage.VERIFYING)
            val verification = try {
                modelStore.verify(stored.digest)
            } catch (_: RuntimeException) {
                runCatching { modelStore.remove(stored.digest) }
                return fail(
                    observer,
                    installationId,
                    ModelInstallationFailureCode.POST_IMPORT_VERIFICATION_FAILED,
                    "Installed model verification could not be completed",
                )
            }
            if (!verification.valid) {
                runCatching { modelStore.remove(stored.digest) }
                return fail(
                    observer,
                    installationId,
                    ModelInstallationFailureCode.POST_IMPORT_VERIFICATION_FAILED,
                    "Installed model failed integrity verification",
                )
            }

            val discarded =
                request.retentionPolicy == VerifiedDownloadRetentionPolicy.DISCARD_AFTER_SUCCESS &&
                    runCatching { verifiedDownloads.discard(request.handle) }.getOrDefault(false)
            emit(observer, installationId, ModelInstallationStage.COMPLETED)
            ModelInstallationResult.Success(
                installationId = installationId,
                installed = InstalledModelDescriptor(
                    digest = stored.digest,
                    sizeBytes = stored.sizeBytes,
                    releaseId = request.release.id,
                    target = request.target,
                    profileKey = request.profile.key,
                    architecture = request.release.artifact.architecture,
                    quantization = request.release.artifact.quantization,
                    metadata = metadata,
                ),
                verifiedDownloadDiscarded = discarded,
            )
        } catch (_: RuntimeException) {
            fail(
                observer,
                installationId,
                ModelInstallationFailureCode.INTERNAL_FAILURE,
                "Unexpected installation failure",
            )
        } finally {
            staged.delete()
        }
    }

    private fun copyVerifiedDownload(request: ModelInstallationRequest, staged: File): InstallationFailure? {
        val copied =
            verifiedDownloads.copyTo(
                VerifiedDownloadCopyRequest(
                    handle = request.handle,
                    expectedDigest = request.release.artifact.digest,
                    expectedSizeBytes = request.release.artifact.sizeBytes,
                ),
                staged,
            )
        return when (copied) {
            is VerifiedDownloadCopyResult.Success -> null

            is VerifiedDownloadCopyResult.Failure ->
                when (copied.code) {
                    VerifiedDownloadAccessFailureCode.VERIFIED_DOWNLOAD_MISSING ->
                        InstallationFailure(
                            ModelInstallationFailureCode.VERIFIED_DOWNLOAD_UNAVAILABLE,
                            copied.code.name,
                        )

                    else ->
                        InstallationFailure(
                            ModelInstallationFailureCode.VERIFIED_DOWNLOAD_INVALID,
                            copied.code.name,
                        )
                }
        }
    }

    private fun validate(request: ModelInstallationRequest): InstallationFailure? {
        val remote = request.release.artifact
        val profile = request.profile
        if (remote.sizeBytes <= 0L || request.handle.value.isBlank()) {
            return InstallationFailure(
                ModelInstallationFailureCode.INVALID_DESCRIPTOR,
                "Installation descriptor is invalid",
            )
        }
        if (
            request.release.availability in
            setOf(CatalogAvailability.REVOKED, CatalogAvailability.UNAVAILABLE)
        ) {
            return InstallationFailure(
                ModelInstallationFailureCode.RELEASE_UNAVAILABLE,
                "Catalog release is unavailable for installation",
            )
        }
        if (request.target !in request.release.allowedTargets) {
            return InstallationFailure(
                ModelInstallationFailureCode.TARGET_NOT_ALLOWED,
                "Catalog release is not allowed for the requested target",
            )
        }
        if (profile.key != request.release.profileKey) {
            return InstallationFailure(
                ModelInstallationFailureCode.PROFILE_MISMATCH,
                "Catalog release does not match the resolved profile key",
            )
        }
        val artifact = profile.artifact
        if (
            artifact.digest != remote.digest ||
            artifact.sizeBytes != remote.sizeBytes ||
            artifact.fileName != remote.fileName ||
            normalize(artifact.architecture) != normalize(remote.architecture) ||
            normalize(artifact.quantization) != normalize(remote.quantization)
        ) {
            return InstallationFailure(
                ModelInstallationFailureCode.PROFILE_MISMATCH,
                "Catalog artifact does not match the resolved application profile",
            )
        }
        return null
    }

    private fun validateInspectedMetadata(request: ModelInstallationRequest, metadata: GgufArtifactMetadata): InstallationFailure? {
        val expectedArchitecture = normalize(request.release.artifact.architecture)
        val actualArchitecture = metadata.architecture?.let(::normalize)
        if (actualArchitecture == null || actualArchitecture != expectedArchitecture) {
            return InstallationFailure(
                ModelInstallationFailureCode.ARCHITECTURE_MISMATCH,
                "GGUF architecture does not match the catalog release",
            )
        }
        val actualQuantization = metadata.quantization?.let(::normalize)
        if (
            actualQuantization != null &&
            actualQuantization != normalize(request.release.artifact.quantization)
        ) {
            return InstallationFailure(
                ModelInstallationFailureCode.QUANTIZATION_MISMATCH,
                "GGUF quantization does not match the catalog release",
            )
        }
        return null
    }

    private fun createStagingFile(): File? = try {
        File.createTempFile("model-install-", ".gguf", stagingRoot).also { file ->
            check(file.canonicalFile.parentFile == stagingRoot) {
                "Installation staging file escaped its root"
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun fail(
        observer: ModelInstallationObserver,
        installationId: ModelInstallationId,
        code: ModelInstallationFailureCode,
        detail: String,
    ): ModelInstallationResult.Failure {
        emit(observer, installationId, ModelInstallationStage.FAILED)
        return ModelInstallationResult.Failure(installationId, code, detail)
    }

    private fun emit(observer: ModelInstallationObserver, installationId: ModelInstallationId, stage: ModelInstallationStage) {
        runCatching { observer.onProgress(ModelInstallationProgress(installationId, stage)) }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace('-', '_')

    private data class InstallationFailure(val code: ModelInstallationFailureCode, val detail: String)

    private companion object {
        fun controlledStagingRoot(directory: File): File {
            val root = try {
                directory.canonicalFile
            } catch (error: IOException) {
                throw IllegalArgumentException(
                    "Unable to resolve model installation staging directory",
                    error,
                )
            }
            require(root.exists() || root.mkdirs()) {
                "Unable to create model installation staging directory"
            }
            require(root.isDirectory) { "Model installation staging path must be a directory" }
            return root
        }
    }
}
