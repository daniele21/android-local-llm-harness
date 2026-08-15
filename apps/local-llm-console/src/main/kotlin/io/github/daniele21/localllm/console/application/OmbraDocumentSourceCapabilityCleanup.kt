package io.github.daniele21.localllm.console.application

/**
 * Application-owned cleanup boundary for process-local document source capabilities.
 *
 * The contract deliberately exposes no Android Uri or storage primitive. Document adapters may
 * retain the capability implementation, while workflow reset/process recreation can release it.
 */
internal fun interface OmbraDocumentSourceCapabilityCleanup {
    fun releaseAll()
}

internal val NoOpOmbraDocumentSourceCapabilityCleanup = OmbraDocumentSourceCapabilityCleanup {}
