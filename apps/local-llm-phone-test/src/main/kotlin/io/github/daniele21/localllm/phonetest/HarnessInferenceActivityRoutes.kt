package io.github.daniele21.localllm.phonetest

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object HarnessInferenceActivityRoutes {
    const val REQUEST_ID_ARGUMENT = "activityRequestId"
    const val DETAIL_PATTERN = "activity/{$REQUEST_ID_ARGUMENT}"

    fun detail(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        return "activity/" + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(requestId.toByteArray(StandardCharsets.UTF_8))
    }

    fun decodeRequestId(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
