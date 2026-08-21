#pragma once

#include <type_traits>

namespace local_llm {

// llama.cpp uses the same tri-state integer values on both the Harness 0.5 pin
// and candidate 60addddf3c567c43ec3caf70fc953fba3572d96f. Keep the translation
// isolated so Kotlin/JNI can carry AUTO without changing product defaults.
enum class FlashAttentionMode : int {
    AUTO = -1,
    DISABLED = 0,
    ENABLED = 1,
};

inline bool is_supported_flash_attention_mode(int value) {
    return value >= static_cast<int>(FlashAttentionMode::AUTO) &&
        value <= static_cast<int>(FlashAttentionMode::ENABLED);
}

template <typename Params>
bool apply_flash_attention_mode(Params& params, int value) {
    if (!is_supported_flash_attention_mode(value)) {
        return false;
    }
    params.flash_attn_type = static_cast<decltype(params.flash_attn_type)>(value);
    return true;
}

}  // namespace local_llm
