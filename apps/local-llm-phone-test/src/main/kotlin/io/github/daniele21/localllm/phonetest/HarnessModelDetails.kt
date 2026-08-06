package io.github.daniele21.localllm.phonetest

import java.util.Locale

internal enum class HarnessModelDetailTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

internal enum class HarnessModelRecoveryAction {
    ADOPT_LOADED_SELECTION,
    RELEASE_RUNTIME,
}

internal data class HarnessModelRecoveryRequest(val identity: String, val action: HarnessModelRecoveryAction)

internal data class HarnessModelRecoveryOption(
    val action: HarnessModelRecoveryAction,
    val label: String,
    val detail: String,
    val requiresConfirmation: Boolean,
)

internal data class HarnessModelDetailPresentation(
    val identity: String,
    val title: String,
    val subtitle: String,
    val origin: String,
    val lifecycle: String,
    val compatibility: String,
    val integrity: String,
    val installation: String,
    val selection: String,
    val runtimeOwnership: String,
    val architecture: String,
    val quantization: String,
    val size: String,
    val digest: String,
    val detail: String?,
    val tone: HarnessModelDetailTone,
    val recoveryOptions: List<HarnessModelRecoveryOption>,
)

internal object HarnessModelDetails {
    private const val DIGEST_PREFIX = "digest:"
    private const val STABLE_PREFIX = "stable:"

    fun identity(item: HarnessModelInventoryItem): String = item.digest?.takeIf(String::isNotBlank)?.let(DIGEST_PREFIX::plus)
        ?: STABLE_PREFIX + item.stableId

    fun resolve(inventory: HarnessModelInventoryState, identity: String?): HarnessModelInventoryItem? {
        if (identity.isNullOrBlank()) return null
        return when {
            identity.startsWith(DIGEST_PREFIX) -> {
                val digest = identity.removePrefix(DIGEST_PREFIX)
                inventory.items.firstOrNull { it.digest == digest }
            }

            identity.startsWith(STABLE_PREFIX) -> {
                val stableId = identity.removePrefix(STABLE_PREFIX)
                inventory.items.firstOrNull { it.stableId == stableId }
            }

            else -> null
        }
    }

    fun present(inventory: HarnessModelInventoryState, identity: String?): HarnessModelDetailPresentation? {
        val item = resolve(inventory, identity) ?: return null
        return HarnessModelDetailPresentation(
            identity = identity.orEmpty(),
            title = item.displayName,
            subtitle = "${item.origin.displayLabel()} model",
            origin = item.origin.displayLabel(),
            lifecycle = item.lifecycle.displayLabel(),
            compatibility = if (item.compatible) "Compatible" else "Incompatible",
            integrity = item.integrityLabel(),
            installation = if (item.installed) "Installed" else item.lifecycle.displayLabel(),
            selection = if (item.selected) "Selected for this app" else "Not selected",
            runtimeOwnership = when {
                item.loaded -> "Owned by runtime"
                inventory.loadedDigest == null -> "Runtime released"
                else -> "Not owned by runtime"
            },
            architecture = item.architecture ?: "Unavailable",
            quantization = item.quantization ?: "Unavailable",
            size = item.sizeBytes?.let(::formatBytes) ?: "Unavailable",
            digest = item.digest?.let { "${it.take(16)}…" } ?: "Unavailable",
            detail = item.detail,
            tone = item.detailTone(),
            recoveryOptions = item.recoveryOptions(),
        )
    }

    private fun HarnessModelInventoryItem.integrityLabel(): String = when {
        digest.isNullOrBlank() -> "Digest unavailable"
        lifecycle == HarnessModelLifecycle.FAILED -> "Verification failed"
        lifecycle == HarnessModelLifecycle.DEGRADED -> "Digest recorded; runtime state needs recovery"
        else -> "Digest recorded"
    }

    private fun HarnessModelInventoryItem.detailTone(): HarnessModelDetailTone = when (lifecycle) {
        HarnessModelLifecycle.LOADED,
        HarnessModelLifecycle.SELECTED,
        HarnessModelLifecycle.INSTALLED,
        -> HarnessModelDetailTone.SUCCESS

        HarnessModelLifecycle.DEGRADED,
        HarnessModelLifecycle.CANCELLED,
        -> HarnessModelDetailTone.WARNING

        HarnessModelLifecycle.FAILED,
        HarnessModelLifecycle.INCOMPATIBLE,
        -> HarnessModelDetailTone.ERROR

        else -> HarnessModelDetailTone.NEUTRAL
    }

    private fun HarnessModelInventoryItem.recoveryOptions(): List<HarnessModelRecoveryOption> = when (degradation) {
        HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION -> buildList {
            if (origin == HarnessModelOrigin.CATALOG && installed && loaded) {
                add(
                    HarnessModelRecoveryOption(
                        action = HarnessModelRecoveryAction.ADOPT_LOADED_SELECTION,
                        label = "Use loaded model",
                        detail = "Align the selected model with the compatible model already owned by the runtime.",
                        requiresConfirmation = false,
                    ),
                )
            }
            add(releaseRuntimeOption())
        }

        HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY -> listOf(releaseRuntimeOption())

        null -> emptyList()
    }

    private fun releaseRuntimeOption(): HarnessModelRecoveryOption = HarnessModelRecoveryOption(
        action = HarnessModelRecoveryAction.RELEASE_RUNTIME,
        label = "Release runtime model",
        detail = "Unload the current runtime owner without deleting any model file.",
        requiresConfirmation = true,
    )

    private fun HarnessModelOrigin.displayLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)
    private fun HarnessModelLifecycle.displayLabel(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun formatBytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
}
