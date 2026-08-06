#pragma once

#include "llama.h"

#include <cstdint>
#include <memory>
#include <string>

using GenerationSampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;

GenerationSampler create_generation_sampler(
    const llama_vocab* vocab,
    float temperature,
    float top_p,
    std::int32_t top_k,
    float repeat_penalty,
    std::int32_t repeat_last_n,
    std::uint32_t seed,
    const std::string& grammar = {}
);
