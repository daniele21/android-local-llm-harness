#include <jni.h>

#include "ggml-backend.h"
#include "gguf_metadata.h"
#include "llama.h"
#include "native_handle_registry.h"
#include "json-schema-to-grammar.h"
#include "chat.h"
#include <nlohmann/json.hpp>

#include <atomic>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <stdexcept>
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
        metadata.context_length.has_value() ? std::to_string(metadata.context_length.value()) : std::string{},
        metadata.block_count.has_value() ? std::to_string(metadata.block_count.value()) : std::string{},
        metadata.embedding_length.has_value() ? std::to_string(metadata.embedding_length.value()) : std::string{},
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
    UtfChars(JNIEnv* env, jstring value) {
        if (value == nullptr) return;
        const jsize length = env->GetStringLength(value);
        const jchar* units = env->GetStringChars(value, nullptr);
        if (units == nullptr) return;
        bool valid = true;
        for (jsize index = 0; index < length && valid; ++index) {
            std::uint32_t code_point = units[index];
            if (code_point >= 0xD800U && code_point <= 0xDBFFU) {
                if (++index >= length || units[index] < 0xDC00U || units[index] > 0xDFFFU) {
                    valid = false;
                    break;
                }
                code_point = 0x10000U + ((code_point - 0xD800U) << 10U) + (units[index] - 0xDC00U);
            } else if (code_point >= 0xDC00U && code_point <= 0xDFFFU) {
                valid = false;
                break;
            }
            append_code_point(code_point);
        }
        env->ReleaseStringChars(value, units);
        valid_ = valid;
    }

    UtfChars(const UtfChars&) = delete;
    UtfChars& operator=(const UtfChars&) = delete;

    const char* get() const {
        return valid_ ? chars_.c_str() : nullptr;
    }

