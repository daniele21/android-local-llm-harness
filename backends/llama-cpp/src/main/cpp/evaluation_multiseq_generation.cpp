#include "evaluation_multiseq_generation.h"

#include "generation_output_buffer.h"

#include <algorithm>
#include <chrono>
#include <limits>
#include <unordered_set>
#include <utility>

namespace local_llm {
namespace {

using Clock = std::chrono::steady_clock;

struct SequenceState final {
    EvaluationMultiSequenceCaseInput* input = nullptr;
    EvaluationMultiSequenceCaseResult result;
    llama_seq_id sequence_id = 0;
    llama_token pending_token = LLAMA_TOKEN_NULL;
    llama_pos pending_position = 0;
    bool pending = false;
    bool cleaned = false;
};

std::int64_t elapsed_ms(Clock::time_point started) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - started).count();
}

bool contains_token(const std::vector<llama_token>& values, llama_token token) {
    return std::find(values.begin(), values.end(), token) != values.end();
}

std::string token_piece(const llama_vocab* vocab, llama_token token) {
    std::vector<char> buffer(128);
    std::int32_t written = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<std::int32_t>(buffer.size()),
        0,
        true);
    if (written < 0) {
        buffer.resize(static_cast<std::size_t>(-written));
        written = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<std::int32_t>(buffer.size()),
            0,
            true);
    }
    return written < 0 ? std::string{} : std::string(buffer.data(), static_cast<std::size_t>(written));
}

bool cleanup_sequence(llama_memory_t memory, SequenceState& state) {
    if (state.cleaned) {
        return true;
    }
    if (!llama_memory_seq_rm(memory, state.sequence_id, -1, -1)) {
        return false;
    }
    state.cleaned = true;
    state.pending = false;
    return true;
}

bool cleanup_all(llama_memory_t memory, std::vector<SequenceState>& states) {
    bool ok = true;
    for (auto& state : states) {
        ok = cleanup_sequence(memory, state) && ok;
    }
    return ok;
}

EvaluationMultiSequenceGenerationResult fatal_result(
    EvaluationMultiSequenceGenerationError error,
    std::string message,
    llama_memory_t memory,
    std::vector<SequenceState>& states) {
    if (!cleanup_all(memory, states)) {
        return {
            EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
            "Unable to remove all evaluation sequences after a native batch failure",
            {},
        };
    }
    return {error, std::move(message), {}};
}

EvaluationMultiSequenceGenerationError validate_inputs(
    llama_context* context,
    const llama_vocab* vocab,
    std::int32_t batch_size,
    std::uint32_t per_sequence_context_size,
    const std::vector<EvaluationMultiSequenceCaseInput>& cases) {
    if (context == nullptr || vocab == nullptr || batch_size <= 0 || per_sequence_context_size == 0) {
        return EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT;
    }
    if (cases.size() < 2 || cases.size() > llama_n_seq_max(context) ||
        static_cast<std::size_t>(batch_size) < cases.size()) {
        return EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT;
    }
    if (llama_n_ctx_seq(context) != per_sequence_context_size ||
        per_sequence_context_size > static_cast<std::uint32_t>(std::numeric_limits<llama_pos>::max())) {
        return EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT;
    }

    std::unordered_set<std::string> request_ids;
    request_ids.reserve(cases.size());
    for (const auto& input : cases) {
        if (input.request_id.empty() || !request_ids.insert(input.request_id).second || input.prompt_tokens.empty() ||
            input.sampler == nullptr || input.cancellation == nullptr || input.max_output_tokens <= 0 ||
            input.stop_sequences.end() != std::find(input.stop_sequences.begin(), input.stop_sequences.end(), "")) {
            return EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT;
        }
        if (input.prompt_tokens.size() > static_cast<std::size_t>(per_sequence_context_size) ||
            static_cast<std::size_t>(input.max_output_tokens) >
                static_cast<std::size_t>(per_sequence_context_size) - input.prompt_tokens.size()) {
            return EvaluationMultiSequenceGenerationError::CONTEXT_OVERFLOW;
        }
    }
    return EvaluationMultiSequenceGenerationError::NONE;
}

