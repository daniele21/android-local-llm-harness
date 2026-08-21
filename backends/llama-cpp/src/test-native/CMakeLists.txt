cmake_minimum_required(VERSION 3.22.1)
project(local_llm_native_tests LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(
    REPOSITORY_ROOT
    "${CMAKE_CURRENT_LIST_DIR}/../../../.."
    ABSOLUTE
)
set(LLAMA_CPP_DIR "${REPOSITORY_ROOT}/third_party/llama.cpp")

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Pinned llama.cpp submodule is missing")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
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
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp")

add_library(
    local_llm_gguf_metadata
    STATIC
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp/gguf_metadata.cpp"
)

target_include_directories(
    local_llm_gguf_metadata
    PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
    "${LLAMA_CPP_DIR}/ggml/include"
)

target_link_libraries(local_llm_gguf_metadata PUBLIC ggml)

add_executable(gguf_metadata_test gguf_metadata_test.cpp)
target_include_directories(
    gguf_metadata_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
    "${LLAMA_CPP_DIR}/include"
)
target_link_libraries(gguf_metadata_test PRIVATE local_llm_gguf_metadata llama)

add_executable(native_cancellation_registry_test native_cancellation_registry_test.cpp)
target_include_directories(
    native_cancellation_registry_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

find_package(Threads REQUIRED)
target_link_libraries(native_cancellation_registry_test PRIVATE Threads::Threads)

add_executable(
    generation_output_buffer_test
    generation_output_buffer_test.cpp
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp/generation_output_buffer.cpp"
)
target_include_directories(
    generation_output_buffer_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

add_executable(
    generation_sampler_test
    generation_sampler_test.cpp
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp/generation_sampler.cpp"
)
target_include_directories(
    generation_sampler_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
    "${LLAMA_CPP_DIR}/include"
)
target_link_libraries(generation_sampler_test PRIVATE llama)

add_executable(
    prepared_prompt_cache_test
    prepared_prompt_cache_test.cpp
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp/prepared_prompt_cache.cpp"
)
target_include_directories(
    prepared_prompt_cache_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

target_link_libraries(prepared_prompt_cache_test PRIVATE Threads::Threads)

add_executable(model_load_params_compat_test model_load_params_compat_test.cpp)
target_include_directories(
    model_load_params_compat_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

add_executable(flash_attention_params_test flash_attention_params_test.cpp)
target_include_directories(
    flash_attention_params_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

add_executable(
    reasoning_transition_test
    reasoning_transition_test.cpp
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp/reasoning_transition.cpp"
)
target_include_directories(
    reasoning_transition_test
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
)

add_executable(recurrent_state_reuse_probe recurrent_state_reuse_probe.cpp)
target_include_directories(
    recurrent_state_reuse_probe
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../main/cpp"
    "${LLAMA_CPP_DIR}/include"
)
target_link_libraries(recurrent_state_reuse_probe PRIVATE llama)

enable_testing()
add_test(NAME gguf_metadata_test COMMAND gguf_metadata_test)
add_test(NAME native_cancellation_registry_test COMMAND native_cancellation_registry_test)
add_test(NAME generation_output_buffer_test COMMAND generation_output_buffer_test)
add_test(NAME generation_sampler_test COMMAND generation_sampler_test)
add_test(NAME prepared_prompt_cache_test COMMAND prepared_prompt_cache_test)
add_test(NAME model_load_params_compat_test COMMAND model_load_params_compat_test)
add_test(NAME flash_attention_params_test COMMAND flash_attention_params_test)
add_test(NAME reasoning_transition_test COMMAND reasoning_transition_test)
add_test(NAME recurrent_state_reuse_probe COMMAND recurrent_state_reuse_probe)
set_tests_properties(recurrent_state_reuse_probe PROPERTIES SKIP_RETURN_CODE 77)
