// LLRT-9B2B2c keeps the existing JNI implementation and evaluation-only
// multi-sequence entry points in one translation unit so the evaluation path
// can reuse the same resident-model/context ownership registries. The stable
// production JNI surface remains implemented in llama_jni.cpp and unchanged.
#include "llama_jni.cpp"

#include "evaluation_multiseq_context_params.h"
#include "evaluation_multiseq_generation.h"

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

struct EvaluationContextRecord final {
    EvaluationContextRecord(
        llama_context* context_value,
        std::shared_ptr<ModelRecord> model_value,
        std::int32_t batch_size_value,
        std::uint32_t per_sequence_context_size_value,
        std::uint32_t max_sequences_value
    ) : context(context_value),
        model(std::move(model_value)),
        batch_size(batch_size_value),
        per_sequence_context_size(per_sequence_context_size_value),
        max_sequences(max_sequences_value) {}

    ~EvaluationContextRecord() {
        if (context != nullptr) {
            llama_free(context);
        }
        std::lock_guard<std::mutex> lock(model->lifecycle_mutex);
        if (model->active_contexts > 0) {
            --model->active_contexts;
        }
    }

    EvaluationContextRecord(const EvaluationContextRecord&) = delete;
    EvaluationContextRecord& operator=(const EvaluationContextRecord&) = delete;

    llama_context* context;
    std::shared_ptr<ModelRecord> model;
    std::int32_t batch_size;
    std::uint32_t per_sequence_context_size;
    std::uint32_t max_sequences;
    std::mutex generation_mutex;
};

NativeHandleRegistry<EvaluationContextRecord> evaluation_contexts;

class EvaluationCancellationScope final {
public:
    explicit EvaluationCancellationScope(NativeCancellationRegistry& registry) : registry_(registry) {}

    ~EvaluationCancellationScope() {
        for (const auto& request_id : request_ids_) {
            registry_.finish(request_id);
        }
    }

    bool begin(const std::string& request_id, std::shared_ptr<std::atomic_bool>& signal) {
        signal = registry_.begin(request_id);
        if (!signal) {
            return false;
        }
        request_ids_.push_back(request_id);
        return true;
    }

    EvaluationCancellationScope(const EvaluationCancellationScope&) = delete;
    EvaluationCancellationScope& operator=(const EvaluationCancellationScope&) = delete;

private:
    NativeCancellationRegistry& registry_;
    std::vector<std::string> request_ids_;
};

bool read_int_array(JNIEnv* env, jintArray values, std::size_t expected, std::vector<std::int32_t>& output) {
    if (values == nullptr || env->GetArrayLength(values) != static_cast<jsize>(expected)) {
        return false;
    }
    output.resize(expected);
    env->GetIntArrayRegion(values, 0, static_cast<jsize>(expected), reinterpret_cast<jint*>(output.data()));
    return !env->ExceptionCheck();
}

bool read_float_array(JNIEnv* env, jfloatArray values, std::size_t expected, std::vector<float>& output) {
    if (values == nullptr || env->GetArrayLength(values) != static_cast<jsize>(expected)) {
        return false;
    }
    output.resize(expected);
    env->GetFloatArrayRegion(values, 0, static_cast<jsize>(expected), output.data());
    return !env->ExceptionCheck();
}

bool read_long_array(JNIEnv* env, jlongArray values, std::size_t expected, std::vector<std::int64_t>& output) {
    if (values == nullptr || env->GetArrayLength(values) != static_cast<jsize>(expected)) {
        return false;
    }
    output.resize(expected);
    env->GetLongArrayRegion(values, 0, static_cast<jsize>(expected), reinterpret_cast<jlong*>(output.data()));
    return !env->ExceptionCheck();
}

