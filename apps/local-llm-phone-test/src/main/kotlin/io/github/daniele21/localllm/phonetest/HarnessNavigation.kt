package io.github.daniele21.localllm.phonetest

import java.nio.charset.StandardCharsets
import java.util.Base64

internal enum class HarnessSettingsDetail(
    val route: String,
    val title: String,
    val subtitle: String,
) {
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

    private const val REQUEST_TIMELINE_PREFIX = "runs/"

    fun requestTimeline(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(requestId.toByteArray(StandardCharsets.UTF_8))
        return REQUEST_TIMELINE_PREFIX + encoded
    }

    fun decodeRequestId(encodedRequestId: String?): String? {
        if (encodedRequestId.isNullOrBlank()) return null
        return runCatching {
            String(
                Base64.getUrlDecoder().decode(encodedRequestId),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

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
        return HarnessShellState(destination = HarnessDestination.fromRoute(route))
    }
}
