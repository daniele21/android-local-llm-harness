#include "gguf_metadata.h"
#include "native_handle_registry.h"

#include "gguf.h"
#include "llama.h"

#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>

namespace {

std::filesystem::path test_path(const std::string& suffix) {
    const auto timestamp = std::chrono::steady_clock::now().time_since_epoch().count();
    return std::filesystem::temp_directory_path() /
        ("android-local-llm-harness-" + std::to_string(timestamp) + suffix);
}

bool require(bool condition, const char* expression, int line) {
    if (!condition) {
        std::cerr << "Assertion failed at line " << line << ": " << expression << '\n';
    }
    return condition;
}

#define REQUIRE(expression) \
    do { \
        if (!require((expression), #expression, __LINE__)) { \
            return false; \
        } \
    } while (false)

bool test_runtime_is_linked() {
    REQUIRE(llama_max_devices() > 0);
    static_cast<void>(llama_supports_mmap());
    return true;
}

bool test_native_handle_registry() {
    struct Value {
        explicit Value(int input) : value(input) {}
        int value;
    };

    NativeHandleRegistry<Value> registry;
    REQUIRE(registry.empty());
    REQUIRE(registry.add(nullptr) == 0);

    const auto first_handle = registry.add(std::make_shared<Value>(7));
    const auto second_handle = registry.add(std::make_shared<Value>(9));
    REQUIRE(first_handle > 0);
    REQUIRE(second_handle > first_handle);
    REQUIRE(registry.size() == 2);
    REQUIRE(registry.get(first_handle)->value == 7);
    REQUIRE(registry.get(second_handle)->value == 9);
    REQUIRE(!registry.get(999));

    const auto acquired = registry.get(first_handle);
    REQUIRE(registry.remove(first_handle));
    REQUIRE(!registry.get(first_handle));
    REQUIRE(acquired->value == 7);
    REQUIRE(!registry.remove(first_handle));

    registry.clear();
    REQUIRE(registry.empty());
    return true;
}

bool test_missing_file() {
    const auto path = test_path("-missing.gguf");
    const local_llm::GgufInspection result = local_llm::inspect_gguf_metadata(path.string());
    REQUIRE(!result.success());
    REQUIRE(result.error_code == local_llm::GgufInspectionErrorCode::file_not_found);
    return true;
}

bool test_invalid_magic() {
    const auto path = test_path("-invalid.gguf");
    {
        std::ofstream output(path, std::ios::binary);
        output << "NOT_A_GGUF_FILE";
    }

    const local_llm::GgufInspection result = local_llm::inspect_gguf_metadata(path.string());
    std::filesystem::remove(path);

    REQUIRE(!result.success());
    REQUIRE(result.error_code == local_llm::GgufInspectionErrorCode::invalid_magic);
    return true;
}

bool test_metadata_only_inspection() {
    const auto path = test_path("-valid.gguf");
    gguf_context* context = gguf_init_empty();
    REQUIRE(context != nullptr);

    gguf_set_val_str(context, "general.architecture", "qwen2");
    gguf_set_val_str(context, "general.name", "native-test-model");
    gguf_set_val_u32(context, "general.file_type", 15);
    const bool written = gguf_write_to_file(context, path.string().c_str(), true);
    gguf_free(context);
    REQUIRE(written);

    const local_llm::GgufInspection result = local_llm::inspect_gguf_metadata(path.string());
    std::filesystem::remove(path);

    REQUIRE(result.success());
    REQUIRE(result.metadata.version == GGUF_VERSION);
    REQUIRE(result.metadata.alignment == GGUF_DEFAULT_ALIGNMENT);
    REQUIRE(result.metadata.key_value_count == 3);
    REQUIRE(result.metadata.tensor_count == 0);
    REQUIRE(result.metadata.architecture == "qwen2");
    REQUIRE(result.metadata.name == "native-test-model");
    REQUIRE(result.metadata.file_type.has_value());
    REQUIRE(result.metadata.file_type.value() == 15);
    return true;
}

}  // namespace

int main() {
    if (!test_runtime_is_linked()) {
        return 1;
    }
    if (!test_native_handle_registry()) {
        return 1;
    }
    if (!test_missing_file()) {
        return 1;
    }
    if (!test_invalid_magic()) {
        return 1;
    }
    if (!test_metadata_only_inspection()) {
        return 1;
    }

    std::cout << "All native llama.cpp lifecycle and GGUF metadata tests passed\n";
    return 0;
}