bool read_nested_stop_tokens(
    JNIEnv* env,
    jobjectArray values,
    std::size_t expected,
    std::vector<std::vector<llama_token>>& output
) {
    if (values == nullptr || env->GetArrayLength(values) != static_cast<jsize>(expected)) {
        return false;
    }
    output.reserve(expected);
    for (std::size_t index = 0; index < expected; ++index) {
        auto entry = static_cast<jintArray>(env->GetObjectArrayElement(values, static_cast<jsize>(index)));
        if (entry == nullptr) {
            return false;
        }
        output.push_back(read_java_stop_tokens(env, entry));
        env->DeleteLocalRef(entry);
        if (env->ExceptionCheck()) {
            return false;
        }
    }
    return true;
}

bool read_nested_stop_sequences(
    JNIEnv* env,
    jobjectArray values,
    std::size_t expected,
    std::vector<std::vector<std::string>>& output
) {
    if (values == nullptr || env->GetArrayLength(values) != static_cast<jsize>(expected)) {
        return false;
    }
    output.reserve(expected);
    for (std::size_t index = 0; index < expected; ++index) {
        auto entry = static_cast<jobjectArray>(env->GetObjectArrayElement(values, static_cast<jsize>(index)));
        if (entry == nullptr) {
            return false;
        }
        std::vector<std::string> sequences;
        const bool read = read_java_string_array(env, entry, sequences);
        env->DeleteLocalRef(entry);
        if (!read) {
            return false;
        }
        output.push_back(std::move(sequences));
    }
    return true;
}

const char* evaluation_generation_error_code(local_llm::EvaluationMultiSequenceGenerationError error) {
    using Error = local_llm::EvaluationMultiSequenceGenerationError;
    switch (error) {
        case Error::INVALID_ARGUMENT:
            return "INVALID_ARGUMENT";
        case Error::CONTEXT_OVERFLOW:
            return "CONTEXT_OVERFLOW";
        case Error::SAMPLER_FAILED:
            return "SAMPLER_FAILED";
        case Error::DECODE_FAILED:
            return "DECODE_FAILED";
        case Error::TOKEN_DECODE_FAILED:
            return "TOKEN_DECODE_FAILED";
        case Error::BATCH_ALLOCATION_FAILED:
        case Error::CLEANUP_FAILED:
        case Error::NONE:
            return "INTERNAL";
    }
    return "INTERNAL";
}

std::vector<std::string> encode_evaluation_batch(
    const local_llm::EvaluationMultiSequenceGenerationResult& result
) {
    if (!result.ok()) {
        return error_response(evaluation_generation_error_code(result.error), result.error_message);
    }
    std::vector<std::string> response;
    response.reserve(2U + result.cases.size() * 8U);
    response.push_back("ok");
    response.push_back(std::to_string(result.cases.size()));
    for (const auto& result_case : result.cases) {
        response.push_back(result_case.request_id);
        response.push_back(
            result_case.status == local_llm::EvaluationMultiSequenceCaseStatus::CANCELLED
                ? "CANCELLED"
                : "COMPLETED"
        );
        response.push_back(base64_encode(result_case.output));
        response.push_back(std::to_string(result_case.input_tokens));
        response.push_back(std::to_string(result_case.output_tokens));
        response.push_back(std::to_string(result_case.prompt_duration_ms));
        response.push_back(std::to_string(result_case.generation_duration_ms));
        response.push_back(result_case.stop_reason);
    }
    return response;
}

