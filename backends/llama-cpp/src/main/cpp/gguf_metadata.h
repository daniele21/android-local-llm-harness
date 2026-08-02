#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace local_llm {

enum class GgufInspectionErrorCode {
    none,
    invalid_argument,
    file_not_found,
    invalid_magic,
    parse_failed,
};

struct GgufMetadata {
    std::uint32_t version = 0;
    std::uint64_t alignment = 0;
    std::uint64_t data_offset = 0;
    std::int64_t key_value_count = 0;
    std::int64_t tensor_count = 0;
    std::string architecture;
    std::string name;
    std::optional<std::int64_t> file_type;
};

struct GgufInspection {
    GgufInspectionErrorCode error_code = GgufInspectionErrorCode::none;
    std::string error_message;
    GgufMetadata metadata;

    bool success() const {
        return error_code == GgufInspectionErrorCode::none;
    }
};

GgufInspection inspect_gguf_metadata(const std::string& path);
const char* gguf_inspection_error_code_name(GgufInspectionErrorCode code);

}  // namespace local_llm
