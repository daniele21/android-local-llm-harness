cmake_minimum_required(VERSION 3.22.1)
project(local_llm_jni LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(
    LLAMA_CPP_DIR
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/llama.cpp"
    ABSOLUTE
)

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Pinned llama.cpp submodule is missing. Run git submodule update --init --recursive.")
endif()

# Runtime-selected Android CPU variants are emitted as shared backend libraries.
# The backend loader and BUILD_SHARED_LIBS must therefore stay enabled together.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_TOOLS_INSTALL OFF CACHE BOOL "" FORCE)
set(LLAMA_TESTS_INSTALL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp")

add_library(
    local_llm_jni
    SHARED
    gguf_metadata.cpp
    common_string_utils.cpp
    generation_output_buffer.cpp
    generation_sampler.cpp
    llama_jni_entry.cpp
    "${LLAMA_CPP_DIR}/common/json-schema-to-grammar.cpp"
)

target_compile_definitions(
    local_llm_jni
    PRIVATE
    LOCAL_LLM_LLAMA_CPP_TAG="b9637"
    LOCAL_LLM_LLAMA_CPP_COMMIT="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"
)

target_include_directories(
    local_llm_jni
    PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
    "${LLAMA_CPP_DIR}/common"
    "${LLAMA_CPP_DIR}/vendor"
)

find_library(android_lib android REQUIRED)
find_library(log_lib log REQUIRED)
find_library(dl_lib dl REQUIRED)

target_link_libraries(
    local_llm_jni
    PRIVATE
    llama
    ggml
    ${android_lib}
    ${log_lib}
    ${dl_lib}
)
