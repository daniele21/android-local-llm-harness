#include "kv_cache_type_params.h"

#include <cassert>
#include <utility>
#include <vector>

int main() {
    {
        llama_context_params params = llama_context_default_params();
        const ggml_type default_key = params.type_k;
        const ggml_type default_value = params.type_v;

        assert(local_llm::apply_kv_cache_type_overrides(params, nullptr, nullptr));
        assert(params.type_k == default_key);
        assert(params.type_v == default_value);
    }

    const std::vector<std::pair<const char*, ggml_type>> supported = {
        {"f32", GGML_TYPE_F32},
        {"f16", GGML_TYPE_F16},
        {"bf16", GGML_TYPE_BF16},
        {"q8_0", GGML_TYPE_Q8_0},
        {"q4_0", GGML_TYPE_Q4_0},
        {"q4_1", GGML_TYPE_Q4_1},
        {"iq4_nl", GGML_TYPE_IQ4_NL},
        {"q5_0", GGML_TYPE_Q5_0},
        {"q5_1", GGML_TYPE_Q5_1},
    };

    for (const auto& [wire_name, expected] : supported) {
        ggml_type parsed = GGML_TYPE_COUNT;
        assert(local_llm::kv_cache_type_from_wire_name(wire_name, parsed));
        assert(parsed == expected);

        llama_context_params params = llama_context_default_params();
        assert(local_llm::apply_kv_cache_type_overrides(params, wire_name, wire_name));
        assert(params.type_k == expected);
        assert(params.type_v == expected);
    }

    {
        llama_context_params params = llama_context_default_params();
        const ggml_type default_key = params.type_k;
        const ggml_type default_value = params.type_v;

        assert(!local_llm::apply_kv_cache_type_overrides(params, "q8_0", "q3_not_real"));
        assert(params.type_k == default_key);
        assert(params.type_v == default_value);
    }

    {
        ggml_type parsed = GGML_TYPE_COUNT;
        assert(!local_llm::kv_cache_type_from_wire_name("Q8_0", parsed));
        assert(!local_llm::kv_cache_type_from_wire_name("q4_k", parsed));
        assert(!local_llm::kv_cache_type_from_wire_name("", parsed));
    }

    return 0;
}
