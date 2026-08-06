#include "llama_jni.cpp"

#include "native_cancellation_registry.h"
#include "generation_output_buffer.h"
#include "generation_sampler.h"

namespace {

NativeCancellationRegistry cancellations;

class CancellationScope final {
public:
    CancellationScope(NativeCancellationRegistry& registry, std::string request_id)
        : registry_(registry), request_id_(std::move(request_id)) {}

    ~CancellationScope() {
        registry_.finish(request_id_);
    }

    CancellationScope(const CancellationScope&) = delete;
    CancellationScope& operator=(const CancellationScope&) = delete;

private:
    NativeCancellationRegistry& registry_;
    std::string request_id_;
};

class JavaStreamingCallback final {
public:
    JavaStreamingCallback(JNIEnv* env, jobject callback) : env_(env), callback_(callback) {
        if (callback_ == nullptr) {
            return;
        }
        jclass callback_class = env_->GetObjectClass(callback_);
        if (callback_class == nullptr) {
            return;
        }
        method_ = env_->GetMethodID(callback_class, "onChunk", "(Ljava/lang/String;I)Z");
        env_->DeleteLocalRef(callback_class);
    }

    bool valid() const {
        return callback_ != nullptr && method_ != nullptr;
    }

    bool emit(const std::string& text, std::int32_t generated_tokens) {
        const std::string encoded = base64_encode(text);
        jstring payload = env_->NewStringUTF(encoded.c_str());
        if (payload == nullptr) {
            return false;
        }
        const jboolean accepted = env_->CallBooleanMethod(
            callback_,
            method_,
            payload,
            static_cast<jint>(generated_tokens)
        );
        env_->DeleteLocalRef(payload);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return false;
        }
        return accepted == JNI_TRUE;
    }

private:
    JNIEnv* env_;
    jobject callback_;
    jmethodID method_ = nullptr;
};

std::vector<std::string> cancelled_response(
    std::size_t input_tokens,
    std::int32_t output_tokens,
    std::int64_t prompt_ms,
    std::int64_t generation_ms
) {
    return {
        "cancelled",
        std::to_string(input_tokens),
        std::to_string(output_tokens),
        std::to_string(prompt_ms),
        std::to_string(generation_ms),
        "UNKNOWN",
    };
}

std::vector<std::string> read_java_stop_sequences(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> output;
    if (values != nullptr && !read_java_string_array(env, values, output)) {
        return {};
    }
    return output;
}

std::vector<llama_token> read_java_stop_tokens(JNIEnv* env, jintArray values) {
    if (values == nullptr) {
        return {};
    }
    const jsize count = env->GetArrayLength(values);
    std::vector<llama_token> output(static_cast<std::size_t>(count));
    env->GetIntArrayRegion(values, 0, count, reinterpret_cast<jint*>(output.data()));
    return output;
}

bool contains_token(const std::vector<llama_token>& values, llama_token token) {
    return std::find(values.begin(), values.end(), token) != values.end();
}

std::string grammar_for_constraint(const std::string& type, const std::string& schema) {
    if (type == "TEXT") {
        return {};
    }
    if (type == "JSON") {
        static const std::string json_schema = R"({"$schema":"http://json-schema.org/draft-07/schema#"})";
        return json_schema_to_grammar(nlohmann::ordered_json::parse(json_schema), true);
    }
    if (type == "JSON_SCHEMA") {
        return json_schema_to_grammar(nlohmann::ordered_json::parse(schema), true);
    }
    throw std::invalid_argument("Unsupported output constraint type");
}

