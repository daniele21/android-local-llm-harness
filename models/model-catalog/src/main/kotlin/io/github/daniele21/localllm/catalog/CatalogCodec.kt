@file:Suppress("TooManyFunctions")

package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import java.net.URI

interface CatalogDocumentCodec {
    fun decode(bytes: ByteArray): CatalogDecodeResult
    fun encode(document: CatalogModelDocument): CatalogEncodeResult
}

sealed interface CatalogDecodeResult {
    data class Success(val document: CatalogModelDocument) : CatalogDecodeResult
    data class Failure(val error: CatalogCodecError) : CatalogDecodeResult
}

sealed interface CatalogEncodeResult {
    data class Success(val bytes: ByteArray) : CatalogEncodeResult
    data class Failure(val error: CatalogCodecError) : CatalogEncodeResult
}

data class CatalogCodecError(val code: CatalogCodecErrorCode, val path: String)

enum class CatalogCodecErrorCode {
    EMPTY_DOCUMENT,
    DOCUMENT_TOO_LARGE,
    INVALID_UTF8,
    INVALID_UNICODE,
    MALFORMED_JSON,
    JSON_LIMIT_EXCEEDED,
    DUPLICATE_FIELD,
    DUPLICATE_VALUE,
    ROOT_NOT_OBJECT,
    UNKNOWN_FIELD,
    MISSING_FIELD,
    INVALID_FIELD_TYPE,
    INVALID_NUMBER,
    INVALID_ENUM,
    INVALID_URI,
    ENCODED_DOCUMENT_TOO_LARGE,
}

