cmake_minimum_required(VERSION 3.22.1)
project(local_llm_jni LANGUAGES C CXX)

add_library(
    local_llm_jni
    SHARED
    llama_jni_stub.cpp
)

find_library(log-lib log)

target_link_libraries(
    local_llm_jni
    PRIVATE
    ${log-lib}
)
