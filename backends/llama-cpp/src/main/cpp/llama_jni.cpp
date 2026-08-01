#include <jni.h>

#include "gguf_metadata.h"
#include "llama.h"

#include <string>
#include <vector>

namespace {

jobjectArray to_java_string_array(JNIEnv* env, const std::vector<std::string>& values) {
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) {
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(values.size()), string_class, nullptr);
    if (result == nullptr) {
        return nullptr;
    }

    for (std::size_t index = 0; index < values.size(); ++index) {
        jstring value = env->NewStringUTF(values[index].c_str());
        if (value == nullptr) {
            return nullptr;
        }
        env->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }

    return result;
}

std::vector<std::string> encode_inspection(const local_llm::GgufInspection& inspection) {
    if (!inspection.success()) {
        return {
            "error",
            local_llm::gguf_inspection_error_code_name(inspection.error_code),
            inspection.error_message,
        };
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

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_runtimeVersion(JNIEnv* env, jobject /* this */) {
    const std::string version = std::string(LOCAL_LLM_LLAMA_CPP_TAG) + " (" + LOCAL_LLM_LLAMA_CPP_COMMIT + ")";
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_isLlamaCppLinked(JNIEnv* /* env */, jobject /* this */) {
    return llama_max_devices() > 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_supportsMmap(JNIEnv* /* env */, jobject /* this */) {
    return llama_supports_mmap() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_inspectGguf(
    JNIEnv* env,
    jobject /* this */,
    jstring path
) {
    if (path == nullptr) {
        return to_java_string_array(env, {"error", "INVALID_ARGUMENT", "GGUF path must not be null"});
    }

    const char* raw_path = env->GetStringUTFChars(path, nullptr);
    if (raw_path == nullptr) {
        return nullptr;
    }

    const local_llm::GgufInspection inspection = local_llm::inspect_gguf_metadata(raw_path);
    env->ReleaseStringUTFChars(path, raw_path);
    return to_java_string_array(env, encode_inspection(inspection));
}
