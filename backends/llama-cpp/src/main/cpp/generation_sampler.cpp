#include "generation_sampler.h"

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
    const std::string& grammar
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

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_penalties(repeat_last_n, repeat_penalty, 0.0F, presence_penalty)
    );
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
