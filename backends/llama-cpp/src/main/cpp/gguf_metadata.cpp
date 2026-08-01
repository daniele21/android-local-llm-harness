#include "gguf_metadata.h"

#include "gguf.h"

#include <cstring>
#include <fstream>
#include <limits>

namespace local_llm {
namespace {

std::string read_optional_string(const gguf_context* context, const char* key) {
    const std::int64_t key_id = gguf_find_key(context, key);
    if (key_id < 0 || gguf_get_kv_type(context, key_id) != GGUF_TYPE_STRING) {
        return {};
    }

    const char* value = gguf_get_val_str(context, key_id);
    return value == nullptr ? std::string{} : std::string(value);
}

std::optional<std::int64_t> read_optional_integer(const gguf_context* context, const char* key) {
    const std::int64_t key_id = gguf_find_key(context, key);
    if (key_id < 0) {
        return std::nullopt;
    }

    switch (gguf_get_kv_type(context, key_id)) {
        case GGUF_TYPE_UINT8:
            return gguf_get_val_u8(context, key_id);
        case GGUF_TYPE_INT8:
            return gguf_get_val_i8(context, key_id);
        case GGUF_TYPE_UINT16:
            return gguf_get_val_u16(context, key_id);
        case GGUF_TYPE_INT16:
            return gguf_get_val_i16(context, key_id);
        case GGUF_TYPE_UINT32:
            return gguf_get_val_u32(context, key_id);
        case GGUF_TYPE_INT32:
            return gguf_get_val_i32(context, key_id);
        case GGUF_TYPE_UINT64: {
            const std::uint64_t value = gguf_get_val_u64(context, key_id);
            if (value > static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max())) {
                return std::nullopt;
            }
            return static_cast<std::int64_t>(value);
        }
        case GGUF_TYPE_INT64:
            return gguf_get_val_i64(context, key_id);
        default:
            return std::nullopt;
    }
}

GgufInspection failure(GgufInspectionErrorCode code, std::string message) {
    GgufInspection result;
    result.error_code = code;
    result.error_message = std::move(message);
    return result;
}

}  // namespace

GgufInspection inspect_gguf_metadata(const std::string& path) {
    if (path.empty()) {
        return failure(GgufInspectionErrorCode::invalid_argument, "GGUF path must not be empty");
    }

    std::ifstream input(path, std::ios::binary);
    if (!input.is_open()) {
        return failure(GgufInspectionErrorCode::file_not_found, "GGUF file cannot be opened");
    }

    char magic[4] = {};
    input.read(magic, sizeof(magic));
    if (input.gcount() != static_cast<std::streamsize>(sizeof(magic)) ||
        std::memcmp(magic, GGUF_MAGIC, sizeof(magic)) != 0) {
        return failure(GgufInspectionErrorCode::invalid_magic, "File does not start with GGUF magic bytes");
    }
    input.close();

    gguf_init_params params = {};
    params.no_alloc = true;
    params.ctx = nullptr;

    gguf_context* context = gguf_init_from_file(path.c_str(), params);
    if (context == nullptr) {
        return failure(GgufInspectionErrorCode::parse_failed, "llama.cpp could not parse GGUF metadata");
    }

    GgufInspection result;
    result.metadata.version = gguf_get_version(context);
    result.metadata.alignment = gguf_get_alignment(context);
    result.metadata.data_offset = gguf_get_data_offset(context);
    result.metadata.key_value_count = gguf_get_n_kv(context);
    result.metadata.tensor_count = gguf_get_n_tensors(context);
    result.metadata.architecture = read_optional_string(context, "general.architecture");
    result.metadata.name = read_optional_string(context, "general.name");
    result.metadata.file_type = read_optional_integer(context, "general.file_type");

    gguf_free(context);
    return result;
}

const char* gguf_inspection_error_code_name(GgufInspectionErrorCode code) {
    switch (code) {
        case GgufInspectionErrorCode::none:
            return "NONE";
        case GgufInspectionErrorCode::invalid_argument:
            return "INVALID_ARGUMENT";
        case GgufInspectionErrorCode::file_not_found:
            return "FILE_NOT_FOUND";
        case GgufInspectionErrorCode::invalid_magic:
            return "INVALID_MAGIC";
        case GgufInspectionErrorCode::parse_failed:
            return "PARSE_FAILED";
    }
    return "UNKNOWN";
}

}  // namespace local_llm
