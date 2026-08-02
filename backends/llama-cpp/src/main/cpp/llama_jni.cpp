#include <jni.h>

#include "ggml-backend.h"
#include "gguf_metadata.h"
#include "llama.h"
#include "native_handle_registry.h"

#include <atomic>
#include <chrono>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

jobjectArray to_java_string_array(JNIEnv* env, const std::vector<std::string>& values) {
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) {
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(values.size()), string_class, nullptr);
    if (result == nullptr) {
        env->DeleteLocalRef(string_class);
        return nullptr;
    }

    for (std::size_t index = 0; index < values.size(); ++index) {
        jstring value = env->NewStringUTF(values[index].c_str());
        if (value == nullptr) {
            env->DeleteLocalRef(string_class);
            return nullptr;
        }
        env->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(string_class);
    return result;
}

std::vector<std::string> error_response(const char* code, std::string message) {
    return {"error", code, std::move(message)};
}

std::vector<std::string> encode_inspection(const local_llm::GgufInspection& inspection) {
    if (!inspection.success()) {
        return error_response(
            local_llm::gguf_inspection_error_code_name(inspection.error_code),
            inspection.error_message
        );
    }

    const local_llm::GgufMetadata& metadata = inspection.metadata;
    return {
        "ok",
        std::to_string(metadata.version),
        std::to_string(metadata.alignment),
        std::to_string(metadata.data_offset),
        std::to_string(metadata.key_value_count),
        std::to_string(metadata.tensor_count),
        metadata.architecture,
        metadata.name,
        metadata.file_type.has_value() ? std::to_string(metadata.file_type.value()) : std::string{},
    };
}

std::string base64_encode(const std::string& input) {
    static constexpr char alphabet[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string output;
    output.reserve(((input.size() + 2) / 3) * 4);

    std::uint32_t accumulator = 0;
    int bits = -6;
    for (const unsigned char value : input) {
        accumulator = (accumulator << 8) | value;
        bits += 8;
        while (bits >= 0) {
            output.push_back(alphabet[(accumulator >> bits) & 0x3fU]);
            bits -= 6;
        }
    }
    if (bits > -6) {
        output.push_back(alphabet[((accumulator << 8) >> (bits + 8)) & 0x3fU]);
    }
    while (output.size() % 4 != 0) {
        output.push_back('=');
    }
    return output;
}

class UtfChars final {
public:
    UtfChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (value_ != nullptr) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~UtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    UtfChars(const UtfChars&) = delete;
    UtfChars& operator=(const UtfChars&) = delete;

    const char* get() const {
        return chars_;
    }

private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_ = nullptr;
};

class BackendRuntime final {
public:
    std::vector<std::string> initialize(const std::string& native_library_dir) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (initialized_) {
            return {"ok", std::to_string(llama_max_devices())};
        }

        if (native_library_dir.empty()) {
            ggml_backend_load_all();
        } else {
            ggml_backend_load_all_from_path(native_library_dir.c_str());
        }
        llama_backend_init();

        const std::size_t device_count = llama_max_devices();
        if (device_count == 0) {
            llama_backend_free();
            return error_response("BACKEND_UNAVAILABLE", "No llama.cpp backend device was registered");
        }

        initialized_ = true;
        return {"ok", std::to_string(device_count)};
    }

    std::vector<std::string> shutdown(bool resources_active) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!initialized_) {
            return {"ok"};
        }
        if (resources_active) {
            return error_response("BUSY", "Native resources must be released before runtime shutdown");
        }

        llama_backend_free();
        initialized_ = false;
        return {"ok"};
    }

    bool initialized() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return initialized_;
    }

private:
    mutable std::mutex mutex_;
    bool initialized_ = false;
};

struct ModelRecord final {
    ModelRecord(llama_model* model_value, std::string path_value)
        : model(model_value), path(std::move(path_value)) {}

    ~ModelRecord() {
        if (model != nullptr) {
            llama_model_free(model);
        }
    }

    ModelRecord(const ModelRecord&) = delete;
    ModelRecord& operator=(const ModelRecord&) = delete;

    llama_model* model;
    std::string path;
    std::mutex lifecycle_mutex;
    std::size_t active_contexts = 0;
    bool available = true;
};

struct ContextRecord final {
    ContextRecord(
        llama_context* context_value,
        std::shared_ptr<ModelRecord> model_value,
        std::int32_t batch_size_value
    ) : context(context_value), model(std::move(model_value)), batch_size(batch_size_value) {}

    ~ContextRecord() {
        if (context != nullptr) {
            llama_free(context);
        }
        std::lock_guard<std::mutex> lock(model->lifecycle_mutex);
        if (model->active_contexts > 0) {
            --model->active_contexts;
        }
    }

    ContextRecord(const ContextRecord&) = delete;
    ContextRecord& operator=(const ContextRecord&) = delete;

    llama_context* context;
    std::shared_ptr<ModelRecord> model;
    std::int32_t batch_size;
    std::mutex generation_mutex;
};