private:
    void append_code_point(std::uint32_t code_point) {
        if (code_point <= 0x7FU) {
            chars_.push_back(static_cast<char>(code_point));
        } else if (code_point <= 0x7FFU) {
            chars_.push_back(static_cast<char>(0xC0U | (code_point >> 6U)));
            chars_.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
        } else if (code_point <= 0xFFFFU) {
            chars_.push_back(static_cast<char>(0xE0U | (code_point >> 12U)));
            chars_.push_back(static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
            chars_.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
        } else {
            chars_.push_back(static_cast<char>(0xF0U | (code_point >> 18U)));
            chars_.push_back(static_cast<char>(0x80U | ((code_point >> 12U) & 0x3FU)));
            chars_.push_back(static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
            chars_.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
        }
    }

    std::string chars_;
    bool valid_ = false;
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

bool read_java_string_array(JNIEnv* env, jobjectArray values, std::vector<std::string>& output) {
    if (values == nullptr) {
        return false;
    }
    const jsize size = env->GetArrayLength(values);
    if (size < 0 || size > 128) {
        return false;
    }
    output.reserve(static_cast<std::size_t>(size));
    for (jsize index = 0; index < size; ++index) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        if (value == nullptr) {
            return false;
        }
        const UtfChars chars(env, value);
        if (chars.get() == nullptr) {
            env->DeleteLocalRef(value);
            return false;
        }
        output.emplace_back(chars.get());
        env->DeleteLocalRef(value);
    }
    return true;
}

std::vector<std::string> plan_prompt(
    ModelRecord& record,
    const std::vector<std::string>& roles,
    const std::vector<std::string>& contents,
    const std::string& system_prompt,
    const std::string& application_template_id,
    const std::string& application_template,
    const std::string& family_template_id,
    const std::string& family_template,
    const std::string& raw_completion,
    bool enable_thinking
) {
    const llama_vocab* vocab = llama_model_get_vocab(record.model);
    if (!raw_completion.empty()) {
        const std::vector<llama_token> tokens = tokenize_prompt(vocab, raw_completion);
        if (tokens.empty()) {
            return error_response("TOKENIZATION_FAILED", "Raw completion tokenization returned no tokens");
        }
        return {
            "ok",
            base64_encode(raw_completion),
            std::to_string(tokens.size()),
            "raw-completion",
            "RAW_COMPLETION",
            "",
        };
    }
    if (roles.size() != contents.size() || roles.empty()) {
        return error_response("INVALID_ARGUMENT", "Prompt roles and contents must have the same non-zero size");
    }

    const char* selected_template = nullptr;
    std::string template_id;
    std::string template_source;
    selected_template = llama_model_chat_template(record.model, nullptr);
    if (selected_template != nullptr && selected_template[0] != '\0') {
        template_id = "gguf-default";
        template_source = "GGUF";
    } else if (!application_template.empty()) {
        selected_template = application_template.c_str();
        template_id = application_template_id;
        template_source = "APPLICATION_OVERRIDE";
    } else if (!family_template.empty()) {
        selected_template = family_template.c_str();
        template_id = family_template_id;
        template_source = "FAMILY_FALLBACK";
    } else {
        return error_response("CHAT_TEMPLATE_UNAVAILABLE", "No approved chat template is available for this model");
    }

    common_chat_templates_ptr templates;
    try {
        const std::string override_template = template_source == "GGUF" ? std::string{} : std::string(selected_template);
        templates = common_chat_templates_init(record.model, override_template);
    } catch (const std::exception&) {
        return error_response("CHAT_TEMPLATE_UNSUPPORTED", "The selected chat template is not supported by this runtime");
    }
    if (!templates) {
        return error_response("CHAT_TEMPLATE_UNSUPPORTED", "The selected chat template could not be initialized");
    }

    common_chat_templates_inputs inputs;
    inputs.add_generation_prompt = true;
    inputs.use_jinja = true;
    inputs.enable_thinking = enable_thinking;
    inputs.messages.reserve(roles.size() + (system_prompt.empty() ? 0U : 1U));
    if (!system_prompt.empty()) {
        common_chat_msg message;
        message.role = "system";
        message.content = system_prompt;
        inputs.messages.push_back(std::move(message));
    }
    for (std::size_t index = 0; index < roles.size(); ++index) {
        common_chat_msg message;
        message.role = roles[index];
        message.content = contents[index];
        inputs.messages.push_back(std::move(message));
    }

    std::string prompt;
    try {
        prompt = common_chat_templates_apply(templates.get(), inputs).prompt;
    } catch (const std::exception&) {
        return error_response("CHAT_TEMPLATE_UNSUPPORTED", "The selected chat template could not be rendered");
    }
    if (prompt.empty()) {
        return error_response("CHAT_TEMPLATE_UNSUPPORTED", "The selected chat template rendered an empty prompt");
    }
    const std::vector<llama_token> tokens = tokenize_prompt(vocab, prompt);
    if (tokens.empty()) {
        return error_response("TOKENIZATION_FAILED", "Rendered prompt tokenization returned no tokens");
    }
    return {
        "ok",
        base64_encode(prompt),
        std::to_string(tokens.size()),
        template_id,
        template_source,
        "",
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
Java_io_github_daniele21_localllm_llamacpp_JniLlamaPromptApi_modelCapabilities(
    JNIEnv* env,
    jobject /* this */,
    jlong model_handle
) {
    const auto model = models.get(static_cast<std::int64_t>(model_handle));
    if (!model) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    const std::int32_t maximum_context = llama_model_n_ctx_train(model->model);
    if (maximum_context <= 0) {
        return to_java_string_array(env, error_response("INTERNAL", "Model context capacity is unavailable"));
    }
    return to_java_string_array(env, {"ok", std::to_string(maximum_context), "true"});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaPromptApi_planPrompt(
    JNIEnv* env,
    jobject /* this */,
    jlong model_handle,
    jobjectArray roles_value,
    jobjectArray contents_value,
    jstring system_prompt_value,
    jstring application_template_id_value,
    jstring application_template_value,
    jstring family_template_id_value,
    jstring family_template_value,
    jstring raw_completion_value,
    jboolean enable_thinking
) {
    const auto model = models.get(static_cast<std::int64_t>(model_handle));
    if (!model) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    if (!read_java_string_array(env, roles_value, roles) ||
        !read_java_string_array(env, contents_value, contents)) {
        return to_java_string_array(env, error_response("INVALID_ARGUMENT", "Prompt message arrays are invalid"));
    }

    auto read_optional = [env](jstring value, std::string& output) -> bool {
        if (value == nullptr) {
            return true;
        }
        const UtfChars chars(env, value);
        if (chars.get() == nullptr) {
            return false;
        }
        output.assign(chars.get());
        return true;
    };
    std::string system_prompt;
    std::string application_template_id;
    std::string application_template;
    std::string family_template_id;
    std::string family_template;
    std::string raw_completion;
    if (!read_optional(system_prompt_value, system_prompt) ||
        !read_optional(application_template_id_value, application_template_id) ||
        !read_optional(application_template_value, application_template) ||
        !read_optional(family_template_id_value, family_template_id) ||
        !read_optional(family_template_value, family_template) ||
        !read_optional(raw_completion_value, raw_completion)) {
        return nullptr;
    }
    return to_java_string_array(
        env,
        plan_prompt(
            *model,
            roles,
            contents,
            system_prompt,
            application_template_id,
            application_template,
            family_template_id,
            family_template,
            raw_completion,
            enable_thinking == JNI_TRUE
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
