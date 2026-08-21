package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile

/**
 * Exact K/V cache data-type names accepted by the pinned llama.cpp revision.
 *
 * The Kotlin boundary validates the exact wire vocabulary before JNI. Native
 * materialization performs the same validation again and applies explicit
 * values to llama_context_params.type_k/type_v. Null values intentionally keep
 * the pinned upstream defaults unchanged.
 */
internal enum class NativeKvCacheType(val wireName: String, val quantized: Boolean) {
    F32("f32", false),
    F16("f16", false),
    BF16("bf16", false),
    Q8_0("q8_0", true),
    Q4_0("q4_0", true),
    Q4_1("q4_1", true),
    IQ4_NL("iq4_nl", true),
    Q5_0("q5_0", true),
    Q5_1("q5_1", true),
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
    val unsupportedName = requested.firstOrNull { (_, value) -> NativeKvCacheType.fromWireName(value) == null }
        ?: return null

    return GenerationNativeError(
        code = GenerationNativeErrorCode.INVALID_ARGUMENT,
        message = "Unsupported ${unsupportedName.first} cache type: ${unsupportedName.second}",
    )
}

internal fun GgufModelProfile.kvCacheCompatibilityError(
    flashAttentionMode: NativeFlashAttentionMode,
): GenerationNativeError? {
    val valueType = kvCacheTypeV?.let(NativeKvCacheType::fromWireName) ?: return null
    if (valueType.quantized && flashAttentionMode == NativeFlashAttentionMode.DISABLED) {
        return GenerationNativeError(
            code = GenerationNativeErrorCode.UNSUPPORTED_CONFIGURATION,
            message = "Quantized V cache requires Flash Attention on the pinned llama.cpp revision",
        )
    }
    return null
}
