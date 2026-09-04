#pragma once

#include "llama.h"

#include <cstdint>

namespace local_llm {

constexpr std::uint32_t kEvaluationMultiSequenceMinWidth = 2;
constexpr std::uint32_t kEvaluationMultiSequenceMaxWidth = 4;
constexpr std::uint32_t kEvaluationMultiSequenceContextAlignment = 256;

enum class EvaluationMultiSequenceContextError {
    NONE,
    INVALID_SEQUENCE_WIDTH,
    INVALID_PER_SEQUENCE_CONTEXT,
    BATCH_TOO_SMALL,
    CONTEXT_OVERFLOW,
};

struct EvaluationMultiSequenceContextPlan {
    std::uint32_t per_sequence_context_size = 0;
    std::uint32_t aggregate_context_size = 0;
    std::uint32_t max_sequences = 0;
};

struct EvaluationMultiSequenceContextPlanResult {
    EvaluationMultiSequenceContextError error = EvaluationMultiSequenceContextError::NONE;
    EvaluationMultiSequenceContextPlan plan{};

    bool ok() const {
        return error == EvaluationMultiSequenceContextError::NONE;
    }
};

EvaluationMultiSequenceContextPlanResult plan_evaluation_multi_sequence_context(
    std::uint32_t per_sequence_context_size,
    std::uint32_t max_sequences,
    std::uint32_t batch_size);

EvaluationMultiSequenceContextError apply_evaluation_multi_sequence_context_params(
    llama_context_params& params,
    std::uint32_t per_sequence_context_size,
    std::uint32_t max_sequences);

}  // namespace local_llm
