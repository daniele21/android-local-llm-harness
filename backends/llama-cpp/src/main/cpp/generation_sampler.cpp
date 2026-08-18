#include "generation_sampler.h"

#include <type_traits>

namespace {

template <typename>
inline constexpr bool unsupported_penalties_signature = false;

template <typename PenaltiesFn>
llama_sampler* init_penalties_compat(
    PenaltiesFn penalties_fn,
    std::int32_t vocabulary_size,
    std::int32_t repeat_last_n,
    float repeat_penalty,
    float frequency_penalty,
    float presence_penalty
) {
    if constexpr (
        std::is_invocable_r_v<
            llama_sampler*,
            PenaltiesFn,
            std::int32_t,
            float,
            float,
            float
        >
    ) {
        return penalties_fn(
            repeat_last_n,
            repeat_penalty,
            frequency_penalty,
            presence_penalty
        );
    } else if constexpr (
        std::is_invocable_r_v<
            llama_sampler*,
            PenaltiesFn,
            std::int32_t,
            std::int32_t,
            float,
            float,
            float
        >
    ) {
        if (vocabulary_size <= 0) {
            return nullptr;
        }
        return penalties_fn(
            vocabulary_size,
            repeat_last_n,
            repeat_penalty,
            frequency_penalty,
            presence_penalty
        );
    } else {
        static_assert(
            unsupported_penalties_signature<PenaltiesFn>,
            "Unsupported llama_sampler_init_penalties signature"
        );
    }
}

}  // namespace

GenerationSampler create_generation_sampler(
    const llama_vocab* vocab,
    float temperature,
    float top_p,
    std::int32_t top_k,
    float min_p,
    float presence_penalty,
    float repeat_penalty,
    std::int32_t repeat_last_n,
    std::uint32_t seed,
    const std::string& grammar,
    std::int32_t vocabulary_size_override
) {
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) {
        return {nullptr, llama_sampler_free};
    }

    if (!grammar.empty()) {
        llama_sampler* grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (grammar_sampler == nullptr) {
            llama_sampler_free(sampler);
            return {nullptr, llama_sampler_free};
        }
        llama_sampler_chain_add(sampler, grammar_sampler);
    }

    const std::int32_t vocabulary_size = vocabulary_size_override > 0
        ? vocabulary_size_override
        : (vocab != nullptr ? llama_vocab_n_tokens(vocab) : 0);
    llama_sampler* penalties_sampler = init_penalties_compat(
        &llama_sampler_init_penalties,
        vocabulary_size,
        repeat_last_n,
        repeat_penalty,
        0.0F,
        presence_penalty
    );
    if (penalties_sampler == nullptr) {
        llama_sampler_free(sampler);
        return {nullptr, llama_sampler_free};
    }
    llama_sampler_chain_add(sampler, penalties_sampler);

    if (temperature <= 0.0F) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        if (min_p > 0.0F) {
            llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));
    }
    return {sampler, llama_sampler_free};
}
