#include "llama.h"

#include <cstdint>

namespace {

int fail(int code) {
    return code;
}

}  // namespace

int main() {
    llama_context_params params = llama_context_default_params();
    if (params.n_seq_max != 1U) {
        return fail(1);
    }

    params.n_seq_max = 2U;
    if (params.n_seq_max != 2U) {
        return fail(2);
    }

    llama_batch batch = llama_batch_init(2, 0, 2);
    if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr || batch.seq_id == nullptr ||
        batch.logits == nullptr) {
        llama_batch_free(batch);
        return fail(3);
    }

    batch.n_tokens = 2;
    batch.token[0] = 0;
    batch.pos[0] = 0;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = 1;

    batch.token[1] = 0;
    batch.pos[1] = 0;
    batch.n_seq_id[1] = 1;
    batch.seq_id[1][0] = 1;
    batch.logits[1] = 1;

    if (batch.seq_id[0][0] != 0 || batch.seq_id[1][0] != 1) {
        llama_batch_free(batch);
        return fail(4);
    }

    llama_batch_free(batch);

    using DecodeFn = int32_t (*)(llama_context*, llama_batch);
    using LogitsFn = float* (*)(llama_context*, int32_t);
    using SequenceCountFn = uint32_t (*)(const llama_context*);
    using SequenceRemoveFn = bool (*)(llama_memory_t, llama_seq_id, llama_pos, llama_pos);

    const DecodeFn decode_fn = &llama_decode;
    const LogitsFn logits_fn = &llama_get_logits_ith;
    const SequenceCountFn sequence_count_fn = &llama_n_seq_max;
    const SequenceRemoveFn sequence_remove_fn = &llama_memory_seq_rm;

    if (decode_fn == nullptr || logits_fn == nullptr || sequence_count_fn == nullptr || sequence_remove_fn == nullptr) {
        return fail(5);
    }

    return 0;
}