BackendRuntime backend_runtime;
NativeHandleRegistry<ModelRecord> models;
NativeHandleRegistry<ContextRecord> contexts;
std::atomic<std::size_t> active_generations{0};

class ActiveGeneration final {
public:
    ActiveGeneration() {
        active_generations.fetch_add(1, std::memory_order_acq_rel);
    }

    ~ActiveGeneration() {
        active_generations.fetch_sub(1, std::memory_order_acq_rel);
    }

    ActiveGeneration(const ActiveGeneration&) = delete;
    ActiveGeneration& operator=(const ActiveGeneration&) = delete;
};

std::vector<llama_token> tokenize_prompt(const llama_vocab* vocab, const std::string& prompt) {
    if (prompt.size() > static_cast<std::size_t>(std::numeric_limits<std::int32_t>::max())) {
        return {};
    }

    const std::int32_t required = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<std::int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (required == std::numeric_limits<std::int32_t>::min()) {
        return {};
    }

    const std::int32_t token_count = required < 0 ? -required : required;
    if (token_count <= 0) {
        return {};
    }

    std::vector<llama_token> tokens(static_cast<std::size_t>(token_count));
    const std::int32_t written = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<std::int32_t>(prompt.size()),
        tokens.data(),
        token_count,
        true,
        true
    );
    if (written < 0) {
        return {};
    }
    tokens.resize(static_cast<std::size_t>(written));
    return tokens;
}

std::string token_piece(const llama_vocab* vocab, llama_token token) {
    std::vector<char> buffer(128);
    std::int32_t written = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<std::int32_t>(buffer.size()),
        0,
        true
    );
    if (written < 0) {
        buffer.resize(static_cast<std::size_t>(-written));
        written = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<std::int32_t>(buffer.size()),
            0,
            true
        );
    }
    return written < 0 ? std::string{} : std::string(buffer.data(), static_cast<std::size_t>(written));
}

std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> create_sampler(
    float temperature,
    float top_p,
    std::int32_t top_k,
    std::uint32_t seed
) {
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) {
        return {nullptr, llama_sampler_free};
    }

    if (temperature <= 0.0F) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));
    }
    return {sampler, llama_sampler_free};
}