std::vector<std::string> create_evaluation_context(
    jlong model_handle,
    jint per_sequence_context_size,
    jint max_sequences,
    jint batch_size,
    jint micro_batch_size,
    jint threads,
    jint batch_threads,
    jint flash_attention_mode,
    const char* kv_cache_type_k,
    const char* kv_cache_type_v
) {
    const auto model = models.get(static_cast<std::int64_t>(model_handle));
    if (!model) {
        return error_response("UNKNOWN_HANDLE", "Unknown native model handle");
    }
    if (per_sequence_context_size <= 0 || max_sequences <= 0 || batch_size <= 0 ||
        micro_batch_size <= 0 || threads <= 0 || batch_threads <= 0) {
        return error_response("INVALID_ARGUMENT", "Evaluation context and thread parameters must be positive");
    }

    std::lock_guard<std::mutex> lifecycle_lock(model->lifecycle_mutex);
    if (!model->available) {
        return error_response("UNKNOWN_HANDLE", "Unknown native model handle");
    }

    llama_context_params params = llama_context_default_params();
    params.n_batch = static_cast<std::uint32_t>(batch_size);
    params.n_ubatch = static_cast<std::uint32_t>(micro_batch_size);
    params.n_threads = threads;
    params.n_threads_batch = batch_threads;
    const auto context_error = local_llm::apply_evaluation_multi_sequence_context_params(
        params,
        static_cast<std::uint32_t>(per_sequence_context_size),
        static_cast<std::uint32_t>(max_sequences)
    );
    if (context_error == local_llm::EvaluationMultiSequenceContextError::CONTEXT_OVERFLOW) {
        return error_response("CONTEXT_OVERFLOW", "Evaluation aggregate context size overflow");
    }
    if (context_error != local_llm::EvaluationMultiSequenceContextError::NONE) {
        return error_response("INVALID_ARGUMENT", "Invalid evaluation multi-sequence context configuration");
    }
    if (!local_llm::apply_flash_attention_mode(params, flash_attention_mode)) {
        return error_response("INVALID_ARGUMENT", "Unsupported flash-attention mode");
    }
    if (!local_llm::apply_kv_cache_type_overrides(params, kv_cache_type_k, kv_cache_type_v)) {
        return error_response("INVALID_ARGUMENT", "Unsupported K/V cache type");
    }

    llama_context* raw_context = llama_init_from_model(model->model, params);
    if (raw_context == nullptr) {
        return error_response("CONTEXT_CREATE_FAILED", "llama.cpp could not create the evaluation context");
    }
    if (llama_n_seq_max(raw_context) != static_cast<std::uint32_t>(max_sequences) ||
        llama_n_ctx_seq(raw_context) != static_cast<std::uint32_t>(per_sequence_context_size)) {
        llama_free(raw_context);
        return error_response("CONTEXT_CREATE_FAILED", "llama.cpp materialized unexpected evaluation context capacity");
    }

    ++model->active_contexts;
    const std::int64_t handle = evaluation_contexts.add(
        std::make_shared<EvaluationContextRecord>(
            raw_context,
            model,
            batch_size,
            static_cast<std::uint32_t>(per_sequence_context_size),
            static_cast<std::uint32_t>(max_sequences)
        )
    );
    if (handle == 0) {
        --model->active_contexts;
        llama_free(raw_context);
        return error_response("INTERNAL", "Unable to allocate a native evaluation context handle");
    }
    return {"ok", std::to_string(handle)};
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaEvaluationBatchApi_createEvaluationContext(
    JNIEnv* env,
    jobject /* this */,
    jlong model_handle,
    jint per_sequence_context_size,
    jint max_sequences,
    jint batch_size,
    jint micro_batch_size,
    jint threads,
    jint batch_threads,
    jint flash_attention_mode,
    jstring kv_cache_type_k_value,
    jstring kv_cache_type_v_value
) {
    std::string kv_cache_type_k;
    std::string kv_cache_type_v;
    const char* kv_cache_type_k_ptr = nullptr;
    const char* kv_cache_type_v_ptr = nullptr;
    if (kv_cache_type_k_value != nullptr) {
        const UtfChars value(env, kv_cache_type_k_value);
        if (value.get() == nullptr) {
            return nullptr;
        }
        kv_cache_type_k.assign(value.get());
        kv_cache_type_k_ptr = kv_cache_type_k.c_str();
    }
    if (kv_cache_type_v_value != nullptr) {
        const UtfChars value(env, kv_cache_type_v_value);
        if (value.get() == nullptr) {
            return nullptr;
        }
        kv_cache_type_v.assign(value.get());
        kv_cache_type_v_ptr = kv_cache_type_v.c_str();
    }
    return to_java_string_array(
        env,
        create_evaluation_context(
            model_handle,
            per_sequence_context_size,
            max_sequences,
            batch_size,
            micro_batch_size,
            threads,
            batch_threads,
            flash_attention_mode,
            kv_cache_type_k_ptr,
            kv_cache_type_v_ptr
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaEvaluationBatchApi_releaseEvaluationContext(
    JNIEnv* env,
    jobject /* this */,
    jlong context_handle
) {
    if (!evaluation_contexts.remove(static_cast<std::int64_t>(context_handle))) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native evaluation context handle"));
    }
    return to_java_string_array(env, {"ok"});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaEvaluationBatchApi_generateEvaluationBatch(
    JNIEnv* env,
    jobject /* this */,
    jlong context_handle,
    jobjectArray request_ids_value,
    jobjectArray prompts_value,
    jintArray max_output_tokens_value,
    jfloatArray temperatures_value,
    jfloatArray top_ps_value,
    jintArray top_ks_value,
    jfloatArray min_ps_value,
    jfloatArray presence_penalties_value,
    jfloatArray repeat_penalties_value,
    jintArray repeat_last_ns_value,
    jlongArray seeds_value,
    jobjectArray output_constraint_types_value,
    jobjectArray output_schemas_value,
    jobjectArray stop_token_ids_value,
    jobjectArray stop_sequences_value
) {
    const auto context = evaluation_contexts.get(static_cast<std::int64_t>(context_handle));
    if (!context) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native evaluation context handle"));
    }

    std::vector<std::string> request_ids;
    std::vector<std::string> prompts;
    std::vector<std::string> output_constraint_types;
    std::vector<std::string> output_schemas;
    if (!read_java_string_array(env, request_ids_value, request_ids) ||
        !read_java_string_array(env, prompts_value, prompts) ||
        !read_java_string_array(env, output_constraint_types_value, output_constraint_types) ||
        !read_java_string_array(env, output_schemas_value, output_schemas)) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation string arrays are invalid"));
    }
    const std::size_t count = request_ids.size();
    if (count < local_llm::kEvaluationMultiSequenceMinWidth ||
        count > context->max_sequences ||
        prompts.size() != count || output_constraint_types.size() != count || output_schemas.size() != count) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation arrays must have the same bounded case count"));
    }

    std::vector<std::int32_t> max_output_tokens;
    std::vector<float> temperatures;
    std::vector<float> top_ps;
    std::vector<std::int32_t> top_ks;
    std::vector<float> min_ps;
    std::vector<float> presence_penalties;
    std::vector<float> repeat_penalties;
    std::vector<std::int32_t> repeat_last_ns;
    std::vector<std::int64_t> seeds;
    std::vector<std::vector<llama_token>> stop_token_ids;
    std::vector<std::vector<std::string>> stop_sequences;
    if (!read_int_array(env, max_output_tokens_value, count, max_output_tokens) ||
        !read_float_array(env, temperatures_value, count, temperatures) ||
        !read_float_array(env, top_ps_value, count, top_ps) ||
        !read_int_array(env, top_ks_value, count, top_ks) ||
        !read_float_array(env, min_ps_value, count, min_ps) ||
        !read_float_array(env, presence_penalties_value, count, presence_penalties) ||
        !read_float_array(env, repeat_penalties_value, count, repeat_penalties) ||
        !read_int_array(env, repeat_last_ns_value, count, repeat_last_ns) ||
        !read_long_array(env, seeds_value, count, seeds) ||
        !read_nested_stop_tokens(env, stop_token_ids_value, count, stop_token_ids) ||
        !read_nested_stop_sequences(env, stop_sequences_value, count, stop_sequences)) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation configuration arrays are invalid"));
    }

    std::lock_guard<std::mutex> generation_lock(context->generation_mutex);
    if (llama_model_has_encoder(context->model->model)) {
        return to_java_string_array(env, error_response("UNSUPPORTED_MODEL", "Encoder-decoder models are not supported by evaluation batching"));
    }

    const llama_vocab* vocab = llama_model_get_vocab(context->model->model);
    EvaluationCancellationScope cancellation_scope(cancellations);
    std::vector<std::shared_ptr<std::atomic_bool>> cancellation_signals(count);
    std::vector<GenerationSampler> sampler_owners;
    sampler_owners.reserve(count);
    std::vector<local_llm::EvaluationMultiSequenceCaseInput> cases;
    cases.reserve(count);

    for (std::size_t index = 0; index < count; ++index) {
        if (request_ids[index].empty()) {
            return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation request IDs must not be blank"));
        }
        if (!cancellation_scope.begin(request_ids[index], cancellation_signals[index])) {
            return to_java_string_array(env, error_response("DUPLICATE_REQUEST", "An evaluation or generation with this request ID is already active"));
        }
        if (max_output_tokens[index] <= 0 || seeds[index] < 0) {
            return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation token budgets and seeds are invalid"));
        }

        std::string grammar;
        try {
            grammar = grammar_for_constraint(output_constraint_types[index], output_schemas[index]);
        } catch (const std::exception& error) {
            return to_java_string_array(env, error_response("INVALID_ARGUMENT", error.what()));
        }
        auto sampler = create_generation_sampler(
            vocab,
            temperatures[index],
            top_ps[index],
            top_ks[index],
            min_ps[index],
            presence_penalties[index],
            repeat_penalties[index],
            repeat_last_ns[index],
            static_cast<std::uint32_t>(seeds[index]),
            grammar
        );
        if (!sampler) {
            return to_java_string_array(env, error_response("SAMPLER_FAILED", "Unable to create an evaluation sampler chain"));
        }

        auto prepared_prompt = context->model->prepared_prompt_cache.take(prompts[index]);
        std::vector<llama_token> prompt_tokens = prepared_prompt.has_value()
            ? std::move(prepared_prompt.value())
            : tokenize_prompt(vocab, prompts[index]);
        if (prompt_tokens.empty()) {
            return to_java_string_array(env, error_response("TOKENIZATION_FAILED", "Evaluation prompt tokenization returned no tokens"));
        }

        sampler_owners.push_back(std::move(sampler));
        local_llm::EvaluationMultiSequenceCaseInput input;
        input.request_id = request_ids[index];
        input.prompt_tokens = std::move(prompt_tokens);
        input.sampler = sampler_owners.back().get();
        input.max_output_tokens = max_output_tokens[index];
        input.stop_token_ids = std::move(stop_token_ids[index]);
        input.stop_sequences = std::move(stop_sequences[index]);
        input.cancellation = cancellation_signals[index];
        input.grammar_constrained = !grammar.empty();
        cases.push_back(std::move(input));
    }

    ActiveGeneration active_generation;
    llama_memory_clear(llama_get_memory(context->context), true);
    return to_java_string_array(
        env,
        encode_evaluation_batch(
            local_llm::generate_evaluation_multi_sequence(
                context->context,
                vocab,
                context->batch_size,
                context->per_sequence_context_size,
                cases
            )
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaEvaluationBatchApi_cancelEvaluationCase(
    JNIEnv* env,
    jobject /* this */,
    jstring request_id_value
) {
    if (request_id_value == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Evaluation request ID must not be null"));
    }
    const UtfChars request_id(env, request_id_value);
    if (request_id.get() == nullptr) {
        return nullptr;
    }
    const bool cancelled = cancellations.cancel(request_id.get());
    return to_java_string_array(env, {"ok", cancelled ? "true" : "false"});
}
