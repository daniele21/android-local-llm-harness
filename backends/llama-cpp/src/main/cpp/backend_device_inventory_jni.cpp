#include <jni.h>

#include "ggml-backend.h"

#include <cstddef>
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

const char* device_type_name(enum ggml_backend_dev_type type) {
    switch (type) {
        case GGML_BACKEND_DEVICE_TYPE_CPU:
            return "CPU";
        case GGML_BACKEND_DEVICE_TYPE_GPU:
            return "GPU";
        case GGML_BACKEND_DEVICE_TYPE_IGPU:
            return "IGPU";
        case GGML_BACKEND_DEVICE_TYPE_ACCEL:
            return "ACCELERATOR";
        case GGML_BACKEND_DEVICE_TYPE_META:
            return "META";
        default:
            return "UNKNOWN";
    }
}

const char* bool_name(bool value) {
    return value ? "true" : "false";
}

std::string safe_string(const char* value) {
    return value == nullptr ? std::string{} : std::string(value);
}

std::vector<std::string> backend_device_inventory() {
    const std::size_t device_count = ggml_backend_dev_count();
    if (device_count == 0) {
        return {"error", "BACKEND_UNAVAILABLE", "No ggml backend devices are registered"};
    }

    constexpr std::size_t fields_per_device = 11;
    std::vector<std::string> response;
    response.reserve(2 + device_count * fields_per_device);
    response.emplace_back("ok");
    response.emplace_back(std::to_string(device_count));

    for (std::size_t index = 0; index < device_count; ++index) {
        ggml_backend_dev_t device = ggml_backend_dev_get(index);
        if (device == nullptr) {
            return {"error", "INTERNAL", "ggml returned a null backend device"};
        }

        ggml_backend_dev_props props{};
        ggml_backend_dev_get_props(device, &props);
        response.emplace_back(std::to_string(index));
        response.emplace_back(safe_string(props.name));
        response.emplace_back(safe_string(props.description));
        response.emplace_back(device_type_name(props.type));
        response.emplace_back(safe_string(props.device_id));
        response.emplace_back(std::to_string(props.memory_free));
        response.emplace_back(std::to_string(props.memory_total));
        response.emplace_back(bool_name(props.caps.async));
        response.emplace_back(bool_name(props.caps.host_buffer));
        response.emplace_back(bool_name(props.caps.buffer_from_host_ptr));
        response.emplace_back(bool_name(props.caps.events));
    }

    return response;
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_daniele21_localllm_llamacpp_JniLlamaApi_backendDevices(
    JNIEnv* env,
    jobject /* this */
) {
    return to_java_string_array(env, backend_device_inventory());
}