@Suppress("TooManyFunctions")
class CatalogJsonCodec(
    private val maxDocumentBytes: Int = DEFAULT_MAX_DOCUMENT_BYTES,
    maxJsonDepth: Int = DEFAULT_MAX_JSON_DEPTH,
    maxJsonNodes: Int = DEFAULT_MAX_JSON_NODES,
    maxJsonStringChars: Int = DEFAULT_MAX_JSON_STRING_CHARS,
) : CatalogDocumentCodec {
    private val parser = BoundedCatalogJsonParser(maxJsonDepth, maxJsonNodes, maxJsonStringChars)

    init {
        require(maxDocumentBytes > 0)
    }

    override fun decode(bytes: ByteArray): CatalogDecodeResult {
        if (bytes.isEmpty()) return failure(CatalogCodecErrorCode.EMPTY_DOCUMENT)
        if (bytes.size > maxDocumentBytes) return failure(CatalogCodecErrorCode.DOCUMENT_TOO_LARGE)
        return try {
            val root = parser.parse(bytes)
            CatalogDecodeResult.Success(decodeDocument(root))
        } catch (error: CatalogJsonSyntaxException) {
            failure(error.code)
        } catch (error: CatalogMappingException) {
            failure(error.code, error.path)
        }
    }

    override fun encode(document: CatalogModelDocument): CatalogEncodeResult = try {
        val bytes = CatalogJsonWriter.encode(encodeDocument(document))
        if (bytes.size > maxDocumentBytes) {
            CatalogEncodeResult.Failure(CatalogCodecError(CatalogCodecErrorCode.ENCODED_DOCUMENT_TOO_LARGE, ROOT_PATH))
        } else {
            CatalogEncodeResult.Success(bytes)
        }
    } catch (error: CatalogMappingException) {
        CatalogEncodeResult.Failure(CatalogCodecError(error.code, error.path))
    } catch (error: CatalogJsonWriteException) {
        CatalogEncodeResult.Failure(CatalogCodecError(error.code, ROOT_PATH))
    }

    internal fun decodeValue(value: CatalogJsonValue): CatalogModelDocument = decodeDocument(value)

    internal fun encodeValue(document: CatalogModelDocument): CatalogJsonObject = encodeDocument(document)

    private fun decodeDocument(value: CatalogJsonValue): CatalogModelDocument {
        val objectValue = value.asObject(ROOT_PATH, CatalogCodecErrorCode.ROOT_NOT_OBJECT)
        objectValue.requireFields(DOCUMENT_FIELDS, ROOT_PATH)
        return CatalogModelDocument(
            schemaVersion = objectValue.requiredInt("schemaVersion", ROOT_PATH),
            catalogId = CatalogId(objectValue.requiredString("catalogId", ROOT_PATH)),
            revision = objectValue.requiredLong("revision", ROOT_PATH),
            generatedAtEpochMs = objectValue.requiredLong("generatedAtEpochMs", ROOT_PATH),
            expiresAtEpochMs = objectValue.requiredLong("expiresAtEpochMs", ROOT_PATH),
            entries = objectValue.requiredArray("entries", ROOT_PATH).values.mapIndexed(::decodeRelease),
        )
    }

    private fun decodeRelease(index: Int, value: CatalogJsonValue): CatalogModelRelease {
        val path = "$.entries[$index]"
        val objectValue = value.asObject(path)
        objectValue.requireFields(RELEASE_FIELDS, path)
        return CatalogModelRelease(
            id = CatalogReleaseId(
                modelId = CatalogModelId(objectValue.requiredString("modelId", path)),
                version = CatalogModelVersion(objectValue.requiredString("version", path)),
            ),
            displayName = objectValue.requiredString("displayName", path),
            description = objectValue.requiredString("description", path),
            artifact = decodeArtifact(objectValue.requiredObject("artifact", path), "$path.artifact"),
            compatibility = decodeCompatibility(
                objectValue.requiredObject("compatibility", path),
                "$path.compatibility",
            ),
            availability = objectValue.requiredEnum<CatalogAvailability>("availability", path),
            allowedTargets = objectValue.requiredArray("allowedTargets", path).values.mapIndexed { targetIndex, target ->
                decodeTarget(target, "$path.allowedTargets[$targetIndex]")
            }.requireDistinct("$path.allowedTargets"),
            profileKey = ModelProfileKey(objectValue.requiredString("profileKey", path)),
            license = decodeLicense(objectValue.requiredObject("license", path), "$path.license"),
            replacement = objectValue.optionalObject("replacement", path)?.let { replacement ->
                decodeReleaseId(replacement, "$path.replacement")
            },
        )
    }

    private fun decodeArtifact(value: CatalogJsonObject, path: String): CatalogGgufArtifact {
        value.requireFields(ARTIFACT_FIELDS, path)
        return CatalogGgufArtifact(
            digest = ModelDigest(value.requiredString("sha256", path)),
            sizeBytes = value.requiredLong("sizeBytes", path),
            downloadUri = parseUri(value.requiredString("downloadUrl", path), "$path.downloadUrl"),
            architecture = value.requiredString("architecture", path),
            quantization = value.requiredString("quantization", path),
            fileName = value.requiredString("fileName", path),
        )
    }

    private fun decodeCompatibility(value: CatalogJsonObject, path: String): CatalogCompatibility {
        value.requireFields(COMPATIBILITY_FIELDS, path)
        return CatalogCompatibility(
            minSdk = value.requiredInt("minSdk", path),
            supportedAbis = value.requiredStringSet("supportedAbis", path),
            minRamBytes = value.optionalLong("minRamBytes", path),
            recommendedRamBytes = value.optionalLong("recommendedRamBytes", path),
            minFreeStorageBytes = value.requiredLong("minFreeStorageBytes", path),
            minHarnessVersion = value.optionalString("minHarnessVersion", path),
            maxHarnessVersionExclusive = value.optionalString("maxHarnessVersionExclusive", path),
            supportedBackendIds = value.requiredStringSet("supportedBackendIds", path),
        )
    }

    private fun decodeTarget(value: CatalogJsonValue, path: String): CatalogTarget {
        val objectValue = value.asObject(path)
        objectValue.requireFields(TARGET_FIELDS, path)
        return CatalogTarget(
            applicationId = ApplicationId(objectValue.requiredString("applicationId", path)),
            useCaseId = UseCaseId(objectValue.requiredString("useCaseId", path)),
        )
    }

    private fun decodeLicense(value: CatalogJsonObject, path: String): CatalogLicense {
        value.requireFields(LICENSE_FIELDS, path)
        return CatalogLicense(
            id = value.requiredString("id", path),
            displayName = value.requiredString("displayName", path),
            sourceUri = value.optionalString("sourceUrl", path)?.let { parseUri(it, "$path.sourceUrl") },
            licenseUri = value.optionalString("licenseUrl", path)?.let { parseUri(it, "$path.licenseUrl") },
        )
    }

    private fun decodeReleaseId(value: CatalogJsonObject, path: String): CatalogReleaseId {
        value.requireFields(RELEASE_ID_FIELDS, path)
        return CatalogReleaseId(
            modelId = CatalogModelId(value.requiredString("modelId", path)),
            version = CatalogModelVersion(value.requiredString("version", path)),
        )
    }

    private fun encodeDocument(document: CatalogModelDocument): CatalogJsonObject = jsonObject(
        "schemaVersion" to jsonNumber(document.schemaVersion),
        "catalogId" to CatalogJsonString(document.catalogId.value),
        "revision" to jsonNumber(document.revision),
        "generatedAtEpochMs" to jsonNumber(document.generatedAtEpochMs),
        "expiresAtEpochMs" to jsonNumber(document.expiresAtEpochMs),
        "entries" to CatalogJsonArray(document.entries.map(::encodeRelease)),
    )

    private fun encodeRelease(release: CatalogModelRelease): CatalogJsonObject = jsonObject(
        "modelId" to CatalogJsonString(release.id.modelId.value),
        "version" to CatalogJsonString(release.id.version.value),
        "displayName" to CatalogJsonString(release.displayName),
        "description" to CatalogJsonString(release.description),
        "artifact" to encodeArtifact(release.artifact),
        "compatibility" to encodeCompatibility(release.compatibility),
        "availability" to CatalogJsonString(release.availability.name),
        "allowedTargets" to CatalogJsonArray(
            release.allowedTargets
                .sortedWith(compareBy({ it.applicationId.value }, { it.useCaseId.value }))
                .map(::encodeTarget),
        ),
        "profileKey" to CatalogJsonString(release.profileKey.value),
        "license" to encodeLicense(release.license),
        "replacement" to release.replacement?.let(::encodeReleaseId).orJsonNull(),
    )

    private fun encodeArtifact(artifact: CatalogGgufArtifact): CatalogJsonObject = jsonObject(
        "sha256" to CatalogJsonString(artifact.digest.sha256),
        "sizeBytes" to jsonNumber(artifact.sizeBytes),
        "downloadUrl" to CatalogJsonString(artifact.downloadUri.toASCIIString()),
        "architecture" to CatalogJsonString(artifact.architecture),
        "quantization" to CatalogJsonString(artifact.quantization),
        "fileName" to CatalogJsonString(artifact.fileName),
    )

    private fun encodeCompatibility(value: CatalogCompatibility): CatalogJsonObject = jsonObject(
        "minSdk" to jsonNumber(value.minSdk),
        "supportedAbis" to jsonStringArray(value.supportedAbis),
        "minRamBytes" to value.minRamBytes?.let(::jsonNumber).orJsonNull(),
        "recommendedRamBytes" to value.recommendedRamBytes?.let(::jsonNumber).orJsonNull(),
        "minFreeStorageBytes" to jsonNumber(value.minFreeStorageBytes),
        "minHarnessVersion" to value.minHarnessVersion?.let(::CatalogJsonString).orJsonNull(),
        "maxHarnessVersionExclusive" to value.maxHarnessVersionExclusive?.let(::CatalogJsonString).orJsonNull(),
        "supportedBackendIds" to jsonStringArray(value.supportedBackendIds),
    )

    private fun encodeTarget(target: CatalogTarget): CatalogJsonObject = jsonObject(
        "applicationId" to CatalogJsonString(target.applicationId.value),
        "useCaseId" to CatalogJsonString(target.useCaseId.value),
    )

    private fun encodeLicense(license: CatalogLicense): CatalogJsonObject = jsonObject(
        "id" to CatalogJsonString(license.id),
        "displayName" to CatalogJsonString(license.displayName),
        "sourceUrl" to license.sourceUri?.toASCIIString()?.let(::CatalogJsonString).orJsonNull(),
        "licenseUrl" to license.licenseUri?.toASCIIString()?.let(::CatalogJsonString).orJsonNull(),
    )

    private fun encodeReleaseId(id: CatalogReleaseId): CatalogJsonObject = jsonObject(
        "modelId" to CatalogJsonString(id.modelId.value),
        "version" to CatalogJsonString(id.version.value),
    )

    private fun parseUri(value: String, path: String): URI = try {
        URI(value)
    } catch (_: java.net.URISyntaxException) {
        throw CatalogMappingException(CatalogCodecErrorCode.INVALID_URI, path)
    }

    private fun jsonStringArray(values: Set<String>): CatalogJsonArray =
        CatalogJsonArray(values.sorted().map(::CatalogJsonString))

    private fun jsonNumber(value: Number): CatalogJsonNumber = CatalogJsonNumber(value.toString())

    private fun CatalogJsonValue?.orJsonNull(): CatalogJsonValue = this ?: CatalogJsonNull

    private fun jsonObject(vararg fields: Pair<String, CatalogJsonValue>): CatalogJsonObject {
        val values = linkedMapOf<String, CatalogJsonValue>()
        fields.forEach { (name, value) -> values[name] = value }
        return CatalogJsonObject(values)
    }

    private fun failure(code: CatalogCodecErrorCode, path: String = ROOT_PATH): CatalogDecodeResult.Failure =
        CatalogDecodeResult.Failure(CatalogCodecError(code, path))

    private companion object {
        const val ROOT_PATH = "$"
        const val DEFAULT_MAX_DOCUMENT_BYTES = 1024 * 1024
        const val DEFAULT_MAX_JSON_DEPTH = 16
        const val DEFAULT_MAX_JSON_NODES = 20_000
        const val DEFAULT_MAX_JSON_STRING_CHARS = 8_192
        val DOCUMENT_FIELDS = setOf("schemaVersion", "catalogId", "revision", "generatedAtEpochMs", "expiresAtEpochMs", "entries")
        val RELEASE_FIELDS = setOf(
            "modelId", "version", "displayName", "description", "artifact", "compatibility",
            "availability", "allowedTargets", "profileKey", "license", "replacement",
        )
        val ARTIFACT_FIELDS = setOf("sha256", "sizeBytes", "downloadUrl", "architecture", "quantization", "fileName")
        val COMPATIBILITY_FIELDS = setOf(
            "minSdk", "supportedAbis", "minRamBytes", "recommendedRamBytes", "minFreeStorageBytes",
            "minHarnessVersion", "maxHarnessVersionExclusive", "supportedBackendIds",
        )
        val TARGET_FIELDS = setOf("applicationId", "useCaseId")
        val LICENSE_FIELDS = setOf("id", "displayName", "sourceUrl", "licenseUrl")
        val RELEASE_ID_FIELDS = setOf("modelId", "version")
    }
}

