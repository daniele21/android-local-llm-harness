#include "llama.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

namespace {

constexpr int kSkipReturnCode = 77;
constexpr float kLogitTolerance = 1.0e-4F;

using ModelPtr = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
using ContextPtr = std::unique_ptr<llama_context, decltype(&llama_free)>;

std::vector<llama_token> tokenize(const llama_vocab* vocab, const std::string& text, bool add_special) {
    const std::int32_t required = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<std::int32_t>(text.size()),
        nullptr,
        0,
        add_special,
        true
    );
    if (required == 0) {
        return {};
    }
    const std::int32_t count = required < 0 ? -required : required;
    std::vector<llama_token> tokens(static_cast<std::size_t>(count));
    const std::int32_t written = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<std::int32_t>(text.size()),
        tokens.data(),
        count,
        add_special,
        true
    );
    if (written <= 0) {
        return {};
    }
    tokens.resize(static_cast<std::size_t>(written));
    return tokens;
}

ContextPtr new_context(llama_model* model) {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = 512;
    params.n_batch = 512;
    params.n_ubatch = 512;
    params.n_threads = 2;
    params.n_threads_batch = 2;
    params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    return ContextPtr(llama_init_from_model(model, params), llama_free);
}

bool decode(ContextPtr& context, std::vector<llama_token>& tokens) {
    if (!context || tokens.empty()) {
        return false;
    }
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<std::int32_t>(tokens.size()));
    return llama_decode(context.get(), batch) == 0;
}

std::vector<float> copy_last_logits(llama_context* context, const llama_vocab* vocab) {
    float* logits = llama_get_logits_ith(context, -1);
    const std::int32_t vocabulary_size = llama_vocab_n_tokens(vocab);
    if (logits == nullptr || vocabulary_size <= 0) {
        return {};
    }
    return std::vector<float>(logits, logits + vocabulary_size);
}

std::vector<std::uint8_t> capture_sequence_state(llama_context* context) {
    const std::size_t size = llama_state_seq_get_size(context, 0);
    if (size == 0) {
        return {};
    }
    std::vector<std::uint8_t> state(size);
    const std::size_t written = llama_state_seq_get_data(context, state.data(), state.size(), 0);
    if (written == 0 || written > state.size()) {
        return {};
    }
    state.resize(written);
    return state;
}

bool restore_sequence_state(llama_context* context, const std::vector<std::uint8_t>& state) {
    return !state.empty() && llama_state_seq_set_data(context, state.data(), state.size(), 0) > 0;
}

bool logits_equivalent(const std::vector<float>& expected, const std::vector<float>& actual) {
    if (expected.size() != actual.size() || expected.empty()) {
        return false;
    }
    float maximum_delta = 0.0F;
    for (std::size_t index = 0; index < expected.size(); ++index) {
        if (!std::isfinite(expected[index]) || !std::isfinite(actual[index])) {
            if (expected[index] != actual[index]) {
                return false;
            }
            continue;
        }
        maximum_delta = std::max(maximum_delta, std::abs(expected[index] - actual[index]));
    }
    std::cout << "maximum logit delta=" << maximum_delta << '\n';
    return maximum_delta <= kLogitTolerance;
}

std::vector<float> clean_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<llama_token>& prefix,
    const std::vector<llama_token>& suffix
) {
    ContextPtr context = new_context(model);
    if (!context) {
        return {};
    }
    std::vector<llama_token> prefix_copy = prefix;
    std::vector<llama_token> suffix_copy = suffix;
    if (!decode(context, prefix_copy) || !decode(context, suffix_copy)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

std::vector<float> restored_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<std::uint8_t>& prefix_state,
    const std::vector<llama_token>& suffix
) {
    ContextPtr context = new_context(model);
    if (!context || !restore_sequence_state(context.get(), prefix_state)) {
        return {};
    }
    std::vector<llama_token> suffix_copy = suffix;
    if (!decode(context, suffix_copy)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

}  // namespace

int main() {
    const char* model_path = std::getenv("LOCAL_LLM_LLRT4_MODEL");
    if (model_path == nullptr || model_path[0] == '\0') {
        std::cout << "LLRT-4 probe skipped: set LOCAL_LLM_LLRT4_MODEL to an exact GGUF artifact path\n";
        return kSkipReturnCode;
    }

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = true;
    model_params.use_mlock = false;
    ModelPtr model(llama_model_load_from_file(model_path, model_params), llama_model_free);
    if (!model) {
        std::cerr << "LLRT-4 probe failed: unable to load model\n";
        llama_backend_free();
        return 1;
    }

    const llama_vocab* vocab = llama_model_get_vocab(model.get());
    const std::vector<llama_token> prefix = tokenize(vocab, "The following statement is deterministic:", true);
    const std::vector<llama_token> suffix_a = tokenize(vocab, " branch A", false);
    const std::vector<llama_token> suffix_b = tokenize(vocab, " branch B", false);
    if (prefix.empty() || suffix_a.empty() || suffix_b.empty()) {
        std::cerr << "LLRT-4 probe failed: tokenization returned no tokens\n";
        model.reset();
        llama_backend_free();
        return 1;
    }

    ContextPtr source = new_context(model.get());
    std::vector<llama_token> prefix_copy = prefix;
    if (!source || !decode(source, prefix_copy)) {
        std::cerr << "LLRT-4 probe failed: prefix decode failed\n";
        model.reset();
        llama_backend_free();
        return 1;
    }
    const std::vector<std::uint8_t> prefix_state = capture_sequence_state(source.get());
    source.reset();
    if (prefix_state.empty()) {
        std::cerr << "LLRT-4 probe failed: sequence state capture is unavailable\n";
        model.reset();
        llama_backend_free();
        return 1;
    }

    const std::vector<float> clean_a = clean_logits(model.get(), vocab, prefix, suffix_a);
    const std::vector<float> restored_a = restored_logits(model.get(), vocab, prefix_state, suffix_a);
    const std::vector<float> clean_b = clean_logits(model.get(), vocab, prefix, suffix_b);
    const std::vector<float> restored_b = restored_logits(model.get(), vocab, prefix_state, suffix_b);

    const bool append_equivalent = logits_equivalent(clean_a, restored_a);
    const bool divergent_equivalent = logits_equivalent(clean_b, restored_b);
    std::cout << "append-only equivalence=" << (append_equivalent ? "PASS" : "FAIL") << '\n';
    std::cout << "divergent-branch equivalence=" << (divergent_equivalent ? "PASS" : "FAIL") << '\n';

    model.reset();
    llama_backend_free();
    return append_equivalent && divergent_equivalent ? 0 : 1;
}
