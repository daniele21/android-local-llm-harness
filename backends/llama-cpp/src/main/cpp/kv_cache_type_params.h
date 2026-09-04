#pragma once

#include "llama.h"

#include <string_view>

namespace local_llm {

inline bool kv_cache_type_from_wire_name(std::string_view value, ggml_type& output) {
    if (value == "f32") {
        output = GGML_TYPE_F32;
        return true;
    }
    if (value == "f16") {
        output = GGML_TYPE_F16;
        return true;
    }
    if (value == "bf16") {
        output = GGML_TYPE_BF16;
        return true;
    }
    if (value == "q8_0") {
        output = GGML_TYPE_Q8_0;
        return true;
    }
    if (value == "q4_0") {
        output = GGML_TYPE_Q4_0;
        return true;
    }
    if (value == "q4_1") {
        output = GGML_TYPE_Q4_1;
        return true;
    }
    if (value == "iq4_nl") {
        output = GGML_TYPE_IQ4_NL;
        return true;
    }
    if (value == "q5_0") {
        output = GGML_TYPE_Q5_0;
        return true;
    }
    if (value == "q5_1") {
        output = GGML_TYPE_Q5_1;
        return true;
    }
    return false;
}

inline bool apply_kv_cache_type_overrides(
    llama_context_params& params,
    const char* key_type,
    const char* value_type
) {
    ggml_type parsed_key = params.type_k;
    ggml_type parsed_value = params.type_v;

    if (key_type != nullptr && !kv_cache_type_from_wire_name(key_type, parsed_key)) {
        return false;
    }
    if (value_type != nullptr && !kv_cache_type_from_wire_name(value_type, parsed_value)) {
        return false;
    }
    if (value_type != nullptr &&
        ggml_is_quantized(parsed_value) &&
        params.flash_attn_type == LLAMA_FLASH_ATTN_TYPE_DISABLED) {
        return false;
    }

    params.type_k = parsed_key;
    params.type_v = parsed_value;
    return true;
}

}  // namespace local_llm