internal class CatalogMappingException(val code: CatalogCodecErrorCode, val path: String) : IllegalArgumentException()

private fun CatalogJsonValue.asObject(
    path: String,
    errorCode: CatalogCodecErrorCode = CatalogCodecErrorCode.INVALID_FIELD_TYPE,
): CatalogJsonObject = this as? CatalogJsonObject ?: throw CatalogMappingException(errorCode, path)

private fun CatalogJsonObject.requireFields(expected: Set<String>, path: String) {
    val unknown = fields.keys.firstOrNull { it !in expected }
    if (unknown != null) throw CatalogMappingException(CatalogCodecErrorCode.UNKNOWN_FIELD, "$path.$unknown")
    val missing = expected.firstOrNull { it !in fields }
    if (missing != null) throw CatalogMappingException(CatalogCodecErrorCode.MISSING_FIELD, "$path.$missing")
}

private fun CatalogJsonObject.requiredValue(name: String, path: String): CatalogJsonValue =
    fields[name] ?: throw CatalogMappingException(CatalogCodecErrorCode.MISSING_FIELD, "$path.$name")

private fun CatalogJsonObject.requiredString(name: String, path: String): String =
    (requiredValue(name, path) as? CatalogJsonString)?.value
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")

private fun CatalogJsonObject.optionalString(name: String, path: String): String? = when (val value = requiredValue(name, path)) {
    CatalogJsonNull -> null
    is CatalogJsonString -> value.value
    else -> throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")
}

