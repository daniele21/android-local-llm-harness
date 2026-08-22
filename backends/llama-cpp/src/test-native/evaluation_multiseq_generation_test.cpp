#include "evaluation_multiseq_generation.h"

#include <cstdint>

namespace {

int expect(bool condition, int code) {
    return condition ? 0 : code;
}

}  // namespace

int main() {
    llama_batch batch = llama_batch_init(2, 0, 1);
    if (const int code = expect(batch.token != nullptr, 1)) return code;
    if (const int code = expect(batch.pos != nullptr, 2)) return code;
    if (const int code = expect(batch.n_seq_id != nullptr, 3)) return code;
    if (const int code = expect(batch.seq_id != nullptr, 4)) return code;
    if (const int code = expect(batch.logits != nullptr, 5)) return code;

    if (const int code = expect(
            local_llm::append_evaluation_multi_sequence_batch_token(batch, 2, 11, 7, 0, true),
            6)) {
        return code;
    }
    if (const int code = expect(
            local_llm::append_evaluation_multi_sequence_batch_token(batch, 2, 12, 9, 1, true),
            7)) {
        return code;
    }
    if (const int code = expect(batch.n_tokens == 2, 8)) return code;
    if (const int code = expect(batch.token[0] == 11 && batch.token[1] == 12, 9)) return code;
    if (const int code = expect(batch.pos[0] == 7 && batch.pos[1] == 9, 10)) return code;
    if (const int code = expect(batch.n_seq_id[0] == 1 && batch.n_seq_id[1] == 1, 11)) return code;
    if (const int code = expect(batch.seq_id[0][0] == 0 && batch.seq_id[1][0] == 1, 12)) return code;
    if (const int code = expect(batch.logits[0] == 1 && batch.logits[1] == 1, 13)) return code;
    if (const int code = expect(
            !local_llm::append_evaluation_multi_sequence_batch_token(batch, 2, 13, 10, 1, true),
            14)) {
        return code;
    }

    llama_batch_free(batch);

    const auto kernel = &local_llm::generate_evaluation_multi_sequence;
    if (const int code = expect(kernel != nullptr, 15)) return code;

    local_llm::EvaluationMultiSequenceGenerationResult default_result;
    if (const int code = expect(default_result.ok(), 16)) return code;
    if (const int code = expect(default_result.cases.empty(), 17)) return code;

    return 0;
}
