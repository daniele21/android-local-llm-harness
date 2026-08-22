#include "generation_sampler.h"

#include <cassert>
#include <string>
#include <vector>

namespace {

constexpr std::int32_t kFixtureVocabularySize = 1024;

struct RecordingSamplerContext final {
    std::vector<llama_token> accepted;
};

const char* recording_name(const llama_sampler* /* sampler */) {
    return "recording";
}

void recording_accept(llama_sampler* sampler, llama_token token) {
    auto* context = static_cast<RecordingSamplerContext*>(sampler->ctx);
    context->accepted.push_back(token);
}

void recording_apply(llama_sampler* /* sampler */, llama_token_data_array* /* candidates */) {}

void recording_reset(llama_sampler* sampler) {
    auto* context = static_cast<RecordingSamplerContext*>(sampler->ctx);
    context->accepted.clear();
}

llama_sampler* recording_clone(const llama_sampler* sampler);

void recording_free(llama_sampler* sampler) {
    delete static_cast<RecordingSamplerContext*>(sampler->ctx);
}

llama_sampler_i recording_interface = {
    /* .name = */ recording_name,
    /* .accept = */ recording_accept,
    /* .apply = */ recording_apply,
    /* .reset = */ recording_reset,
    /* .clone = */ recording_clone,
    /* .free = */ recording_free,
    /* .backend_init = */ nullptr,
    /* .backend_accept = */ nullptr,
    /* .backend_apply = */ nullptr,
    /* .backend_set_input = */ nullptr,
};

llama_sampler* recording_clone(const llama_sampler* sampler) {
    const auto* source = static_cast<const RecordingSamplerContext*>(sampler->ctx);
    auto* context = new RecordingSamplerContext(*source);
    return llama_sampler_init(&recording_interface, context);
}

void assert_accept_once_normalization() {
    auto* context = new RecordingSamplerContext();
    GenerationSampler recording(llama_sampler_init(&recording_interface, context), llama_sampler_free);
    auto normalized = normalize_sampled_token_acceptance(std::move(recording));
    assert(normalized != nullptr);
    assert(std::string(llama_sampler_name(normalized.get())) == "harness-sampled-token-accept-once");

    llama_token_data candidate{7, 1.0F, 0.0F};
    llama_token_data_array candidates{&candidate, 1, -1, false};

    // llama_sampler_sample() performs apply -> accept internally. The current
    // Harness decode loops then perform one compatibility accept for the same
    // sampled token. Only the internal accept must reach stateful samplers.
    llama_sampler_apply(normalized.get(), &candidates);
    llama_sampler_accept(normalized.get(), 7);
    llama_sampler_accept(normalized.get(), 7);
    assert((context->accepted == std::vector<llama_token>{7}));

    // Directly injected tokens do not follow an apply and must never be
    // deduplicated, including when the injected token repeats.
    llama_sampler_accept(normalized.get(), 8);
    llama_sampler_accept(normalized.get(), 8);
    assert((context->accepted == std::vector<llama_token>{7, 8, 8}));

    // A new apply starts a new sampled-token epoch, so the same model token is
    // accepted again on the next generation step.
    llama_sampler_apply(normalized.get(), &candidates);
    llama_sampler_accept(normalized.get(), 7);
    llama_sampler_accept(normalized.get(), 7);
    assert((context->accepted == std::vector<llama_token>{7, 8, 8, 7}));

    llama_sampler_reset(normalized.get());
    assert(context->accepted.empty());
}

void assert_factory_returns_normalized_sampler(float temperature, float min_p) {
    auto sampler = create_generation_sampler(
        nullptr,
        temperature,
        0.8F,
        20,
        min_p,
        1.5F,
        1.0F,
        64,
        42,
        {},
        kFixtureVocabularySize
    );
    assert(sampler != nullptr);
    assert(std::string(llama_sampler_name(sampler.get())) == "harness-sampled-token-accept-once");
}

}  // namespace

int main() {
    assert_factory_returns_normalized_sampler(0.7F, 0.05F);
    assert_factory_returns_normalized_sampler(1.0F, 0.0F);
    assert_factory_returns_normalized_sampler(0.0F, 0.0F);
    assert_accept_once_normalization();
    return 0;
}
