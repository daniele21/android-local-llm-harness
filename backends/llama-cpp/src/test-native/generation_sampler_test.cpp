#include "generation_sampler.h"

#include <cassert>
#include <string>

namespace {

void assert_sampler_name(llama_sampler* chain, int index, const char* expected) {
    llama_sampler* sampler = llama_sampler_chain_get(chain, index);
    assert(sampler != nullptr);
    assert(std::string(llama_sampler_name(sampler)) == expected);
}

}  // namespace

int main() {
    auto sampled = create_generation_sampler(
        nullptr,
        0.7F,
        0.8F,
        20,
        0.05F,
        1.5F,
        1.0F,
        64,
        42
    );
    assert(sampled != nullptr);
    assert(llama_sampler_chain_n(sampled.get()) == 6);
    assert_sampler_name(sampled.get(), 0, "penalties");
    assert_sampler_name(sampled.get(), 1, "top-k");
    assert_sampler_name(sampled.get(), 2, "top-p");
    assert_sampler_name(sampled.get(), 3, "min-p");
    assert_sampler_name(sampled.get(), 4, "temp");
    assert_sampler_name(sampled.get(), 5, "dist");

    auto sampled_without_min_p = create_generation_sampler(
        nullptr,
        1.0F,
        1.0F,
        20,
        0.0F,
        2.0F,
        1.0F,
        64,
        42
    );
    assert(sampled_without_min_p != nullptr);
    assert(llama_sampler_chain_n(sampled_without_min_p.get()) == 5);
    assert_sampler_name(sampled_without_min_p.get(), 0, "penalties");
    assert_sampler_name(sampled_without_min_p.get(), 1, "top-k");
    assert_sampler_name(sampled_without_min_p.get(), 2, "top-p");
    assert_sampler_name(sampled_without_min_p.get(), 3, "temp");
    assert_sampler_name(sampled_without_min_p.get(), 4, "dist");

    auto greedy = create_generation_sampler(
        nullptr,
        0.0F,
        1.0F,
        0,
        0.0F,
        0.0F,
        1.0F,
        64,
        42
    );
    assert(greedy != nullptr);
    assert(llama_sampler_chain_n(greedy.get()) == 2);
    assert_sampler_name(greedy.get(), 0, "penalties");
    assert_sampler_name(greedy.get(), 1, "greedy");
    return 0;
}
