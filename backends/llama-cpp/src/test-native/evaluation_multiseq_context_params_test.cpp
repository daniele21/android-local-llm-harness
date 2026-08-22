#include "evaluation_multiseq_context_params.h"

#include <cstdint>
#include <limits>

namespace {

int expect(bool condition, int code) {
    return condition ? 0 : code;
}

}  // namespace

int main() {
    using local_llm::EvaluationMultiSequenceContextError;

    const auto valid = local_llm::plan_evaluation_multi_sequence_context(2048, 2, 128);
    if (const int code = expect(valid.ok(), 1)) return code;
    if (const int code = expect(valid.plan.per_sequence_context_size == 2048, 2)) return code;
    if (const int code = expect(valid.plan.aggregate_context_size == 4096, 3)) return code;
    if (const int code = expect(valid.plan.max_sequences == 2, 4)) return code;

    if (const int code = expect(
            local_llm::plan_evaluation_multi_sequence_context(2048, 1, 128).error ==
                EvaluationMultiSequenceContextError::INVALID_SEQUENCE_WIDTH,
            5)) {
        return code;
    }
    if (const int code = expect(
            local_llm::plan_evaluation_multi_sequence_context(2048, 5, 128).error ==
                EvaluationMultiSequenceContextError::INVALID_SEQUENCE_WIDTH,
            6)) {
        return code;
    }
    if (const int code = expect(
            local_llm::plan_evaluation_multi_sequence_context(2000, 2, 128).error ==
                EvaluationMultiSequenceContextError::INVALID_PER_SEQUENCE_CONTEXT,
            7)) {
        return code;
    }
    if (const int code = expect(
            local_llm::plan_evaluation_multi_sequence_context(2048, 4, 2).error ==
                EvaluationMultiSequenceContextError::BATCH_TOO_SMALL,
            8)) {
        return code;
    }
    if (const int code = expect(
            local_llm::plan_evaluation_multi_sequence_context(
                std::numeric_limits<std::uint32_t>::max() - 255,
                2,
                128)
                    .error == EvaluationMultiSequenceContextError::CONTEXT_OVERFLOW,
            9)) {
        return code;
    }

    llama_context_params params = llama_context_default_params();
    params.n_batch = 128;
    params.n_ubatch = 64;
    params.kv_unified = true;
    params.swa_full = false;
    const auto applied = local_llm::apply_evaluation_multi_sequence_context_params(params, 2048, 4);
    if (const int code = expect(applied == EvaluationMultiSequenceContextError::NONE, 10)) return code;
    if (const int code = expect(params.n_ctx == 8192, 11)) return code;
    if (const int code = expect(params.n_seq_max == 4, 12)) return code;
    if (const int code = expect(params.n_outputs_max == 4, 13)) return code;
    if (const int code = expect(!params.kv_unified, 14)) return code;
    if (const int code = expect(params.swa_full, 15)) return code;
    if (const int code = expect(params.n_batch == 128, 16)) return code;
    if (const int code = expect(params.n_ubatch == 64, 17)) return code;

    return 0;
}