std::vector<std::string> stream_text(
    ContextRecord& record,
    const std::string& request_id,
    const std::string& prompt,
    std::int32_t max_output_tokens,
    float temperature,
    float top_p,
    std::int32_t top_k,
    float repeat_penalty,
    std::int32_t repeat_last_n,
    std::uint32_t seed,
    const std::string& output_constraint_type,
    const std::string& output_schema,
    const std::vector<llama_token>& stop_token_ids,
    const std::vector<std::string>& stop_sequences,
    JavaStreamingCallback& callback
) {
    const auto cancellation = cancellations.begin(request_id);
    if (!cancellation) {
        return error_response("DUPLICATE_REQUEST", "A generation with this request ID is already active");
    }
    CancellationScope cancellation_scope(cancellations, request_id);
    std::lock_guard<std::mutex> generation_lock(record.generation_mutex);

    if (!callback.valid()) {
        return error_response("CALLBACK_FAILED", "Streaming callback does not expose onChunk(String, Int)");
    }
    if (max_output_tokens <= 0) {
        return error_response("INVALID_ARGUMENT", "Maximum output tokens must be positive");
    }
    if (llama_model_has_encoder(record.model->model)) {
        return error_response("UNSUPPORTED_MODEL", "Encoder-decoder models are not supported by this runtime path");
    }

    const llama_vocab* vocab = llama_model_get_vocab(record.model->model);
    std::string grammar;
    try {
        grammar = grammar_for_constraint(output_constraint_type, output_schema);
    } catch (const std::exception& error) {
        return error_response("INVALID_OUTPUT_CONSTRAINT", error.what());
    }
    auto sampler = create_generation_sampler(
        vocab,
        temperature,
        top_p,
        top_k,
        repeat_penalty,
        repeat_last_n,
        seed,
        grammar
    );
    if (!sampler) {
        return error_response("SAMPLER_FAILED", "Unable to create the llama.cpp sampler chain");
    }
    const std::vector<llama_token> prompt_tokens = tokenize_prompt(vocab, prompt);
    if (prompt_tokens.empty()) {
        return error_response("TOKENIZATION_FAILED", "Prompt tokenization returned no tokens");
    }
    if (prompt_tokens.size() + static_cast<std::size_t>(max_output_tokens) > llama_n_ctx(record.context)) {
        return error_response("CONTEXT_OVERFLOW", "Prompt and requested output exceed the configured context size");
    }

    ActiveGeneration active_generation;
    llama_memory_clear(llama_get_memory(record.context), true);
    const auto prompt_started = std::chrono::steady_clock::now();
    for (std::size_t offset = 0; offset < prompt_tokens.size();) {
        if (cancellation->load(std::memory_order_acquire)) {
            const auto prompt_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - prompt_started
            ).count();
            return cancelled_response(prompt_tokens.size(), 0, prompt_ms, 0);
        }
        const std::size_t remaining = prompt_tokens.size() - offset;
        const std::int32_t chunk_size = static_cast<std::int32_t>(
            std::min<std::size_t>(remaining, static_cast<std::size_t>(record.batch_size))
        );
        llama_batch prompt_batch = llama_batch_get_one(
            const_cast<llama_token*>(prompt_tokens.data() + offset),
            chunk_size
        );
        if (llama_decode(record.context, prompt_batch) != 0) {
            return error_response("DECODE_FAILED", "llama.cpp failed while processing the prompt");
        }
        offset += static_cast<std::size_t>(chunk_size);
    }
    const auto prompt_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - prompt_started
    ).count();

    std::string pending;
    std::size_t stop_lookbehind = 0;
    for (const std::string& sequence : stop_sequences) {
        stop_lookbehind = std::max(stop_lookbehind, sequence.size() > 0 ? sequence.size() - 1 : 0);
    }
    std::int32_t pending_tokens = 0;
    std::int32_t generated_tokens = 0;
    bool cancelled = false;
    bool stopped = false;
    std::string stop_reason = "MAX_OUTPUT_TOKENS";
    const auto generation_started = std::chrono::steady_clock::now();
    while (generated_tokens < max_output_tokens) {
        if (cancellation->load(std::memory_order_acquire)) {
            cancelled = true;
            break;
        }

        llama_token token = llama_sampler_sample(sampler.get(), record.context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            stop_reason = grammar.empty() ? "END_OF_GENERATION" : "GRAMMAR_COMPLETE";
            break;
        }
        if (contains_token(stop_token_ids, token)) {
            stop_reason = "STOP_SEQUENCE";
            stopped = true;
            break;
        }

        const std::string piece = token_piece(vocab, token);
        if (piece.empty()) {
            return error_response("TOKEN_DECODE_FAILED", "Unable to decode a generated token");
        }
        pending.append(piece);
        ++pending_tokens;
        ++generated_tokens;
        llama_sampler_accept(sampler.get(), token);

        const auto stop_position = earliest_stop_position(pending, stop_sequences);
        if (stop_position.has_value()) {
            pending.resize(stop_position.value());
            stop_reason = "STOP_SEQUENCE";
            stopped = true;
        }
        if (stopped) {
            break;
        }

        if (pending_tokens >= 8 || pending.size() >= 64) {
            const std::size_t desired_emit_size = pending.size() > stop_lookbehind
                ? pending.size() - stop_lookbehind
                : 0;
            const std::size_t emit_size = utf8_complete_prefix_size(pending, desired_emit_size);
            const std::string ready = pending.substr(0, emit_size);
            if (!ready.empty() && !callback.emit(ready, generated_tokens)) {
                cancellation->store(true, std::memory_order_release);
                cancelled = true;
                pending.clear();
                break;
            }
            pending.erase(0, emit_size);
            pending_tokens = 0;
        }

        llama_batch token_batch = llama_batch_get_one(&token, 1);
        if (generated_tokens < max_output_tokens && llama_decode(record.context, token_batch) != 0) {
            return error_response("DECODE_FAILED", "llama.cpp failed while decoding a generated token");
        }
    }

    if (!cancelled && !pending.empty()) {
        const std::size_t emit_size = utf8_complete_prefix_size(pending, pending.size());
        if (emit_size != pending.size()) {
            return error_response("TOKEN_DECODE_FAILED", "Generated output ended with incomplete UTF-8");
        }
        if (!callback.emit(pending, generated_tokens)) {
            cancellation->store(true, std::memory_order_release);
            cancelled = true;
        }
    }
    const auto generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - generation_started
    ).count();

    if (cancelled) {
        return cancelled_response(prompt_tokens.size(), generated_tokens, prompt_ms, generation_ms);
    }
    return {
        "ok",
        std::to_string(prompt_tokens.size()),
        std::to_string(generated_tokens),
        std::to_string(prompt_ms),
        std::to_string(generation_ms),
        stop_reason,
    };
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaStreamingApi_generateStreaming(
    JNIEnv* env,
    jobject /* this */,
    jlong context_handle,
    jstring request_id_value,
    jstring prompt_value,
    jint max_output_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repeat_penalty,
    jint repeat_last_n,
    jlong seed,
    jstring output_constraint_type_value,
    jstring output_schema_value,
    jintArray stop_token_ids_value,
    jobjectArray stop_sequences_value,
    jobject callback_value
) {
    const auto context = contexts.get(static_cast<std::int64_t>(context_handle));
    if (!context) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native context handle"));
    }
    if (request_id_value == nullptr || prompt_value == nullptr || output_constraint_type_value == nullptr || callback_value == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Request ID, prompt and callback are required"));
    }

    const UtfChars request_id(env, request_id_value);
    const UtfChars prompt(env, prompt_value);
    const UtfChars output_constraint_type(env, output_constraint_type_value);
    if (request_id.get() == nullptr || prompt.get() == nullptr || output_constraint_type.get() == nullptr) {
        return nullptr;
    }

    std::string output_schema;
    if (output_schema_value != nullptr) {
        const UtfChars schema(env, output_schema_value);
        if (schema.get() == nullptr) {
            return nullptr;
        }
        output_schema.assign(schema.get());
    }
    const std::vector<llama_token> stop_token_ids = read_java_stop_tokens(env, stop_token_ids_value);
    const std::vector<std::string> stop_sequences = read_java_stop_sequences(env, stop_sequences_value);
    if (request_id.get()[0] == '\0') {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Request ID must not be empty"));
    }

    JavaStreamingCallback callback(env, callback_value);
    return to_java_string_array(
        env,
        stream_text(
            *context,
            request_id.get(),
            prompt.get(),
            max_output_tokens,
            temperature,
            top_p,
            top_k,
            repeat_penalty,
            repeat_last_n,
            static_cast<std::uint32_t>(seed),
            output_constraint_type.get(),
            output_schema,
            stop_token_ids,
            stop_sequences,
            callback
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaStreamingApi_cancel(
    JNIEnv* env,
    jobject /* this */,
    jstring request_id_value
) {
    if (request_id_value == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Request ID must not be null"));
    }
    const UtfChars request_id(env, request_id_value);
    if (request_id.get() == nullptr) {
        return nullptr;
    }
    const bool was_running = cancellations.cancel(request_id.get());
    return to_java_string_array(env, {"ok", was_running ? "true" : "false"});
}