private fun CatalogJsonObject.requiredLong(name: String, path: String): Long {
    val raw = (requiredValue(name, path) as? CatalogJsonNumber)?.raw
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")
    if (raw.any { it == '.' || it == 'e' || it == 'E' }) {
        throw CatalogMappingException(CatalogCodecErrorCode.INVALID_NUMBER, "$path.$name")
    }
    return raw.toLongOrNull() ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_NUMBER, "$path.$name")
}

private fun CatalogJsonObject.optionalLong(name: String, path: String): Long? = when (requiredValue(name, path)) {
    CatalogJsonNull -> null
    else -> requiredLong(name, path)
}

private fun CatalogJsonObject.requiredInt(name: String, path: String): Int {
    val value = requiredLong(name, path)
    return value.toInt().takeIf { it.toLong() == value }
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_NUMBER, "$path.$name")
}

private fun CatalogJsonObject.requiredArray(name: String, path: String): CatalogJsonArray =
    requiredValue(name, path) as? CatalogJsonArray
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")

private fun CatalogJsonObject.requiredObject(name: String, path: String): CatalogJsonObject =
    requiredValue(name, path) as? CatalogJsonObject
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")

private fun CatalogJsonObject.optionalObject(
    name: String,
    path: String,
): CatalogJsonObject? = when (val value = requiredValue(name, path)) {
    CatalogJsonNull -> null
    is CatalogJsonObject -> value
    else -> throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name")
}

private inline fun <reified T : Enum<T>> CatalogJsonObject.requiredEnum(name: String, path: String): T {
    val raw = requiredString(name, path)
    return enumValues<T>().firstOrNull { it.name == raw }
        ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_ENUM, "$path.$name")
}

private fun CatalogJsonObject.requiredStringSet(name: String, path: String): Set<String> =
    requiredArray(name, path).values.mapIndexed { index, value ->
        (value as? CatalogJsonString)?.value
            ?: throw CatalogMappingException(CatalogCodecErrorCode.INVALID_FIELD_TYPE, "$path.$name[$index]")
    }.requireDistinct("$path.$name")

private fun <T> List<T>.requireDistinct(path: String): Set<T> {
    val distinct = toSet()
    if (distinct.size != size) throw CatalogMappingException(CatalogCodecErrorCode.DUPLICATE_VALUE, path)
    return distinct
}