std::vector<std::string> generate_text(
    ContextRecord& record,
    const std::string& prompt,
    std::int32_t max_output_tokens,
    float temperature,
    float top_p,
    std::int32_t top_k,
    std::uint32_t seed
) {
    std::lock_guard<std::mutex> generation_lock(record.generation_mutex);
    if (max_output_tokens <= 0) {
        return error_response("INVALID_ARGUMENT", "Maximum output tokens must be positive");
    }
    if (llama_model_has_encoder(record.model->model)) {
        return error_response("UNSUPPORTED_MODEL", "Encoder-decoder models are not supported by this runtime path");
    }

    const llama_vocab* vocab = llama_model_get_vocab(record.model->model);
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

    auto sampler = create_sampler(temperature, top_p, top_k, seed);
    if (!sampler) {
        return error_response("SAMPLER_FAILED", "Unable to create the llama.cpp sampler chain");
    }

    std::string output;
    std::int32_t generated_tokens = 0;
    const auto generation_started = std::chrono::steady_clock::now();
    while (generated_tokens < max_output_tokens) {
        llama_token token = llama_sampler_sample(sampler.get(), record.context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        const std::string piece = token_piece(vocab, token);
        if (piece.empty()) {
            return error_response("TOKEN_DECODE_FAILED", "Unable to decode a generated token");
        }
        output.append(piece);
        ++generated_tokens;

        llama_batch token_batch = llama_batch_get_one(&token, 1);
        if (generated_tokens < max_output_tokens && llama_decode(record.context, token_batch) != 0) {
            return error_response("DECODE_FAILED", "llama.cpp failed while decoding a generated token");
        }
    }
    const auto generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - generation_started
    ).count();

    return {
        "ok",
        base64_encode(output),
        std::to_string(prompt_tokens.size()),
        std::to_string(generated_tokens),
        std::to_string(prompt_ms),
        std::to_string(generation_ms),
    };
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_runtimeVersion(JNIEnv* env, jobject /* this */) {
    const std::string version = std::string(LOCAL_LLM_LLAMA_CPP_TAG) + " (" + LOCAL_LLM_LLAMA_CPP_COMMIT + ")";
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_isLlamaCppLinked(JNIEnv* /* env */, jobject /* this */) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_supportsMmap(JNIEnv* /* env */, jobject /* this */) {
    return llama_supports_mmap() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_initialize(
    JNIEnv* env,
    jobject /* this */,
    jstring native_library_dir
) {
    if (native_library_dir == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Native library directory must not be null"));
    }

    const UtfChars path(env, native_library_dir);
    if (path.get() == nullptr) {
        return nullptr;
    }
    return to_java_string_array(env, backend_runtime.initialize(path.get()));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_shutdown(JNIEnv* env, jobject /* this */) {
    const bool resources_active = !models.empty() || !contexts.empty() ||
        active_generations.load(std::memory_order_acquire) != 0;
    return to_java_string_array(env, backend_runtime.shutdown(resources_active));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_loadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint n_gpu_layers,
    jboolean use_mmap,
    jboolean use_mlock
) {
    if (!backend_runtime.initialized()) {
        return to_java_string_array(env, error_response("NOT_INITIALIZED", "Initialize the native runtime before loading a model"));
    }
    if (model_path == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Model path must not be null"));
    }

    const UtfChars path(env, model_path);
    if (path.get() == nullptr) {
        return nullptr;
    }

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = n_gpu_layers;
    params.use_mmap = use_mmap == JNI_TRUE;
    params.use_mlock = use_mlock == JNI_TRUE;

    const auto started_at = std::chrono::steady_clock::now();
    llama_model* raw_model = llama_model_load_from_file(path.get(), params);
    const auto load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - started_at
    ).count();

    if (raw_model == nullptr) {
        return to_java_string_array(env, error_response("MODEL_LOAD_FAILED", "llama.cpp could not load the GGUF model"));
    }

    const std::int64_t handle = models.add(std::make_shared<ModelRecord>(raw_model, path.get()));
    if (handle == 0) {
        llama_model_free(raw_model);
        return to_java_string_array(env, error_response("INTERNAL", "Unable to allocate a native model handle"));
    }

    return to_java_string_array(env, {"ok", std::to_string(handle), std::to_string(load_ms)});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_unloadModel(
    JNIEnv* env,
    jobject /* this */,
    jlong model_handle
) {
    const auto model = models.get(static_cast<std::int64_t>(model_handle));
    if (!model) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }

    std::lock_guard<std::mutex> lock(model->lifecycle_mutex);
    if (!model->available) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    if (model->active_contexts != 0) {
        return to_java_string_array(env, error_response("MODEL_IN_USE", "Release all contexts before unloading the model"));
    }
    model->available = false;
    if (!models.remove(static_cast<std::int64_t>(model_handle))) {
        model->available = true;
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    return to_java_string_array(env, {"ok"});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaGenerationApi_createContext(
    JNIEnv* env,
    jobject /* this */,
    jlong model_handle,
    jint context_size,
    jint batch_size,
    jint micro_batch_size,
    jint threads,
    jint batch_threads,
    jboolean flash_attention
) {
    const auto model = models.get(static_cast<std::int64_t>(model_handle));
    if (!model) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    if (context_size <= 0 || batch_size <= 0 || micro_batch_size <= 0 || threads <= 0 || batch_threads <= 0) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Context and thread parameters must be positive"));
    }

    std::lock_guard<std::mutex> lifecycle_lock(model->lifecycle_mutex);
    if (!model->available) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<std::uint32_t>(context_size);
    params.n_batch = static_cast<std::uint32_t>(batch_size);
    params.n_ubatch = static_cast<std::uint32_t>(micro_batch_size);
    params.n_threads = threads;
    params.n_threads_batch = batch_threads;
    params.flash_attn_type = flash_attention == JNI_TRUE
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED
        : LLAMA_FLASH_ATTN_TYPE_DISABLED;

    llama_context* raw_context = llama_init_from_model(model->model, params);
    if (raw_context == nullptr) {
        return to_java_string_array(env, error_response("CONTEXT_CREATE_FAILED", "llama.cpp could not create the context"));
    }

    ++model->active_contexts;
    const std::int64_t handle = contexts.add(
        std::make_shared<ContextRecord>(raw_context, model, batch_size)
    );
    if (handle == 0) {
        --model->active_contexts;
        llama_free(raw_context);
        return to_java_string_array(env, error_response("INTERNAL", "Unable to allocate a native context handle"));
    }
    return to_java_string_array(env, {"ok", std::to_string(handle)});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaGenerationApi_releaseContext(
    JNIEnv* env,
    jobject /* this */,
    jlong context_handle
) {
    if (!contexts.remove(static_cast<std::int64_t>(context_handle))) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native context handle"));
    }
    return to_java_string_array(env, {"ok"});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaGenerationApi_generate(
    JNIEnv* env,
    jobject /* this */,
    jlong context_handle,
    jstring prompt_value,
    jint max_output_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jlong seed
) {
    const auto context = contexts.get(static_cast<std::int64_t>(context_handle));
    if (!context) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native context handle"));
    }
    if (prompt_value == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Prompt must not be null"));
    }

    const UtfChars prompt(env, prompt_value);
    if (prompt.get() == nullptr) {
        return nullptr;
    }

    return to_java_string_array(
        env,
        generate_text(
            *context,
            prompt.get(),
            max_output_tokens,
            temperature,
            top_p,
            top_k,
            static_cast<std::uint32_t>(seed)
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_inspectGguf(
    JNIEnv* env,
    jobject /* this */,
    jstring path_value
) {
    if (path_value == nullptr) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "GGUF path must not be null"));
    }

    const UtfChars path(env, path_value);
    if (path.get() == nullptr) {
        return nullptr;
    }

    return to_java_string_array(env, encode_inspection(local_llm::inspect_gguf_metadata(path.get())));
}
