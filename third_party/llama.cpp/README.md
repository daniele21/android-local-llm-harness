# llama.cpp integration placeholder

The initial repository does not silently track `llama.cpp` master.

Before enabling inference:

1. select and benchmark a specific upstream commit;
2. add it as a pinned git submodule at this path;
3. update `backends/llama-cpp/src/main/cpp/CMakeLists.txt` to add the upstream project;
4. replace `llama_jni_stub.cpp` with the real coarse-grained JNI bridge;
5. record the commit, NDK, compiler flags and supported GGUF version in runtime diagnostics.

The JNI API should keep tokenization, prefill and decode loops native. Kotlin callbacks should be aggregated rather than emitted once per token.
