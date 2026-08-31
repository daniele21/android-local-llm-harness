package io.github.daniele21.localllm.phonetest

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class HarnessApplicationRouteIdentity(
    val applicationId: String,
    val useCaseId: String? = null,
    val presetId: String? = null,
    val presetRevision: Int? = null,
)

internal object HarnessApplicationRoutes {
    const val APPLICATION_ID_ARGUMENT = "applicationId"
    const val USE_CASE_ID_ARGUMENT = "useCaseId"
    const val PRESET_ID_ARGUMENT = "presetId"
    const val PRESET_REVISION_ARGUMENT = "presetRevision"

    const val NEW_APPLICATION_ROUTE = "applications/new"
    const val APPLICATION_PATTERN = "applications/{$APPLICATION_ID_ARGUMENT}"
    const val ASSIGNMENT_PATTERN =
        "applications/{$APPLICATION_ID_ARGUMENT}/use-cases/{$USE_CASE_ID_ARGUMENT}"
    const val PRESET_PATTERN =
        "applications/{$APPLICATION_ID_ARGUMENT}/use-cases/{$USE_CASE_ID_ARGUMENT}/presets/{$PRESET_ID_ARGUMENT}/{$PRESET_REVISION_ARGUMENT}"
    const val TECHNICAL_DETAILS_PATTERN =
        "$PRESET_PATTERN/technical"
    const val NEW_PRESET_PATTERN =
        "applications/{$APPLICATION_ID_ARGUMENT}/use-cases/{$USE_CASE_ID_ARGUMENT}/presets/new"

    private const val APPLICATION_PREFIX = "applications/"
    private const val USE_CASE_SEGMENT = "/use-cases/"
    private const val PRESET_SEGMENT = "/presets/"

    fun newApplication(): String = NEW_APPLICATION_ROUTE

    fun application(applicationId: String): String = APPLICATION_PREFIX + encode(applicationId, "applicationId")

    fun assignment(applicationId: String, useCaseId: String): String =
        application(applicationId) + USE_CASE_SEGMENT + encode(useCaseId, "useCaseId")

    fun preset(applicationId: String, useCaseId: String, presetId: String, presetRevision: Int): String {
        require(presetRevision > 0) { "presetRevision must be positive" }
        return assignment(applicationId, useCaseId) + PRESET_SEGMENT + encode(presetId, "presetId") + "/$presetRevision"
    }

    fun technicalDetails(applicationId: String, useCaseId: String, presetId: String, presetRevision: Int): String =
        preset(applicationId, useCaseId, presetId, presetRevision) + "/technical"

    fun newPreset(applicationId: String, useCaseId: String): String = assignment(applicationId, useCaseId) + PRESET_SEGMENT + "new"

    fun decodeApplicationId(encoded: String?): String? = decode(encoded)

    fun identity(
        encodedApplicationId: String?,
        encodedUseCaseId: String? = null,
        encodedPresetId: String? = null,
        presetRevision: Int? = null,
    ): HarnessApplicationRouteIdentity? {
        val applicationId = decode(encodedApplicationId)
        val useCaseId = encodedUseCaseId?.let(::decode)
        val presetId = encodedPresetId?.let(::decode)
        val invalid = applicationId == null ||
            (encodedUseCaseId != null && useCaseId == null) ||
            (encodedPresetId != null && presetId == null) ||
            (presetRevision != null && presetRevision <= 0) ||
            (presetId != null && (useCaseId == null || presetRevision == null))
        if (invalid) return null
        return HarnessApplicationRouteIdentity(applicationId, useCaseId, presetId, presetRevision)
    }

    private fun encode(value: String, label: String): String {
        require(value.isNotBlank()) { "$label must not be blank" }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decode(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
