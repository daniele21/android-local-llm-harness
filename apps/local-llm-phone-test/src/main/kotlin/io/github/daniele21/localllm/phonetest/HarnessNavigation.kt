package io.github.daniele21.localllm.phonetest

import java.nio.charset.StandardCharsets
import java.util.Base64

internal enum class HarnessSettingsDetail(val route: String, val title: String, val subtitle: String) {
    PRIVACY(
        route = "settings/privacy",
        title = "Privacy",
        subtitle = "On-device data boundaries",
    ),
    STORAGE(
        route = "settings/storage",
        title = "Storage",
        subtitle = "Models and local data",
    ),
    BUILD(
        route = "settings/build",
        title = "Build information",
        subtitle = "Application and runtime identity",
    ),
    DEVELOPER_TOOLS(
        route = "settings/developer-tools",
        title = "Developer tools",
        subtitle = "Diagnostics and validation",
    ),
    PHYSICAL_VALIDATION(
        route = "settings/developer-tools/physical-validation",
        title = "Physical validation",
        subtitle = "Real-device GGUF evidence",
    ),
}

internal data class HarnessShellState(
    val destination: HarnessDestination,
    val detailTitle: String? = null,
    val detailSubtitle: String? = null,
) {
    val isDetail: Boolean
        get() = detailTitle != null

    val showBottomNavigation: Boolean
        get() = !isDetail && destination != HarnessDestination.SETTINGS
}

internal object HarnessRoutes {
    const val REQUEST_ID_ARGUMENT = "requestId"
    const val REQUEST_TIMELINE_PATTERN = "runs/{$REQUEST_ID_ARGUMENT}"
    const val MODEL_IDENTITY_ARGUMENT = "modelIdentity"
    const val MODEL_DETAIL_PATTERN = "models/{$MODEL_IDENTITY_ARGUMENT}"

    private const val REQUEST_TIMELINE_PREFIX = "runs/"
    private const val MODEL_DETAIL_PREFIX = "models/"

    fun requestTimeline(requestId: String): String = REQUEST_TIMELINE_PREFIX + encode(requestId, "requestId")

    fun decodeRequestId(encodedRequestId: String?): String? = decode(encodedRequestId)

    fun modelDetail(item: HarnessModelInventoryItem): String = modelDetail(HarnessModelDetails.identity(item))

    internal fun modelDetail(identity: String): String = MODEL_DETAIL_PREFIX + encode(identity, "model identity")

    fun decodeModelIdentity(encodedIdentity: String?): String? = decode(encodedIdentity)

    fun shellState(route: String?): HarnessShellState {
        val settingsDetail = HarnessSettingsDetail.entries.firstOrNull { it.route == route }
        if (settingsDetail != null) {
            return HarnessShellState(
                destination = HarnessDestination.SETTINGS,
                detailTitle = settingsDetail.title,
                detailSubtitle = settingsDetail.subtitle,
            )
        }
        if (route == REQUEST_TIMELINE_PATTERN || route?.startsWith(REQUEST_TIMELINE_PREFIX) == true) {
            return HarnessShellState(
                destination = HarnessDestination.DIAGNOSTICS,
                detailTitle = "Request timeline",
                detailSubtitle = "Privacy-safe correlated events",
            )
        }
        if (route == MODEL_DETAIL_PATTERN || route?.startsWith(MODEL_DETAIL_PREFIX) == true) {
            return HarnessShellState(
                destination = HarnessDestination.MODELS,
                detailTitle = "Model details",
                detailSubtitle = "Compatibility, integrity and runtime ownership",
            )
        }
        return HarnessShellState(destination = HarnessDestination.fromRoute(route))
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
            String(
                Base64.getUrlDecoder().decode(encoded),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
