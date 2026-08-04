package io.github.daniele21.localllm.catalog

import java.net.URI

data class CatalogViolation(
    val code: CatalogViolationCode,
    val path: String,
)

data class CatalogValidationResult(val violations: List<CatalogViolation>) {
    val valid: Boolean
        get() = violations.isEmpty()
}

enum class CatalogViolationCode {
    UNSUPPORTED_SCHEMA,
    INVALID_CATALOG_ID,
    INVALID_REVISION,
    INVALID_TIME_WINDOW,
    DOCUMENT_EXPIRED,
    TOO_MANY_ENTRIES,
    DUPLICATE_RELEASE,
    CONFLICTING_DIGEST_METADATA,
    INVALID_MODEL_ID,
    INVALID_VERSION,
    INVALID_DISPLAY_NAME,
    INVALID_DESCRIPTION,
    INVALID_PROFILE_KEY,
    INVALID_DIGEST,
    INVALID_SIZE,
    INVALID_DOWNLOAD_URI,
    INVALID_FILE_NAME,
    INVALID_ARCHITECTURE,
    INVALID_QUANTIZATION,
    INVALID_COMPATIBILITY,
    MISSING_TARGET,
    INVALID_TARGET,
    INVALID_LICENSE,
    INVALID_LICENSE_URI,
    SELF_REPLACEMENT,
}