bool finish_cancelled(llama_memory_t memory, SequenceState& state, std::int64_t generation_ms) {
    state.result.status = EvaluationMultiSequenceCaseStatus::CANCELLED;
    state.result.output.clear();
    state.result.stop_reason = "UNKNOWN";
    state.result.generation_duration_ms = generation_ms;
    return cleanup_sequence(memory, state);
}

EvaluationMultiSequenceGenerationError process_sample(
    llama_memory_t memory,
    const llama_vocab* vocab,
    SequenceState& state,
    llama_token token,
    std::int64_t generation_ms) {
    auto& input = *state.input;
    auto& result = state.result;

    if (token == LLAMA_TOKEN_NULL) {
        return EvaluationMultiSequenceGenerationError::SAMPLER_FAILED;
    }
    if (llama_vocab_is_eog(vocab, token)) {
        result.stop_reason = input.grammar_constrained ? "GRAMMAR_COMPLETE" : "END_OF_GENERATION";
        result.generation_duration_ms = generation_ms;
        return cleanup_sequence(memory, state)
            ? EvaluationMultiSequenceGenerationError::NONE
            : EvaluationMultiSequenceGenerationError::CLEANUP_FAILED;
    }
    if (contains_token(input.stop_token_ids, token)) {
        result.stop_reason = "STOP_SEQUENCE";
        result.generation_duration_ms = generation_ms;
        return cleanup_sequence(memory, state)
            ? EvaluationMultiSequenceGenerationError::NONE
            : EvaluationMultiSequenceGenerationError::CLEANUP_FAILED;
    }

    const std::string piece = token_piece(vocab, token);
    if (piece.empty()) {
        return EvaluationMultiSequenceGenerationError::TOKEN_DECODE_FAILED;
    }
    result.output.append(piece);
    ++result.output_tokens;
    llama_sampler_accept(input.sampler, token);

    const auto stop_position = earliest_stop_position(result.output, input.stop_sequences);
    if (stop_position.has_value()) {
        result.output.resize(stop_position.value());
        result.stop_reason = "STOP_SEQUENCE";
        result.generation_duration_ms = generation_ms;
        return cleanup_sequence(memory, state)
            ? EvaluationMultiSequenceGenerationError::NONE
            : EvaluationMultiSequenceGenerationError::CLEANUP_FAILED;
    }

    if (result.output_tokens >= input.max_output_tokens) {
        result.stop_reason = "MAX_OUTPUT_TOKENS";
        result.generation_duration_ms = generation_ms;
        return cleanup_sequence(memory, state)
            ? EvaluationMultiSequenceGenerationError::NONE
            : EvaluationMultiSequenceGenerationError::CLEANUP_FAILED;
    }

    const std::size_t token_position = input.prompt_tokens.size() +
        static_cast<std::size_t>(result.output_tokens) - 1U;
    if (token_position > static_cast<std::size_t>(std::numeric_limits<llama_pos>::max())) {
        return EvaluationMultiSequenceGenerationError::CONTEXT_OVERFLOW;
    }
    state.pending_token = token;
    state.pending_position = static_cast<llama_pos>(token_position);
    state.pending = true;
    return EvaluationMultiSequenceGenerationError::NONE;
}

}  // namespace

bool append_evaluation_multi_sequence_batch_token(
    llama_batch& batch,
    std::int32_t capacity,
    llama_token token,
    llama_pos position,
    llama_seq_id sequence_id,
    bool logits) {
    if (capacity <= 0 || batch.n_tokens < 0 || batch.n_tokens >= capacity || batch.token == nullptr ||
        batch.pos == nullptr || batch.n_seq_id == nullptr || batch.seq_id == nullptr || batch.logits == nullptr ||
        sequence_id < 0) {
        return false;
    }
    const std::int32_t index = batch.n_tokens++;
    batch.token[index] = token;
    batch.pos[index] = position;
    batch.n_seq_id[index] = 1;
    batch.seq_id[index][0] = sequence_id;
    batch.logits[index] = logits ? 1 : 0;
    return true;
}

