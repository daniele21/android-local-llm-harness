#include "llama.h"
#include "model_load_params_compat.h"

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
constexpr int kRepeatCycles = 3;

using ModelPtr = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
using ContextPtr = std::unique_ptr<llama_context, decltype(&llama_free)>;

struct EquivalenceResult final {
    bool equivalent = false;
    float maximum_delta = 0.0F;
};

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

bool decode(ContextPtr& context, const std::vector<llama_token>& tokens) {
    if (!context || tokens.empty()) {
        return false;
    }
    std::vector<llama_token> copy = tokens;
    llama_batch batch = llama_batch_get_one(copy.data(), static_cast<std::int32_t>(copy.size()));
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

EquivalenceResult compare_logits(const std::vector<float>& expected, const std::vector<float>& actual) {
    EquivalenceResult result;
    if (expected.size() != actual.size() || expected.empty()) {
        return result;
    }
    for (std::size_t index = 0; index < expected.size(); ++index) {
        if (!std::isfinite(expected[index]) || !std::isfinite(actual[index])) {
            if (expected[index] != actual[index]) {
                return result;
            }
            continue;
        }
        result.maximum_delta = std::max(result.maximum_delta, std::abs(expected[index] - actual[index]));
    }
    result.equivalent = result.maximum_delta <= kLogitTolerance;
    return result;
}

std::vector<float> clean_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<llama_token>& prefix,
    const std::vector<llama_token>& suffix
) {
    ContextPtr context = new_context(model);
    if (!context || !decode(context, prefix) || !decode(context, suffix)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

std::vector<float> restored_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<std::uint8_t>& prefix_state,
    const std::vector<llama_token>& suffix,
    bool clear_before_restore
) {
    ContextPtr context = new_context(model);
    if (!context) {
        return {};
    }
    if (clear_before_restore) {
        llama_memory_clear(llama_get_memory(context.get()), true);
    }
    if (!restore_sequence_state(context.get(), prefix_state) || !decode(context, suffix)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

std::vector<float> full_remove_then_restore_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<llama_token>& prefix,
    const std::vector<llama_token>& first_suffix,
    const std::vector<std::uint8_t>& prefix_state,
    const std::vector<llama_token>& replacement_suffix,
    bool& removal_supported
) {
    ContextPtr context = new_context(model);
    if (!context || !decode(context, prefix) || !decode(context, first_suffix)) {
        return {};
    }
    llama_memory_t memory = llama_get_memory(context.get());
    removal_supported = llama_memory_seq_rm(memory, 0, 0, -1);
    if (!removal_supported || !restore_sequence_state(context.get(), prefix_state) || !decode(context, replacement_suffix)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

std::vector<float> partial_rollback_logits(
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<llama_token>& prefix,
    const std::vector<llama_token>& first_suffix,
    const std::vector<llama_token>& replacement_suffix,
    bool& rollback_supported
) {
    ContextPtr context = new_context(model);
    if (!context || !decode(context, prefix) || !decode(context, first_suffix)) {
        return {};
    }
    llama_memory_t memory = llama_get_memory(context.get());
    rollback_supported = llama_memory_seq_rm(
        memory,
        0,
        static_cast<llama_pos>(prefix.size()),
        -1
    );
    if (!rollback_supported || !decode(context, replacement_suffix)) {
        return {};
    }
    return copy_last_logits(context.get(), vocab);
}

void print_equivalence(const char* label, const EquivalenceResult& result) {
    std::cout << "LLRT4 " << label << ' ' << (result.equivalent ? "PASS" : "FAIL")
              << " maxDelta=" << result.maximum_delta << '\n';
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
    local_llm::apply_legacy_model_load_policy(model_params, true, false);
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
    if (!source || !decode(source, prefix)) {
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
    const std::vector<float> clean_b = clean_logits(model.get(), vocab, prefix, suffix_b);
    if (clean_a.empty() || clean_b.empty()) {
        std::cerr << "LLRT-4 probe failed: clean-context reference decode failed\n";
        model.reset();
        llama_backend_free();
        return 1;
    }

    const EquivalenceResult append_result = compare_logits(
        clean_a,
        restored_logits(model.get(), vocab, prefix_state, suffix_a, false)
    );
    const EquivalenceResult divergent_result = compare_logits(
        clean_b,
        restored_logits(model.get(), vocab, prefix_state, suffix_b, false)
    );
    const EquivalenceResult clear_restore_result = compare_logits(
        clean_a,
        restored_logits(model.get(), vocab, prefix_state, suffix_a, true)
    );

    bool repeated_equivalent = true;
    float repeated_maximum_delta = 0.0F;
    for (int cycle = 0; cycle < kRepeatCycles; ++cycle) {
        const EquivalenceResult cycle_a = compare_logits(
            clean_a,
            restored_logits(model.get(), vocab, prefix_state, suffix_a, false)
        );
        const EquivalenceResult cycle_b = compare_logits(
            clean_b,
            restored_logits(model.get(), vocab, prefix_state, suffix_b, false)
        );
        repeated_equivalent = repeated_equivalent && cycle_a.equivalent && cycle_b.equivalent;
        repeated_maximum_delta = std::max({
            repeated_maximum_delta,
            cycle_a.maximum_delta,
            cycle_b.maximum_delta,
        });
    }
    const EquivalenceResult repeated_result{repeated_equivalent, repeated_maximum_delta};

    bool full_remove_supported = false;
    const EquivalenceResult full_remove_result = compare_logits(
        clean_b,
        full_remove_then_restore_logits(
            model.get(),
            vocab,
            prefix,
            suffix_a,
            prefix_state,
            suffix_b,
            full_remove_supported
        )
    );

    bool partial_rollback_supported = false;
    EquivalenceResult partial_rollback_result;
    const std::vector<float> partial_logits = partial_rollback_logits(
        model.get(),
        vocab,
        prefix,
        suffix_a,
        suffix_b,
        partial_rollback_supported
    );
    if (partial_rollback_supported) {
        partial_rollback_result = compare_logits(clean_b, partial_logits);
    }

    print_equivalence("append-only-equivalence", append_result);
    print_equivalence("divergent-restore-equivalence", divergent_result);
    print_equivalence("clear-restore-equivalence", clear_restore_result);
    print_equivalence("repeated-restore-equivalence", repeated_result);
    std::cout << "LLRT4 full-sequence-remove " << (full_remove_supported ? "SUPPORTED" : "UNSUPPORTED") << '\n';
    if (full_remove_supported) {
        print_equivalence("full-remove-restore-equivalence", full_remove_result);
    }
    std::cout << "LLRT4 partial-rollback " << (partial_rollback_supported ? "SUPPORTED" : "UNSUPPORTED") << '\n';
    if (partial_rollback_supported) {
        print_equivalence("partial-rollback-equivalence", partial_rollback_result);
    }

    const bool native_state_compatible =
        append_result.equivalent &&
        divergent_result.equivalent &&
        clear_restore_result.equivalent &&
        repeated_result.equivalent &&
        full_remove_supported &&
        full_remove_result.equivalent &&
        partial_rollback_supported &&
        partial_rollback_result.equivalent;

    std::cout << "LLRT4_NATIVE_VERDICT "
              << (native_state_compatible ? "NATIVE_STATE_COMPATIBLE" : "KEEP_DISABLED")
              << '\n';
    std::cout << "LLRT4_NOTE production reuse remains disabled until runtime-level cancellation, pressure, switch and structured-mode evidence is complete\n";

    model.reset();
    llama_backend_free();
    return 0;
}
