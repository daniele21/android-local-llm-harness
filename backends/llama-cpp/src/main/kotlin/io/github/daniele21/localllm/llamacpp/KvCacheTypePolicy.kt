package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile

/**
 * Exact K/V cache data-type names accepted by the pinned llama.cpp revision.
 *
 * Explicit cache selection is intentionally fail-closed until the JNI context
 * boundary materializes the requested type. This prevents a profile field from
 * changing execution identity while the native context silently keeps its
 * upstream default.
 */
internal enum class NativeKvCacheType(val wireName: String) {
    F32("f32"),
    F16("f16"),
    BF16("bf16"),
    Q8_0("q8_0"),
    Q4_0("q4_0"),
    Q4_1("q4_1"),
    IQ4_NL("iq4_nl"),
    Q5_0("q5_0"),
    Q5_1("q5_1"),
    ;

    companion object {
        fun fromWireName(value: String): NativeKvCacheType? = entries.firstOrNull { it.wireName == value }
    }
}

internal fun GgufModelProfile.explicitKvCacheSelectionError(): GenerationNativeError? {
    val requested = listOfNotNull(
        kvCacheTypeK?.let { "K" to it },
        kvCacheTypeV?.let { "V" to it },
    )
    if (requested.isEmpty()) return null

    val unsupportedName = requested.firstOrNull { (_, value) -> NativeKvCacheType.fromWireName(value) == null }
    if (unsupportedName != null) {
        return GenerationNativeError(
            code = GenerationNativeErrorCode.INVALID_ARGUMENT,
            message = "Unsupported ${unsupportedName.first} cache type: ${unsupportedName.second}",
        )
    }

    return GenerationNativeError(
        code = GenerationNativeErrorCode.UNSUPPORTED_CONFIGURATION,
        message = "Explicit K/V cache types are not materialized by the pinned JNI context boundary yet",
    )
}
