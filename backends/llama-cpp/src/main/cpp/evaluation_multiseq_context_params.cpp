#include "evaluation_multiseq_context_params.h"

#include <limits>

namespace local_llm {

EvaluationMultiSequenceContextPlanResult plan_evaluation_multi_sequence_context(
    std::uint32_t per_sequence_context_size,
    std::uint32_t max_sequences,
    std::uint32_t batch_size) {
    if (max_sequences < kEvaluationMultiSequenceMinWidth || max_sequences > kEvaluationMultiSequenceMaxWidth) {
        return {EvaluationMultiSequenceContextError::INVALID_SEQUENCE_WIDTH, {}};
    }
    if (per_sequence_context_size == 0 ||
        per_sequence_context_size % kEvaluationMultiSequenceContextAlignment != 0) {
        return {EvaluationMultiSequenceContextError::INVALID_PER_SEQUENCE_CONTEXT, {}};
    }
    if (batch_size < max_sequences) {
        return {EvaluationMultiSequenceContextError::BATCH_TOO_SMALL, {}};
    }
    if (per_sequence_context_size > std::numeric_limits<std::uint32_t>::max() / max_sequences) {
        return {EvaluationMultiSequenceContextError::CONTEXT_OVERFLOW, {}};
    }

    return {
        EvaluationMultiSequenceContextError::NONE,
        {
            per_sequence_context_size,
            per_sequence_context_size * max_sequences,
            max_sequences,
        },
    };
}

EvaluationMultiSequenceContextError apply_evaluation_multi_sequence_context_params(
    llama_context_params& params,
    std::uint32_t per_sequence_context_size,
    std::uint32_t max_sequences) {
    const auto result = plan_evaluation_multi_sequence_context(
        per_sequence_context_size,
        max_sequences,
        params.n_batch);
    if (!result.ok()) {
        return result.error;
    }

    params.n_ctx = result.plan.aggregate_context_size;
    params.n_seq_max = result.plan.max_sequences;
    params.n_outputs_max = result.plan.max_sequences;
    params.kv_unified = false;
    params.swa_full = true;
    return EvaluationMultiSequenceContextError::NONE;
}

}  // namespace local_llm
