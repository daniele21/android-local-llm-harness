#include <jni.h>

#include "ggml-backend.h"
#include "gguf_metadata.h"
#include "llama.h"
#include "native_handle_registry.h"

#include <chrono>
#include <cstdint>
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
};

BackendRuntime backend_runtime;
NativeHandleRegistry<ModelRecord> models;

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
    return to_java_string_array(env, backend_runtime.shutdown(!models.empty()));
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
    if (!models.remove(static_cast<std::int64_t>(model_handle))) {
        return to_java_string_array(env, error_response("UNKNOWN_HANDLE", "Unknown native model handle"));
    }
    return to_java_string_array(env, {"ok"});
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
