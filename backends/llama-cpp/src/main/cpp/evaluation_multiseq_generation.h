#pragma once

#include "llama.h"

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace local_llm {

enum class EvaluationMultiSequenceGenerationError {
    NONE,
    INVALID_ARGUMENT,
    CONTEXT_OVERFLOW,
    BATCH_ALLOCATION_FAILED,
    SAMPLER_FAILED,
    DECODE_FAILED,
    TOKEN_DECODE_FAILED,
    CLEANUP_FAILED,
};

enum class EvaluationMultiSequenceCaseStatus {
    COMPLETED,
    CANCELLED,
};

struct EvaluationMultiSequenceCaseInput {
    std::string request_id;
    std::vector<llama_token> prompt_tokens;
    llama_sampler* sampler = nullptr;
    std::int32_t max_output_tokens = 0;
    std::vector<llama_token> stop_token_ids;
    std::vector<std::string> stop_sequences;
    std::shared_ptr<std::atomic_bool> cancellation;
    bool grammar_constrained = false;
};

struct EvaluationMultiSequenceCaseResult {
    std::string request_id;
    EvaluationMultiSequenceCaseStatus status = EvaluationMultiSequenceCaseStatus::COMPLETED;
    std::string output;
    std::size_t input_tokens = 0;
    std::int32_t output_tokens = 0;
    std::int64_t prompt_duration_ms = 0;
    std::int64_t generation_duration_ms = 0;
    std::string stop_reason = "UNKNOWN";
};

struct EvaluationMultiSequenceGenerationResult {
    EvaluationMultiSequenceGenerationError error = EvaluationMultiSequenceGenerationError::NONE;
    std::string error_message;
    std::vector<EvaluationMultiSequenceCaseResult> cases;

    bool ok() const {
        return error == EvaluationMultiSequenceGenerationError::NONE;
    }
};

bool append_evaluation_multi_sequence_batch_token(
    llama_batch& batch,
    std::int32_t capacity,
    llama_token token,
    llama_pos position,
    llama_seq_id sequence_id,
    bool logits);

EvaluationMultiSequenceGenerationResult generate_evaluation_multi_sequence(
    llama_context* context,
    const llama_vocab* vocab,
    std::int32_t batch_size,
    std::uint32_t per_sequence_context_size,
    std::vector<EvaluationMultiSequenceCaseInput>& cases);

}  // namespace local_llm