EvaluationMultiSequenceGenerationResult generate_evaluation_multi_sequence(
    llama_context* context,
    const llama_vocab* vocab,
    std::int32_t batch_size,
    std::uint32_t per_sequence_context_size,
    std::vector<EvaluationMultiSequenceCaseInput>& cases) {
    const auto validation = validate_inputs(context, vocab, batch_size, per_sequence_context_size, cases);
    if (validation != EvaluationMultiSequenceGenerationError::NONE) {
        return {validation, "Invalid evaluation multi-sequence generation inputs", {}};
    }

    llama_memory_t memory = llama_get_memory(context);
    if (memory == nullptr) {
        return {
            EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT,
            "Evaluation multi-sequence context does not expose memory",
            {},
        };
    }

    llama_batch batch = llama_batch_init(batch_size, 0, 1);
    if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr || batch.seq_id == nullptr ||
        batch.logits == nullptr) {
        llama_batch_free(batch);
        return {
            EvaluationMultiSequenceGenerationError::BATCH_ALLOCATION_FAILED,
            "Unable to allocate the evaluation multi-sequence batch",
            {},
        };
    }

    std::vector<SequenceState> states;
    states.reserve(cases.size());
    for (std::size_t index = 0; index < cases.size(); ++index) {
        SequenceState state;
        state.input = &cases[index];
        state.sequence_id = static_cast<llama_seq_id>(index);
        state.result.request_id = cases[index].request_id;
        state.result.input_tokens = cases[index].prompt_tokens.size();
        state.result.stop_reason = "MAX_OUTPUT_TOKENS";
        states.push_back(std::move(state));
    }

    llama_memory_clear(memory, true);

    for (auto& state : states) {
        auto& input = *state.input;
        const auto prompt_started = Clock::now();
        std::size_t offset = 0;
        while (offset < input.prompt_tokens.size()) {
            if (input.cancellation->load(std::memory_order_acquire)) {
                state.result.prompt_duration_ms = elapsed_ms(prompt_started);
                if (!finish_cancelled(memory, state, 0)) {
                    llama_batch_free(batch);
                    return fatal_result(
                        EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
                        "Unable to clean a cancelled evaluation sequence during prefill",
                        memory,
                        states);
                }
                break;
            }

            batch.n_tokens = 0;
            const std::size_t remaining = input.prompt_tokens.size() - offset;
            const std::int32_t chunk_size = static_cast<std::int32_t>(
                std::min<std::size_t>(remaining, static_cast<std::size_t>(batch_size)));
            for (std::int32_t index = 0; index < chunk_size; ++index) {
                const std::size_t prompt_index = offset + static_cast<std::size_t>(index);
                if (!append_evaluation_multi_sequence_batch_token(
                        batch,
                        batch_size,
                        input.prompt_tokens[prompt_index],
                        static_cast<llama_pos>(prompt_index),
                        state.sequence_id,
                        index == chunk_size - 1)) {
                    llama_batch_free(batch);
                    return fatal_result(
                        EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT,
                        "Unable to construct an evaluation prefill batch",
                        memory,
                        states);
                }
            }
            if (llama_decode(context, batch) != 0) {
                llama_batch_free(batch);
                return fatal_result(
                    EvaluationMultiSequenceGenerationError::DECODE_FAILED,
                    "llama.cpp failed while prefilling an evaluation sequence",
                    memory,
                    states);
            }
            offset += static_cast<std::size_t>(chunk_size);
        }

        if (state.cleaned) {
            continue;
        }
        state.result.prompt_duration_ms = elapsed_ms(prompt_started);
        if (input.cancellation->load(std::memory_order_acquire)) {
            if (!finish_cancelled(memory, state, 0)) {
                llama_batch_free(batch);
                return fatal_result(
                    EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
                    "Unable to clean a cancelled evaluation sequence after prefill",
                    memory,
                    states);
            }
            continue;
        }

        const llama_token first_token = llama_sampler_sample(input.sampler, context, -1);
        const auto sample_error = process_sample(memory, vocab, state, first_token, 0);
        if (sample_error != EvaluationMultiSequenceGenerationError::NONE) {
            llama_batch_free(batch);
            return fatal_result(sample_error, "Unable to sample the first evaluation token", memory, states);
        }
    }

    const auto generation_started = Clock::now();
    while (true) {
        std::vector<std::size_t> scheduled;
        scheduled.reserve(states.size());
        batch.n_tokens = 0;

        for (std::size_t index = 0; index < states.size(); ++index) {
            auto& state = states[index];
            if (!state.pending || state.cleaned) {
                continue;
            }
            if (state.input->cancellation->load(std::memory_order_acquire)) {
                if (!finish_cancelled(memory, state, elapsed_ms(generation_started))) {
                    llama_batch_free(batch);
                    return fatal_result(
                        EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
                        "Unable to clean a cancelled evaluation sequence before decode",
                        memory,
                        states);
                }
                continue;
            }
            if (!append_evaluation_multi_sequence_batch_token(
                    batch,
                    batch_size,
                    state.pending_token,
                    state.pending_position,
                    state.sequence_id,
                    true)) {
                llama_batch_free(batch);
                return fatal_result(
                    EvaluationMultiSequenceGenerationError::INVALID_ARGUMENT,
                    "Unable to construct an evaluation decode batch",
                    memory,
                    states);
            }
            scheduled.push_back(index);
        }

        if (scheduled.empty()) {
            break;
        }
        if (llama_decode(context, batch) != 0) {
            llama_batch_free(batch);
            return fatal_result(
                EvaluationMultiSequenceGenerationError::DECODE_FAILED,
                "llama.cpp failed while decoding an evaluation multi-sequence batch",
                memory,
                states);
        }

        for (std::size_t output_index = 0; output_index < scheduled.size(); ++output_index) {
            auto& state = states[scheduled[output_index]];
            state.pending = false;
            if (state.input->cancellation->load(std::memory_order_acquire)) {
                if (!finish_cancelled(memory, state, elapsed_ms(generation_started))) {
                    llama_batch_free(batch);
                    return fatal_result(
                        EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
                        "Unable to clean a cancelled evaluation sequence after decode",
                        memory,
                        states);
                }
                continue;
            }

            const llama_token token = llama_sampler_sample(
                state.input->sampler,
                context,
                static_cast<std::int32_t>(output_index));
            const auto sample_error = process_sample(
                memory,
                vocab,
                state,
                token,
                elapsed_ms(generation_started));
            if (sample_error != EvaluationMultiSequenceGenerationError::NONE) {
                llama_batch_free(batch);
                return fatal_result(sample_error, "Unable to sample an evaluation batch token", memory, states);
            }
        }
    }

    llama_batch_free(batch);
    if (!cleanup_all(memory, states)) {
        return {
            EvaluationMultiSequenceGenerationError::CLEANUP_FAILED,
            "Unable to clean all evaluation sequences after batch completion",
            {},
        };
    }

    EvaluationMultiSequenceGenerationResult result;
    result.cases.reserve(states.size());
    for (auto& state : states) {
        if (state.result.status == EvaluationMultiSequenceCaseStatus::COMPLETED &&
            utf8_complete_prefix_size(state.result.output, state.result.output.size()) != state.result.output.size()) {
            return {
                EvaluationMultiSequenceGenerationError::TOKEN_DECODE_FAILED,
                "Evaluation output ended with incomplete UTF-8",
                {},
            };
        }
        result.cases.push_back(std::move(state.result));
    }
    return result;
}

}  // namespace local_llm