@Suppress("TooManyFunctions")
class CatalogValidator(
    private val supportedSchemaVersions: Set<Int> = setOf(CURRENT_SCHEMA_VERSION),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxIdentifierLength: Int = DEFAULT_MAX_IDENTIFIER_LENGTH,
    private val maxDisplayNameLength: Int = DEFAULT_MAX_DISPLAY_NAME_LENGTH,
    private val maxDescriptionLength: Int = DEFAULT_MAX_DESCRIPTION_LENGTH,
) {
    init {
        require(supportedSchemaVersions.isNotEmpty())
        require(maxEntries > 0)
        require(maxIdentifierLength > 0)
        require(maxDisplayNameLength > 0)
        require(maxDescriptionLength > 0)
    }

    fun validate(document: CatalogModelDocument, nowEpochMs: Long): CatalogValidationResult {
        val violations = mutableListOf<CatalogViolation>()
        validateDocument(document, nowEpochMs, violations)
        validateEntries(document.entries, violations)
        return CatalogValidationResult(violations)
    }

    private fun validateDocument(
        document: CatalogModelDocument,
        nowEpochMs: Long,
        violations: MutableList<CatalogViolation>,
    ) {
        if (document.schemaVersion !in supportedSchemaVersions) {
            violations += violation(CatalogViolationCode.UNSUPPORTED_SCHEMA, "schemaVersion")
        }
        if (!validIdentifier(document.catalogId.value)) {
            violations += violation(CatalogViolationCode.INVALID_CATALOG_ID, "catalogId")
        }
        if (document.revision < 0) {
            violations += violation(CatalogViolationCode.INVALID_REVISION, "revision")
        }
        if (document.generatedAtEpochMs < 0 || document.expiresAtEpochMs <= document.generatedAtEpochMs) {
            violations += violation(CatalogViolationCode.INVALID_TIME_WINDOW, "expiresAtEpochMs")
        } else if (document.expiresAtEpochMs <= nowEpochMs) {
            violations += violation(CatalogViolationCode.DOCUMENT_EXPIRED, "expiresAtEpochMs")
        }
        if (document.entries.size > maxEntries) {
            violations += violation(CatalogViolationCode.TOO_MANY_ENTRIES, "entries")
        }
    }

    private fun validateEntries(
        entries: List<CatalogModelRelease>,
        violations: MutableList<CatalogViolation>,
    ) {
        val releaseIds = mutableSetOf<CatalogReleaseId>()
        val digestSizes = mutableMapOf<String, Long>()
        entries.forEachIndexed { index, release ->
            val path = "entries[$index]"
            if (!releaseIds.add(release.id)) {
                violations += violation(CatalogViolationCode.DUPLICATE_RELEASE, "$path.id")
            }
            validateRelease(release, path, violations)
            val digest = release.artifact.digest.sha256.lowercase()
            val existingSize = digestSizes.putIfAbsent(digest, release.artifact.sizeBytes)
            if (existingSize != null && existingSize != release.artifact.sizeBytes) {
                violations += violation(
                    CatalogViolationCode.CONFLICTING_DIGEST_METADATA,
                    "$path.artifact.sizeBytes",
                )
            }
        }
    }

    private fun validateRelease(
        release: CatalogModelRelease,
        path: String,
        violations: MutableList<CatalogViolation>,
    ) {
        if (!validIdentifier(release.id.modelId.value)) {
            violations += violation(CatalogViolationCode.INVALID_MODEL_ID, "$path.id.modelId")
        }
        if (!validIdentifier(release.id.version.value)) {
            violations += violation(CatalogViolationCode.INVALID_VERSION, "$path.id.version")
        }
        if (!validText(release.displayName, maxDisplayNameLength)) {
            violations += violation(CatalogViolationCode.INVALID_DISPLAY_NAME, "$path.displayName")
        }
        if (!validText(release.description, maxDescriptionLength)) {
            violations += violation(CatalogViolationCode.INVALID_DESCRIPTION, "$path.description")
        }
        if (!validIdentifier(release.profileKey.value)) {
            violations += violation(CatalogViolationCode.INVALID_PROFILE_KEY, "$path.profileKey")
        }
        validateArtifact(release.artifact, "$path.artifact", violations)
        validateCompatibility(release.compatibility, "$path.compatibility", violations)
        validateTargets(release.allowedTargets, "$path.allowedTargets", violations)
        validateLicense(release.license, "$path.license", violations)
        if (release.replacement == release.id) {
            violations += violation(CatalogViolationCode.SELF_REPLACEMENT, "$path.replacement")
        }
    }

    private fun validateArtifact(
        artifact: CatalogGgufArtifact,
        path: String,
        violations: MutableList<CatalogViolation>,
    ) {
        if (!SHA_256.matches(artifact.digest.sha256)) {
            violations += violation(CatalogViolationCode.INVALID_DIGEST, "$path.digest")
        }
        if (artifact.sizeBytes <= 0) {
            violations += violation(CatalogViolationCode.INVALID_SIZE, "$path.sizeBytes")
        }
        if (!validHttpsUri(artifact.downloadUri)) {
            violations += violation(CatalogViolationCode.INVALID_DOWNLOAD_URI, "$path.downloadUri")
        }
        if (!validFileName(artifact.fileName)) {
            violations += violation(CatalogViolationCode.INVALID_FILE_NAME, "$path.fileName")
        }
        if (!validIdentifier(artifact.architecture)) {
            violations += violation(CatalogViolationCode.INVALID_ARCHITECTURE, "$path.architecture")
        }
        if (!validIdentifier(artifact.quantization)) {
            violations += violation(CatalogViolationCode.INVALID_QUANTIZATION, "$path.quantization")
        }
    }

    private fun validateCompatibility(
        compatibility: CatalogCompatibility,
        path: String,
        violations: MutableList<CatalogViolation>,
    ) {
        val invalidRam = compatibility.minRamBytes?.let { it <= 0 } == true ||
            compatibility.recommendedRamBytes?.let { it <= 0 } == true ||
            (
                compatibility.minRamBytes != null &&
                    compatibility.recommendedRamBytes != null &&
                    compatibility.recommendedRamBytes < compatibility.minRamBytes
            )
        val invalidVersions = compatibility.minHarnessVersion?.isBlank() == true ||
            compatibility.maxHarnessVersionExclusive?.isBlank() == true
        val invalidBackends = compatibility.supportedBackendIds.any { !validIdentifier(it) }
        if (
            compatibility.minSdk < 1 ||
            compatibility.supportedAbis.isEmpty() ||
            compatibility.supportedAbis.any { !validIdentifier(it) } ||
            compatibility.minFreeStorageBytes < 0 ||
            invalidRam ||
            invalidVersions ||
            invalidBackends
        ) {
            violations += violation(CatalogViolationCode.INVALID_COMPATIBILITY, path)
        }
    }

    private fun validateTargets(
        targets: Set<CatalogTarget>,
        path: String,
        violations: MutableList<CatalogViolation>,
    ) {
        if (targets.isEmpty()) {
            violations += violation(CatalogViolationCode.MISSING_TARGET, path)
        }
        targets.forEachIndexed { index, target ->
            if (!validIdentifier(target.applicationId.value) || !validIdentifier(target.useCaseId.value)) {
                violations += violation(CatalogViolationCode.INVALID_TARGET, "$path[$index]")
            }
        }
    }

    private fun validateLicense(
        license: CatalogLicense,
        path: String,
        violations: MutableList<CatalogViolation>,
    ) {
        if (!validIdentifier(license.id) || !validText(license.displayName, maxDisplayNameLength)) {
            violations += violation(CatalogViolationCode.INVALID_LICENSE, path)
        }
        listOf(license.sourceUri, license.licenseUri).filterNotNull().forEachIndexed { index, uri ->
            if (!validHttpsUri(uri)) {
                violations += violation(CatalogViolationCode.INVALID_LICENSE_URI, "$path.uri[$index]")
            }
        }
    }

    private fun validIdentifier(value: String): Boolean =
        value.length in 1..maxIdentifierLength && IDENTIFIER.matches(value)

    private fun validText(value: String, maximumLength: Int): Boolean =
        value.isNotBlank() && value.length <= maximumLength && value.none(Char::isISOControl)

    private fun validHttpsUri(uri: URI): Boolean =
        uri.isAbsolute &&
            uri.scheme.equals(HTTPS, ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null

    private fun validFileName(value: String): Boolean =
        value.length in 1..maxIdentifierLength &&
            value.lowercase().endsWith(GGUF_SUFFIX) &&
            !value.contains('/') &&
            !value.contains('\\') &&
            value != "." &&
            value != ".."

    private fun violation(code: CatalogViolationCode, path: String): CatalogViolation =
        CatalogViolation(code = code, path = path)

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_MAX_ENTRIES = 500
        const val DEFAULT_MAX_IDENTIFIER_LENGTH = 128
        const val DEFAULT_MAX_DISPLAY_NAME_LENGTH = 160
        const val DEFAULT_MAX_DESCRIPTION_LENGTH = 2_000
        const val HTTPS = "https"
        const val GGUF_SUFFIX = ".gguf"
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
        val IDENTIFIER = Regex("^[A-Za-z0-9][A-Za-z0-9._:+-]*$")
    }
}
